package com.nairb.ai130.app.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nairb.ai130.api.dto.StepEvent;
import com.nairb.ai130.app.workflow.executor.*;
import com.nairb.ai130.common.enums.WorkflowNodeType;
import com.nairb.ai130.domain.workflow.CheckResult;
import com.nairb.ai130.domain.workflow.NodeResult;
import com.nairb.ai130.domain.workflow.WorkflowDefinition;
import com.nairb.ai130.domain.workflow.WorkflowExecution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 工作流核心引擎 — 负责 DAG 图的遍历、节点调度、条件转移、断点续跑。
 * <p>
 * 使用说明：
 * <pre>
 *   // 启动新流程
 *   engine.start("default-step-check", prompt, sessionId)
 *       .subscribe(event -> { ... });
 *
 *   // 恢复中断流程
 *   engine.resume(sessionId)
 *       .subscribe(event -> { ... });
 * </pre>
 */
@Service
public class WorkflowEngine {

    private static final Logger log = LoggerFactory.getLogger(WorkflowEngine.class);
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final String GEN_ID = "wf-" + UUID.randomUUID().toString().substring(0, 8);

    private final WorkflowRepository repository;
    private final AnalysisExecutor analysisExecutor;
    private final DecomposeExecutor decomposeExecutor;
    private final ExecuteExecutor executeExecutor;
    private final CheckExecutor checkExecutor;
    private final SynthesizeExecutor synthesizeExecutor;
    private final ParallelSplitExecutor parallelSplitExecutor;
    private final ParallelMergeExecutor parallelMergeExecutor;

    /** 并行分支执行追踪：executionId → List<Flux<NodeResult>> */
    private final Map<String, List<Flux<NodeResult>>> pendingBranches = new ConcurrentHashMap<>();

    public WorkflowEngine(WorkflowRepository repository,
                          AnalysisExecutor analysisExecutor,
                          DecomposeExecutor decomposeExecutor,
                          ExecuteExecutor executeExecutor,
                          CheckExecutor checkExecutor,
                          SynthesizeExecutor synthesizeExecutor,
                          ParallelSplitExecutor parallelSplitExecutor,
                          ParallelMergeExecutor parallelMergeExecutor) {
        this.repository = repository;
        this.analysisExecutor = analysisExecutor;
        this.decomposeExecutor = decomposeExecutor;
        this.executeExecutor = executeExecutor;
        this.checkExecutor = checkExecutor;
        this.synthesizeExecutor = synthesizeExecutor;
        this.parallelSplitExecutor = parallelSplitExecutor;
        this.parallelMergeExecutor = parallelMergeExecutor;
    }

    // ==================== 公开 API ====================

    /**
     * 启动一个新工作流。
     */
    public Flux<ServerSentEvent<String>> start(String definitionId, String prompt, String sessionId) {
        // 1. 加载流程定义
        WorkflowDefinition definition = repository.loadDefinition(definitionId);
        if (definition == null) {
            return Flux.just(buildErrorEvent("工作流定义不存在: " + definitionId));
        }

        // 2. 创建执行记录
        String executionId = genId();
        WorkflowDefinition.NodeDef startNode = definition.getStartNode();
        if (startNode == null) {
            return Flux.just(buildErrorEvent("工作流无起始节点"));
        }

        WorkflowExecution execution = WorkflowExecution.create(executionId, definitionId, sessionId, prompt);
        execution.enterNode(startNode.getId());
        repository.createExecution(execution);

        log.info("[{}] Workflow started: definition={}, execution={}", sessionId, definitionId, executionId);

        // 3. 从起始节点开始执行
        return executeNodeRecursive(execution, definition, prompt, new ArrayList<>(), 0);
    }

    /**
     * 恢复中断的工作流（断点续跑）。
     */
    public Flux<ServerSentEvent<String>> resume(String sessionId) {
        // 1. 查找最近一次执行记录
        WorkflowExecution execution = repository.findLastExecutionBySession(sessionId);
        if (execution == null) {
            return Flux.just(buildErrorEvent("未找到可恢复的工作流"));
        }

        if ("completed".equals(execution.getStatus()) || "failed".equals(execution.getStatus())) {
            log.info("[{}] Workflow already finished (status={}), no need to resume", sessionId, execution.getStatus());
            return Flux.empty();
        }

        // 2. 查找最后一个已完成的节点
        String definitionId = execution.getWorkflowId();
        WorkflowDefinition definition = repository.loadDefinition(definitionId);
        if (definition == null) {
            return Flux.just(buildErrorEvent("工作流定义不存在: " + definitionId));
        }

        NodeResult lastCompleted = repository.getLastCompletedNodeResult(execution.getId());
        List<NodeResult> completedResults = repository.getNodeResults(execution.getId())
                .stream()
                .filter(r -> "completed".equals(r.getStatus()) || "failed".equals(r.getStatus()))
                .collect(Collectors.toList());

        // 3. 从最后一个已完成节点的下一个节点继续
        log.info("[{}] Resuming workflow execution={}, lastCompleted={}",
                sessionId, execution.getId(), lastCompleted != null ? lastCompleted.getNodeId() : "none");

        execution.setStatus("running");

        String resumeNodeId;
        if (lastCompleted != null) {
            // 找到该节点的下一个节点
            WorkflowDefinition.NodeDef completedNodeDef = definition.getNode(lastCompleted.getNodeId());
            if (completedNodeDef == null) {
                return Flux.just(buildErrorEvent("断点节点不存在"));
            }
            List<WorkflowDefinition.EdgeDef> edges = definition.getOutgoingEdges(completedNodeDef.getId());
            if (edges.isEmpty()) {
                return Flux.just(buildErrorEvent("断点后无路径"));
            }
            resumeNodeId = edges.get(0).getTargetNodeId();
        } else {
            // 无已完成节点，从头开始
            WorkflowDefinition.NodeDef startNode = definition.getStartNode();
            if (startNode == null) return Flux.just(buildErrorEvent("工作流无起始节点"));
            resumeNodeId = startNode.getId();
        }

        execution.enterNode(resumeNodeId);
        repository.updateExecution(execution);

        return executeNodeRecursive(execution, definition, execution.getPrompt(), completedResults, 0);
    }

    // ==================== 核心递归执行 ====================

    /**
     * 递归执行节点图，每次执行一个节点后按边条件决定下一个节点。
     */
    private Flux<ServerSentEvent<String>> executeNodeRecursive(
            WorkflowExecution execution,
            WorkflowDefinition definition,
            String prompt,
            List<NodeResult> prevResults,
            int depth) {

        // 深度保护
        if (depth > 20) {
            log.warn("[{}] Max recursion depth reached, aborting", execution.getSessionId());
            execution.fail("递归深度超限");
            repository.updateExecution(execution);
            return Flux.just(buildErrorEvent("流程嵌套过深，已终止"));
        }

        String currentNodeId = execution.getCurrentNodeId();
        if (currentNodeId == null) {
            // 流程结束
            execution.complete();
            repository.updateExecution(execution);
            return Flux.just(buildCompleteEvent(execution));
        }

        WorkflowDefinition.NodeDef currentNode = definition.getNode(currentNodeId);
        if (currentNode == null) {
            execution.fail("节点不存在: " + currentNodeId);
            repository.updateExecution(execution);
            return Flux.just(buildErrorEvent("流程节点不存在"));
        }

        String sessionId = execution.getSessionId();
        log.info("[{}] Executing node: {} ({})", sessionId, currentNode.getName(), currentNode.getNodeType());

        // 发射 step_start 事件
        Flux<ServerSentEvent<String>> startEvent = Flux.just(buildStepStartEvent(currentNode, execution));

        // 路由到对应的执行器
        Flux<NodeResult> nodeResultFlux = routeToExecutor(execution, currentNode, definition, prevResults, prompt);

        // 处理执行结果
        return Flux.concat(
                startEvent,
                nodeResultFlux.flatMap(nodeResult -> {
                    // 保存结果
                    repository.saveNodeResult(nodeResult);
                    List<NodeResult> nextResults = new ArrayList<>(prevResults);
                    nextResults.add(nodeResult);

                    // 如果是 PARALLEL_SPLIT，需要创建子任务分支
                    if (currentNode.getNodeType() == WorkflowNodeType.PARALLEL_SPLIT) {
                        return handleParallelSplit(execution, definition, nodeResult, nextResults, prompt, depth);
                    }

                    // 发射 step_result 事件（仅 EXECUTE 节点）
                    Flux<ServerSentEvent<String>> resultEvent = Flux.empty();
                    if (currentNode.getNodeType() == WorkflowNodeType.EXECUTE
                            || currentNode.getNodeType() == WorkflowNodeType.SYNTHESIZE) {
                        resultEvent = Flux.just(buildStepResultEvent(currentNode, nodeResult));
                    }

                    // 决定下一个节点
                    String nextNodeId = resolveNextNode(definition, currentNode, nodeResult, execution);

                    if (nextNodeId == null) {
                        // 流程结束
                        execution.complete();
                        repository.updateExecution(execution);
                        Flux<ServerSentEvent<String>> answerEvent = Flux.empty();
                        if (currentNode.getNodeType() == WorkflowNodeType.SYNTHESIZE) {
                            answerEvent = Flux.just(ServerSentEvent.<String>builder()
                                    .data(nodeResult.getOutputText() != null ? nodeResult.getOutputText() : "")
                                    .build());
                        }
                        return Flux.concat(resultEvent, answerEvent, Flux.just(buildCompleteEvent(execution)));
                    }

                    execution.enterNode(nextNodeId);
                    repository.updateExecution(execution);

                    // 递归执行下一个节点
                    return Flux.concat(
                            resultEvent,
                            executeNodeRecursive(execution, definition, prompt, nextResults, depth + 1));
                })
        );
    }

    // ==================== 执行器路由 ====================

    /**
     * 根据节点类型路由到对应的执行器。
     */
    private Flux<NodeResult> routeToExecutor(
            WorkflowExecution execution,
            WorkflowDefinition.NodeDef node,
            WorkflowDefinition definition,
            List<NodeResult> prevResults,
            String prompt) {

        // 只传入与当前节点相关的上游结果（用于上下文）
        List<NodeResult> relevantPrev = filterRelevantResults(node, prevResults);

        return switch (node.getNodeType()) {
            case ANALYSIS -> analysisExecutor.execute(execution, node, definition, relevantPrev, prompt);
            case DECOMPOSE -> decomposeExecutor.execute(execution, node, definition, relevantPrev, prompt);
            case EXECUTE -> executeExecutor.execute(execution, node, definition, relevantPrev, prompt);
            case CHECK -> checkExecutor.execute(execution, node, definition, relevantPrev, prompt);
            case SYNTHESIZE -> synthesizeExecutor.execute(execution, node, definition, relevantPrev, prompt);
            case PARALLEL_SPLIT -> parallelSplitExecutor.execute(execution, node, definition, relevantPrev, prompt);
            case PARALLEL_MERGE -> parallelMergeExecutor.execute(execution, node, definition, relevantPrev, prompt);
            default -> {
                log.warn("Unsupported node type: {}", node.getNodeType());
                NodeResult fallback = NodeResult.pending(
                        UUID.randomUUID().toString(), execution.getId(), node.getId(), "");
                fallback.markRunning();
                fallback.markCompleted("[不支持的节点类型: " + node.getNodeType() + "]");
                yield Flux.just(fallback);
            }
        };
    }

    // ==================== 并行分支处理 ====================

    /**
     * 处理 PARALLEL_SPLIT 后的并行执行。
     * 为每个子任务创建虚拟 EXECUTE 节点，并行执行后由 PARALLEL_MERGE 汇总。
     */
    private Flux<ServerSentEvent<String>> handleParallelSplit(
            WorkflowExecution execution,
            WorkflowDefinition definition,
            NodeResult splitResult,
            List<NodeResult> prevResults,
            String prompt,
            int depth) {

        String sessionId = execution.getSessionId();

        // 解析子任务列表
        List<ParallelSplitExecutor.TaskItem> tasks = parallelSplitExecutor.parseTasks(splitResult.getOutputText());
        if (tasks.isEmpty()) {
            // 无任务可执行，直接走到下一个节点
            String nextNodeId = resolveNextNode(definition,
                    definition.getNode(execution.getCurrentNodeId()), splitResult, execution);
            if (nextNodeId == null) {
                execution.complete();
                repository.updateExecution(execution);
                return Flux.just(buildCompleteEvent(execution));
            }
            execution.enterNode(nextNodeId);
            repository.updateExecution(execution);
            return executeNodeRecursive(execution, definition, prompt, prevResults, depth + 1);
        }

        log.info("[{}] Parallel split: {} tasks", sessionId, tasks.size());

        // 为每个子任务创建一个虚拟 EXECUTE 节点并并行执行
        List<Flux<NodeResult>> branchFluxes = new ArrayList<>();
        for (ParallelSplitExecutor.TaskItem task : tasks) {
            WorkflowDefinition.NodeDef virtualNode = createVirtualExecuteNode(task, definition.getId());
            branchFluxes.add(executeExecutor.execute(execution, virtualNode, definition, prevResults,
                    "任务 " + task.getId() + ": " + task.getDesc()));
        }

        // 并行执行所有分支，完成后由 PARALLEL_MERGE 汇总
        List<NodeResult> branchResults = new ArrayList<>();
        return Flux.merge(branchFluxes)
                .doOnNext(branchResults::add)
                .thenMany(Flux.defer(() -> {
                    // 所有分支完成后，找到 MERGE 节点
                    WorkflowDefinition.NodeDef mergeNode = findNodeByType(definition, WorkflowNodeType.PARALLEL_MERGE);
                    if (mergeNode == null) {
                        // 没有 MERGE 节点，直接汇总所有结果并走到 SYNTHESIZE
                        NodeResult merged = mergeBranchResults(execution, definition, branchResults);
                        List<NodeResult> allResults = new ArrayList<>(prevResults);
                        allResults.add(merged);

                        WorkflowDefinition.NodeDef synthesizeNode = findNodeByType(definition, WorkflowNodeType.SYNTHESIZE);
                        if (synthesizeNode != null) {
                            execution.enterNode(synthesizeNode.getId());
                            repository.updateExecution(execution);
                            return executeNodeRecursive(execution, definition, prompt, allResults, depth + 1);
                        }
                    }

                    execution.enterNode(mergeNode.getId());
                    repository.updateExecution(execution);

                    // 将分支结果作为 prevResults 传给 MERGE
                    List<NodeResult> mergePrev = new ArrayList<>(prevResults);
                    mergePrev.addAll(branchResults);

                    return executeNodeRecursive(execution, definition, prompt, mergePrev, depth + 1);
                }));
    }

    // ==================== 条件边解析 ====================

    /**
     * 根据边条件和节点执行结果决定下一个节点。
     * <p>
     * 支持的 condition_expr:
     * - NULL → 无条件，走第一条边
     * - check.passed == true → CHECK 通过
     * - check.passed == false → CHECK 不通过
     * - check.passed == false && retryCount < 3 → 不通过且可重试
     * - check.passed == false && retryCount >= 3 → 不通过且超重试上限
     */
    private String resolveNextNode(WorkflowDefinition definition,
                                   WorkflowDefinition.NodeDef currentNode,
                                   NodeResult nodeResult,
                                   WorkflowExecution execution) {
        List<WorkflowDefinition.EdgeDef> edges = definition.getOutgoingEdges(currentNode.getId());
        if (edges.isEmpty()) return null;

        // 解析检查结果
        CheckResult checkResult = parseCheckResult(nodeResult);
        Boolean requiresWorkflow = parseRequiresWorkflow(nodeResult);

        for (WorkflowDefinition.EdgeDef edge : edges) {
            String expr = edge.getConditionExpr();
            if (expr == null || expr.isBlank()) {
                // 无条件边，直接走（取第一条无条件边）
                return edge.getTargetNodeId();
            }

            // 评估条件
            if (evaluateCondition(expr, checkResult, requiresWorkflow, execution)) {
                return edge.getTargetNodeId();
            }
        }

        // 没有匹配的边，走最后一条无条件边（如果有）
        for (WorkflowDefinition.EdgeDef edge : edges) {
            if (edge.getConditionExpr() == null || edge.getConditionExpr().isBlank()) {
                return edge.getTargetNodeId();
            }
        }

        return null;
    }

    /**
     * 评估条件表达式。
     */
    private boolean evaluateCondition(String expr, CheckResult checkResult,
                                      Boolean requiresWorkflow, WorkflowExecution execution) {
        if (expr == null || expr.isBlank()) return true;

        boolean passed = checkResult != null && checkResult.isPassed();
        int retryCount = execution.getRetryCount();

        if ("analysis.requiresWorkflow == true".equals(expr)) return Boolean.TRUE.equals(requiresWorkflow);
        if ("analysis.requiresWorkflow == false".equals(expr)) return Boolean.FALSE.equals(requiresWorkflow);

        // 简单条件解析
        if ("check.passed == true".equals(expr)) return passed;
        if ("check.passed == false".equals(expr)) return !passed;

        // 动态条件：解析 retryCount
        if (expr.contains("retryCount")) {
            if (expr.contains("< 3") && retryCount < 3 && !passed) {
                execution.incrementRetry();
                return true;
            }
            if (expr.contains(">= 3") && retryCount >= 3) return !passed;
            if (expr.contains("<")) {
                int limit = extractNumber(expr, "<");
                if (!passed && retryCount < limit) {
                    execution.incrementRetry();
                    return true;
                }
            }
            if (expr.contains(">=")) {
                int limit = extractNumber(expr, ">=");
                if (!passed && retryCount >= limit) return true;
            }
        }

        return false;
    }

    private Boolean parseRequiresWorkflow(NodeResult nodeResult) {
        if (nodeResult == null || nodeResult.getOutputText() == null) return true;
        String text = nodeResult.getOutputText();
        try {
            int start = text.indexOf('{');
            int end = text.lastIndexOf('}');
            if (start < 0 || end <= start) return true;
            var root = mapper.readTree(text.substring(start, end + 1));
            return root.has("requiresWorkflow") ? root.get("requiresWorkflow").asBoolean(true) : true;
        } catch (Exception e) {
            log.warn("Failed to parse analysis complexity result: {}", e.getMessage());
            return true;
        }
    }

    private int extractNumber(String expr, String operator) {
        try {
            int idx = expr.indexOf(operator);
            if (idx < 0) return Integer.MAX_VALUE;
            String after = expr.substring(idx + operator.length()).trim();
            String[] parts = after.split("\\s+");
            return Integer.parseInt(parts[0]);
        } catch (Exception e) {
            return Integer.MAX_VALUE;
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 为子任务创建虚拟 EXECUTE 节点。
     */
    private WorkflowDefinition.NodeDef createVirtualExecuteNode(ParallelSplitExecutor.TaskItem task, String workflowId) {
        WorkflowDefinition.NodeDef node = new WorkflowDefinition.NodeDef();
        node.setId("exec-" + task.getId());
        node.setWorkflowId(workflowId);
        node.setNodeType(WorkflowNodeType.EXECUTE);
        node.setName("子任务 " + task.getId());
        node.setConfigJson(task.getDesc());  // configJson 存储子任务描述
        node.setRetryLimit(3);
        return node;
    }

    private WorkflowDefinition.NodeDef findNodeByType(WorkflowDefinition definition, WorkflowNodeType type) {
        if (definition.getNodes() == null) return null;
        return definition.getNodes().stream()
                .filter(n -> n.getNodeType() == type)
                .findFirst()
                .orElse(null);
    }

    private NodeResult mergeBranchResults(WorkflowExecution execution,
                                           WorkflowDefinition definition,
                                           List<NodeResult> branchResults) {
        StringBuilder merged = new StringBuilder("## 并行执行结果\n\n");
        for (int i = 0; i < branchResults.size(); i++) {
            NodeResult r = branchResults.get(i);
            merged.append("### 子任务 ").append(i + 1).append("\n");
            merged.append(r.getOutputText() != null ? r.getOutputText() : "无输出").append("\n\n");
        }

        NodeResult result = NodeResult.pending(
                UUID.randomUUID().toString(), execution.getId(), "merge", "");
        result.markRunning();
        result.markCompleted(merged.toString());
        repository.saveNodeResult(result);
        return result;
    }

    /**
     * 筛选与当前节点相关的上游结果。
     */
    private List<NodeResult> filterRelevantResults(WorkflowDefinition.NodeDef currentNode, List<NodeResult> allResults) {
        if (allResults == null || allResults.isEmpty()) return List.of();

        return switch (currentNode.getNodeType()) {
            case ANALYSIS -> List.of();  // 分析阶段不依赖上游
            case DECOMPOSE -> allResults;  // 需要 ANALYSIS 结果
            case EXECUTE -> allResults;    // 需要上下文
            case CHECK -> allResults;      // 需要所有执行结果
            case SYNTHESIZE -> allResults; // 需要全部结果
            default -> allResults;
        };
    }

    /**
     * 从 NodeResult 的 checkResultJson 解析 CheckResult。
     */
    private CheckResult parseCheckResult(NodeResult nodeResult) {
        if (nodeResult == null || nodeResult.getCheckResultJson() == null) return null;
        try {
            return mapper.readValue(nodeResult.getCheckResultJson(), CheckResult.class);
        } catch (Exception e) {
            return null;
        }
    }

    // ==================== SSE 事件构建 ====================

    private ServerSentEvent<String> buildStepStartEvent(WorkflowDefinition.NodeDef node, WorkflowExecution execution) {
        StepEvent event = new StepEvent();
        event.setPhase(node.getNodeType().name());
        event.setMessage("正在" + node.getName() + "...");
        event.setLoopCount(execution.getRetryCount());
        return ServerSentEvent.<String>builder()
                .event("step_start")
                .data(toJson(event))
                .build();
    }

    private ServerSentEvent<String> buildStepResultEvent(WorkflowDefinition.NodeDef node, NodeResult result) {
        StepEvent event = new StepEvent();
        event.setPhase(node.getNodeType().name());
        event.setTask(node.getName());
        event.setTaskId(node.getId());
        event.setOutput(result.getOutputText());
        return ServerSentEvent.<String>builder()
                .event("step_result")
                .data(toJson(event))
                .build();
    }

    private ServerSentEvent<String> buildCompleteEvent(WorkflowExecution execution) {
        StepEvent event = new StepEvent();
        event.setPhase("COMPLETE");
        event.setTotalLoops(execution.getRetryCount());
        event.setMessage("流程执行完成");
        return ServerSentEvent.<String>builder()
                .event("step_complete")
                .data(toJson(event))
                .build();
    }

    private ServerSentEvent<String> buildErrorEvent(String error) {
        StepEvent event = new StepEvent();
        event.setPhase("ERROR");
        event.setError(error);
        return ServerSentEvent.<String>builder()
                .event("step_error")
                .data(toJson(event))
                .build();
    }

    private String toJson(Object obj) {
        try {
            return mapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String genId() {
        return GEN_ID + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}

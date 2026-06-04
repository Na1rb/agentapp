package com.nairb.ai130.app.workflow.executor;

import com.nairb.ai130.domain.workflow.NodeResult;
import com.nairb.ai130.domain.workflow.WorkflowDefinition;
import com.nairb.ai130.domain.workflow.WorkflowExecution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * PARALLEL_MERGE 节点执行器 — 收集所有并行分支的执行结果。
 * <p>
 * 不调用 AI 模型，只做结果归集整理，供 SYNTHESIZE 节点使用。
 */
@Component
public class ParallelMergeExecutor implements NodeExecutor {

    private static final Logger log = LoggerFactory.getLogger(ParallelMergeExecutor.class);

    @Override
    public Flux<NodeResult> execute(WorkflowExecution execution,
                                    WorkflowDefinition.NodeDef node,
                                    WorkflowDefinition definition,
                                    List<NodeResult> prevResults,
                                    String prompt) {
        String sessionId = execution.getSessionId();

        // 汇总所有前置 EXECUTE 节点的输出
        StringBuilder merged = new StringBuilder();
        merged.append("## 并行执行结果汇总\n\n");

        List<NodeResult> executeResults = prevResults.stream()
                .filter(r -> r.getOutputText() != null && !r.getOutputText().isBlank())
                .collect(Collectors.toList());

        for (int i = 0; i < executeResults.size(); i++) {
            NodeResult r = executeResults.get(i);
            merged.append("### 子任务 ").append(i + 1).append(" (").append(r.getNodeId()).append(")\n");
            merged.append(r.getOutputText()).append("\n\n");
        }

        log.info("[{}] PARALLEL_MERGE: merged {} results", sessionId, executeResults.size());

        NodeResult result = NodeResult.pending(
                UUID.randomUUID().toString(),
                execution.getId(),
                node.getId(),
                "合并并行执行结果");
        result.markRunning();
        result.markCompleted(merged.toString());

        return Flux.just(result);
    }
}

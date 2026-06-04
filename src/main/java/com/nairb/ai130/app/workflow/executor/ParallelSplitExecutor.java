package com.nairb.ai130.app.workflow.executor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nairb.ai130.common.enums.WorkflowNodeType;
import com.nairb.ai130.domain.workflow.NodeResult;
import com.nairb.ai130.domain.workflow.WorkflowDefinition;
import com.nairb.ai130.domain.workflow.WorkflowExecution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * PARALLEL_SPLIT 节点执行器 — 将 DECOMPOSE 输出的子任务列表拆分为多个 EXECUTE 子节点。
 * <p>
 * 该节点不直接调用 AI 模型，而是解析前序节点的输出，
 * 为每个子任务创建一个虚拟的 EXECUTE 节点配置，由 WorkflowEngine 并行调度。
 */
@Component
public class ParallelSplitExecutor implements NodeExecutor {

    private static final Logger log = LoggerFactory.getLogger(ParallelSplitExecutor.class);

    private static final ObjectMapper mapper = new ObjectMapper();
    private static final Pattern JSON_BLOCK = Pattern.compile(
            "```(?:json)?\\s*([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);
    private static final Pattern JSON_OBJECT = Pattern.compile(
            "\\{[^{}]*\"tasks\"[^{}]*\\[[^]]*\\][^{}]*\\}", Pattern.CASE_INSENSITIVE);

    @Override
    public Flux<NodeResult> execute(WorkflowExecution execution,
                                    WorkflowDefinition.NodeDef node,
                                    WorkflowDefinition definition,
                                    List<NodeResult> prevResults,
                                    String prompt) {
        String sessionId = execution.getSessionId();

        // 从上一个节点（DECOMPOSE）的输出中解析子任务列表
        String decomposeOutput = extractLastOutput(prevResults);
        List<TaskItem> tasks = parseTasks(decomposeOutput);

        String resultJson;
        try {
            resultJson = mapper.writeValueAsString(tasks);
        } catch (JsonProcessingException e) {
            resultJson = "[]";
        }

        log.info("[{}] PARALLEL_SPLIT: extracted {} tasks", sessionId, tasks.size());

        NodeResult result = NodeResult.pending(
                UUID.randomUUID().toString(),
                execution.getId(),
                node.getId(),
                decomposeOutput);
        result.markRunning();
        result.markCompleted(resultJson);

        return Flux.just(result);
    }

    /**
     * 解析从 DECOMPOSE 输出中提取的子任务。
     */
    public List<TaskItem> parseTasks(String text) {
        if (text == null || text.isBlank()) return List.of();

        String json = extractJson(text);
        if (json == null) return List.of();

        try {
            Map<String, Object> map = mapper.readValue(json,
                    new TypeReference<Map<String, Object>>() {});
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> tasksRaw = (List<Map<String, Object>>) map.get("tasks");
            if (tasksRaw == null || tasksRaw.isEmpty()) return List.of();

            List<TaskItem> tasks = new ArrayList<>();
            for (Map<String, Object> t : tasksRaw) {
                String id = String.valueOf(t.getOrDefault("id", String.valueOf(tasks.size() + 1)));
                String desc = String.valueOf(t.getOrDefault("desc", "未命名任务"));
                tasks.add(new TaskItem(id, desc));
            }
            return tasks;
        } catch (Exception e) {
            log.warn("Failed to parse tasks: {}", e.getMessage());
            return List.of();
        }
    }

    private String extractJson(String text) {
        Matcher blockMatcher = JSON_BLOCK.matcher(text);
        if (blockMatcher.find()) return blockMatcher.group(1).trim();
        Matcher objMatcher = JSON_OBJECT.matcher(text);
        if (objMatcher.find()) return objMatcher.group(0).trim();
        String trimmed = text.trim();
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) return trimmed;
        return null;
    }

    private String extractLastOutput(List<NodeResult> results) {
        if (results == null || results.isEmpty()) return "";
        return results.stream()
                .filter(r -> r.getOutputText() != null)
                .reduce((a, b) -> b)
                .map(NodeResult::getOutputText)
                .orElse("");
    }

    // ==================== 内部类 ====================

    /** 解析出的子任务项 */
    public static class TaskItem {
        private String id;
        private String desc;

        public TaskItem() {}
        public TaskItem(String id, String desc) { this.id = id; this.desc = desc; }
        public String getId() { return id; }
        public String getDesc() { return desc; }
        public void setId(String id) { this.id = id; }
        public void setDesc(String desc) { this.desc = desc; }
    }
}

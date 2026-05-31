package com.nairb.ai130.app.advisor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nairb.ai130.api.dto.StepState;
import com.nairb.ai130.app.service.StepStateManager;
import com.nairb.ai130.common.enums.StepPhase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 核心分步编排器。
 * <p>
 * 实现 ANALYZE → EXECUTE → CHECK → LOOP 状态机，支持最大 3 次循环。
 * 不直接实现 Spring AI Advisor 接口，而是作为纯逻辑组件由 {@code ChatAppService} 驱动：
 * <ul>
 *   <li>{@link #buildPhasePrompt(StepState)}：根据当前阶段生成对应的 system prompt</li>
 *   <li>{@link #handleResponse(StepState, String)}：解析模型输出，推进状态机</li>
 * </ul>
 *
 * <h3>降级策略</h3>
 * 当 ANALYZE 阶段模型输出格式不稳定导致 JSON 解析失败时，
 * 自动降级为单步直接回答模式（创建一个综合任务继续执行）。
 */
public class StepOrchestrationAdvisor {

    private static final Logger log = LoggerFactory.getLogger(StepOrchestrationAdvisor.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /** 匹配 JSON 代码块：```json ... ``` */
    private static final Pattern JSON_BLOCK_PATTERN = Pattern.compile(
            "```(?:json)?\\s*([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);

    /** 匹配裸 JSON 对象 */
    private static final Pattern JSON_OBJECT_PATTERN = Pattern.compile(
            "\\{[^{}]*\"tasks\"[^{}]*\\[[^]]*\\][^{}]*\\}", Pattern.CASE_INSENSITIVE);

    private final StepStateManager stepStateManager;
    private final String chatId;

    public StepOrchestrationAdvisor(StepStateManager stepStateManager, String chatId) {
        this.stepStateManager = stepStateManager;
        this.chatId = chatId;
    }

    // ==================== Prompt 构建 ====================

    /**
     * 根据当前阶段构建对应的 system prompt 追加文本。
     */
    public String buildPhasePrompt() {
        StepState state = stepStateManager.get(chatId);
        if (state == null) return "";

        return switch (state.getPhase()) {
            case ANALYZE -> buildAnalyzePrompt();
            case EXECUTE -> buildExecutePrompt(state);
            case SYNTHESIZE -> buildSynthesizePrompt(state);
            case CHECK -> buildCheckPrompt(state);
        };
    }

    /**
     * ANALYZE 阶段：要求模型拆解用户请求为子任务列表，输出 JSON。
     */
    private String buildAnalyzePrompt() {
        return """
                [任务分解模式]
                请分析用户的请求，将其拆解为可独立执行的子任务。

                要求：
                1. 每个子任务应该是原子操作（一次工具调用或一个简单推理）
                2. 子任务应按执行顺序排列
                3. 如果涉及工具调用（如天气查询），每个地点应独立为一个子任务
                4. 最后应包含一个"综合分析"子任务来汇总结果

                请严格按以下 JSON 格式输出（不要包含其他文字）：
                ```json
                {
                  "tasks": [
                    {"id": "1", "desc": "查询杭州明天天气"},
                    {"id": "2", "desc": "查询北京明天天气"},
                    {"id": "3", "desc": "比较两地天气并给出建议"}
                  ]
                }
                ```

                用户请求请见下一条消息，请直接输出 JSON 任务列表。
                """;
    }

    /**
     * EXECUTE 阶段：要求模型执行当前子任务。
     */
    private String buildExecutePrompt(StepState state) {
        StepState.Task currentTask = state.getCurrentTask();
        String taskDesc = currentTask != null ? currentTask.getDesc() : "未知任务";

        StringBuilder sb = new StringBuilder();
        sb.append("[任务执行模式]\n");
        sb.append("请执行以下子任务：").append(taskDesc).append("\n\n");

        // 提供上下文：已完成的任务结果
        if (state.getCompletedResults() != null && !state.getCompletedResults().isEmpty()) {
            sb.append("已完成的任务结果（供参考）：\n");
            for (StepState.CompletedResult cr : state.getCompletedResults()) {
                sb.append("- [").append(cr.getTaskId()).append("] ")
                        .append(truncate(cr.getOutput(), 300)).append("\n");
            }
            sb.append("\n");
        }

        sb.append("如果有可用的工具，请使用工具获取最新数据。直接给出执行结果，无需额外解释。");
        return sb.toString();
    }

    /**
     * CHECK 阶段：程序化检查的 prompt（保留作为兜底）。
     */
    private String buildCheckPrompt(StepState state) {
        StringBuilder sb = new StringBuilder();
        sb.append("[结果检查模式]\n");
        sb.append("以下是已完成的子任务结果：\n\n");

        if (state.getCompletedResults() != null) {
            for (StepState.CompletedResult cr : state.getCompletedResults()) {
                sb.append("- [").append(cr.getTaskId()).append("] ")
                        .append(truncate(cr.getOutput(), 200)).append("\n");
            }
        }

        sb.append("\n待完成的子任务：\n");
        if (state.getPendingTasks() != null) {
            for (StepState.Task t : state.getPendingTasks()) {
                if (!"DONE".equals(t.getStatus())) {
                    sb.append("- [").append(t.getId()).append("] ").append(t.getDesc()).append("\n");
                }
            }
        }

        sb.append("\n如果所有任务都已完成且质量达标，回复 DONE。否则回复 CONTINUE。");
        return sb.toString();
    }

    /**
     * SYNTHESIZE 阶段：汇总全部结果生成最终回答。
     */
    private String buildSynthesizePrompt(StepState state) {
        StringBuilder sb = new StringBuilder();
        sb.append("[结果汇总模式]\n");
        sb.append("请基于以下子任务执行结果，生成一个完整、连贯的回答。\n\n");

        sb.append("已完成的子任务结果：\n");
        if (state.getCompletedResults() != null && !state.getCompletedResults().isEmpty()) {
            for (StepState.CompletedResult cr : state.getCompletedResults()) {
                sb.append("- [").append(cr.getTaskId()).append("] ")
                        .append(truncate(cr.getOutput(), 400)).append("\n");
            }
        } else {
            sb.append("（无已完成结果）\n");
        }

        if (state.isMaxLoopsReached() && !state.allTasksDone()) {
            sb.append("\n注意：已达到最大循环次数，部分任务未完成。请基于已有结果给出最佳回答。\n");
        }

        sb.append("\n请直接给出最终回答：");
        return sb.toString();
    }

    // ==================== 响应处理 & 状态推进 ====================

    /**
     * 解析模型响应并推进状态机。
     *
     * @param state        当前状态
     * @param responseText 模型完整响应文本
     */
    public void handleResponse(StepState state, String responseText) {
        if (state == null) return;

        log.info("[{}] handleResponse phase={} responseLen={}",
                chatId, state.getPhase(), responseText != null ? responseText.length() : 0);

        switch (state.getPhase()) {
            case ANALYZE -> handleAnalyzeResponse(state, responseText);
            case EXECUTE -> handleExecuteResponse(state, responseText);
            case SYNTHESIZE -> handleSynthesizeResponse(state);
            default -> log.debug("[{}] No state transition for phase={}", chatId, state.getPhase());
        }

        stepStateManager.save(chatId, state);
    }

    /**
     * ANALYZE 响应处理：解析模型输出 → 提取子任务列表 → 推进到 EXECUTE。
     * <p>
     * 如果 JSON 解析失败，降级为单步直接回答模式。
     */
    private void handleAnalyzeResponse(StepState state, String responseText) {
        List<StepState.Task> tasks = parseTasksFromResponse(responseText);

        if (tasks == null || tasks.isEmpty()) {
            log.warn("[{}] ANALYZE parse failed, degrading to single-step mode. Response: {}",
                    chatId, truncate(responseText, 200));
            tasks = new ArrayList<>();
            tasks.add(new StepState.Task("1", "综合处理用户请求", "PENDING"));
        }

        state.setPendingTasks(tasks);
        state.setCurrentTaskIndex(0);
        state.setPhase(StepPhase.EXECUTE);

        log.info("[{}] ANALYZE → EXECUTE, {} tasks: {}",
                chatId, tasks.size(),
                tasks.stream().map(t -> t.getId() + ":" + t.getDesc())
                        .collect(Collectors.joining(", ")));
    }

    /**
     * EXECUTE 响应处理：记录当前任务完成 → 推进到 CHECK → 判定下一步。
     */
    private void handleExecuteResponse(StepState state, String responseText) {
        StepState.Task currentTask = state.getCurrentTask();
        if (currentTask != null) {
            state.markCurrentTaskDone(responseText);
            log.info("[{}] Task [{}] done: {}", chatId, currentTask.getId(), currentTask.getDesc());
        } else {
            log.warn("[{}] EXECUTE: no current task found", chatId);
        }

        // CHECK 逻辑（程序化判断，不需要模型调用）
        if (state.allTasksDone()) {
            state.setPhase(StepPhase.SYNTHESIZE);
            log.info("[{}] CHECK → SYNTHESIZE (all tasks done, loop={})",
                    chatId, state.getLoopCount());
        } else if (state.isMaxLoopsReached()) {
            state.setPhase(StepPhase.SYNTHESIZE);
            log.info("[{}] CHECK → SYNTHESIZE (max loops reached, loop={})",
                    chatId, state.getLoopCount());
        } else {
            state.incrementLoop();
            state.setPhase(StepPhase.EXECUTE);
            log.info("[{}] CHECK → EXECUTE (loop={}/{}, next task: [{}])",
                    chatId, state.getLoopCount(), state.getMaxLoops(),
                    state.getCurrentTask() != null ? state.getCurrentTask().getDesc() : "none");
        }
    }

    /**
     * SYNTHESIZE 响应处理：流程结束，清理 Redis 状态。
     */
    private void handleSynthesizeResponse(StepState state) {
        log.info("[{}] SYNTHESIZE complete, clearing state (totalLoops={})",
                chatId, state.getLoopCount());
    }

    // ==================== JSON 解析 ====================

    /**
     * 从模型响应中解析子任务列表。
     */
    List<StepState.Task> parseTasksFromResponse(String responseText) {
        if (responseText == null || responseText.isBlank()) return null;

        String json = extractJson(responseText);
        if (json == null) return null;

        try {
            Map<String, Object> map = objectMapper.readValue(json,
                    new TypeReference<Map<String, Object>>() {});
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> tasksRaw = (List<Map<String, Object>>) map.get("tasks");
            if (tasksRaw == null || tasksRaw.isEmpty()) return null;

            List<StepState.Task> tasks = new ArrayList<>();
            for (Map<String, Object> t : tasksRaw) {
                String id = String.valueOf(t.getOrDefault("id", String.valueOf(tasks.size() + 1)));
                String desc = String.valueOf(t.getOrDefault("desc", "未命名任务"));
                tasks.add(new StepState.Task(id, desc, "PENDING"));
            }
            return tasks;
        } catch (JsonProcessingException e) {
            log.warn("[{}] Failed to parse tasks JSON: {}", chatId, e.getMessage());
            return null;
        } catch (Exception e) {
            log.warn("[{}] Unexpected error parsing tasks: {}", chatId, e.getMessage());
            return null;
        }
    }

    private String extractJson(String text) {
        // 1. 尝试匹配 ```json ... ```
        Matcher blockMatcher = JSON_BLOCK_PATTERN.matcher(text);
        if (blockMatcher.find()) {
            return blockMatcher.group(1).trim();
        }

        // 2. 尝试匹配裸 JSON 对象
        Matcher objectMatcher = JSON_OBJECT_PATTERN.matcher(text);
        if (objectMatcher.find()) {
            return objectMatcher.group(0).trim();
        }

        // 3. 尝试将整个文本作为 JSON
        String trimmed = text.trim();
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            return trimmed;
        }

        return null;
    }

    // ==================== 工具方法 ====================

    private String truncate(String s, int maxLen) {
        if (s == null) return "null";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }
}

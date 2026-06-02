package com.nairb.ai130.agent.engine;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nairb.ai130.domain.entity.AgentFlowStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 顺序管道引擎：严格按照 sequence 顺序逐步执行。
 */
public class SequentialEngine implements ExecutionEngine {

    private static final Logger log = LoggerFactory.getLogger(SequentialEngine.class);
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final Pattern VAR_PATTERN = Pattern.compile("\\{\\{(\\w+)\\.?(\\w*)\\}\\}");

    private final ChatClient primaryClient;
    private final ChatClient fallbackClient;
    private final String fallbackModel;

    /** 存储每步的输出，用于 {{stepId.output}} 引用 */
    private final Map<String, String> outputs = new LinkedHashMap<>();

    public SequentialEngine(ChatClient primaryClient, ChatClient fallbackClient, String fallbackModel) {
        this.primaryClient = primaryClient;
        this.fallbackClient = fallbackClient;
        this.fallbackModel = fallbackModel;
    }

    @Override
    public Flux<ServerSentEvent<String>> execute(String sessionId,
                                                  String userInput,
                                                  List<AgentFlowStep> steps,
                                                  String modelCode) {
        outputs.clear();
        outputs.put("user_input", userInput != null ? userInput : "");

        return executeSteps(sessionId, userInput, steps, modelCode, 0);
    }

    private Flux<ServerSentEvent<String>> executeSteps(String sessionId, String userInput,
                                                        List<AgentFlowStep> steps,
                                                        String modelCode, int index) {
        if (index >= steps.size()) {
            // 全部完成 → 发射 done 事件
            return Flux.just(buildDoneEvent());
        }

        AgentFlowStep step = steps.get(index);
        log.info("[{}] Executing step {}/{}: {} [{}]", sessionId, index + 1, steps.size(),
                step.getStepId(), step.getClientType());

        Flux<ServerSentEvent<String>> startEvent = Flux.just(
            ServerSentEvent.<String>builder()
                .event("step_start")
                .data(json(Map.of(
                    "step_id", step.getStepId(),
                    "client_type", step.getClientType(),
                    "client_name", step.getClientName() != null ? step.getClientName() : ""
                )))
                .build()
        );

        Flux<ServerSentEvent<String>> result = switch (step.getClientType().toUpperCase()) {
            case "MCP_TOOL" -> executeMcpStep(sessionId, step);
            default -> executeLlmStep(sessionId, step, modelCode);
        };

        return Flux.concat(startEvent, result)
            .concatMap(event -> {
                if ("step_result".equals(event.event()) && "done".equals(parseStatus(event.data()))) {
                    // 继续下一个 step
                    return executeSteps(sessionId, userInput, steps, modelCode, index + 1);
                }
                if ("step_error".equals(event.event())) {
                    String onError = step.getOnError() != null ? step.getOnError() : "abort";
                    return handleStepError(sessionId, step, steps, userInput, modelCode, index, event.data(), onError);
                }
                return Flux.just(event);
            });
    }

    // ==================== LLM Step ====================

    private Flux<ServerSentEvent<String>> executeLlmStep(String sessionId,
                                                          AgentFlowStep step,
                                                          String modelCode) {
        String prompt = resolvePrompt(step.getStepPrompt());
        String model = (modelCode != null && !modelCode.isEmpty()) ? modelCode : "qwen-plus";
        ChatClient client = model.toLowerCase().startsWith("deepseek-") ? fallbackClient : primaryClient;

        log.debug("[{}] LLM step {} prompt: {}", sessionId, step.getStepId(),
                prompt.length() > 200 ? prompt.substring(0, 200) + "..." : prompt);

        StringBuilder fullResponse = new StringBuilder();

        return client.prompt()
                .system(s -> s.text(prompt))
                .user("请执行此任务并给出结果。")
                .options(OpenAiChatOptions.builder().model(model).temperature(0.5).build())
                .stream().content()
                .map(chunk -> {
                    fullResponse.append(chunk);
                    return ServerSentEvent.<String>builder()
                            .event("step_thinking")
                            .data(json(Map.of("step_id", step.getStepId(), "content", chunk)))
                            .build();
                })
                .concatWith(Flux.defer(() -> {
                    String output = fullResponse.toString().trim();
                    if (step.getOutputKey() != null) {
                        outputs.put(step.getOutputKey(), output);
                    }
                    return Flux.just(ServerSentEvent.<String>builder()
                            .event("step_result")
                            .data(json(Map.of(
                                "step_id", step.getStepId(),
                                "content", output.length() > 500 ? output.substring(0, 500) : output,
                                "status", "done"
                            )))
                            .build());
                }))
                .onErrorResume(e -> {
                    log.error("[{}] LLM step {} failed: {}", sessionId, step.getStepId(), e.getMessage());
                    return Flux.just(ServerSentEvent.<String>builder()
                            .event("step_error")
                            .data(json(Map.of("step_id", step.getStepId(), "error", e.getMessage())))
                            .build());
                });
    }

    // ==================== MCP Step (TODO) ====================

    private Flux<ServerSentEvent<String>> executeMcpStep(String sessionId, AgentFlowStep step) {
        log.info("[{}] MCP step {} — 暂未连接 MCP，返回占位结果", sessionId, step.getStepId());
        String placeholder = "[MCP 工具 \"" + step.getClientName() + "\" 暂未连接，请配置 MCP 后重试]";
        if (step.getOutputKey() != null) {
            outputs.put(step.getOutputKey(), placeholder);
        }
        return Flux.just(
            ServerSentEvent.<String>builder()
                .event("step_thinking")
                .data(json(Map.of("step_id", step.getStepId(), "content", "MCP 工具暂未连接...")))
                .build(),
            ServerSentEvent.<String>builder()
                .event("step_result")
                .data(json(Map.of("step_id", step.getStepId(), "content", placeholder, "status", "done")))
                .build()
        );
    }

    // ==================== 容错 ====================

    private Flux<ServerSentEvent<String>> handleStepError(String sessionId, AgentFlowStep step,
                                                           List<AgentFlowStep> steps,
                                                           String userInput, String modelCode,
                                                           int index, String errorData, String onError) {
        log.warn("[{}] Step {} error, on_error={}", sessionId, step.getStepId(), onError);

        return switch (onError.toLowerCase()) {
            case "retry" -> {
                log.info("[{}] Retrying step {}", sessionId, step.getStepId());
                Flux<ServerSentEvent<String>> retry = switch (step.getClientType().toUpperCase()) {
                    case "MCP_TOOL" -> executeMcpStep(sessionId, step);
                    default -> executeLlmStep(sessionId, step, modelCode);
                };
                yield Flux.concat(
                    Flux.just(ServerSentEvent.<String>builder()
                        .event("step_thinking")
                        .data(json(Map.of("step_id", step.getStepId(), "content", "正在重试...")))
                        .build()),
                    retry.onErrorResume(e -> Flux.just(ServerSentEvent.<String>builder()
                        .event("step_error")
                        .data(json(Map.of("step_id", step.getStepId(), "error", "重试失败: " + e.getMessage())))
                        .build()))
                );
            }
            case "fallback_to_step" -> {
                String fallbackId = step.getFallbackStep();
                if (fallbackId == null) {
                    yield Flux.just(buildErrorEvent(step.getStepId(), "无 fallback_step 配置"));
                }
                int fallbackIndex = -1;
                for (int i = 0; i < steps.size(); i++) {
                    if (fallbackId.equals(steps.get(i).getStepId())) { fallbackIndex = i; break; }
                }
                if (fallbackIndex < 0) {
                    yield Flux.just(buildErrorEvent(step.getStepId(), "fallback_step " + fallbackId + " 不存在"));
                }
                yield executeSteps(sessionId, userInput, steps, modelCode, fallbackIndex);
            }
            default -> // abort
                Flux.just(buildErrorEvent(step.getStepId(), "步骤失败，流程终止"));
        };
    }

    // ==================== 工具方法 ====================

    private String resolvePrompt(String prompt) {
        if (prompt == null) return "请完成任务。";
        String result = prompt;
        var matcher = VAR_PATTERN.matcher(result);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String key = matcher.group(1);
            String suffix = matcher.group(2);
            String lookup = suffix != null && !suffix.isEmpty() ? key + "." + suffix : key;
            String value = outputs.getOrDefault(key,
                outputs.getOrDefault(lookup, "{{" + lookup + "}}"));
            matcher.appendReplacement(sb, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private ServerSentEvent<String> buildDoneEvent() {
        String finalAnswer = outputs.get("final") != null ? outputs.get("final")
            : outputs.values().stream().reduce((a, b) -> b).orElse("");
        return ServerSentEvent.<String>builder()
                .event("done")
                .data(json(Map.of("final_answer", finalAnswer, "total_steps", outputs.size())))
                .build();
    }

    private ServerSentEvent<String> buildErrorEvent(String stepId, String msg) {
        return ServerSentEvent.<String>builder()
                .event("done")
                .data(json(Map.of("final_answer", "[ERR] " + msg, "error", true)))
                .build();
    }

    private String json(Object obj) {
        try { return mapper.writeValueAsString(obj); }
        catch (JsonProcessingException e) { return "{}"; }
    }

    private String parseStatus(String json) {
        try {
            Map<String, Object> m = mapper.readValue(json, new TypeReference<>() {});
            return String.valueOf(m.getOrDefault("status", ""));
        } catch (Exception e) { return ""; }
    }
}

package com.nairb.ai130.agent.engine;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nairb.ai130.domain.entity.AgentFlowStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class SequentialEngine implements ExecutionEngine {

    private static final Logger log = LoggerFactory.getLogger(SequentialEngine.class);
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final Pattern VAR_PATTERN = Pattern.compile("\\{\\{(\\w+)\\.?(\\w*)\\}\\}");

    private final ChatClient primaryClient;
    private final ChatClient fallbackClient;
    private final String fallbackModel;
    public SequentialEngine(@Qualifier("chatClient") ChatClient primaryClient,
                            @Qualifier("deepseekChatClient") ChatClient fallbackClient,
                            @Value("${spring.deepseek.openai.chat.options.model:deepseek-v4-flash}")
                            String fallbackModel) {
        this.primaryClient = primaryClient;
        this.fallbackClient = fallbackClient;
        this.fallbackModel = fallbackModel;
    }

    @Override
    public Flux<ServerSentEvent<String>> execute(String sessionId,
                                                  String userInput,
                                                  List<AgentFlowStep> steps,
                                                  String modelCode) {
        return Flux.defer(() -> {
            Map<String, String> outputs = new LinkedHashMap<>();
            outputs.put("user_input", userInput != null ? userInput : "");
            return executeSteps(sessionId, userInput, steps, modelCode, 0, 0, outputs);
        });
    }

    private Flux<ServerSentEvent<String>> executeSteps(String sessionId,
                                                        String userInput,
                                                        List<AgentFlowStep> steps,
                                                        String modelCode,
                                                        int index,
                                                        int retryAttempt,
                                                        Map<String, String> outputs) {
        if (index >= steps.size()) {
            return Flux.just(buildDoneEvent(outputs));
        }

        AgentFlowStep step = steps.get(index);
        int retryLimit = retryLimit(step);
        log.info("[{}] Executing step {}/{}: {} [{}], retry={}/{}",
                sessionId, index + 1, steps.size(), step.getStepId(), step.getClientType(),
                retryAttempt, retryLimit);

        Flux<ServerSentEvent<String>> startEvent = Flux.just(
                ServerSentEvent.<String>builder()
                        .event("step_start")
                        .data(json(Map.of(
                                "step_id", step.getStepId(),
                                "client_type", step.getClientType(),
                                "client_name", step.getClientName() != null ? step.getClientName() : "",
                                "retry_attempt", retryAttempt,
                                "retry_limit", retryLimit)))
                        .build());

        Flux<ServerSentEvent<String>> execution = executeStep(sessionId, step, modelCode, outputs);

        return Flux.concat(startEvent, execution)
                .concatMap(event -> {
                    if ("step_result".equals(event.event()) && "done".equals(parseStatus(event.data()))) {
                        return Flux.concat(
                                Flux.just(event),
                                executeSteps(sessionId, userInput, steps, modelCode, index + 1, 0, outputs));
                    }
                    if ("step_error".equals(event.event())) {
                        return handleStepError(
                                sessionId, step, steps, userInput, modelCode, index, retryAttempt, outputs);
                    }
                    return Flux.just(event);
                });
    }

    private Flux<ServerSentEvent<String>> executeStep(String sessionId,
                                                       AgentFlowStep step,
                                                       String modelCode,
                                                       Map<String, String> outputs) {
        return switch (step.getClientType().toUpperCase()) {
            case "MCP_TOOL" -> executeMcpStep(sessionId, step, outputs);
            default -> executeLlmStep(sessionId, step, modelCode, outputs);
        };
    }

    private Flux<ServerSentEvent<String>> executeLlmStep(String sessionId,
                                                          AgentFlowStep step,
                                                          String modelCode,
                                                          Map<String, String> outputs) {
        String prompt = resolvePrompt(step.getStepPrompt(), outputs);
        String model = modelCode != null && !modelCode.isEmpty() ? modelCode : "qwen-plus";
        ChatClient client = model.toLowerCase().startsWith("deepseek-") ? fallbackClient : primaryClient;
        StringBuilder fullResponse = new StringBuilder();

        return client.prompt()
                .system(s -> s.text(prompt))
                .user("Execute this step and provide its result.")
                .options(OpenAiChatOptions.builder().model(model).temperature(0.5).build())
                .stream()
                .content()
                .map(chunk -> {
                    fullResponse.append(chunk);
                    return ServerSentEvent.<String>builder()
                            .event("step_thinking")
                            .data(json(Map.of("step_id", step.getStepId(), "content", chunk)))
                            .build();
                })
                .concatWith(Flux.defer(() -> {
                    String output = fullResponse.toString().trim();
                    storeOutput(step, output, outputs);
                    return Flux.just(ServerSentEvent.<String>builder()
                            .event("step_result")
                            .data(json(Map.of(
                                    "step_id", step.getStepId(),
                                    "content", truncate(output, 500),
                                    "status", "done")))
                            .build());
                }))
                .onErrorResume(e -> {
                    log.error("[{}] LLM step {} failed: {}", sessionId, step.getStepId(), e.getMessage());
                    return Flux.just(buildStepErrorEvent(step.getStepId(), e.getMessage()));
                });
    }

    private Flux<ServerSentEvent<String>> executeMcpStep(String sessionId,
                                                         AgentFlowStep step,
                                                         Map<String, String> outputs) {
        log.info("[{}] MCP step {} is not connected yet", sessionId, step.getStepId());
        String placeholder = "[MCP tool \"" + step.getClientName() + "\" is not connected]";
        storeOutput(step, placeholder, outputs);
        return Flux.just(
                ServerSentEvent.<String>builder()
                        .event("step_thinking")
                        .data(json(Map.of("step_id", step.getStepId(), "content", "Calling MCP tool...")))
                        .build(),
                ServerSentEvent.<String>builder()
                        .event("step_result")
                        .data(json(Map.of(
                                "step_id", step.getStepId(),
                                "content", placeholder,
                                "status", "done")))
                        .build());
    }

    private Flux<ServerSentEvent<String>> handleStepError(String sessionId,
                                                           AgentFlowStep step,
                                                           List<AgentFlowStep> steps,
                                                           String userInput,
                                                           String modelCode,
                                                           int index,
                                                           int retryAttempt,
                                                           Map<String, String> outputs) {
        String onError = step.getOnError() != null ? step.getOnError().toLowerCase() : "abort";
        log.warn("[{}] Step {} error, on_error={}, retry={}/{}",
                sessionId, step.getStepId(), onError, retryAttempt, retryLimit(step));

        return switch (onError) {
            case "retry" -> retryStep(
                    sessionId, step, steps, userInput, modelCode, index, retryAttempt, outputs);
            case "fallback_to_step" -> fallbackToStep(
                    sessionId, step, steps, userInput, modelCode, outputs);
            default -> Flux.just(buildErrorEvent(step.getStepId(), "Step failed; workflow aborted"));
        };
    }

    private Flux<ServerSentEvent<String>> retryStep(String sessionId,
                                                     AgentFlowStep step,
                                                     List<AgentFlowStep> steps,
                                                     String userInput,
                                                     String modelCode,
                                                     int index,
                                                     int retryAttempt,
                                                     Map<String, String> outputs) {
        int retryLimit = retryLimit(step);
        if (retryAttempt >= retryLimit) {
            return Flux.just(buildErrorEvent(
                    step.getStepId(), "Step failed after " + retryLimit + " retries"));
        }

        int nextAttempt = retryAttempt + 1;
        log.info("[{}] Retrying step {} ({}/{})",
                sessionId, step.getStepId(), nextAttempt, retryLimit);

        return Flux.concat(
                Flux.just(ServerSentEvent.<String>builder()
                        .event("step_thinking")
                        .data(json(Map.of(
                                "step_id", step.getStepId(),
                                "content", "Retrying (" + nextAttempt + "/" + retryLimit + ")...")))
                        .build()),
                executeSteps(sessionId, userInput, steps, modelCode, index, nextAttempt, outputs));
    }

    private Flux<ServerSentEvent<String>> fallbackToStep(String sessionId,
                                                          AgentFlowStep step,
                                                          List<AgentFlowStep> steps,
                                                          String userInput,
                                                          String modelCode,
                                                          Map<String, String> outputs) {
        String fallbackId = step.getFallbackStep();
        if (fallbackId == null || fallbackId.isBlank()) {
            return Flux.just(buildErrorEvent(step.getStepId(), "No fallback step configured"));
        }

        for (int i = 0; i < steps.size(); i++) {
            if (fallbackId.equals(steps.get(i).getStepId())) {
                return executeSteps(sessionId, userInput, steps, modelCode, i, 0, outputs);
            }
        }
        return Flux.just(buildErrorEvent(step.getStepId(), "Fallback step not found: " + fallbackId));
    }

    private int retryLimit(AgentFlowStep step) {
        return step.getRetryLimit() != null ? Math.max(0, step.getRetryLimit()) : 2;
    }

    private void storeOutput(AgentFlowStep step, String output, Map<String, String> outputs) {
        outputs.put(step.getStepId(), output);
        if (step.getOutputKey() != null && !step.getOutputKey().isBlank()) {
            outputs.put(step.getOutputKey(), output);
        }
    }

    private String resolvePrompt(String prompt, Map<String, String> outputs) {
        if (prompt == null) return "Complete the configured task.";
        Matcher matcher = VAR_PATTERN.matcher(prompt);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String key = matcher.group(1);
            String suffix = matcher.group(2);
            String lookup = suffix != null && !suffix.isEmpty() ? key + "." + suffix : key;
            String value = outputs.getOrDefault(key, outputs.getOrDefault(lookup, "{{" + lookup + "}}"));
            matcher.appendReplacement(result, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private ServerSentEvent<String> buildDoneEvent(Map<String, String> outputs) {
        String finalAnswer = outputs.get("final") != null
                ? outputs.get("final")
                : outputs.values().stream().reduce((a, b) -> b).orElse("");
        return ServerSentEvent.<String>builder()
                .event("done")
                .data(json(Map.of("final_answer", finalAnswer, "total_steps", outputs.size())))
                .build();
    }

    private ServerSentEvent<String> buildStepErrorEvent(String stepId, String message) {
        return ServerSentEvent.<String>builder()
                .event("step_error")
                .data(json(Map.of("step_id", stepId, "error", message != null ? message : "Unknown error")))
                .build();
    }

    private ServerSentEvent<String> buildErrorEvent(String stepId, String message) {
        return ServerSentEvent.<String>builder()
                .event("done")
                .data(json(Map.of(
                        "step_id", stepId,
                        "final_answer", "[ERR] " + message,
                        "error", true)))
                .build();
    }

    private String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private String parseStatus(String value) {
        try {
            Map<String, Object> parsed = mapper.readValue(value, new TypeReference<>() {});
            return String.valueOf(parsed.getOrDefault("status", ""));
        } catch (Exception e) {
            return "";
        }
    }
}

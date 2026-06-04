package com.nairb.ai130.agent.engine;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nairb.ai130.app.agent.TemplateLoader;
import com.nairb.ai130.app.service.McpToolLoader;
import com.nairb.ai130.domain.agent.AgentExecutionOptions;
import com.nairb.ai130.domain.agent.ExecuteContext;
import com.nairb.ai130.domain.entity.AgentFlowStep;
import com.nairb.ai130.domain.entity.AgentNodeResult;
import com.nairb.ai130.domain.repository.AgentExecutionLogRepository;
import com.nairb.ai130.domain.repository.AgentNodeResultRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class ReactEngine implements ExecutionEngine {

    private static final Logger log = LoggerFactory.getLogger(ReactEngine.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ChatClient primaryClient;
    private final ChatClient fallbackClient;
    private final TemplateLoader templateLoader;
    private final McpToolLoader mcpToolLoader;
    private final AgentNodeResultRepository nodeResultRepository;
    private final AgentExecutionLogRepository executionLogRepository;
    private final String primaryModel;
    private final String fallbackModel;
    private final int defaultMaxPace;

    public ReactEngine(@Qualifier("chatClient") ChatClient primaryClient,
                       @Qualifier("deepseekChatClient") ChatClient fallbackClient,
                       TemplateLoader templateLoader,
                       McpToolLoader mcpToolLoader,
                       AgentNodeResultRepository nodeResultRepository,
                       AgentExecutionLogRepository executionLogRepository,
                       @Value("${spring.ai.openai.chat.options.model:qwen-plus}") String primaryModel,
                       @Value("${spring.deepseek.openai.chat.options.model:deepseek-v4-flash}") String fallbackModel,
                       @Value("${agent.react.max-pace:10}") int defaultMaxPace) {
        this.primaryClient = primaryClient;
        this.fallbackClient = fallbackClient;
        this.templateLoader = templateLoader;
        this.mcpToolLoader = mcpToolLoader;
        this.nodeResultRepository = nodeResultRepository;
        this.executionLogRepository = executionLogRepository;
        this.primaryModel = primaryModel;
        this.fallbackModel = fallbackModel;
        this.defaultMaxPace = clampMaxPace(defaultMaxPace);
    }

    @Override
    public String strategy() {
        return "react";
    }

    @Override
    public Flux<ServerSentEvent<String>> execute(String sessionId, String userInput,
                                                  List<AgentFlowStep> steps, String modelCode) {
        return execute(sessionId, userInput, steps, modelCode,
                new AgentExecutionOptions(null, 5, defaultMaxPace));
    }

    @Override
    public Flux<ServerSentEvent<String>> execute(String sessionId, String userInput,
                                                  List<AgentFlowStep> steps, String modelCode,
                                                  AgentExecutionOptions options) {
        AgentExecutionOptions resolved = options != null
                ? options : new AgentExecutionOptions(null, 5, defaultMaxPace);
        return Flux.defer(() -> {
            ExecuteContext context = new ExecuteContext();
            context.setSessionId(sessionId);
            context.setExecutionLogId(resolved.executionLogId());
            context.setUserInput(userInput != null ? userInput : "");
            context.setStrategy(strategy());
            context.setMaxPace(resolved.maxPace());
            return observe(context, steps != null ? steps : List.of(), modelCode)
                    .onErrorResume(error -> Flux.concat(
                            Flux.just(errorEvent(context, error)),
                            Flux.error(error)));
        });
    }

    private Flux<ServerSentEvent<String>> observe(ExecuteContext context,
                                                   List<AgentFlowStep> steps,
                                                   String modelCode) {
        context.setLastAnalysis(null);
        context.setLastResult(null);
        context.setLastVerdict(null);
        return streamingExecute("observer", context, steps, modelCode)
                .concatWith(Flux.defer(() -> {
                    String verdict = context.getLastVerdict();
                    int pace = context.getPace();
                    if ("FAIL".equals(verdict)) {
                        return Flux.error(new IllegalStateException(
                                "Observer stopped execution at pace " + pace));
                    }
                    if ("COMPLETED".equals(verdict) || pace >= context.getMaxPace()) {
                        return evaluate(context, steps, modelCode);
                    }
                    return reason(context, steps, modelCode);
                }));
    }

    private Flux<ServerSentEvent<String>> reason(ExecuteContext context,
                                                  List<AgentFlowStep> steps,
                                                  String modelCode) {
        return streamingExecute("reasoner", context, steps, modelCode)
                .concatWith(Flux.defer(() -> act(context, steps, modelCode)));
    }

    private Flux<ServerSentEvent<String>> act(ExecuteContext context,
                                               List<AgentFlowStep> steps,
                                               String modelCode) {
        return streamingExecute("actor", context, steps, modelCode)
                .concatWith(Flux.defer(() -> {
                    appendPaceHistory(context);
                    persistResults(context);
                    context.setPace(context.getPace() + 1);
                    return observe(context, steps, modelCode);
                }));
    }

    private Flux<ServerSentEvent<String>> evaluate(ExecuteContext context,
                                                    List<AgentFlowStep> steps,
                                                    String modelCode) {
        return streamingExecute("evaluator", context, steps, modelCode)
                .concatWith(Flux.defer(() -> {
                    int totalPaces = Math.max(0, Math.min(context.getPace() - 1, context.getMaxPace()));
                    persistResults(context);
                    return Flux.just(event("done", Map.of(
                            "final_answer", context.getLastAnalysis(),
                            "total_paces", totalPaces,
                            "verdict", context.getLastVerdict())));
                }));
    }

    /**
     * 流式执行一个角色：开始事件 → 逐 token 推送 → 结果事件。
     */
    private Flux<ServerSentEvent<String>> streamingExecute(String role,
                                                            ExecuteContext context,
                                                            List<AgentFlowStep> steps,
                                                            String modelCode) {
        String prompt = buildRoleInput(role, context);
        int pace = context.getPace();
        long startedAt = System.nanoTime();
        context.setCurrentRole(role);

        StringBuilder full = new StringBuilder();
        Flux<String> tokenStream = streamRoleTokens(role, context, steps, modelCode);

        return Flux.concat(
                Flux.just(startEvent(role, pace)),
                tokenStream.map(chunk -> {
                    full.append(chunk);
                    return event("step_thinking", Map.of(
                            "role", role, "pace", pace,
                            "strategy", strategy(), "content", chunk));
                }),
                Flux.defer(() -> {
                    String output = full.toString().trim();
                    int durationMs = (int) Math.min((System.nanoTime() - startedAt) / 1_000_000, Integer.MAX_VALUE);
                    recordNode(context, role, prompt, output, durationMs);
                    String verdict = "observer".equals(role) ? parseObserverVerdict(output) : null;
                    if ("observer".equals(role)) {
                        context.setLastAnalysis(output);
                        context.setLastVerdict(verdict);
                    }
                    if ("reasoner".equals(role)) context.setLastAnalysis(output);
                    if ("actor".equals(role)) context.setLastResult(output);
                    if ("evaluator".equals(role)) context.setLastAnalysis(output);
                    return Flux.just(resultEvent(role, pace, output, verdict));
                })
        );
    }

    protected Flux<String> streamRoleTokens(String role,
                                            ExecuteContext context,
                                            List<AgentFlowStep> steps,
                                            String modelCode) {
        String systemPrompt = templateLoader.load(strategy(), role);
        String prompt = buildRoleInput(role, context);
        String requestedModel = modelCode != null && !modelCode.isBlank() ? modelCode : primaryModel;
        boolean fallbackFirst = requestedModel.toLowerCase(Locale.ROOT).startsWith("deepseek-");
        ChatClient firstClient = fallbackFirst ? fallbackClient : primaryClient;
        List<FunctionToolCallback<String, String>> tools = "actor".equals(role)
                ? loadActorTools(steps) : List.of();

        AtomicBoolean emitted = new AtomicBoolean(false);
        return streamTokens(firstClient, requestedModel, systemPrompt, prompt,
                context.getSessionId(), tools)
                .doOnNext(ignored -> emitted.set(true))
                .onErrorResume(error -> {
                    if (fallbackFirst) return Flux.error(error);
                    if (emitted.get()) {
                        log.warn("[{}] {} primary failed after streaming started; skip fallback to avoid mixed output",
                                context.getSessionId(), role);
                        return Flux.error(error);
                    }
                    log.warn("[{}] {} primary failed, falling back", context.getSessionId(), role);
                    return streamTokens(fallbackClient, fallbackModel, systemPrompt, prompt,
                            context.getSessionId(), List.of());
                });
    }

    private Flux<String> streamTokens(ChatClient client, String model, String systemPrompt,
                                       String userPrompt, String sessionId,
                                       List<FunctionToolCallback<String, String>> tools) {
        var spec = client.prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .options(OpenAiChatOptions.builder().model(model).temperature(0.4).build())
                .advisors(a -> a.param("conversation_id", sessionId).param("retrieve_size", 10));
        if (!tools.isEmpty()) spec = spec.tools(tools.toArray(new FunctionToolCallback[0]));
        return spec.stream().content();
    }

    private String buildRoleInput(String role, ExecuteContext context) {
        String history = context.getExecutionHistory().isEmpty()
                ? "(no previous actions)" : context.getExecutionHistory().toString();
        return switch (role) {
            case "observer" -> """
                    Goal: %s
                    Action history: %s
                    Current pace: %d of %d
                    """.formatted(context.getUserInput(), history, context.getPace(), context.getMaxPace());
            case "reasoner" -> """
                    Goal: %s
                    Latest observation: %s
                    Action history: %s
                    Plan exactly one next action.
                    """.formatted(context.getUserInput(), context.getLastAnalysis(), history);
            case "actor" -> """
                    Goal: %s
                    Execute only this next action: %s
                    Action history: %s
                    """.formatted(context.getUserInput(), context.getLastAnalysis(), history);
            case "evaluator" -> """
                    Goal: %s
                    Full action history: %s
                    Observer verdict: %s
                    Produce the final user-facing result.
                    """.formatted(context.getUserInput(), history, context.getLastVerdict());
            default -> throw new IllegalArgumentException("Unsupported ReAct role: " + role);
        };
    }

    private List<FunctionToolCallback<String, String>> loadActorTools(List<AgentFlowStep> steps) {
        return mcpToolLoader.loadTools(steps.stream()
                .filter(step -> "MCP_TOOL".equalsIgnoreCase(step.getClientType()))
                .map(AgentFlowStep::getClientId)
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .toList());
    }

    private void appendPaceHistory(ExecuteContext context) {
        context.getExecutionHistory()
                .append("## Pace ").append(context.getPace()).append('\n')
                .append("Next action:\n").append(context.getLastAnalysis()).append('\n')
                .append("Action result:\n").append(context.getLastResult()).append("\n\n");
    }

    private void recordNode(ExecuteContext context, String role, String prompt,
                            String output, int durationMs) {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("pace", String.valueOf(context.getPace()));
        result.put("role", role);
        result.put("output", output);
        context.getNodeResults().add(result);
        if (context.getExecutionLogId() != null && nodeResultRepository != null) {
            AgentNodeResult entity = new AgentNodeResult();
            entity.setLogId(context.getExecutionLogId());
            entity.setRound(context.getPace());
            entity.setRole(role);
            entity.setPrompt(prompt);
            entity.setOutput(output);
            entity.setDurationMs(durationMs);
            nodeResultRepository.insert(entity);
        }
    }

    private void persistResults(ExecuteContext context) {
        if (context.getExecutionLogId() == null || executionLogRepository == null) return;
        try {
            executionLogRepository.updateStepResults(
                    context.getExecutionLogId(), MAPPER.writeValueAsString(context.getNodeResults()));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize ReAct results", e);
        }
    }

    static String parseObserverVerdict(String output) {
        if (output == null || output.isBlank()) return "CONTINUE";
        int start = output.indexOf('{');
        int end = output.lastIndexOf('}');
        if (start >= 0 && end > start) {
            try {
                JsonNode node = MAPPER.readTree(output.substring(start, end + 1));
                String verdict = normalizeVerdict(node.path("verdict").asText());
                if (verdict != null) return verdict;
            } catch (Exception ignored) {
                // Text fallback below.
            }
        }
        for (String line : output.lines().toList()) {
            String verdict = normalizeVerdict(line);
            if (verdict != null) return verdict;
        }
        return "CONTINUE";
    }

    private static String normalizeVerdict(String value) {
        if (value == null) return null;
        String normalized = value.stripLeading().toUpperCase(Locale.ROOT);
        if (normalized.startsWith("COMPLETED")) return "COMPLETED";
        if (normalized.startsWith("CONTINUE")) return "CONTINUE";
        if (normalized.startsWith("FAIL")) return "FAIL";
        return null;
    }

    private ServerSentEvent<String> startEvent(String role, int pace) {
        return event("step_start", Map.of("role", role, "pace", pace, "strategy", strategy()));
    }

    private ServerSentEvent<String> resultEvent(String role, int pace, String output, String verdict) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("role", role);
        data.put("pace", pace);
        data.put("output", output);
        data.put("status", "done");
        if (verdict != null) data.put("verdict", verdict);
        return event("step_result", data);
    }

    private ServerSentEvent<String> errorEvent(ExecuteContext context, Throwable error) {
        return event("step_error", Map.of(
                "role", context.getCurrentRole() != null ? context.getCurrentRole() : "observer",
                "pace", context.getPace(),
                "strategy", strategy(),
                "error", error.getMessage() != null ? error.getMessage() : "ReAct execution failed"));
    }

    private ServerSentEvent<String> event(String name, Object data) {
        try {
            return ServerSentEvent.<String>builder().event(name)
                    .data(MAPPER.writeValueAsString(data)).build();
        } catch (JsonProcessingException e) {
            return ServerSentEvent.<String>builder().event(name).data("{}").build();
        }
    }

    private int clampMaxPace(int value) {
        return Math.max(1, Math.min(value, 50));
    }
}

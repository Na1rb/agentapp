package com.nairb.ai130.agent.engine;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nairb.ai130.app.agent.TemplateLoader;
import com.nairb.ai130.app.service.McpToolLoader;
import com.nairb.ai130.domain.agent.ExecuteContext;
import com.nairb.ai130.domain.agent.AgentExecutionOptions;
import com.nairb.ai130.domain.entity.AgentNodeResult;
import com.nairb.ai130.domain.entity.AgentFlowStep;
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
public class LoopEngine implements ExecutionEngine {

    private static final Logger log = LoggerFactory.getLogger(LoopEngine.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ChatClient primaryClient;
    private final ChatClient fallbackClient;
    private final TemplateLoader templateLoader;
    private final McpToolLoader mcpToolLoader;
    private final AgentNodeResultRepository nodeResultRepository;
    private final AgentExecutionLogRepository executionLogRepository;
    private final String primaryModel;
    private final String fallbackModel;
    private final int defaultMaxRound;

    public LoopEngine(@Qualifier("chatClient") ChatClient primaryClient,
                      @Qualifier("deepseekChatClient") ChatClient fallbackClient,
                      TemplateLoader templateLoader,
                      McpToolLoader mcpToolLoader,
                      AgentNodeResultRepository nodeResultRepository,
                      AgentExecutionLogRepository executionLogRepository,
                      @Value("${spring.ai.openai.chat.options.model:qwen-plus}") String primaryModel,
                      @Value("${spring.deepseek.openai.chat.options.model:deepseek-v4-flash}") String fallbackModel,
                      @Value("${agent.loop.max-round:5}") int defaultMaxRound) {
        this.primaryClient = primaryClient;
        this.fallbackClient = fallbackClient;
        this.templateLoader = templateLoader;
        this.mcpToolLoader = mcpToolLoader;
        this.nodeResultRepository = nodeResultRepository;
        this.executionLogRepository = executionLogRepository;
        this.primaryModel = primaryModel;
        this.fallbackModel = fallbackModel;
        this.defaultMaxRound = clampMaxRound(defaultMaxRound);
    }

    @Override
    public String strategy() {
        return "loop";
    }

    @Override
    public Flux<ServerSentEvent<String>> execute(String sessionId,
                                                  String userInput,
                                                  List<AgentFlowStep> steps,
                                                  String modelCode) {
        return execute(sessionId, userInput, steps, modelCode, (Long) null);
    }

    @Override
    public Flux<ServerSentEvent<String>> execute(String sessionId,
                                                  String userInput,
                                                  List<AgentFlowStep> steps,
                                                  String modelCode,
                                                  AgentExecutionOptions options) {
        AgentExecutionOptions resolved = options != null
                ? options
                : new AgentExecutionOptions(null, defaultMaxRound, 10);
        return executeInternal(sessionId, userInput, steps, modelCode, resolved.executionLogId(), resolved.maxRound());
    }

    @Override
    public Flux<ServerSentEvent<String>> execute(String sessionId,
                                                  String userInput,
                                                  List<AgentFlowStep> steps,
                                                  String modelCode,
                                                  Long executionLogId) {
        return executeInternal(sessionId, userInput, steps, modelCode, executionLogId, defaultMaxRound);
    }

    private Flux<ServerSentEvent<String>> executeInternal(String sessionId,
                                                           String userInput,
                                                           List<AgentFlowStep> steps,
                                                           String modelCode,
                                                           Long executionLogId,
                                                           int maxRound) {
        return Flux.defer(() -> {
            ExecuteContext context = new ExecuteContext();
            context.setSessionId(sessionId);
            context.setExecutionLogId(executionLogId);
            context.setUserInput(userInput != null ? userInput : "");
            context.setStrategy(strategy());
            context.setMaxRound(clampMaxRound(maxRound));

            log.info("[{}] Loop Agent started, maxRound={}", sessionId, context.getMaxRound());
            return executeRound(context, safeSteps(steps), modelCode)
                    .onErrorResume(error -> {
                        log.error("[{}] Loop Agent failed at round {}", sessionId, context.getRound(), error);
                        return Flux.concat(
                                Flux.just(errorEvent(context, error)),
                                Flux.error(error));
                    });
        });
    }

    private Flux<ServerSentEvent<String>> executeRound(ExecuteContext context,
                                                        List<AgentFlowStep> steps,
                                                        String modelCode) {
        context.setLastAnalysis(null);
        context.setLastResult(null);
        context.setLastVerdict(null);
        return streamingExecute("analyzer", context, steps, modelCode)
                .concatWith(Flux.defer(() -> executePerformer(context, steps, modelCode)));
    }

    private Flux<ServerSentEvent<String>> executePerformer(ExecuteContext context,
                                                            List<AgentFlowStep> steps,
                                                            String modelCode) {
        return streamingExecute("performer", context, steps, modelCode)
                .concatWith(Flux.defer(() -> executeSupervisor(context, steps, modelCode)));
    }

    private Flux<ServerSentEvent<String>> executeSupervisor(ExecuteContext context,
                                                             List<AgentFlowStep> steps,
                                                             String modelCode) {
        return streamingExecute("supervisor", context, steps, modelCode)
                .concatWith(Flux.defer(() -> {
                    String verdict = context.getLastVerdict();
                    int round = context.getRound();
                    if ("PASS".equals(verdict) || round >= context.getMaxRound()) {
                        return executeSummarizer(context, steps, modelCode);
                    }
                    context.setRound(round + 1);
                    return executeRound(context, steps, modelCode);
                }));
    }

    private Flux<ServerSentEvent<String>> executeSummarizer(ExecuteContext context,
                                                             List<AgentFlowStep> steps,
                                                             String modelCode) {
        return streamingExecute("summarizer", context, steps, modelCode)
                .concatWith(Flux.defer(() -> {
                    String finalAnswer = context.getLastAnalysis();
                    int totalRounds = Math.min(context.getRound(), context.getMaxRound());
                    persistRoundResults(context);
                    return Flux.just(doneEvent(finalAnswer, totalRounds, context.getLastVerdict()));
                }));
    }

    /**
     * 流式执行一个角色：开始事件 → 逐 token 推送 → 结果事件。
     * 每个 token 以 step_thinking SSE 事件推送，实现真实流式输出。
     */
    private Flux<ServerSentEvent<String>> streamingExecute(String role,
                                                            ExecuteContext context,
                                                            List<AgentFlowStep> steps,
                                                            String modelCode) {
        String prompt = buildRoleInput(role, context);
        int round = context.getRound();
        long startedAt = System.nanoTime();
        context.setCurrentRole(role);

        StringBuilder full = new StringBuilder();
        Flux<String> tokenStream = streamRoleTokens(role, context, steps, modelCode);

        return Flux.concat(
                Flux.just(startEvent(role, round)),
                tokenStream.map(chunk -> {
                    full.append(chunk);
                    return event("step_thinking", Map.of(
                            "role", role, "round", round,
                            "strategy", strategy(), "content", chunk));
                }),
                Flux.defer(() -> {
                    String output = full.toString().trim();
                    int durationMs = (int) Math.min((System.nanoTime() - startedAt) / 1_000_000, Integer.MAX_VALUE);
                    recordNode(context, role, prompt, output, durationMs);
                    String verdict = "supervisor".equals(role) ? parseVerdict(output) : null;
                    if ("analyzer".equals(role)) context.setLastAnalysis(output);
                    if ("performer".equals(role)) context.setLastResult(output);
                    if ("supervisor".equals(role)) {
                        context.setLastVerdict(verdict);
                        appendRoundHistory(context, output);
                        persistRoundResults(context);
                    }
                    if ("summarizer".equals(role)) context.setLastAnalysis(output);
                    return Flux.just(resultEvent(role, round, output, verdict));
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
        boolean useFallbackFirst = requestedModel.toLowerCase(Locale.ROOT).startsWith("deepseek-");
        ChatClient firstClient = useFallbackFirst ? fallbackClient : primaryClient;
        ChatClient secondClient = useFallbackFirst ? null : fallbackClient;
        List<FunctionToolCallback<String, String>> tools = "performer".equals(role)
                ? loadPerformerTools(steps) : List.of();

        AtomicBoolean emitted = new AtomicBoolean(false);
        Flux<String> tokenStream = streamTokens(firstClient, requestedModel, systemPrompt, prompt,
                context.getSessionId(), tools)
                .doOnNext(ignored -> emitted.set(true));
        if (secondClient == null) {
            return tokenStream;
        }
        return tokenStream.onErrorResume(error -> {
            if (emitted.get()) {
                log.warn("[{}] {} primary failed after streaming started; skip fallback to avoid mixed output",
                        context.getSessionId(), role);
                return Flux.error(error);
            }
            log.warn("[{}] {} primary failed, falling back", context.getSessionId(), role);
            return streamTokens(secondClient, fallbackModel, systemPrompt, prompt,
                    context.getSessionId(), List.of());
        });
    }

    private Flux<String> streamTokens(ChatClient client, String model, String systemPrompt,
                                       String userPrompt, String sessionId,
                                       List<FunctionToolCallback<String, String>> tools) {
        var spec = client.prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .options(OpenAiChatOptions.builder().model(model).temperature(0.5).build())
                .advisors(a -> a.param("conversation_id", sessionId).param("retrieve_size", 10));
        if (!tools.isEmpty()) {
            spec = spec.tools(tools.toArray(new FunctionToolCallback[0]));
        }
        return spec.stream().content();
    }

    private String buildRoleInput(String role, ExecuteContext context) {
        String history = context.getExecutionHistory().isEmpty()
                ? "(no previous rounds)"
                : context.getExecutionHistory().toString();
        return switch (role) {
            case "analyzer" -> """
                    Original task:
                    %s

                    Execution history:
                    %s

                    Current round: %d of %d
                    """.formatted(context.getUserInput(), history, context.getRound(), context.getMaxRound());
            case "performer" -> """
                    Original task:
                    %s

                    Current-round strategy:
                    %s

                    Execution history:
                    %s
                    """.formatted(context.getUserInput(), context.getLastAnalysis(), history);
            case "supervisor" -> """
                    Original task:
                    %s

                    Current-round strategy:
                    %s

                    Current-round result:
                    %s

                    Previous execution history:
                    %s
                    """.formatted(context.getUserInput(), context.getLastAnalysis(), context.getLastResult(), history);
            case "summarizer" -> """
                    Original task:
                    %s

                    Full execution history:
                    %s

                    Final supervisor verdict: %s
                    """.formatted(context.getUserInput(), history, context.getLastVerdict());
            default -> throw new IllegalArgumentException("Unsupported Loop role: " + role);
        };
    }

    private List<FunctionToolCallback<String, String>> loadPerformerTools(List<AgentFlowStep> steps) {
        List<String> toolNames = steps.stream()
                .filter(step -> "MCP_TOOL".equalsIgnoreCase(step.getClientType()))
                .map(AgentFlowStep::getClientId)
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .toList();
        return mcpToolLoader.loadTools(toolNames);
    }

    private void appendRoundHistory(ExecuteContext context, String supervisorOutput) {
        context.getExecutionHistory()
                .append("## Round ").append(context.getRound()).append('\n')
                .append("Analysis:\n").append(context.getLastAnalysis()).append('\n')
                .append("Result:\n").append(context.getLastResult()).append('\n')
                .append("Supervisor:\n").append(supervisorOutput).append("\n\n");
    }

    private void recordNode(ExecuteContext context,
                            String role,
                            String prompt,
                            String output,
                            int durationMs) {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("round", String.valueOf(context.getRound()));
        result.put("role", role);
        result.put("output", output);
        context.getNodeResults().add(result);

        if (context.getExecutionLogId() != null && nodeResultRepository != null) {
            AgentNodeResult entity = new AgentNodeResult();
            entity.setLogId(context.getExecutionLogId());
            entity.setRound(context.getRound());
            entity.setRole(role);
            entity.setPrompt(prompt);
            entity.setOutput(output);
            entity.setDurationMs(durationMs);
            nodeResultRepository.insert(entity);
        }
    }

    private void persistRoundResults(ExecuteContext context) {
        if (context.getExecutionLogId() == null || executionLogRepository == null) {
            return;
        }
        try {
            executionLogRepository.updateStepResults(
                    context.getExecutionLogId(),
                    MAPPER.writeValueAsString(context.getNodeResults()));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize Loop execution results", e);
        }
    }

    static String parseVerdict(String output) {
        if (output == null || output.isBlank()) {
            return "OPTIMIZE";
        }
        String json = extractJson(output);
        if (json != null) {
            try {
                JsonNode node = MAPPER.readTree(json);
                String verdict = normalizeVerdict(node.path("verdict").asText());
                if (verdict != null) return verdict;
            } catch (Exception e) {
                log.debug("Supervisor JSON parse failed, falling back to text verdict: {}", e.getMessage());
            }
        }
        for (String line : output.lines().toList()) {
            String verdict = normalizeVerdict(line);
            if (verdict != null) return verdict;
        }
        return "OPTIMIZE";
    }

    private static String extractJson(String output) {
        int start = output.indexOf('{');
        int end = output.lastIndexOf('}');
        return start >= 0 && end > start ? output.substring(start, end + 1) : null;
    }

    private static String normalizeVerdict(String value) {
        if (value == null) return null;
        String normalized = value.stripLeading().toUpperCase(Locale.ROOT);
        if (normalized.startsWith("PASS")) return "PASS";
        if (normalized.startsWith("FAIL")) return "FAIL";
        if (normalized.startsWith("OPTIMIZE")) return "OPTIMIZE";
        return null;
    }

    private ServerSentEvent<String> startEvent(String role, int round) {
        return event("step_start", Map.of("role", role, "round", round, "strategy", strategy()));
    }

    private ServerSentEvent<String> resultEvent(String role, int round, String output, String verdict) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("role", role);
        data.put("round", round);
        data.put("output", output);
        data.put("status", "done");
        if (verdict != null) data.put("verdict", verdict);
        return event("step_result", data);
    }

    private ServerSentEvent<String> doneEvent(String finalAnswer, int totalRounds, String verdict) {
        return event("done", Map.of(
                "final_answer", finalAnswer,
                "total_rounds", totalRounds,
                "verdict", verdict != null ? verdict : ""));
    }

    private ServerSentEvent<String> errorEvent(ExecuteContext context, Throwable error) {
        return event("step_error", Map.of(
                "role", context.getCurrentRole() != null ? context.getCurrentRole() : currentRole(context),
                "round", context.getRound(),
                "strategy", strategy(),
                "error", error.getMessage() != null ? error.getMessage() : "Loop execution failed"));
    }

    private String currentRole(ExecuteContext context) {
        if (context.getLastAnalysis() == null) return "analyzer";
        if (context.getLastResult() == null) return "performer";
        if (context.getLastVerdict() == null) return "supervisor";
        return "summarizer";
    }

    private ServerSentEvent<String> event(String name, Object data) {
        try {
            return ServerSentEvent.<String>builder()
                    .event(name)
                    .data(MAPPER.writeValueAsString(data))
                    .build();
        } catch (JsonProcessingException e) {
            return ServerSentEvent.<String>builder().event(name).data("{}").build();
        }
    }

    private List<AgentFlowStep> safeSteps(List<AgentFlowStep> steps) {
        return steps != null ? steps : List.of();
    }

    private int clampMaxRound(int maxRound) {
        return Math.max(1, Math.min(maxRound, 20));
    }
}

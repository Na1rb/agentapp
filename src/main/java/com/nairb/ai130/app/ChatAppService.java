package com.nairb.ai130.app;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nairb.ai130.api.dto.StepEvent;
import com.nairb.ai130.api.dto.StepState;
import com.nairb.ai130.app.advisor.StepOrchestrationAdvisor;
import com.nairb.ai130.app.service.McpToolLoader;
import com.nairb.ai130.app.service.StepStateManager;
import com.nairb.ai130.common.enums.StepPhase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class ChatAppService {
    private static final Logger log = LoggerFactory.getLogger(ChatAppService.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final ChatClient primaryClient;
    private final ChatClient fallbackClient;
    private final McpToolLoader mcpToolLoader;
    private final StepStateManager stepStateManager;

    @Value("${spring.ai.openai.chat.options.model:qwen-plus}")
    private String primaryModel;
    @Value("${spring.deepseek.openai.chat.options.model:deepseek-chat}")
    private String fallbackModel;

    public ChatAppService(@Qualifier("chatClient") ChatClient primaryClient,
                          @Qualifier("deepseekChatClient") ChatClient fallbackClient,
                          McpToolLoader mcpToolLoader,
                          StepStateManager stepStateManager) {
        this.primaryClient = primaryClient;
        this.fallbackClient = fallbackClient;
        this.mcpToolLoader = mcpToolLoader;
        this.stepStateManager = stepStateManager;
    }

    // ==================== 公开方法 ====================

    // ==================== 向后兼容: 无 modelCode ====================

    /**
     * 流式对话（无工具，无 modelCode — 兜底用）。
     */
    public Flux<String> streamChat(String prompt, String sessionId) {
        return streamChat(prompt, sessionId, Collections.emptyList(), null);
    }

    // ==================== 主入口: 支持 modelCode ====================

    /**
     * 流式对话（支持动态工具注入和模型选择）。
     *
     * @param modelCode 前端选中的模型 code；为 null 时使用默认主模型
     */
    public Flux<String> streamChat(String prompt, String sessionId, List<String> toolIds, String modelCode) {
        List<FunctionToolCallback<String, String>> tools = (toolIds != null && !toolIds.isEmpty())
                ? mcpToolLoader.loadTools(toolIds)
                : Collections.emptyList();

        String resolvedModel = (modelCode != null) ? modelCode : primaryModel;
        log.info("streamChat session={}, model={}, tools={}", sessionId, resolvedModel,
                tools.stream().map(t -> t.getToolDefinition().name()).toList());

        ChatClient client = resolveClient(resolvedModel);
        Flux<String> stream = streamByModel(prompt, sessionId, resolvedModel, client, tools);

        // 如果不是默认主模型，不走降级；如果是主模型且失败，降级到 fallback
        if (!resolvedModel.equals(primaryModel)) {
            return stream.onErrorResume(e -> {
                log.warn("[{}] Model {} failed: {}, trying primary fallback", sessionId, resolvedModel, e.getMessage());
                return streamByModel(prompt, sessionId, primaryModel, primaryClient, tools)
                        .onErrorResume(e2 -> Flux.just("Service unavailable."));
            });
        }

        // 主模型失败 → 降级到 fallback
        AtomicBoolean primaryFailed = new AtomicBoolean(false);
        AtomicBoolean hasOutput = new AtomicBoolean(false);

        Flux<String> primary = stream
                .doOnNext(c -> hasOutput.set(true))
                .doOnError(e -> { primaryFailed.set(true); log.warn("Primary failed: {}", e.getMessage()); })
                .onErrorResume(e -> Flux.<String>empty());

        Flux<String> fallback = Flux.defer(() -> {
            if (primaryFailed.get() || !hasOutput.get()) {
                log.info("Fallback to [{}]", fallbackModel);
                return Flux.concat(Flux.just("\n[Primary unavailable, switched to fallback]\n"),
                        streamByModel(prompt, sessionId, fallbackModel, fallbackClient, tools));
            }
            return Flux.<String>empty();
        }).onErrorResume(e -> Flux.just("Both models unavailable."));

        return Flux.concat(primary, fallback).switchIfEmpty(Flux.just("Service unavailable."));
    }

    /**
     * 分步编排流式对话（STEP_CHECK 策略，向后兼容，无 modelCode）。
     */
    public Flux<ServerSentEvent<String>> streamChatWithSteps(String prompt, String sessionId,
                                                              List<String> toolIds) {
        return streamChatWithSteps(prompt, sessionId, toolIds, null);
    }

    /**
     * 分步编排流式对话（STEP_CHECK 策略）。
     * <p>
     * 按照 ANALYZE → EXECUTE → CHECK → LOOP 状态机分步执行，
     * 每个步骤通过 SSE 命名事件通知前端进度。
     *
     * @param prompt    用户输入
     * @param sessionId 会话 ID
     * @param toolIds   工具名称列表
     * @param modelCode 前端选中的模型 code；为 null 时使用默认主模型
     * @return SSE 事件流（step_start / step_result / step_complete / data）
     */
    public Flux<ServerSentEvent<String>> streamChatWithSteps(String prompt, String sessionId,
                                                              List<String> toolIds, String modelCode) {
        List<FunctionToolCallback<String, String>> tools = (toolIds != null && !toolIds.isEmpty())
                ? mcpToolLoader.loadTools(toolIds)
                : Collections.emptyList();

        String resolvedModel = (modelCode != null) ? modelCode : primaryModel;
        log.info("streamChatWithSteps session={}, model={}, tools={}", sessionId, resolvedModel,
                tools.stream().map(t -> t.getToolDefinition().name()).toList());

        // 初始化步骤状态
        stepStateManager.getOrInit(sessionId, "STEP_CHECK");

        // 开始编排循环
        return orchestrateStep(prompt, sessionId, tools, resolvedModel);
    }

    // ==================== 编排循环 ====================

    /** 最大递归深度（独立硬上限，防止状态机逻辑 bug 导致无限递归） */
    private static final int MAX_RECURSION_DEPTH = 10;
    /** 单次模型响应最大缓冲字符数（防 OOM） */
    private static final int MAX_RESPONSE_BUFFER = 100_000;

    /**
     * 递归编排：根据当前阶段决定执行 ANALYZE / EXECUTE / SYNTHESIZE。
     * <p>
     * 每次递归：
     * <ol>
     *   <li>读取当前状态</li>
     *   <li>发射 step_start 事件</li>
     *   <li>调用模型（ANALYZE/EXECUTE 收集完整响应；SYNTHESIZE 流式输出）</li>
     *   <li>推进状态机</li>
     *   <li>发射 step_result 事件</li>
     *   <li>判定是否需要继续循环</li>
     * </ol>
     */
    private Flux<ServerSentEvent<String>> orchestrateStep(String prompt, String sessionId,
                                                           List<FunctionToolCallback<String, String>> tools,
                                                           String modelCode) {
        return orchestrateStep(prompt, sessionId, tools, modelCode, 0);
    }

    private Flux<ServerSentEvent<String>> orchestrateStep(String prompt, String sessionId,
                                                           List<FunctionToolCallback<String, String>> tools,
                                                           String modelCode, int depth) {
        return Flux.defer(() -> {
            // 独立递归深度防护
            if (depth > MAX_RECURSION_DEPTH) {
                log.error("[{}] Max recursion depth {} exceeded, aborting", sessionId, MAX_RECURSION_DEPTH);
                stepStateManager.clear(sessionId);
                return Flux.just(ServerSentEvent.<String>builder()
                        .event("step_error")
                        .data("{\"error\":\"编排步骤过多，已终止\"}")
                        .build());
            }

            StepState state = stepStateManager.get(sessionId);
            if (state == null) {
                log.warn("[{}] State lost, clearing and notifying client", sessionId);
                stepStateManager.clear(sessionId);
                return Flux.just(ServerSentEvent.<String>builder()
                        .event("step_error")
                        .data("{\"error\":\"会话状态丢失，请重新发起请求\"}")
                        .build());
            }

            StepOrchestrationAdvisor advisor = new StepOrchestrationAdvisor(stepStateManager, sessionId);
            StepPhase currentPhase = state.getPhase();
            String phasePrompt = advisor.buildPhasePrompt();

            log.info("[{}] orchestrateStep phase={} loop={}/{} model={}",
                    sessionId, currentPhase, state.getLoopCount(), state.getMaxLoops(), modelCode);

            // 1. 发射 step_start 事件
            Flux<ServerSentEvent<String>> startEvent = Flux.just(
                    buildStepStartEvent(state)
            );

            // 2. SYNTHESIZE 阶段：流式输出最终回答
            if (currentPhase == StepPhase.SYNTHESIZE) {
                return Flux.concat(
                        startEvent,
                        streamSynthesizeAndComplete(prompt, sessionId, tools, phasePrompt, state, modelCode)
                );
            }

            // 3. ANALYZE / EXECUTE 阶段：收集完整响应 → 推进状态机
            return Flux.concat(
                    startEvent,
                    collectAndAdvance(prompt, sessionId, tools, phasePrompt, advisor, state, currentPhase, depth, modelCode)
            );
        });
    }

    /**
     * SYNTHESIZE 阶段：流式输出最终回答，完成后清理状态。
     */
    private Flux<ServerSentEvent<String>> streamSynthesizeAndComplete(
            String prompt, String sessionId, List<FunctionToolCallback<String, String>> tools,
            String phasePrompt, StepState state, String modelCode) {

        ChatClient client = resolveClient(modelCode);
        Flux<String> modelStream = streamByModelWithPrompt(
                prompt, sessionId, modelCode, client, tools, phasePrompt);

        Flux<ServerSentEvent<String>> dataEvents = modelStream
                .map(s -> ServerSentEvent.<String>builder().data(s).build());

        // 流结束后发射 step_complete 并清理状态
        Flux<ServerSentEvent<String>> completeEvent = Flux.defer(() -> {
            int totalLoops = state.getLoopCount();
            stepStateManager.clear(sessionId);
            log.info("[{}] SYNTHESIZE complete, totalLoops={}", sessionId, totalLoops);

            StepEvent complete = StepEvent.completeFrom(state);
            return Flux.just(ServerSentEvent.<String>builder()
                    .event("step_complete")
                    .data(toJson(complete))
                    .build());
        });

        return Flux.concat(dataEvents, completeEvent);
    }

    /**
     * ANALYZE / EXECUTE 阶段：收集完整模型响应，推进状态机，判定下一步。
     */
    private Flux<ServerSentEvent<String>> collectAndAdvance(
            String prompt, String sessionId, List<FunctionToolCallback<String, String>> tools,
            String phasePrompt, StepOrchestrationAdvisor advisor,
            StepState state, StepPhase currentPhase, int depth, String modelCode) {

        // ANALYZE 阶段不注入工具（只需要推理，工具会在 EXECUTE 阶段使用）
        List<FunctionToolCallback<String, String>> effectiveTools = (currentPhase == StepPhase.ANALYZE)
                ? Collections.emptyList()
                : tools;

        ChatClient stepClient = resolveClient(modelCode);
        Flux<String> modelStream = streamByModelWithPrompt(
                prompt, sessionId, modelCode, stepClient, effectiveTools, phasePrompt);

        return modelStream
                .reduce("", (accumulated, chunk) -> {
                    String combined = accumulated + chunk;
                    // 防 OOM：超过上限截断
                    if (combined.length() > MAX_RESPONSE_BUFFER) {
                        return combined.substring(0, MAX_RESPONSE_BUFFER) + "...[truncated]";
                    }
                    return combined;
                })
                .flatMapMany(fullResponse -> {

                    // 推进状态机
                    advisor.handleResponse(state, fullResponse);

                    // 构建 step_result 事件（仅 EXECUTE 阶段产生）
                    Flux<ServerSentEvent<String>> resultEvents = buildResultEvents(state, currentPhase);

                    // 读取推进后的状态
                    StepState newState = stepStateManager.get(sessionId);
                    if (newState == null) {
                        return resultEvents;
                    }

                    // CHECK 过渡：发射 step_start(CHECK) 事件（程序化检查，无模型调用）
                    Flux<ServerSentEvent<String>> checkTransition = Flux.just(
                            buildCheckStartEvent(state)
                    );

                    // 继续编排（depth+1 递进递归深度防护）
                    return Flux.concat(
                            resultEvents,
                            checkTransition,
                            orchestrateStep(prompt, sessionId, tools, modelCode, depth + 1)
                    );
                })
                .onErrorResume(e -> {
                    log.error("[{}] Error in step orchestration: {}", sessionId, e.getMessage(), e);
                    stepStateManager.clear(sessionId);
                    return Flux.just(ServerSentEvent.<String>builder()
                            .event("step_error")
                            .data("{\"error\":\"" + escapeJson(e.getMessage()) + "\"}")
                            .build());
                });
    }

    // ==================== 事件构建 ====================

    private ServerSentEvent<String> buildStepStartEvent(StepState state) {
        StepEvent event = StepEvent.startFrom(state);
        return ServerSentEvent.<String>builder()
                .event("step_start")
                .data(toJson(event))
                .build();
    }

    private ServerSentEvent<String> buildCheckStartEvent(StepState state) {
        // 模拟 CHECK 阶段的 step_start
        StepEvent event = new StepEvent();
        event.setPhase(StepPhase.CHECK.name());
        event.setMessage("正在检查执行结果...");
        event.setLoopCount(state.getLoopCount());
        return ServerSentEvent.<String>builder()
                .event("step_start")
                .data(toJson(event))
                .build();
    }

    private Flux<ServerSentEvent<String>> buildResultEvents(StepState state, StepPhase previousPhase) {
        if (previousPhase != StepPhase.EXECUTE) {
            // ANALYZE 不产生 step_result（任务列表在后续 EXECUTE 中体现）
            return Flux.empty();
        }

        // 查找最近完成的任务
        int idx = state.getCurrentTaskIndex() - 1;
        if (idx >= 0 && idx < state.getPendingTasks().size()) {
            StepState.Task task = state.getPendingTasks().get(idx);
            StepState.CompletedResult result = state.getCompletedResults().stream()
                    .filter(r -> r.getTaskId().equals(task.getId()))
                    .findFirst().orElse(null);

            StepEvent event = StepEvent.resultFrom(state, task.getId(),
                    result != null ? result.getOutput() : "");
            return Flux.just(ServerSentEvent.<String>builder()
                    .event("step_result")
                    .data(toJson(event))
                    .build());
        }
        return Flux.empty();
    }

    // ==================== 模型路由 & 调用 ====================

    /**
     * 根据模型 code 解析对应的 ChatClient。
     * <p>
     * 模型以 "deepseek-" 开头 → fallbackClient（DeepSeek API）
     * 其他（含 "qwen-"）→ primaryClient（阿里云 DashScope API）
     */
    private ChatClient resolveClient(String modelCode) {
        if (modelCode != null && modelCode.toLowerCase().startsWith("deepseek-")) {
            return fallbackClient;
        }
        return primaryClient;
    }

    /**
     * 流式模型调用（带自定义 phase prompt）。
     */
    private Flux<String> streamByModelWithPrompt(String prompt, String sessionId, String model,
                                                  ChatClient client,
                                                  List<FunctionToolCallback<String, String>> tools,
                                                  String phasePrompt) {
        String baseSystemText = "你是一个智能助手。\n1. 理解用户意图，包括同音错别字。\n2. 基于上下文回答问题。\n3. 如无相关信息则如实说明。";
        String systemText = (phasePrompt != null && !phasePrompt.isEmpty())
                ? baseSystemText + "\n\n" + phasePrompt
                : baseSystemText;

        var spec = client.prompt()
                .system(s -> s.text(systemText))
                .user(prompt)
                .options(OpenAiChatOptions.builder().model(model).temperature(0.7).build())
                .advisors(a -> a.param("conversation_id", sessionId).param("retrieve_size", 10));

        if (tools != null && !tools.isEmpty()) {
            spec = spec.tools(tools.toArray(new FunctionToolCallback[0]));
            log.debug("Injected {} tool(s) for model [{}]", tools.size(), model);
        }

        return spec.stream().content();
    }

    /**
     * 流式模型调用（基本版）。
     */
    private Flux<String> streamByModel(String prompt, String sessionId, String model,
                                        ChatClient client,
                                        List<FunctionToolCallback<String, String>> tools) {
        var spec = client.prompt()
                .system(s -> s.text("你是一个智能助手。\n1. 理解用户意图，包括同音错别字。\n2. 基于上下文回答问题。\n3. 如无相关信息则如实说明。"))
                .user(prompt)
                .options(OpenAiChatOptions.builder().model(model).temperature(0.7).build())
                .advisors(a -> a.param("conversation_id", sessionId).param("retrieve_size", 10));

        if (tools != null && !tools.isEmpty()) {
            spec = spec.tools(tools.toArray(new FunctionToolCallback[0]));
            log.debug("Injected {} tool(s) for model [{}]", tools.size(), model);
        }

        return spec.stream().content();
    }

    // ==================== 工具方法 ====================

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize to JSON: {}", e.getMessage());
            return "{}";
        }
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}

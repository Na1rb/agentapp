package com.nairb.ai130.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class ChatAppService {
    private static final Logger log = LoggerFactory.getLogger(ChatAppService.class);

    private final DynamicModelFactory modelFactory;

    public ChatAppService(DynamicModelFactory modelFactory) {
        this.modelFactory = modelFactory;
    }

    /**
     * 流式对话，支持动态模型选择 + 自动降级
     *
     * @param modelCode 可选，为空则用系统默认模型
     */
    public Flux<String> streamChat(String prompt, String sessionId, String modelCode) {
        ChatClient client = modelFactory.getChatClient(modelCode);
        String modelName = modelFactory.resolveModelName(modelCode);

        AtomicBoolean primaryFailed = new AtomicBoolean(false);
        AtomicBoolean hasOutput = new AtomicBoolean(false);

        Flux<String> primary = doStream(client, prompt, sessionId, modelName)
                .doOnNext(c -> hasOutput.set(true))
                .doOnError(e -> {
                    primaryFailed.set(true);
                    log.warn("模型 [{}] 失败: {}", modelName, e.getMessage());
                })
                .onErrorResume(e -> Flux.empty());

        Flux<String> fallback = Flux.defer(() -> {
            if (primaryFailed.get() || !hasOutput.get()) {
                log.info("触发降级模型");
                ChatClient fbClient = modelFactory.getFallbackClient();
                return Flux.concat(
                        Flux.just("\n[模型不可用，已自动切换]\n"),
                        doStream(fbClient, prompt, sessionId, "fallback"));
            }
            return Flux.empty();
        }).onErrorResume(e -> Flux.just("所有模型不可用，请稍后重试。"));

        return Flux.concat(primary, fallback)
                .switchIfEmpty(Flux.just("服务暂时不可用。"));
    }

    private Flux<String> doStream(ChatClient client, String prompt, String sessionId, String modelName) {
        return client.prompt()
                .system(s -> s.text("你是一个智能助手。\n"
                        + "1. 理解用户意图，包括同音错别字。\n"
                        + "2. 基于上下文回答问题。\n"
                        + "3. 如无相关信息则如实说明。"))
                .user(prompt)
                .options(OpenAiChatOptions.builder().model(modelName).temperature(0.7).build())
                .advisors(a -> a.param("conversation_id", sessionId))
                .stream().content();
    }
}

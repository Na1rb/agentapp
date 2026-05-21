package com.nairb.ai130.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class ChatAppService {
    private static final Logger log = LoggerFactory.getLogger(ChatAppService.class);

    private final ChatClient primaryClient;
    private final ChatClient fallbackClient;

    @Value("${spring.ai.openai.chat.options.model:qwen-plus}")
    private String primaryModel;
    @Value("${spring.deepseek.openai.chat.options.model:deepseek-chat}")
    private String fallbackModel;

    public ChatAppService(ChatClient primaryClient,
                          @Qualifier("deepseekChatClient") ChatClient fallbackClient) {
        this.primaryClient = primaryClient; this.fallbackClient = fallbackClient;
    }

    public Flux<String> streamChat(String prompt, String sessionId) {
        AtomicBoolean primaryFailed = new AtomicBoolean(false);
        AtomicBoolean hasOutput = new AtomicBoolean(false);

        Flux<String> primary = streamByModel(prompt, sessionId, primaryModel)
                .doOnNext(c -> hasOutput.set(true))
                .doOnError(e -> { primaryFailed.set(true); log.warn("Primary failed: {}", e.getMessage()); })
                .onErrorResume(e -> Flux.empty());

        Flux<String> fallback = Flux.defer(() -> {
            if (primaryFailed.get() || !hasOutput.get()) {
                log.info("Fallback to [{}]", fallbackModel);
                return Flux.concat(Flux.just("\n[Primary unavailable, switched to fallback]\n"),
                        streamByModel(prompt, sessionId, fallbackModel));
            }
            return Flux.empty();
        }).onErrorResume(e -> Flux.just("Both models unavailable."));

        return Flux.concat(primary, fallback).switchIfEmpty(Flux.just("Service unavailable."));
    }

    private Flux<String> streamByModel(String prompt, String sessionId, String model) {
        ChatClient client = fallbackModel.equalsIgnoreCase(model) ? fallbackClient : primaryClient;
        return client.prompt()
                .system(s -> s.text("你是一个智能助手。\n1. 理解用户意图，包括同音错别字。\n2. 基于上下文回答问题。\n3. 如无相关信息则如实说明。"))
                .user(prompt)
                .options(OpenAiChatOptions.builder().model(model).temperature(0.7).build())
                .advisors(a -> a.param("conversation_id", sessionId).param("retrieve_size", 10))
                .stream().content();
    }
}

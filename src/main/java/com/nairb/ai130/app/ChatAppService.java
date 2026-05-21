package com.nairb.ai130.app;

import com.nairb.ai130.app.factory.ChatClientFactory;
import com.nairb.ai130.types.enums.AgentEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class ChatAppService {
    private static final Logger log = LoggerFactory.getLogger(ChatAppService.class);

    private final ChatClientFactory clientFactory;
    private final VectorStore vectorStore;

    public ChatAppService(ChatClientFactory clientFactory, VectorStore vectorStore) {
        this.clientFactory = clientFactory;
        this.vectorStore = vectorStore;
    }

    public Flux<String> streamChat(String prompt, String sessionId, String agentId, 
                                   String primaryModelId, String fallbackModelId) {
        AtomicBoolean primaryFailed = new AtomicBoolean(false);
        AtomicBoolean hasOutput = new AtomicBoolean(false);

        AgentEnum agent = AgentEnum.fromId(agentId);

        Flux<String> primary = streamByModel(prompt, sessionId, primaryModelId, agent)
                .doOnNext(c -> hasOutput.set(true))
                .doOnError(e -> { primaryFailed.set(true); log.warn("Primary failed: {}", e.getMessage()); })
                .onErrorResume(e -> Flux.empty());

        Flux<String> fallback = Flux.defer(() -> {
            if (primaryFailed.get() || !hasOutput.get()) {
                log.info("Fallback to [{}]", fallbackModelId);
                return Flux.concat(Flux.just("\n[Primary unavailable, switched to fallback]\n"),
                        streamByModel(prompt, sessionId, fallbackModelId, agent));
            }
            return Flux.empty();
        }).onErrorResume(e -> Flux.just("Both models unavailable."));

        return Flux.concat(primary, fallback).switchIfEmpty(Flux.just("Service unavailable."));
    }

    private Flux<String> streamByModel(String prompt, String sessionId, String modelId, AgentEnum agent) {
        ChatClient client = clientFactory.getOrCreateClient(modelId);
        if (client == null) {
            return Flux.error(new RuntimeException("Model not found: " + modelId));
        }

        return client.prompt()
                .system(s -> s.text(agent.getSystemPrompt()))
                .user(prompt)
                .options(OpenAiChatOptions.builder().temperature(agent.getTemperature()).build())
                .advisors(a -> a.param("conversation_id", sessionId).param("retrieve_size", 10))
                .advisors(new QuestionAnswerAdvisor(
                        vectorStore,
                        SearchRequest.builder().similarityThreshold(0.5).topK(3).build()
                ))
                .stream().content();
    }
}

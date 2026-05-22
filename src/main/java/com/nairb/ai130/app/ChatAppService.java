package com.nairb.ai130.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.postretrieval.DocumentPostProcessor;
import org.springframework.ai.rag.preretrieval.query.transformation.RewriteQueryTransformer;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
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
    private final VectorStore vectorStore;

    @Value("${spring.ai.openai.chat.options.model:qwen-plus}")
    private String primaryModel;
    @Value("${spring.deepseek.openai.chat.options.model:deepseek-chat}")
    private String fallbackModel;

    public ChatAppService(ChatClient primaryClient,
                          @Qualifier("deepseekChatClient") ChatClient fallbackClient,
                          VectorStore vectorStore) {
        this.primaryClient = primaryClient; 
        this.fallbackClient = fallbackClient;
        this.vectorStore = vectorStore;
    }

    public Flux<String> streamChat(String prompt, String sessionId, String kbId) {
        AtomicBoolean primaryFailed = new AtomicBoolean(false);
        AtomicBoolean hasOutput = new AtomicBoolean(false);

        Flux<String> primary = streamByModel(prompt, sessionId, primaryModel, kbId)
                .doOnNext(c -> hasOutput.set(true))
                .doOnError(e -> { primaryFailed.set(true); log.warn("Primary failed: {}", e.getMessage()); })
                .onErrorResume(e -> Flux.empty());

        Flux<String> fallback = Flux.defer(() -> {
            if (primaryFailed.get() || !hasOutput.get()) {
                log.info("Fallback to [{}]", fallbackModel);
                return Flux.concat(Flux.just("\n[Primary unavailable, switched to fallback]\n"),
                        streamByModel(prompt, sessionId, fallbackModel, kbId));
            }
            return Flux.empty();
        }).onErrorResume(e -> Flux.just("Both models unavailable."));

        return Flux.concat(primary, fallback).switchIfEmpty(Flux.just("Service unavailable."));
    }

    private Flux<String> streamByModel(String prompt, String sessionId, String model, String kbId) {
        ChatClient client = fallbackModel.equalsIgnoreCase(model) ? fallbackClient : primaryClient;
        
        // 1. 构建高级文档检索器 (动态配置 kbId)
        var retrieverBuilder = VectorStoreDocumentRetriever.builder()
                .vectorStore(vectorStore)
                .topK(10) // 调大 TopK 配合下面的重排
                .similarityThreshold(0.5);
                
        if (kbId != null && !kbId.isEmpty()) {
            retrieverBuilder.filterExpression("kbId == '" + kbId + "'");
        }
        var documentRetriever = retrieverBuilder.build();

        // 2. 构建查询重写器 (Query Rewrite)
        // 结合历史对话，将口语化/代词指代（如"它是什么"）翻译成精准搜索词
        var queryTransformer = RewriteQueryTransformer.builder()
                .chatClientBuilder(client.mutate())
                .build();

        // 3. 构建文档后置处理器 (精排/重排/过滤)
        // 这里模拟实现：先获取 Top 10，然后在这里写业务逻辑，最终只保留最好的 Top 3
        DocumentPostProcessor documentPostProcessor = documents -> {
            if (documents == null || documents.isEmpty()) {
                return java.util.Collections.emptyList();
            }
            // TODO: 未来可接入专门的 Rerank 模型（如 bge-reranker）给 documents 重新打分排序
            // 简单规则过滤：保留前3个
            return documents.stream().limit(3).toList();
        };

        // 4. 组装增强检索顾问
        var advancedRagAdvisor = RetrievalAugmentationAdvisor.builder()
                .documentRetriever(documentRetriever)
                .queryTransformers(queryTransformer) // 🚀 挂载查询重写
                .documentPostProcessors(documentPostProcessor) // 🚀 挂载后置精排
                .build();

        return client.prompt()
                .system(s -> s.text("你是一个智能助手。\n1. 理解用户意图，包括同音错别字。\n2. 基于上下文回答问题。\n3. 如无相关信息则如实说明。"))
                .user(prompt)
                .options(OpenAiChatOptions.builder().model(model).temperature(0.7).build())
                .advisors(a -> a.param("conversation_id", sessionId).param("retrieve_size", 10))
                .advisors(advancedRagAdvisor)
                .stream().content();
    }
}

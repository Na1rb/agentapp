package com.nairb.ai130.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.preretrieval.query.transformation.RewriteQueryTransformer;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class ChatAppService {
    private static final Logger log = LoggerFactory.getLogger(ChatAppService.class);

    private final DynamicModelFactory dynamicModelFactory;
    private final VectorStore vectorStore;

    public ChatAppService(DynamicModelFactory dynamicModelFactory, VectorStore vectorStore) {
        this.dynamicModelFactory = dynamicModelFactory;
        this.vectorStore = vectorStore;
    }

    public Flux<String> streamChat(String prompt, String sessionId, String kbId, String targetModelCode) {
        AtomicBoolean primaryFailed = new AtomicBoolean(false);
        AtomicBoolean hasOutput = new AtomicBoolean(false);

        // 1. 尝试使用用户指定的模型（或系统默认模型）
        Flux<String> primary = streamByModelCode(prompt, sessionId, kbId, targetModelCode)
                .doOnNext(c -> hasOutput.set(true))
                .doOnError(e -> { 
                    primaryFailed.set(true); 
                    log.warn("Model [{}] failed: {}", targetModelCode == null ? "default" : targetModelCode, e.getMessage()); 
                })
                .onErrorResume(e -> Flux.empty());

        // 2. 降级备用逻辑：如果主模型挂了，强制使用 null (默认模型) 兜底。如果主模型本身就是默认模型，尝试获取其它的
        Flux<String> fallback = Flux.defer(() -> {
            if (primaryFailed.get() || !hasOutput.get()) {
                log.info("Fallback triggered! Attempting to use default fallback model...");
                // 为了演示高可用，我们回退到一个硬编码标识或者干脆让 Factory 给我们找一个兜底模型
                // 这里我们传一个空字符串让 Factory 找 Default，如果刚才就是 Default 失败了则这里可能也会报错
                return Flux.concat(Flux.just("\n[请求的服务不可用，已自动为您切换到备用模型]\n"),
                        streamByModelCode(prompt, sessionId, kbId, null));
            }
            return Flux.empty();
        }).onErrorResume(e -> Flux.just("Both primary and fallback models are unavailable."));

        return Flux.concat(primary, fallback).switchIfEmpty(Flux.just("Service unavailable."));
    }

    private Flux<String> streamByModelCode(String prompt, String sessionId, String kbId, String modelCode) {
        // 从动态工厂获取组装好的客户端
        ChatClient client = dynamicModelFactory.getChatClient(modelCode);
        
        // 1. 构建高级文档检索器 (动态配置 kbId)
        var retrieverBuilder = VectorStoreDocumentRetriever.builder()
                .vectorStore(vectorStore)
                .topK(10) 
                .similarityThreshold(0.5);
                
        if (kbId != null && !kbId.isEmpty()) {
            var b = new org.springframework.ai.vectorstore.filter.FilterExpressionBuilder();
            retrieverBuilder.filterExpression(b.eq("kbId", kbId).build());
        }
        var documentRetriever = retrieverBuilder.build();

        // 2. 构建查询重写器 (Query Rewrite)
        var queryTransformer = RewriteQueryTransformer.builder()
                .chatClientBuilder(client.mutate())
                .build();

        // 3. 组装增强检索顾问
        var advancedRagAdvisor = RetrievalAugmentationAdvisor.builder()
                .documentRetriever(documentRetriever)
                .queryTransformers(queryTransformer)
                .build();

        return client.prompt()
                .system(s -> s.text("你是一个智能助手。\n1. 理解用户意图，包括同音错别字。\n2. 基于上下文回答问题。\n3. 如无相关信息则如实说明。"))
                .user(prompt)
                // 注意：这里移除了 options() 强行覆盖 model，因为 dynamicModelFactory 在构建时已经动态写入了正确的 model
                .advisors(a -> a.param("conversation_id", sessionId).param("retrieve_size", 10))
                .advisors(advancedRagAdvisor)
                .stream().content();
    }
}

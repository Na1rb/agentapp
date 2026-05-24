package com.nairb.ai130.app;

import com.nairb.ai130.common.config.AiProperties;
import com.nairb.ai130.domain.model.AiModelConfig;
import com.nairb.ai130.infrastructure.memory.RedisChatMemory;
import com.nairb.ai130.infrastructure.repository.AiModelRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class DynamicModelFactory {

    private static final Logger log = LoggerFactory.getLogger(DynamicModelFactory.class);

    private final AiModelRepository repo;
    private final RedisChatMemory memory;
    private final AiProperties aiProperties;
    private final Map<String, ChatClient> cache = new ConcurrentHashMap<>();

    public DynamicModelFactory(AiModelRepository repo, RedisChatMemory memory, AiProperties aiProperties) {
        this.repo = repo;
        this.memory = memory;
        this.aiProperties = aiProperties;
    }

    // ==================== 对外 API ====================

    /** 获取指定模型（传 null 或空 → 默认模型） */
    public ChatClient getChatClient(String modelCode) {
        AiModelConfig config = resolveFromDb(modelCode);
        if (config != null) {
            return cache.computeIfAbsent("db:" + config.getModelCode(), k -> build(config));
        }
        log.info("数据库无模型配置，使用 yaml 兜底");
        return cache.computeIfAbsent("yaml:primary", k -> buildPrimary());
    }

    /** 获取降级模型（优先数据库第二个启用的，否则 yaml deepseek） */
    public ChatClient getFallbackClient() {
        var all = repo.findEnabled();
        if (all.size() > 1) {
            AiModelConfig fallback = all.get(1);
            return cache.computeIfAbsent("db:" + fallback.getModelCode(), k -> build(fallback));
        }
        return cache.computeIfAbsent("yaml:fallback", k -> buildFallback());
    }

    /** 获取模型名称用于日志 */
    public String resolveModelName(String modelCode) {
        AiModelConfig config = resolveFromDb(modelCode);
        return config != null ? config.getModelName() : "qwen-plus";
    }

    // ==================== 内部 ====================

    private AiModelConfig resolveFromDb(String modelCode) {
        try {
            if (modelCode != null && !modelCode.isEmpty()) {
                return repo.findByCode(modelCode);
            }
            return repo.findDefault();
        } catch (Exception e) {
            log.warn("查询模型配置失败（表可能不存在）: {}", e.getMessage());
            return null;
        }
    }

    private ChatClient build(AiModelConfig cfg) {
        return buildChatClient(
                cfg.getBaseUrl(),
                cfg.getApiKey(),
                cfg.getModelName(),
                cfg.getTemperature() != null ? cfg.getTemperature() : 0.7);
    }

    private ChatClient buildPrimary() {
        return buildChatClient(
                aiProperties.getOpenai().getBaseUrl(),
                aiProperties.getOpenai().getApiKey(),
                "qwen-plus", 0.7);
    }

    private ChatClient buildFallback() {
        return buildChatClient(
                aiProperties.getDeepseek().getBaseUrl(),
                aiProperties.getDeepseek().getApiKey(),
                "deepseek-chat", 0.7);
    }

    /** 统一的 ChatClient 构建工厂方法 —— 只有这一处构建逻辑 */
    private ChatClient buildChatClient(String baseUrl, String apiKey, String modelName, double temperature) {
        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .build();

        OpenAiChatModel model = new OpenAiChatModel(
                api,
                OpenAiChatOptions.builder().model(modelName).temperature(temperature).build(),
                null,
                new org.springframework.retry.support.RetryTemplate(),
                io.micrometer.observation.ObservationRegistry.NOOP
        );

        return ChatClient.builder(model)
                .defaultAdvisors(
                        new SimpleLoggerAdvisor(),
                        MessageChatMemoryAdvisor.builder(memory).build()
                )
                .build();
    }
}

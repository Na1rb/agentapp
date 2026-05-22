package com.nairb.ai130.app;

import com.nairb.ai130.common.exception.BusinessException;
import com.nairb.ai130.domain.model.AiModelConfig;
import com.nairb.ai130.infrastructure.memory.RedisChatMemory;
import com.nairb.ai130.infrastructure.repository.AiModelRepository;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DynamicModelFactory {

    private final AiModelRepository aiModelRepository;
    private final RedisChatMemory redisChatMemory;

    private final Map<String, ChatClient> chatClientCache = new ConcurrentHashMap<>();

    public DynamicModelFactory(AiModelRepository aiModelRepository, RedisChatMemory redisChatMemory) {
        this.aiModelRepository = aiModelRepository;
        this.redisChatMemory = redisChatMemory;
    }

    public ChatClient getChatClient(String modelCode) {
        AiModelConfig config;
        if (modelCode == null || modelCode.isEmpty()) {
            config = aiModelRepository.findDefaultModel();
            if (config == null) {
                var all = aiModelRepository.findAllEnabledModels();
                if (all.isEmpty()) {
                    throw new BusinessException(500, "系统中没有可用的AI模型配置");
                }
                config = all.get(0);
            }
        } else {
            config = aiModelRepository.findByModelCode(modelCode);
            if (config == null) {
                throw new BusinessException(404, "找不到指定的AI模型配置或该模型已禁用：" + modelCode);
            }
        }
        AiModelConfig finalConfig = config;
        return chatClientCache.computeIfAbsent(finalConfig.getModelCode(), key -> buildChatClient(finalConfig));
    }

    private ChatClient buildChatClient(AiModelConfig config) {
        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(config.getBaseUrl())
                .apiKey(config.getApiKey())
                .build();

        OpenAiChatModel chatModel = new OpenAiChatModel(
                api,
                OpenAiChatOptions.builder()
                        .model(config.getModelName())
                        .temperature(config.getTemperature() != null ? config.getTemperature() : 0.7)
                        .build(),
                null,
                new org.springframework.retry.support.RetryTemplate(),
                io.micrometer.observation.ObservationRegistry.NOOP
        );

        return ChatClient.builder(chatModel)
                .defaultAdvisors(
                        new SimpleLoggerAdvisor(),
                        MessageChatMemoryAdvisor.builder(redisChatMemory).build()
                )
                .build();
    }
}

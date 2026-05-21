package com.nairb.ai130.app.factory;

import com.nairb.ai130.domain.model.AiModelConfig;
import com.nairb.ai130.infrastructure.memory.RedisChatMemory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ChatClientFactory {
    private static final Logger log = LoggerFactory.getLogger(ChatClientFactory.class);

    private final JdbcTemplate jdbcTemplate;
    private final RedisChatMemory chatMemory;
    private final ChatClient.Builder defaultBuilder;
    
    // 缓存：Key是模型ID，Value是构建好的ChatClient
    private final Map<String, ChatClient> clientCache = new ConcurrentHashMap<>();

    public ChatClientFactory(JdbcTemplate jdbcTemplate, RedisChatMemory chatMemory, ChatClient.Builder defaultBuilder) {
        this.jdbcTemplate = jdbcTemplate;
        this.chatMemory = chatMemory;
        this.defaultBuilder = defaultBuilder;
    }

    public ChatClient getOrCreateClient(String modelId) {
        if (clientCache.containsKey(modelId)) {
            return clientCache.get(modelId);
        }

        AiModelConfig config = getModelConfigFromDb(modelId);
        if (config == null) {
            log.error("Model config not found for id: {}", modelId);
            return null;
        }

        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(config.getBaseUrl())
                .apiKey(config.getApiKey())
                .build();

        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(config.getModelName())
                        .build())
                .build();

        ChatClient client = ChatClient.builder(chatModel)
                .defaultAdvisors(
                        new SimpleLoggerAdvisor(),
                        MessageChatMemoryAdvisor.builder(chatMemory).build()
                )
                .build();

        clientCache.put(modelId, client);
        log.info("Successfully created and cached ChatClient for model: {}", modelId);
        return client;
    }

    private AiModelConfig getModelConfigFromDb(String modelId) {
        try {
            String sql = "SELECT id, model_name, provider, base_url, api_key, is_active, description FROM sys_ai_model WHERE id = ?";
            return jdbcTemplate.queryForObject(sql, (rs, rowNum) -> {
                AiModelConfig config = new AiModelConfig();
                config.setId(rs.getString("id"));
                config.setModelName(rs.getString("model_name"));
                config.setProvider(rs.getString("provider"));
                config.setBaseUrl(rs.getString("base_url"));
                config.setApiKey(rs.getString("api_key"));
                config.setActive(rs.getBoolean("is_active"));
                config.setDescription(rs.getString("description"));
                return config;
            }, modelId);
        } catch (Exception e) {
            log.error("Failed to query model config for id: {}", modelId, e);
            return null;
        }
    }
}

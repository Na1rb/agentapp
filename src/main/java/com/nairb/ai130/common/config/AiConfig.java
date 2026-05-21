package com.nairb.ai130.common.config;

import com.nairb.ai130.infrastructure.memory.RedisChatMemory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.model.ApiKey;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
public class AiConfig {

    private static final Logger log = LoggerFactory.getLogger(AiConfig.class);

    // ==================== 基础数据源 ====================

    @Bean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    // ==================== 文本分割器 ====================

    @Bean
    public TextSplitter textSplitter() {
        return new TokenTextSplitter(100, 500, 50, 10000, true, true);
    }

    // ==================== 向量存储 (PgVector) ====================

    @Bean
    public VectorStore vectorStore(JdbcTemplate jdbcTemplate, OpenAiEmbeddingModel embeddingModel) {
        return PgVectorStore.builder(jdbcTemplate, embeddingModel)
                .dimensions(1024)
                .initializeSchema(true)
                .build();
    }

    // ==================== Redis 配置 ====================

    @Bean
    public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new StringRedisSerializer());
        template.afterPropertiesSet();
        return template;
    }

    @Bean
    public RedisChatMemory redisChatMemory(RedisTemplate<String, String> redisTemplate) {
        return new RedisChatMemory(redisTemplate);
    }

    // ==================== 主用模型 ChatClient (阿里云 Qwen) ====================

    @Bean
    public ChatClient chatClient(OpenAiChatModel model,
                                 RedisChatMemory redisChatMemory) {
        return ChatClient.builder(model)
                .defaultAdvisors(
                        new SimpleLoggerAdvisor(),
                        MessageChatMemoryAdvisor.builder(redisChatMemory).build()
                )
                .build();
    }

    // ==================== 降级模型 ChatClient (DeepSeek) ====================

    @Value("${spring.deepseek.openai.base-url:https://api.deepseek.com}")
    private String deepseekBaseUrl;

    @Value("${spring.deepseek.openai.api-key:}")
    private ApiKey deepseekApiKey;

    @Bean("deepseekChatClient")
    public ChatClient deepseekChatClient(RedisChatMemory redisChatMemory) {
        OpenAiApi deepseekApi = OpenAiApi.builder()
                .baseUrl(deepseekBaseUrl)
                .apiKey(deepseekApiKey)
                .build();
        
        OpenAiChatModel deepseekModel = OpenAiChatModel.builder()
                .openAiApi(deepseekApi)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model("deepseek-chat")
                        .temperature(0.7)
                        .build())
                .build();

        return ChatClient.builder(deepseekModel)
                .defaultAdvisors(
                        new SimpleLoggerAdvisor(),
                        MessageChatMemoryAdvisor.builder(redisChatMemory).build()
                )
                .build();
    }
}

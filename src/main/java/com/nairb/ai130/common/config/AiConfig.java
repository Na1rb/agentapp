package com.nairb.ai130.common.config;

import com.nairb.ai130.infrastructure.memory.RedisChatMemory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
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

import java.util.List;


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
        return new TokenTextSplitter(100, 500, 50, 10000, true, List.of('.', '。', '!', '！', '?', '？', ';', '；', '\n'));
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

    // ==================== 主用模型 (阿里云 Qwen / OpenAI 兼容) ====================

    @Value("${spring.ai.openai.base-url:https://api.openai.com}")
    private String openaiBaseUrl;

    @Value("${spring.ai.openai.api-key:}")
    private String openaiApiKey;

    @Value("${spring.ai.openai.chat.options.model:qwen-plus}")
    private String openaiChatModel;

    @Value("${spring.ai.openai.embedding.options.model:text-embedding-v3}")
    private String openaiEmbeddingModel;

    /**
     * 主用 OpenAiApi（指向阿里云 DashScope 兼容端点）
     */
    @Bean
    public OpenAiApi openAiApi() {
        return OpenAiApi.builder()
                .baseUrl(openaiBaseUrl)
                .apiKey(openaiApiKey)
                .build();
    }

    /**
     * 主用 ChatModel
     */
    @Bean
    public OpenAiChatModel openAiChatModel(OpenAiApi openAiApi) {
        return OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(openaiChatModel)
                        .temperature(0.7)
                        .build())
                .build();
    }

    /**
     * 主用 EmbeddingModel
     */
    @Bean
    public OpenAiEmbeddingModel openAiEmbeddingModel(OpenAiApi openAiApi) {
        return new OpenAiEmbeddingModel(openAiApi, MetadataMode.EMBED,
                OpenAiEmbeddingOptions.builder()
                        .model(openaiEmbeddingModel)
                        .build());
    }

    /**
     * 主用 ChatClient
     */
    @Bean
    public ChatClient chatClient(OpenAiChatModel openAiChatModel,
                                 RedisChatMemory redisChatMemory,
                                 VectorStore vectorStore) {
        return ChatClient.builder(openAiChatModel)
                .defaultAdvisors(
                new SimpleLoggerAdvisor(),
                        MessageChatMemoryAdvisor.builder(redisChatMemory).build(),
                        QuestionAnswerAdvisor.builder(vectorStore)
                                .searchRequest(SearchRequest.builder()
                                        .similarityThreshold(0.5)
                                        .topK(7)
                                        .build())
                                .build()
                )
                .build();
    }

    // ==================== 降级模型 DeepSeek ====================

    @Value("${spring.deepseek.openai.base-url:https://api.deepseek.com}")
    private String deepseekBaseUrl;

    @Value("${spring.deepseek.openai.api-key:}")
    private String deepseekApiKey;

    @Value("${spring.deepseek.openai.chat.options.model:deepseek-v4-flash}")
    private String deepseekChatModelName;

    /**
     * DeepSeek OpenAiApi
     */
    @Bean
    public OpenAiApi deepseekOpenAiApi() {
        return OpenAiApi.builder()
                .baseUrl(deepseekBaseUrl)
                .apiKey(deepseekApiKey)
                .build();
    }

    /**
     * DeepSeek ChatModel（降级备用）
     */
    @Bean
    public OpenAiChatModel deepseekChatModel(OpenAiApi deepseekOpenAiApi) {
        return OpenAiChatModel.builder()
                .openAiApi(deepseekOpenAiApi)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(deepseekChatModelName)
                        .temperature(0.7)
                        .build())
                .build();
    }

    @Bean("deepseekChatClient")
    public ChatClient deepseekChatClient(OpenAiChatModel deepseekChatModel,
                                         RedisChatMemory redisChatMemory,
                                         VectorStore vectorStore) {
        return ChatClient.builder(deepseekChatModel)
                .defaultAdvisors(
                new SimpleLoggerAdvisor(),
                        MessageChatMemoryAdvisor.builder(redisChatMemory).build(),
                        QuestionAnswerAdvisor.builder(vectorStore)
                                .searchRequest(SearchRequest.builder()
                                        .similarityThreshold(0.5)
                                        .topK(7)
                                        .build())
                                .build()
                )
                .build();
    }
}

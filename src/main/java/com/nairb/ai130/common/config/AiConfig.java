package com.nairb.ai130.common.config;

import com.nairb.ai130.infrastructure.memory.RedisChatMemory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
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

    @Bean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean
    public TextSplitter textSplitter() {
        return new TokenTextSplitter();
    }

    @Bean
    public VectorStore vectorStore(JdbcTemplate jdbcTemplate,
                                   org.springframework.context.ApplicationContext applicationContext) {
        OpenAiEmbeddingModel embeddingModel;
        try {
            embeddingModel = applicationContext.getBean(OpenAiEmbeddingModel.class);
        } catch (Exception e) {
            log.warn("未找到 OpenAiEmbeddingModel，使用 dummy 兜底");
            embeddingModel = new OpenAiEmbeddingModel(OpenAiApi.builder()
                    .baseUrl("https://api.openai.com")
                    .apiKey("dummy")
                    .build());
        }
        return PgVectorStore.builder(jdbcTemplate, embeddingModel)
                .dimensions(1024)
                .initializeSchema(true)
                .build();
    }

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
}

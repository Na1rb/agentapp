package com.nairb.ai130.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AI 模型配置属性，映射 application.yaml 中的 spring.ai.openai.* 和 spring.deepseek.*。
 * <p>
 * 作为 DynamicModelFactory 的 yaml 兜底数据源。
 */
@Component
@ConfigurationProperties(prefix = "spring.ai")
public class AiProperties {

    private final Openai openai = new Openai();
    private final Deepseek deepseek = new Deepseek();

    public Openai getOpenai() { return openai; }
    public Deepseek getDeepseek() { return deepseek; }

    public static class Openai {
        private String baseUrl;
        private String apiKey;

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    }

    public static class Deepseek {
        private String baseUrl;
        private String apiKey;

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    }
}

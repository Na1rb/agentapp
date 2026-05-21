package com.nairb.ai130.domain.model;

public class AiModelConfig {
    private String id;
    private String modelName;
    private String provider;
    private String baseUrl;
    private String apiKey;
    private boolean isActive;
    private String description;

    public AiModelConfig() {}

    public AiModelConfig(String id, String modelName, String provider, String baseUrl, String apiKey, boolean isActive, String description) {
        this.id = id;
        this.modelName = modelName;
        this.provider = provider;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.isActive = isActive;
        this.description = description;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }
    
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    
    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { this.isActive = active; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}

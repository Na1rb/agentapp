package com.nairb.ai130.types;

public class AiModelVO {
    private String modelCode;
    private String provider;
    private String modelName;
    private String description;
    private Boolean isDefault;

    public AiModelVO(String modelCode, String provider, String modelName, String description, Boolean isDefault) {
        this.modelCode = modelCode;
        this.provider = provider;
        this.modelName = modelName;
        this.description = description;
        this.isDefault = isDefault;
    }

    // Getters
    public String getModelCode() { return modelCode; }
    public void setModelCode(String modelCode) { this.modelCode = modelCode; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Boolean getIsDefault() { return isDefault; }
    public void setIsDefault(Boolean isDefault) { this.isDefault = isDefault; }
}

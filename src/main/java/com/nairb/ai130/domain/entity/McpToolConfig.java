package com.nairb.ai130.domain.entity;

import java.time.LocalDateTime;

/**
 * MCP 工具配置实体。
 * <p>
 * 对应数据库表 {@code mcp_tool_config}，存储 MCP 工具的元数据，
 * 包括 HTTP 调用端点、参数 Schema、认证配置等。
 */
public class McpToolConfig {

    private Long id;
    private String toolName;
    private String displayName;
    private String description;
    private String toolType;         // HTTP_API
    private String endpointUrl;
    private String httpMethod;       // GET, POST, PUT, DELETE
    private String requestTemplate;
    private String responseParser;   // JSON_PATH
    private String headersConfig;    // JSON
    private String authConfig;       // JSON
    private String paramSchema;      // JSON Schema
    private Boolean isEnabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public McpToolConfig() {}

    // ==================== Getters / Setters ====================

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getToolName() { return toolName; }
    public void setToolName(String toolName) { this.toolName = toolName; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getToolType() { return toolType; }
    public void setToolType(String toolType) { this.toolType = toolType; }

    public String getEndpointUrl() { return endpointUrl; }
    public void setEndpointUrl(String endpointUrl) { this.endpointUrl = endpointUrl; }

    public String getHttpMethod() { return httpMethod; }
    public void setHttpMethod(String httpMethod) { this.httpMethod = httpMethod; }

    public String getRequestTemplate() { return requestTemplate; }
    public void setRequestTemplate(String requestTemplate) { this.requestTemplate = requestTemplate; }

    public String getResponseParser() { return responseParser; }
    public void setResponseParser(String responseParser) { this.responseParser = responseParser; }

    public String getHeadersConfig() { return headersConfig; }
    public void setHeadersConfig(String headersConfig) { this.headersConfig = headersConfig; }

    public String getAuthConfig() { return authConfig; }
    public void setAuthConfig(String authConfig) { this.authConfig = authConfig; }

    public String getParamSchema() { return paramSchema; }
    public void setParamSchema(String paramSchema) { this.paramSchema = paramSchema; }

    public Boolean getIsEnabled() { return isEnabled; }
    public void setIsEnabled(Boolean isEnabled) { this.isEnabled = isEnabled; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}

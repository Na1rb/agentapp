package com.nairb.ai130.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.nairb.ai130.domain.entity.McpToolConfig;

/**
 * 返回前端的 MCP 工具信息 DTO。
 * <p>
 * 只暴露前端需要的字段，隐藏内部配置细节（如 endpoint_url、headers、auth 等）。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class McpToolResponse {

    private String toolName;
    private String displayName;
    private String description;
    private String toolType;
    private String paramSchema;
    private boolean enabled;

    public McpToolResponse() {}

    /**
     * 从实体构建响应 DTO。
     */
    public static McpToolResponse from(McpToolConfig config) {
        McpToolResponse dto = new McpToolResponse();
        dto.toolName = config.getToolName();
        dto.displayName = config.getDisplayName();
        dto.description = config.getDescription();
        dto.toolType = config.getToolType();
        dto.paramSchema = config.getParamSchema();
        dto.enabled = Boolean.TRUE.equals(config.getIsEnabled());
        return dto;
    }

    // ==================== Getters ====================

    public String getToolName() { return toolName; }
    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    public String getToolType() { return toolType; }
    public String getParamSchema() { return paramSchema; }
    public boolean isEnabled() { return enabled; }
}

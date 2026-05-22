package com.nairb.ai130.types.dto;

/**
 * MCP 工具配置请求 DTO —— 创建/更新时使用
 */
public class McpToolRequestDTO {

    private Long id;                // 更新时使用
    private String mcpId;
    private String mcpName;
    private String transportType;   // sse | stdio
    private String transportConfig; // JSON 字符串
    private Integer requestTimeout;
    private Integer status;         // 0=禁用 1=启用

    public McpToolRequestDTO() {}

    // ==================== Getters / Setters ====================

    public Long getId()                          { return id; }
    public void setId(Long id)                   { this.id = id; }
    public String getMcpId()                     { return mcpId; }
    public void setMcpId(String mcpId)           { this.mcpId = mcpId; }
    public String getMcpName()                   { return mcpName; }
    public void setMcpName(String mcpName)       { this.mcpName = mcpName; }
    public String getTransportType()             { return transportType; }
    public void setTransportType(String type)    { this.transportType = type; }
    public String getTransportConfig()           { return transportConfig; }
    public void setTransportConfig(String cfg)   { this.transportConfig = cfg; }
    public Integer getRequestTimeout()           { return requestTimeout; }
    public void setRequestTimeout(Integer t)     { this.requestTimeout = t; }
    public Integer getStatus()                   { return status; }
    public void setStatus(Integer status)        { this.status = status; }
}

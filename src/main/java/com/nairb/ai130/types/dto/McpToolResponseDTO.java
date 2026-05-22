package com.nairb.ai130.types.dto;

import java.time.LocalDateTime;

/**
 * MCP 工具配置响应 DTO —— 查询时返回
 */
public class McpToolResponseDTO {

    private Long id;
    private String mcpId;
    private String mcpName;
    private String transportType;
    private String transportConfig;
    private Integer requestTimeout;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public McpToolResponseDTO() {}

    // ==================== Getters / Setters ====================

    public Long getId()                              { return id; }
    public void setId(Long id)                       { this.id = id; }
    public String getMcpId()                         { return mcpId; }
    public void setMcpId(String mcpId)               { this.mcpId = mcpId; }
    public String getMcpName()                       { return mcpName; }
    public void setMcpName(String mcpName)           { this.mcpName = mcpName; }
    public String getTransportType()                 { return transportType; }
    public void setTransportType(String type)        { this.transportType = type; }
    public String getTransportConfig()               { return transportConfig; }
    public void setTransportConfig(String cfg)       { this.transportConfig = cfg; }
    public Integer getRequestTimeout()               { return requestTimeout; }
    public void setRequestTimeout(Integer t)         { this.requestTimeout = t; }
    public Integer getStatus()                       { return status; }
    public void setStatus(Integer status)            { this.status = status; }
    public LocalDateTime getCreateTime()             { return createTime; }
    public void setCreateTime(LocalDateTime t)       { this.createTime = t; }
    public LocalDateTime getUpdateTime()             { return updateTime; }
    public void setUpdateTime(LocalDateTime t)       { this.updateTime = t; }
}

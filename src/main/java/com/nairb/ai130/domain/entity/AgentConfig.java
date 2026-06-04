package com.nairb.ai130.domain.entity;

import java.time.LocalDateTime;

/** 对应 agent_config 表 */
public class AgentConfig {
    private String agentId;
    private String agentName;
    private String description;
    private String channel;
    private String strategy;
    private Integer status;
    private Integer maxRound;
    private Integer maxPace;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public String getAgentId() { return agentId; }
    public void setAgentId(String v) { agentId = v; }
    public String getAgentName() { return agentName; }
    public void setAgentName(String v) { agentName = v; }
    public String getDescription() { return description; }
    public void setDescription(String v) { description = v; }
    public String getChannel() { return channel; }
    public void setChannel(String v) { channel = v; }
    public String getStrategy() { return strategy; }
    public void setStrategy(String v) { strategy = v; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer v) { status = v; }
    public Integer getMaxRound() { return maxRound; }
    public void setMaxRound(Integer v) { maxRound = v; }
    public Integer getMaxPace() { return maxPace; }
    public void setMaxPace(Integer v) { maxPace = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime v) { createdAt = v; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime v) { updatedAt = v; }
}

package com.nairb.ai130.infrastructure.dao.po;

import java.time.LocalDateTime;

/**
 * 智能体-客户端流程关联 PO
 */
public class AiAgentFlowConfig {

    private Long id;
    private String agentId;
    private String clientId;
    private String clientName;
    private String clientType;
    private Integer sequence;
    private String stepPrompt;
    private LocalDateTime createTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }
    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    public String getClientName() { return clientName; }
    public void setClientName(String clientName) { this.clientName = clientName; }
    public String getClientType() { return clientType; }
    public void setClientType(String clientType) { this.clientType = clientType; }
    public Integer getSequence() { return sequence; }
    public void setSequence(Integer sequence) { this.sequence = sequence; }
    public String getStepPrompt() { return stepPrompt; }
    public void setStepPrompt(String stepPrompt) { this.stepPrompt = stepPrompt; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}

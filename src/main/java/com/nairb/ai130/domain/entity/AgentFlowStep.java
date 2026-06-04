package com.nairb.ai130.domain.entity;

import java.time.LocalDateTime;

/** 对应 agent_flow_step 表 */
public class AgentFlowStep {
    private Long id;
    private String agentId;
    private String stepId;
    private Integer sequence;
    private String clientType;      // MCP_TOOL / LLM
    private String clientName;
    private String clientId;        // 工具名 或 模型code
    private String stepPrompt;
    private String inputMapping;    // JSON
    private String outputKey;
    private String onError;         // abort / retry / fallback_to_step
    private String fallbackStep;
    private Integer retryLimit;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long v) { id = v; }
    public String getAgentId() { return agentId; }
    public void setAgentId(String v) { agentId = v; }
    public String getStepId() { return stepId; }
    public void setStepId(String v) { stepId = v; }
    public Integer getSequence() { return sequence; }
    public void setSequence(Integer v) { sequence = v; }
    public String getClientType() { return clientType; }
    public void setClientType(String v) { clientType = v; }
    public String getClientName() { return clientName; }
    public void setClientName(String v) { clientName = v; }
    public String getClientId() { return clientId; }
    public void setClientId(String v) { clientId = v; }
    public String getStepPrompt() { return stepPrompt; }
    public void setStepPrompt(String v) { stepPrompt = v; }
    public String getInputMapping() { return inputMapping; }
    public void setInputMapping(String v) { inputMapping = v; }
    public String getOutputKey() { return outputKey; }
    public void setOutputKey(String v) { outputKey = v; }
    public String getOnError() { return onError; }
    public void setOnError(String v) { onError = v; }
    public String getFallbackStep() { return fallbackStep; }
    public void setFallbackStep(String v) { fallbackStep = v; }
    public Integer getRetryLimit() { return retryLimit; }
    public void setRetryLimit(Integer v) { retryLimit = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime v) { createdAt = v; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime v) { updatedAt = v; }
}

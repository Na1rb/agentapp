package com.nairb.ai130.api.dto;

/**
 * Agent 执行请求 DTO
 */
public class ExecuteAgentRequestDTO {

    private String aiAgentId;
    private String message;
    private String sessionId;
    private Integer maxStep;

    public ExecuteAgentRequestDTO() {}

    public String getAiAgentId() { return aiAgentId; }
    public void setAiAgentId(String aiAgentId) { this.aiAgentId = aiAgentId; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public Integer getMaxStep() { return maxStep; }
    public void setMaxStep(Integer maxStep) { this.maxStep = maxStep; }
}

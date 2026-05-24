package com.nairb.ai130.domain.agent.model.entity;

/**
 * 执行命令实体 —— 用户发起 Agent 对话的入参
 */
public class ExecuteCommandEntity {

    private String aiAgentId;
    private String message;
    private String sessionId;
    private Integer maxStep;

    public ExecuteCommandEntity() {}

    public ExecuteCommandEntity(String aiAgentId, String message, String sessionId, Integer maxStep) {
        this.aiAgentId = aiAgentId;
        this.message = message;
        this.sessionId = sessionId;
        this.maxStep = maxStep;
    }

    public static ExecuteCommandEntityBuilder builder() {
        return new ExecuteCommandEntityBuilder();
    }

    public static class ExecuteCommandEntityBuilder {
        private String aiAgentId;
        private String message;
        private String sessionId;
        private Integer maxStep;

        ExecuteCommandEntityBuilder() {}

        public ExecuteCommandEntityBuilder aiAgentId(String aiAgentId) { this.aiAgentId = aiAgentId; return this; }
        public ExecuteCommandEntityBuilder message(String message) { this.message = message; return this; }
        public ExecuteCommandEntityBuilder sessionId(String sessionId) { this.sessionId = sessionId; return this; }
        public ExecuteCommandEntityBuilder maxStep(Integer maxStep) { this.maxStep = maxStep; return this; }
        public ExecuteCommandEntity build() { return new ExecuteCommandEntity(aiAgentId, message, sessionId, maxStep); }
    }

    public String getAiAgentId() { return aiAgentId; }
    public void setAiAgentId(String aiAgentId) { this.aiAgentId = aiAgentId; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public Integer getMaxStep() { return maxStep; }
    public void setMaxStep(Integer maxStep) { this.maxStep = maxStep; }
}

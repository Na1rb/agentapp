package com.nairb.ai130.domain.agent.model.entity;

/**
 * Agent 执行结果实体 —— 用于 SSE 流式推送到前端
 */
public class AgentExecuteResultEntity {

    private String type;
    private Integer step;
    private String content;
    private String sessionId;
    private Boolean completed;
    private Long timestamp;

    public AgentExecuteResultEntity() {}

    public AgentExecuteResultEntity(String type, Integer step, String content, String sessionId, Boolean completed, Long timestamp) {
        this.type = type;
        this.step = step;
        this.content = content;
        this.sessionId = sessionId;
        this.completed = completed;
        this.timestamp = timestamp;
    }

    // ============ 工厂方法 ============

    public static AgentExecuteResultEntity createAnalysisResult(Integer step, String content, String sessionId) {
        return new AgentExecuteResultEntity("analysis", step, content, sessionId, false, System.currentTimeMillis());
    }

    public static AgentExecuteResultEntity createExecutionResult(Integer step, String content, String sessionId) {
        return new AgentExecuteResultEntity("execution", step, content, sessionId, false, System.currentTimeMillis());
    }

    public static AgentExecuteResultEntity createSupervisionResult(Integer step, String content, String sessionId) {
        return new AgentExecuteResultEntity("supervision", step, content, sessionId, false, System.currentTimeMillis());
    }

    public static AgentExecuteResultEntity createSummaryResult(String content, String sessionId) {
        return new AgentExecuteResultEntity("summary", null, content, sessionId, true, System.currentTimeMillis());
    }

    public static AgentExecuteResultEntity createErrorResult(String content, String sessionId) {
        return new AgentExecuteResultEntity("error", null, content, sessionId, true, System.currentTimeMillis());
    }

    public static AgentExecuteResultEntity createCompleteResult(String sessionId) {
        return new AgentExecuteResultEntity("complete", null, "执行完成", sessionId, true, System.currentTimeMillis());
    }

    // ============ Getters / Setters ============

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public Integer getStep() { return step; }
    public void setStep(Integer step) { this.step = step; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public Boolean getCompleted() { return completed; }
    public void setCompleted(Boolean completed) { this.completed = completed; }
    public Long getTimestamp() { return timestamp; }
    public void setTimestamp(Long timestamp) { this.timestamp = timestamp; }
}

package com.nairb.ai130.domain.entity;

import java.time.LocalDateTime;

/** 对应 agent_execution_log 表 */
public class AgentExecutionLog {
    private Long id;
    private String agentId;
    private String sessionId;
    private Long userId;
    private String status;
    private String stepResults;   // JSON 数组
    private Integer totalTokens;
    private String errorMsg;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;

    public Long getId() { return id; }
    public void setId(Long v) { id = v; }
    public String getAgentId() { return agentId; }
    public void setAgentId(String v) { agentId = v; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String v) { sessionId = v; }
    public Long getUserId() { return userId; }
    public void setUserId(Long v) { userId = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { status = v; }
    public String getStepResults() { return stepResults; }
    public void setStepResults(String v) { stepResults = v; }
    public Integer getTotalTokens() { return totalTokens; }
    public void setTotalTokens(Integer v) { totalTokens = v; }
    public String getErrorMsg() { return errorMsg; }
    public void setErrorMsg(String v) { errorMsg = v; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime v) { startedAt = v; }
    public LocalDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(LocalDateTime v) { finishedAt = v; }
}

package com.nairb.ai130.domain.workflow;

import java.time.Instant;

/**
 * 工作流执行记录 — 对应于 wf_execution 表。
 * <p>
 * 每次用户触发分步编排时创建一条记录，持久化到数据库。
 * 支持断点续跑：按 session_id 可查询上次执行记录并恢复。
 */
public class WorkflowExecution {

    private String id;
    private String workflowId;
    private String sessionId;
    private String prompt;
    private String status;          // running | paused | completing | completed | failed
    private String currentNodeId;
    private int retryCount;
    private String errorMessage;
    private Instant startedAt;
    private Instant finishedAt;
    private Instant updatedAt;

    public WorkflowExecution() {}

    /**
     * 创建新的执行记录。
     */
    public static WorkflowExecution create(String id, String workflowId, String sessionId, String prompt) {
        WorkflowExecution e = new WorkflowExecution();
        e.id = id;
        e.workflowId = workflowId;
        e.sessionId = sessionId;
        e.prompt = prompt;
        e.status = "running";
        e.retryCount = 0;
        e.startedAt = Instant.now();
        e.updatedAt = Instant.now();
        return e;
    }

    // ==================== 状态推进 ====================

    /** 进入下一个节点 */
    public void enterNode(String nodeId) {
        this.currentNodeId = nodeId;
        this.updatedAt = Instant.now();
    }

    /** 标记为暂停 */
    public void pause() {
        this.status = "paused";
        this.updatedAt = Instant.now();
    }

    /** 标记为完成 */
    public void complete() {
        this.status = "completed";
        this.finishedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    /** 标记为失败 */
    public void fail(String error) {
        this.status = "failed";
        this.errorMessage = error;
        this.finishedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    /** 递增重试计数 */
    public void incrementRetry() {
        this.retryCount++;
        this.updatedAt = Instant.now();
    }

    // ==================== Getters / Setters ====================

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getWorkflowId() { return workflowId; }
    public void setWorkflowId(String workflowId) { this.workflowId = workflowId; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getPrompt() { return prompt; }
    public void setPrompt(String prompt) { this.prompt = prompt; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getCurrentNodeId() { return currentNodeId; }
    public void setCurrentNodeId(String currentNodeId) { this.currentNodeId = currentNodeId; }

    public int getRetryCount() { return retryCount; }
    public void setRetryCount(int retryCount) { this.retryCount = retryCount; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }

    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

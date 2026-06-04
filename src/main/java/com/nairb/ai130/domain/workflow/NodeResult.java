package com.nairb.ai130.domain.workflow;

import java.time.Instant;

/**
 * 节点执行结果 — 对应于 wf_node_result 表。
 * <p>
 * 记录每个节点一次执行的完整生命周期，持久化到数据库支持断点续跑。
 */
public class NodeResult {

    private String id;
    private String executionId;
    private String nodeId;
    private String status;        // pending | running | completed | failed | skipped
    private String inputText;
    private String outputText;
    private String errorMessage;
    private String checkResultJson; // 结构化 JSON
    private Instant startedAt;
    private Instant finishedAt;

    public NodeResult() {}

    /**
     * 创建一个新的待执行节点记录。
     */
    public static NodeResult pending(String id, String executionId, String nodeId, String inputText) {
        NodeResult r = new NodeResult();
        r.id = id;
        r.executionId = executionId;
        r.nodeId = nodeId;
        r.status = "pending";
        r.inputText = inputText;
        return r;
    }

    /** 标记为运行中 */
    public void markRunning() {
        this.status = "running";
        this.startedAt = Instant.now();
    }

    /** 标记为完成 */
    public void markCompleted(String output) {
        this.status = "completed";
        this.outputText = output;
        this.finishedAt = Instant.now();
    }

    /** 标记为失败 */
    public void markFailed(String error) {
        this.status = "failed";
        this.errorMessage = error;
        this.finishedAt = Instant.now();
    }

    /** 标记为跳过 */
    public void markSkipped() {
        this.status = "skipped";
        this.finishedAt = Instant.now();
    }

    // ==================== Getters / Setters ====================

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getExecutionId() { return executionId; }
    public void setExecutionId(String executionId) { this.executionId = executionId; }

    public String getNodeId() { return nodeId; }
    public void setNodeId(String nodeId) { this.nodeId = nodeId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getInputText() { return inputText; }
    public void setInputText(String inputText) { this.inputText = inputText; }

    public String getOutputText() { return outputText; }
    public void setOutputText(String outputText) { this.outputText = outputText; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public String getCheckResultJson() { return checkResultJson; }
    public void setCheckResultJson(String checkResultJson) { this.checkResultJson = checkResultJson; }

    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }

    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }
}

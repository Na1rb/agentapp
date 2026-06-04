package com.nairb.ai130.domain.entity;

import java.time.LocalDateTime;

public class AgentNodeResult {

    private Long id;
    private Long logId;
    private Integer round;
    private String role;
    private String prompt;
    private String output;
    private Integer durationMs;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getLogId() { return logId; }
    public void setLogId(Long logId) { this.logId = logId; }
    public Integer getRound() { return round; }
    public void setRound(Integer round) { this.round = round; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getPrompt() { return prompt; }
    public void setPrompt(String prompt) { this.prompt = prompt; }
    public String getOutput() { return output; }
    public void setOutput(String output) { this.output = output; }
    public Integer getDurationMs() { return durationMs; }
    public void setDurationMs(Integer durationMs) { this.durationMs = durationMs; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}

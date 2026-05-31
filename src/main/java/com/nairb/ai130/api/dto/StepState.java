package com.nairb.ai130.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.nairb.ai130.common.enums.StepPhase;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 步骤状态快照 — Redis 中 step:state:{chatId} 的 Java 模型。
 * <p>
 * 记录当前编排进度（阶段、循环次数、子任务列表、已完成结果等），
 * 由 {@code StepStateManager} 进行 JSON 序列化后存入 Redis。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class StepState {

    /** 当前阶段 */
    private StepPhase phase = StepPhase.ANALYZE;

    /** 当前循环次数（EXECUTE→CHECK 计为一次） */
    private int loopCount = 0;

    /** 最大循环次数（硬上限，默认 3） */
    private int maxLoops = 3;

    /** 策略标识（如 STEP_CHECK） */
    private String strategy;

    /** 待执行的子任务列表 */
    private List<Task> pendingTasks = new ArrayList<>();

    /** 已完成的结果列表 */
    private List<CompletedResult> completedResults = new ArrayList<>();

    /** 当前正在执行的任务索引（从 0 开始） */
    private int currentTaskIndex = 0;

    /** 创建时间（ISO-8601） */
    private String createdAt;

    /** 乐观锁版本号（每次 save 自动 +1，用于并发写入检测） */
    private int version = 0;

    // ==================== 工厂方法 ====================

    public static StepState init(String strategy) {
        StepState s = new StepState();
        s.phase = StepPhase.ANALYZE;
        s.loopCount = 0;
        s.maxLoops = 3;
        s.strategy = strategy;
        s.createdAt = Instant.now().toString();
        return s;
    }

    // ==================== 便捷方法 ====================

    /** 是否有待处理的子任务 */
    public boolean hasPendingTasks() {
        return pendingTasks != null && !pendingTasks.isEmpty();
    }

    /** 所有子任务是否都已完成 */
    public boolean allTasksDone() {
        if (pendingTasks == null || pendingTasks.isEmpty()) return false;
        return pendingTasks.stream().allMatch(t -> "DONE".equals(t.getStatus()));
    }

    /** 获取当前待执行的子任务 */
    public Task getCurrentTask() {
        if (pendingTasks == null || pendingTasks.isEmpty()) return null;
        if (currentTaskIndex >= pendingTasks.size()) return null;
        return pendingTasks.get(currentTaskIndex);
    }

    /** 标记当前任务完成并记录结果 */
    public void markCurrentTaskDone(String output) {
        Task task = getCurrentTask();
        if (task != null) {
            task.setStatus("DONE");
            completedResults.add(new CompletedResult(task.getId(), output));
            currentTaskIndex++;
        }
    }

    /** 标记当前任务失败 */
    public void markCurrentTaskFailed(String error) {
        Task task = getCurrentTask();
        if (task != null) {
            task.setStatus("FAILED");
            completedResults.add(new CompletedResult(task.getId(), "[ERROR] " + error));
            currentTaskIndex++;
        }
    }

    /** 循环计数 +1 */
    public void incrementLoop() {
        this.loopCount++;
    }

    /** 是否已达到最大循环次数 */
    public boolean isMaxLoopsReached() {
        return loopCount >= maxLoops;
    }

    // ==================== Getters / Setters ====================

    public StepPhase getPhase() { return phase; }
    public void setPhase(StepPhase phase) { this.phase = phase; }

    public int getLoopCount() { return loopCount; }
    public void setLoopCount(int loopCount) { this.loopCount = loopCount; }

    public int getMaxLoops() { return maxLoops; }
    public void setMaxLoops(int maxLoops) { this.maxLoops = maxLoops; }

    public String getStrategy() { return strategy; }
    public void setStrategy(String strategy) { this.strategy = strategy; }

    public List<Task> getPendingTasks() { return pendingTasks; }
    public void setPendingTasks(List<Task> pendingTasks) { this.pendingTasks = pendingTasks; }

    public List<CompletedResult> getCompletedResults() { return completedResults; }
    public void setCompletedResults(List<CompletedResult> completedResults) { this.completedResults = completedResults; }

    public int getCurrentTaskIndex() { return currentTaskIndex; }
    public void setCurrentTaskIndex(int currentTaskIndex) { this.currentTaskIndex = currentTaskIndex; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }

    // ==================== 内部类 ====================

    /** 子任务定义 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Task {
        private String id;
        private String desc;
        private String status; // PENDING | IN_PROGRESS | DONE | FAILED

        public Task() {}
        public Task(String id, String desc, String status) {
            this.id = id; this.desc = desc; this.status = status;
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getDesc() { return desc; }
        public void setDesc(String desc) { this.desc = desc; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }

    /** 已完成的任务结果 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CompletedResult {
        private String taskId;
        private String output;

        public CompletedResult() {}
        public CompletedResult(String taskId, String output) {
            this.taskId = taskId; this.output = output;
        }

        public String getTaskId() { return taskId; }
        public void setTaskId(String taskId) { this.taskId = taskId; }

        public String getOutput() { return output; }
        public void setOutput(String output) { this.output = output; }
    }
}

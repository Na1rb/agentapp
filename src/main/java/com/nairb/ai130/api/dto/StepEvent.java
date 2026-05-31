package com.nairb.ai130.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.nairb.ai130.common.enums.StepPhase;

/**
 * SSE 步骤事件 DTO。
 * <p>
 * 用于前端实时展示编排进度，事件类型包括：
 * <ul>
 *   <li>{@code step_start} — 进入某个阶段</li>
 *   <li>{@code step_result} — 子任务执行结果</li>
 *   <li>{@code step_error} — 子任务执行异常</li>
 *   <li>{@code step_complete} — 编排流程结束</li>
 * </ul>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StepEvent {

    /** 当前阶段 */
    private String phase;

    /** 当前子任务描述（EXECUTE 阶段） */
    private String task;

    /** 子任务 ID */
    private String taskId;

    /** 子任务执行输出 */
    private String output;

    /** 错误信息 */
    private String error;

    /** 提示消息 */
    private String message;

    /** 当前循环次数 */
    private Integer loopCount;

    /** 总循环次数（step_complete 事件） */
    private Integer totalLoops;

    // ==================== 工厂方法 ====================

    /** 从 StepState 构建 step_start 事件 */
    public static StepEvent startFrom(StepState state) {
        StepEvent e = new StepEvent();
        e.phase = state.getPhase().name();
        e.loopCount = state.getLoopCount();

        switch (state.getPhase()) {
            case ANALYZE:
                e.message = "正在分析您的请求...";
                break;
            case EXECUTE: {
                StepState.Task task = state.getCurrentTask();
                if (task != null) {
                    e.task = task.getDesc();
                    e.taskId = task.getId();
                }
                e.message = "正在执行: " + (task != null ? task.getDesc() : "未知任务");
                break;
            }
            case CHECK:
                e.message = "正在检查执行结果...";
                break;
            case SYNTHESIZE:
                e.message = "正在汇总全部结果...";
                break;
        }
        return e;
    }

    /** 构建 step_result 事件 */
    public static StepEvent resultFrom(StepState state, String taskId, String output) {
        StepEvent e = new StepEvent();
        e.phase = StepPhase.EXECUTE.name();
        e.taskId = taskId;
        e.output = output;
        e.loopCount = state.getLoopCount();
        return e;
    }

    /** 构建 step_error 事件 */
    public static StepEvent errorFrom(StepState state, String taskId, String error) {
        StepEvent e = new StepEvent();
        e.phase = StepPhase.EXECUTE.name();
        e.taskId = taskId;
        e.error = error;
        e.loopCount = state.getLoopCount();
        return e;
    }

    /** 构建 step_complete 事件 */
    public static StepEvent completeFrom(StepState state) {
        StepEvent e = new StepEvent();
        e.phase = StepPhase.SYNTHESIZE.name();
        e.totalLoops = state.getLoopCount();
        e.message = state.isMaxLoopsReached() && !state.allTasksDone()
                ? "已达到最大循环次数，汇总已有结果"
                : "所有任务已完成，正在汇总";
        return e;
    }

    // ==================== Getters / Setters ====================

    public String getPhase() { return phase; }
    public void setPhase(String phase) { this.phase = phase; }

    public String getTask() { return task; }
    public void setTask(String task) { this.task = task; }

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }

    public String getOutput() { return output; }
    public void setOutput(String output) { this.output = output; }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public Integer getLoopCount() { return loopCount; }
    public void setLoopCount(Integer loopCount) { this.loopCount = loopCount; }

    public Integer getTotalLoops() { return totalLoops; }
    public void setTotalLoops(Integer totalLoops) { this.totalLoops = totalLoops; }
}

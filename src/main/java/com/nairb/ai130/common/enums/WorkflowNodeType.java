package com.nairb.ai130.common.enums;

/**
 * 工作流节点类型枚举。
 * <p>
 * 定义了 DAG 工作流中所有支持的节点类型，每个节点对应一种处理语义。
 */
public enum WorkflowNodeType {

    /** 分析阶段：理解用户请求，拆分为目标 */
    ANALYSIS,

    /** 拆解阶段：将目标进一步分解为可执行的子任务列表 */
    DECOMPOSE,

    /** 执行阶段：执行一个具体的子任务（可能调用 MCP 工具） */
    EXECUTE,

    /** 检查阶段：对执行结果进行结构化校验，输出通过/失败判定 */
    CHECK,

    /** 汇总阶段：合并全部执行结果，生成最终回答 */
    SYNTHESIZE,

    /** 并行分裂：将执行流的 pending 任务分发到多个并行分支 */
    PARALLEL_SPLIT,

    /** 并行合并：等待所有并行分支完成，收集结果 */
    PARALLEL_MERGE,

    /** 条件分支：根据表达式计算动态路由 */
    CONDITION,

    /** 人工确认：暂停执行，等待用户确认后继续 */
    HUMAN_CONFIRM
}

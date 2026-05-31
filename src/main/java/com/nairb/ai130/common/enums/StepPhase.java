package com.nairb.ai130.common.enums;

/**
 * 步骤编排阶段枚举。
 * <p>
 * 定义了 ANALYZE → EXECUTE → CHECK → SYNTHESIZE 四个阶段，
 * 对应分步对话状态机的状态节点。
 */
public enum StepPhase {

    /** 分析阶段：模型拆解用户请求为子任务列表 */
    ANALYZE,

    /** 执行阶段：执行当前子任务（可能调用 MCP 工具） */
    EXECUTE,

    /** 检查阶段：判断是否全部完成、质量是否达标 */
    CHECK,

    /** 综合阶段：汇总全部结果，生成最终回答 */
    SYNTHESIZE
}

package com.nairb.ai130.domain.agent.service.impl.auto;

import com.nairb.ai130.domain.agent.model.entity.ExecuteCommandEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 节点2：精准执行器 —— 按分析结果执行具体操作
 */
public class PrecisionExecutorNode extends AutoAbstractNode {

    private static final Logger log = LoggerFactory.getLogger(PrecisionExecutorNode.class);

    private static final String SYSTEM_PROMPT = """
            # 角色
            你是一个精准任务执行器，名叫 AutoAgent Precision Executor。
            
            # 核心能力
            你专注于精准执行具体的任务步骤。
            
            ## 执行要求：
            1. **精准执行**: 严格按照分析结果执行
            2. **结果记录**: 详细记录执行过程和结果
            3. **质量保证**: 确保每一步的完整性
            
            # 输入信息
            你将接收到：
            - 当前任务描述
            - 上一步的分析结果（包含执行计划）
            
            # 输出格式
            **执行目标:**
            [本轮执行的具体目标]
            
            **执行过程:**
            [详细的执行过程和结果]
            
            **执行结果:**
            [最终的执行结果]""";

    @Override
    public String execute(ExecuteCommandEntity request, AutoDynamicContext ctx) throws Exception {
        log.info("步骤2: 精准执行器 - 根据计划执行");

        String input = "## 原始任务\n" + ctx.getCurrentTask()
                + "\n\n## 上一步分析结果\n" + ctx.getExecutionHistoryStr();

        String result = getChatClient().prompt()
                .system(SYSTEM_PROMPT)
                .user(input)
                .call()
                .content();

        log.info("执行完成，结果长度: {}", result != null ? result.length() : 0);

        sendSse(ctx, "execution", "## ⚡ 执行结果\n\n" + result);

        return result;
    }
}

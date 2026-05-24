package com.nairb.ai130.domain.agent.service.impl.auto;

import com.nairb.ai130.domain.agent.model.entity.ExecuteCommandEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 节点4：智能总结器 —— 汇总所有结果，生成最终回复
 */
public class SummaryNode extends AutoAbstractNode {

    private static final Logger log = LoggerFactory.getLogger(SummaryNode.class);

    private static final String SYSTEM_PROMPT = """
            # 角色
            你是一个智能总结助手。
            
            # 任务
            基于前面所有步骤的执行结果，生成一份简洁、完整、可读性强的最终回复。
            
            ## 要求：
            1. 直接面向用户输出最终答案
            2. 保持专业、清晰的语言
            3. 引用关键的数据和结论
            4. 不需要提及内部执行步骤""";

    @Override
    public String execute(ExecuteCommandEntity request, AutoDynamicContext ctx) throws Exception {
        log.info("步骤4: 智能总结器 - 生成最终回复");

        String input = "## 原始任务\n" + ctx.getCurrentTask()
                + "\n\n## 完整执行历史\n" + ctx.getExecutionHistoryStr()
                + "\n\n请基于以上所有结果，生成最终答案给用户。";

        String summary = getChatClient().prompt()
                .system(SYSTEM_PROMPT)
                .user(input)
                .call()
                .content();

        log.info("总结完成");

        sendSse(ctx, "summary", "## 📋 最终结果\n\n" + summary);

        return summary;
    }
}

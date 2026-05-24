package com.nairb.ai130.domain.agent.service.impl.flow;

import com.nairb.ai130.domain.agent.model.entity.ExecuteCommandEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 步骤4：结果汇总
 * 汇总所有执行结果，生成最终回复
 */
public class SummaryAggregationNode extends FlowAbstractNode {

    private static final Logger log = LoggerFactory.getLogger(SummaryAggregationNode.class);

    @Override
    public String execute(ExecuteCommandEntity request, FlowDynamicContext ctx) throws Exception {
        log.info("步骤4: 结果汇总");

        String executeResult = ctx.getValue("executeResult");
        if (executeResult == null) executeResult = "无执行结果";

        String summary = getChatClient().prompt()
                .system("""
                        # 角色
                        你是结果汇总专家。
                        
                        ## 任务
                        将所有执行结果汇总成一份清晰、完整的回复给用户。
                        
                        ## 要求
                        1. 直接输出最终结果，不提内部步骤
                        2. 结构清晰、专业
                        3. 如果有多步骤，合并输出""")
                .user("## 执行结果\n" + executeResult + "\n\n## 用户原始需求\n" + ctx.getCurrentTask()
                        + "\n\n请生成最终回复。")
                .call()
                .content();

        sendSse(ctx, "summary", "## 📊 最终汇总\n\n" + summary);
        return summary;
    }
}

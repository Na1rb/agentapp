package com.nairb.ai130.domain.agent.service.impl.flow;

import com.nairb.ai130.domain.agent.model.entity.ExecuteCommandEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 步骤2：执行规划
 * 基于工具分析结果，制定详细的执行计划
 */
public class PlanningNode extends FlowAbstractNode {

    private static final Logger log = LoggerFactory.getLogger(PlanningNode.class);

    @Override
    public String execute(ExecuteCommandEntity request, FlowDynamicContext ctx) throws Exception {
        log.info("步骤2: 执行规划");

        String history = ctx.getValue("history");
        if (history == null) history = "";

        String plan = getChatClient().prompt()
                .system("""
                        # 角色
                        你是执行规划师。基于工具分析结果，制定明确的执行计划。
                        
                        ## 要求
                        1. 把任务拆解成 3-5 个可执行的具体步骤
                        2. 每个步骤要有明确的目标和输出
                        3. 步骤之间要有清晰的依赖关系
                        
                        ## 输出格式
                        **执行计划:**
                        步骤1: [目标] → [方法]
                        步骤2: [目标] → [方法]
                        ...""")
                .user("## 工具分析结果\n" + history + "\n\n## 用户请求\n" + ctx.getCurrentTask())
                .call()
                .content();

        ctx.setValue("plan", plan);
        sendSse(ctx, "execution", "## 📋 执行计划\n\n" + plan);
        return plan;
    }
}

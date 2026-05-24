package com.nairb.ai130.domain.agent.service.impl.flow;

import com.nairb.ai130.domain.agent.model.entity.ExecuteCommandEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 步骤3：步骤解析与执行
 * 按计划逐步执行，每一步独立输出结果
 */
public class ParseExecuteNode extends FlowAbstractNode {

    private static final Logger log = LoggerFactory.getLogger(ParseExecuteNode.class);

    @Override
    public String execute(ExecuteCommandEntity request, FlowDynamicContext ctx) throws Exception {
        log.info("步骤3: 按计划执行");

        String plan = ctx.getValue("plan");
        if (plan == null) plan = "无执行计划";

        String result = getChatClient().prompt()
                .system("""
                        # 角色
                        你是一个精准的执行器。
                        
                        ## 任务
                        按照执行计划，逐步执行并输出结果。
                        
                        对于每一步：
                        1. 说明当前在做什么
                        2. 执行并给出结果
                        3. 记录执行状态（成功/失败）
                        
                        ## 输出格式
                        **执行记录:**
                        
                        ✅ 步骤1: [名称]
                        [执行过程和结果]
                        
                        ✅ 步骤2: [名称]
                        [执行过程和结果]""")
                .user("## 执行计划\n" + plan + "\n\n## 用户请求\n" + ctx.getCurrentTask())
                .call()
                .content();

        ctx.setValue("executeResult", result);
        sendSse(ctx, "execution", "## ⚙️ 执行记录\n\n" + result);
        return result;
    }
}

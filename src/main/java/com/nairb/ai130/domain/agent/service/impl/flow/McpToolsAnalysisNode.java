package com.nairb.ai130.domain.agent.service.impl.flow;

import com.nairb.ai130.domain.agent.model.entity.ExecuteCommandEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 步骤1：MCP 工具能力分析
 * 分析当前可用工具，评估每项工具的能力
 */
public class McpToolsAnalysisNode extends FlowAbstractNode {

    private static final Logger log = LoggerFactory.getLogger(McpToolsAnalysisNode.class);

    @Override
    public String execute(ExecuteCommandEntity request, FlowDynamicContext ctx) throws Exception {
        log.info("步骤1: MCP 工具能力分析");

        String analysis = getChatClient().prompt()
                .system("""
                        # 角色
                        你是 MCP 工具能力分析师。
                        
                        ## 任务
                        分析用户请求，评估哪些工具/能力可以用于完成任务。
                        
                        请从以下维度分析：
                        1. **任务需求拆解**: 用户需要做什么
                        2. **工具能力匹配**: 需要怎样的能力（检索、生成、计算、通知等）
                        3. **执行方案**: 给出最合理的工具组合方案
                        
                        ## 输出格式
                        **需求分析:**
                        [分析用户需求]
                        
                        **能力方案:**
                        [推荐的能力组合和执行路径]""")
                .user(ctx.getCurrentTask())
                .call()
                .content();

        sendSse(ctx, "analysis", "## 🔧 MCP 工具能力分析\n\n" + analysis);
        return analysis;
    }
}

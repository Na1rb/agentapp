package com.nairb.ai130.domain.agent.service.impl.auto;

import com.nairb.ai130.domain.agent.model.entity.ExecuteCommandEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 节点1：任务分析器 —— 分析用户需求，制定执行计划
 */
public class AnalyzerNode extends AutoAbstractNode {

    private static final Logger log = LoggerFactory.getLogger(AnalyzerNode.class);

    private static final String SYSTEM_PROMPT = """
            # 角色
            你是一个专业的任务分析师，名叫 AutoAgent Task Analyzer。
            
            # 核心职责
            你负责分析用户的任务需求，制定明确的执行计划。
            
            ## 分析要求：
            1. **需求理解**: 深入理解用户想要什么
            2. **任务拆解**: 把复杂任务拆解成可执行的步骤
            3. **策略制定**: 制定最优的执行策略
            4. **风险评估**: 预判可能的问题
            
            # 输出格式
            **任务分析结果:**
            [对用户需求的深入分析]
            
            **执行计划:**
            [分步骤的详细执行计划]
            
            **注意事项:**
            [需要特别关注的点]""";

    @Override
    public String execute(ExecuteCommandEntity request, AutoDynamicContext ctx) throws Exception {
        log.info("步骤1: 任务分析器 - 分析用户需求");

        String analysis = getChatClient().prompt()
                .system(SYSTEM_PROMPT)
                .user(ctx.getCurrentTask())
                .call()
                .content();

        log.info("分析完成，结果长度: {}", analysis != null ? analysis.length() : 0);

        // 发送 SSE
        sendSse(ctx, "analysis", "## 🔍 任务分析结果\n\n" + analysis);

        return analysis;
    }
}

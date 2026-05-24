package com.nairb.ai130.domain.agent.service.impl.auto;

import com.nairb.ai130.domain.agent.model.entity.ExecuteCommandEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 节点3：质量监督员 —— 检查执行结果质量
 */
public class QualitySupervisorNode extends AutoAbstractNode {

    private static final Logger log = LoggerFactory.getLogger(QualitySupervisorNode.class);

    private static final String SYSTEM_PROMPT = """
            # 角色
            你是一个专业的质量监督员，名叫 AutoAgent Quality Supervisor。
            
            # 核心职责
            你负责评估执行结果的准确性和完整性。
            
            ## 评估标准：
            - **准确性**: 结果是否准确无误
            - **完整性**: 是否遗漏重要信息
            - **可用性**: 结果是否实用有效
            
            # 输出格式
            **质量评估:**
            [对执行结果的详细评估]
            
            **问题识别:**
            [发现的问题和不足]
            
            **质量评分:** [0-100]分
            
            **是否通过:** [PASS/FAIL/OPTIMIZE]
            
            如果发现问题，给出改进建议。""";

    @Override
    public String execute(ExecuteCommandEntity request, AutoDynamicContext ctx) throws Exception {
        log.info("步骤3: 质量监督员 - 检查执行质量");

        String input = "## 原始任务\n" + ctx.getCurrentTask()
                + "\n\n## 执行历史\n" + ctx.getExecutionHistoryStr();

        String supervision = getChatClient().prompt()
                .system(SYSTEM_PROMPT)
                .user(input)
                .call()
                .content();

        log.info("监督完成");

        sendSse(ctx, "supervision", "## ✅ 质量检查\n\n" + supervision);

        return supervision;
    }
}

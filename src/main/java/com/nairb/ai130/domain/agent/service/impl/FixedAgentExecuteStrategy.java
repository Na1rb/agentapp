package com.nairb.ai130.domain.agent.service.impl;

import com.nairb.ai130.app.DynamicModelFactory;
import com.nairb.ai130.domain.agent.model.entity.ExecuteCommandEntity;
import com.nairb.ai130.domain.agent.service.IExecuteStrategy;
import com.nairb.ai130.infrastructure.dao.AiAgentFlowConfigDao;
import com.nairb.ai130.infrastructure.dao.po.AiAgentFlowConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import java.util.List;

/**
 * 固定执行策略（Fixed Agent）
 *
 * 工作流：
 * 1. 查 ai_agent_flow_config 获取客户端列表（按 sequence 排序）
 * 2. 遍历每个客户端，用其 step_prompt 作为 system 提示词调 AI
 * 3. 上一步的输出作为下一步的输入（串联）
 * 4. 每步结果通过 SSE 推送到前端
 *
 * Bean 名称 = "fixedAgentExecuteStrategy"，与 ai_agent.strategy 字段值对应
 */
@Service("fixedAgentExecuteStrategy")
public class FixedAgentExecuteStrategy implements IExecuteStrategy {

    private static final Logger log = LoggerFactory.getLogger(FixedAgentExecuteStrategy.class);

    private final AiAgentFlowConfigDao flowConfigDao;
    private final DynamicModelFactory modelFactory;

    public FixedAgentExecuteStrategy(AiAgentFlowConfigDao flowConfigDao,
                                     DynamicModelFactory modelFactory) {
        this.flowConfigDao = flowConfigDao;
        this.modelFactory = modelFactory;
    }

    @Override
    public void execute(ExecuteCommandEntity request, ResponseBodyEmitter emitter) throws Exception {
        // 1. 查流程配置（按 sequence 排序的步骤列表）
        List<AiAgentFlowConfig> steps = flowConfigDao.findByAgentIdOrderBySeq(request.getAiAgentId());
        if (steps.isEmpty()) {
            AgentDispatchServiceImpl.sendSse(emitter, "error", "该 Agent 未配置执行步骤");
            return;
        }

        log.info("FixedAgent 开始执行: agentId={}, steps={}", request.getAiAgentId(), steps.size());

        // 2. 获取 ChatClient（复用现有动态模型工厂）
        ChatClient chatClient = modelFactory.getChatClient(null);

        // 3. 串行执行每个步骤
        String context = request.getMessage();

        for (int i = 0; i < steps.size(); i++) {
            AiAgentFlowConfig step = steps.get(i);
            String stepPrompt = step.getStepPrompt();
            String currentContext = context; // copy to effectively-final variable for lambda

            log.info("执行步骤 {}/{}: clientName={}, prompt={}",
                    i + 1, steps.size(), step.getClientName(), stepPrompt);

            // 调 AI：上一步输出 + 本步 system prompt
            String reply = chatClient.prompt()
                    .system(s -> s.text(stepPrompt))
                    .user(u -> u.text(currentContext))
                    .call()
                    .content();

            log.info("步骤 {} 完成，结果长度: {}", i + 1, reply != null ? reply.length() : 0);

            // SSE 推送本步骤结果
            String ssePayload = "步骤" + (i + 1) + "/" + steps.size() +
                    " **" + step.getClientName() + "**\n\n" + reply;
            AgentDispatchServiceImpl.sendSse(emitter, "execution", ssePayload);

            // 上一步输出作为下一步输入
            context = reply;
        }

        // 4. 发送最终结果
        AgentDispatchServiceImpl.sendSse(emitter, "summary", context);
        log.info("FixedAgent 执行完成: agentId={}", request.getAiAgentId());
    }
}

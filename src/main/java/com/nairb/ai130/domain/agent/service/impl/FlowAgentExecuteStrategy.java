package com.nairb.ai130.domain.agent.service.impl;

import com.nairb.ai130.app.DynamicModelFactory;
import com.nairb.ai130.domain.agent.model.entity.ExecuteCommandEntity;
import com.nairb.ai130.domain.agent.service.IExecuteStrategy;
import com.nairb.ai130.domain.agent.service.impl.flow.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

/**
 * 流程执行策略（Flow Agent）
 *
 * 4 步责任链：
 *   MCP分析 → 规划 → 解析执行 → 汇总
 *
 * 侧重于工具/能力分析后的流程化执行
 *
 * Bean 名称 = "flowAgentExecuteStrategy"
 */
@Service("flowAgentExecuteStrategy")
public class FlowAgentExecuteStrategy implements IExecuteStrategy {

    private static final Logger log = LoggerFactory.getLogger(FlowAgentExecuteStrategy.class);

    private final DynamicModelFactory modelFactory;

    public FlowAgentExecuteStrategy(DynamicModelFactory modelFactory) {
        this.modelFactory = modelFactory;
    }

    @Override
    public void execute(ExecuteCommandEntity request, ResponseBodyEmitter emitter) throws Exception {
        log.info("FlowAgent 开始执行: agentId={}, message={}", request.getAiAgentId(), request.getMessage());

        // 1. 构建责任链
        McpToolsAnalysisNode node1 = new McpToolsAnalysisNode();
        PlanningNode node2 = new PlanningNode();
        ParseExecuteNode node3 = new ParseExecuteNode();
        SummaryAggregationNode node4 = new SummaryAggregationNode();

        node1.setModelFactory(modelFactory);
        node2.setModelFactory(modelFactory);
        node3.setModelFactory(modelFactory);
        node4.setModelFactory(modelFactory);

        node1.setNextNode(node2);
        node2.setNextNode(node3);
        node3.setNextNode(node4);
        node4.setNextNode(null);

        // 2. 创建上下文
        FlowDynamicContext ctx = new FlowDynamicContext();
        ctx.setMaxStep(request.getMaxStep() != null ? request.getMaxStep() : 4);
        ctx.setCurrentTask(request.getMessage());
        ctx.setSessionId(request.getSessionId());
        ctx.setValue("emitter", emitter);

        // 3. 执行
        node1.apply(request, ctx);

        // 4. 完成
        AgentDispatchServiceImpl.sendSse(emitter, "complete", "Flow Agent 流程执行完成 ✅");
        log.info("FlowAgent 执行完成: agentId={}", request.getAiAgentId());
    }
}

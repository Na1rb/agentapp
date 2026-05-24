package com.nairb.ai130.domain.agent.service.impl;

import com.nairb.ai130.app.DynamicModelFactory;
import com.nairb.ai130.domain.agent.model.entity.AgentExecuteResultEntity;
import com.nairb.ai130.domain.agent.model.entity.ExecuteCommandEntity;
import com.nairb.ai130.domain.agent.service.IExecuteStrategy;
import com.nairb.ai130.domain.agent.service.impl.auto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

/**
 * 自动执行策略（Auto Agent）
 *
 * 4 步责任链：
 *   分析器 → 执行器 → 监督员 → 总结器
 *
 * 每步都用不同的 System Prompt 调大模型，结果通过 SSE 推送到前端
 *
 * Bean 名称 = "autoAgentExecuteStrategy"
 */
@Service("autoAgentExecuteStrategy")
public class AutoAgentExecuteStrategy implements IExecuteStrategy {

    private static final Logger log = LoggerFactory.getLogger(AutoAgentExecuteStrategy.class);

    private final DynamicModelFactory modelFactory;

    public AutoAgentExecuteStrategy(DynamicModelFactory modelFactory) {
        this.modelFactory = modelFactory;
    }

    @Override
    public void execute(ExecuteCommandEntity request, ResponseBodyEmitter emitter) throws Exception {
        log.info("AutoAgent 开始执行: agentId={}, message={}", request.getAiAgentId(), request.getMessage());

        // 1. 构建责任链：分析器 → 执行器 → 监督员 → 总结器
        AnalyzerNode analyzer = new AnalyzerNode();
        PrecisionExecutorNode executor = new PrecisionExecutorNode();
        QualitySupervisorNode supervisor = new QualitySupervisorNode();
        SummaryNode summary = new SummaryNode();

        analyzer.setModelFactory(modelFactory);
        executor.setModelFactory(modelFactory);
        supervisor.setModelFactory(modelFactory);
        summary.setModelFactory(modelFactory);

        analyzer.setNextNode(executor);
        executor.setNextNode(supervisor);
        supervisor.setNextNode(summary);
        summary.setNextNode(null);

        // 2. 创建上下文
        AutoDynamicContext ctx = new AutoDynamicContext();
        ctx.setMaxStep(request.getMaxStep() != null ? request.getMaxStep() : 4);
        ctx.setCurrentTask(request.getMessage());
        ctx.setSessionId(request.getSessionId());
        ctx.setValue("emitter", emitter);

        // 3. 执行责任链
        analyzer.apply(request, ctx);

        // 4. 推送完成标识
        AgentDispatchServiceImpl.sendSse(emitter, "complete", "Agent 任务执行完成 ✅");
        log.info("AutoAgent 执行完成: agentId={}", request.getAiAgentId());
    }
}

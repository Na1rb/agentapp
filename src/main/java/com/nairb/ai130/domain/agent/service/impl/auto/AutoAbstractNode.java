package com.nairb.ai130.domain.agent.service.impl.auto;

import com.nairb.ai130.app.DynamicModelFactory;
import com.nairb.ai130.domain.agent.model.entity.ExecuteCommandEntity;
import com.nairb.ai130.domain.agent.service.impl.AgentDispatchServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

/**
 * AutoAgent 执行策略 —— 抽象节点基类
 */
public abstract class AutoAbstractNode {

    private static final Logger log = LoggerFactory.getLogger(AutoAbstractNode.class);

    protected DynamicModelFactory modelFactory;
    protected AutoAbstractNode nextNode;

    protected AutoAbstractNode() {}

    public void setModelFactory(DynamicModelFactory modelFactory) { this.modelFactory = modelFactory; }
    public void setNextNode(AutoAbstractNode nextNode) { this.nextNode = nextNode; }

    public abstract String execute(ExecuteCommandEntity request, AutoDynamicContext ctx) throws Exception;

    public String apply(ExecuteCommandEntity request, AutoDynamicContext ctx) throws Exception {
        if (ctx.getStep() > ctx.getMaxStep()) {
            log.info("AutoAgent 达到最大步数，结束: step={}, maxStep={}", ctx.getStep(), ctx.getMaxStep());
            return ctx.getExecutionHistoryStr();
        }
        log.info("--- AutoAgent Step {}/{} ---", ctx.getStep(), ctx.getMaxStep());
        String result = execute(request, ctx);
        ctx.setExecutionHistoryStr(ctx.getExecutionHistoryStr() + "\n\n" + result);
        ctx.setStep(ctx.getStep() + 1);
        if (nextNode != null) return nextNode.apply(request, ctx);
        return result;
    }

    protected ChatClient getChatClient() { return modelFactory.getChatClient(null); }

    protected void sendSse(AutoDynamicContext ctx, String type, String content) {
        try {
            ResponseBodyEmitter emitter = ctx.getValue("emitter");
            if (emitter != null) AgentDispatchServiceImpl.sendSse(emitter, type, content);
        } catch (Exception e) { log.warn("SSE 发送失败: {}", e.getMessage()); }
    }
}

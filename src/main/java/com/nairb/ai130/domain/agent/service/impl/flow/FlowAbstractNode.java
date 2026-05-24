package com.nairb.ai130.domain.agent.service.impl.flow;

import com.nairb.ai130.app.DynamicModelFactory;
import com.nairb.ai130.domain.agent.model.entity.ExecuteCommandEntity;
import com.nairb.ai130.domain.agent.service.impl.AgentDispatchServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

/**
 * FlowAgent 执行策略 —— 抽象节点基类
 */
public abstract class FlowAbstractNode {

    private static final Logger log = LoggerFactory.getLogger(FlowAbstractNode.class);

    protected DynamicModelFactory modelFactory;
    protected FlowAbstractNode nextNode;

    protected FlowAbstractNode() {}

    public void setModelFactory(DynamicModelFactory modelFactory) { this.modelFactory = modelFactory; }
    public void setNextNode(FlowAbstractNode nextNode) { this.nextNode = nextNode; }

    public abstract String execute(ExecuteCommandEntity request, FlowDynamicContext ctx) throws Exception;

    public String apply(ExecuteCommandEntity request, FlowDynamicContext ctx) throws Exception {
        if (ctx.getStep() > ctx.getMaxStep()) return "完成";
        log.info("--- FlowAgent Step {}/{} ---", ctx.getStep(), ctx.getMaxStep());
        String result = execute(request, ctx);
        ctx.setStep(ctx.getStep() + 1);
        if (nextNode != null) return nextNode.apply(request, ctx);
        return result;
    }

    protected ChatClient getChatClient() { return modelFactory.getChatClient(null); }

    protected void sendSse(FlowDynamicContext ctx, String type, String content) {
        try {
            ResponseBodyEmitter emitter = ctx.getValue("emitter");
            if (emitter != null) AgentDispatchServiceImpl.sendSse(emitter, type, content);
        } catch (Exception e) { log.warn("SSE 发送失败: {}", e.getMessage()); }
    }
}

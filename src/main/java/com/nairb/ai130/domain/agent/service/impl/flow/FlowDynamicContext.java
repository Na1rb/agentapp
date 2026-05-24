package com.nairb.ai130.domain.agent.service.impl.flow;

import com.nairb.ai130.domain.agent.service.impl.framework.AbstractStrategyRouter;

/**
 * FlowAgent 执行策略 —— 动态上下文
 */
public class FlowDynamicContext extends AbstractStrategyRouter.DynamicContext {

    private String sessionId;

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }
}

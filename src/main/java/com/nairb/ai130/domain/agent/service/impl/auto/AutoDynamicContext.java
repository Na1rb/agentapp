package com.nairb.ai130.domain.agent.service.impl.auto;

import com.nairb.ai130.domain.agent.model.entity.ExecuteCommandEntity;
import com.nairb.ai130.domain.agent.service.impl.framework.AbstractStrategyRouter;

import java.util.HashMap;
import java.util.Map;

/**
 * AutoAgent 执行策略 —— 动态上下文
 */
public class AutoDynamicContext extends AbstractStrategyRouter.DynamicContext {

    /** 当前会话ID */
    private String sessionId;

    /** 执行历史文本 */
    private String executionHistoryStr = "";

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getExecutionHistoryStr() {
        return executionHistoryStr;
    }

    public void setExecutionHistoryStr(String executionHistoryStr) {
        this.executionHistoryStr = executionHistoryStr;
    }
}

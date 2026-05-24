package com.nairb.ai130.domain.agent.service;

import com.nairb.ai130.domain.agent.model.entity.ExecuteCommandEntity;
import com.nairb.ai130.infrastructure.dao.po.AiAgent;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import java.util.List;

/**
 * Agent 策略调度器接口
 */
public interface IAgentDispatchService {

    /**
     * 调度 Agent 执行
     * @param request  执行命令
     * @param emitter  SSE 流式输出器
     */
    void dispatch(ExecuteCommandEntity request, ResponseBodyEmitter emitter);

    /**
     * 获取所有可用的 Agent 列表
     */
    List<AiAgent> listAvailableAgents();
}

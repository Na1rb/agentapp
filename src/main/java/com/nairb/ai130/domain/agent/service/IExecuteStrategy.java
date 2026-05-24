package com.nairb.ai130.domain.agent.service;

import com.nairb.ai130.domain.agent.model.entity.ExecuteCommandEntity;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

/**
 * 执行策略接口 —— 所有策略实现此接口，Spring 自动注册到 Map
 */
public interface IExecuteStrategy {

    /**
     * 执行策略
     * @param request  执行命令
     * @param emitter  SSE 流式输出器
     */
    void execute(ExecuteCommandEntity request, ResponseBodyEmitter emitter) throws Exception;
}

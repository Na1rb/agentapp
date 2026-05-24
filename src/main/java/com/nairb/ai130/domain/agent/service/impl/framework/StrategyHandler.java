package com.nairb.ai130.domain.agent.service.impl.framework;

/**
 * 策略处理器接口 —— 责任链模式
 */
public interface StrategyHandler<I, C, O> {

    /**
     * 执行处理
     * @param request  入参
     * @param context  动态上下文
     * @return 处理结果
     */
    O apply(I request, C context) throws Exception;
}

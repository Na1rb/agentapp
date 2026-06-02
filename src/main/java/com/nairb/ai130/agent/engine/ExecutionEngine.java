package com.nairb.ai130.agent.engine;

import com.nairb.ai130.domain.entity.AgentFlowStep;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * Agent 执行引擎接口。
 * <p>
 * {@code strategy} 字段映射到不同的实现：
 * <ul>
 *   <li>normal → {@link SequentialEngine}</li>
 *   <li>react  → (预留)</li>
 *   <li>rag    → (预留)</li>
 * </ul>
 */
public interface ExecutionEngine {

    /**
     * 执行 Agent 流程，返回 SSE 事件流。
     *
     * @param sessionId   会话 ID
     * @param userInput   用户输入消息
     * @param steps       按序列排序的步骤列表
     * @param modelCode   前端选中的模型 code（可为 null）
     * @return SSE 事件流（step_start / step_thinking / step_result / done / step_error）
     */
    Flux<ServerSentEvent<String>> execute(String sessionId,
                                           String userInput,
                                           List<AgentFlowStep> steps,
                                           String modelCode);
}

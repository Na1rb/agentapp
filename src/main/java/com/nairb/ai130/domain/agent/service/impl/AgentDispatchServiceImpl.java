package com.nairb.ai130.domain.agent.service.impl;

import com.nairb.ai130.domain.agent.model.entity.ExecuteCommandEntity;
import com.nairb.ai130.domain.agent.service.IAgentDispatchService;
import com.nairb.ai130.domain.agent.service.IExecuteStrategy;
import com.nairb.ai130.infrastructure.dao.AiAgentDao;
import com.nairb.ai130.infrastructure.dao.po.AiAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Agent 策略调度器实现
 *
 * 工作流：
 * 1. 查 ai_agent 表，获取 strategy 字段值（如 "fixedAgentExecuteStrategy"）
 * 2. strategy 值就是 Spring Bean 的名称，从 Map 中拿到对应的策略实现
 * 3. 提交到线程池异步执行
 */
@Service
public class AgentDispatchServiceImpl implements IAgentDispatchService {

    private static final Logger log = LoggerFactory.getLogger(AgentDispatchServiceImpl.class);

    /**
     * Spring 会自动注入所有 IExecuteStrategy 实现类，key = Bean 名称
     * 例如：
     *   "fixedAgentExecuteStrategy" → FixedAgentExecuteStrategy
     *   "autoAgentExecuteStrategy"  → AutoAgentExecuteStrategy
     *   "flowAgentExecuteStrategy"  → FlowAgentExecuteStrategy
     */
    private final Map<String, IExecuteStrategy> executeStrategyMap;
    private final AiAgentDao aiAgentDao;
    private final ThreadPoolExecutor threadPoolExecutor;

    public AgentDispatchServiceImpl(Map<String, IExecuteStrategy> executeStrategyMap,
                                    AiAgentDao aiAgentDao,
                                    ThreadPoolExecutor threadPoolExecutor) {
        this.executeStrategyMap = executeStrategyMap;
        this.aiAgentDao = aiAgentDao;
        this.threadPoolExecutor = threadPoolExecutor;
    }

    @Override
    public void dispatch(ExecuteCommandEntity request, ResponseBodyEmitter emitter) {
        // 1. 查数据库获取 Agent 配置
        AiAgent agent = aiAgentDao.findByAgentId(request.getAiAgentId());
        if (agent == null) {
            sendError(emitter, "智能体不存在: " + request.getAiAgentId());
            return;
        }

        // 2. 根据 strategy 字段找到对应的执行策略 Bean
        String strategy = agent.getStrategy();
        IExecuteStrategy executor = executeStrategyMap.get(strategy);
        if (executor == null) {
            sendError(emitter, "不存在的执行策略: " + strategy);
            return;
        }

        log.info("Agent 调度开始: agentId={}, strategy={}, message={}",
                request.getAiAgentId(), strategy, request.getMessage());

        // 3. 异步执行（不阻塞 HTTP 线程）
        threadPoolExecutor.execute(() -> {
            try {
                executor.execute(request, emitter);
            } catch (Exception e) {
                log.error("Agent 执行异常: {}", e.getMessage(), e);
                sendSse(emitter, "error", "执行异常: " + e.getMessage());
            } finally {
                try {
                    emitter.complete();
                } catch (Exception e) {
                    log.error("emitter.complete 失败", e);
                }
            }
        });
    }

    // ============ 工具方法 ============

    private void sendError(ResponseBodyEmitter emitter, String msg) {
        try {
            sendSse(emitter, "error", msg);
            emitter.complete();
        } catch (Exception ignored) {}
    }

    public static void sendSse(ResponseBodyEmitter emitter, String type, String content) {
        try {
            String sseData = "data: " + content + "\n\n";
            emitter.send(sseData);
        } catch (Exception e) {
            throw new RuntimeException("SSE 发送失败", e);
        }
    }

    @Override
    public List<AiAgent> listAvailableAgents() {
        return aiAgentDao.findAllEnabled();
    }
}

package com.nairb.ai130.app;

import com.nairb.ai130.agent.engine.EngineFactory;
import com.nairb.ai130.agent.engine.ExecutionEngine;
import com.nairb.ai130.common.exception.BusinessException;
import com.nairb.ai130.domain.entity.AgentConfig;
import com.nairb.ai130.domain.agent.AgentExecutionOptions;
import com.nairb.ai130.domain.entity.AgentExecutionLog;
import com.nairb.ai130.domain.entity.AgentFlowStep;
import com.nairb.ai130.domain.repository.AgentConfigRepository;
import com.nairb.ai130.domain.repository.AgentExecutionLogRepository;
import com.nairb.ai130.domain.repository.AgentFlowStepRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * Agent 应用服务 — Agent CRUD + 执行入口。
 */
@Service
public class AgentAppService {

    private static final Logger log = LoggerFactory.getLogger(AgentAppService.class);

    private final AgentConfigRepository configRepo;
    private final AgentFlowStepRepository stepRepo;
    private final AgentExecutionLogRepository logRepo;
    private final EngineFactory engineFactory;

    public AgentAppService(AgentConfigRepository configRepo,
                           AgentFlowStepRepository stepRepo,
                           AgentExecutionLogRepository logRepo,
                           EngineFactory engineFactory) {
        this.configRepo = configRepo;
        this.stepRepo = stepRepo;
        this.logRepo = logRepo;
        this.engineFactory = engineFactory;
    }

    // ==================== Agent CRUD ====================

    public List<AgentConfig> listEnabled() {
        return configRepo.findEnabled();
    }

    public List<AgentConfig> listAll() {
        return configRepo.findAll();
    }

    public AgentConfig getById(String agentId) {
        return configRepo.findById(agentId)
                .orElseThrow(() -> new BusinessException(404, "Agent not found: " + agentId));
    }

    @Transactional
    public AgentConfig create(AgentConfig config) {
        normalizeLimits(config);
        configRepo.save(config);
        return config;
    }

    @Transactional
    public AgentConfig update(String agentId, AgentConfig config) {
        getById(agentId); // 校验存在
        config.setAgentId(agentId);
        normalizeLimits(config);
        configRepo.update(config);
        return config;
    }

    @Transactional
    public void delete(String agentId) {
        getById(agentId);
        stepRepo.deleteByAgentId(agentId);
        configRepo.deleteById(agentId);
    }

    private void normalizeLimits(AgentConfig config) {
        int maxRound = config.getMaxRound() != null ? config.getMaxRound() : 5;
        int maxPace = config.getMaxPace() != null ? config.getMaxPace() : 10;
        config.setMaxRound(Math.max(1, Math.min(maxRound, 20)));
        config.setMaxPace(Math.max(1, Math.min(maxPace, 50)));
    }

    // ==================== Flow Steps CRUD ====================

    public List<AgentFlowStep> getFlowSteps(String agentId) {
        return stepRepo.findByAgentId(agentId);
    }

    @Transactional
    public List<AgentFlowStep> saveFlowSteps(String agentId, List<AgentFlowStep> steps) {
        getById(agentId); // 校验 Agent 存在
        for (AgentFlowStep step : steps) {
            int retryLimit = step.getRetryLimit() != null ? step.getRetryLimit() : 2;
            step.setRetryLimit(Math.max(0, Math.min(retryLimit, 10)));
        }
        stepRepo.replaceAll(agentId, steps);
        return steps;
    }

    // ==================== Agent 执行 ====================

    /**
     * 执行 Agent 流程，返回 SSE 事件流。
     *
     * @param agentId    Agent 标识
     * @param sessionId  前端传入的会话 ID
     * @param userInput  用户输入
     * @param modelCode  选中的模型 code（可为 null）
     * @param userId     用户 ID（可为 null）
     */
    public Flux<ServerSentEvent<String>> execute(String agentId, String sessionId,
                                                  String userInput, String modelCode, Long userId) {
        // 1. 校验 Agent
        AgentConfig agent = configRepo.findById(agentId)
                .orElseThrow(() -> new BusinessException(404, "Agent not found: " + agentId));
        if (agent.getStatus() != 1) {
            throw new BusinessException(403, "Agent is disabled: " + agentId);
        }

        // 2. 选择执行引擎并加载可选步骤
        ExecutionEngine engine = engineFactory.getEngine(agent.getStrategy());
        List<AgentFlowStep> steps = stepRepo.findByAgentId(agentId);
        if ("normal".equals(engine.strategy()) && steps.isEmpty()) {
            throw new BusinessException(400, "Agent has no flow steps: " + agentId);
        }

        // 3. 创建执行日志
        AgentExecutionLog execLog = new AgentExecutionLog();
        execLog.setAgentId(agentId);
        execLog.setSessionId(sessionId);
        execLog.setUserId(userId);
        execLog.setStatus("running");
        long logId = logRepo.insert(execLog);
        log.info("[{}] Agent execution started: logId={}, agentId={}, steps={}",
                sessionId, logId, agentId, steps.size());

        // 4. 执行并写入日志
        AgentExecutionOptions options = new AgentExecutionOptions(
                logId,
                agent.getMaxRound() != null ? agent.getMaxRound() : 5,
                agent.getMaxPace() != null ? agent.getMaxPace() : 10);
        return engine.execute(sessionId, userInput, steps, modelCode, options)
                .doOnComplete(() -> {
                    logRepo.updateResult(logId, "done", null, null, null);
                    log.info("[{}] Agent execution completed: logId={}", sessionId, logId);
                })
                .doOnError(e -> {
                    logRepo.updateResult(logId, "failed", null, null, e.getMessage());
                    log.error("[{}] Agent execution failed: logId={}", sessionId, logId, e);
                });
    }
}

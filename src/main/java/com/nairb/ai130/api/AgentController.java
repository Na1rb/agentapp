package com.nairb.ai130.api;

import com.nairb.ai130.api.dto.ExecuteAgentRequestDTO;
import com.nairb.ai130.domain.agent.model.entity.ExecuteCommandEntity;
import com.nairb.ai130.domain.agent.service.IAgentDispatchService;
import com.nairb.ai130.infrastructure.dao.po.AiAgent;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import java.util.List;
import java.util.UUID;

/**
 * Agent 智能体执行控制器
 *
 * 端点：POST /api/agent/execute
 * 类型：SSE 流式响应（text/event-stream）
 */
@RestController
@RequestMapping("/api/agent")
@CrossOrigin(origins = "*")
public class AgentController {

    private static final Logger log = LoggerFactory.getLogger(AgentController.class);

    private final IAgentDispatchService agentDispatchService;

    public AgentController(IAgentDispatchService agentDispatchService) {
        this.agentDispatchService = agentDispatchService;
    }

    @PostMapping(value = "/execute", produces = "text/event-stream;charset=UTF-8")
    public ResponseBodyEmitter executeAgent(@RequestBody ExecuteAgentRequestDTO request,
                                            HttpServletResponse response) {
        log.info("Agent 执行请求: agentId={}, message={}", request.getAiAgentId(), request.getMessage());

        // 设置 SSE 响应头
        response.setContentType("text/event-stream");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Connection", "keep-alive");
        response.setHeader("X-Accel-Buffering", "no");

        // 创建流式输出器（永不过期）
        ResponseBodyEmitter emitter = new ResponseBodyEmitter(Long.MAX_VALUE);

        // 构建执行命令
        ExecuteCommandEntity cmd = ExecuteCommandEntity.builder()
                .aiAgentId(request.getAiAgentId())
                .message(request.getMessage())
                .sessionId(request.getSessionId() != null ? request.getSessionId() : UUID.randomUUID().toString())
                .maxStep(request.getMaxStep())
                .build();

        // 调度执行（内部会提交到线程池异步执行）
        agentDispatchService.dispatch(cmd, emitter);

        return emitter;
    }

    /**
     * 获取可用 Agent 列表（供前端下拉选择）
     */
    @GetMapping("/list")
    public List<AiAgent> listAgents() {
        return agentDispatchService.listAvailableAgents();
    }
}

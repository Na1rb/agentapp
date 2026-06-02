package com.nairb.ai130.api;

import com.nairb.ai130.app.AgentAppService;
import com.nairb.ai130.common.exception.BusinessException;
import com.nairb.ai130.common.response.ApiResponse;
import com.nairb.ai130.domain.entity.AgentConfig;
import com.nairb.ai130.domain.entity.AgentFlowStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping
@CrossOrigin(origins = "*")
public class AgentController {

    private static final Logger log = LoggerFactory.getLogger(AgentController.class);
    private final AgentAppService agentService;

    public AgentController(AgentAppService agentService) {
        this.agentService = agentService;
    }

    // ==================== 前台 ====================

    /**
     * 获取可用 Agent 列表。
     */
    @GetMapping("/api/agent/list")
    public ResponseEntity<ApiResponse<List<AgentConfig>>> listEnabled() {
        return ResponseEntity.ok(ApiResponse.success(agentService.listEnabled()));
    }

    /**
     * 执行 Agent（SSE 流）。
     */
    @PostMapping(value = "/api/agent/execute", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> execute(@RequestBody Map<String, Object> body) {
        String agentId = String.valueOf(body.get("aiAgentId"));
        String message = String.valueOf(body.getOrDefault("message", ""));
        String sessionId = String.valueOf(body.getOrDefault("sessionId", ""));
        String modelCode = body.get("modelCode") != null ? String.valueOf(body.get("modelCode")) : null;
        Long userId = body.get("userId") instanceof Number ? ((Number) body.get("userId")).longValue() : null;

        log.info("Agent execute: agentId={}, sessionId={}, modelCode={}", agentId, sessionId, modelCode);
        return agentService.execute(agentId, sessionId, message, modelCode, userId);
    }

    // ==================== 管理端: Agent CRUD ====================

    @GetMapping("/admin/agents")
    public ResponseEntity<ApiResponse<List<AgentConfig>>> listAll() {
        return ResponseEntity.ok(ApiResponse.success(agentService.listAll()));
    }

    @PostMapping("/admin/agents")
    public ResponseEntity<ApiResponse<AgentConfig>> create(@RequestBody AgentConfig config) {
        try {
            return ResponseEntity.ok(ApiResponse.success(agentService.create(config)));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.badRequest(e.getMessage()));
        }
    }

    @PutMapping("/admin/agents/{agentId}")
    public ResponseEntity<ApiResponse<AgentConfig>> update(@PathVariable String agentId,
                                                            @RequestBody AgentConfig config) {
        try {
            return ResponseEntity.ok(ApiResponse.success(agentService.update(agentId, config)));
        } catch (BusinessException e) {
            return ResponseEntity.status(e.getCode() >= 500 ? 500 : 400)
                    .body(ApiResponse.error(e.getCode(), e.getMessage()));
        }
    }

    @DeleteMapping("/admin/agents/{agentId}")
    public ResponseEntity<ApiResponse<String>> delete(@PathVariable String agentId) {
        try {
            agentService.delete(agentId);
            return ResponseEntity.ok(ApiResponse.success("deleted"));
        } catch (BusinessException e) {
            return ResponseEntity.status(e.getCode() >= 500 ? 500 : 400)
                    .body(ApiResponse.error(e.getCode(), e.getMessage()));
        }
    }

    // ==================== 管理端: Flow Steps CRUD ====================

    @GetMapping("/admin/agents/{agentId}/flow")
    public ResponseEntity<ApiResponse<List<AgentFlowStep>>> getFlow(@PathVariable String agentId) {
        return ResponseEntity.ok(ApiResponse.success(agentService.getFlowSteps(agentId)));
    }

    @PostMapping("/admin/agents/{agentId}/flow")
    public ResponseEntity<ApiResponse<List<AgentFlowStep>>> saveFlow(
            @PathVariable String agentId,
            @RequestBody List<AgentFlowStep> steps) {
        try {
            return ResponseEntity.ok(ApiResponse.success(agentService.saveFlowSteps(agentId, steps)));
        } catch (BusinessException e) {
            return ResponseEntity.status(e.getCode() >= 500 ? 500 : 400)
                    .body(ApiResponse.error(e.getCode(), e.getMessage()));
        }
    }

    @PutMapping("/admin/agents/{agentId}/flow/{stepId}")
    public ResponseEntity<ApiResponse<String>> updateFlow(@PathVariable String agentId,
                                                           @PathVariable String stepId,
                                                           @RequestBody AgentFlowStep step) {
        // TODO: 单个步骤更新 — MVP 用批量替换（POST）即可
        return ResponseEntity.ok(ApiResponse.success("ok"));
    }

    @DeleteMapping("/admin/agents/{agentId}/flow/{stepId}")
    public ResponseEntity<ApiResponse<String>> deleteFlow(@PathVariable String agentId,
                                                           @PathVariable String stepId) {
        // TODO: 单个步骤删除 — MVP 用批量替换（POST）即可
        return ResponseEntity.ok(ApiResponse.success("ok"));
    }
}

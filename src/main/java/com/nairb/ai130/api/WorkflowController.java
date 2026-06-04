package com.nairb.ai130.api;

import com.nairb.ai130.app.workflow.WorkflowEngine;
import com.nairb.ai130.app.workflow.WorkflowTemplateService;
import com.nairb.ai130.common.enums.WorkflowNodeType;
import com.nairb.ai130.common.response.ApiResponse;
import com.nairb.ai130.domain.workflow.WorkflowDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

/**
 * 工作流相关 API。
 * <p>
 * 提供模板管理和流程恢复端点。
 */
@RestController
@RequestMapping("/api/workflow")
@CrossOrigin(origins = "*")
public class WorkflowController {

    private static final Logger log = LoggerFactory.getLogger(WorkflowController.class);

    private final WorkflowTemplateService templateService;
    private final WorkflowEngine workflowEngine;

    public WorkflowController(WorkflowTemplateService templateService, WorkflowEngine workflowEngine) {
        this.templateService = templateService;
        this.workflowEngine = workflowEngine;
    }

    // ==================== 模板管理 ====================

    /** 列出所有可用模板 */
    @GetMapping("/templates")
    public ApiResponse<List<WorkflowDefinition>> listTemplates() {
        return ApiResponse.success(templateService.listTemplates());
    }

    /** 获取模板详情 */
    @GetMapping("/templates/{id}")
    public ApiResponse<WorkflowDefinition> getTemplate(@PathVariable String id) {
        WorkflowDefinition def = templateService.getTemplate(id);
        return def != null ? ApiResponse.success(def) : ApiResponse.notFound("模板不存在");
    }

    /** 在指定节点后插入新节点（创建自定义模板） */
    @PostMapping("/templates/{id}/insert-node")
    public ApiResponse<String> insertNode(
            @PathVariable String id,
            @RequestBody Map<String, String> body) {
        String afterNodeId = body.get("afterNodeId");
        String nodeType = body.get("nodeType");
        String nodeName = body.get("nodeName");

        if (afterNodeId == null || nodeType == null || nodeName == null) {
            return ApiResponse.badRequest("缺少参数: afterNodeId, nodeType, nodeName");
        }

        try {
            WorkflowNodeType type = WorkflowNodeType.valueOf(nodeType.toUpperCase());
            String newDefId = templateService.insertNode(id, afterNodeId, type, nodeName);
            return ApiResponse.success("创建成功", newDefId);
        } catch (IllegalArgumentException e) {
            return ApiResponse.badRequest("无效的 nodeType: " + nodeType);
        }
    }

    /** 跳过模板中的某个节点（创建自定义模板） */
    @PostMapping("/templates/{id}/skip-node")
    public ApiResponse<String> skipNode(
            @PathVariable String id,
            @RequestBody Map<String, String> body) {
        String nodeId = body.get("nodeId");
        if (nodeId == null) return ApiResponse.badRequest("缺少参数: nodeId");

        try {
            String newDefId = templateService.skipNode(id, nodeId);
            return ApiResponse.success("创建成功", newDefId);
        } catch (IllegalArgumentException e) {
            return ApiResponse.badRequest(e.getMessage());
        }
    }

    /** 启用/禁用模板 */
    @PutMapping("/templates/{id}/toggle")
    public ApiResponse<String> toggleTemplate(
            @PathVariable String id,
            @RequestBody Map<String, Boolean> body) {
        templateService.toggleTemplate(id, body.getOrDefault("enabled", true));
        return ApiResponse.success("已更新");
    }

    // ==================== 断点续跑 ====================

    /**
     * 恢复中断的工作流。
     * <pre>GET /api/workflow/resume?sessionId=xxx</pre>
     * 返回与 /api/dchat 相同的 SSE 事件流。
     */
    @GetMapping(value = "/resume", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> resume(@RequestParam String sessionId) {
        log.info("Resuming workflow for sessionId={}", sessionId);
        return workflowEngine.resume(sessionId);
    }
}

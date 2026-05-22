package com.nairb.ai130.api;

import com.nairb.ai130.app.McpToolService;
import com.nairb.ai130.common.response.ApiResponse;
import com.nairb.ai130.types.dto.McpToolRequestDTO;
import com.nairb.ai130.types.dto.McpToolResponseDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * MCP 工具配置管理接口。
 * <p>
 * 路径：/api/admin/mcp
 */
@RestController
@RequestMapping("/api/admin/mcp")
public class McpToolController {

    private static final Logger log = LoggerFactory.getLogger(McpToolController.class);

    private final McpToolService service;

    public McpToolController(McpToolService service) {
        this.service = service;
    }

    // ==================== 查询 ====================

    /** 查询所有 MCP 配置 */
    @GetMapping
    public ResponseEntity<ApiResponse<List<McpToolResponseDTO>>> list() {
        try {
            return ResponseEntity.ok(ApiResponse.success(service.findAll()));
        } catch (Exception e) {
            log.error("查询 MCP 列表失败", e);
            return ResponseEntity.internalServerError().body(ApiResponse.serverError(e.getMessage()));
        }
    }

    /** 根据 mcpId 查询 */
    @GetMapping("/{mcpId}")
    public ResponseEntity<ApiResponse<McpToolResponseDTO>> get(@PathVariable String mcpId) {
        McpToolResponseDTO dto = service.findByMcpId(mcpId);
        if (dto == null) return ResponseEntity.ok(ApiResponse.notFound("MCP 配置不存在"));
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    // ==================== 创建 ====================

    @PostMapping
    public ResponseEntity<ApiResponse<McpToolResponseDTO>> create(@RequestBody McpToolRequestDTO request) {
        try {
            return ResponseEntity.ok(ApiResponse.success("创建成功", service.create(request)));
        } catch (Exception e) {
            log.error("创建 MCP 配置失败", e);
            return ResponseEntity.badRequest().body(ApiResponse.badRequest(e.getMessage()));
        }
    }

    // ==================== 更新 ====================

    @PutMapping("/{mcpId}")
    public ResponseEntity<ApiResponse<McpToolResponseDTO>> update(@PathVariable String mcpId,
                                                                   @RequestBody McpToolRequestDTO request) {
        try {
            return ResponseEntity.ok(ApiResponse.success("更新成功", service.update(mcpId, request)));
        } catch (Exception e) {
            log.error("更新 MCP 配置失败: mcpId={}", mcpId, e);
            return ResponseEntity.badRequest().body(ApiResponse.badRequest(e.getMessage()));
        }
    }

    // ==================== 删除 ====================

    @DeleteMapping("/{mcpId}")
    public ResponseEntity<ApiResponse<String>> delete(@PathVariable String mcpId) {
        try {
            service.delete(mcpId);
            return ResponseEntity.ok(ApiResponse.success("已删除"));
        } catch (Exception e) {
            log.error("删除 MCP 配置失败: mcpId={}", mcpId, e);
            return ResponseEntity.badRequest().body(ApiResponse.badRequest(e.getMessage()));
        }
    }

    // ==================== 客户端操作 ====================

    /** 重新加载指定 MCP 客户端（从数据库重新读取配置并重建连接） */
    @PostMapping("/{mcpId}/reload")
    public ResponseEntity<ApiResponse<String>> reload(@PathVariable String mcpId) {
        try {
            service.reload(mcpId);
            return ResponseEntity.ok(ApiResponse.success("mcpId=" + mcpId + " 已重新加载"));
        } catch (Exception e) {
            log.error("重新加载 MCP 客户端失败: mcpId={}", mcpId, e);
            return ResponseEntity.badRequest().body(ApiResponse.badRequest(e.getMessage()));
        }
    }

    /** 查看当前已加载的 MCP 客户端数量 */
    @GetMapping("/stats/loaded")
    public ResponseEntity<ApiResponse<Integer>> loadedCount() {
        return ResponseEntity.ok(ApiResponse.success("已加载 " + service.loadedClientCount() + " 个客户端",
                service.loadedClientCount()));
    }
}

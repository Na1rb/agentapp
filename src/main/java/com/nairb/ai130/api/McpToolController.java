package com.nairb.ai130.api;

import com.nairb.ai130.api.dto.McpToolResponse;
import com.nairb.ai130.common.response.ApiResponse;
import com.nairb.ai130.domain.repository.McpToolConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * MCP 工具配置 API。
 * <p>
 * 前端通过此接口获取当前可用的 MCP 工具列表，
 * 用于展示工具面板、参数表单等。
 */
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class McpToolController {

    private static final Logger log = LoggerFactory.getLogger(McpToolController.class);

    private final McpToolConfigRepository repository;

    public McpToolController(McpToolConfigRepository repository) {
        this.repository = repository;
    }

    /**
     * 获取所有启用的 MCP 工具列表。
     * <pre>
     * GET /api/mcp-tools
     * </pre>
     */
    @GetMapping("/mcp-tools")
    public ResponseEntity<ApiResponse<List<McpToolResponse>>> listEnabled() {
        log.debug("Fetching enabled MCP tools");
        List<McpToolResponse> tools = repository.findAllEnabled()
                .stream()
                .map(McpToolResponse::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(tools));
    }
}

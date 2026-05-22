package com.nairb.ai130.app;

import com.nairb.ai130.common.exception.BusinessException;
import com.nairb.ai130.infrastructure.mcp.McpClientFactory;
import com.nairb.ai130.infrastructure.mcp.McpToolRepository;
import com.nairb.ai130.types.dto.McpToolRequestDTO;
import com.nairb.ai130.types.dto.McpToolResponseDTO;
import com.nairb.ai130.types.vo.McpToolConfigVO;
import io.modelcontextprotocol.client.McpSyncClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.util.List;

/**
 * MCP 工具管理服务 —— 管理 MCP 配置的 CRUD 及客户端生命周期。
 */
@Service
public class McpToolService {

    private static final Logger log = LoggerFactory.getLogger(McpToolService.class);

    private final McpToolRepository repository;
    private final McpClientFactory factory;

    public McpToolService(McpToolRepository repository, McpClientFactory factory) {
        this.repository = repository;
        this.factory = factory;
    }

    // ==================== CRUD ====================

    /** 创建 MCP 配置 */
    public McpToolResponseDTO create(McpToolRequestDTO request) {
        if (request.getMcpId() == null || request.getMcpId().isBlank())
            throw new BusinessException("mcpId 不能为空");
        if (repository.existsByMcpId(request.getMcpId()))
            throw new BusinessException("mcpId 已存在: " + request.getMcpId());

        McpToolConfigVO vo = dtoToVo(request);
        repository.insert(vo);
        log.info("MCP 配置已创建: mcpId={}", vo.getMcpId());

        // 刷新全局 ToolCallbackProvider
        refreshToolCallbacks();
        return voToDto(repository.findByMcpId(vo.getMcpId()));
    }

    /** 更新 MCP 配置 */
    public McpToolResponseDTO update(String mcpId, McpToolRequestDTO request) {
        McpToolConfigVO existing = repository.findByMcpId(mcpId);
        if (existing == null) throw new BusinessException(404, "MCP 配置不存在: " + mcpId);

        request.setMcpId(mcpId);
        McpToolConfigVO vo = dtoToVo(request);
        repository.updateByMcpId(vo);
        log.info("MCP 配置已更新: mcpId={}", mcpId);

        // 重新加载该客户端
        McpToolConfigVO latest = repository.findByMcpId(mcpId);
        if (latest.getStatus() != null && latest.getStatus() == 1) {
            factory.createOrReplace(latest);
        } else {
            factory.closeAndRemove(mcpId);
        }
        // 刷新全局 ToolCallbackProvider
        refreshToolCallbacks();
        return voToDto(latest);
    }

    /** 删除 MCP 配置 */
    public void delete(String mcpId) {
        if (!repository.existsByMcpId(mcpId))
            throw new BusinessException(404, "MCP 配置不存在: " + mcpId);
        factory.closeAndRemove(mcpId);
        repository.deleteByMcpId(mcpId);
        log.info("MCP 配置已删除: mcpId={}", mcpId);
        // 刷新全局 ToolCallbackProvider
        refreshToolCallbacks();
    }

    /** 查询单个 MCP 配置 */
    public McpToolResponseDTO findByMcpId(String mcpId) {
        McpToolConfigVO vo = repository.findByMcpId(mcpId);
        return vo != null ? voToDto(vo) : null;
    }

    /** 查询所有 MCP 配置 */
    public List<McpToolResponseDTO> findAll() {
        return repository.findAll().stream().map(this::voToDto).toList();
    }

    /** 查询所有启用的 MCP 配置 */
    public List<McpToolConfigVO> findEnabled() {
        return repository.findEnabled();
    }

    // ==================== 客户端生命周期 ====================

    /** 重新加载指定 MCP 客户端 */
    public McpSyncClient reload(String mcpId) {
        McpToolConfigVO vo = repository.findByMcpId(mcpId);
        if (vo == null) throw new BusinessException(404, "MCP 配置不存在: " + mcpId);
        if (vo.getStatus() == null || vo.getStatus() != 1)
            throw new BusinessException(400, "MCP 未启用: " + mcpId);
        McpSyncClient client = factory.createOrReplace(vo);
        refreshToolCallbacks();
        return client;
    }

    /** 获取可动态刷新的 ToolCallbackProvider，供 ChatClient 持有 */
    public ToolCallbackProvider getRefreshableProvider() {
        return factory.getRefreshableProvider();
    }

    /** 启动 / 刷新：从 DB 加载所有启用的 MCP，更新 ChatClient 的工具列表 */
    public void refreshToolCallbacks() {
        List<McpToolConfigVO> enabled = repository.findEnabled();
        factory.refreshAll(enabled);
    }

    /** 获取当前已加载的客户端数量 */
    public int loadedClientCount() {
        return factory.clientCount();
    }

    /** 关闭所有 MCP 客户端（应用关闭时调用） */
    @PreDestroy
    public void closeAll() {
        factory.closeAll();
    }

    // ==================== 内部转换 ====================

    private McpToolConfigVO dtoToVo(McpToolRequestDTO dto) {
        McpToolConfigVO vo = new McpToolConfigVO();
        vo.setMcpId(dto.getMcpId());
        vo.setMcpName(dto.getMcpName());
        vo.setTransportType(dto.getTransportType());
        vo.setTransportConfig(dto.getTransportConfig());
        vo.setRequestTimeout(dto.getRequestTimeout());
        vo.setStatus(dto.getStatus());
        return vo;
    }

    private McpToolResponseDTO voToDto(McpToolConfigVO vo) {
        McpToolResponseDTO dto = new McpToolResponseDTO();
        dto.setMcpId(vo.getMcpId());
        dto.setMcpName(vo.getMcpName());
        dto.setTransportType(vo.getTransportType());
        dto.setTransportConfig(vo.getTransportConfig());
        dto.setRequestTimeout(vo.getRequestTimeout());
        dto.setStatus(vo.getStatus());
        dto.setCreateTime(vo.getCreateTime());
        dto.setUpdateTime(vo.getUpdateTime());
        return dto;
    }
}

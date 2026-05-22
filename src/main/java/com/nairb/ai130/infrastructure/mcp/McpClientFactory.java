package com.nairb.ai130.infrastructure.mcp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nairb.ai130.types.vo.McpToolConfigVO;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MCP 客户端工厂 —— 根据配置创建 / 管理 McpSyncClient 实例。
 * <p>
 * 支持两种传输协议：
 * <ul>
 *   <li><b>SSE</b>：远程 MCP Server（HTTP + SSE），适用于独立部署的工具服务</li>
 *   <li><b>Stdio</b>：本地 MCP Server（子进程），适用于 filesystem、git 等本地工具</li>
 * </ul>
 * <p>
 * 所有客户端缓存在 {@code clients} Map 中，key = mcp_id。
 */
@Component
public class McpClientFactory {

    private static final Logger log = LoggerFactory.getLogger(McpClientFactory.class);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, McpSyncClient> clients = new ConcurrentHashMap<>();
    private final ApplicationEventPublisher eventPublisher;

    /** 可动态刷新的 ToolCallbackProvider，ChatClient 持有此引用不变，内部 delegate 可替换 */
    private final RefreshableToolCallbackProvider refreshableProvider = new RefreshableToolCallbackProvider();

    public McpClientFactory(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    // ==================== 对外 API ====================

    /**
     * 根据配置创建（或重建）一个 MCP 客户端，并缓存。
     *
     * @param config MCP 工具配置
     * @return 创建成功的 McpSyncClient，失败返回 null
     */
    public McpSyncClient createOrReplace(McpToolConfigVO config) {
        String mcpId = config.getMcpId();
        closeSilently(mcpId); // 先关闭旧的

        try {
            McpSyncClient client = buildClient(config);
            client.initialize();
            clients.put(mcpId, client);
            log.info("MCP 客户端创建成功: mcpId={}, type={}, name={}", mcpId, config.getTransportType(), config.getMcpName());
            return client;
        } catch (Exception e) {
            log.error("MCP 客户端创建失败: mcpId={}, error={}", mcpId, e.getMessage(), e);
            return null;
        }
    }

    /**
     * 根据 mcpId 获取已缓存的客户端（可能为 null）。
     */
    public McpSyncClient getClient(String mcpId) {
        return clients.get(mcpId);
    }

    /**
     * 关闭并移除指定客户端。
     */
    public void closeAndRemove(String mcpId) {
        closeSilently(mcpId);
        clients.remove(mcpId);
    }

    /**
     * 清理所有客户端（应用关闭时调用）。
     */
    public void closeAll() {
        clients.keySet().forEach(this::closeSilently);
        clients.clear();
        log.info("所有 MCP 客户端已关闭");
    }

    /**
     * 从配置创建 ToolCallbackProvider（可直接注入 ChatClient.defaultToolCallbacks）。
     */
    public ToolCallbackProvider createToolCallbackProvider(McpToolConfigVO config) {
        McpSyncClient client = createOrReplace(config);
        if (client == null) return null;
        
        ToolCallback[] rawTools = new SyncMcpToolCallbackProvider(client).getToolCallbacks();
        ToolCallback[] wrappedTools = new ToolCallback[rawTools.length];
        for (int i = 0; i < rawTools.length; i++) {
            wrappedTools[i] = new com.nairb.ai130.infrastructure.mcp.tools.MonitoredToolCallback(rawTools[i], config.getMcpName(), eventPublisher);
        }
        return () -> wrappedTools;
    }

    /**
     * 将多个已启用的 MCP 配置合并为一个 ToolCallbackProvider。
     */
    public ToolCallbackProvider createToolCallbackProvider(List<McpToolConfigVO> configs) {
        List<McpSyncClient> activeClients = configs.stream()
                .map(c -> {
                    McpSyncClient existing = clients.get(c.getMcpId());
                    if (existing != null) return existing;
                    return createOrReplace(c);
                })
                .filter(c -> c != null)
                .toList();

        if (activeClients.isEmpty()) {
            log.warn("没有可用的 MCP 客户端");
            return null;
        }
        
        List<ToolCallback> allWrappedTools = new java.util.ArrayList<>();
        for (McpToolConfigVO config : configs) {
            McpSyncClient client = clients.get(config.getMcpId());
            if (client == null) continue;
            ToolCallback[] rawTools = new SyncMcpToolCallbackProvider(client).getToolCallbacks();
            for (ToolCallback rawTool : rawTools) {
                allWrappedTools.add(new com.nairb.ai130.infrastructure.mcp.tools.MonitoredToolCallback(rawTool, config.getMcpName(), eventPublisher));
            }
        }
        
        return () -> allWrappedTools.toArray(new ToolCallback[0]);
    }

    /**
     * 当前已注册的 MCP 客户端数量。
     */
    public int clientCount() {
        return clients.size();
    }

    /**
     * 获取可动态刷新的 ToolCallbackProvider。
     * ChatClient 持有此引用不变；调用 {@link #refreshAll()} 后内部 delegate 自动替换。
     */
    public ToolCallbackProvider getRefreshableProvider() {
        return refreshableProvider;
    }

    /**
     * 从数据库重新加载所有启用的 MCP，并刷新 ChatClient 的工具列表。
     * 每次调用 McpToolService.reload / create / update / delete 后也应调用此方法。
     */
    public void refreshAll(List<McpToolConfigVO> configs) {
        ToolCallbackProvider newProvider = createToolCallbackProvider(configs);
        refreshableProvider.setDelegate(newProvider);
        if (newProvider != null) {
            log.info("MCP 工具已刷新，共 {} 个客户端", clients.size());
        } else {
            log.info("MCP 工具已清空（无启用配置）");
        }
    }

    // ==================== 可动态替换的 ToolCallbackProvider ====================

    /**
     * 代理模式：ChatClient 持有此对象，内部 delegate 通过 {@link #setDelegate} 替换。
     * 这样不需要重建 ChatClient 即可实现 MCP 工具的运行时增减。
     */
    private static class RefreshableToolCallbackProvider implements ToolCallbackProvider {

        private volatile ToolCallbackProvider delegate;

        void setDelegate(ToolCallbackProvider delegate) {
            this.delegate = delegate;
        }

        @Override
        public ToolCallback[] getToolCallbacks() {
            ToolCallbackProvider d = this.delegate;
            return d != null ? d.getToolCallbacks() : new ToolCallback[0];
        }
    }

    // ==================== 内部构建逻辑 ====================

    private McpSyncClient buildClient(McpToolConfigVO config) {
        parseTransportConfig(config);
        return switch (config.getTransportType()) {
            case "sse"   -> buildSseClient(config);
            case "stdio" -> buildStdioClient(config);
            default -> throw new IllegalArgumentException("不支持的传输类型: " + config.getTransportType());
        };
    }

    // ---- SSE ----

    private McpSyncClient buildSseClient(McpToolConfigVO config) {
        McpToolConfigVO.TransportConfigSse sse = config.getTransportConfigSse();
        if (sse == null) throw new IllegalArgumentException("SSE 配置缺失");

        String originalUri = sse.getBaseUri();
        String baseUri;
        String sseEndpoint;

        // 如果 baseUri 已包含 /sse 路径，自动分离
        int idx = originalUri.indexOf("/sse");
        if (idx != -1) {
            baseUri = originalUri.substring(0, idx);
            sseEndpoint = originalUri.substring(idx);
        } else {
            baseUri = originalUri;
            sseEndpoint = (sse.getSseEndpoint() != null && !sse.getSseEndpoint().isBlank())
                    ? sse.getSseEndpoint() : "/sse";
        }

        HttpClientSseClientTransport transport = HttpClientSseClientTransport
                .builder(baseUri)
                .sseEndpoint(sseEndpoint)
                .build();

        return McpClient.sync(transport)
                .requestTimeout(Duration.ofMinutes(config.getRequestTimeout() != null ? config.getRequestTimeout() : 5))
                .build();
    }

    // ---- Stdio ----

    private McpSyncClient buildStdioClient(McpToolConfigVO config) {
        McpToolConfigVO.TransportConfigStdio stdioConfig = config.getTransportConfigStdio();
        if (stdioConfig == null || stdioConfig.getStdio() == null)
            throw new IllegalArgumentException("Stdio 配置缺失");

        // key = mcpName，从中取对应进程定义
        McpToolConfigVO.TransportConfigStdio.Stdio stdio =
                stdioConfig.getStdio().get(config.getMcpName());
        if (stdio == null)
            throw new IllegalArgumentException("Stdio 配置中找不到 mcpName=" + config.getMcpName());

        ServerParameters params = ServerParameters.builder(stdio.getCommand())
                .args(stdio.getArgs() != null ? stdio.getArgs() : List.of())
                .env(stdio.getEnv() != null ? stdio.getEnv() : Map.of())
                .build();

        // 引入 ObjectMapper 包装为 McpJsonMapper，这里由于不同版本的 MCP Java SDK 可能有不同的 Mapper 实现类
        // 通常是 new org.springframework.ai.mcp.client.stdio.JacksonMcpJsonMapper 或类似类
        // 由于是 io.modelcontextprotocol.json.McpJsonMapper 接口，可以直接传入一个新的 mapper 包装
        // spring-ai-mcp 一般使用 com.fasterxml.jackson.databind.ObjectMapper
        io.modelcontextprotocol.json.McpJsonMapper mcpJsonMapper;
        try {
            // 尝试使用 JacksonMcpJsonMapper 反射创建，兼容不同包路径
            Class<?> mapperClass = Class.forName("io.modelcontextprotocol.json.JacksonMcpJsonMapper");
            mcpJsonMapper = (io.modelcontextprotocol.json.McpJsonMapper) mapperClass.getConstructor(ObjectMapper.class).newInstance(objectMapper);
        } catch (Exception e) {
            throw new RuntimeException("无法找到 McpJsonMapper 实现类", e);
        }

        return McpClient.sync(new StdioClientTransport(params, mcpJsonMapper))
                .requestTimeout(Duration.ofSeconds(config.getRequestTimeout() != null ? config.getRequestTimeout() : 5))
                .build();
    }

    // ---- JSON → 结构化对象 ----

    private void parseTransportConfig(McpToolConfigVO config) {
        String raw = config.getTransportConfig();
        if (raw == null || raw.isBlank()) return;

        try {
            if ("sse".equals(config.getTransportType())) {
                config.setTransportConfigSse(objectMapper.readValue(raw, McpToolConfigVO.TransportConfigSse.class));
            } else if ("stdio".equals(config.getTransportType())) {
                Map<String, McpToolConfigVO.TransportConfigStdio.Stdio> stdioMap = objectMapper.readValue(
                        raw, new TypeReference<Map<String, McpToolConfigVO.TransportConfigStdio.Stdio>>() {});
                McpToolConfigVO.TransportConfigStdio stdio = new McpToolConfigVO.TransportConfigStdio();
                stdio.setStdio(stdioMap);
                config.setTransportConfigStdio(stdio);
            }
        } catch (Exception e) {
            log.error("解析 transport_config JSON 失败: mcpId={}, raw={}", config.getMcpId(), raw, e);
        }
    }

    private void closeSilently(String mcpId) {
        McpSyncClient old = clients.get(mcpId);
        if (old != null) {
            try { old.close(); } catch (Exception e) { log.warn("关闭 MCP 客户端异常: mcpId={}", mcpId, e); }
        }
    }
}

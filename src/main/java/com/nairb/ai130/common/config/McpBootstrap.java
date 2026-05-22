package com.nairb.ai130.common.config;

import com.nairb.ai130.app.McpToolService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * MCP 启动引导 —— 应用就绪后自动从数据库加载所有启用的 MCP 配置。
 * <p>
 * 与 AiConfig#mcpToolCallbackProvider 互补：
 * 数据库可能未就绪导致 Bean 创建时返回 null，这里做二次补偿加载。
 */
@Component
public class McpBootstrap {

    private static final Logger log = LoggerFactory.getLogger(McpBootstrap.class);

    private final McpToolService mcpToolService;

    public McpBootstrap(McpToolService mcpToolService) {
        this.mcpToolService = mcpToolService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        try {
            int before = mcpToolService.loadedClientCount();
            mcpToolService.refreshToolCallbacks();
            int after = mcpToolService.loadedClientCount();
            if (after > 0) {
                log.info("MCP 启动加载完成: 共 {} 个客户端", after);
            } else {
                log.info("MCP 启动加载: 没有启用的 MCP 配置");
            }
        } catch (Exception e) {
            log.warn("MCP 启动加载失败（数据库可能未就绪，后续可通过 /api/admin/mcp/{mcpId}/reload 手动加载）: {}",
                    e.getMessage());
        }
    }
}

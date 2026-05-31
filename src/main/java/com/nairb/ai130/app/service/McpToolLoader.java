package com.nairb.ai130.app.service;

import com.nairb.ai130.domain.entity.McpToolConfig;
import com.nairb.ai130.domain.repository.McpToolConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * MCP 工具加载器。
 * <p>
 * 根据前端传入的工具名称列表，从数据库加载对应的 {@link McpToolConfig}，
 * 并包装为 Spring AI 的 {@link FunctionToolCallback} 列表，供 {@code ChatClient} 动态注入。
 *
 * <h3>使用方式</h3>
 * <pre>{@code
 * List<FunctionToolCallback<String, String>> tools = mcpToolLoader.loadTools(List.of("weather_query", "web_search"));
 * chatClient.prompt()
 *     .tools(tools.toArray(new FunctionToolCallback[0]))
 *     .user(prompt)
 *     .stream().content();
 * }</pre>
 */
@Component
public class McpToolLoader {

    private static final Logger log = LoggerFactory.getLogger(McpToolLoader.class);

    private final McpToolConfigRepository repository;
    private final McpToolExecutor executor;

    public McpToolLoader(McpToolConfigRepository repository, McpToolExecutor executor) {
        this.repository = repository;
        this.executor = executor;
    }

    // ==================== 公开方法 ====================

    /**
     * 根据工具名称列表加载 FunctionToolCallback。
     *
     * @param toolNames 工具名称列表（对应 {@code mcp_tool_config.tool_name}）
     * @return FunctionToolCallback 列表，未找到或已禁用的工具会被静默跳过
     */
    public List<FunctionToolCallback<String, String>> loadTools(List<String> toolNames) {
        if (toolNames == null || toolNames.isEmpty()) {
            return Collections.emptyList();
        }

        List<McpToolConfig> configs = repository.findByToolNames(toolNames);
        if (configs.isEmpty()) {
            log.warn("No MCP tool configs found for names: {}", toolNames);
            return Collections.emptyList();
        }

        List<FunctionToolCallback<String, String>> callbacks = new ArrayList<>(configs.size());
        for (McpToolConfig config : configs) {
            try {
                callbacks.add(buildCallback(config));
                log.info("Loaded MCP tool: {} [{}]", config.getToolName(), config.getHttpMethod());
            } catch (Exception e) {
                log.error("Failed to build FunctionToolCallback for tool: {}", config.getToolName(), e);
            }
        }

        log.info("Loaded {} MCP tool(s) out of {} requested", callbacks.size(), toolNames.size());
        return callbacks;
    }

    /**
     * 加载单个工具。
     *
     * @param toolName 工具名称
     * @return FunctionToolCallback，如果工具不存在或已禁用则返回 {@code Optional.empty()}
     */
    public Optional<FunctionToolCallback<String, String>> loadTool(String toolName) {
        if (toolName == null || toolName.isBlank()) {
            return Optional.empty();
        }
        Optional<McpToolConfig> configOpt = repository.findByToolName(toolName);
        if (configOpt.isEmpty()) {
            log.warn("MCP tool not found: {}", toolName);
            return Optional.empty();
        }
        McpToolConfig config = configOpt.get();
        if (Boolean.FALSE.equals(config.getIsEnabled())) {
            log.warn("MCP tool is disabled: {}", toolName);
            return Optional.empty();
        }
        return Optional.of(buildCallback(config));
    }

    // ==================== 内部方法 ====================

    /**
     * 将单个 {@link McpToolConfig} 构建为 {@link FunctionToolCallback}。
     * <p>
     * 使用 Spring AI 的 {@link FunctionToolCallback#builder(String, java.util.function.Function)} API，
     * 将模型传入的 JSON 参数转发给 {@link McpToolExecutor} 执行 HTTP 调用。
     */
    private FunctionToolCallback<String, String> buildCallback(McpToolConfig config) {
        String description = config.getDescription() != null
                ? config.getDescription()
                : config.getDisplayName();

        return FunctionToolCallback.<String, String>builder(
                        config.getToolName(),
                        input -> executor.execute(config, input))
                .description(description)
                .inputType(String.class)
                .build();
    }
}

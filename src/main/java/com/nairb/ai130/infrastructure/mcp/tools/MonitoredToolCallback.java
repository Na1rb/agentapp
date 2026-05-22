package com.nairb.ai130.infrastructure.mcp.tools;

import com.nairb.ai130.common.event.McpToolExecuteEvent;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.lang.Nullable;

public class MonitoredToolCallback implements ToolCallback {

    private final ToolCallback delegate;
    private final String serverName;
    private final ApplicationEventPublisher eventPublisher;

    public MonitoredToolCallback(ToolCallback delegate, String serverName, ApplicationEventPublisher eventPublisher) {
        this.delegate = delegate;
        this.serverName = serverName;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @Override
    public ToolMetadata getToolMetadata() {
        return delegate.getToolMetadata();
    }

    @Override
    public String call(String toolInput) {
        return callWithEvent(toolInput, null);
    }

    @Override
    public String call(String toolInput, @Nullable ToolContext toolContext) {
        return callWithEvent(toolInput, toolContext);
    }

    private String callWithEvent(String toolInput, @Nullable ToolContext toolContext) {
        // 1. 发送【开始调用】事件 (推给前端显示：思考中...)
        eventPublisher.publishEvent(McpToolExecuteEvent.builder()
                .eventType(McpToolExecuteEvent.EventType.START)
                .serverName(serverName)
                .toolName(getToolDefinition().name())
                .arguments(toolInput)
                .build());

        long startTime = System.currentTimeMillis();
        try {
            // 2. 真实执行外部工具
            String result = toolContext != null ? delegate.call(toolInput, toolContext) : delegate.call(toolInput);
            long duration = System.currentTimeMillis() - startTime;

            // 3. 发送【调用成功】事件 (记录数据库 + 通知前端)
            eventPublisher.publishEvent(McpToolExecuteEvent.builder()
                    .eventType(McpToolExecuteEvent.EventType.SUCCESS)
                    .serverName(serverName)
                    .toolName(getToolDefinition().name())
                    .arguments(toolInput)
                    .resultOrError(result)
                    .durationMs(duration)
                    .build());

            return result;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            
            // 4. 发送【调用失败】事件 (记录数据库 + 通知前端)
            eventPublisher.publishEvent(McpToolExecuteEvent.builder()
                    .eventType(McpToolExecuteEvent.EventType.FAILED)
                    .serverName(serverName)
                    .toolName(getToolDefinition().name())
                    .arguments(toolInput)
                    .resultOrError(e.getMessage())
                    .durationMs(duration)
                    .build());
            throw e;
        }
    }
}

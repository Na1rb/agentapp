package com.nairb.ai130.app.listener;

import com.nairb.ai130.common.event.McpToolExecuteEvent;
import com.nairb.ai130.domain.mcp.McpExecuteLog;
import com.nairb.ai130.infrastructure.mcp.McpExecuteLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class McpExecuteLogListener {

    private static final Logger log = LoggerFactory.getLogger(McpExecuteLogListener.class);
    private final McpExecuteLogRepository executeLogRepository;

    public McpExecuteLogListener(McpExecuteLogRepository executeLogRepository) {
        this.executeLogRepository = executeLogRepository;
    }

    // 监听事件并异步存库
    @Async
    @EventListener
    public void handleToolExecutionEvent(McpToolExecuteEvent event) {
        // 只记录成功或失败的结果到数据库，START状态仅给前端用，不需要入库
        if (event.getEventType() == McpToolExecuteEvent.EventType.START) {
            return;
        }

        McpExecuteLog executeLog = new McpExecuteLog();
        executeLog.setServerName(event.getServerName());
        executeLog.setToolName(event.getToolName());
        executeLog.setArguments(event.getArguments());
        executeLog.setDurationMs(event.getDurationMs());
        executeLog.setCreateTime(LocalDateTime.now());
        // TODO: sessionId 可以通过 ThreadLocal 从请求上下文中获取，目前暂留空或在 Event 中填充

        if (event.getEventType() == McpToolExecuteEvent.EventType.SUCCESS) {
            executeLog.setStatus(1);
        } else {
            executeLog.setStatus(0);
            executeLog.setErrorMessage(event.getResultOrError());
        }

        try {
            executeLogRepository.insert(executeLog);
        } catch (Exception e) {
            log.error("插入MCP执行日志失败: {}", e.getMessage(), e);
        }
    }
}

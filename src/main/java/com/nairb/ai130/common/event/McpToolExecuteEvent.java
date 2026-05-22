package com.nairb.ai130.common.event;

public class McpToolExecuteEvent {
    public enum EventType { START, SUCCESS, FAILED }

    private EventType eventType;
    private String serverName;
    private String toolName;
    private String arguments;
    private String resultOrError;
    private Long durationMs;

    public McpToolExecuteEvent() {}

    public McpToolExecuteEvent(EventType eventType, String serverName, String toolName, String arguments, String resultOrError, Long durationMs) {
        this.eventType = eventType;
        this.serverName = serverName;
        this.toolName = toolName;
        this.arguments = arguments;
        this.resultOrError = resultOrError;
        this.durationMs = durationMs;
    }

    public static Builder builder() {
        return new Builder();
    }

    public EventType getEventType() { return eventType; }
    public void setEventType(EventType eventType) { this.eventType = eventType; }

    public String getServerName() { return serverName; }
    public void setServerName(String serverName) { this.serverName = serverName; }

    public String getToolName() { return toolName; }
    public void setToolName(String toolName) { this.toolName = toolName; }

    public String getArguments() { return arguments; }
    public void setArguments(String arguments) { this.arguments = arguments; }

    public String getResultOrError() { return resultOrError; }
    public void setResultOrError(String resultOrError) { this.resultOrError = resultOrError; }

    public Long getDurationMs() { return durationMs; }
    public void setDurationMs(Long durationMs) { this.durationMs = durationMs; }

    public static class Builder {
        private EventType eventType;
        private String serverName;
        private String toolName;
        private String arguments;
        private String resultOrError;
        private Long durationMs;

        public Builder eventType(EventType eventType) { this.eventType = eventType; return this; }
        public Builder serverName(String serverName) { this.serverName = serverName; return this; }
        public Builder toolName(String toolName) { this.toolName = toolName; return this; }
        public Builder arguments(String arguments) { this.arguments = arguments; return this; }
        public Builder resultOrError(String resultOrError) { this.resultOrError = resultOrError; return this; }
        public Builder durationMs(Long durationMs) { this.durationMs = durationMs; return this; }

        public McpToolExecuteEvent build() {
            return new McpToolExecuteEvent(eventType, serverName, toolName, arguments, resultOrError, durationMs);
        }
    }
}

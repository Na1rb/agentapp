package com.nairb.ai130.types.vo;

import java.util.List;
import java.util.Map;

/**
 * MCP 工具配置值对象 —— 对应 ai_client_tool_mcp 表。
 * <p>
 * 存储 MCP Server 的完整连接信息，支持 SSE 和 Stdio 两种传输协议。
 * transportConfig 字段为 JSON 字符串，反序列化后填充 transportConfigSse / transportConfigStdio。
 *
 * @see TransportConfigSse
 * @see TransportConfigStdio
 */
public class McpToolConfigVO {

    private String mcpId;
    private String mcpName;
    private String transportType;   // sse | stdio
    private String transportConfig; // 原始 JSON
    private Integer requestTimeout;
    private Integer status;
    private java.time.LocalDateTime createTime;
    private java.time.LocalDateTime updateTime;

    // === 反序列化后的结构化对象 ===
    private TransportConfigSse transportConfigSse;
    private TransportConfigStdio transportConfigStdio;

    // ==================== 内嵌类 ====================

    /** SSE 传输配置 */
    public static class TransportConfigSse {
        private String baseUri;
        private String sseEndpoint;

        public TransportConfigSse() {}
        public TransportConfigSse(String baseUri, String sseEndpoint) {
            this.baseUri = baseUri;
            this.sseEndpoint = sseEndpoint;
        }
        public String getBaseUri()     { return baseUri; }
        public String getSseEndpoint() { return sseEndpoint; }
        public void setBaseUri(String baseUri)         { this.baseUri = baseUri; }
        public void setSseEndpoint(String sseEndpoint) { this.sseEndpoint = sseEndpoint; }
    }

    /** Stdio 传输配置 */
    public static class TransportConfigStdio {
        private Map<String, Stdio> stdio;

        public TransportConfigStdio() {}
        public TransportConfigStdio(Map<String, Stdio> stdio) { this.stdio = stdio; }
        public Map<String, Stdio> getStdio() { return stdio; }
        public void setStdio(Map<String, Stdio> stdio) { this.stdio = stdio; }

        /** 单条 Stdio 进程定义 */
        public static class Stdio {
            private String command;
            private List<String> args;
            private Map<String, String> env;

            public Stdio() {}
            public Stdio(String command, List<String> args, Map<String, String> env) {
                this.command = command; this.args = args; this.env = env;
            }
            public String getCommand()          { return command; }
            public List<String> getArgs()       { return args; }
            public Map<String, String> getEnv() { return env; }
            public void setCommand(String command)                  { this.command = command; }
            public void setArgs(List<String> args)                  { this.args = args; }
            public void setEnv(Map<String, String> env)             { this.env = env; }
        }
    }

    // ==================== Getters / Setters ====================

    public String getMcpId()                               { return mcpId; }
    public void setMcpId(String mcpId)                     { this.mcpId = mcpId; }
    public String getMcpName()                             { return mcpName; }
    public void setMcpName(String mcpName)                 { this.mcpName = mcpName; }
    public String getTransportType()                       { return transportType; }
    public void setTransportType(String transportType)     { this.transportType = transportType; }
    public String getTransportConfig()                     { return transportConfig; }
    public void setTransportConfig(String transportConfig) { this.transportConfig = transportConfig; }
    public Integer getRequestTimeout()                     { return requestTimeout; }
    public void setRequestTimeout(Integer requestTimeout)  { this.requestTimeout = requestTimeout; }
    public Integer getStatus()                             { return status; }
    public void setStatus(Integer status)                  { this.status = status; }
    public java.time.LocalDateTime getCreateTime()         { return createTime; }
    public void setCreateTime(java.time.LocalDateTime t)   { this.createTime = t; }
    public java.time.LocalDateTime getUpdateTime()         { return updateTime; }
    public void setUpdateTime(java.time.LocalDateTime t)   { this.updateTime = t; }
    public TransportConfigSse getTransportConfigSse()      { return transportConfigSse; }
    public void setTransportConfigSse(TransportConfigSse sse)   { this.transportConfigSse = sse; }
    public TransportConfigStdio getTransportConfigStdio()       { return transportConfigStdio; }
    public void setTransportConfigStdio(TransportConfigStdio s) { this.transportConfigStdio = s; }
}

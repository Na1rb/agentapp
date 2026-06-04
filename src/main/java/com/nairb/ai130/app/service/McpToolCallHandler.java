package com.nairb.ai130.app.service;

/**
 * MCP 工具调用降级处理器。
 * <p>
 * 当工具调用失败（超时、上游异常、内部错误）时，生成结构化的错误 JSON，
 * 使 LLM 能够理解失败原因并向用户做出合理说明。
 * <p>
 * 所有降级方法均为静态，供 {@link McpToolExecutor} 直接调用。
 */
public final class McpToolCallHandler {

    private McpToolCallHandler() {
        // 工具类，禁止实例化
    }

    // ==================== 降级模板 ====================

    /**
     * 超时降级（含重试后仍超时）。
     *
     * @param toolName 工具名称
     * @return 错误 JSON
     */
    public static String timeoutFallback(String toolName) {
        return String.format(
                "{\"success\":false,\"status\":\"error\",\"errorType\":\"timeout\",\"retryable\":true,\"error\":\"工具调用超时\",\"tool\":\"%s\",\"message\":\"工具 '%s' 在等待 %.0f 秒后未响应，请稍后重试或换一种方式查询。\"}",
                toolName, toolName, 15.0);
    }

    /**
     * 上游服务异常降级（HTTP 4xx/5xx）。
     *
     * @param toolName 工具名称
     * @param httpCode HTTP 状态码
     * @return 错误 JSON
     */
    public static String upstreamErrorFallback(String toolName, int httpCode) {
        return String.format(
                "{\"success\":false,\"status\":\"error\",\"errorType\":\"upstream_http\",\"retryable\":%s,\"error\":\"上游服务异常\",\"tool\":\"%s\",\"code\":%d,\"message\":\"工具 '%s' 的上游服务返回了 HTTP %d 错误，该工具暂时不可用。\"}",
                httpCode >= 500, toolName, httpCode, toolName, httpCode);
    }

    /**
     * 内部错误降级（网络异常、解析异常等）。
     *
     * @param toolName 工具名称
     * @param detail   错误详情
     * @return 错误 JSON
     */
    public static String internalErrorFallback(String toolName, String detail) {
        String safeDetail = detail != null ? detail.replace("\"", "'") : "未知错误";
        return String.format(
                "{\"success\":false,\"status\":\"error\",\"errorType\":\"internal\",\"retryable\":false,\"error\":\"内部错误\",\"tool\":\"%s\",\"message\":\"工具 '%s' 执行时发生内部错误：%s\"}",
                toolName, toolName, safeDetail);
    }
}

package com.nairb.ai130.app.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nairb.ai130.domain.entity.McpToolConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * MCP 工具执行器。
 * <p>
 * 根据 {@link McpToolConfig} 配置执行 HTTP 调用，具备超时控制、1 次重试和降级策略。
 * 支持 GET（查询参数）和 POST（JSON 请求体）两种 HTTP 方法。
 *
 * <h3>降级策略</h3>
 * <pre>
 * 执行工具调用
 *   ├─ 成功（2xx） → 返回响应体
 *   ├─ 超时（&gt;15s）→ 重试 1 次
 *   │     ├─ 成功 → 返回响应体
 *   │     └─ 失败 → 返回降级 JSON
 *   ├─ HTTP 4xx/5xx → 返回降级 JSON
 *   └─ 异常 → catch → 返回降级 JSON
 * </pre>
 */
@Component
public class McpToolExecutor {

    private static final Logger log = LoggerFactory.getLogger(McpToolExecutor.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(15);
    private static final int MAX_RETRIES = 1;

    /** 允许的 URL scheme 白名单（防 SSRF） */
    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public McpToolExecutor() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) TIMEOUT.toMillis());
        factory.setReadTimeout((int) TIMEOUT.toMillis());
        this.restClient = RestClient.builder()
                .requestFactory(factory)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    // ==================== 公开方法 ====================

    /**
     * 执行工具调用。
     *
     * @param config    工具配置
     * @param arguments 模型传入的 JSON 参数（对应 param_schema 定义的字段）
     * @return HTTP 响应体字符串，或降级错误 JSON
     */
    public String execute(McpToolConfig config, String arguments) {
        return executeWithRetry(config, arguments, 0);
    }

    // ==================== 核心执行逻辑 ====================

    @SuppressWarnings("unchecked")
    private String executeWithRetry(McpToolConfig config, String arguments, int attempt) {
        try {
            URI uri = buildUri(config, arguments);
            log.debug("[{}] attempt={} {} {}", config.getToolName(), attempt + 1, config.getHttpMethod(), uri);

            CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
                RestClient.RequestBodySpec spec = restClient
                        .method(HttpMethod.valueOf(config.getHttpMethod().toUpperCase()))
                        .uri(uri)
                        .accept(MediaType.APPLICATION_JSON);

                // 应用自定义 HTTP 头（headersConfig）
                applyHeaders(spec, config);

                // POST/PUT 请求：发送 JSON 请求体
                if (needsBody(config.getHttpMethod())) {
                    spec.contentType(MediaType.APPLICATION_JSON);
                    spec.body(arguments != null ? arguments : "{}");
                }

                return spec.retrieve()
                        .onStatus(status -> status.value() >= 400, (req, resp) -> {
                            byte[] body = resp.getBody() != null ? resp.getBody().readAllBytes() : new byte[0];
                            String bodyStr = new String(body, StandardCharsets.UTF_8);
                            throw new ToolExecutionException(
                                    "HTTP " + resp.getStatusCode().value() + ": " + bodyStr,
                                    resp.getStatusCode().value());
                        })
                        .body(String.class);
            });

            String result = future.get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            log.debug("[{}] success, length={}", config.getToolName(),
                    result != null ? result.length() : 0);
            return result != null ? result : "";

        } catch (TimeoutException e) {
            log.warn("[{}] timeout (attempt {})", config.getToolName(), attempt + 1);
            if (attempt < MAX_RETRIES) {
                return executeWithRetry(config, arguments, attempt + 1);
            }
            return McpToolCallHandler.timeoutFallback(config.getToolName());

        } catch (ToolExecutionException e) {
            log.warn("[{}] HTTP {} error: {}", config.getToolName(), e.getHttpCode(), e.getMessage());
            if (attempt < MAX_RETRIES && e.getHttpCode() >= 500) {
                // 仅对 5xx 服务端错误进行重试
                return executeWithRetry(config, arguments, attempt + 1);
            }
            return McpToolCallHandler.upstreamErrorFallback(config.getToolName(), e.getHttpCode());

        } catch (Exception e) {
            log.error("[{}] unexpected error: {}", config.getToolName(), e.getMessage(), e);
            if (attempt < MAX_RETRIES) {
                return executeWithRetry(config, arguments, attempt + 1);
            }
            return McpToolCallHandler.internalErrorFallback(config.getToolName(), e.getMessage());
        }
    }

    // ==================== URI 构建 ====================

    /** 应用 headersConfig 和 authConfig 到请求 */
    private void applyHeaders(RestClient.RequestBodySpec spec, McpToolConfig config) {
        // headersConfig: JSON 对象 {"Header-Name": "value", ...}
        if (config.getHeadersConfig() != null && !config.getHeadersConfig().isBlank()) {
            try {
                Map<String, String> headers = objectMapper.readValue(
                        config.getHeadersConfig(), new TypeReference<Map<String, String>>() {});
                headers.forEach((name, value) -> {
                    if (name != null && value != null) {
                        spec.header(name, value);
                        log.debug("[{}] Applied header: {}={}", config.getToolName(), name,
                                name.toLowerCase().contains("auth") ? "***" : value);
                    }
                });
            } catch (Exception e) {
                log.warn("[{}] Failed to parse headersConfig: {}", config.getToolName(), e.getMessage());
            }
        }

        // authConfig: JSON 对象 {"type": "bearer", "token": "xxx"} 或 {"type": "basic", ...}
        if (config.getAuthConfig() != null && !config.getAuthConfig().isBlank()) {
            try {
                Map<String, Object> auth = objectMapper.readValue(
                        config.getAuthConfig(), new TypeReference<Map<String, Object>>() {});
                String type = String.valueOf(auth.getOrDefault("type", "")).toLowerCase();
                if ("bearer".equals(type)) {
                    String token = String.valueOf(auth.getOrDefault("token", ""));
                    spec.header("Authorization", "Bearer " + token);
                } else if ("basic".equals(type)) {
                    String username = String.valueOf(auth.getOrDefault("username", ""));
                    String password = String.valueOf(auth.getOrDefault("password", ""));
                    String encoded = java.util.Base64.getEncoder().encodeToString(
                            (username + ":" + password).getBytes(StandardCharsets.UTF_8));
                    spec.header("Authorization", "Basic " + encoded);
                } else if ("apikey".equals(type)) {
                    String headerName = String.valueOf(auth.getOrDefault("headerName", "X-API-Key"));
                    String apiKey = String.valueOf(auth.getOrDefault("apiKey", ""));
                    spec.header(headerName, apiKey);
                }
                log.debug("[{}] Applied auth type={}", config.getToolName(), type);
            } catch (Exception e) {
                log.warn("[{}] Failed to parse authConfig: {}", config.getToolName(), e.getMessage());
            }
        }
    }

    private URI buildUri(McpToolConfig config, String arguments) throws Exception {
        String baseUrl = config.getEndpointUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("endpoint_url is empty for tool: " + config.getToolName());
        }

        // SSRF 防护：仅允许 http/https scheme
        URI uri = URI.create(baseUrl);
        String scheme = uri.getScheme();
        if (scheme == null || !ALLOWED_SCHEMES.contains(scheme.toLowerCase())) {
            throw new SecurityException(
                    "Blocked URL scheme '" + scheme + "' for tool: " + config.getToolName() +
                    ". Only http/https are allowed.");
        }

        // GET 请求：将参数拼接到 URL 查询字符串
        if ("GET".equalsIgnoreCase(config.getHttpMethod()) && arguments != null && !arguments.isBlank()) {
            Map<String, Object> params = objectMapper.readValue(arguments, Map.class);
            StringBuilder query = new StringBuilder();
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                if (entry.getValue() == null) continue;
                if (query.length() > 0) query.append('&');
                query.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8))
                        .append('=')
                        .append(URLEncoder.encode(entry.getValue().toString(), StandardCharsets.UTF_8));
            }
            String separator = baseUrl.contains("?") ? "&" : "?";
            return URI.create(baseUrl + separator + query);
        }

        return URI.create(baseUrl);
    }

    private boolean needsBody(String httpMethod) {
        String m = httpMethod.toUpperCase();
        return "POST".equals(m) || "PUT".equals(m) || "PATCH".equals(m);
    }

    // ==================== 内部异常类 ====================

    /**
     * 工具执行异常（携带 HTTP 状态码，用于重试决策）。
     */
    private static class ToolExecutionException extends RuntimeException {
        private final int httpCode;

        ToolExecutionException(String message, int httpCode) {
            super(message);
            this.httpCode = httpCode;
        }

        int getHttpCode() {
            return httpCode;
        }
    }
}

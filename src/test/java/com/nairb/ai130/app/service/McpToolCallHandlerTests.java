package com.nairb.ai130.app.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpToolCallHandlerTests {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void reportsStructuredToolFailures() throws Exception {
        JsonNode timeout = MAPPER.readTree(McpToolCallHandler.timeoutFallback("search"));
        JsonNode upstream = MAPPER.readTree(McpToolCallHandler.upstreamErrorFallback("search", 503));
        JsonNode internal = MAPPER.readTree(McpToolCallHandler.internalErrorFallback("search", "broken"));

        assertFalse(timeout.path("success").asBoolean());
        assertEquals("error", timeout.path("status").asText());
        assertEquals("timeout", timeout.path("errorType").asText());
        assertTrue(timeout.path("retryable").asBoolean());

        assertEquals("upstream_http", upstream.path("errorType").asText());
        assertTrue(upstream.path("retryable").asBoolean());
        assertFalse(internal.path("retryable").asBoolean());
    }
}

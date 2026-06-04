package com.nairb.ai130.agent.engine;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nairb.ai130.domain.agent.AgentExecutionOptions;
import com.nairb.ai130.domain.agent.ExecuteContext;
import com.nairb.ai130.domain.entity.AgentFlowStep;
import org.junit.jupiter.api.Test;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReactEngineTests {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void rollsOneActionThenCompletesThroughEvaluator() {
        StubReactEngine engine = new StubReactEngine(List.of(
                "{\"verdict\":\"CONTINUE\",\"observation\":\"need weather\"}",
                "query current weather",
                "weather is sunny",
                "{\"verdict\":\"COMPLETED\",\"observation\":\"goal satisfied\"}",
                "It is sunny."));

        List<ServerSentEvent<String>> events = engine.execute(
                "session", "current weather", List.of(), null,
                new AgentExecutionOptions(null, 5, 10)).collectList().block();

        assertEquals(List.of(
                        "step_start", "step_thinking", "step_result",
                        "step_start", "step_thinking", "step_result",
                        "step_start", "step_thinking", "step_result",
                        "step_start", "step_thinking", "step_result",
                        "step_start", "step_thinking", "step_result",
                        "done"),
                events.stream().map(ServerSentEvent::event).toList());
        assertEquals(1, data(events.get(events.size() - 1)).get("total_paces"));
        assertEquals("COMPLETED", data(events.get(events.size() - 1)).get("verdict"));
        assertEquals("actor", data(events.get(7)).get("role"));
        assertEquals(1, data(events.get(7)).get("pace"));
    }

    @Test
    void observerJsonParsingFallsBackToText() {
        assertEquals("COMPLETED", ReactEngine.parseObserverVerdict("{\"verdict\":\"COMPLETED\"}"));
        assertEquals("FAIL", ReactEngine.parseObserverVerdict("FAIL - unavailable"));
        assertEquals("CONTINUE", ReactEngine.parseObserverVerdict("unexpected"));
    }

    private static Map<String, Object> data(ServerSentEvent<String> event) {
        try {
            return MAPPER.readValue(event.data(), new TypeReference<>() {});
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static final class StubReactEngine extends ReactEngine {
        private final Deque<String> outputs;

        private StubReactEngine(List<String> outputs) {
            super(null, null, null, null, null, null, "primary", "fallback", 10);
            this.outputs = new ArrayDeque<>(outputs);
        }

        @Override
        protected Flux<String> streamRoleTokens(String role, ExecuteContext context,
                                                List<AgentFlowStep> steps, String modelCode) {
            return Flux.just(outputs.removeFirst());
        }
    }
}

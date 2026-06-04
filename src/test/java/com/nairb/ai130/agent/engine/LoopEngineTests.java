package com.nairb.ai130.agent.engine;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nairb.ai130.domain.agent.ExecuteContext;
import com.nairb.ai130.domain.entity.AgentNodeResult;
import com.nairb.ai130.domain.entity.AgentFlowStep;
import com.nairb.ai130.domain.repository.AgentExecutionLogRepository;
import com.nairb.ai130.domain.repository.AgentNodeResultRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class LoopEngineTests {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void stopsImmediatelyWhenSupervisorPasses() {
        StubLoopEngine engine = new StubLoopEngine(5,
                List.of("plan", "result", "PASS complete", "final answer"));

        List<ServerSentEvent<String>> events = engine.execute("session", "task", List.of(), null)
                .collectList()
                .block();

        assertEquals(List.of(
                        "step_start", "step_thinking", "step_result",
                        "step_start", "step_thinking", "step_result",
                        "step_start", "step_thinking", "step_result",
                        "step_start", "step_thinking", "step_result",
                        "done"),
                events.stream().map(ServerSentEvent::event).toList());
        assertEquals(1, data(events.get(events.size() - 1)).get("total_rounds"));
        assertEquals("PASS", data(events.get(8)).get("verdict"));
        assertEquals("supervisor", data(events.get(7)).get("role"));
        assertEquals(1, data(events.get(7)).get("round"));
    }

    @Test
    void continuesOnFailAndOptimizeThenStopsAtMaxRound() {
        StubLoopEngine engine = new StubLoopEngine(2, List.of(
                "plan 1", "result 1", "FAIL try again",
                "plan 2", "result 2", "OPTIMIZE more work remains",
                "best available summary"));

        List<ServerSentEvent<String>> events = engine.execute("session", "task", List.of(), null)
                .collectList()
                .block();

        long analyzerStarts = events.stream()
                .filter(event -> "step_start".equals(event.event()))
                .map(event -> data(event).get("role"))
                .filter("analyzer"::equals)
                .count();
        Map<String, Object> done = data(events.get(events.size() - 1));

        assertEquals(2, analyzerStarts);
        assertEquals(2, done.get("total_rounds"));
        assertEquals("OPTIMIZE", done.get("verdict"));
        assertEquals("best available summary", done.get("final_answer"));
        assertTrue(engine.historiesSeenByAnalyzer.get(1).contains("Round 1"));
    }

    @Test
    void parsesSupervisorVerdictConservatively() {
        assertEquals("PASS", LoopEngine.parseVerdict(" PASS - complete"));
        assertEquals("FAIL", LoopEngine.parseVerdict("FAIL: broken"));
        assertEquals("OPTIMIZE", LoopEngine.parseVerdict("OPTIMIZE more"));
        assertEquals("PASS", LoopEngine.parseVerdict("""
                {"verdict":"PASS","reason":"complete","nextAction":""}
                """));
        assertEquals("FAIL", LoopEngine.parseVerdict("""
                ```json
                {"verdict":"FAIL","reason":"tool failed","nextAction":"retry"}
                ```
                """));
        assertEquals("PASS", LoopEngine.parseVerdict(
                "{\"verdict\":\"UNKNOWN\"}\nPASS - fallback text"));
        assertEquals("OPTIMIZE", LoopEngine.parseVerdict("unexpected response"));
        assertEquals("OPTIMIZE", LoopEngine.parseVerdict(null));
    }

    @Test
    void persistsEveryRoleAndCumulativeStepResults() {
        AgentNodeResultRepository nodeRepository = mock(AgentNodeResultRepository.class);
        AgentExecutionLogRepository logRepository = mock(AgentExecutionLogRepository.class);
        StubLoopEngine engine = new StubLoopEngine(5,
                List.of("plan", "result", "PASS complete", "final answer"),
                nodeRepository, logRepository);

        engine.execute("session", "task", List.of(), null, 42L).collectList().block();

        ArgumentCaptor<AgentNodeResult> captor = ArgumentCaptor.forClass(AgentNodeResult.class);
        verify(nodeRepository, times(4)).insert(captor.capture());
        assertEquals(List.of("analyzer", "performer", "supervisor", "summarizer"),
                captor.getAllValues().stream().map(AgentNodeResult::getRole).toList());
        assertTrue(captor.getAllValues().stream().allMatch(result -> result.getLogId() == 42L));
        verify(logRepository, atLeast(2)).updateStepResults(eq(42L), anyString());
    }

    private static Map<String, Object> data(ServerSentEvent<String> event) {
        try {
            return MAPPER.readValue(event.data(), new TypeReference<>() {});
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static final class StubLoopEngine extends LoopEngine {
        private final Deque<String> outputs;
        private final List<String> historiesSeenByAnalyzer = new ArrayList<>();

        private StubLoopEngine(int maxRound, List<String> outputs) {
            this(maxRound, outputs, null, null);
        }

        private StubLoopEngine(int maxRound,
                               List<String> outputs,
                               AgentNodeResultRepository nodeRepository,
                               AgentExecutionLogRepository logRepository) {
            super(null, null, null, null, nodeRepository, logRepository,
                    "primary", "fallback", maxRound);
            this.outputs = new ArrayDeque<>(outputs);
        }

        @Override
        protected Flux<String> streamRoleTokens(String role,
                                                ExecuteContext context,
                                                List<AgentFlowStep> steps,
                                                String modelCode) {
            if ("analyzer".equals(role)) {
                historiesSeenByAnalyzer.add(context.getExecutionHistory().toString());
            }
            return Flux.just(outputs.removeFirst());
        }
    }
}

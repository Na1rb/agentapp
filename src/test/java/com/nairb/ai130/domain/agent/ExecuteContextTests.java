package com.nairb.ai130.domain.agent;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecuteContextTests {

    @Test
    void initializesLoopControlAndMutableCollections() {
        ExecuteContext context = new ExecuteContext();

        assertEquals("normal", context.getStrategy());
        assertEquals(1, context.getRound());
        assertEquals(5, context.getMaxRound());
        assertEquals(1, context.getPace());
        assertEquals(10, context.getMaxPace());
        assertNotNull(context.getExecutionHistory());
        assertNotNull(context.getNodeResults());
        assertTrue(context.getExecutionHistory().isEmpty());
        assertTrue(context.getNodeResults().isEmpty());
    }

    @Test
    void carriesExecutionStateBetweenNodes() {
        ExecuteContext context = new ExecuteContext();
        context.setSessionId("session-1");
        context.setUserInput("prepare a report");
        context.setStrategy("loop");
        context.setRound(2);
        context.setMaxRound(4);
        context.getExecutionHistory().append("round 1");
        context.setLastAnalysis("collect more evidence");
        context.setLastResult("evidence collected");
        context.setLastVerdict("OPTIMIZE");
        context.setNodeResults(List.of(Map.of("role", "supervisor", "output", "OPTIMIZE")));

        assertEquals("session-1", context.getSessionId());
        assertEquals("prepare a report", context.getUserInput());
        assertEquals("loop", context.getStrategy());
        assertEquals(2, context.getRound());
        assertEquals(4, context.getMaxRound());
        assertEquals("round 1", context.getExecutionHistory().toString());
        assertEquals("collect more evidence", context.getLastAnalysis());
        assertEquals("evidence collected", context.getLastResult());
        assertEquals("OPTIMIZE", context.getLastVerdict());
        assertEquals("supervisor", context.getNodeResults().get(0).get("role"));

        context.getNodeResults().add(Map.of("role", "analyzer", "output", "next round"));
        assertEquals(2, context.getNodeResults().size());
    }

    @Test
    void replacesNullMutableStateWithEmptyInstances() {
        ExecuteContext context = new ExecuteContext();

        context.setExecutionHistory(null);
        context.setNodeResults(null);

        assertNotNull(context.getExecutionHistory());
        assertNotNull(context.getNodeResults());
        assertTrue(context.getExecutionHistory().isEmpty());
        assertTrue(context.getNodeResults().isEmpty());
    }
}

package com.nairb.ai130.app.workflow;

import com.nairb.ai130.domain.workflow.CheckResult;
import com.nairb.ai130.domain.workflow.NodeResult;
import com.nairb.ai130.domain.workflow.WorkflowExecution;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkflowEngineRoutingTests {

    private final WorkflowEngine engine = new WorkflowEngine(
            null, null, null, null, null, null, null, null);

    @Test
    void parsesExplicitSimpleAndComplexAnalysisResults() throws Exception {
        Method method = WorkflowEngine.class.getDeclaredMethod("parseRequiresWorkflow", NodeResult.class);
        method.setAccessible(true);

        NodeResult simple = completedResult("""
                {"requiresWorkflow":false,"complexity":"simple"}
                """);
        NodeResult complex = completedResult("""
                {"requiresWorkflow":true,"complexity":"complex"}
                """);
        NodeResult malformed = completedResult("not-json");

        assertEquals(Boolean.FALSE, method.invoke(engine, simple));
        assertEquals(Boolean.TRUE, method.invoke(engine, complex));
        assertEquals(Boolean.TRUE, method.invoke(engine, malformed));
    }

    @Test
    void incrementsRetryCountUntilLimitThenSelectsExitEdge() throws Exception {
        Method method = WorkflowEngine.class.getDeclaredMethod(
                "evaluateCondition",
                String.class,
                CheckResult.class,
                Boolean.class,
                WorkflowExecution.class);
        method.setAccessible(true);

        WorkflowExecution execution = WorkflowExecution.create("exec", "workflow", "session", "prompt");
        CheckResult failed = CheckResult.fail(List.of("failed"), "retry");

        assertTrue((boolean) method.invoke(
                engine, "check.passed == false && retryCount < 3", failed, true, execution));
        assertEquals(1, execution.getRetryCount());

        execution.setRetryCount(3);
        assertFalse((boolean) method.invoke(
                engine, "check.passed == false && retryCount < 3", failed, true, execution));
        assertTrue((boolean) method.invoke(
                engine, "check.passed == false && retryCount >= 3", failed, true, execution));
    }

    private NodeResult completedResult(String output) {
        NodeResult result = NodeResult.pending("result", "execution", "analysis", "input");
        result.markRunning();
        result.markCompleted(output);
        return result;
    }
}

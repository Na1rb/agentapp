package com.nairb.ai130.domain.repository;

import com.nairb.ai130.domain.entity.AgentNodeResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@Transactional
class AgentNodeResultRepositoryTests {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private AgentNodeResultRepository repository;

    @Autowired
    private AgentExecutionLogRepository executionLogRepository;

    @Test
    void insertsAndLoadsResultsInRoundOrder() {
        jdbc.update("""
                INSERT INTO agent_execution_log (agent_id, session_id, status)
                VALUES ('test-loop', 'test-session', 'running')
                """);
        long logId = jdbc.queryForObject("SELECT LASTVAL()", Long.class);

        AgentNodeResult result = new AgentNodeResult();
        result.setLogId(logId);
        result.setRound(2);
        result.setRole("analyzer");
        result.setPrompt("analyze");
        result.setOutput("plan");
        result.setDurationMs(42);

        long id = repository.insert(result);
        List<AgentNodeResult> stored = repository.findByLogId(logId);

        assertNotNull(result.getId());
        assertEquals(id, result.getId());
        assertEquals(1, stored.size());
        assertEquals("analyzer", stored.get(0).getRole());
        assertEquals("plan", stored.get(0).getOutput());
        assertEquals(42, stored.get(0).getDurationMs());

        executionLogRepository.updateStepResults(logId, "[{\"role\":\"analyzer\"}]");
        executionLogRepository.updateResult(logId, "done", null, null, null);
        assertEquals("[{\"role\":\"analyzer\"}]",
                jdbc.queryForObject(
                        "SELECT step_results FROM agent_execution_log WHERE id = ?",
                        String.class,
                        logId));
    }
}

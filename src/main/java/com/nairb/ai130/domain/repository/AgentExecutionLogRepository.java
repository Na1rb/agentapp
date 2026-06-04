package com.nairb.ai130.domain.repository;

import com.nairb.ai130.domain.entity.AgentExecutionLog;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AgentExecutionLogRepository {

    private final JdbcTemplate jdbc;
    public AgentExecutionLogRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public long insert(AgentExecutionLog log) {
        jdbc.update("""
            INSERT INTO agent_execution_log (agent_id, session_id, user_id, status, started_at)
            VALUES (?,?,?,'running',NOW())
            """, log.getAgentId(), log.getSessionId(), log.getUserId());
        return jdbc.queryForObject("SELECT LASTVAL()", Long.class);
    }

    public void updateResult(long id, String status, String stepResults, Integer totalTokens, String errorMsg) {
        jdbc.update("""
            UPDATE agent_execution_log
            SET status=?, step_results=COALESCE(?, step_results), total_tokens=?, error_msg=?, finished_at=NOW()
            WHERE id=?
            """, status, stepResults, totalTokens, errorMsg, id);
    }

    public void updateStepResults(long id, String stepResults) {
        jdbc.update("""
            UPDATE agent_execution_log SET step_results=? WHERE id=?
            """, stepResults, id);
    }
}

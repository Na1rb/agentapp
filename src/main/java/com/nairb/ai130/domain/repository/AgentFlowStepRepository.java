package com.nairb.ai130.domain.repository;

import com.nairb.ai130.domain.entity.AgentFlowStep;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public class AgentFlowStepRepository {

    private final JdbcTemplate jdbc;
    public AgentFlowStepRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    private static final RowMapper<AgentFlowStep> RM = (rs, rowNum) -> {
        AgentFlowStep s = new AgentFlowStep();
        s.setId(rs.getLong("id"));
        s.setAgentId(rs.getString("agent_id"));
        s.setStepId(rs.getString("step_id"));
        s.setSequence(rs.getInt("sequence"));
        s.setClientType(rs.getString("client_type"));
        s.setClientName(rs.getString("client_name"));
        s.setClientId(rs.getString("client_id"));
        s.setStepPrompt(rs.getString("step_prompt"));
        s.setInputMapping(rs.getString("input_mapping"));
        s.setOutputKey(rs.getString("output_key"));
        s.setOnError(rs.getString("on_error"));
        s.setFallbackStep(rs.getString("fallback_step"));
        return s;
    };

    public List<AgentFlowStep> findByAgentId(String agentId) {
        return jdbc.query("SELECT * FROM agent_flow_step WHERE agent_id = ? ORDER BY sequence", RM, agentId);
    }

    @Transactional
    public void replaceAll(String agentId, List<AgentFlowStep> steps) {
        jdbc.update("DELETE FROM agent_flow_step WHERE agent_id = ?", agentId);
        for (AgentFlowStep s : steps) {
            jdbc.update("""
                INSERT INTO agent_flow_step (agent_id, step_id, sequence, client_type, client_name, client_id,
                    step_prompt, input_mapping, output_key, on_error, fallback_step)
                VALUES (?,?,?,?,?,?,?,?,?,?,?)
                """, agentId, s.getStepId(), s.getSequence(), s.getClientType(), s.getClientName(),
                s.getClientId(), s.getStepPrompt(), s.getInputMapping(), s.getOutputKey(),
                s.getOnError(), s.getFallbackStep());
        }
    }

    public void deleteByAgentId(String agentId) {
        jdbc.update("DELETE FROM agent_flow_step WHERE agent_id = ?", agentId);
    }
}

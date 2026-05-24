package com.nairb.ai130.infrastructure.dao;

import com.nairb.ai130.infrastructure.dao.po.AiAgentFlowConfig;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 智能体-客户端流程关联 DAO —— JdbcTemplate 实现
 */
@Repository
public class AiAgentFlowConfigDao {

    private final JdbcTemplate jdbc;

    public AiAgentFlowConfigDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final String TABLE = "ai_agent_flow_config";
    private static final BeanPropertyRowMapper<AiAgentFlowConfig> MAPPER =
            new BeanPropertyRowMapper<>(AiAgentFlowConfig.class);

    /**
     * 根据 agentId 查询流程配置，按 sequence 排序
     */
    public List<AiAgentFlowConfig> findByAgentIdOrderBySeq(String agentId) {
        return jdbc.query(
                "SELECT * FROM " + TABLE + " WHERE agent_id = ? ORDER BY sequence ASC",
                MAPPER, agentId);
    }

    public boolean insert(AiAgentFlowConfig config) {
        return jdbc.update(
                "INSERT INTO " + TABLE + " (agent_id, client_id, client_name, client_type, sequence, step_prompt) VALUES (?, ?, ?, ?, ?, ?)",
                config.getAgentId(), config.getClientId(), config.getClientName(),
                config.getClientType(), config.getSequence(), config.getStepPrompt()) > 0;
    }

    public boolean deleteByAgentId(String agentId) {
        return jdbc.update("DELETE FROM " + TABLE + " WHERE agent_id = ?", agentId) > 0;
    }
}

package com.nairb.ai130.infrastructure.dao;

import com.nairb.ai130.infrastructure.dao.po.AiAgent;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * AI 智能体 DAO —— JdbcTemplate 实现
 */
@Repository
public class AiAgentDao {

    private final JdbcTemplate jdbc;

    public AiAgentDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final String TABLE = "ai_agent";
    private static final BeanPropertyRowMapper<AiAgent> MAPPER =
            new BeanPropertyRowMapper<>(AiAgent.class);

    public AiAgent findByAgentId(String agentId) {
        List<AiAgent> list = jdbc.query(
                "SELECT * FROM " + TABLE + " WHERE agent_id = ? AND status = 1 LIMIT 1",
                MAPPER, agentId);
        return list.isEmpty() ? null : list.get(0);
    }

    public List<AiAgent> findAllEnabled() {
        return jdbc.query(
                "SELECT * FROM " + TABLE + " WHERE status = 1 ORDER BY id",
                MAPPER);
    }

    public List<AiAgent> findAll() {
        return jdbc.query("SELECT * FROM " + TABLE + " ORDER BY id", MAPPER);
    }

    public boolean insert(AiAgent agent) {
        return jdbc.update(
                "INSERT INTO " + TABLE + " (agent_id, agent_name, description, channel, strategy, status) VALUES (?, ?, ?, ?, ?, ?)",
                agent.getAgentId(), agent.getAgentName(), agent.getDescription(),
                agent.getChannel(), agent.getStrategy(), agent.getStatus()) > 0;
    }

    public boolean update(AiAgent agent) {
        return jdbc.update(
                "UPDATE " + TABLE + " SET agent_name=?, description=?, channel=?, strategy=?, status=? WHERE agent_id=?",
                agent.getAgentName(), agent.getDescription(), agent.getChannel(),
                agent.getStrategy(), agent.getStatus(), agent.getAgentId()) > 0;
    }
}

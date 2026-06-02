package com.nairb.ai130.domain.repository;

import com.nairb.ai130.domain.entity.AgentConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.*;

@Repository
public class AgentConfigRepository {

    private static final Logger log = LoggerFactory.getLogger(AgentConfigRepository.class);
    private final JdbcTemplate jdbc;

    public AgentConfigRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    private static final RowMapper<AgentConfig> RM = (rs, rowNum) -> {
        AgentConfig a = new AgentConfig();
        a.setAgentId(rs.getString("agent_id"));
        a.setAgentName(rs.getString("agent_name"));
        a.setDescription(rs.getString("description"));
        a.setChannel(rs.getString("channel"));
        a.setStrategy(rs.getString("strategy"));
        a.setStatus(rs.getInt("status"));
        a.setCreatedAt(rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toLocalDateTime() : null);
        a.setUpdatedAt(rs.getTimestamp("updated_at") != null ? rs.getTimestamp("updated_at").toLocalDateTime() : null);
        return a;
    };

    // 前台：启用的 Agent
    public List<AgentConfig> findEnabled() {
        return jdbc.query("SELECT * FROM agent_config WHERE status = 1 AND channel LIKE '%chat%' ORDER BY updated_at DESC", RM);
    }

    // 管理端：全部
    public List<AgentConfig> findAll() {
        return jdbc.query("SELECT * FROM agent_config ORDER BY updated_at DESC", RM);
    }

    public Optional<AgentConfig> findById(String agentId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("SELECT * FROM agent_config WHERE agent_id = ?", RM, agentId));
        } catch (EmptyResultDataAccessException e) { return Optional.empty(); }
    }

    public void save(AgentConfig a) {
        jdbc.update("""
            INSERT INTO agent_config (agent_id, agent_name, description, channel, strategy, status)
            VALUES (?,?,?,?,?,?)
            """, a.getAgentId(), a.getAgentName(), a.getDescription(), a.getChannel(), a.getStrategy(), a.getStatus());
    }

    public void update(AgentConfig a) {
        jdbc.update("""
            UPDATE agent_config SET agent_name=?, description=?, channel=?, strategy=?, status=?, updated_at=NOW()
            WHERE agent_id=?
            """, a.getAgentName(), a.getDescription(), a.getChannel(), a.getStrategy(), a.getStatus(), a.getAgentId());
    }

    public void deleteById(String agentId) {
        jdbc.update("DELETE FROM agent_config WHERE agent_id = ?", agentId);
    }
}

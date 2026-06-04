package com.nairb.ai130.domain.repository;

import com.nairb.ai130.domain.entity.AgentNodeResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Types;
import java.util.List;

@Repository
public class AgentNodeResultRepository {

    private static final RowMapper<AgentNodeResult> ROW_MAPPER = (rs, rowNum) -> {
        AgentNodeResult result = new AgentNodeResult();
        result.setId(rs.getLong("id"));
        result.setLogId(rs.getLong("log_id"));
        result.setRound(rs.getInt("round"));
        result.setRole(rs.getString("role"));
        result.setPrompt(rs.getString("prompt"));
        result.setOutput(rs.getString("output"));
        result.setDurationMs((Integer) rs.getObject("duration_ms"));
        result.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        return result;
    };

    private final JdbcTemplate jdbc;

    public AgentNodeResultRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long insert(AgentNodeResult result) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO agent_node_result (log_id, round, role, prompt, output, duration_ms)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, new String[]{"id"});
            statement.setLong(1, result.getLogId());
            statement.setInt(2, result.getRound() != null ? result.getRound() : 1);
            statement.setString(3, result.getRole());
            statement.setString(4, result.getPrompt());
            statement.setString(5, result.getOutput());
            if (result.getDurationMs() != null) {
                statement.setInt(6, result.getDurationMs());
            } else {
                statement.setNull(6, Types.INTEGER);
            }
            return statement;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Failed to obtain generated Agent node result ID");
        }
        result.setId(key.longValue());
        return result.getId();
    }

    public List<AgentNodeResult> findByLogId(long logId) {
        return jdbc.query("""
                SELECT * FROM agent_node_result
                WHERE log_id = ?
                ORDER BY round, id
                """, ROW_MAPPER, logId);
    }
}

package com.nairb.ai130.infrastructure.mapper;

import com.nairb.ai130.infrastructure.dao.po.AiAgent;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface AiAgentMapper {

    @Select("SELECT * FROM ai_agent WHERE agent_id = #{agentId} AND status = 1 LIMIT 1")
    AiAgent findByAgentId(String agentId);

    @Select("SELECT * FROM ai_agent WHERE status = 1 ORDER BY id")
    List<AiAgent> findAllEnabled();

    @Select("SELECT * FROM ai_agent ORDER BY id")
    List<AiAgent> findAll();

    @Insert("""
            INSERT INTO ai_agent (agent_id, agent_name, description, channel, strategy, status)
            VALUES (#{agentId}, #{agentName}, #{description}, #{channel}, #{strategy}, #{status})
            """)
    int insert(AiAgent agent);

    @Update("""
            UPDATE ai_agent
            SET agent_name = #{agentName},
                description = #{description},
                channel = #{channel},
                strategy = #{strategy},
                status = #{status}
            WHERE agent_id = #{agentId}
            """)
    int update(AiAgent agent);
}

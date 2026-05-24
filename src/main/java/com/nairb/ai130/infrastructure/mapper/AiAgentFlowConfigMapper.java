package com.nairb.ai130.infrastructure.mapper;

import com.nairb.ai130.infrastructure.dao.po.AiAgentFlowConfig;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AiAgentFlowConfigMapper {

    @Select("SELECT * FROM ai_agent_flow_config WHERE agent_id = #{agentId} ORDER BY sequence ASC")
    List<AiAgentFlowConfig> findByAgentIdOrderBySeq(String agentId);

    @Insert("""
            INSERT INTO ai_agent_flow_config (agent_id, client_id, client_name, client_type, sequence, step_prompt)
            VALUES (#{agentId}, #{clientId}, #{clientName}, #{clientType}, #{sequence}, #{stepPrompt})
            """)
    int insert(AiAgentFlowConfig config);

    @Delete("DELETE FROM ai_agent_flow_config WHERE agent_id = #{agentId}")
    int deleteByAgentId(String agentId);
}

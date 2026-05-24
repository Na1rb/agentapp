package com.nairb.ai130.infrastructure.dao;

import com.nairb.ai130.infrastructure.dao.po.AiAgentFlowConfig;
import com.nairb.ai130.infrastructure.mapper.AiAgentFlowConfigMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 智能体-客户端流程关联 DAO —— MyBatis 实现
 */
@Repository
public class AiAgentFlowConfigDao {

    private final AiAgentFlowConfigMapper aiAgentFlowConfigMapper;

    public AiAgentFlowConfigDao(AiAgentFlowConfigMapper aiAgentFlowConfigMapper) {
        this.aiAgentFlowConfigMapper = aiAgentFlowConfigMapper;
    }

    /**
     * 根据 agentId 查询流程配置，按 sequence 排序
     */
    public List<AiAgentFlowConfig> findByAgentIdOrderBySeq(String agentId) {
        return aiAgentFlowConfigMapper.findByAgentIdOrderBySeq(agentId);
    }

    public boolean insert(AiAgentFlowConfig config) {
        return aiAgentFlowConfigMapper.insert(config) > 0;
    }

    public boolean deleteByAgentId(String agentId) {
        return aiAgentFlowConfigMapper.deleteByAgentId(agentId) > 0;
    }
}

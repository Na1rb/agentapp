package com.nairb.ai130.infrastructure.dao;

import com.nairb.ai130.infrastructure.dao.po.AiAgent;
import com.nairb.ai130.infrastructure.mapper.AiAgentMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * AI 智能体 DAO —— MyBatis 实现
 */
@Repository
public class AiAgentDao {

    private final AiAgentMapper aiAgentMapper;

    public AiAgentDao(AiAgentMapper aiAgentMapper) {
        this.aiAgentMapper = aiAgentMapper;
    }

    public AiAgent findByAgentId(String agentId) {
        return aiAgentMapper.findByAgentId(agentId);
    }

    public List<AiAgent> findAllEnabled() {
        return aiAgentMapper.findAllEnabled();
    }

    public List<AiAgent> findAll() {
        return aiAgentMapper.findAll();
    }

    public boolean insert(AiAgent agent) {
        return aiAgentMapper.insert(agent) > 0;
    }

    public boolean update(AiAgent agent) {
        return aiAgentMapper.update(agent) > 0;
    }
}

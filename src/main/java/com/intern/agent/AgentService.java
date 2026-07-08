package com.intern.agent;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.intern.mapper.AiAgentMapper;
import com.intern.model.entity.AiAgent;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AgentService {
    private final AiAgentMapper aiAgentMapper;
    private final AgentRoutingPolicy routingPolicy;

    public AgentService(AiAgentMapper aiAgentMapper, AgentRoutingPolicy routingPolicy) {
        this.aiAgentMapper = aiAgentMapper;
        this.routingPolicy = routingPolicy;
    }

    public List<AiAgent> list() {
        return aiAgentMapper.selectList(new LambdaQueryWrapper<AiAgent>()
                .orderByAsc(AiAgent::getPriority));
    }

    public AiAgent create(AiAgent agent) {
        aiAgentMapper.insert(agent);
        return agent;
    }

    public AiAgent update(Long id, AiAgent input) {
        input.setId(id);
        aiAgentMapper.updateById(input);
        return aiAgentMapper.selectById(id);
    }

    public AiAgent route(String content) {
        return routingPolicy.route(content, list());
    }

    public AgentRouteDecision decideRoute(String content) {
        return routingPolicy.decide(content, list());
    }

    public boolean shouldHandoff(String content) {
        return routingPolicy.shouldHandoff(content);
    }
}

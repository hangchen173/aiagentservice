package com.intern.agent;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.intern.mapper.AiAgentMapper;
import com.intern.model.entity.AiAgent;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
public class AgentService {
    private final AiAgentMapper aiAgentMapper;

    public AgentService(AiAgentMapper aiAgentMapper) {
        this.aiAgentMapper = aiAgentMapper;
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
        String normalized = content == null ? "" : content.toLowerCase();
        return list().stream()
                .filter(agent -> Boolean.TRUE.equals(agent.getEnabled()))
                .filter(agent -> matches(normalized, agent.getTriggerKeywords()))
                .min(Comparator.comparing(AiAgent::getPriority))
                .orElseGet(this::generalAgent);
    }

    public boolean shouldHandoff(String content) {
        String normalized = content == null ? "" : content.toLowerCase();
        return List.of("人工", "转人工", "投诉", "退款", "主管", "举报", "不满意").stream()
                .anyMatch(normalized::contains);
    }

    private AiAgent generalAgent() {
        return list().stream()
                .filter(agent -> "general".equals(agent.getCode()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("缺少通用客服智能体"));
    }

    private boolean matches(String content, String keywords) {
        if (keywords == null || keywords.isBlank()) {
            return false;
        }
        for (String keyword : keywords.split(",")) {
            String trimmed = keyword.trim().toLowerCase();
            if (!trimmed.isEmpty() && content.contains(trimmed)) {
                return true;
            }
        }
        return false;
    }
}

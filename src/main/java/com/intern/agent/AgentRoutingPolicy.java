package com.intern.agent;

import com.intern.model.entity.AiAgent;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Component
public class AgentRoutingPolicy {
    private static final List<String> HANDOFF_KEYWORDS = List.of("人工", "转人工", "投诉", "退款", "主管", "举报", "不满意");
    private static final String GENERAL_AGENT_CODE = "general";
    private static final String HANDOFF_AGENT_CODE = "handoff";

    public AgentRouteDecision decide(String content, List<AiAgent> agents) {
        String normalized = normalize(content);
        Optional<AgentKeywordMatch> selectedMatch = agents.stream()
                .filter(this::enabled)
                .filter(agent -> !HANDOFF_AGENT_CODE.equals(agent.getCode()))
                .map(agent -> match(agent, normalized))
                .flatMap(Optional::stream)
                .min(Comparator.comparing(candidate -> priority(candidate.agent())));
        if (selectedMatch.isPresent()) {
            AgentKeywordMatch matched = selectedMatch.get();
            return new AgentRouteDecision(matched.agent(), matched.keyword(), false, shouldHandoff(content));
        }
        AiAgent general = generalAgent(agents)
                .orElseThrow(() -> new IllegalStateException("缺少启用的通用客服智能体"));
        return new AgentRouteDecision(general, null, true, shouldHandoff(content));
    }

    public AiAgent route(String content, List<AiAgent> agents) {
        return decide(content, agents).agent();
    }

    public boolean shouldHandoff(String content) {
        String normalized = normalize(content);
        return HANDOFF_KEYWORDS.stream().anyMatch(normalized::contains);
    }

    private Optional<AiAgent> generalAgent(List<AiAgent> agents) {
        return agents.stream()
                .filter(this::enabled)
                .filter(agent -> GENERAL_AGENT_CODE.equals(agent.getCode()))
                .findFirst();
    }

    private boolean enabled(AiAgent agent) {
        return Boolean.TRUE.equals(agent.getEnabled());
    }

    private int priority(AiAgent agent) {
        return agent.getPriority() == null ? Integer.MAX_VALUE : agent.getPriority();
    }

    private Optional<AgentKeywordMatch> match(AiAgent agent, String content) {
        String keywords = agent.getTriggerKeywords();
        if (keywords == null || keywords.isBlank()) {
            return Optional.empty();
        }
        for (String keyword : keywords.split(",")) {
            String trimmed = normalize(keyword);
            if (!trimmed.isEmpty() && content.contains(trimmed)) {
                return Optional.of(new AgentKeywordMatch(agent, trimmed));
            }
        }
        return Optional.empty();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private record AgentKeywordMatch(AiAgent agent, String keyword) {
    }
}

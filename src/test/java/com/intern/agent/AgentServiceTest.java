package com.intern.agent;

import com.intern.mapper.AiAgentMapper;
import com.intern.model.entity.AiAgent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentServiceTest {
    @Test
    void routesByKeywordPriority() {
        AiAgentMapper mapper = mock(AiAgentMapper.class);
        when(mapper.selectList(any())).thenReturn(List.of(
                agent("general", "通用", 100, "你好,咨询"),
                agent("handoff", "转人工", 5, "人工,转人工"),
                agent("complaint", "投诉", 10, "投诉,人工")
        ));

        AgentService service = new AgentService(mapper, new AgentRoutingPolicy());

        assertThat(service.route("我要投诉并转人工").getCode()).isEqualTo("complaint");
        AgentRouteDecision decision = service.decideRoute("我要投诉并转人工");
        assertThat(decision.agent().getCode()).isEqualTo("complaint");
        assertThat(decision.matchedKeyword()).isEqualTo("投诉");
        assertThat(decision.fallback()).isFalse();
        assertThat(decision.handoffRecommended()).isTrue();
    }

    @Test
    void handoffAgentDoesNotOverrideBusinessRoute() {
        AiAgentMapper mapper = mock(AiAgentMapper.class);
        when(mapper.selectList(any())).thenReturn(List.of(
                agent("handoff", "转人工", 5, "人工,转人工,主管"),
                agent("complaint", "投诉", 10, "投诉,人工,主管"),
                agent("general", "通用", 100, "你好")
        ));

        AgentService service = new AgentService(mapper, new AgentRoutingPolicy());

        AgentRouteDecision decision = service.decideRoute("我要投诉并转人工");
        assertThat(decision.agent().getCode()).isEqualTo("complaint");
        assertThat(decision.handoffRecommended()).isTrue();
    }

    @Test
    void detectsHandoffIntent() {
        AgentService service = new AgentService(mock(AiAgentMapper.class), new AgentRoutingPolicy());

        assertThat(service.shouldHandoff("我不满意，帮我转人工")).isTrue();
    }

    @Test
    void fallsBackToGeneralAgentWithoutReloadingRules() {
        AiAgentMapper mapper = mock(AiAgentMapper.class);
        when(mapper.selectList(any())).thenReturn(List.of(
                agent("general", "通用", 100, "你好"),
                agent("presales", "售前", 20, "价格")
        ));

        AgentService service = new AgentService(mapper, new AgentRoutingPolicy());

        AgentRouteDecision decision = service.decideRoute("我想了解配送时间");
        assertThat(decision.agent().getCode()).isEqualTo("general");
        assertThat(decision.matchedKeyword()).isNull();
        assertThat(decision.fallback()).isTrue();
        assertThat(decision.handoffRecommended()).isFalse();
    }

    @Test
    void ignoresDisabledMatchedAgentAndFallsBackToEnabledGeneral() {
        AiAgentMapper mapper = mock(AiAgentMapper.class);
        AiAgent disabledPresales = agent("presales", "售前", 20, "价格");
        disabledPresales.setEnabled(false);
        when(mapper.selectList(any())).thenReturn(List.of(
                agent("general", "通用", 100, "你好"),
                disabledPresales
        ));

        AgentService service = new AgentService(mapper, new AgentRoutingPolicy());

        AgentRouteDecision decision = service.decideRoute("我想了解价格");
        assertThat(decision.agent().getCode()).isEqualTo("general");
        assertThat(decision.fallback()).isTrue();
    }

    @Test
    void failsWhenOnlyDisabledGeneralCanFallback() {
        AiAgentMapper mapper = mock(AiAgentMapper.class);
        AiAgent disabledGeneral = agent("general", "通用", 100, "你好");
        disabledGeneral.setEnabled(false);
        when(mapper.selectList(any())).thenReturn(List.of(disabledGeneral));

        AgentService service = new AgentService(mapper, new AgentRoutingPolicy());

        assertThatThrownBy(() -> service.decideRoute("未知问题"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("缺少启用的通用客服智能体");
    }

    private AiAgent agent(String code, String name, int priority, String keywords) {
        AiAgent agent = new AiAgent();
        agent.setCode(code);
        agent.setName(name);
        agent.setPriority(priority);
        agent.setEnabled(true);
        agent.setTriggerKeywords(keywords);
        return agent;
    }
}

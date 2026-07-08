package com.intern.agent;

import com.intern.mapper.AiAgentMapper;
import com.intern.model.entity.AiAgent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentServiceTest {
    @Test
    void routesByKeywordPriority() {
        AiAgentMapper mapper = mock(AiAgentMapper.class);
        when(mapper.selectList(any())).thenReturn(List.of(
                agent("general", "通用", 100, "你好,咨询"),
                agent("complaint", "投诉", 10, "投诉,人工")
        ));

        AgentService service = new AgentService(mapper);

        assertThat(service.route("我要投诉并转人工").getCode()).isEqualTo("complaint");
    }

    @Test
    void detectsHandoffIntent() {
        AgentService service = new AgentService(mock(AiAgentMapper.class));

        assertThat(service.shouldHandoff("我不满意，帮我转人工")).isTrue();
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

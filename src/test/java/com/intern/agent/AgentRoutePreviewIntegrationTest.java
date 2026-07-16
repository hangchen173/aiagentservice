package com.intern.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.intern.mapper.ChatMessageMapper;
import com.intern.mapper.ChatSessionMapper;
import com.intern.mapper.HandoffRecordMapper;
import com.intern.mapper.ModelCallLogMapper;
import com.intern.mapper.TicketMapper;
import com.intern.support.IntegrationTestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:agent_route_preview;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "nexusmind.bootstrap.admin.username=test_admin",
        "nexusmind.bootstrap.admin.password=test-admin-password",
        "nexusmind.bootstrap.agent.username=test_agent",
        "nexusmind.bootstrap.agent.password=test-agent-password",
        "nexusmind.ai.dashscope-api-key="
})
@AutoConfigureMockMvc
class AgentRoutePreviewIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ChatSessionMapper chatSessionMapper;

    @Autowired
    private ChatMessageMapper chatMessageMapper;

    @Autowired
    private TicketMapper ticketMapper;

    @Autowired
    private HandoffRecordMapper handoffRecordMapper;

    @Autowired
    private ModelCallLogMapper modelCallLogMapper;

    private IntegrationTestFixture fixture;

    @BeforeEach
    void setUpData() {
        fixture = new IntegrationTestFixture(jdbcTemplate, mockMvc, objectMapper);
        fixture.reset();
        fixture.seedUsers("ADMIN", "VISITOR");
        fixture.seedDefaultAgents();
    }

    @Test
    void adminCanPreviewRouteWithoutCreatingWorkflowSideEffects() throws Exception {
        String token = fixture.login("admin", "admin123");

        mockMvc.perform(post("/api/agents/route-preview")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"想了解价格和购买方案\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.agentCode").value("presales"))
                .andExpect(jsonPath("$.data.agentName").value("售前咨询智能体"))
                .andExpect(jsonPath("$.data.scenario").value("产品、价格、方案咨询"))
                .andExpect(jsonPath("$.data.matchedKeyword").value("价格"))
                .andExpect(jsonPath("$.data.fallback").value(false))
                .andExpect(jsonPath("$.data.handoffRecommended").value(false));

        assertThat(chatSessionMapper.selectList(null)).isEmpty();
        assertThat(chatMessageMapper.selectList(null)).isEmpty();
        assertThat(ticketMapper.selectList(null)).isEmpty();
        assertThat(handoffRecordMapper.selectList(null)).isEmpty();
        assertThat(modelCallLogMapper.selectList(null)).isEmpty();
    }

    @Test
    void visitorCannotPreviewAdminOnlyRoutingDiagnostics() throws Exception {
        String token = fixture.login("visitor", "visitor123");

        mockMvc.perform(post("/api/agents/route-preview")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"我要投诉\"}"))
                .andExpect(status().isForbidden());
    }

}

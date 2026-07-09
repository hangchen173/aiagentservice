package com.intern.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.intern.mapper.ChatMessageMapper;
import com.intern.mapper.ChatSessionMapper;
import com.intern.mapper.HandoffRecordMapper;
import com.intern.mapper.ModelCallLogMapper;
import com.intern.mapper.TicketMapper;
import com.intern.model.entity.ChatMessage;
import com.intern.model.entity.ChatSession;
import com.intern.model.entity.HandoffRecord;
import com.intern.model.entity.ModelCallLog;
import com.intern.model.entity.Ticket;
import com.intern.support.IntegrationTestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:chat_workflow;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.sql.init.mode=always",
        "nexusmind.ai.dashscope-api-key="
})
@AutoConfigureMockMvc
class ChatWorkflowIntegrationTest {
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
        fixture.seedUsers("VISITOR");
        fixture.seedDefaultModel();
        fixture.seedDefaultAgents();
    }

    @Test
    void visitorComplaintRoutesToComplaintAgentCreatesTicketAndPersistsAiReply() throws Exception {
        String token = fixture.login("visitor", "visitor123");

        long sessionId = fixture.createSession(token);

        mockMvc.perform(post("/api/chat/sessions/{id}/messages", sessionId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"我要投诉并转人工，申请退款\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.senderType").value("AI"))
                .andExpect(jsonPath("$.data.content").value("我理解你的诉求。这个问题建议转人工客服进一步核实，我也会同步生成工单，方便后续跟进处理。"));

        ChatSession session = chatSessionMapper.selectById(sessionId);
        assertThat(session.getCurrentAiAgentCode()).isEqualTo("complaint");
        assertThat(session.getStatus()).isEqualTo("PENDING_HANDOFF");

        List<ChatMessage> messages = chatMessageMapper.selectList(null);
        assertThat(messages).extracting(ChatMessage::getSenderType).containsExactly("VISITOR", "AI");
        assertThat(messages).extracting(ChatMessage::getContent)
                .anySatisfy(content -> assertThat(content).contains("申请退款"))
                .anySatisfy(content -> assertThat(content).contains("建议转人工客服"));

        List<Ticket> tickets = ticketMapper.selectList(null);
        assertThat(tickets).hasSize(1);
        assertThat(tickets.get(0).getPriority()).isEqualTo("HIGH");
        assertThat(tickets.get(0).getStatus()).isEqualTo("OPEN");

        List<ModelCallLog> logs = modelCallLogMapper.selectList(null);
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getStatus()).isEqualTo("SUCCESS");
        assertThat(logs.get(0).getAgentCode()).isEqualTo("complaint");
    }

    @Test
    void visitorPresalesQuestionRoutesWithoutCreatingTicket() throws Exception {
        String token = fixture.login("visitor", "visitor123");
        long sessionId = fixture.createSession(token);

        mockMvc.perform(post("/api/chat/sessions/{id}/messages", sessionId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"请介绍一下价格和试用方案\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.senderType").value("AI"))
                .andExpect(jsonPath("$.data.content").value("NexusMind 支持多模型接入、智能体调度和人工流转。你可以先描述使用规模、接入渠道和预算区间，我会帮你整理合适方案。"));

        ChatSession session = chatSessionMapper.selectById(sessionId);
        assertThat(session.getCurrentAiAgentCode()).isEqualTo("presales");
        assertThat(session.getStatus()).isEqualTo("AI_SERVING");
        assertThat(ticketMapper.selectList(null)).isEmpty();
        assertThat(handoffRecordMapper.selectList(null)).isEmpty();

        List<ModelCallLog> logs = modelCallLogMapper.selectList(null);
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getAgentCode()).isEqualTo("presales");
    }

    @Test
    void explicitHandoffEndpointCreatesSystemMessageTicketAndHandoffRecord() throws Exception {
        String token = fixture.login("visitor", "visitor123");
        long sessionId = fixture.createSession(token);

        mockMvc.perform(post("/api/chat/sessions/{id}/handoff", sessionId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"访客主动请求转人工\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("OPEN"))
                .andExpect(jsonPath("$.data.priority").value("NORMAL"));

        ChatSession session = chatSessionMapper.selectById(sessionId);
        assertThat(session.getStatus()).isEqualTo("PENDING_HANDOFF");

        List<ChatMessage> messages = chatMessageMapper.selectList(null);
        assertThat(messages).extracting(ChatMessage::getSenderType).containsExactly("SYSTEM");
        assertThat(messages.get(0).getContent()).contains("已为你转人工并创建工单");

        List<HandoffRecord> handoffRecords = handoffRecordMapper.selectList(null);
        assertThat(handoffRecords).hasSize(1);
        assertThat(handoffRecords.get(0).getStatus()).isEqualTo("PENDING");

        List<Ticket> tickets = ticketMapper.selectList(null);
        assertThat(tickets).hasSize(1);
        assertThat(tickets.get(0).getDescription()).isEqualTo("访客主动请求转人工");
    }

    @Test
    void visitorOnlyListsOwnSessions() throws Exception {
        String visitorToken = fixture.login("visitor", "visitor123");
        long ownSessionId = fixture.createSession(visitorToken);
        jdbcTemplate.update("""
                insert into sys_users (username, password, display_name, role, online, created_at, updated_at, deleted)
                values ('visitor2', '{noop}visitor123', '第二访客', 'VISITOR', false, current_timestamp, current_timestamp, false)
                """);
        String otherVisitorToken = fixture.login("visitor2", "visitor123");
        fixture.createSession(otherVisitorToken);

        mockMvc.perform(get("/api/chat/sessions")
                        .header("Authorization", "Bearer " + visitorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(ownSessionId));
    }

    @Test
    void visitorCannotReadOrOperateOtherVisitorSession() throws Exception {
        String visitorToken = fixture.login("visitor", "visitor123");
        jdbcTemplate.update("""
                insert into sys_users (username, password, display_name, role, online, created_at, updated_at, deleted)
                values ('visitor2', '{noop}visitor123', '第二访客', 'VISITOR', false, current_timestamp, current_timestamp, false)
                """);
        String otherVisitorToken = fixture.login("visitor2", "visitor123");
        long otherSessionId = fixture.createSession(otherVisitorToken);

        mockMvc.perform(get("/api/chat/sessions/{id}/messages", otherSessionId)
                        .header("Authorization", "Bearer " + visitorToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("权限不足"));

        mockMvc.perform(post("/api/chat/sessions/{id}/handoff", otherSessionId)
                        .header("Authorization", "Bearer " + visitorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"越权转人工\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("权限不足"));
    }

    @Test
    void missingSessionMessagesReturnNotFound() throws Exception {
        String token = fixture.login("visitor", "visitor123");

        mockMvc.perform(get("/api/chat/sessions/{id}/messages", 99999L)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("会话不存在"));
    }

    @Test
    void createSessionAllowsEmptyBodyAndUsesDefaultTitle() throws Exception {
        String token = fixture.login("visitor", "visitor123");

        mockMvc.perform(post("/api/chat/sessions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.title").value("访客咨询"));
    }

}

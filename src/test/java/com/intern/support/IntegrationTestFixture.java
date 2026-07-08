package com.intern.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class IntegrationTestFixture {
    private final JdbcTemplate jdbcTemplate;
    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;

    public IntegrationTestFixture(JdbcTemplate jdbcTemplate, MockMvc mockMvc, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
    }

    public void reset() {
        jdbcTemplate.update("delete from model_call_logs");
        jdbcTemplate.update("delete from tickets");
        jdbcTemplate.update("delete from handoff_records");
        jdbcTemplate.update("delete from chat_messages");
        jdbcTemplate.update("delete from chat_sessions");
        jdbcTemplate.update("delete from ai_agents");
        jdbcTemplate.update("delete from ai_models");
        jdbcTemplate.update("delete from sys_users");
    }

    public void seedUsers(String... roles) {
        for (String role : roles) {
            if ("ADMIN".equals(role)) {
                jdbcTemplate.update("""
                        insert into sys_users (username, password, display_name, role, online, created_at, updated_at, deleted)
                        values ('admin', '{noop}admin123', '系统管理员', 'ADMIN', false, current_timestamp, current_timestamp, false)
                        """);
            }
            if ("VISITOR".equals(role)) {
                jdbcTemplate.update("""
                        insert into sys_users (username, password, display_name, role, online, created_at, updated_at, deleted)
                        values ('visitor', '{noop}visitor123', '演示访客', 'VISITOR', false, current_timestamp, current_timestamp, false)
                        """);
            }
        }
    }

    public void seedDefaultModel() {
        jdbcTemplate.update("""
                insert into ai_models (provider, model_name, temperature, max_tokens, enabled, created_at, updated_at, deleted)
                values ('DASHSCOPE', 'qwen3.7-plus', 0.70, 1200, true, current_timestamp, current_timestamp, false)
                """);
    }

    public void seedDefaultAgents() {
        jdbcTemplate.update("""
                insert into ai_agents (code, name, scenario, prompt, priority, enabled, trigger_keywords, created_at, updated_at, deleted)
                values
                  ('general', '通用客服智能体', '通用咨询与兜底回复', '你是 NexusMind 通用客服智能体。', 100, true, '你好,咨询,帮助', current_timestamp, current_timestamp, false),
                  ('presales', '售前咨询智能体', '产品、价格、方案咨询', '你是 NexusMind 售前咨询智能体。', 20, true, '价格,方案,购买,试用,报价', current_timestamp, current_timestamp, false),
                  ('complaint', '投诉处理智能体', '投诉与升级处理', '你是 NexusMind 投诉处理智能体。', 10, true, '投诉,不满意,举报,差评,人工', current_timestamp, current_timestamp, false)
                """);
    }

    public String login(String username, String password) throws Exception {
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode root = objectMapper.readTree(response);
        return root.path("data").path("token").asText();
    }

    public long createSession(String token) throws Exception {
        String response = mockMvc.perform(post("/api/chat/sessions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"集成测试会话\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode root = objectMapper.readTree(response);
        return root.path("data").path("id").asLong();
    }
}

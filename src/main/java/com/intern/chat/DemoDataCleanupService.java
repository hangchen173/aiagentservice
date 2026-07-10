package com.intern.chat;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DemoDataCleanupService {
    private final JdbcTemplate jdbcTemplate;

    public DemoDataCleanupService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public DemoDataCleanupResponse cleanup() {
        String targetSessions = """
                select id from chat_sessions
                where deleted = false
                  and (
                    title like 'LOAD_SESSION_%'
                    or title like '%_COVERAGE_%'
                    or title like 'HANDOFF_IDEMPOTENCY_CHECK%'
                    or (title = '访客咨询' and not exists (
                        select 1 from chat_messages where chat_messages.session_id = chat_sessions.id and chat_messages.deleted = false
                    ))
                  )
                """;
        int logs = jdbcTemplate.update("delete from model_call_logs where session_id in (" + targetSessions + ")");
        int tickets = jdbcTemplate.update("delete from tickets where session_id in (" + targetSessions + ")");
        int handoffs = jdbcTemplate.update("delete from handoff_records where session_id in (" + targetSessions + ")");
        int messages = jdbcTemplate.update("delete from chat_messages where session_id in (" + targetSessions + ")");
        int sessions = jdbcTemplate.update("delete from chat_sessions where id in (" + targetSessions + ")");
        return new DemoDataCleanupResponse(sessions, messages, tickets, handoffs, logs);
    }
}

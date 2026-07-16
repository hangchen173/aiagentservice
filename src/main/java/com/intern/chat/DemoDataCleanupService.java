package com.intern.chat;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DemoDataCleanupService {
    private final JdbcTemplate jdbcTemplate;
    private final ImageStorageService imageStorageService;

    public DemoDataCleanupService(JdbcTemplate jdbcTemplate, ImageStorageService imageStorageService) {
        this.jdbcTemplate = jdbcTemplate;
        this.imageStorageService = imageStorageService;
    }

    @Transactional
    public DemoDataCleanupResponse cleanup() {
        var attachmentKeys = jdbcTemplate.queryForList(
                "select attachment_key from chat_messages where attachment_key is not null", String.class);
        String targetSessions = "select id from chat_sessions";
        int logs = jdbcTemplate.update("delete from model_call_logs where session_id in (" + targetSessions + ")");
        int tickets = jdbcTemplate.update("delete from tickets where session_id in (" + targetSessions + ")");
        int handoffs = jdbcTemplate.update("delete from handoff_records where session_id in (" + targetSessions + ")");
        int messages = jdbcTemplate.update("delete from chat_messages where session_id in (" + targetSessions + ")");
        int sessions = jdbcTemplate.update("delete from chat_sessions where id in (" + targetSessions + ")");
        imageStorageService.deleteAll(attachmentKeys);
        return new DemoDataCleanupResponse(sessions, messages, tickets, handoffs, logs);
    }
}

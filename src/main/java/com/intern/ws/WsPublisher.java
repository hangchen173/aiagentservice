package com.intern.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class WsPublisher {
    private final ObjectMapper objectMapper;
    private final WsSessionRegistry registry;

    public WsPublisher(ObjectMapper objectMapper, WsSessionRegistry registry) {
        this.objectMapper = objectMapper;
        this.registry = registry;
    }

    public void publish(Long sessionId, String type, String content) {
        try {
            registry.broadcast(sessionId, objectMapper.writeValueAsString(new WsMessage(type, sessionId, content)));
        } catch (Exception ignored) {
        }
    }
}

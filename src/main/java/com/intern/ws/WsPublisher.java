package com.intern.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class WsPublisher {
    public static final String CHAT_CHANNEL = "nexusmind:ws:chat";

    private final ObjectMapper objectMapper;
    private final WsSessionRegistry registry;
    private final StringRedisTemplate redisTemplate;
    private final boolean redisPubSubEnabled;

    public WsPublisher(ObjectMapper objectMapper, WsSessionRegistry registry,
                       StringRedisTemplate redisTemplate,
                       @Value("${nexusmind.websocket.redis-pubsub-enabled:false}") boolean redisPubSubEnabled) {
        this.objectMapper = objectMapper;
        this.registry = registry;
        this.redisTemplate = redisTemplate;
        this.redisPubSubEnabled = redisPubSubEnabled;
    }

    public void publish(Long sessionId, String type, String content) {
        try {
            String payload = objectMapper.writeValueAsString(new WsMessage(type, sessionId, content));
            if (redisPubSubEnabled) {
                try {
                    redisTemplate.convertAndSend(CHAT_CHANNEL, payload);
                } catch (Exception ex) {
                    registry.broadcast(sessionId, payload);
                }
            } else {
                registry.broadcast(sessionId, payload);
            }
        } catch (Exception ignored) {
        }
    }

    public void publishLocal(String payload) {
        try {
            WsMessage message = objectMapper.readValue(payload, WsMessage.class);
            registry.broadcast(message.getSessionId(), payload);
        } catch (Exception ignored) {
        }
    }
}

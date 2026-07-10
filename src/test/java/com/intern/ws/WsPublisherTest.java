package com.intern.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WsPublisherTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void publishesThroughRedisWhenPubSubIsEnabled() {
        WsSessionRegistry registry = mock(WsSessionRegistry.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        WsPublisher publisher = new WsPublisher(objectMapper, registry, redisTemplate, true);

        publisher.publish(7L, "AI_MESSAGE_DELTA", "你好");

        verify(redisTemplate).convertAndSend(eq(WsPublisher.CHAT_CHANNEL), contains("AI_MESSAGE_DELTA"));
        verify(registry, never()).broadcast(eq(7L), contains("AI_MESSAGE_DELTA"));
    }

    @Test
    void broadcastsLocalMessageReceivedFromRedisSubscription() {
        WsSessionRegistry registry = mock(WsSessionRegistry.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        WsPublisher publisher = new WsPublisher(objectMapper, registry, redisTemplate, true);

        publisher.publishLocal("{\"type\":\"AI_MESSAGE_DONE\",\"sessionId\":8,\"content\":\"完成\"}");

        verify(registry).broadcast(eq(8L), contains("AI_MESSAGE_DONE"));
    }

    @Test
    void fallsBackToLocalBroadcastWhenRedisPublishFails() {
        WsSessionRegistry registry = mock(WsSessionRegistry.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.convertAndSend(eq(WsPublisher.CHAT_CHANNEL), contains("TICKET_CREATED")))
                .thenThrow(new IllegalStateException("redis down"));
        WsPublisher publisher = new WsPublisher(objectMapper, registry, redisTemplate, true);

        publisher.publish(9L, "TICKET_CREATED", "已创建工单");

        verify(registry).broadcast(eq(9L), contains("TICKET_CREATED"));
    }
}

package com.intern.config;

import com.intern.ws.WsPublisher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.nio.charset.StandardCharsets;

@Configuration
@ConditionalOnProperty(prefix = "nexusmind.websocket", name = "redis-pubsub-enabled", havingValue = "true")
public class RedisWebSocketConfig {
    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            WsPublisher wsPublisher) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener((message, pattern) ->
                        wsPublisher.publishLocal(new String(message.getBody(), StandardCharsets.UTF_8)),
                new ChannelTopic(WsPublisher.CHAT_CHANNEL));
        return container;
    }
}

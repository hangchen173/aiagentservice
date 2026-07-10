package com.intern.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@Primary
@ConditionalOnProperty(prefix = "nexusmind.rate-limit", name = "redis-enabled", havingValue = "true")
public class RedisRateLimitStore implements RateLimitStore {
    private final StringRedisTemplate redisTemplate;
    private final InMemoryRateLimitStore fallback = new InMemoryRateLimitStore();

    public RedisRateLimitStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean tryConsume(String key, long windowMillis, int maxRequests) {
        String redisKey = "nexusmind:rate-limit:" + key;
        try {
            Long count = redisTemplate.opsForValue().increment(redisKey);
            if (count != null && count == 1L) {
                redisTemplate.expire(redisKey, Duration.ofMillis(windowMillis));
            }
            return count != null && count <= maxRequests;
        } catch (Exception ex) {
            return fallback.tryConsume(key, windowMillis, maxRequests);
        }
    }
}

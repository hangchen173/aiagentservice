package com.intern.security;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryRateLimitStore implements RateLimitStore {
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final Clock clock;

    public InMemoryRateLimitStore() {
        this(Clock.systemUTC());
    }

    InMemoryRateLimitStore(Clock clock) {
        this.clock = clock;
    }

    @Override
    public boolean tryConsume(String key, long windowMillis, int maxRequests) {
        long now = clock.millis();
        Bucket bucket = buckets.compute(key, (ignored, current) -> {
            if (current == null || now - current.windowStartMillis >= windowMillis) {
                return new Bucket(now, 1);
            }
            return new Bucket(current.windowStartMillis, current.count + 1);
        });
        return bucket.count <= maxRequests;
    }

    private record Bucket(long windowStartMillis, int count) {
    }
}

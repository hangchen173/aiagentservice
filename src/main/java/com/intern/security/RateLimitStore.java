package com.intern.security;

public interface RateLimitStore {
    boolean tryConsume(String key, long windowMillis, int maxRequests);
}

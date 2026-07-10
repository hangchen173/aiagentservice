package com.intern.security;

import com.intern.common.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class RateLimitFilter extends OncePerRequestFilter {
    private final ObjectMapper objectMapper;
    private final RateLimitStore rateLimitStore;
    private final long windowMillis;
    private final int maxRequests;

    public RateLimitFilter(
            ObjectMapper objectMapper,
            RateLimitStore rateLimitStore,
            @Value("${nexusmind.rate-limit.window-seconds:60}") long windowSeconds,
            @Value("${nexusmind.rate-limit.max-requests:120}") int maxRequests) {
        this.objectMapper = objectMapper;
        this.rateLimitStore = rateLimitStore;
        this.windowMillis = windowSeconds * 1000L;
        this.maxRequests = maxRequests;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return !uri.startsWith("/api/auth")
                && !uri.startsWith("/api/chat")
                && !uri.startsWith("/api/tickets")
                && !uri.startsWith("/api/agents/route-preview")
                && !uri.startsWith("/ws/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String key = request.getRemoteAddr() + ":" + userKey(request) + ":" + request.getRequestURI();
        if (!rateLimitStore.tryConsume(key, windowMillis, maxRequests)) {
            response.setStatus(429);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.fail("请求过于频繁，请稍后再试")));
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String userKey(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || authorization.isBlank()) {
            return "anonymous";
        }
        return Integer.toHexString(authorization.hashCode());
    }
}

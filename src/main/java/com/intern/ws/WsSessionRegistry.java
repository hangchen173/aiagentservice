package com.intern.ws;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class WsSessionRegistry {
    private final Map<Long, Set<WebSocketSession>> sessions = new ConcurrentHashMap<>();

    public void join(Long sessionId, WebSocketSession webSocketSession) {
        sessions.computeIfAbsent(sessionId, key -> ConcurrentHashMap.newKeySet()).add(webSocketSession);
    }

    public void remove(WebSocketSession webSocketSession) {
        sessions.values().forEach(set -> set.remove(webSocketSession));
    }

    public void broadcast(Long sessionId, String payload) {
        Set<WebSocketSession> joined = sessions.get(sessionId);
        if (joined == null) {
            return;
        }
        joined.removeIf(session -> !session.isOpen());
        for (WebSocketSession session : joined) {
            try {
                session.sendMessage(new TextMessage(payload));
            } catch (IOException ignored) {
                joined.remove(session);
            }
        }
    }
}

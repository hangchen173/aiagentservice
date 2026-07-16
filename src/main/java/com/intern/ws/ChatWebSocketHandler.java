package com.intern.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.intern.chat.ChatService;
import com.intern.security.AuthUser;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;

@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {
    private final ObjectMapper objectMapper;
    private final WsSessionRegistry registry;
    private final ChatService chatService;

    public ChatWebSocketHandler(ObjectMapper objectMapper, WsSessionRegistry registry, ChatService chatService) {
        this.objectMapper = objectMapper;
        this.registry = registry;
        this.chatService = chatService;
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        AuthUser user = (AuthUser) session.getAttributes().get(WsAuthHandshakeInterceptor.AUTH_USER_ATTRIBUTE);
        if (user == null) {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("请先登录"));
            return;
        }
        WsMessage wsMessage = objectMapper.readValue(message.getPayload(), WsMessage.class);
        if (wsMessage.getSessionId() == null) {
            sendError(session, null, "缺少会话 ID");
            return;
        }
        if ("JOIN_SESSION".equals(wsMessage.getType())) {
            try {
                chatService.requireAccessibleSession(wsMessage.getSessionId(), user);
            } catch (AccessDeniedException | ResponseStatusException ex) {
                sendError(session, wsMessage.getSessionId(), "会话不存在或无权访问");
                session.close(CloseStatus.POLICY_VIOLATION);
                return;
            }
            registry.join(wsMessage.getSessionId(), session);
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(
                    new WsMessage("SESSION_JOINED", wsMessage.getSessionId(), "已接入实时会话"))));
            return;
        }
        if ("CHAT_MESSAGE".equals(wsMessage.getType())) {
            chatService.handleVisitorMessageStream(wsMessage.getSessionId(), wsMessage.getContent(), user)
                    .subscribe(
                            ignored -> {
                            },
                            error -> {
                                try {
                                    sendError(session, wsMessage.getSessionId(), "实时 AI 回复失败，请稍后再试");
                                } catch (Exception ignored) {
                                }
                            });
            return;
        }
        if ("HANDOFF_REQUEST".equals(wsMessage.getType())) {
            chatService.handoff(wsMessage.getSessionId(), wsMessage.getContent(), user);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        registry.remove(session);
    }

    private void sendError(WebSocketSession session, Long sessionId, String content) throws Exception {
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(new WsMessage("ERROR", sessionId, content))));
    }
}

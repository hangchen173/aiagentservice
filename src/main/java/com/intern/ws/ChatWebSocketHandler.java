package com.intern.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.intern.chat.ChatService;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

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
        WsMessage wsMessage = objectMapper.readValue(message.getPayload(), WsMessage.class);
        if ("JOIN_SESSION".equals(wsMessage.getType())) {
            registry.join(wsMessage.getSessionId(), session);
            registry.broadcast(wsMessage.getSessionId(), objectMapper.writeValueAsString(
                    new WsMessage("SYSTEM_NOTICE", wsMessage.getSessionId(), "已接入实时会话")));
            return;
        }
        if ("CHAT_MESSAGE".equals(wsMessage.getType())) {
            chatService.handleVisitorMessage(wsMessage.getSessionId(), wsMessage.getContent());
            return;
        }
        if ("HANDOFF_REQUEST".equals(wsMessage.getType())) {
            chatService.handoff(wsMessage.getSessionId(), wsMessage.getContent());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        registry.remove(session);
    }
}

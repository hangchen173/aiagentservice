package com.intern.chat;

import com.intern.common.ApiResponse;
import com.intern.model.entity.ChatMessage;
import com.intern.model.entity.ChatSession;
import com.intern.model.entity.Ticket;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
public class ChatController {
    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping("/sessions")
    public ApiResponse<ChatSession> createSession(@Valid @RequestBody(required = false) CreateSessionRequest request) {
        return ApiResponse.ok(chatService.createSession(request));
    }

    @GetMapping("/sessions")
    public ApiResponse<List<ChatSession>> listSessions() {
        return ApiResponse.ok(chatService.listSessions());
    }

    @GetMapping("/sessions/{id}/messages")
    public ApiResponse<List<ChatMessage>> listMessages(@PathVariable Long id) {
        return ApiResponse.ok(chatService.listMessages(id));
    }

    @PostMapping("/sessions/{id}/messages")
    public ApiResponse<ChatMessage> sendMessage(@PathVariable Long id, @Valid @RequestBody SendMessageRequest request) {
        return ApiResponse.ok(chatService.sendRestMessage(id, request));
    }

    @PostMapping("/sessions/{id}/handoff")
    public ApiResponse<Ticket> handoff(@PathVariable Long id, @Valid @RequestBody SendMessageRequest request) {
        return ApiResponse.ok(chatService.handoff(id, request.content()));
    }
}

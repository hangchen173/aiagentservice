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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

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

    @PostMapping("/sessions/{id}/agent-messages")
    public ApiResponse<ChatMessage> sendAgentMessage(@PathVariable Long id,
                                                      @Valid @RequestBody SendMessageRequest request) {
        return ApiResponse.ok(chatService.sendAgentMessage(id, request.content()));
    }

    @PostMapping(value = "/sessions/{id}/messages/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ChatMessage> sendImage(
            @PathVariable Long id,
            @RequestParam(name = "content", required = false) String content,
            @RequestParam("image") MultipartFile image) {
        return ApiResponse.ok(chatService.handleImageMessage(id, content, image));
    }

    @GetMapping("/sessions/{sessionId}/messages/{messageId}/image")
    public ResponseEntity<ByteArrayResource> getImage(
            @PathVariable Long sessionId,
            @PathVariable Long messageId) {
        ImageDownload image = chatService.loadMessageImage(sessionId, messageId);
        ByteArrayResource resource = new ByteArrayResource(image.data());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(image.filename(), java.nio.charset.StandardCharsets.UTF_8)
                        .build().toString())
                .body(resource);
    }

    @PostMapping("/sessions/{id}/handoff")
    public ApiResponse<Ticket> handoff(@PathVariable Long id, @Valid @RequestBody SendMessageRequest request) {
        return ApiResponse.ok(chatService.handoff(id, request.content()));
    }
}

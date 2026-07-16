package com.intern.chat;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.intern.agent.AgentService;
import com.intern.agent.AgentRouteDecision;
import com.intern.aimodel.AiModelService;
import com.intern.aimodel.ImageInput;
import com.intern.common.BusinessException;
import com.intern.mapper.ChatMessageMapper;
import com.intern.mapper.ChatSessionMapper;
import com.intern.model.entity.AiAgent;
import com.intern.model.entity.ChatMessage;
import com.intern.model.entity.ChatSession;
import com.intern.model.entity.Ticket;
import com.intern.security.AuthUser;
import com.intern.security.SecurityContext;
import com.intern.ticket.TicketService;
import com.intern.ws.WsPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;

import java.util.List;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ChatService {
    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final AgentService agentService;
    private final AiModelService aiModelService;
    private final TicketService ticketService;
    private final WsPublisher wsPublisher;
    private final ImageStorageService imageStorageService;

    public ChatService(
            ChatSessionMapper chatSessionMapper,
            ChatMessageMapper chatMessageMapper,
            AgentService agentService,
            AiModelService aiModelService,
            TicketService ticketService,
            WsPublisher wsPublisher,
            ImageStorageService imageStorageService) {
        this.chatSessionMapper = chatSessionMapper;
        this.chatMessageMapper = chatMessageMapper;
        this.agentService = agentService;
        this.aiModelService = aiModelService;
        this.ticketService = ticketService;
        this.wsPublisher = wsPublisher;
        this.imageStorageService = imageStorageService;
    }

    public ChatSession createSession(CreateSessionRequest request) {
        AuthUser user = SecurityContext.currentUser();
        if (!isVisitor(user)) {
            throw new AccessDeniedException("只有普通用户可以创建咨询会话");
        }
        ChatSession session = new ChatSession();
        session.setVisitorId(user.id());
        String requestedTitle = request == null ? null : request.title();
        session.setTitle((requestedTitle == null || requestedTitle.isBlank()) ? "访客咨询" : requestedTitle.trim());
        session.setStatus("AI_SERVING");
        session.setCurrentAiAgentCode("general");
        chatSessionMapper.insert(session);
        return session;
    }

    public List<ChatSession> listSessions() {
        AuthUser user = SecurityContext.currentUser();
        LambdaQueryWrapper<ChatSession> query = new LambdaQueryWrapper<ChatSession>()
                .orderByDesc(ChatSession::getUpdatedAt);
        if (isVisitor(user)) {
            query.eq(ChatSession::getVisitorId, user.id());
        }
        return chatSessionMapper.selectList(query);
    }

    public List<ChatMessage> listMessages(Long sessionId) {
        requireAccessibleSession(sessionId, SecurityContext.currentUser());
        return chatMessageMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getSessionId, sessionId)
                .orderByAsc(ChatMessage::getCreatedAt));
    }

    @Transactional
    public ChatMessage sendRestMessage(Long sessionId, SendMessageRequest request) {
        return handleVisitorMessage(sessionId, request.content());
    }

    @Transactional
    public ChatMessage handleVisitorMessage(Long sessionId, String content) {
        return handleVisitorMessage(sessionId, content, SecurityContext.currentUser());
    }

    @Transactional
    public ChatMessage handleVisitorMessage(Long sessionId, String content, AuthUser user) {
        ChatSession session = requireOwnedVisitorSession(sessionId, user);
        ChatMessage userMessage = saveMessage(sessionId, "VISITOR", session.getVisitorId(), content);
        wsPublisher.publish(sessionId, "CHAT_MESSAGE", content);

        AgentRouteDecision routeDecision = agentService.decideRoute(content);
        AiAgent routedAgent = routeDecision.agent();
        session.setCurrentAiAgentCode(routedAgent.getCode());
        chatSessionMapper.updateById(session);

        wsPublisher.publish(sessionId, "SYSTEM_NOTICE", routedAgent.getName() + " 已接入");
        String reply = aiModelService.complete(sessionId, routedAgent, content);
        ChatMessage aiMessage = saveMessage(sessionId, "AI", null, reply);
        wsPublisher.publish(sessionId, "AI_MESSAGE", reply);

        if (routeDecision.handoffRecommended()) {
            Ticket ticket = ticketService.createFromHandoff(sessionId, session.getVisitorId(), routedAgent.getCode(), content);
            session.setStatus("PENDING_HANDOFF");
            chatSessionMapper.updateById(session);
            String notice = "已创建工单 #" + ticket.getId();
            saveMessage(sessionId, "SYSTEM", null, notice);
            wsPublisher.publish(sessionId, "TICKET_CREATED", notice);
        }
        return aiMessage.getId() == null ? userMessage : aiMessage;
    }

    @Transactional
    public ChatMessage sendAgentMessage(Long sessionId, String content) {
        AuthUser user = SecurityContext.currentUser();
        if (!"AGENT".equals(user.role()) && !"ADMIN".equals(user.role())) {
            throw new AccessDeniedException("只有客服可以回复");
        }
        ChatSession session = requireAccessibleSession(sessionId, user);
        if ("CLOSED".equals(session.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "会话已关闭");
        }
        ticketService.requireReplyAllowed(sessionId, user);
        ChatMessage message = saveMessage(sessionId, "AGENT", user.id(), content);
        wsPublisher.publish(sessionId, "AGENT_MESSAGE", content);
        return message;
    }

    public ChatMessage handleImageMessage(Long sessionId, String content, MultipartFile image) {
        AuthUser user = SecurityContext.currentUser();
        ChatSession session = requireOwnedVisitorSession(sessionId, user);
        String question = content == null || content.isBlank() ? "请描述这张图片" : content.trim();
        if (question.length() > 1000) {
            throw new BusinessException("图片问题不能超过 1000 个字符");
        }
        StoredImage storedImage = imageStorageService.store(image);
        ChatMessage userMessage = saveImageMessage(sessionId, session.getVisitorId(), question, storedImage);
        wsPublisher.publish(sessionId, "CHAT_MESSAGE", "[图片] " + question);

        AgentRouteDecision routeDecision = agentService.decideRoute(question);
        AiAgent routedAgent = routeDecision.agent();
        session.setCurrentAiAgentCode(routedAgent.getCode());
        chatSessionMapper.updateById(session);
        wsPublisher.publish(sessionId, "SYSTEM_NOTICE", routedAgent.getName() + " 已接入并分析图片");

        ImageInput input = new ImageInput(storedImage.data(), storedImage.contentType(), storedImage.originalName());
        String reply = aiModelService.completeWithImage(sessionId, routedAgent, question, input);
        ChatMessage aiMessage = saveMessage(sessionId, "AI", null, reply);
        wsPublisher.publish(sessionId, "AI_MESSAGE", reply);
        return aiMessage.getId() == null ? userMessage : aiMessage;
    }

    public ImageDownload loadMessageImage(Long sessionId, Long messageId) {
        requireAccessibleSession(sessionId, SecurityContext.currentUser());
        ChatMessage message = chatMessageMapper.selectById(messageId);
        if (message == null || !sessionId.equals(message.getSessionId()) || !"IMAGE".equals(message.getMessageType())
                || message.getAttachmentKey() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "图片不存在");
        }
        return new ImageDownload(
                imageStorageService.read(message.getAttachmentKey()),
                message.getAttachmentContentType(),
                message.getAttachmentName());
    }

    public Flux<String> handleVisitorMessageStream(Long sessionId, String content, AuthUser user) {
        ChatSession session = requireOwnedVisitorSession(sessionId, user);
        saveMessage(sessionId, "VISITOR", session.getVisitorId(), content);
        wsPublisher.publish(sessionId, "CHAT_MESSAGE", content);

        AgentRouteDecision routeDecision = agentService.decideRoute(content);
        AiAgent routedAgent = routeDecision.agent();
        session.setCurrentAiAgentCode(routedAgent.getCode());
        chatSessionMapper.updateById(session);

        wsPublisher.publish(sessionId, "SYSTEM_NOTICE", routedAgent.getName() + " 已接入");
        StringBuilder reply = new StringBuilder();
        return aiModelService.stream(sessionId, routedAgent, content)
                .doOnNext(chunk -> {
                    reply.append(chunk);
                    wsPublisher.publish(sessionId, "AI_MESSAGE_DELTA", chunk);
                })
                .doOnComplete(() -> {
                    saveMessage(sessionId, "AI", null, reply.toString());
                    wsPublisher.publish(sessionId, "AI_MESSAGE_DONE", reply.toString());
                    if (routeDecision.handoffRecommended()) {
                        Ticket ticket = ticketService.createFromHandoff(sessionId, session.getVisitorId(), routedAgent.getCode(), content);
                        session.setStatus("PENDING_HANDOFF");
                        chatSessionMapper.updateById(session);
                        String notice = "已创建工单 #" + ticket.getId();
                        saveMessage(sessionId, "SYSTEM", null, notice);
                        wsPublisher.publish(sessionId, "TICKET_CREATED", notice);
                    }
                });
    }

    @Transactional
    public Ticket handoff(Long sessionId, String reason) {
        return handoff(sessionId, reason, SecurityContext.currentUser());
    }

    @Transactional
    public Ticket handoff(Long sessionId, String reason, AuthUser user) {
        ChatSession session = requireOwnedVisitorSession(sessionId, user);
        Ticket ticket = ticketService.createFromHandoff(sessionId, session.getVisitorId(), session.getCurrentAiAgentCode(), reason);
        session.setStatus("PENDING_HANDOFF");
        chatSessionMapper.updateById(session);
        String notice = "已为你转人工并创建工单 #" + ticket.getId();
        if (!hasSystemMessage(sessionId, notice)) {
            saveMessage(sessionId, "SYSTEM", null, notice);
        }
        wsPublisher.publish(sessionId, "TICKET_CREATED", notice);
        return ticket;
    }

    private ChatSession requireSession(Long sessionId) {
        ChatSession session = chatSessionMapper.selectById(sessionId);
        if (session == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "会话不存在");
        }
        return session;
    }

    public ChatSession requireAccessibleSession(Long sessionId, AuthUser user) {
        ChatSession session = requireSession(sessionId);
        if (isVisitor(user) && !session.getVisitorId().equals(user.id())) {
            throw new AccessDeniedException("只能访问自己的会话");
        }
        return session;
    }

    private ChatSession requireOwnedVisitorSession(Long sessionId, AuthUser user) {
        ChatSession session = requireAccessibleSession(sessionId, user);
        if (!isVisitor(user)) {
            throw new AccessDeniedException("只有访客可以发送访客消息");
        }
        return session;
    }

    private boolean isVisitor(AuthUser user) {
        return "VISITOR".equals(user.role());
    }

    private boolean hasSystemMessage(Long sessionId, String content) {
        Long count = chatMessageMapper.selectCount(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getSessionId, sessionId)
                .eq(ChatMessage::getSenderType, "SYSTEM")
                .eq(ChatMessage::getContent, content));
        return count != null && count > 0;
    }

    private ChatMessage saveMessage(Long sessionId, String senderType, Long senderId, String content) {
        ChatMessage message = new ChatMessage();
        message.setSessionId(sessionId);
        message.setSenderType(senderType);
        message.setSenderId(senderId);
        message.setContent(content);
        message.setMessageType("TEXT");
        chatMessageMapper.insert(message);
        return message;
    }

    private ChatMessage saveImageMessage(Long sessionId, Long senderId, String content, StoredImage image) {
        ChatMessage message = new ChatMessage();
        message.setSessionId(sessionId);
        message.setSenderType("VISITOR");
        message.setSenderId(senderId);
        message.setContent(content);
        message.setMessageType("IMAGE");
        message.setAttachmentKey(image.key());
        message.setAttachmentContentType(image.contentType());
        message.setAttachmentName(image.originalName());
        chatMessageMapper.insert(message);
        return message;
    }
}

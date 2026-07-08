package com.intern.chat;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.intern.agent.AgentService;
import com.intern.agent.AgentRouteDecision;
import com.intern.aimodel.AiModelService;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ChatService {
    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final AgentService agentService;
    private final AiModelService aiModelService;
    private final TicketService ticketService;
    private final WsPublisher wsPublisher;

    public ChatService(
            ChatSessionMapper chatSessionMapper,
            ChatMessageMapper chatMessageMapper,
            AgentService agentService,
            AiModelService aiModelService,
            TicketService ticketService,
            WsPublisher wsPublisher) {
        this.chatSessionMapper = chatSessionMapper;
        this.chatMessageMapper = chatMessageMapper;
        this.agentService = agentService;
        this.aiModelService = aiModelService;
        this.ticketService = ticketService;
        this.wsPublisher = wsPublisher;
    }

    public ChatSession createSession(CreateSessionRequest request) {
        AuthUser user = SecurityContext.currentUser();
        ChatSession session = new ChatSession();
        session.setVisitorId(user.id());
        session.setTitle((request.title() == null || request.title().isBlank()) ? "访客咨询" : request.title());
        session.setStatus("AI_SERVING");
        session.setCurrentAiAgentCode("general");
        chatSessionMapper.insert(session);
        return session;
    }

    public List<ChatSession> listSessions() {
        return chatSessionMapper.selectList(new LambdaQueryWrapper<ChatSession>()
                .orderByDesc(ChatSession::getUpdatedAt));
    }

    public List<ChatMessage> listMessages(Long sessionId) {
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
        ChatSession session = requireSession(sessionId);
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
            wsPublisher.publish(sessionId, "TICKET_CREATED", "已创建工单 #" + ticket.getId());
        }
        return aiMessage.getId() == null ? userMessage : aiMessage;
    }

    @Transactional
    public Ticket handoff(Long sessionId, String reason) {
        ChatSession session = requireSession(sessionId);
        Ticket ticket = ticketService.createFromHandoff(sessionId, session.getVisitorId(), session.getCurrentAiAgentCode(), reason);
        session.setStatus("PENDING_HANDOFF");
        chatSessionMapper.updateById(session);
        saveMessage(sessionId, "SYSTEM", null, "已为你转人工并创建工单 #" + ticket.getId());
        wsPublisher.publish(sessionId, "TICKET_CREATED", "已为你转人工并创建工单 #" + ticket.getId());
        return ticket;
    }

    private ChatSession requireSession(Long sessionId) {
        ChatSession session = chatSessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BusinessException("会话不存在");
        }
        return session;
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
}

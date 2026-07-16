package com.intern.ticket;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.intern.mapper.HandoffRecordMapper;
import com.intern.mapper.ChatMessageMapper;
import com.intern.mapper.ChatSessionMapper;
import com.intern.mapper.TicketMapper;
import com.intern.model.entity.ChatMessage;
import com.intern.model.entity.ChatSession;
import com.intern.model.entity.HandoffRecord;
import com.intern.model.entity.Ticket;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.intern.security.AuthUser;
import com.intern.security.SecurityContext;
import com.intern.ws.WsPublisher;

import java.util.List;

@Service
public class TicketService {
    private final TicketMapper ticketMapper;
    private final HandoffRecordMapper handoffRecordMapper;
    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final WsPublisher wsPublisher;

    public TicketService(TicketMapper ticketMapper, HandoffRecordMapper handoffRecordMapper,
                         ChatSessionMapper chatSessionMapper, ChatMessageMapper chatMessageMapper,
                         WsPublisher wsPublisher) {
        this.ticketMapper = ticketMapper;
        this.handoffRecordMapper = handoffRecordMapper;
        this.chatSessionMapper = chatSessionMapper;
        this.chatMessageMapper = chatMessageMapper;
        this.wsPublisher = wsPublisher;
    }

    public List<Ticket> list() {
        AuthUser user = SecurityContext.currentUser();
        LambdaQueryWrapper<Ticket> query = new LambdaQueryWrapper<Ticket>().orderByDesc(Ticket::getCreatedAt);
        if ("AGENT".equals(user.role())) {
            query.and(scope -> scope.eq(Ticket::getStatus, "OPEN").or().eq(Ticket::getAssigneeId, user.id()));
        }
        return ticketMapper.selectList(query);
    }

    public Ticket createFromHandoff(Long sessionId, Long requesterId, String agentCode, String reason) {
        Ticket existingTicket = findOpenTicket(sessionId);
        if (existingTicket != null) {
            return existingTicket;
        }

        HandoffRecord handoff = new HandoffRecord();
        handoff.setSessionId(sessionId);
        handoff.setFromAiAgentCode(agentCode);
        handoff.setReason(reason);
        handoff.setStatus("PENDING");
        handoffRecordMapper.insert(handoff);

        Ticket ticket = new Ticket();
        ticket.setSessionId(sessionId);
        ticket.setRequesterId(requesterId);
        ticket.setTitle("会话 #" + sessionId + " 需要人工跟进");
        ticket.setDescription(reason);
        ticket.setStatus("OPEN");
        ticket.setPriority(reason.contains("投诉") ? "HIGH" : "NORMAL");
        ticketMapper.insert(ticket);
        return ticket;
    }

    @Transactional
    public Ticket accept(Long id) {
        AuthUser user = SecurityContext.currentUser();
        Ticket ticket = requireTicket(id);
        if ("CLOSED".equals(ticket.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "已关闭工单不能接单");
        }
        if (ticket.getAssigneeId() != null && !ticket.getAssigneeId().equals(user.id()) && !isAdmin(user)) {
            throw new AccessDeniedException("工单已由其他客服处理");
        }
        ticket.setStatus("PROCESSING");
        ticket.setAssigneeId(user.id());
        ticketMapper.updateById(ticket);
        updateHandoffs(ticket.getSessionId(), "ACCEPTED", user.id());
        ChatSession session = requireSession(ticket.getSessionId());
        session.setStatus("AGENT_SERVING");
        session.setAssignedAgentId(user.id());
        chatSessionMapper.updateById(session);
        publishSystem(ticket.getSessionId(), "人工客服已接入", "AGENT_ACCEPTED");
        return ticketMapper.selectById(id);
    }

    @Transactional
    public Ticket close(Long id) {
        AuthUser user = SecurityContext.currentUser();
        Ticket ticket = requireTicket(id);
        requireOwnerOrAdmin(ticket, user);
        if (!"CLOSED".equals(ticket.getStatus())) {
            ticket.setStatus("CLOSED");
            ticketMapper.updateById(ticket);
            updateHandoffs(ticket.getSessionId(), "CLOSED", ticket.getAssigneeId());
            ChatSession session = requireSession(ticket.getSessionId());
            session.setStatus("CLOSED");
            chatSessionMapper.updateById(session);
            publishSystem(ticket.getSessionId(), "人工服务已结束", "SESSION_CLOSED");
        }
        return ticketMapper.selectById(id);
    }

    @Transactional
    public void delete(Long id) {
        AuthUser user = SecurityContext.currentUser();
        Ticket ticket = requireTicket(id);
        requireOwnerOrAdmin(ticket, user);
        if (!"CLOSED".equals(ticket.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "只能删除已关闭工单");
        }
        ticketMapper.deleteById(ticket);
    }

    public Ticket requireReplyAllowed(Long sessionId, AuthUser user) {
        Ticket ticket = ticketMapper.selectOne(new LambdaQueryWrapper<Ticket>()
                .eq(Ticket::getSessionId, sessionId)
                .eq(Ticket::getStatus, "PROCESSING")
                .orderByDesc(Ticket::getCreatedAt)
                .last("limit 1"));
        if (ticket == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "请先接单再回复");
        }
        requireOwnerOrAdmin(ticket, user);
        return ticket;
    }

    private Ticket requireTicket(Long id) {
        Ticket ticket = ticketMapper.selectById(id);
        if (ticket == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "工单不存在");
        return ticket;
    }

    private ChatSession requireSession(Long id) {
        ChatSession session = chatSessionMapper.selectById(id);
        if (session == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "会话不存在");
        return session;
    }

    private void requireOwnerOrAdmin(Ticket ticket, AuthUser user) {
        if (!isAdmin(user) && (ticket.getAssigneeId() == null || !ticket.getAssigneeId().equals(user.id()))) {
            throw new AccessDeniedException("只能处理分配给自己的工单");
        }
    }

    private boolean isAdmin(AuthUser user) {
        return "ADMIN".equals(user.role());
    }

    private void updateHandoffs(Long sessionId, String status, Long agentId) {
        List<HandoffRecord> records = handoffRecordMapper.selectList(new LambdaQueryWrapper<HandoffRecord>()
                .eq(HandoffRecord::getSessionId, sessionId));
        records.forEach(record -> {
            record.setStatus(status);
            if (agentId != null) record.setToAgentId(agentId);
            handoffRecordMapper.updateById(record);
        });
    }

    private void publishSystem(Long sessionId, String content, String eventType) {
        ChatMessage message = new ChatMessage();
        message.setSessionId(sessionId);
        message.setSenderType("SYSTEM");
        message.setContent(content);
        message.setMessageType("SYSTEM");
        chatMessageMapper.insert(message);
        wsPublisher.publish(sessionId, eventType, content);
    }

    private Ticket findOpenTicket(Long sessionId) {
        return ticketMapper.selectOne(new LambdaQueryWrapper<Ticket>()
                .eq(Ticket::getSessionId, sessionId)
                .in(Ticket::getStatus, List.of("OPEN", "PROCESSING"))
                .orderByDesc(Ticket::getCreatedAt)
                .last("limit 1"));
    }
}

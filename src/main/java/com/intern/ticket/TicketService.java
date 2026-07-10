package com.intern.ticket;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.intern.mapper.HandoffRecordMapper;
import com.intern.mapper.TicketMapper;
import com.intern.model.entity.HandoffRecord;
import com.intern.model.entity.Ticket;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class TicketService {
    private final TicketMapper ticketMapper;
    private final HandoffRecordMapper handoffRecordMapper;

    public TicketService(TicketMapper ticketMapper, HandoffRecordMapper handoffRecordMapper) {
        this.ticketMapper = ticketMapper;
        this.handoffRecordMapper = handoffRecordMapper;
    }

    public List<Ticket> list() {
        return ticketMapper.selectList(new LambdaQueryWrapper<Ticket>().orderByDesc(Ticket::getCreatedAt));
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

    public Ticket updateStatus(Long id, Ticket input) {
        Ticket ticket = ticketMapper.selectById(id);
        if (ticket == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "工单不存在");
        }
        ticket.setStatus(input.getStatus());
        ticket.setAssigneeId(input.getAssigneeId());
        ticketMapper.updateById(ticket);
        return ticketMapper.selectById(id);
    }

    private Ticket findOpenTicket(Long sessionId) {
        return ticketMapper.selectOne(new LambdaQueryWrapper<Ticket>()
                .eq(Ticket::getSessionId, sessionId)
                .in(Ticket::getStatus, List.of("OPEN", "PROCESSING"))
                .orderByDesc(Ticket::getCreatedAt)
                .last("limit 1"));
    }
}

package com.intern.ticket;

import com.intern.common.ApiResponse;
import com.intern.model.entity.Ticket;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/tickets")
public class TicketController {
    private final TicketService ticketService;

    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @GetMapping
    public ApiResponse<List<Ticket>> list() {
        return ApiResponse.ok(ticketService.list());
    }

    @PutMapping("/{id}/status")
    public ApiResponse<Ticket> updateStatus(@PathVariable Long id, @RequestBody Ticket ticket) {
        return ApiResponse.ok(ticketService.updateStatus(id, ticket));
    }
}

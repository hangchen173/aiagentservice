package com.intern.ticket;

import com.intern.common.ApiResponse;
import com.intern.model.entity.Ticket;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
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

    @PostMapping("/{id}/accept")
    public ApiResponse<Ticket> accept(@PathVariable Long id) {
        return ApiResponse.ok(ticketService.accept(id));
    }

    @PostMapping("/{id}/close")
    public ApiResponse<Ticket> close(@PathVariable Long id) {
        return ApiResponse.ok(ticketService.close(id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        ticketService.delete(id);
        return ApiResponse.ok(null);
    }
}

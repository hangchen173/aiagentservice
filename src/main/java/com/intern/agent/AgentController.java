package com.intern.agent;

import com.intern.common.ApiResponse;
import com.intern.model.entity.AiAgent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/agents")
public class AgentController {
    private final AgentService agentService;

    public AgentController(AgentService agentService) {
        this.agentService = agentService;
    }

    @GetMapping
    public ApiResponse<List<AiAgent>> list() {
        return ApiResponse.ok(agentService.list());
    }

    @PostMapping
    public ApiResponse<AiAgent> create(@RequestBody AiAgent agent) {
        return ApiResponse.ok(agentService.create(agent));
    }

    @PutMapping("/{id}")
    public ApiResponse<AiAgent> update(@PathVariable Long id, @RequestBody AiAgent agent) {
        return ApiResponse.ok(agentService.update(id, agent));
    }
}

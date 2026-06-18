package com.smart.kf.controller;

import com.smart.kf.service.agent.AgentExecutionService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/agents/{agentId}")
public class AgentExecutionController {

    private final AgentExecutionService executionService;

    public AgentExecutionController(AgentExecutionService executionService) {
        this.executionService = executionService;
    }

    @GetMapping("/executions")
    @PreAuthorize("hasAuthority('agent:read')")
    public ResponseEntity<?> listExecutions(
        @PathVariable String agentId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return ok("获取执行历史成功", executionService.listExecutionLogs(agentId, page, size));
    }

    @GetMapping("/executions/{executionId}")
    @PreAuthorize("hasAuthority('agent:read')")
    public ResponseEntity<?> getExecution(@PathVariable String executionId) {
        return ok("获取执行详情成功", executionService.getExecutionLog(executionId));
    }

    private ResponseEntity<?> ok(String message, Object data) {
        return ResponseEntity.ok(Map.of("code", 200, "message", message, "data", data));
    }
}

package com.smart.kf.controller;

import com.smart.kf.model.agent.Agent;
import com.smart.kf.service.agent.AgentExecutionService;
import com.smart.kf.service.agent.AgentService;
import com.smart.kf.utils.pagination.PageQuery;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/agents")
public class AgentController {

    private final AgentService agentService;
    private final AgentExecutionService agentExecutionService;

    public AgentController(AgentService agentService, AgentExecutionService agentExecutionService) {
        this.agentService = agentService;
        this.agentExecutionService = agentExecutionService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('agent:read')")
    public ResponseEntity<?> listAgents(
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) Integer page,
        @RequestParam(required = false) Integer size,
        @RequestParam(required = false) String cursor
    ) {
        return ok("获取Agent列表成功", agentService.listAgents(keyword, PageQuery.of(page, size, cursor)));
    }

    @GetMapping("/stats")
    public ResponseEntity<?> agentStats() {
        return ok("获取Agent统计成功", agentService.agentStats());
    }

    @GetMapping("/{agentId}")
    public ResponseEntity<?> getAgent(@PathVariable String agentId) {
        return ok("获取Agent详情成功", agentService.getAgent(agentId));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('agent:write')")
    public ResponseEntity<?> createAgent(@RequestBody Agent request) {
        return ok("保存Agent成功", agentService.saveAgent(request));
    }

    @PutMapping("/{agentId}")
    @PreAuthorize("hasAuthority('agent:write')")
    public ResponseEntity<?> updateAgent(@PathVariable String agentId, @RequestBody Agent request) {
        request.setAgentId(agentId);
        return ok("保存Agent成功", agentService.saveAgent(request));
    }

    @PostMapping("/{agentId}/copy")
    public ResponseEntity<?> copyAgent(@PathVariable String agentId) {
        return ok("复制Agent成功", agentService.copyAgent(agentId));
    }

    @PostMapping("/{agentId}/publish")
    @PreAuthorize("hasAuthority('agent:write')")
    public ResponseEntity<?> publishAgent(@PathVariable String agentId) {
        return ok("发布Agent成功", agentService.publishAgent(agentId));
    }

    @DeleteMapping("/{agentId}")
    @PreAuthorize("hasAuthority('agent:write')")
    public ResponseEntity<?> deleteAgent(@PathVariable String agentId) {
        agentService.deleteAgent(agentId);
        return ok("删除Agent成功", null);
    }

    @PostMapping("/{agentId}/chat")
    @PreAuthorize("hasAuthority('agent:run')")
    public ResponseEntity<?> chatAgent(
        @PathVariable String agentId,
        @RequestBody Map<String, Object> input,
        org.springframework.security.core.Authentication authentication
    ) {
        String query = (String) input.get("query");
        @SuppressWarnings("unchecked")
        java.util.List<Map<String, String>> history = (java.util.List<Map<String, String>>) input.get("history");
        String username = authentication != null ? authentication.getName() : "anonymous";
        return ok("Agent对话成功",
            agentExecutionService.chat(agentId, query, history, input, username));
    }

    private ResponseEntity<?> ok(String message, Object data) {
        Map<String, Object> response = new HashMap<>();
        response.put("code", 200);
        response.put("message", message);
        response.put("data", data);
        return ResponseEntity.ok(response);
    }
}

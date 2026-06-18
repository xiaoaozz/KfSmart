package com.smart.kf.controller;

import com.smart.kf.service.agent.AgentVersionService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/agents/{agentId}/versions")
public class AgentVersionController {

    private final AgentVersionService versionService;

    public AgentVersionController(AgentVersionService versionService) {
        this.versionService = versionService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('agent:read')")
    public ResponseEntity<?> listVersions(@PathVariable String agentId) {
        return ok("获取版本列表成功", versionService.listVersions(agentId));
    }

    @GetMapping("/{versionId}")
    @PreAuthorize("hasAuthority('agent:read')")
    @SuppressWarnings("unused")
    public ResponseEntity<?> getVersion(@PathVariable String agentId, @PathVariable String versionId) {
        return ok("获取版本详情成功", versionService.getVersion(versionId));
    }
    @PostMapping("/{versionId}/rollback")
    @PreAuthorize("hasAuthority('agent:write')")
    public ResponseEntity<?> rollback(
        @PathVariable String agentId,
        @PathVariable String versionId,
        org.springframework.security.core.Authentication authentication
    ) {
        String operator = authentication != null ? authentication.getName() : "anonymous";
        return ok("回滚成功", versionService.rollback(agentId, versionId, operator));
    }

    @PostMapping("/{versionId}/activate")
    @PreAuthorize("hasAuthority('agent:write')")
    public ResponseEntity<?> activateVersion(@PathVariable String versionId) {
        versionService.activateVersion(versionId);
        return ok("激活成功", null);
    }

    private ResponseEntity<?> ok(String message, Object data) {
        Map<String, Object> body = new HashMap<>();
        body.put("code", 200);
        body.put("message", message);
        body.put("data", data);
        return ResponseEntity.ok(body);
    }
}

package com.smart.kf.controller;

import com.smart.kf.service.workflow.WorkflowVersionService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/workflows/{workflowId}/versions")
public class WorkflowVersionController {

    private final WorkflowVersionService versionService;

    public WorkflowVersionController(WorkflowVersionService versionService) {
        this.versionService = versionService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('agent:read')")
    public ResponseEntity<?> listVersions(@PathVariable String workflowId) {
        return ok("获取版本列表成功", versionService.listVersions(workflowId));
    }

    @GetMapping("/{versionId}")
    @PreAuthorize("hasAuthority('agent:read')")
    @SuppressWarnings("unused")
    public ResponseEntity<?> getVersion(@PathVariable String workflowId, @PathVariable String versionId) {
        return ok("获取版本详情成功", versionService.getVersion(versionId));
    }

    @PostMapping("/{versionId}/rollback")
    @PreAuthorize("hasAuthority('agent:write')")
    public ResponseEntity<?> rollback(
        @PathVariable String workflowId,
        @PathVariable String versionId,
        org.springframework.security.core.Authentication authentication
    ) {
        String operator = authentication != null ? authentication.getName() : "anonymous";
        return ok("回滚成功", versionService.rollback(workflowId, versionId, operator));
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

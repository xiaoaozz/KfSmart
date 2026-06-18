package com.smart.kf.controller;

import com.smart.kf.service.workflow.WorkflowExecutionService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/workflows/{workflowId}")
public class WorkflowExecutionController {

    private final WorkflowExecutionService executionService;

    public WorkflowExecutionController(WorkflowExecutionService executionService) {
        this.executionService = executionService;
    }

    @PostMapping("/execute")
    @PreAuthorize("hasAuthority('agent:run')")
    public ResponseEntity<?> executeSync(
        @PathVariable String workflowId,
        @RequestBody Map<String, Object> input
    ) {
        return ok("执行成功", executionService.executeSync(workflowId, input, currentUsername()));
    }

    @PostMapping("/execute-async")
    @PreAuthorize("hasAuthority('agent:run')")
    public ResponseEntity<?> executeAsync(
        @PathVariable String workflowId,
        @RequestBody Map<String, Object> input
    ) {
        String executionId = executionService.executeAsync(workflowId, input, currentUsername());
        return ok("异步执行已提交", Map.of("executionId", executionId));
    }

    @GetMapping("/executions")
    @PreAuthorize("hasAuthority('agent:read')")
    public ResponseEntity<?> listExecutions(
        @PathVariable String workflowId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return ok("获取执行历史成功", executionService.listExecutionLogs(workflowId, page, size));
    }

    @GetMapping("/executions/{executionId}")
    @PreAuthorize("hasAuthority('agent:read')")
    public ResponseEntity<?> getExecution(@PathVariable String executionId) {
        return ok("获取执行详情成功", executionService.getExecutionLog(executionId));
    }

    private String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "anonymous";
    }

    private ResponseEntity<?> ok(String message, Object data) {
        Map<String, Object> response = new HashMap<>();
        response.put("code", 200);
        response.put("message", message);
        response.put("data", data);
        return ResponseEntity.ok(response);
    }
}

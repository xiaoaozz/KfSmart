package com.smart.kf.controller;

import com.smart.kf.model.workflow.Workflow;
import com.smart.kf.service.workflow.WorkflowService;
import com.smart.kf.utils.pagination.PageQuery;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/workflows")
public class WorkflowController {

    private final WorkflowService workflowService;

    public WorkflowController(WorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('agent:read')")
    public ResponseEntity<?> listWorkflows(
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) Integer page,
        @RequestParam(required = false) Integer size,
        @RequestParam(required = false) String cursor
    ) {
        return ok("获取工作流列表成功", workflowService.listWorkflows(keyword, PageQuery.of(page, size, cursor)));
    }

    @GetMapping("/stats")
    public ResponseEntity<?> workflowStats() {
        return ok("获取工作流统计成功", workflowService.workflowStats());
    }

    @GetMapping("/{workflowId}")
    public ResponseEntity<?> getWorkflow(@PathVariable String workflowId) {
        return ok("获取工作流详情成功", workflowService.getWorkflow(workflowId));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('agent:write')")
    public ResponseEntity<?> createWorkflow(@RequestBody Workflow request) {
        return ok("保存工作流成功", workflowService.saveWorkflow(request));
    }

    @PutMapping("/{workflowId}")
    @PreAuthorize("hasAuthority('agent:write')")
    public ResponseEntity<?> updateWorkflow(@PathVariable String workflowId, @RequestBody Workflow request) {
        request.setWorkflowId(workflowId);
        return ok("保存工作流成功", workflowService.saveWorkflow(request));
    }

    @PostMapping("/{workflowId}/copy")
    public ResponseEntity<?> copyWorkflow(@PathVariable String workflowId) {
        return ok("复制工作流成功", workflowService.copyWorkflow(workflowId));
    }

    @PostMapping("/{workflowId}/publish")
    @PreAuthorize("hasAuthority('agent:write')")
    public ResponseEntity<?> publishWorkflow(@PathVariable String workflowId) {
        return ok("发布工作流成功", workflowService.publishWorkflow(workflowId));
    }

    @DeleteMapping("/{workflowId}")
    @PreAuthorize("hasAuthority('agent:write')")
    public ResponseEntity<?> deleteWorkflow(@PathVariable String workflowId) {
        workflowService.deleteWorkflow(workflowId);
        return ok("删除工作流成功", null);
    }

    @PostMapping("/{workflowId}/debug")
    @PreAuthorize("hasAuthority('agent:run')")
    public ResponseEntity<?> debugWorkflow(@PathVariable String workflowId, @RequestBody Map<String, Object> input) {
        return ok("调试运行成功", workflowService.debugWorkflow(workflowId, input, currentUsername()));
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

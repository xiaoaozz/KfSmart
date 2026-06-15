package com.smart.kf.controller;

import com.smart.kf.model.agent.AgentWorkflow;
import com.smart.kf.model.agent.McpToolConfig;
import com.smart.kf.model.agent.PromptTemplate;
import com.smart.kf.service.AgentCenterService;
import com.smart.kf.utils.pagination.PageQuery;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.HashMap;

@RestController
@RequestMapping("/api/v1/agent-center")
public class AgentCenterController {
    private final AgentCenterService agentCenterService;

    public AgentCenterController(AgentCenterService agentCenterService) {
        this.agentCenterService = agentCenterService;
    }

    @GetMapping("/workflows")
    @PreAuthorize("hasAuthority('agent:read')")
    public ResponseEntity<?> listWorkflows(
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) Integer page,
        @RequestParam(required = false) Integer size,
        @RequestParam(required = false) String cursor
    ) {
        return ok("获取工作流列表成功", agentCenterService.listWorkflows(keyword, PageQuery.of(page, size, cursor)));
    }

    @GetMapping("/workflows/stats")
    public ResponseEntity<?> workflowStats() {
        return ok("获取工作流统计成功", agentCenterService.workflowStats());
    }

    @GetMapping("/workflows/{workflowId}")
    public ResponseEntity<?> getWorkflow(@PathVariable String workflowId) {
        return ok("获取工作流详情成功", agentCenterService.getWorkflow(workflowId));
    }

    @PostMapping("/workflows")
    @PreAuthorize("hasAuthority('agent:write')")
    public ResponseEntity<?> createWorkflow(@RequestBody AgentWorkflow request) {
        return ok("保存工作流成功", agentCenterService.saveWorkflow(request));
    }

    @PutMapping("/workflows/{workflowId}")
    @PreAuthorize("hasAuthority('agent:write')")
    public ResponseEntity<?> updateWorkflow(@PathVariable String workflowId, @RequestBody AgentWorkflow request) {
        request.setWorkflowId(workflowId);
        return ok("保存工作流成功", agentCenterService.saveWorkflow(request));
    }

    @PostMapping("/workflows/{workflowId}/copy")
    public ResponseEntity<?> copyWorkflow(@PathVariable String workflowId) {
        return ok("复制工作流成功", agentCenterService.copyWorkflow(workflowId));
    }

    @PostMapping("/workflows/{workflowId}/publish")
    @PreAuthorize("hasAuthority('agent:write')")
    public ResponseEntity<?> publishWorkflow(@PathVariable String workflowId) {
        return ok("发布工作流成功", agentCenterService.publishWorkflow(workflowId));
    }

    @DeleteMapping("/workflows/{workflowId}")
    @PreAuthorize("hasAuthority('agent:write')")
    public ResponseEntity<?> deleteWorkflow(@PathVariable String workflowId) {
        agentCenterService.deleteWorkflow(workflowId);
        return ok("删除工作流成功", null);
    }

    @PostMapping("/workflows/{workflowId}/debug")
    @PreAuthorize("hasAuthority('agent:run')")
    public ResponseEntity<?> debugWorkflow(@PathVariable String workflowId, @RequestBody Map<String, Object> input) {
        return ok("调试运行成功", agentCenterService.debugWorkflow(workflowId, input, currentUsername()));
    }

    @GetMapping("/prompts")
    public ResponseEntity<?> listPrompts(
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) String category,
        @RequestParam(required = false) Integer page,
        @RequestParam(required = false) Integer size,
        @RequestParam(required = false) String cursor
    ) {
        return ok("获取Prompt列表成功", agentCenterService.listPrompts(keyword, category, PageQuery.of(page, size, cursor)));
    }

    @GetMapping("/prompts/categories")
    public ResponseEntity<?> listPromptCategories() {
        return ok("获取Prompt分类成功", agentCenterService.listPromptCategories());
    }

    @GetMapping("/prompts/{templateId}")
    public ResponseEntity<?> getPrompt(@PathVariable String templateId) {
        return ok("获取Prompt详情成功", agentCenterService.getPrompt(templateId));
    }

    @PostMapping("/prompts")
    @PreAuthorize("hasAuthority('agent:write')")
    public ResponseEntity<?> savePrompt(@RequestBody PromptTemplate request) {
        return ok("保存Prompt成功", agentCenterService.savePrompt(request, currentUsername()));
    }

    @PutMapping("/prompts/{templateId}")
    @PreAuthorize("hasAuthority('agent:write')")
    public ResponseEntity<?> updatePrompt(@PathVariable String templateId, @RequestBody PromptTemplate request) {
        request.setTemplateId(templateId);
        return ok("保存Prompt成功", agentCenterService.savePrompt(request, currentUsername()));
    }

    @PutMapping("/prompts/{templateId}/toggle-status")
    @PreAuthorize("hasAuthority('agent:write')")
    public ResponseEntity<?> togglePromptStatus(@PathVariable String templateId) {
        agentCenterService.togglePromptStatus(templateId);
        return ok("切换状态成功", null);
    }

    @DeleteMapping("/prompts/{templateId}")
    @PreAuthorize("hasAuthority('agent:write')")
    public ResponseEntity<?> deletePrompt(@PathVariable String templateId) {
        agentCenterService.deletePrompt(templateId);
        return ok("删除Prompt成功", null);
    }

    @GetMapping("/prompts/{templateId}/histories")
    @PreAuthorize("hasAuthority('agent:read')")
    public ResponseEntity<?> listPromptHistories(@PathVariable String templateId) {
        return ok("获取版本历史成功", agentCenterService.getPromptHistories(templateId));
    }

    @GetMapping("/prompts/{templateId}/histories/{snapshotId}")
    @PreAuthorize("hasAuthority('agent:read')")
    public ResponseEntity<?> getPromptHistory(@PathVariable String templateId, @PathVariable Long snapshotId) {
        return ok("获取版本详情成功", agentCenterService.getPromptHistory(templateId, snapshotId));
    }

    @PostMapping("/prompts/{templateId}/rollback/{snapshotId}")
    @PreAuthorize("hasAuthority('agent:write')")
    public ResponseEntity<?> rollbackPrompt(@PathVariable String templateId, @PathVariable Long snapshotId) {
        return ok("回滚成功", agentCenterService.rollbackPrompt(templateId, snapshotId, currentUsername()));
    }

    @GetMapping("/mcp-tools")
    public ResponseEntity<?> listTools(
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) Integer page,
        @RequestParam(required = false) Integer size,
        @RequestParam(required = false) String cursor
    ) {
        return ok("获取MCP工具列表成功", agentCenterService.listTools(keyword, PageQuery.of(page, size, cursor)));
    }

    @PostMapping("/mcp-tools")
    @PreAuthorize("hasAuthority('agent:write')")
    public ResponseEntity<?> saveTool(@RequestBody McpToolConfig request) {
        return ok("保存MCP工具成功", agentCenterService.saveTool(request));
    }

    @PutMapping("/mcp-tools/{toolId}")
    public ResponseEntity<?> updateTool(@PathVariable String toolId, @RequestBody McpToolConfig request) {
        request.setToolId(toolId);
        return ok("保存MCP工具成功", agentCenterService.saveTool(request));
    }

    @DeleteMapping("/mcp-tools/{toolId}")
    @PreAuthorize("hasAuthority('agent:write')")
    public ResponseEntity<?> deleteTool(@PathVariable String toolId) {
        agentCenterService.deleteTool(toolId);
        return ok("删除MCP工具成功", null);
    }

    @GetMapping("/marketplace")
    public ResponseEntity<?> marketplace() {
        return ok("获取Agent广场成功", agentCenterService.marketplace());
    }

    @GetMapping("/analysis")
    public ResponseEntity<?> analysis() {
        return ok("获取运行分析成功", agentCenterService.runAnalysis());
    }

    @GetMapping("/models")
    public ResponseEntity<?> models() {
        return ok("获取模型配置成功", agentCenterService.listModels());
    }

    private String currentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            return "anonymous";
        }
        return authentication.getName();
    }

    private ResponseEntity<?> ok(String message, Object data) {
        Map<String, Object> response = new HashMap<>();
        response.put("code", 200);
        response.put("message", message);
        response.put("data", data);
        return ResponseEntity.ok(response);
    }
}

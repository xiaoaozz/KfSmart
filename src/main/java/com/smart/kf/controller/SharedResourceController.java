package com.smart.kf.controller;

import com.smart.kf.model.agent.McpToolConfig;
import com.smart.kf.model.agent.PromptTemplate;
import com.smart.kf.service.SharedResourceService;
import com.smart.kf.service.agent.AgentService;
import com.smart.kf.utils.pagination.PageQuery;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/resources")
public class SharedResourceController {

    private final SharedResourceService resourceService;
    private final AgentService agentService;

    public SharedResourceController(SharedResourceService resourceService, AgentService agentService) {
        this.resourceService = resourceService;
        this.agentService = agentService;
    }

    // ── Prompt ──

    @GetMapping("/prompts")
    public ResponseEntity<?> listPrompts(
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) String category,
        @RequestParam(required = false) Integer page,
        @RequestParam(required = false) Integer size,
        @RequestParam(required = false) String cursor
    ) {
        return ok("获取Prompt列表成功", resourceService.listPrompts(keyword, category, PageQuery.of(page, size, cursor)));
    }

    @GetMapping("/prompts/categories")
    public ResponseEntity<?> listPromptCategories() {
        return ok("获取Prompt分类成功", resourceService.listPromptCategories());
    }

    @GetMapping("/prompts/{templateId}")
    public ResponseEntity<?> getPrompt(@PathVariable String templateId) {
        return ok("获取Prompt详情成功", resourceService.getPrompt(templateId));
    }

    @PostMapping("/prompts")
    @PreAuthorize("hasAuthority('agent:write')")
    public ResponseEntity<?> savePrompt(@RequestBody PromptTemplate request) {
        return ok("保存Prompt成功", resourceService.savePrompt(request, currentUsername()));
    }

    @PutMapping("/prompts/{templateId}")
    @PreAuthorize("hasAuthority('agent:write')")
    public ResponseEntity<?> updatePrompt(@PathVariable String templateId, @RequestBody PromptTemplate request) {
        request.setTemplateId(templateId);
        return ok("保存Prompt成功", resourceService.savePrompt(request, currentUsername()));
    }

    @PutMapping("/prompts/{templateId}/toggle-status")
    @PreAuthorize("hasAuthority('agent:write')")
    public ResponseEntity<?> togglePromptStatus(@PathVariable String templateId) {
        resourceService.togglePromptStatus(templateId);
        return ok("切换状态成功", null);
    }

    @DeleteMapping("/prompts/{templateId}")
    @PreAuthorize("hasAuthority('agent:write')")
    public ResponseEntity<?> deletePrompt(@PathVariable String templateId) {
        resourceService.deletePrompt(templateId);
        return ok("删除Prompt成功", null);
    }

    @GetMapping("/prompts/{templateId}/histories")
    @PreAuthorize("hasAuthority('agent:read')")
    public ResponseEntity<?> listPromptHistories(@PathVariable String templateId) {
        return ok("获取版本历史成功", resourceService.getPromptHistories(templateId));
    }

    @GetMapping("/prompts/{templateId}/histories/{snapshotId}")
    @PreAuthorize("hasAuthority('agent:read')")
    public ResponseEntity<?> getPromptHistory(@PathVariable String templateId, @PathVariable Long snapshotId) {
        return ok("获取版本详情成功", resourceService.getPromptHistory(templateId, snapshotId));
    }

    @PostMapping("/prompts/{templateId}/rollback/{snapshotId}")
    @PreAuthorize("hasAuthority('agent:write')")
    public ResponseEntity<?> rollbackPrompt(@PathVariable String templateId, @PathVariable Long snapshotId) {
        return ok("回滚成功", resourceService.rollbackPrompt(templateId, snapshotId, currentUsername()));
    }

    // ── MCP Tools ──

    @GetMapping("/mcp-tools")
    public ResponseEntity<?> listTools(
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) Integer page,
        @RequestParam(required = false) Integer size,
        @RequestParam(required = false) String cursor
    ) {
        return ok("获取MCP工具列表成功", resourceService.listTools(keyword, PageQuery.of(page, size, cursor)));
    }

    @PostMapping("/mcp-tools")
    @PreAuthorize("hasAuthority('agent:write')")
    public ResponseEntity<?> saveTool(@RequestBody McpToolConfig request) {
        return ok("保存MCP工具成功", resourceService.saveTool(request));
    }

    @PutMapping("/mcp-tools/{toolId}")
    public ResponseEntity<?> updateTool(@PathVariable String toolId, @RequestBody McpToolConfig request) {
        request.setToolId(toolId);
        return ok("保存MCP工具成功", resourceService.saveTool(request));
    }

    @DeleteMapping("/mcp-tools/{toolId}")
    @PreAuthorize("hasAuthority('agent:write')")
    public ResponseEntity<?> deleteTool(@PathVariable String toolId) {
        resourceService.deleteTool(toolId);
        return ok("删除MCP工具成功", null);
    }

    // ── Models ──

    @GetMapping("/models")
    public ResponseEntity<?> models() {
        return ok("获取模型配置成功", resourceService.listModels());
    }

    // ── Marketplace & Analysis ──

    @GetMapping("/marketplace")
    public ResponseEntity<?> marketplace() {
        return ok("获取Agent广场成功", agentService.marketplace());
    }

    @GetMapping("/analysis")
    public ResponseEntity<?> analysis() {
        return ok("获取运行分析成功", agentService.runAnalysis());
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

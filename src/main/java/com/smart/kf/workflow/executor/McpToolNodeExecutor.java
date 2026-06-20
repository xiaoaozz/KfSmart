package com.smart.kf.workflow.executor;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.kf.model.agent.McpToolConfig;
import com.smart.kf.service.McpToolInvocationService;
import com.smart.kf.workflow.engine.ExecutionContext;
import com.smart.kf.workflow.engine.NodeExecutionResult;
import com.smart.kf.workflow.engine.NodeExecutor;
import com.smart.kf.workflow.model.WorkflowNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class McpToolNodeExecutor implements NodeExecutor {

    private static final Logger logger = LoggerFactory.getLogger(McpToolNodeExecutor.class);

    private final McpToolInvocationService invocationService;
    private final ObjectMapper objectMapper;

    public McpToolNodeExecutor(McpToolInvocationService invocationService, ObjectMapper objectMapper) {
        this.invocationService = invocationService;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getNodeType() {
        return "MCP工具";
    }

    @Override
    public NodeExecutionResult execute(WorkflowNode node, ExecutionContext ctx) {
        String toolId = node.configString("toolId");
        String outputField = node.configString("outputField");
        if (outputField == null || outputField.isBlank()) {
            outputField = "result";
        }

        // 即时调试覆盖
        String debugMcpTools = (String) ctx.getVariable("debug_mcpTools");
        if (debugMcpTools != null && !debugMcpTools.isBlank()) {
            toolId = debugMcpTools;
        }

        if (toolId == null || toolId.isBlank()) {
            logger.info("MCP 工具节点未配置工具，跳过");
            Map<String, Object> outputs = new HashMap<>();
            outputs.put(outputField, "");
            ctx.setVariable("toolResult", "");
            return NodeExecutionResult.of(outputs, "未配置MCP工具，跳过执行");
        }

        McpToolConfig tool = findTool(toolId);
        Map<String, Object> arguments = buildArguments(node, ctx);

        Object result;
        try {
            result = invocationService.execute(toolId, arguments);
        } catch (Exception e) {
            String detail = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            throw new IllegalStateException("MCP 工具 [" + tool.getName() + "] 调用失败: " + detail, e);
        }

        ctx.setVariable("toolResult", result != null ? result : "");

        Map<String, Object> outputs = new HashMap<>();
        outputs.put(outputField, result != null ? result : "");
        outputs.put("result", result != null ? result : "");
        return NodeExecutionResult.of(outputs, "调用MCP工具[" + tool.getName() + "]");
    }

    private McpToolConfig findTool(String toolId) {
        return invocationService.resolveTool(toolId);
    }

    private Map<String, Object> buildArguments(WorkflowNode node, ExecutionContext ctx) {
        Map<String, Object> arguments = new HashMap<>();
        String inputMapping = node.configString("inputMapping");
        if (inputMapping != null && !inputMapping.isBlank()) {
            try {
                Map<String, Object> mapping = objectMapper.readValue(inputMapping, new TypeReference<>() {});
                mapping.forEach((key, value) -> arguments.put(key, resolveValue(value, ctx)));
            } catch (Exception e) {
                logger.warn("MCP 工具节点 inputMapping 解析失败，使用默认参数: {}", e.getMessage());
            }
        }

        arguments.putIfAbsent("query", ctx.getOrDefault("query", ""));
        arguments.putIfAbsent("variables", ctx.getVariables());
        return arguments;
    }

    private Object resolveValue(Object value, ExecutionContext ctx) {
        if (value instanceof String text) {
            return ctx.resolveTemplate(text);
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> resolved = new HashMap<>();
            map.forEach((key, nested) -> resolved.put(String.valueOf(key), resolveValue(nested, ctx)));
            return resolved;
        }
        if (value instanceof Iterable<?> iterable) {
            java.util.List<Object> resolved = new java.util.ArrayList<>();
            iterable.forEach(item -> resolved.add(resolveValue(item, ctx)));
            return resolved;
        }
        return value;
    }
}

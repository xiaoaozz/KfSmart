package com.smart.kf.agent.engine;

import com.smart.kf.agent.engine.tools.KnowledgeBaseTool;
import com.smart.kf.agent.engine.tools.McpToolExecutor;
import com.smart.kf.model.agent.Agent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class ToolRegistry {

    private static final Logger logger = LoggerFactory.getLogger(ToolRegistry.class);

    private final KnowledgeBaseTool knowledgeBaseTool;
    private final McpToolExecutor mcpToolExecutor;

    public ToolRegistry(
        KnowledgeBaseTool knowledgeBaseTool,
        McpToolExecutor mcpToolExecutor
    ) {
        this.knowledgeBaseTool = knowledgeBaseTool;
        this.mcpToolExecutor = mcpToolExecutor;
    }

    public List<ToolDefinition> resolveTools(Agent agent) {
        List<ToolDefinition> tools = new ArrayList<>();

        if (isPresent(agent.getKnowledgeBases())) {
            tools.add(buildKnowledgeBaseTool(agent.getKnowledgeBases()));
        }

        if (isPresent(agent.getMcpTools())) {
            for (String toolName : agent.getMcpTools().split(",")) {
                String trimmed = toolName.trim();
                if (!trimmed.isEmpty()) {
                    tools.add(buildMcpTool(trimmed));
                }
            }
        }

        return tools;
    }

    public List<Map<String, Object>> toOpenAiToolsFormat(List<ToolDefinition> tools) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (ToolDefinition tool : tools) {
            Map<String, Object> functionDef = new HashMap<>();
            functionDef.put("type", "function");
            Map<String, Object> function = new HashMap<>();
            function.put("name", tool.name());
            function.put("description", tool.description());
            function.put("parameters", tool.parametersSchema() != null ? tool.parametersSchema() : defaultParameters());
            functionDef.put("function", function);
            result.add(functionDef);
        }
        return result;
    }

    public ToolResult executeTool(String toolName, Map<String, Object> arguments, List<ToolDefinition> tools) {
        for (ToolDefinition tool : tools) {
            if (tool.name().equals(toolName)) {
                try {
                    return tool.executor().apply(arguments);
                } catch (Exception e) {
                    logger.error("工具执行失败: tool={}, error={}", toolName, e.getMessage(), e);
                    return ToolResult.failure(e.getMessage());
                }
            }
        }
        return ToolResult.failure("未找到工具: " + toolName);
    }

    private ToolDefinition buildKnowledgeBaseTool(String knowledgeBases) {
        Map<String, Object> params = new HashMap<>();
        params.put("type", "object");
        Map<String, Object> properties = new HashMap<>();
        Map<String, Object> queryProp = new HashMap<>();
        queryProp.put("type", "string");
        queryProp.put("description", "搜索查询文本");
        properties.put("query", queryProp);
        params.put("properties", properties);
        params.put("required", List.of("query"));

        return new ToolDefinition(
            "search_knowledge_base",
            "在知识库中搜索相关信息。当需要查找文档、资料或事实信息时使用此工具。",
            params,
            args -> {
                String query = String.valueOf(args.getOrDefault("query", ""));
                return knowledgeBaseTool.search(query, knowledgeBases);
            }
        );
    }

    private ToolDefinition buildMcpTool(String toolName) {
        Map<String, Object> params = new HashMap<>();
        params.put("type", "object");
        Map<String, Object> properties = new HashMap<>();
        Map<String, Object> queryProp = new HashMap<>();
        queryProp.put("type", "string");
        queryProp.put("description", "请求参数或查询内容");
        properties.put("query", queryProp);
        params.put("properties", properties);

        return new ToolDefinition(
            "mcp_" + sanitizeToolName(toolName),
            "调用外部工具: " + toolName,
            params,
            args -> mcpToolExecutor.execute(toolName, args)
        );
    }

    private Map<String, Object> defaultParameters() {
        Map<String, Object> params = new HashMap<>();
        params.put("type", "object");
        params.put("properties", new HashMap<>());
        return params;
    }

    private String sanitizeToolName(String name) {
        return name.replaceAll("[^a-zA-Z0-9_]", "_").toLowerCase();
    }

    private boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }
}

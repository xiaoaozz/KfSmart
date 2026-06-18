package com.smart.kf.agent.engine.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.kf.agent.engine.ToolResult;
import com.smart.kf.model.agent.McpToolConfig;
import com.smart.kf.repository.agent.McpToolConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class McpToolExecutor {

    private static final Logger logger = LoggerFactory.getLogger(McpToolExecutor.class);

    private final McpToolConfigRepository toolRepository;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public McpToolExecutor(McpToolConfigRepository toolRepository, WebClient.Builder webClientBuilder) {
        this.toolRepository = toolRepository;
        this.webClient = webClientBuilder.build();
        this.objectMapper = new ObjectMapper();
    }

    public ToolResult execute(String toolName, Map<String, Object> arguments) {
        McpToolConfig tool = findTool(toolName);
        if (tool == null) {
            return ToolResult.failure("未找到 MCP 工具: " + toolName);
        }
        if (tool.getEndpoint() == null || tool.getEndpoint().isBlank()) {
            return ToolResult.failure("MCP 工具 [" + tool.getName() + "] 的 Endpoint 未配置");
        }

        try {
            Map<String, Object> body = new HashMap<>();
            body.put("query", arguments.getOrDefault("query", ""));
            body.put("arguments", arguments);

            Object result = webClient.post()
                .uri(tool.getEndpoint().trim())
                .headers(headers -> applyAuth(headers, tool))
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Object.class)
                .block();

            tool.setCallCount(tool.getCallCount() + 1);
            toolRepository.save(tool);

            String output = objectMapper.writeValueAsString(result);
            logger.info("MCP 工具调用完成: tool={}", toolName);
            return ToolResult.success(output);
        } catch (Exception e) {
            logger.error("MCP 工具调用失败: tool={}, error={}", toolName, e.getMessage(), e);
            return ToolResult.failure("工具调用失败: " + e.getMessage());
        }
    }

    private McpToolConfig findTool(String toolName) {
        List<McpToolConfig> tools = toolRepository.findAll();
        return tools.stream()
            .filter(t -> t.getName().equals(toolName)
                || toolName.contains(t.getName())
                || t.getToolId().equals(toolName))
            .findFirst()
            .orElse(null);
    }

    private void applyAuth(org.springframework.http.HttpHeaders headers, McpToolConfig tool) {
        if (tool.getApiKey() == null || tool.getApiKey().isBlank()) return;
        String authType = tool.getAuthType() == null ? "" : tool.getAuthType().toLowerCase();
        if (authType.contains("api key")) {
            headers.set("X-API-Key", tool.getApiKey());
        } else if (authType.contains("bearer")) {
            headers.setBearerAuth(tool.getApiKey());
        }
    }
}

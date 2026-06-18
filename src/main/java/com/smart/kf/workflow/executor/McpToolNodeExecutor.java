package com.smart.kf.workflow.executor;

import com.smart.kf.model.agent.McpToolConfig;
import com.smart.kf.repository.agent.McpToolConfigRepository;
import com.smart.kf.workflow.engine.ExecutionContext;
import com.smart.kf.workflow.engine.NodeExecutionResult;
import com.smart.kf.workflow.engine.NodeExecutor;
import com.smart.kf.workflow.model.WorkflowNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class McpToolNodeExecutor implements NodeExecutor {

    private static final Logger logger = LoggerFactory.getLogger(McpToolNodeExecutor.class);

    private final McpToolConfigRepository toolRepository;
    private final WebClient webClient;

    public McpToolNodeExecutor(McpToolConfigRepository toolRepository, WebClient.Builder webClientBuilder) {
        this.toolRepository = toolRepository;
        this.webClient = webClientBuilder.build();
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
        if (tool == null) {
            throw new IllegalStateException("未找到 MCP 工具: " + toolId);
        }

        if (tool.getEndpoint() == null || tool.getEndpoint().isBlank()) {
            throw new IllegalStateException("MCP 工具 [" + tool.getName() + "] 的 Endpoint 未配置");
        }

        String endpoint = tool.getEndpoint().trim();
        if (!endpoint.startsWith("http://") && !endpoint.startsWith("https://")) {
            throw new IllegalStateException("MCP 工具 [" + tool.getName() + "] 的 Endpoint 格式无效");
        }

        // 构建请求体
        Map<String, Object> body = new HashMap<>();
        body.put("query", ctx.getOrDefault("query", ""));
        body.put("variables", ctx.getVariables());

        Object result;
        try {
            result = webClient.post()
                .uri(endpoint)
                .headers(headers -> applyAuth(headers, tool))
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Object.class)
                .block();
        } catch (Exception e) {
            String detail = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            throw new IllegalStateException("MCP 工具 [" + tool.getName() + "] 调用失败 (" + endpoint + "): " + detail, e);
        }

        tool.setCallCount(tool.getCallCount() + 1);
        toolRepository.save(tool);

        ctx.setVariable("toolResult", result != null ? result : "");

        Map<String, Object> outputs = new HashMap<>();
        outputs.put(outputField, result != null ? result : "");
        outputs.put("result", result != null ? result : "");
        return NodeExecutionResult.of(outputs, "调用MCP工具[" + tool.getName() + "]，endpoint=" + endpoint);
    }

    private McpToolConfig findTool(String toolId) {
        List<McpToolConfig> tools = toolRepository.findAll();
        return tools.stream()
            .filter(t -> t.getName().equals(toolId) || toolId.contains(t.getName()) || t.getToolId().equals(toolId))
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

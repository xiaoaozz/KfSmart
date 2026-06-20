package com.smart.kf.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.kf.model.agent.McpToolConfig;
import com.smart.kf.repository.agent.McpToolConfigRepository;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class McpToolInvocationService {

    private static final String MODE_MCP_JSON_RPC = "MCP_JSON_RPC";
    private static final String MODE_HTTP_COMPAT = "HTTP_COMPAT";

    private final McpToolConfigRepository toolRepository;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public McpToolInvocationService(
        McpToolConfigRepository toolRepository,
        WebClient.Builder webClientBuilder,
        ObjectMapper objectMapper
    ) {
        this.toolRepository = toolRepository;
        this.webClient = webClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Object execute(String toolRef, Map<String, Object> arguments) {
        McpToolConfig tool = resolveTool(toolRef);
        validateRunnable(tool);

        Object result = invoke(tool, normalizeArguments(arguments), true);
        tool.setCallCount(tool.getCallCount() + 1);
        toolRepository.save(tool);
        return result;
    }

    @Transactional
    public Map<String, Object> test(String toolId, Map<String, Object> arguments) {
        McpToolConfig tool = toolRepository.findByToolId(toolId)
            .orElseThrow(() -> new IllegalArgumentException("MCP 工具不存在"));

        try {
            validateRunnable(tool);
            Object result = invoke(tool, normalizeArguments(arguments), false);
            tool.setLastTestStatus("成功");
            tool.setLastTestMessage(compact(result));
            tool.setLastTestAt(LocalDateTime.now());
            toolRepository.save(tool);
            return Map.of(
                "success", true,
                "message", "连接测试成功",
                "result", result
            );
        } catch (Exception e) {
            String detail = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            tool.setLastTestStatus("失败");
            tool.setLastTestMessage(detail);
            tool.setLastTestAt(LocalDateTime.now());
            toolRepository.save(tool);
            return Map.of(
                "success", false,
                "message", detail
            );
        }
    }

    public McpToolConfig resolveTool(String toolRef) {
        if (toolRef == null || toolRef.isBlank()) {
            throw new IllegalArgumentException("MCP 工具未配置");
        }
        String ref = toolRef.trim();
        return toolRepository.findByToolId(ref)
            .or(() -> toolRepository.findByToolName(ref))
            .or(() -> toolRepository.findByName(ref))
            .orElseGet(() -> toolRepository.findAll().stream()
                .filter(t -> ref.equals(t.getToolId())
                    || ref.equals(t.getName())
                    || ref.equals(t.getToolName())
                    || (!isBlank(t.getName()) && ref.contains(t.getName())))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未找到 MCP 工具: " + toolRef)));
    }

    public Map<String, Object> parseSchema(String schemaJson) {
        if (isBlank(schemaJson)) {
            return defaultSchema();
        }
        try {
            return objectMapper.readValue(schemaJson, new TypeReference<>() {});
        } catch (Exception e) {
            return defaultSchema();
        }
    }

    public String normalizeToolName(McpToolConfig tool) {
        if (!isBlank(tool.getToolName())) {
            return tool.getToolName().trim();
        }
        if (!isBlank(tool.getName())) {
            return sanitizeToolName(tool.getName());
        }
        return tool.getToolId();
    }

    private Object invoke(McpToolConfig tool, Map<String, Object> arguments, boolean toolCall) {
        String endpoint = tool.getEndpoint().trim();
        String mode = isBlank(tool.getRequestMode()) ? MODE_MCP_JSON_RPC : tool.getRequestMode();
        Map<String, Object> body = MODE_HTTP_COMPAT.equals(mode)
            ? buildHttpCompatBody(arguments)
            : buildMcpJsonRpcBody(tool, arguments, toolCall);

        Object response = webClient.post()
            .uri(endpoint)
            .headers(headers -> applyHeaders(headers, tool))
            .bodyValue(body)
            .retrieve()
            .bodyToMono(Object.class)
            .block();

        if (MODE_MCP_JSON_RPC.equals(mode)) {
            return unwrapJsonRpcResponse(response);
        }
        return response;
    }

    private Map<String, Object> buildMcpJsonRpcBody(McpToolConfig tool, Map<String, Object> arguments, boolean toolCall) {
        Map<String, Object> body = new HashMap<>();
        body.put("jsonrpc", "2.0");
        body.put("id", UUID.randomUUID().toString());
        if (toolCall) {
            body.put("method", "tools/call");
            body.put("params", Map.of(
                "name", normalizeToolName(tool),
                "arguments", arguments
            ));
        } else {
            body.put("method", "tools/list");
            body.put("params", Map.of());
        }
        return body;
    }

    private Map<String, Object> buildHttpCompatBody(Map<String, Object> arguments) {
        Map<String, Object> body = new HashMap<>();
        body.put("query", arguments.getOrDefault("query", ""));
        body.put("arguments", arguments);
        return body;
    }

    private Object unwrapJsonRpcResponse(Object response) {
        if (!(response instanceof Map<?, ?> map)) {
            return response;
        }
        if (map.containsKey("error")) {
            throw new IllegalStateException("MCP 返回错误: " + compact(map.get("error")));
        }
        Object result = map.get("result");
        if (result instanceof Map<?, ?> resultMap && resultMap.containsKey("content")) {
            return resultMap;
        }
        return result != null ? result : response;
    }

    private void applyHeaders(HttpHeaders headers, McpToolConfig tool) {
        headers.set("Accept", "application/json");
        headers.set("Content-Type", "application/json");
        if (!isBlank(tool.getProtocolVersion())) {
            headers.set("MCP-Protocol-Version", tool.getProtocolVersion().trim());
        }
        if (isBlank(tool.getApiKey())) {
            return;
        }
        String authType = tool.getAuthType() == null ? "" : tool.getAuthType().toLowerCase();
        if (authType.contains("bearer")) {
            headers.setBearerAuth(tool.getApiKey());
        } else if (authType.contains("api key")) {
            String headerName = isBlank(tool.getAuthHeaderName()) ? "X-API-Key" : tool.getAuthHeaderName().trim();
            headers.set(headerName, tool.getApiKey());
        }
    }

    private void validateRunnable(McpToolConfig tool) {
        if (!"在线".equals(tool.getStatus())) {
            throw new IllegalStateException("MCP 工具 [" + tool.getName() + "] 已停用");
        }
        if (isBlank(tool.getEndpoint())) {
            throw new IllegalStateException("MCP 工具 [" + tool.getName() + "] 的 Endpoint 未配置");
        }
        URI uri = URI.create(tool.getEndpoint().trim());
        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            throw new IllegalStateException("MCP 工具 [" + tool.getName() + "] 的 Endpoint 格式无效");
        }
    }

    private Map<String, Object> normalizeArguments(Map<String, Object> arguments) {
        return arguments == null ? new HashMap<>() : new HashMap<>(arguments);
    }

    private Map<String, Object> defaultSchema() {
        Map<String, Object> queryProp = new HashMap<>();
        queryProp.put("type", "string");
        queryProp.put("description", "请求参数或查询内容");
        Map<String, Object> properties = new HashMap<>();
        properties.put("query", queryProp);
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        return schema;
    }

    private String sanitizeToolName(String value) {
        String sanitized = value.toLowerCase()
            .replaceAll("[^a-z0-9_\\u4e00-\\u9fa5-]", "_")
            .replaceAll("_+", "_")
            .replaceAll("^_|_$", "");
        return sanitized.isBlank() ? "mcp_tool" : sanitized;
    }

    private String compact(Object value) {
        if (value == null) {
            return "";
        }
        try {
            String text = value instanceof String ? (String) value : objectMapper.writeValueAsString(value);
            return text.length() > 900 ? text.substring(0, 900) + "..." : text;
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

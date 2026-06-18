package com.smart.kf.workflow.executor;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.kf.workflow.engine.ExecutionContext;
import com.smart.kf.workflow.engine.NodeExecutionResult;
import com.smart.kf.workflow.engine.NodeExecutor;
import com.smart.kf.workflow.model.WorkflowNode;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashMap;
import java.util.Map;

@Component
public class HttpNodeExecutor implements NodeExecutor {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public HttpNodeExecutor(WebClient.Builder webClientBuilder, ObjectMapper objectMapper) {
        this.webClient = webClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    @Override
    public String getNodeType() {
        return "HTTP请求";
    }

    @Override
    public NodeExecutionResult execute(WorkflowNode node, ExecutionContext ctx) {
        String url = ctx.resolveTemplate(node.configString("url"));
        String method = node.configString("method");
        String bodyStr = node.configString("body");

        if (url == null || url.isBlank()) {
            throw new IllegalStateException("HTTP 请求节点未配置 URL");
        }

        HttpMethod httpMethod = HttpMethod.valueOf(method != null ? method.toUpperCase() : "GET");

        WebClient.RequestBodySpec request = webClient.method(httpMethod).uri(url);

        // 解析请求头
        String headersJson = node.configString("headers");
        if (headersJson != null && !headersJson.isBlank()) {
            try {
                Map<String, String> headers = objectMapper.readValue(headersJson, new TypeReference<>() {});
                headers.forEach(request::header);
            } catch (Exception ignored) {}
        }

        // 请求体
        if (bodyStr != null && !bodyStr.isBlank()) {
            String resolvedBody = ctx.resolveTemplate(bodyStr);
            request.bodyValue(resolvedBody);
        }

        Object result = request.retrieve()
            .bodyToMono(Object.class)
            .block();

        ctx.setVariableIfAbsent("toolResult", result != null ? result : "");

        Map<String, Object> outputs = new HashMap<>();
        outputs.put("response", result != null ? result : "");
        outputs.put("result", result != null ? result : "");
        return NodeExecutionResult.of(outputs, "发送" + httpMethod.name() + "请求到 " + url);
    }
}

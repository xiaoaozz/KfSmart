package com.smart.kf.client.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.kf.client.ModelClient.FunctionCallResult;
import com.smart.kf.client.ModelClient.ToolCall;
import com.smart.kf.client.TokenCost;
import com.smart.kf.config.AiProperties;
import com.smart.kf.model.ApiKeyConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Anthropic Messages API 协议实现。
 * 鉴权使用 {@code x-api-key: {key}} + {@code anthropic-version} 头；
 * system 消息通过顶层 {@code system} 字段传递，{@code max_tokens} 为必填。
 */
@Component
public class AnthropicProtocolHandler implements ModelProtocolHandler {

    private static final Logger logger = LoggerFactory.getLogger(AnthropicProtocolHandler.class);

    @Override
    public String authType() {
        return "anthropic";
    }

    @Override
    public void applyAuthHeaders(WebClient.Builder builder, String apiKey) {
        if (apiKey != null && !apiKey.trim().isEmpty()) {
            builder.defaultHeader("x-api-key", apiKey);
            builder.defaultHeader("anthropic-version", "2023-06-01");
        }
    }

    @Override
    public String endpointSuffix() {
        return "/messages";
    }

    @Override
    public Map<String, Object> buildRequestBody(List<Map<String, String>> messages,
                                                 String model,
                                                 ApiKeyConfig apiKeyConfig,
                                                 AiProperties aiProperties,
                                                 List<Map<String, Object>> tools) {
        Map<String, Object> request = new HashMap<>();
        request.put("model", model);
        request.put("stream", true);

        // 生成参数
        if (apiKeyConfig != null) {
            if (apiKeyConfig.getTemperature() != null) {
                request.put("temperature", apiKeyConfig.getTemperature());
            }
            if (apiKeyConfig.getTopP() != null) {
                request.put("top_p", apiKeyConfig.getTopP());
            }
            // Anthropic max_tokens 是必填字段
            int maxTokens = apiKeyConfig.getMaxTokens() != null ? apiKeyConfig.getMaxTokens() : 2000;
            request.put("max_tokens", maxTokens);
        } else {
            request.put("max_tokens", 2000);
        }

        // 将 system 消息单独提取到顶层（Anthropic 要求顶层 system 字段）
        List<Map<String, String>> userMessages = new ArrayList<>();
        for (Map<String, String> msg : messages) {
            if ("system".equals(msg.get("role"))) {
                request.put("system", msg.get("content"));
            } else {
                userMessages.add(msg);
            }
        }
        request.put("messages", userMessages);

        if (tools != null && !tools.isEmpty()) {
            request.put("tools", convertToAnthropicTools(tools));
            request.put("tool_choice", Map.of("type", "auto"));
        }
        return request;
    }

    private List<Map<String, Object>> convertToAnthropicTools(List<Map<String, Object>> openAiTools) {
        List<Map<String, Object>> anthropicTools = new ArrayList<>();
        for (Map<String, Object> tool : openAiTools) {
            Object functionObj = tool.get("function");
            if (functionObj instanceof Map<?, ?> function) {
                Map<String, Object> at = new HashMap<>();
                at.put("name", function.get("name"));
                at.put("description", function.get("description"));
                at.put("input_schema", function.get("parameters"));
                anthropicTools.add(at);
            }
        }
        return anthropicTools;
    }

    @Override
    public String parseText(String response, ObjectMapper objectMapper) {
        try {
            JsonNode node = objectMapper.readTree(response);
            StringBuilder builder = new StringBuilder();
            for (JsonNode item : node.path("content")) {
                String text = item.path("text").asText("");
                if (!text.isEmpty()) {
                    builder.append(text);
                }
            }
            return builder.toString();
        } catch (Exception e) {
            logger.error("解析 Anthropic 非流式响应失败: {}", e.getMessage(), e);
            throw new RuntimeException("解析模型响应失败");
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public FunctionCallResult parseFunctionCall(String response, ObjectMapper objectMapper) {
        try {
            JsonNode node = objectMapper.readTree(response);
            StringBuilder content = new StringBuilder();
            List<ToolCall> toolCalls = new ArrayList<>();
            String stopReason = node.path("stop_reason").asText("");

            for (JsonNode block : node.path("content")) {
                String type = block.path("type").asText("");
                if ("text".equals(type)) {
                    content.append(block.path("text").asText(""));
                } else if ("tool_use".equals(type)) {
                    String tcId = block.path("id").asText("");
                    String name = block.path("name").asText("");
                    Map<String, Object> args;
                    try {
                        args = objectMapper.convertValue(block.path("input"), Map.class);
                    } catch (Exception e) {
                        args = new HashMap<>();
                    }
                    toolCalls.add(new ToolCall(tcId, name, args));
                }
            }

            JsonNode usage = node.path("usage");
            int inputTokens = usage.path("input_tokens").asInt(0);
            int outputTokens = usage.path("output_tokens").asInt(0);
            double modelCost = TokenCost.estimate(inputTokens, outputTokens);

            return new FunctionCallResult(content.toString(), toolCalls, stopReason, inputTokens, outputTokens, modelCost);
        } catch (Exception e) {
            logger.error("解析 Anthropic Function Call 响应失败: {}", e.getMessage(), e);
            throw new RuntimeException("解析模型响应失败");
        }
    }

    @Override
    public Disposable stream(WebClient client,
                             String uri,
                             Map<String, Object> requestBody,
                             Consumer<String> onChunk,
                             Consumer<Throwable> onError,
                             Runnable onComplete,
                             ObjectMapper objectMapper) {
        return client.post()
                .uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .onStatus(
                        HttpStatusCode::isError,
                        clientResponse -> clientResponse.bodyToMono(String.class)
                                .flatMap(errorBody -> {
                                    logger.error("Anthropic 服务返回错误，HTTP状态码: {}，响应体: {}",
                                            clientResponse.statusCode(), errorBody);
                                    String msg = "模型服务暂时不可用";
                                    try {
                                        JsonNode errNode = objectMapper.readTree(errorBody);
                                        if (errNode.has("error") && errNode.get("error").has("message")) {
                                            msg = errNode.get("error").get("message").asText(msg);
                                        }
                                    } catch (Exception e) {
                                        logger.warn("无法解析 Anthropic 错误响应体: {}", errorBody);
                                    }
                                    return Mono.error(new RuntimeException(msg));
                                })
                )
                .bodyToFlux(String.class)
                .subscribe(
                        chunk -> processAnthropicChunk(chunk, onChunk, onError, objectMapper),
                        onError,
                        onComplete
                );
    }

    /**
     * 处理 Anthropic SSE 流式响应的数据块。
     * 事件格式：data: {"type":"content_block_delta","delta":{"type":"text_delta","text":"..."}}
     */
    private void processAnthropicChunk(String chunk, Consumer<String> onChunk, Consumer<Throwable> onError, ObjectMapper objectMapper) {
        try {
            if (chunk == null || chunk.isBlank()) return;
            JsonNode node = objectMapper.readTree(chunk);
            String type = node.path("type").asText("");
            if ("content_block_delta".equals(type)) {
                String text = node.path("delta").path("text").asText("");
                if (!text.isEmpty()) {
                    onChunk.accept(text);
                }
            } else if ("error".equals(type)) {
                String msg = node.path("error").path("message").asText("Anthropic 服务错误");
                logger.error("Anthropic 返回错误事件: {}", msg);
                onError.accept(new RuntimeException(msg));
            }
        } catch (Exception e) {
            logger.error("处理 Anthropic 数据块时出错: {}", e.getMessage(), e);
        }
    }
}
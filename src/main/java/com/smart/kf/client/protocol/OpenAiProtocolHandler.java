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
import org.springframework.http.HttpHeaders;
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
 * OpenAI Chat Completions 协议实现（兼容所有 OpenAI 协议的服务：DeepSeek、智谱、Ollama 等）。
 * 鉴权使用 {@code Authorization: Bearer {key}}。
 */
@Component
public class OpenAiProtocolHandler implements ModelProtocolHandler {

    private static final Logger logger = LoggerFactory.getLogger(OpenAiProtocolHandler.class);

    @Override
    public String authType() {
        return "bearer";
    }

    @Override
    public void applyAuthHeaders(WebClient.Builder builder, String apiKey) {
        if (apiKey != null && !apiKey.trim().isEmpty()) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey);
        }
    }

    @Override
    public String endpointSuffix() {
        return "/chat/completions";
    }

    @Override
    public Map<String, Object> buildRequestBody(List<Map<String, String>> messages,
                                                 String model,
                                                 ApiKeyConfig apiKeyConfig,
                                                 AiProperties aiProperties,
                                                 List<Map<String, Object>> tools) {
        Map<String, Object> request = new HashMap<>();
        request.put("model", model);
        request.put("messages", messages);
        request.put("stream", true);

        // 优先使用数据库配置的生成参数；否则使用 YAML 配置
        if (apiKeyConfig != null) {
            if (apiKeyConfig.getTemperature() != null) {
                request.put("temperature", apiKeyConfig.getTemperature());
            }
            if (apiKeyConfig.getTopP() != null) {
                request.put("top_p", apiKeyConfig.getTopP());
            }
            if (apiKeyConfig.getMaxTokens() != null) {
                request.put("max_tokens", apiKeyConfig.getMaxTokens());
            }
        } else {
            AiProperties.Generation gen = aiProperties.getGeneration();
            if (gen.getTemperature() != null) {
                request.put("temperature", gen.getTemperature());
            }
            if (gen.getTopP() != null) {
                request.put("top_p", gen.getTopP());
            }
            if (gen.getMaxTokens() != null) {
                request.put("max_tokens", gen.getMaxTokens());
            }
        }

        if (tools != null && !tools.isEmpty()) {
            request.put("tools", tools);
            request.put("tool_choice", "auto");
        }
        return request;
    }

    @Override
    public String parseText(String response, ObjectMapper objectMapper) {
        try {
            JsonNode node = objectMapper.readTree(response);
            return node.path("choices").path(0).path("message").path("content").asText("");
        } catch (Exception e) {
            logger.error("解析非流式模型响应失败: {}", e.getMessage(), e);
            throw new RuntimeException("解析模型响应失败");
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public FunctionCallResult parseFunctionCall(String response, ObjectMapper objectMapper) {
        try {
            JsonNode node = objectMapper.readTree(response);
            JsonNode choice = node.path("choices").path(0);
            JsonNode message = choice.path("message");
            String content = message.path("content").asText("");
            String finishReason = choice.path("finish_reason").asText("");

            List<ToolCall> toolCalls = new ArrayList<>();
            JsonNode toolCallsNode = message.path("tool_calls");
            if (toolCallsNode.isArray()) {
                for (JsonNode tc : toolCallsNode) {
                    String tcId = tc.path("id").asText("");
                    JsonNode function = tc.path("function");
                    String name = function.path("name").asText("");
                    String argsStr = function.path("arguments").asText("{}");
                    Map<String, Object> args;
                    try {
                        args = objectMapper.readValue(argsStr, Map.class);
                    } catch (Exception e) {
                        args = new HashMap<>();
                    }
                    toolCalls.add(new ToolCall(tcId, name, args));
                }
            }

            JsonNode usage = node.path("usage");
            int promptTokens = usage.path("prompt_tokens").asInt(0);
            int completionTokens = usage.path("completion_tokens").asInt(0);
            double modelCost = TokenCost.estimate(promptTokens, completionTokens);

            return new FunctionCallResult(content, toolCalls, finishReason, promptTokens, completionTokens, modelCost);
        } catch (Exception e) {
            logger.error("解析 OpenAI Function Call 响应失败: {}", e.getMessage(), e);
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
                                    logger.error("模型服务返回错误，HTTP状态码: {}，响应体: {}",
                                            clientResponse.statusCode(), errorBody);
                                    String msg = "模型服务暂时不可用";
                                    try {
                                        JsonNode errNode = objectMapper.readTree(errorBody);
                                        if (errNode.has("msg")) {
                                            msg = errNode.get("msg").asText(msg);
                                        } else if (errNode.has("message")) {
                                            msg = errNode.get("message").asText(msg);
                                        } else if (errNode.has("error")) {
                                            JsonNode errorField = errNode.get("error");
                                            if (errorField.isObject()) {
                                                if (errorField.has("message")) {
                                                    msg = errorField.get("message").asText(msg);
                                                } else if (errorField.has("msg")) {
                                                    msg = errorField.get("msg").asText(msg);
                                                }
                                            } else {
                                                msg = errorField.asText(msg);
                                            }
                                        }
                                    } catch (Exception e) {
                                        logger.warn("无法解析模型服务错误响应体: {}", errorBody);
                                    }
                                    return Mono.error(new RuntimeException(msg));
                                })
                )
                .bodyToFlux(String.class)
                .subscribe(
                        chunk -> processChunk(chunk, onChunk, onError, objectMapper),
                        onError,
                        onComplete
                );
    }

    private void processChunk(String chunk, Consumer<String> onChunk, Consumer<Throwable> onError, ObjectMapper objectMapper) {
        try {
            if ("[DONE]".equals(chunk)) {
                logger.debug("对话结束");
                return;
            }
            JsonNode node = objectMapper.readTree(chunk);

            // 检查是否为错误响应（非流式格式，如智谱AI返回的JSON错误）
            if (node.has("code") && node.has("msg")) {
                int code = node.get("code").asInt(0);
                String msg = node.get("msg").asText("模型服务错误");
                logger.error("模型服务返回业务错误: code={}, msg={}", code, msg);
                onError.accept(new RuntimeException(msg));
                return;
            }

            String content = node.path("choices")
                    .path(0)
                    .path("delta")
                    .path("content")
                    .asText("");
            if (!content.isEmpty()) {
                onChunk.accept(content);
            }
        } catch (Exception e) {
            logger.error("处理数据块时出错: {}", e.getMessage(), e);
        }
    }
}

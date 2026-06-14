package com.smart.kf.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.kf.config.AiProperties;
import com.smart.kf.model.ApiKeyConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 通用 AI 模型对话客户端，兼容所有遵循 OpenAI Chat Completions 协议的模型服务。
 * <p>
 * 支持两种配置来源（优先级从高到低）：
 * <ol>
 *   <li>通过 {@link ApiKeyConfig} 动态指定（数据库管理的 API Key 配置）</li>
 *   <li>回退到 YAML 文件中的默认配置（{@code ai.default.*}）</li>
 * </ol>
 */
@Service
public class ModelClient {

    private static final Logger logger = LoggerFactory.getLogger(ModelClient.class);

    /** 基于 YAML 配置构建的默认 WebClient */
    private final WebClient defaultWebClient;
    /** YAML 配置中的默认模型名称 */
    private final String defaultModel;
    /** 全局 AI 属性（Prompt 模板 + 生成参数） */
    private final AiProperties aiProperties;

    public ModelClient(@Value("${ai.default.url}") String apiUrl,
                       @Value("${ai.default.key:}") String apiKey,
                       @Value("${ai.default.model}") String model,
                       AiProperties aiProperties) {
        WebClient.Builder builder = WebClient.builder().baseUrl(apiUrl);
        if (apiKey != null && !apiKey.trim().isEmpty()) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey);
        }
        this.defaultWebClient = builder.build();
        this.defaultModel = model;
        this.aiProperties = aiProperties;
    }

    /**
     * 使用默认 YAML 配置发起流式对话请求。
     */
    public Disposable streamResponse(String userMessage,
                                     String context,
                                     List<Map<String, String>> history,
                                     Consumer<String> onChunk,
                                     Consumer<Throwable> onError,
                                     Runnable onComplete) {
        return streamResponse(userMessage, context, history, null, onChunk, onError, onComplete);
    }

    /**
     * 使用指定的 {@link ApiKeyConfig} 发起流式对话请求；若 apiKeyConfig 为 {@code null}，则回退到 YAML 默认配置。
     */
    public Disposable streamResponse(String userMessage,
                                     String context,
                                     List<Map<String, String>> history,
                                     ApiKeyConfig apiKeyConfig,
                                     Consumer<String> onChunk,
                                     Consumer<Throwable> onError,
                                     Runnable onComplete) {
        WebClient client;
        String model;

        if (apiKeyConfig != null) {
            logger.info("使用数据库 API Key 配置发起请求，id={}, name={}, provider={}, model={}, authType={}",
                    apiKeyConfig.getId(), apiKeyConfig.getName(), apiKeyConfig.getProvider(),
                    apiKeyConfig.getModelName(), apiKeyConfig.getAuthType());
            client = buildWebClient(apiKeyConfig.getApiKey(), apiKeyConfig.getAuthType());
            model = apiKeyConfig.getModelName();
        } else {
            logger.info("使用 YAML 默认配置发起请求，model={}", defaultModel);
            client = defaultWebClient;
            model = defaultModel;
        }

        Map<String, Object> requestBody = buildRequest(userMessage, context, history, model, apiKeyConfig);

        // Anthropic API 使用不同的端点和请求格式
        String authType = apiKeyConfig != null && apiKeyConfig.getAuthType() != null
                ? apiKeyConfig.getAuthType().toLowerCase()
                : "bearer";

        // 解析请求 URI：兼容「仅填写 baseUrl」和「填写完整 endpoint URL」两种情况
        String resolvedUri = resolveRequestUri(apiKeyConfig, authType);
        logger.info("实际请求 URI: {}", resolvedUri);

        if ("anthropic".equals(authType)) {
            return client.post()
                    .uri(resolvedUri)
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
                                            JsonNode errNode = new ObjectMapper().readTree(errorBody);
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
                            chunk -> processAnthropicChunk(chunk, onChunk, onError),
                            onError,
                            onComplete
                    );
        }

        return client.post()
                .uri(resolvedUri)
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
                                        JsonNode errNode = new ObjectMapper().readTree(errorBody);
                                        if (errNode.has("msg")) {
                                            msg = errNode.get("msg").asText(msg);
                                        } else if (errNode.has("message")) {
                                            msg = errNode.get("message").asText(msg);
                                        } else if (errNode.has("error")) {
                                            msg = errNode.get("error").asText(msg);
                                        }
                                    } catch (Exception e) {
                                        logger.warn("无法解析模型服务错误响应体: {}", errorBody);
                                    }
                                    return Mono.error(new RuntimeException(msg));
                                })
                )
                .bodyToFlux(String.class)
                .subscribe(
                        chunk -> processChunk(chunk, onChunk, onError),
                        onError,
                        onComplete
                );
    }

    /**
     * 非流式对话接口，支持传入 Agent 自定义 systemPrompt，供工作流引擎等同步执行场景使用。
     * 当 systemPrompt 不为空时，替换全局 AiProperties 配置作为 system 消息。
     */
    public String chat(String userMessage,
                       String context,
                       List<Map<String, String>> history,
                       ApiKeyConfig apiKeyConfig,
                       String systemPrompt) {
        WebClient client;
        String model;

        if (apiKeyConfig != null) {
            client = buildWebClient(apiKeyConfig.getApiKey(), apiKeyConfig.getAuthType());
            model = apiKeyConfig.getModelName();
        } else {
            client = defaultWebClient;
            model = defaultModel;
        }

        String authType = apiKeyConfig != null && apiKeyConfig.getAuthType() != null
                ? apiKeyConfig.getAuthType().toLowerCase()
                : "bearer";
        Map<String, Object> requestBody = buildRequest(userMessage, context, history, model, apiKeyConfig, systemPrompt);
        requestBody.put("stream", false);
        String resolvedUri = resolveRequestUri(apiKeyConfig, authType);

        String response = client.post()
                .uri(resolvedUri)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .onStatus(
                        HttpStatusCode::isError,
                        clientResponse -> clientResponse.bodyToMono(String.class)
                                .flatMap(errorBody -> Mono.error(new RuntimeException(parseErrorMessage(errorBody))))
                )
                .bodyToMono(String.class)
                .block();

        return "anthropic".equals(authType) ? parseAnthropicText(response) : parseOpenAiText(response);
    }

    /**
     * 非流式对话接口（不带自定义 systemPrompt），供工作流引擎等同步执行场景使用。
     */
    public String chat(String userMessage,
                       String context,
                       List<Map<String, String>> history,
                       ApiKeyConfig apiKeyConfig) {
        WebClient client;
        String model;

        if (apiKeyConfig != null) {
            client = buildWebClient(apiKeyConfig.getApiKey(), apiKeyConfig.getAuthType());
            model = apiKeyConfig.getModelName();
        } else {
            client = defaultWebClient;
            model = defaultModel;
        }

        String authType = apiKeyConfig != null && apiKeyConfig.getAuthType() != null
                ? apiKeyConfig.getAuthType().toLowerCase()
                : "bearer";
        Map<String, Object> requestBody = buildRequest(userMessage, context, history, model, apiKeyConfig);
        requestBody.put("stream", false);
        String resolvedUri = resolveRequestUri(apiKeyConfig, authType);

        String response = client.post()
                .uri(resolvedUri)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .onStatus(
                        HttpStatusCode::isError,
                        clientResponse -> clientResponse.bodyToMono(String.class)
                                .flatMap(errorBody -> Mono.error(new RuntimeException(parseErrorMessage(errorBody))))
                )
                .bodyToMono(String.class)
                .block();

        return "anthropic".equals(authType) ? parseAnthropicText(response) : parseOpenAiText(response);
    }

    // ───────────────────────── 私有方法 ─────────────────────────

    /**
     * 根据 API Key 和认证方式构建 {@link WebClient}（不设 baseUrl，使用完整 URI 发请求）。
     *
     * <ul>
     *   <li>{@code bearer / openai} — {@code Authorization: Bearer {key}}</li>
     *   <li>{@code anthropic}       — {@code x-api-key: {key}} + {@code anthropic-version} 头</li>
     * </ul>
     */
    private WebClient buildWebClient(String apiKey, String authType) {
        WebClient.Builder builder = WebClient.builder();
        if (apiKey != null && !apiKey.trim().isEmpty()) {
            String type = authType != null ? authType.toLowerCase() : "bearer";
            if ("anthropic".equals(type)) {
                builder.defaultHeader("x-api-key", apiKey);
                builder.defaultHeader("anthropic-version", "2023-06-01");
            } else {
                // bearer / openai / 其他均使用 Bearer Token
                builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey);
            }
        }
        return builder.build();
    }

    /**
     * 解析最终请求的完整 URI。
     * <p>
     * 兼容两种填写习惯：
     * <ul>
     *   <li>填写了完整 endpoint（如 {@code https://open.bigmodel.cn/api/paas/v4/chat/completions}）
     *       → 直接使用，不追加路径</li>
     *   <li>填写了 baseUrl（如 {@code https://api.deepseek.com}）
     *       → 追加标准端点路径（{@code /chat/completions} 或 {@code /messages}）</li>
     * </ul>
     */
    private String resolveRequestUri(ApiKeyConfig apiKeyConfig, String authType) {
        if (apiKeyConfig == null) {
            // 默认 YAML 配置走 /chat/completions（相对路径，配合 defaultWebClient 的 baseUrl 使用）
            return "/chat/completions";
        }
        String apiUrl = apiKeyConfig.getApiUrl();
        if (apiUrl == null || apiUrl.isBlank()) {
            return "/chat/completions";
        }
        String url = apiUrl.trim();
        // 判断是否已经包含了端点路径，避免重复追加
        if (url.contains("/chat/completions") || url.contains("/messages")) {
            return url;
        }
        // baseUrl 场景：拼接标准端点路径
        String suffix = "anthropic".equals(authType) ? "/messages" : "/chat/completions";
        return url.endsWith("/") ? url + suffix.substring(1) : url + suffix;
    }

    private Map<String, Object> buildRequest(String userMessage,
                                             String context,
                                             List<Map<String, String>> history,
                                             String model,
                                             ApiKeyConfig apiKeyConfig) {
        return buildRequest(userMessage, context, history, model, apiKeyConfig, null);
    }

    private Map<String, Object> buildRequest(String userMessage,
                                             String context,
                                             List<Map<String, String>> history,
                                             String model,
                                             ApiKeyConfig apiKeyConfig,
                                             String systemPrompt) {
        logger.info("构建请求，model={}，用户消息长度={}，上下文长度={}，历史消息数={}",
                model,
                userMessage != null ? userMessage.length() : 0,
                context != null ? context.length() : 0,
                history != null ? history.size() : 0);

        String authType = apiKeyConfig != null && apiKeyConfig.getAuthType() != null
                ? apiKeyConfig.getAuthType().toLowerCase()
                : "bearer";

        if ("anthropic".equals(authType)) {
            return buildAnthropicRequest(userMessage, context, history, model, apiKeyConfig, systemPrompt);
        }

        Map<String, Object> request = new HashMap<>();
        request.put("model", model);
        request.put("messages", buildMessages(userMessage, context, history, systemPrompt));
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
        return request;
    }

    /**
     * 构建 Anthropic Messages API 格式的请求体。
     * Anthropic 的 system 消息通过顶层 "system" 字段传递，而非 messages 数组。
     */
    private Map<String, Object> buildAnthropicRequest(String userMessage,
                                                       String context,
                                                       List<Map<String, String>> history,
                                                       String model,
                                                       ApiKeyConfig apiKeyConfig,
                                                       String systemPrompt) {
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

        // 将 system 消息单独提取出来（Anthropic 要求顶层 system 字段）
        List<Map<String, String>> allMessages = buildMessages(userMessage, context, history, systemPrompt);
        List<Map<String, String>> userMessages = new ArrayList<>();
        for (Map<String, String> msg : allMessages) {
            if ("system".equals(msg.get("role"))) {
                request.put("system", msg.get("content"));
            } else {
                userMessages.add(msg);
            }
        }
        request.put("messages", userMessages);
        return request;
    }

    private List<Map<String, String>> buildMessages(String userMessage,
                                                    String context,
                                                    List<Map<String, String>> history,
                                                    String systemPrompt) {
        List<Map<String, String>> messages = new ArrayList<>();

        String systemContent;
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            // Agent 自定义 System Prompt 模式：直接使用，不叠加知识库 RAG 模板
            // 若有知识库检索上下文，追加到 system 末尾供模型参考
            if (context != null && !context.isBlank()) {
                systemContent = systemPrompt + "\n\n以下是检索到的参考资料：\n" + context;
            } else {
                systemContent = systemPrompt;
            }
        } else {
            // 知识库 RAG 模式：使用全局 AiProperties 模板构建 system
            AiProperties.Prompt promptCfg = aiProperties.getPrompt();
            StringBuilder sysBuilder = new StringBuilder();
            String rules = promptCfg.getRules();
            if (rules != null) {
                sysBuilder.append(rules).append("\n\n");
            }
            String refStart = promptCfg.getRefStart() != null ? promptCfg.getRefStart() : "<<REF>>";
            String refEnd   = promptCfg.getRefEnd()   != null ? promptCfg.getRefEnd()   : "<<END>>";
            sysBuilder.append(refStart).append("\n");
            if (context != null && !context.isBlank()) {
                sysBuilder.append(context);
            } else {
                String noResult = promptCfg.getNoResultText() != null ? promptCfg.getNoResultText() : "（本轮无检索结果）";
                sysBuilder.append(noResult).append("\n");
            }
            sysBuilder.append(refEnd);
            systemContent = sysBuilder.toString();
        }

        messages.add(Map.of("role", "system", "content", systemContent));
        logger.debug("添加了系统消息，长度: {}", systemContent.length());

        // 追加历史消息（若有）
        if (history != null && !history.isEmpty()) {
            messages.addAll(history);
        }

        // 当前用户问题
        messages.add(Map.of("role", "user", "content", userMessage));

        return messages;
    }

    private void processChunk(String chunk, Consumer<String> onChunk, Consumer<Throwable> onError) {
        try {
            if ("[DONE]".equals(chunk)) {
                logger.debug("对话结束");
                return;
            }
            ObjectMapper mapper = new ObjectMapper();
            JsonNode node = mapper.readTree(chunk);

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

    /**
     * 处理 Anthropic SSE 流式响应的数据块。
     * 事件格式：data: {"type":"content_block_delta","delta":{"type":"text_delta","text":"..."}}
     */
    private void processAnthropicChunk(String chunk, Consumer<String> onChunk, Consumer<Throwable> onError) {
        try {
            if (chunk == null || chunk.isBlank()) return;
            ObjectMapper mapper = new ObjectMapper();
            JsonNode node = mapper.readTree(chunk);
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

    private String parseOpenAiText(String response) {
        try {
            JsonNode node = new ObjectMapper().readTree(response);
            return node.path("choices").path(0).path("message").path("content").asText("");
        } catch (Exception e) {
            logger.error("解析非流式模型响应失败: {}", e.getMessage(), e);
            throw new RuntimeException("解析模型响应失败");
        }
    }

    private String parseAnthropicText(String response) {
        try {
            JsonNode node = new ObjectMapper().readTree(response);
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

    private String parseErrorMessage(String errorBody) {
        String msg = "模型服务暂时不可用";
        try {
            JsonNode errNode = new ObjectMapper().readTree(errorBody);
            if (errNode.has("msg")) {
                msg = errNode.get("msg").asText(msg);
            } else if (errNode.has("message")) {
                msg = errNode.get("message").asText(msg);
            } else if (errNode.has("error")) {
                JsonNode error = errNode.get("error");
                msg = error.has("message") ? error.get("message").asText(msg) : error.asText(msg);
            }
        } catch (Exception e) {
            logger.warn("无法解析模型服务错误响应体: {}", errorBody);
        }
        return msg;
    }
}

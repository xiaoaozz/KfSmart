package com.smart.kf.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.kf.client.protocol.AnthropicProtocolHandler;
import com.smart.kf.client.protocol.ModelProtocolHandler;
import com.smart.kf.client.protocol.OpenAiProtocolHandler;
import com.smart.kf.config.AiProperties;
import com.smart.kf.model.ApiKeyConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 通用 AI 模型对话客户端，兼容所有遵循 OpenAI Chat Completions 协议的模型服务，
 * 并支持 Anthropic Messages API。
 * <p>
 * 支持两种配置来源（优先级从高到低）：
 * <ol>
 *   <li>通过 {@link ApiKeyConfig} 动态指定（数据库管理的 API Key 配置）</li>
 *   <li>回退到 YAML 文件中的默认配置（{@code ai.default.*}）</li>
 * </ol>
 *
 * <p>协议差异（OpenAI / Anthropic）下沉到 {@link ModelProtocolHandler} 策略实现，
 * 本类只保留协议无关的共享逻辑：密钥/模型解析、消息组装、URI 解析、非流式错误体解析。
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
    /** 全局单例 ObjectMapper（替代每次 new ObjectMapper()） */
    private final ObjectMapper objectMapper;
    /** 协议策略：OpenAI / Anthropic */
    private final OpenAiProtocolHandler openAiHandler;
    private final AnthropicProtocolHandler anthropicHandler;

    public ModelClient(@Value("${ai.default.url}") String apiUrl,
                       @Value("${ai.default.key:}") String apiKey,
                       @Value("${ai.default.model}") String model,
                       AiProperties aiProperties,
                       ObjectMapper objectMapper,
                       OpenAiProtocolHandler openAiHandler,
                       AnthropicProtocolHandler anthropicHandler) {
        WebClient.Builder builder = WebClient.builder().baseUrl(apiUrl);
        // 默认 YAML 配置按 OpenAI 协议鉴权
        openAiHandler.applyAuthHeaders(builder, apiKey);
        this.defaultWebClient = builder.build();
        this.defaultModel = model;
        this.aiProperties = aiProperties;
        this.objectMapper = objectMapper;
        this.openAiHandler = openAiHandler;
        this.anthropicHandler = anthropicHandler;
    }

    /** 解析后的客户端+模型+鉴权类型，统一 4 处重复的 client/model 解析逻辑。 */
    private record ResolvedClient(WebClient client, String model, String authType) {
    }

    private ResolvedClient resolveClientAndModel(ApiKeyConfig apiKeyConfig) {
        if (apiKeyConfig != null) {
            logger.info("使用数据库 API Key 配置发起请求，id={}, name={}, provider={}, model={}, authType={}",
                    apiKeyConfig.getId(), apiKeyConfig.getName(), apiKeyConfig.getProvider(),
                    apiKeyConfig.getModelName(), apiKeyConfig.getAuthType());
            String authType = apiKeyConfig.getAuthType() != null
                    ? apiKeyConfig.getAuthType().toLowerCase()
                    : "bearer";
            return new ResolvedClient(
                    buildWebClient(apiKeyConfig.getApiKey(), authType),
                    apiKeyConfig.getModelName(),
                    authType);
        }
        logger.info("使用 YAML 默认配置发起请求，model={}", defaultModel);
        return new ResolvedClient(defaultWebClient, defaultModel, "bearer");
    }

    /** 按 authType 选择协议策略：anthropic 走 Anthropic，其余（bearer/openai 等）走 OpenAI。 */
    private ModelProtocolHandler handlerFor(String authType) {
        return "anthropic".equals(authType) ? anthropicHandler : openAiHandler;
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
        ResolvedClient resolved = resolveClientAndModel(apiKeyConfig);
        WebClient client = resolved.client();
        String model = resolved.model();
        String authType = resolved.authType();

        Map<String, Object> requestBody = buildRequest(userMessage, context, history, model, apiKeyConfig);
        String resolvedUri = resolveRequestUri(apiKeyConfig, authType);
        logger.info("实际请求 URI: {}", resolvedUri);

        return handlerFor(authType).stream(client, resolvedUri, requestBody, onChunk, onError, onComplete, objectMapper);
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
        ResolvedClient resolved = resolveClientAndModel(apiKeyConfig);
        WebClient client = resolved.client();
        String model = resolved.model();
        String authType = resolved.authType();
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

        return handlerFor(authType).parseText(response, objectMapper);
    }

    /**
     * 非流式对话接口（不带自定义 systemPrompt），供工作流引擎等同步执行场景使用。
     */
    public String chat(String userMessage,
                       String context,
                       List<Map<String, String>> history,
                       ApiKeyConfig apiKeyConfig) {
        return chat(userMessage, context, history, apiKeyConfig, null);
    }

    /**
     * 支持 Function Calling 的非流式对话接口，供 Agent ReAct 引擎使用。
     * 返回 {@link FunctionCallResult}，包含文本内容和可能的 tool_calls。
     */
    public FunctionCallResult chatWithFunctions(
            String userMessage,
            List<Map<String, String>> history,
            ApiKeyConfig apiKeyConfig,
            String systemPrompt,
            List<Map<String, Object>> tools) {
        ResolvedClient resolved = resolveClientAndModel(apiKeyConfig);
        WebClient client = resolved.client();
        String model = resolved.model();
        String authType = resolved.authType();

        List<Map<String, String>> messages = buildMessages(userMessage, null, history, systemPrompt);
        Map<String, Object> requestBody = handlerFor(authType).buildRequestBody(messages, model, apiKeyConfig, aiProperties, tools);
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

        return handlerFor(authType).parseFunctionCall(response, objectMapper);
    }

    // ─── Function Call 相关 record ───

    public record ToolCall(String id, String name, Map<String, Object> arguments) {}

    public record FunctionCallResult(
        String content,
        List<ToolCall> toolCalls,
        String finishReason,
        int promptTokens,
        int completionTokens,
        double modelCost
    ) {
        public boolean hasToolCalls() {
            return toolCalls != null && !toolCalls.isEmpty();
        }
    }

    // ───────────────────────── 私有方法 ─────────────────────────

    /**
     * 根据 API Key 和认证方式构建 {@link WebClient}（不设 baseUrl，使用完整 URI 发请求）。
     * 鉴权头由对应协议策略 {@link ModelProtocolHandler#applyAuthHeaders} 写入。
     */
    private WebClient buildWebClient(String apiKey, String authType) {
        WebClient.Builder builder = WebClient.builder();
        handlerFor(authType).applyAuthHeaders(builder, apiKey);
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
     *       → 追加协议标准端点路径（由 {@link ModelProtocolHandler#endpointSuffix} 提供）</li>
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
        String suffix = handlerFor(authType).endpointSuffix();
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

        List<Map<String, String>> messages = buildMessages(userMessage, context, history, systemPrompt);
        return handlerFor(authType).buildRequestBody(messages, model, apiKeyConfig, aiProperties, null);
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

    private String parseErrorMessage(String errorBody) {
        String msg = "模型服务暂时不可用";
        try {
            JsonNode errNode = objectMapper.readTree(errorBody);
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
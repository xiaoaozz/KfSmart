package com.smart.kf.client.protocol;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.kf.client.ModelClient.FunctionCallResult;
import com.smart.kf.config.AiProperties;
import com.smart.kf.model.ApiKeyConfig;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.Disposable;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * LLM 协议策略抽象，隔离 OpenAI Chat Completions 与 Anthropic Messages 两套协议差异。
 *
 * <p>取代 {@code ModelClient} 中散落的 {@code "anthropic".equals(authType)} 分支，
 * 由 {@link OpenAiProtocolHandler} / {@link AnthropicProtocolHandler} 各自实现，
 * {@code ModelClient} 按 {@code authType} 选择 handler 委托。
 *
 * <p>协议无关的共享逻辑（消息组装、默认 WebClient 构建、密钥/模型解析、非流式错误体解析）
 * 仍保留在 {@code ModelClient}。
 */
public interface ModelProtocolHandler {

    /** 该 handler 服务的鉴权类型（小写），如 {@code "bearer"}/{@code "openai"} 或 {@code "anthropic"}。 */
    String authType();

    /** 向 WebClient 构建器写入协议特定的鉴权头。 */
    void applyAuthHeaders(WebClient.Builder builder, String apiKey);

    /** 标准 endpoint 后缀（追加到 baseUrl），如 {@code "/chat/completions"} 或 {@code "/messages"}。 */
    String endpointSuffix();

    /**
     * 构建协议特定的请求体。
     *
     * @param messages  已组装好的消息列表（含 system，Anthropic 会将其提取到顶层）
     * @param model     模型名
     * @param apiKeyConfig 数据库 API Key 配置（可空，回退 {@code aiProperties} 默认）
     * @param aiProperties YAML 全局 AI 属性
     * @param tools     工具列表（可空；非空时按协议格式注入 tools/tool_choice）
     */
    Map<String, Object> buildRequestBody(List<Map<String, String>> messages,
                                         String model,
                                         ApiKeyConfig apiKeyConfig,
                                         AiProperties aiProperties,
                                         List<Map<String, Object>> tools);

    /** 解析非流式文本响应，返回纯文本。 */
    String parseText(String response, ObjectMapper objectMapper);

    /** 解析非流式 Function Calling 响应。 */
    FunctionCallResult parseFunctionCall(String response, ObjectMapper objectMapper);

    /**
     * 发起流式请求并订阅，返回 {@link Disposable} 以便取消。
     *
     * <p>包含协议特定的错误体解析与流式数据块处理。
     */
    Disposable stream(WebClient client,
                      String uri,
                      Map<String, Object> requestBody,
                      Consumer<String> onChunk,
                      Consumer<Throwable> onError,
                      Runnable onComplete,
                      ObjectMapper objectMapper);
}

package com.smart.kf.workflow.executor;

import com.smart.kf.client.ModelClient;
import com.smart.kf.model.ApiKeyConfig;
import com.smart.kf.service.ApiKeyConfigService;
import com.smart.kf.workflow.engine.ExecutionContext;
import com.smart.kf.workflow.engine.NodeExecutionResult;
import com.smart.kf.workflow.engine.NodeExecutor;
import com.smart.kf.workflow.engine.TokenUsageTracker;
import com.smart.kf.workflow.model.WorkflowNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * LLM 节点执行器。
 * <p>
 * 从 node.config 读取模型参数（model, temperature, maxTokens, topP, systemPrompt）。
 * 优先使用 node.config 中指定的模型，否则使用默认激活模型。
 */
@Component
public class LlmNodeExecutor implements NodeExecutor {

    private static final Logger logger = LoggerFactory.getLogger(LlmNodeExecutor.class);

    private final ModelClient modelClient;
    private final ApiKeyConfigService apiKeyConfigService;

    @Autowired
    public LlmNodeExecutor(
        ModelClient modelClient,
        ApiKeyConfigService apiKeyConfigService
    ) {
        this.modelClient = modelClient;
        this.apiKeyConfigService = apiKeyConfigService;
    }

    @Override
    public String getNodeType() {
        return "LLM";
    }

    @Override
    @SuppressWarnings("unchecked")
    public NodeExecutionResult execute(WorkflowNode node, ExecutionContext ctx) {
        Optional<ApiKeyConfig> activeConfig = apiKeyConfigService.getActiveConfig();
        if (activeConfig.isEmpty()) {
            throw new IllegalStateException("未配置激活模型，请先在模型管理/API Key 管理中激活模型");
        }

        ApiKeyConfig resolvedConfig = copyApiKeyConfig(activeConfig.get());

        // 1. 从 node.config 读取模型参数
        String desiredModel = node.configString("model");
        Object tempConfig = node.configObject("temperature");
        Object topPConfig = node.configObject("topP");
        Object maxTokensConfig = node.configObject("maxTokens");
        String nodeSystemPrompt = node.configString("systemPrompt");

        // 2. 即时调试覆盖（debug_ 前缀）
        String debugModels = (String) ctx.getVariable("debug_models");
        Object debugTemp = ctx.getVariable("debug_temperature");
        Object debugTopP = ctx.getVariable("debug_topP");
        Object debugMaxTokens = ctx.getVariable("debug_maxTokens");
        String debugSystemPrompt = (String) ctx.getVariable("debug_systemPrompt");

        // 模型选择优先级: node.config > debug override > 默认
        String effectiveModel = firstNonBlank(desiredModel, debugModels);
        if (effectiveModel != null && !effectiveModel.isBlank()) {
            String finalModel = effectiveModel.split(",")[0].trim();
            Optional<ApiKeyConfig> matched = apiKeyConfigService.findAll().stream()
                .filter(c -> finalModel.equals(c.getModelName()))
                .findFirst();
            if (matched.isPresent()) {
                resolvedConfig = copyApiKeyConfig(matched.get());
            }
        }

        // 温度等参数：node.config > debug override > 默认
        if (tempConfig instanceof Number n) resolvedConfig.setTemperature(n.doubleValue());
        if (debugTemp instanceof Number dn) resolvedConfig.setTemperature(dn.doubleValue());
        if (topPConfig instanceof Number tp) resolvedConfig.setTopP(tp.doubleValue());
        if (debugTopP instanceof Number dtp) resolvedConfig.setTopP(dtp.doubleValue());
        if (maxTokensConfig instanceof Number mt) resolvedConfig.setMaxTokens(mt.intValue());
        if (debugMaxTokens instanceof Number dmt) resolvedConfig.setMaxTokens(dmt.intValue());

        // 3. 构建 LLM 输入 — 优先使用 prompt 模板，其次 llmPrompt，最后 query
        String promptTemplate = node.configString("prompt");
        String query;
        if (promptTemplate != null && !promptTemplate.isBlank()) {
            query = ctx.resolveTemplate(promptTemplate);
        } else {
            query = String.valueOf(ctx.getVariable("llmPrompt") != null
                ? ctx.getVariable("llmPrompt")
                : ctx.getOrDefault("query", ""));
        }
        String context = String.valueOf(ctx.getOrDefault("context", ""));

        // 4. 构建 System Prompt — 支持模板变量解析
        String systemPrompt = firstNonBlank(debugSystemPrompt, nodeSystemPrompt);
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            systemPrompt = ctx.resolveTemplate(systemPrompt);
        } else {
            systemPrompt = buildAutoSystemPrompt(ctx);
        }

        // 5. 获取历史消息
        List<Map<String, String>> history = (List<Map<String, String>>) ctx.getVariable("history");
        if (history == null) history = new ArrayList<>();

        // 6. 调用模型
        logger.info("LLM 节点执行: model={}, queryLen={}", resolvedConfig.getModelName(), query.length());
        String answer = modelClient.chat(query, context, history, resolvedConfig, systemPrompt);

        // 7. Token 估算
        int promptTokens = TokenUsageTracker.estimateTokens(query + systemPrompt);
        int completionTokens = TokenUsageTracker.estimateTokens(answer);
        ctx.getTokenUsage().add(promptTokens, completionTokens);

        // 8. 设置输出
        String safeAnswer = answer != null ? answer : "";
        ctx.setVariable("answer", safeAnswer);
        Map<String, Object> outputs = new HashMap<>();
        outputs.put("output", safeAnswer);
        outputs.put("answer", safeAnswer);

        String desc = "调用模型[" + resolvedConfig.getModelName() + "]，输入" + query.length() + "字，输出" + safeAnswer.length() + "字，消耗token=" + (promptTokens + completionTokens);
        return NodeExecutionResult.of(outputs, desc, promptTokens, completionTokens);
    }

    private String buildAutoSystemPrompt(ExecutionContext ctx) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一个 AI Agent。\n");
        String context = String.valueOf(ctx.getOrDefault("context", ""));
        if (!context.isBlank()) {
            sb.append("以下是检索到的参考资料：\n").append(context).append("\n");
        }
        sb.append("\n请用简洁、准确的语言回答用户问题。");
        return sb.toString();
    }

    private String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }

    private ApiKeyConfig copyApiKeyConfig(ApiKeyConfig source) {
        ApiKeyConfig copy = new ApiKeyConfig();
        org.springframework.beans.BeanUtils.copyProperties(source, copy);
        return copy;
    }
}

package com.smart.kf.agent.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.kf.client.ModelClient;
import com.smart.kf.client.TokenCost;
import com.smart.kf.model.ApiKeyConfig;
import com.smart.kf.model.agent.Agent;
import com.smart.kf.service.ApiKeyConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Component
public class ReActEngine {

    private static final Logger logger = LoggerFactory.getLogger(ReActEngine.class);

    private final ModelClient modelClient;
    private final ApiKeyConfigService apiKeyConfigService;
    private final ToolRegistry toolRegistry;
    private final ObjectMapper objectMapper;

    public ReActEngine(
        ModelClient modelClient,
        ApiKeyConfigService apiKeyConfigService,
        ToolRegistry toolRegistry,
        ObjectMapper objectMapper
    ) {
        this.modelClient = modelClient;
        this.apiKeyConfigService = apiKeyConfigService;
        this.toolRegistry = toolRegistry;
        this.objectMapper = objectMapper;
    }

    public AgentContext execute(
        Agent agent,
        String userInput,
        List<Map<String, String>> history,
        Map<String, Object> debugOverrides,
        Consumer<AgentStep> progressListener
    ) {
        AgentContext ctx = new AgentContext(agent.getAgentId(), "agent", userInput, history);

        List<ToolDefinition> tools = toolRegistry.resolveTools(agent);
        List<Map<String, Object>> openAiTools = toolRegistry.toOpenAiToolsFormat(tools);

        ApiKeyConfig resolvedConfig = resolveModelConfig(agent, debugOverrides);
        String systemPrompt = resolveSystemPrompt(agent, debugOverrides);
        int maxIterations = resolveMaxIterations(agent, debugOverrides);

        for (int i = 0; i < maxIterations; i++) {
            long stepStart = System.currentTimeMillis();

            try {
                String userMessage = buildUserMessage(ctx, agent, debugOverrides);
                ModelClient.FunctionCallResult llmResult = modelClient.chatWithFunctions(
                    userMessage,
                    filterHistory(ctx.getMessages()),
                    resolvedConfig,
                    systemPrompt,
                    openAiTools
                );

                int promptTokens = llmResult.promptTokens() > 0 ? llmResult.promptTokens() : estimateTokens(systemPrompt + userMessage);
                int completionTokens = llmResult.completionTokens() > 0 ? llmResult.completionTokens() : estimateTokens(llmResult.content());
                double modelCost = llmResult.modelCost() > 0 ? llmResult.modelCost() : TokenCost.estimate(promptTokens, completionTokens);
                ctx.getTokenUsage().add(promptTokens, completionTokens, modelCost);

                if (llmResult.hasToolCalls()) {
                    for (ModelClient.ToolCall toolCall : llmResult.toolCalls()) {
                        String toolName = toolCall.name();
                        Map<String, Object> toolArgs = toolCall.arguments();

                        AgentStep step = new AgentStep(
                            i + 1,
                            llmResult.content(),
                            toolName,
                            objectMapper.writeValueAsString(toolArgs),
                            null,
                            System.currentTimeMillis() - stepStart,
                            "tool_calling"
                        );
                        ctx.addStep(step);
                        if (progressListener != null) progressListener.accept(step);

                        ToolResult toolResult = toolRegistry.executeTool(toolName, toolArgs, tools);
                        ctx.addToolResultMessage(toolName, toolResult.output() != null ? toolResult.output() : toolResult.errorMessage());

                        AgentStep obsStep = new AgentStep(
                            i + 1,
                            null,
                            toolName,
                            null,
                            toolResult.success() ? toolResult.output() : toolResult.errorMessage(),
                            System.currentTimeMillis() - stepStart,
                            "completed"
                        );
                        if (progressListener != null) progressListener.accept(obsStep);
                    }
                } else {
                    String finalAnswer = llmResult.content();
                    ctx.setFinalAnswer(finalAnswer);
                    ctx.addAssistantMessage(finalAnswer);

                    AgentStep step = new AgentStep(
                        i + 1,
                        finalAnswer,
                        null,
                        null,
                        null,
                        System.currentTimeMillis() - stepStart,
                        "final_answer"
                    );
                    ctx.addStep(step);
                    if (progressListener != null) progressListener.accept(step);
                    break;
                }
            } catch (Exception e) {
                logger.error("ReAct 迭代失败: iteration={}, error={}", i + 1, e.getMessage(), e);
                // 不向终端泄漏内部异常细节，仅返回友好提示
                ctx.setFinalAnswer("执行过程中出错，请稍后重试或联系管理员。");
                break;
            }
        }

        if (ctx.getFinalAnswer() == null) {
            ctx.setFinalAnswer("已达到最大迭代次数，未能完成任务。");
        }

        return ctx;
    }

    private String buildUserMessage(AgentContext ctx, Agent agent, Map<String, Object> debugOverrides) {
        List<Map<String, String>> messages = ctx.getMessages();
        if (messages.isEmpty()) {
            return "";
        }
        Map<String, String> last = messages.get(messages.size() - 1);
        String query = last.getOrDefault("content", "");
        if (!"user".equals(last.get("role"))) {
            return query;
        }
        String template = resolveUserPrompt(agent, debugOverrides);
        if (template == null || template.isBlank()) {
            return query;
        }
        if (template.contains("{{query}}") || template.contains("{{input}}")) {
            return template
                .replace("{{query}}", query)
                .replace("{{input}}", query);
        }
        return template + "\n\n用户输入：\n" + query;
    }

    private List<Map<String, String>> filterHistory(List<Map<String, String>> messages) {
        if (messages.size() <= 1) return new ArrayList<>();
        return new ArrayList<>(messages.subList(0, messages.size() - 1));
    }

    private ApiKeyConfig resolveModelConfig(Agent agent, Map<String, Object> debugOverrides) {
        if (debugOverrides != null) {
            String debugModels = (String) debugOverrides.get("models");
            if (debugModels != null && !debugModels.isBlank()) {
                String modelName = debugModels.split(",")[0].trim();
                var matched = apiKeyConfigService.findAll().stream()
                    .filter(c -> modelName.equals(c.getModelName()))
                    .findFirst();
                if (matched.isPresent()) {
                    return copyApiKeyConfig(matched.get());
                }
            }
        }

        if (agent.getModels() != null && !agent.getModels().isBlank()) {
            String modelName = agent.getModels().split(",")[0].trim();
            var matched = apiKeyConfigService.findAll().stream()
                .filter(c -> modelName.equals(c.getModelName()))
                .findFirst();
            if (matched.isPresent()) {
                return copyApiKeyConfig(matched.get());
            }
        }

        return apiKeyConfigService.getActiveConfig()
            .orElseThrow(() -> new IllegalStateException("未配置激活模型"));
    }

    private ApiKeyConfig copyApiKeyConfig(ApiKeyConfig source) {
        ApiKeyConfig copy = new ApiKeyConfig();
        org.springframework.beans.BeanUtils.copyProperties(source, copy);
        return copy;
    }

    private String resolveSystemPrompt(Agent agent, Map<String, Object> debugOverrides) {
        if (debugOverrides != null) {
            String debugSp = (String) debugOverrides.get("systemPrompt");
            if (debugSp != null && !debugSp.isBlank()) {
                return debugSp;
            }
        }
        return agent.getSystemPrompt() != null ? agent.getSystemPrompt() : "你是一个智能助手，请根据用户问题进行推理和回答。";
    }

    private String resolveUserPrompt(Agent agent, Map<String, Object> debugOverrides) {
        if (debugOverrides != null) {
            String debugUserPrompt = (String) debugOverrides.get("userPrompt");
            if (debugUserPrompt != null && !debugUserPrompt.isBlank()) {
                return debugUserPrompt;
            }
        }
        return agent.getUserPrompt();
    }

    private int resolveMaxIterations(Agent agent, Map<String, Object> debugOverrides) {
        if (debugOverrides != null && debugOverrides.get("maxIterations") instanceof Number n) {
            return n.intValue();
        }
        return agent.getMaxIterations() != null ? agent.getMaxIterations() : 10;
    }

    private int estimateTokens(String text) {
        if (text == null || text.isEmpty()) return 0;
        return (int) Math.ceil(text.length() / 4.0);
    }
}

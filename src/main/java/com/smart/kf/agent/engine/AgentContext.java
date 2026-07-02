package com.smart.kf.agent.engine;

import com.smart.kf.client.TokenCost;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Getter
public class AgentContext {

    private final String executionId;
    private final String agentId;
    private final String username;
    private final List<Map<String, String>> messages;
    private final List<AgentStep> steps;
    private final TokenUsage tokenUsage;
    @Setter
    private String finalAnswer;
    private int iterations;

    public AgentContext(String agentId, String username, String userInput, List<Map<String, String>> history) {
        this.executionId = "exec_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        this.agentId = agentId;
        this.username = username;
        this.messages = new ArrayList<>();
        this.steps = new ArrayList<>();
        this.tokenUsage = new TokenUsage();

        if (history != null) {
            messages.addAll(history);
        }
        messages.add(Map.of("role", "user", "content", userInput != null ? userInput : ""));
    }

    public void addAssistantMessage(String content) {
        messages.add(Map.of("role", "assistant", "content", content));
    }

    public void addToolResultMessage(String toolName, String result) {
        messages.add(Map.of("role", "tool", "name", toolName != null ? toolName : "", "content", result));
    }

    public void addStep(AgentStep step) {
        steps.add(step);
        iterations++;
    }

    @Getter
    public static class TokenUsage {
        private int promptTokens;
        private int completionTokens;
        private BigDecimal modelCost = BigDecimal.ZERO;
        private final BigDecimal toolCost = BigDecimal.ZERO;

        public void add(int prompt, int completion) {
            this.promptTokens += prompt;
            this.completionTokens += completion;
            this.modelCost = this.modelCost.add(BigDecimal.valueOf(TokenCost.estimate(prompt, completion)));
        }

        public void add(int prompt, int completion, double modelCost) {
            this.promptTokens += prompt;
            this.completionTokens += completion;
            this.modelCost = this.modelCost.add(BigDecimal.valueOf(Math.max(0D, modelCost)));
        }

        public int getTotalTokens() {
            return promptTokens + completionTokens;
        }

        public double getCost() {
            return getModelCost() + getToolCost();
        }

        public double getModelCost() {
            return modelCost.doubleValue();
        }

        public double getToolCost() {
            return toolCost.doubleValue();
        }
    }
}

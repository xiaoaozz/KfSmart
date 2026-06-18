package com.smart.kf.workflow.engine;

import java.util.concurrent.atomic.AtomicInteger;

public class TokenUsageTracker {
    private final AtomicInteger promptTokens = new AtomicInteger(0);
    private final AtomicInteger completionTokens = new AtomicInteger(0);
    private static final double COST_PER_TOKEN = 0.00001;

    public void add(int promptTokens, int completionTokens) {
        this.promptTokens.addAndGet(promptTokens);
        this.completionTokens.addAndGet(completionTokens);
    }

    public int getPromptTokens() {
        return promptTokens.get();
    }

    public int getCompletionTokens() {
        return completionTokens.get();
    }

    public int getTotalTokens() {
        return promptTokens.get() + completionTokens.get();
    }

    public double getCost() {
        return getTotalTokens() * COST_PER_TOKEN;
    }

    public static int estimateTokens(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return Math.max(1, (int) Math.ceil(text.length() / 4.0));
    }
}

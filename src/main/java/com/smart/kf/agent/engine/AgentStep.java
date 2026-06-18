package com.smart.kf.agent.engine;

public record AgentStep(
    int iteration,
    String thought,
    String action,
    String actionInput,
    String observation,
    long durationMs,
    String status
) {}

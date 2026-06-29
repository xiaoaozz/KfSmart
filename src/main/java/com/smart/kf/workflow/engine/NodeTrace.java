package com.smart.kf.workflow.engine;

public record NodeTrace(
    String nodeId,
    String nodeName,
    String nodeType,
    String status,
    long durationMs,
    long startedAt,
    Integer promptTokens,
    Integer completionTokens,
    String errorMessage,
    String description,
    java.util.Map<String, Object> inputs,
    java.util.Map<String, Object> outputs
) {
    public static NodeTrace start(String nodeId, String nodeName, String nodeType) {
        return new NodeTrace(nodeId, nodeName, nodeType, "running", 0, System.currentTimeMillis(), null, null, null, null, null, null);
    }

    public NodeTrace success(long durationMs, java.util.Map<String, Object> inputs, java.util.Map<String, Object> outputs, String description, int promptTokens, int completionTokens) {
        return new NodeTrace(nodeId, nodeName, nodeType, "success", durationMs, startedAt, promptTokens, completionTokens, null, description, inputs, outputs);
    }

    public NodeTrace failed(long durationMs, String errorMessage) {
        return new NodeTrace(nodeId, nodeName, nodeType, "error", durationMs, startedAt, promptTokens, completionTokens, errorMessage, null, null, null);
    }
}

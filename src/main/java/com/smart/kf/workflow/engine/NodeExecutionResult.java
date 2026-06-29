package com.smart.kf.workflow.engine;

import java.util.List;
import java.util.Map;

public record NodeExecutionResult(
    Map<String, Object> outputs,
    String routingPort,
    List<String> nextNodeIds,
    String description,
    Integer promptTokens,
    Integer completionTokens
) {
    public static NodeExecutionResult of(Map<String, Object> outputs) {
        return new NodeExecutionResult(outputs, null, List.of(), null, 0, 0);
    }

    public static NodeExecutionResult of(Map<String, Object> outputs, String description) {
        return new NodeExecutionResult(outputs, null, List.of(), description, 0, 0);
    }

    public static NodeExecutionResult of(Map<String, Object> outputs, String description, int promptTokens, int completionTokens) {
        return new NodeExecutionResult(outputs, null, List.of(), description, promptTokens, completionTokens);
    }

    public static NodeExecutionResult of(Map<String, Object> outputs, String routingPort, String description) {
        return new NodeExecutionResult(outputs, routingPort, List.of(), description, 0, 0);
    }

    public static NodeExecutionResult empty() {
        return new NodeExecutionResult(Map.of(), null, List.of(), null, 0, 0);
    }
}

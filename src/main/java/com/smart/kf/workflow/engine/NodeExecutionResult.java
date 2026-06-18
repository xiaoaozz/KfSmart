package com.smart.kf.workflow.engine;

import java.util.List;
import java.util.Map;

public record NodeExecutionResult(
    Map<String, Object> outputs,
    String routingPort,
    List<String> nextNodeIds,
    String description
) {
    public static NodeExecutionResult of(Map<String, Object> outputs) {
        return new NodeExecutionResult(outputs, null, List.of(), null);
    }

    public static NodeExecutionResult of(Map<String, Object> outputs, String description) {
        return new NodeExecutionResult(outputs, null, List.of(), description);
    }

    public static NodeExecutionResult of(Map<String, Object> outputs, String routingPort, String description) {
        return new NodeExecutionResult(outputs, routingPort, List.of(), description);
    }

    public static NodeExecutionResult empty() {
        return new NodeExecutionResult(Map.of(), null, List.of(), null);
    }
}

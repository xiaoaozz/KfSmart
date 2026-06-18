package com.smart.kf.workflow.model;

public record WorkflowEdge(
    String id,
    String source,
    String target,
    String sourcePort,
    String label,
    String condition
) {
    public static WorkflowEdge of(String source, String target) {
        return new WorkflowEdge(null, source, target, null, null, null);
    }

    public static WorkflowEdge of(String source, String target, String sourcePort) {
        return new WorkflowEdge(null, source, target, sourcePort, null, null);
    }
}

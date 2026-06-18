package com.smart.kf.agent.engine;

public record ToolResult(
    boolean success,
    String output,
    String errorMessage
) {
    public static ToolResult success(String output) {
        return new ToolResult(true, output, null);
    }

    public static ToolResult failure(String errorMessage) {
        return new ToolResult(false, null, errorMessage);
    }
}

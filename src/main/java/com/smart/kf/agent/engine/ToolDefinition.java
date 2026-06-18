package com.smart.kf.agent.engine;

import java.util.Map;
import java.util.function.Function;

public record ToolDefinition(
    String name,
    String description,
    Map<String, Object> parametersSchema,
    Function<Map<String, Object>, ToolResult> executor
) {}

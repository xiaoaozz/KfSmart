package com.smart.kf.workflow.model;

import java.util.Map;

public record WorkflowNode(
    String id,
    String type,
    String name,
    int x,
    int y,
    String description,
    Map<String, Object> config
) {
    public WorkflowNode {
        if (config == null) {
            config = Map.of();
        }
    }

    public static WorkflowNode of(String id, String type, String name, int x, int y) {
        return new WorkflowNode(id, type, name, x, y, null, Map.of());
    }

    public String configString(String key) {
        if (config == null) return null;
        Object val = config.get(key);
        return val == null ? null : val.toString();
    }

    public Object configObject(String key) {
        return config == null ? null : config.get(key);
    }

    public Object configObject(String key, Object defaultValue) {
        Object val = configObject(key);
        return val != null ? val : defaultValue;
    }
}

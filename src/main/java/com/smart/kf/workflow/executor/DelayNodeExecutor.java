package com.smart.kf.workflow.executor;

import com.smart.kf.workflow.engine.ExecutionContext;
import com.smart.kf.workflow.engine.NodeExecutionResult;
import com.smart.kf.workflow.engine.NodeExecutor;
import com.smart.kf.workflow.model.WorkflowNode;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class DelayNodeExecutor implements NodeExecutor {

    @Override
    public String getNodeType() {
        return "延迟";
    }

    @Override
    public NodeExecutionResult execute(WorkflowNode node, ExecutionContext ctx) {
        long delayMs = toLong(node.configObject("delayMs"), 1000);
        String unit = node.configString("unit");
        if (unit != null) {
            switch (unit) {
                case "s" -> delayMs *= 1000;
                case "min" -> delayMs *= 60000;
            }
        }
        try {
            Thread.sleep(Math.min(delayMs, 300000));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        Map<String, Object> outputs = new HashMap<>();
        outputs.put("delayedMs", delayMs);
        return NodeExecutionResult.of(outputs, "延迟等待" + delayMs + "ms");
    }

    @SuppressWarnings("sameParameterValue")
    private long toLong(Object val, long defaultValue) {
        if (val instanceof Number n) return n.longValue();
        if (val instanceof String s) {
            try { return Long.parseLong(s); } catch (Exception ignored) {}
        }
        return defaultValue;
    }
}

package com.smart.kf.workflow.executor;

import com.smart.kf.workflow.engine.ExecutionContext;
import com.smart.kf.workflow.engine.NodeExecutionResult;
import com.smart.kf.workflow.engine.NodeExecutor;
import com.smart.kf.workflow.model.WorkflowNode;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class StartNodeExecutor implements NodeExecutor {

    @Override
    public String getNodeType() {
        return "开始";
    }

    @Override
    @SuppressWarnings("unchecked")
    public NodeExecutionResult execute(WorkflowNode node, ExecutionContext ctx) {
        Map<String, Object> outputs = new LinkedHashMap<>();

        // 从 node.config 读取 variables 列表
        Object variablesConfig = node.configObject("variables");
        List<Map<String, Object>> variables = new ArrayList<>();
        if (variablesConfig instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    variables.add((Map<String, Object>) map);
                }
            }
        }

        if (!variables.isEmpty()) {
            // 有变量配置：按配置注入，前端传入值优先，否则用默认值
            for (Map<String, Object> varDef : variables) {
                String varName = String.valueOf(varDef.get("name"));
                String defaultValue = varDef.get("value") != null ? String.valueOf(varDef.get("value")) : "";

                Object existing = ctx.getVariable(varName);
                String value = existing != null ? String.valueOf(existing) : defaultValue;

                ctx.setVariable(varName, value);
                outputs.put(varName, value);
            }
        } else {
            // 向后兼容：没有 variables 配置，用 query
            ctx.setVariableIfAbsent("query", "");
            outputs.put("query", ctx.getVariable("query"));
        }

        return NodeExecutionResult.of(outputs, "接收用户输入: " + ctx.getVariables());
    }
}

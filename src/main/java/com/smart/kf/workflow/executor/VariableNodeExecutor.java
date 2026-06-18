package com.smart.kf.workflow.executor;

import com.smart.kf.workflow.engine.ExecutionContext;
import com.smart.kf.workflow.engine.NodeExecutionResult;
import com.smart.kf.workflow.engine.NodeExecutor;
import com.smart.kf.workflow.model.WorkflowNode;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class VariableNodeExecutor implements NodeExecutor {

    @Override
    public String getNodeType() {
        return "变量";
    }

    @Override
    public NodeExecutionResult execute(WorkflowNode node, ExecutionContext ctx) {
        String varName = node.configString("varName");
        String varValue = node.configString("varValue");

        if (varName != null && !varName.isBlank()) {
            String resolved = ctx.resolveTemplate(varValue);
            ctx.setVariable(varName, resolved);
            Map<String, Object> outputs = new HashMap<>();
            outputs.put(varName, resolved);
            return NodeExecutionResult.of(outputs, "设置变量 " + varName + " = " + (resolved.length() > 80 ? resolved.substring(0, 80) + "..." : resolved));
        }
        return NodeExecutionResult.empty();
    }
}

package com.smart.kf.workflow.executor;

import com.smart.kf.workflow.engine.ExecutionContext;
import com.smart.kf.workflow.engine.NodeExecutionResult;
import com.smart.kf.workflow.engine.NodeExecutor;
import com.smart.kf.workflow.model.WorkflowNode;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class StartNodeExecutor implements NodeExecutor {

    @Override
    public String getNodeType() {
        return "开始";
    }

    @Override
    public NodeExecutionResult execute(WorkflowNode node, ExecutionContext ctx) {
        ctx.setVariableIfAbsent("query", "");
        Map<String, Object> outputs = new HashMap<>();
        outputs.put("query", ctx.getVariable("query"));
        return NodeExecutionResult.of(outputs, "接收用户输入: " + ctx.getVariable("query"));
    }
}

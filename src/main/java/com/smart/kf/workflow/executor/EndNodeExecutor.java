package com.smart.kf.workflow.executor;

import com.smart.kf.workflow.engine.ExecutionContext;
import com.smart.kf.workflow.engine.NodeExecutionResult;
import com.smart.kf.workflow.engine.NodeExecutor;
import com.smart.kf.workflow.model.WorkflowNode;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class EndNodeExecutor implements NodeExecutor {

    @Override
    public String getNodeType() {
        return "结束";
    }

    @Override
    public NodeExecutionResult execute(WorkflowNode node, ExecutionContext ctx) {
        String outputMode = node.configString("outputMode");
        Map<String, Object> outputs = new HashMap<>();

        if ("模板渲染".equals(outputMode)) {
            String template = node.configString("outputTemplate");
            String rendered = ctx.resolveTemplate(template);
            ctx.setVariable("answer", rendered);
            outputs.put("answer", rendered);
        } else if ("变量映射".equals(outputMode)) {
            Object answer = ctx.getVariable("answer");
            if (answer == null) {
                answer = ctx.getVariable("toolResult");
            }
            ctx.setVariableIfAbsent("answer", answer != null ? answer : "工作流执行完成");
            outputs.put("answer", ctx.getVariable("answer"));
        } else {
            // 直接输出
            Object answer = ctx.getVariable("answer");
            if (answer == null) {
                answer = ctx.getVariable("toolResult");
            }
            ctx.setVariableIfAbsent("answer", answer != null ? answer : "工作流执行完成");
            outputs.put("answer", ctx.getVariable("answer"));
        }

        return NodeExecutionResult.of(outputs, "输出结果: " + (outputMode != null ? outputMode : "直接输出"));
    }
}

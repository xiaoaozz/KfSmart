package com.smart.kf.workflow.executor;

import com.smart.kf.workflow.engine.ExecutionContext;
import com.smart.kf.workflow.engine.NodeExecutionResult;
import com.smart.kf.workflow.engine.NodeExecutor;
import com.smart.kf.workflow.model.WorkflowNode;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 循环节点执行器。
 * 支持：列表循环（foreach）、计数循环、条件循环（while）。
 * <p>
 * 循环体通过子图执行实现：每次迭代将循环变量注入上下文，然后继续后续节点。
 * 此执行器负责设置循环变量和迭代计数，实际循环体执行由引擎后续节点完成。
 */
@Component
public class LoopNodeExecutor implements NodeExecutor {

    @Override
    public String getNodeType() {
        return "循环";
    }

    @Override
    public NodeExecutionResult execute(WorkflowNode node, ExecutionContext ctx) {
        String loopType = node.configString("loopType");
        int maxIterations = toInt(node.configObject("maxIterations"), 100);
        String loopVar = node.configString("loopVar");
        if (loopVar == null || loopVar.isBlank()) loopVar = "item";

        int currentIteration = toInt(ctx.getVariable("_loop_iteration_" + node.id()), 0);
        boolean shouldContinue = false;

        if ("列表循环".equals(loopType)) {
            String iterateVar = node.configString("iterateVar");
            Object items = ctx.getVariable(iterateVar != null ? iterateVar.replace("{{", "").replace("}}", "").trim() : "items");
            if (items instanceof List<?> list) {
                if (currentIteration < Math.min(list.size(), maxIterations)) {
                    ctx.setVariable(loopVar, list.get(currentIteration));
                    shouldContinue = true;
                }
            }
        } else if ("计数循环".equals(loopType)) {
            int end = toInt(ctx.getVariable("_loop_end_" + node.id()), maxIterations);
            if (currentIteration == 0) {
                ctx.setVariable("_loop_start_" + node.id(), 0);
                ctx.setVariable("_loop_end_" + node.id(), maxIterations);
            }
            if (currentIteration < end) {
                ctx.setVariable(loopVar, currentIteration);
                shouldContinue = true;
            }
        } else if ("条件循环".equals(loopType)) {
            String conditionExpr = node.configString("iterateVar");
            boolean conditionMet = evaluateSimple(conditionExpr, ctx);
            if (conditionMet && currentIteration < maxIterations) {
                shouldContinue = true;
            }
        }

        ctx.setVariable("_loop_iteration_" + node.id(), currentIteration + 1);

        Map<String, Object> outputs = new HashMap<>();
        outputs.put("iteration", currentIteration);
        outputs.put("continue", shouldContinue);

        return NodeExecutionResult.of(outputs, shouldContinue ? "loop" : "exit", "循环类型[" + loopType + "]，第" + (currentIteration + 1) + "次迭代" + (shouldContinue ? "（继续）" : "（结束）"));
    }

    private int toInt(Object val, int defaultValue) {
        if (val instanceof Number n) return n.intValue();
        if (val instanceof String s) {
            try { return Integer.parseInt(s); } catch (Exception ignored) {}
        }
        return defaultValue;
    }

    private boolean evaluateSimple(String expr, ExecutionContext ctx) {
        if (expr == null || expr.isBlank()) return false;
        String resolved = ctx.resolveTemplate(expr).trim();
        return !resolved.isBlank() && !"false".equalsIgnoreCase(resolved) && !"0".equals(resolved);
    }
}

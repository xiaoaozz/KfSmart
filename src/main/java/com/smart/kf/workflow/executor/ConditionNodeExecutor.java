package com.smart.kf.workflow.executor;

import com.smart.kf.workflow.engine.ExecutionContext;
import com.smart.kf.workflow.engine.NodeExecutionResult;
import com.smart.kf.workflow.engine.NodeExecutor;
import com.smart.kf.workflow.model.WorkflowNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 条件判断节点执行器。
 * <p>
 * 解析 conditionExpr 表达式，求值后返回 routingPort ("true"/"false")。
 * 支持的表达式格式：
 * <ul>
 *   <li>{@code {{input.score}} >= 60} — 数值比较</li>
 *   <li>{@code {{input.type}} == "A"} — 字符串等值</li>
 *   <li>{@code {{query}} contains "关键词"} — 包含判断</li>
 * </ul>
 */
@Component
public class ConditionNodeExecutor implements NodeExecutor {

    private static final Logger logger = LoggerFactory.getLogger(ConditionNodeExecutor.class);
    private static final Pattern TEMPLATE_PATTERN = Pattern.compile("\\{\\{([^}]+)}}");

    @Override
    public String getNodeType() {
        return "条件判断";
    }

    @Override
    public NodeExecutionResult execute(WorkflowNode node, ExecutionContext ctx) {
        String conditionExpr = node.configString("conditionExpr");
        if (conditionExpr == null || conditionExpr.isBlank()) {
            conditionExpr = "{{query}} != \"\"";
        }

        boolean matched = evaluateCondition(conditionExpr, ctx);
        logger.info("条件判断: expr={}, result={}", conditionExpr, matched);

        Map<String, Object> outputs = new HashMap<>();
        outputs.put("conditionMatched", matched);
        outputs.put("conditionExpr", conditionExpr);
        ctx.setVariable("conditionMatched", matched);

        return NodeExecutionResult.of(outputs, matched ? "true" : "false", "条件[" + conditionExpr + "]评估结果: " + matched);
    }

    /**
     * 安全评估条件表达式。
     * 支持: >=, <=, >, <, ==, !=, contains
     */
    private boolean evaluateCondition(String expr, ExecutionContext ctx) {
        try {
            String resolved = resolveExpression(expr, ctx);
            resolved = resolved.trim();

            // contains 操作
            int containsIdx = findOperator(resolved, "contains");
            if (containsIdx > 0) {
                String left = resolved.substring(0, containsIdx).trim();
                String right = stripQuotes(resolved.substring(containsIdx + "contains".length()).trim());
                return left.contains(right);
            }

            // >=
            int idx = findOperator(resolved, ">=");
            if (idx > 0) return compareNumeric(resolved, idx, 2) >= 0;

            // <=
            idx = findOperator(resolved, "<=");
            if (idx > 0) return compareNumeric(resolved, idx, 2) <= 0;

            // !=
            idx = findOperator(resolved, "!=");
            if (idx > 0) return !compareString(resolved, idx);

            // ==
            idx = findOperator(resolved, "==");
            if (idx > 0) return compareString(resolved, idx);

            // >
            idx = findOperator(resolved, ">");
            if (idx > 0) return compareNumeric(resolved, idx, 1) > 0;

            // <
            idx = findOperator(resolved, "<");
            if (idx > 0) return compareNumeric(resolved, idx, 1) < 0;

            // 无操作符：非空判断
            return !resolved.isBlank() && !"false".equalsIgnoreCase(resolved) && !"0".equals(resolved);
        } catch (Exception e) {
            logger.warn("条件表达式评估失败: expr={}, error={}", expr, e.getMessage());
            return false;
        }
    }

    private String resolveExpression(String expr, ExecutionContext ctx) {
        Matcher matcher = TEMPLATE_PATTERN.matcher(expr);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String var = matcher.group(1).trim();
            Object val = ctx.getVariable(var);
            String replacement = val != null ? String.valueOf(val) : "";
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private int findOperator(String s, String op) {
        return s.indexOf(op);
    }

    private double compareNumeric(String resolved, int opIdx, int opLen) {
        String left = resolved.substring(0, opIdx).trim().replaceAll("[\"']", "");
        String right = resolved.substring(opIdx + opLen).trim().replaceAll("[\"']", "");
        try {
            return Double.parseDouble(left) - Double.parseDouble(right);
        } catch (NumberFormatException e) {
            return left.compareTo(right);
        }
    }

    private boolean compareString(String resolved, int opIdx) {
        String left = resolved.substring(0, opIdx).trim().replaceAll("[\"']", "");
        String right = resolved.substring(opIdx + 2).trim().replaceAll("[\"']", "");
        return left.equals(right);
    }

    private String stripQuotes(String s) {
        return s.replaceAll("[\"']", "");
    }
}

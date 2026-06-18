package com.smart.kf.workflow.engine;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 工作流执行上下文，线程安全。
 * <p>
 * 管理全局变量、节点输出、执行 trace、token 消耗。
 * 支持 {{变量}} 模板解析，支持 {{nodeId.output}} 点号嵌套访问。
 */
public class ExecutionContext {

    private static final Pattern TEMPLATE_PATTERN = Pattern.compile("\\{\\{([^}]+)}}");

    @Getter
    private final String executionId;
    @Getter
    private final String workflowId;
    @Getter
    private final String username;
    @Getter
    private final long startedAt;

    private final ConcurrentHashMap<String, Object> variables;
    private final ConcurrentHashMap<String, Map<String, Object>> nodeOutputs;
    private final List<NodeTrace> trace;
    @Getter
    private final TokenUsageTracker tokenUsage;
    @Getter
    @Setter
    private volatile Consumer<NodeTrace> progressListener;

    public ExecutionContext(String workflowId, String username, Map<String, Object> input) {
        this.executionId = "exec_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        this.workflowId = workflowId;
        this.username = username;
        this.startedAt = System.currentTimeMillis();
        this.variables = new ConcurrentHashMap<>(input == null ? Map.of() : input);
        this.variables.putIfAbsent("query", "");
        this.nodeOutputs = new ConcurrentHashMap<>();
        this.trace = new ArrayList<>();
        this.tokenUsage = new TokenUsageTracker();
    }

    public long getDurationMs() {
        return System.currentTimeMillis() - startedAt;
    }

    // ── 变量管理 ──

    public Object getVariable(String key) {
        return variables.get(key);
    }

    public Object getOrDefault(String key, Object defaultValue) {
        return variables.getOrDefault(key, defaultValue);
    }

    public void setVariable(String key, Object value) {
        if (key != null && value != null) {
            variables.put(key, value);
        }
    }

    public void setVariableIfAbsent(String key, Object value) {
        if (key != null && value != null) {
            variables.putIfAbsent(key, value);
        }
    }

    public Map<String, Object> getVariables() {
        return new HashMap<>(variables);
    }

    // ── 节点输出 ──

    public void setNodeOutput(String nodeId, Map<String, Object> output) {
        nodeOutputs.put(nodeId, output);
        output.forEach((key, value) -> {
            if (key != null && value != null && !variables.containsKey(key)) {
                variables.put(key, value);
            }
        });
    }

    // ── Trace ──

    public void addTrace(NodeTrace nodeTrace) {
        synchronized (trace) {
            trace.add(nodeTrace);
        }
        if (progressListener != null) {
            progressListener.accept(nodeTrace);
        }
    }

    public List<NodeTrace> getTrace() {
        synchronized (trace) {
            return new ArrayList<>(trace);
        }
    }

    // ── 模板解析 ──

    /**
     * 解析模板字符串中的 {{变量}} 占位符。
     * 支持以下语法：
     * <ul>
     *   <li>{@code {{query}}} → 简单变量</li>
     *   <li>{@code {{input.query}}} → 等价于 {{query}}（input 前缀映射到全局变量）</li>
     *   <li>{@code {{nodeId.key}}} → 访问节点输出的 key</li>
     *   <li>{@code {{context}}} → 知识库检索上下文</li>
     * </ul>
     */
    public String resolveTemplate(String template) {
        if (template == null || template.isBlank()) {
            return "";
        }
        Matcher matcher = TEMPLATE_PATTERN.matcher(template);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String expression = matcher.group(1).trim();
            String replacement = resolveExpression(expression);
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private String resolveExpression(String expression) {
        if (expression.startsWith("input.")) {
            String key = expression.substring("input.".length());
            return String.valueOf(variables.getOrDefault(key, ""));
        }
        if (expression.startsWith("context")) {
            return String.valueOf(variables.getOrDefault("context", ""));
        }
        int dotIndex = expression.indexOf('.');
        if (dotIndex > 0) {
            String prefix = expression.substring(0, dotIndex);
            String suffix = expression.substring(dotIndex + 1);
            Map<String, Object> output = nodeOutputs.get(prefix);
            if (output != null) {
                Object val = output.get(suffix);
                if (val == null && suffix.equals("output")) {
                    val = output.get("answer");
                }
                return val == null ? "" : String.valueOf(val);
            }
        }
        Object val = variables.get(expression);
        return val == null ? "" : String.valueOf(val);
    }

    /**
     * 构建最终执行结果。
     */
    public ExecutionResult buildResult() {
        String answer = String.valueOf(variables.getOrDefault("answer", ""));
        Map<String, Object> output = new HashMap<>();
        output.put("answer", answer);
        output.put("documents", variables.getOrDefault("documents", List.of()));
        output.put("toolResult", variables.get("toolResult"));

        return new ExecutionResult(
            executionId,
            workflowId,
            getTrace(),
            getVariables(),
            output,
            getDurationMs(),
            tokenUsage.getTotalTokens() > 0 || !answer.isBlank(),
            null,
            tokenUsage
        );
    }

    /**
     * 执行结果汇总。
     */
    public record ExecutionResult(
        String executionId,
        String workflowId,
        List<NodeTrace> trace,
        Map<String, Object> variables,
        Map<String, Object> output,
        long durationMs,
        boolean success,
        String errorMessage,
        TokenUsageTracker tokenUsage
    ) {}
}

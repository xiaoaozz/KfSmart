package com.smart.kf.workflow.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.kf.workflow.engine.dag.GraphBuilder;
import com.smart.kf.workflow.engine.dag.TopologicalSorter;
import com.smart.kf.workflow.engine.dag.WorkflowGraph;
import com.smart.kf.workflow.model.WorkflowEdge;
import com.smart.kf.workflow.model.WorkflowNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * DAG 工作流执行引擎核心。
 * <p>
 * 支持条件分支路由、并行扇出、循环节点。
 * 通过 {@link NodeExecutorRegistry} 分发到各节点的执行器。
 */
@Component
public class WorkflowExecutionEngine {

    private static final Logger logger = LoggerFactory.getLogger(WorkflowExecutionEngine.class);

    private final NodeExecutorRegistry registry;
    private final GraphBuilder graphBuilder;
    private final TopologicalSorter sorter;

    public WorkflowExecutionEngine(NodeExecutorRegistry registry, ObjectMapper objectMapper) {
        this.registry = registry;
        this.graphBuilder = new GraphBuilder(objectMapper);
        this.sorter = new TopologicalSorter();
    }

    /**
     * 执行工作流（同步）。
     */
    public ExecutionContext.ExecutionResult execute(
        String nodesJson,
        String edgesJson,
        String workflowId,
        String username,
        Map<String, Object> input
    ) {
        return execute(nodesJson, edgesJson, workflowId, username, input, null);
    }

    /**
     * 执行工作流（支持进度回调）。
     *
     * @param progressListener 每个节点执行完成后回调（用于 WebSocket 推送）
     */
    public ExecutionContext.ExecutionResult execute(
        String nodesJson,
        String edgesJson,
        String workflowId,
        String username,
        Map<String, Object> input,
        Consumer<NodeTrace> progressListener
    ) {
        long startedAt = System.currentTimeMillis();

        WorkflowGraph graph = graphBuilder.build(nodesJson, edgesJson);
        if (graph.isEmpty()) {
            graph = new WorkflowGraph(GraphBuilder.defaultNodes(), GraphBuilder.defaultEdges());
        }

        // DAG 校验（环检测）
        sorter.sort(graph);

        // 提取历史消息
        Map<String, Object> effectiveInput = new HashMap<>(input == null ? Map.of() : input);
        @SuppressWarnings("unchecked")
        List<Map<String, String>> history = (List<Map<String, String>>) effectiveInput.remove("history");
        if (history == null) {
            history = new ArrayList<>();
        }
        effectiveInput.put("history", history);

        // 提取即时调试覆盖配置
        extractDebugOverrides(effectiveInput);

        // 前端调试面板以 {input: "..."} 传入用户输入，映射到 query 变量
        Object inputVal = effectiveInput.get("input");
        if (inputVal != null && effectiveInput.get("query") == null) {
            effectiveInput.put("query", String.valueOf(inputVal));
        }

        ExecutionContext ctx = new ExecutionContext(workflowId, username, effectiveInput);
        if (progressListener != null) {
            ctx.setProgressListener(progressListener);
        }

        boolean success = true;
        String errorMessage = null;

        try {
            WorkflowNode startNode = graph.getStartNode();
            if (startNode == null) {
                throw new IllegalStateException("工作流缺少开始节点");
            }

            Set<String> executed = ConcurrentHashMap.newKeySet();
            executeFromNode(startNode.id(), graph, ctx, executed);

        } catch (Exception e) {
            success = false;
            errorMessage = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            logger.error("工作流执行失败: workflowId={}, error={}", workflowId, errorMessage, e);
        }

        long duration = System.currentTimeMillis() - startedAt;

        if (!success) {
            return createFailedResult(ctx, duration, errorMessage);
        }

        return ctx.buildResult();
    }

    /**
     * 从指定节点开始执行，沿边遍历。
     */
    private void executeFromNode(
        String startNodeId,
        WorkflowGraph graph,
        ExecutionContext ctx,
        Set<String> executed
    ) {
        Queue<String> queue = new ArrayDeque<>();
        queue.add(startNodeId);

        while (!queue.isEmpty()) {
            String currentId = queue.poll();
            if (executed.contains(currentId)) {
                continue;
            }

            WorkflowNode node = graph.getNode(currentId);
            if (node == null) {
                logger.warn("节点不存在: {}", currentId);
                continue;
            }

            // 等待扇入：如果该节点有多个入边且部分前驱未执行，延迟执行
            if (!isReadyToExecute(currentId, graph, executed)) {
                queue.add(currentId);
                continue;
            }

            NodeTrace trace = NodeTrace.start(node.id(), node.name(), node.type());
            ctx.addTrace(trace);

            long nodeStart = System.currentTimeMillis();
            int promptBefore = ctx.getTokenUsage().getPromptTokens();
            int completionBefore = ctx.getTokenUsage().getCompletionTokens();

            // 执行前解析入参映射，将上游节点输出注入到当前节点可访问的变量中
            resolveInputMappings(node, ctx);

            Map<String, Object> inputSnapshot = captureNodeInputs(ctx);

            try {
                NodeExecutor executor = registry.getExecutor(node.type());
                if (executor == null) {
                    throw new IllegalStateException("未找到节点类型 [" + node.type() + "] 的执行器");
                }

                NodeExecutionResult result = executor.execute(node, ctx);
                executed.add(currentId);

                // 记录节点输出到 trace
                Map<String, Object> outputs = result.outputs() != null ? result.outputs() : Map.of();
                ctx.setNodeOutput(currentId, outputs);

                int promptDelta = ctx.getTokenUsage().getPromptTokens() - promptBefore;
                int completionDelta = ctx.getTokenUsage().getCompletionTokens() - completionBefore;
                int nodePromptTokens = result.promptTokens() != null && result.promptTokens() > 0
                    ? result.promptTokens() : promptDelta;
                int nodeCompletionTokens = result.completionTokens() != null && result.completionTokens() > 0
                    ? result.completionTokens() : completionDelta;

                NodeTrace completed = trace.success(
                    System.currentTimeMillis() - nodeStart,
                    inputSnapshot,
                    outputs,
                    result.description(),
                    nodePromptTokens,
                    nodeCompletionTokens
                );
                ctx.addTrace(completed);

                // 确定下一批节点
                List<String> nextIds = resolveNextNodes(graph, currentId, result, executed);
                queue.addAll(nextIds);

            } catch (Exception e) {
                String failMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                NodeTrace failed = trace.failed(System.currentTimeMillis() - nodeStart, failMsg);
                ctx.addTrace(failed);
                throw e;
            }
        }
    }

    /**
     * 检查节点是否准备好执行（所有前驱节点已完成）。
     */
    private boolean isReadyToExecute(String nodeId, WorkflowGraph graph, Set<String> executed) {
        List<WorkflowEdge> incoming = graph.getIncomingEdges(nodeId);
        for (WorkflowEdge edge : incoming) {
            if (!executed.contains(edge.source())) {
                return false;
            }
        }
        return true;
    }

    /**
     * 解析下一批要执行的节点 ID。
     * <ul>
     *   <li>如果 result 指定了 nextNodeIds，直接使用</li>
     *   <li>如果是路由节点（条件判断），按 routingPort 匹配出边</li>
     *   <li>否则跟随所有出边（并行扇出）</li>
     * </ul>
     */
    private List<String> resolveNextNodes(
        WorkflowGraph graph,
        String currentId,
        NodeExecutionResult result,
        Set<String> executed
    ) {
        if (result.nextNodeIds() != null && !result.nextNodeIds().isEmpty()) {
            return result.nextNodeIds().stream()
                .filter(id -> !executed.contains(id))
                .toList();
        }

        List<WorkflowEdge> outgoing = graph.getOutgoingEdges(currentId);
        if (outgoing.isEmpty()) {
            return List.of();
        }

        // 路由节点：按 sourcePort 筛选
        String routingPort = result.routingPort();
        if (routingPort != null && !routingPort.isBlank()) {
            List<String> routed = outgoing.stream()
                .filter(edge -> routingPort.equalsIgnoreCase(edge.sourcePort())
                    || routingPort.equals(edge.label()))
                .map(WorkflowEdge::target)
                .filter(id -> !executed.contains(id))
                .toList();
            if (!routed.isEmpty()) {
                return routed;
            }
            // 如果没有匹配 sourcePort 的边，尝试第一条边
            String fallback = outgoing.get(0).target();
            return executed.contains(fallback) ? List.of() : List.of(fallback);
        }

        // 普通节点：跟随所有出边
        return outgoing.stream()
            .map(WorkflowEdge::target)
            .filter(id -> !executed.contains(id))
            .toList();
    }

    private ExecutionContext.ExecutionResult createFailedResult(
        ExecutionContext ctx, long duration, String errorMessage
    ) {
        TokenUsageTracker tokenUsage = ctx.getTokenUsage();
        Map<String, Object> output = new HashMap<>();
        output.put("answer", "工作流执行失败：" + errorMessage);

        return new ExecutionContext.ExecutionResult(
            ctx.getExecutionId(),
            ctx.getWorkflowId(),
            ctx.getTrace(),
            ctx.getVariables(),
            output,
            duration,
            false,
            errorMessage,
            tokenUsage
        );
    }

    /**
     * 提取前端传入的即时调试覆盖配置（systemPrompt, mcpTools, models 等），
     * 放入 variables 中以 "debug_" 前缀存储，供执行器读取。
     */
    private void extractDebugOverrides(Map<String, Object> input) {
        moveKey(input, "systemPrompt");
        moveKey(input, "mcpTools");
        moveKey(input, "models");
        moveKey(input, "knowledgeBases");
        moveKey(input, "memoryTypes");
        moveKey(input, "temperature");
        moveKey(input, "topP");
        moveKey(input, "maxTokens");
    }

    private void moveKey(Map<String, Object> input, String key) {
        Object value = input.get(key);
        if (value != null) {
            input.put("debug_" + key, value);
        }
    }

    private static final List<String> INPUT_KEYS = List.of("query", "context", "llmPrompt", "answer", "documents", "toolResult", "conditionMatched");

    /**
     * 捕获节点执行时的关键输入变量快照（用于调试展示）。
     */
    private Map<String, Object> captureNodeInputs(ExecutionContext ctx) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        for (String key : INPUT_KEYS) {
            Object val = ctx.getVariable(key);
            if (val != null) {
                snapshot.put(key, val);
            }
        }
        return snapshot;
    }

    /**
     * 解析节点配置的 inputMappings，将上游节点输出注入到当前节点可访问的变量中。
     * 映射格式: [{ param: "prompt", source: "start.query", enabled: true }]
     */
    @SuppressWarnings("unchecked")
    private void resolveInputMappings(WorkflowNode node, ExecutionContext ctx) {
        Object config = node.configObject("inputMappings");
        if (config == null) return;

        List<Map<String, Object>> mappings = new ArrayList<>();
        if (config instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    mappings.add((Map<String, Object>) map);
                }
            }
        }

        for (Map<String, Object> mapping : mappings) {
            Object enabledObj = mapping.get("enabled");
            boolean enabled = enabledObj == null || Boolean.parseBoolean(String.valueOf(enabledObj));
            if (!enabled) continue;

            String source = String.valueOf(mapping.get("source"));
            String param = String.valueOf(mapping.get("param"));
            if (source.isBlank() || param.isBlank()) continue;

            Object value = ctx.resolveTemplate("{{" + source + "}}");
            if (value != null) {
                ctx.setVariable(param, value);
            }
        }
    }
}

package com.smart.kf.workflow.engine.dag;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.kf.workflow.model.WorkflowEdge;
import com.smart.kf.workflow.model.WorkflowNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 将 JSON 字符串解析为 WorkflowGraph。
 * <p>
 * 兼容遗留节点（无 config 字段），自动补充默认配置。
 */
public class GraphBuilder {

    private static final Logger logger = LoggerFactory.getLogger(GraphBuilder.class);

    private final ObjectMapper objectMapper;

    public GraphBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public WorkflowGraph build(String nodesJson, String edgesJson) {
        List<WorkflowNode> nodes = parseNodes(nodesJson);
        List<WorkflowEdge> edges = parseEdges(edgesJson);
        return new WorkflowGraph(nodes, edges);
    }

    private List<WorkflowNode> parseNodes(String json) {
        if (json == null || json.isBlank()) {
            return defaultNodes();
        }
        try {
            List<RawNode> rawNodes = objectMapper.readValue(json, new TypeReference<>() {});
            List<WorkflowNode> nodes = new ArrayList<>();
            for (RawNode raw : rawNodes) {
                Map<String, Object> config = raw.config;
                if (config == null || config.isEmpty()) {
                    config = defaultConfigForType(raw.type);
                }
                nodes.add(new WorkflowNode(
                    raw.id,
                    raw.type,
                    raw.name != null ? raw.name : raw.type,
                    raw.x != null ? raw.x : 0,
                    raw.y != null ? raw.y : 0,
                    raw.description,
                    config
                ));
            }
            return nodes;
        } catch (Exception e) {
            logger.warn("解析 nodesJson 失败，使用默认节点: {}", e.getMessage());
            return defaultNodes();
        }
    }

    private List<WorkflowEdge> parseEdges(String json) {
        if (json == null || json.isBlank()) {
            return defaultEdges();
        }
        try {
            List<RawEdge> rawEdges = objectMapper.readValue(json, new TypeReference<>() {});
            List<WorkflowEdge> edges = new ArrayList<>();
            for (RawEdge raw : rawEdges) {
                edges.add(new WorkflowEdge(
                    raw.id,
                    raw.source,
                    raw.target,
                    raw.sourcePort,
                    raw.label,
                    raw.condition
                ));
            }
            return edges;
        } catch (Exception e) {
            logger.warn("解析 edgesJson 失败，使用默认边: {}", e.getMessage());
            return defaultEdges();
        }
    }

    // ── 默认工作流 ──

    public static List<WorkflowNode> defaultNodes() {
        return List.of(
            new WorkflowNode("start", "开始", "开始", 80, 140, null, defaultConfigForType("开始")),
            new WorkflowNode("kb", "知识库检索", "知识库检索", 280, 140, null, defaultConfigForType("知识库检索")),
            new WorkflowNode("mcp", "MCP工具", "MCP工具", 480, 140, null, defaultConfigForType("MCP工具")),
            new WorkflowNode("llm", "LLM", "LLM生成", 680, 140, null, defaultConfigForType("LLM")),
            new WorkflowNode("end", "结束", "输出结果", 880, 140, null, defaultConfigForType("结束"))
        );
    }

    public static List<WorkflowEdge> defaultEdges() {
        return List.of(
            WorkflowEdge.of("start", "kb"),
            WorkflowEdge.of("kb", "mcp"),
            WorkflowEdge.of("mcp", "llm"),
            WorkflowEdge.of("llm", "end")
        );
    }

    public static String defaultNodesJson() {
        return """
            [{"id":"start","type":"开始","name":"开始","x":80,"y":140},{"id":"kb","type":"知识库检索","name":"知识库检索","x":280,"y":140},{"id":"mcp","type":"MCP工具","name":"MCP工具","x":480,"y":140},{"id":"llm","type":"LLM","name":"LLM生成","x":680,"y":140},{"id":"end","type":"结束","name":"输出结果","x":880,"y":140}]
            """;
    }

    public static String defaultEdgesJson() {
        return """
            [{"source":"start","target":"kb"},{"source":"kb","target":"mcp"},{"source":"mcp","target":"llm"},{"source":"llm","target":"end"}]
            """;
    }

    /**
     * 为指定节点类型返回默认配置（与前端 defaultNodeConfig 保持一致）。
     */
    public static Map<String, Object> defaultConfigForType(String type) {
        return switch (type) {
            case "开始" -> Map.of("triggerType", "手动触发", "inputSchema", "{\"query\": \"string\"}", "timeout", 300);
            case "结束" -> Map.of("outputMode", "直接输出", "outputTemplate", "{{llm.output}}");
            case "变量" -> Map.of("varName", "", "varType", "string", "varValue", "", "scope", "全局");
            case "条件判断" -> Map.of("conditionExpr", "{{input.type}} == \"A\"", "trueLabel", "是", "falseLabel", "否");
            case "循环" -> Map.of("loopType", "列表循环", "iterateVar", "{{input.items}}", "loopVar", "item", "maxIterations", 100);
            case "延迟" -> Map.of("delayMs", 1000, "delayType", "固定延迟", "unit", "ms");
            case "LLM" -> Map.of("model", "", "temperature", 0.7, "maxTokens", 2048, "topP", 1.0, "systemPrompt", "", "stream", true, "timeout", 60);
            case "知识库检索" -> Map.of("knowledgeBase", "", "searchMode", "混合检索", "topK", 5, "scoreThreshold", 0.5, "rerankEnabled", false);
            case "Prompt模板" -> Map.of("templateId", "", "templateContent", "", "inputVars", "{\"query\": \"{{input.query}}\"}");
            case "Agent调用" -> Map.of("agentId", "", "inputMapping", "{\"query\": \"{{input.query}}\"}", "outputMapping", "{\"result\": \"{{agent.output}}\"}", "timeout", 120);
            case "MCP工具" -> Map.of("toolId", "", "inputMapping", "{\"query\": \"{{input.query}}\"}", "outputField", "result", "retryCount", 1, "timeout", 30);
            case "HTTP请求" -> Map.of("url", "", "method", "GET", "headers", "{\"Content-Type\": \"application/json\"}", "body", "", "authType", "none", "timeout", 30, "retryCount", 1);
            case "SQL查询" -> Map.of("datasource", "", "sql", "SELECT * FROM table WHERE id = {{input.id}}", "resultType", "列表", "maxRows", 100);
            case "Python执行" -> Map.of("code", "# 可使用 input 变量\nresult = input.get(\"query\", \"\")", "requirements", "", "timeout", 30);
            case "代码执行" -> Map.of("language", "JavaScript", "code", "// 可使用 input 变量\nconst result = input.query;\nreturn { result };", "timeout", 30);
            case "审批" -> Map.of("approvers", "", "approvalType", "任一审批", "formFields", "{\"reason\": \"string\"}", "timeout", 86400);
            case "消息通知" -> Map.of("channel", "系统通知", "recipients", "", "title", "", "content", "{{input.message}}", "priority", "普通");
            case "邮件发送" -> Map.of("to", "", "cc", "", "subject", "", "body", "", "bodyType", "html");
            case "Webhook" -> Map.of("url", "", "method", "POST", "headers", "{\"Content-Type\": \"application/json\"}", "payload", "{{input}}", "secret", "", "retryCount", 2);
            case "飞书通知" -> Map.of("webhookUrl", "", "msgType", "text", "title", "", "content", "{{input.message}}", "atAll", false);
            case "企业微信通知" -> Map.of("webhookUrl", "", "msgType", "text", "content", "{{input.message}}", "atUsers", "", "atAll", false);
            default -> Map.of();
        };
    }

    // ── 内部 Jackson 反序列化 DTO ──

    private static class RawNode {
        public String id;
        public String type;
        public String name;
        public Integer x;
        public Integer y;
        public String description;
        public Map<String, Object> config;
    }

    private static class RawEdge {
        public String id;
        public String source;
        public String target;
        public String sourcePort;
        public String label;
        public String condition;
    }
}

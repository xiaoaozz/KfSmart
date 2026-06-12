package com.smart.kf.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.kf.client.ModelClient;
import com.smart.kf.entity.SearchResult;
import com.smart.kf.model.ApiKeyConfig;
import com.smart.kf.model.agent.AgentWorkflow;
import com.smart.kf.model.agent.McpToolConfig;
import com.smart.kf.model.agent.PromptTemplate;
import com.smart.kf.repository.agent.AgentWorkflowRepository;
import com.smart.kf.repository.agent.McpToolConfigRepository;
import com.smart.kf.repository.agent.PromptTemplateRepository;
import com.smart.kf.utils.pagination.PageQuery;
import com.smart.kf.utils.pagination.PageResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class AgentCenterService {
    private final AgentWorkflowRepository workflowRepository;
    private final PromptTemplateRepository promptRepository;
    private final McpToolConfigRepository toolRepository;
    private final HybridSearchService hybridSearchService;
    private final ApiKeyConfigService apiKeyConfigService;
    private final ModelClient modelClient;
    private final ObjectMapper objectMapper;
    private final WebClient webClient;

    public AgentCenterService(
        AgentWorkflowRepository workflowRepository,
        PromptTemplateRepository promptRepository,
        McpToolConfigRepository toolRepository,
        HybridSearchService hybridSearchService,
        ApiKeyConfigService apiKeyConfigService,
        ModelClient modelClient,
        ObjectMapper objectMapper,
        WebClient.Builder webClientBuilder
    ) {
        this.workflowRepository = workflowRepository;
        this.promptRepository = promptRepository;
        this.toolRepository = toolRepository;
        this.hybridSearchService = hybridSearchService;
        this.apiKeyConfigService = apiKeyConfigService;
        this.modelClient = modelClient;
        this.objectMapper = objectMapper;
        this.webClient = webClientBuilder.build();
    }

    public PageResult<AgentWorkflow> listWorkflows(String keyword, PageQuery query) {
        List<AgentWorkflow> source = isBlank(keyword)
            ? workflowRepository.findAll()
            : workflowRepository.findByNameContainingIgnoreCaseOrTypeContainingIgnoreCase(keyword, keyword);
        source.sort(Comparator.comparing(AgentWorkflow::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())));
        return PageResult.fromList(source, query);
    }

    public Map<String, Object> workflowStats() {
        List<AgentWorkflow> workflows = workflowRepository.findAll();
        long agentCount = workflows.size();
        long calls = workflows.stream().mapToLong(AgentWorkflow::getCallCount).sum();
        long success = workflows.stream().mapToLong(AgentWorkflow::getSuccessCount).sum();
        long duration = workflows.stream().mapToLong(w -> w.getAvgDurationMs() * Math.max(1, w.getCallCount())).sum();
        long successRate = calls == 0 ? 100 : Math.round(success * 100.0 / calls);
        long avgDurationMs = calls == 0 ? 0 : Math.round(duration * 1.0 / calls);

        Map<String, Object> stats = new HashMap<>();
        stats.put("agentCount", agentCount);
        stats.put("runCount", calls);
        stats.put("successRate", successRate);
        stats.put("avgDurationMs", avgDurationMs);
        return stats;
    }

    public AgentWorkflow getWorkflow(String workflowId) {
        return workflowRepository.findByWorkflowId(workflowId)
            .orElseThrow(() -> new IllegalArgumentException("工作流不存在"));
    }

    @Transactional
    public AgentWorkflow saveWorkflow(AgentWorkflow request) {
        AgentWorkflow workflow = isBlank(request.getWorkflowId())
            ? new AgentWorkflow()
            : workflowRepository.findByWorkflowId(request.getWorkflowId()).orElse(new AgentWorkflow());
        if (isBlank(workflow.getWorkflowId())) {
            workflow.setWorkflowId("wf_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        }
        applyWorkflow(workflow, request);
        return workflowRepository.save(workflow);
    }

    @Transactional
    public AgentWorkflow copyWorkflow(String workflowId) {
        AgentWorkflow source = getWorkflow(workflowId);
        AgentWorkflow copy = new AgentWorkflow();
        applyWorkflow(copy, source);
        copy.setWorkflowId("wf_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        copy.setName(source.getName() + " 副本");
        copy.setStatus("草稿");
        copy.setCallCount(0);
        copy.setSuccessCount(0);
        copy.setFailureCount(0);
        copy.setPublishedAt(null);
        source.setInstallCount(safeLong(source.getInstallCount()) + 1);
        workflowRepository.save(source);
        return workflowRepository.save(copy);
    }

    @Transactional
    public AgentWorkflow publishWorkflow(String workflowId) {
        AgentWorkflow workflow = getWorkflow(workflowId);
        workflow.setStatus("运行中");
        workflow.setPublishedAt(LocalDateTime.now());
        return workflowRepository.save(workflow);
    }

    @Transactional
    public void deleteWorkflow(String workflowId) {
        workflowRepository.delete(getWorkflow(workflowId));
    }

    @Transactional
    public Map<String, Object> debugWorkflow(String workflowId, Map<String, Object> input, String username) {
        AgentWorkflow workflow = getWorkflow(workflowId);
        List<Map<String, Object>> trace = new ArrayList<>();
        Map<String, Object> variables = new HashMap<>(input == null ? Map.of() : input);
        variables.putIfAbsent("query", "");

        boolean success = true;
        String errorMessage = null;
        long startedAt = System.currentTimeMillis();

        for (WorkflowNode node : sortNodes(workflow)) {
            long nodeStarted = System.currentTimeMillis();
            try {
                executeNode(node, workflow, variables, username);
                trace.add(trace(node.name(), System.currentTimeMillis() - nodeStarted, "success", null));
            } catch (Exception e) {
                success = false;
                errorMessage = e.getMessage();
                trace.add(trace(node.name(), System.currentTimeMillis() - nodeStarted, "error", errorMessage));
                break;
            }
        }

        long duration = System.currentTimeMillis() - startedAt;
        long oldCalls = workflow.getCallCount();
        workflow.setCallCount(oldCalls + 1);
        if (success) {
            workflow.setSuccessCount(workflow.getSuccessCount() + 1);
        } else {
            workflow.setFailureCount(workflow.getFailureCount() + 1);
        }
        workflow.setAvgDurationMs(Math.round((workflow.getAvgDurationMs() * oldCalls + duration) * 1.0 / workflow.getCallCount()));
        workflowRepository.save(workflow);

        Map<String, Object> tokens = new HashMap<>();
        String prompt = String.valueOf(variables.getOrDefault("llmPrompt", variables.getOrDefault("query", "")));
        String answer = String.valueOf(variables.getOrDefault("answer", ""));
        tokens.put("promptTokens", estimateTokens(prompt));
        tokens.put("completionTokens", estimateTokens(answer));
        tokens.put("totalTokens", estimateTokens(prompt) + estimateTokens(answer));
        tokens.put("cost", ((Number) tokens.get("totalTokens")).intValue() * 0.00001);

        Map<String, Object> output = new HashMap<>();
        output.put("answer", variables.getOrDefault("answer", success ? "工作流执行完成" : errorMessage));
        output.put("documents", variables.getOrDefault("documents", List.of()));
        output.put("toolResult", variables.get("toolResult"));

        Map<String, Object> result = new HashMap<>();
        result.put("trace", trace);
        result.put("variables", variables);
        result.put("tokens", tokens);
        result.put("output", output);
        result.put("durationMs", duration);
        result.put("success", success);
        result.put("errorMessage", errorMessage);
        return result;
    }

    public PageResult<PromptTemplate> listPrompts(String keyword, PageQuery query) {
        List<PromptTemplate> source = isBlank(keyword)
            ? promptRepository.findAll()
            : promptRepository.findByNameContainingIgnoreCaseOrCategoryContainingIgnoreCase(keyword, keyword);
        source.sort(Comparator.comparing(PromptTemplate::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())));
        return PageResult.fromList(source, query);
    }

    public PromptTemplate savePrompt(PromptTemplate request) {
        PromptTemplate prompt = isBlank(request.getTemplateId())
            ? new PromptTemplate()
            : promptRepository.findByTemplateId(request.getTemplateId()).orElse(new PromptTemplate());
        if (isBlank(prompt.getTemplateId())) {
            prompt.setTemplateId("pt_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        }
        prompt.setName(request.getName());
        prompt.setCategory(request.getCategory());
        prompt.setVersion(isBlank(request.getVersion()) ? "v1.0" : request.getVersion());
        prompt.setContent(request.getContent());
        prompt.setVariables(request.getVariables());
        prompt.setStatus(isBlank(request.getStatus()) ? "启用" : request.getStatus());
        return promptRepository.save(prompt);
    }

    public PageResult<Map<String, Object>> listTools(String keyword, PageQuery query) {
        List<McpToolConfig> source = isBlank(keyword)
            ? toolRepository.findAll()
            : toolRepository.findByNameContainingIgnoreCaseOrTypeContainingIgnoreCase(keyword, keyword);
        source.sort(Comparator.comparing(McpToolConfig::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())));
        return PageResult.fromList(source.stream().map(this::toToolResponse).toList(), query);
    }

    public Map<String, Object> saveTool(McpToolConfig request) {
        McpToolConfig tool = isBlank(request.getToolId())
            ? new McpToolConfig()
            : toolRepository.findByToolId(request.getToolId()).orElse(new McpToolConfig());
        if (isBlank(tool.getToolId())) {
            tool.setToolId("mcp_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        }
        tool.setName(request.getName());
        tool.setType(isBlank(request.getType()) ? "MCP" : request.getType());
        tool.setStatus(isBlank(request.getStatus()) ? "在线" : request.getStatus());
        tool.setEndpoint(request.getEndpoint());
        tool.setAuthType(request.getAuthType());
        if (!isBlank(request.getApiKey()) && !request.getApiKey().contains("****")) {
            tool.setApiKey(request.getApiKey());
        }
        tool.setDescription(request.getDescription());
        return toToolResponse(toolRepository.save(tool));
    }

    @Transactional
    public void deletePrompt(String templateId) {
        promptRepository.delete(promptRepository.findByTemplateId(templateId)
            .orElseThrow(() -> new IllegalArgumentException("Prompt 模板不存在")));
    }

    public Map<String, Object> runAnalysis() {
        List<AgentWorkflow> workflows = workflowRepository.findAll();
        long calls = workflows.stream().mapToLong(AgentWorkflow::getCallCount).sum();
        long success = workflows.stream().mapToLong(AgentWorkflow::getSuccessCount).sum();
        long failures = workflows.stream().mapToLong(AgentWorkflow::getFailureCount).sum();

        Map<String, Object> result = workflowStats();
        result.put("failureRate", calls == 0 ? 0 : Math.round(failures * 100.0 / calls));
        result.put("successRate", calls == 0 ? 100 : Math.round(success * 100.0 / calls));
        result.put("hotAgents", workflows.stream()
            .sorted(Comparator.comparingLong(AgentWorkflow::getCallCount).reversed())
            .limit(5)
            .map(item -> Map.of("name", item.getName(), "callCount", item.getCallCount()))
            .toList());
        result.put("cost", Map.of("tokenUsage", calls * 1280, "modelCost", calls * 0.0128, "toolCost", calls * 0.004));
        return result;
    }

    public List<Map<String, Object>> marketplace() {
        return workflowRepository.findAll().stream()
            .filter(item -> "运行中".equals(item.getStatus()))
            .map(item -> {
                Map<String, Object> row = new HashMap<>();
                row.put("workflowId", item.getWorkflowId());
                row.put("name", item.getName());
                row.put("category", item.getType());
                row.put("description", item.getDescription());
                row.put("installCount", safeLong(item.getInstallCount()));
                row.put("tags", item.getTags());
                return row;
            })
            .toList();
    }

    public List<Map<String, Object>> listModels() {
        return apiKeyConfigService.listAll().stream().map(item -> {
            Map<String, Object> row = new HashMap<>(item);
            row.put("status", Boolean.TRUE.equals(item.get("active")) ? "激活中" : "可用");
            row.put("scene", item.getOrDefault("remark", ""));
            return row;
        }).toList();
    }

    @Transactional
    public void deleteTool(String toolId) {
        toolRepository.delete(toolRepository.findByToolId(toolId)
            .orElseThrow(() -> new IllegalArgumentException("MCP 工具不存在")));
    }

    private void applyWorkflow(AgentWorkflow target, AgentWorkflow source) {
        target.setName(source.getName());
        target.setDescription(source.getDescription());
        target.setType(isBlank(source.getType()) ? "工作流" : source.getType());
        target.setStatus(isBlank(source.getStatus()) ? "草稿" : source.getStatus());
        target.setOwnerName(source.getOwnerName());
        target.setTags(source.getTags());
        target.setPermissionScope(isBlank(source.getPermissionScope()) ? "组织内" : source.getPermissionScope());
        target.setKnowledgeBases(source.getKnowledgeBases());
        target.setPromptRefs(source.getPromptRefs());
        target.setMcpTools(source.getMcpTools());
        target.setModels(source.getModels());
        target.setNodesJson(isBlank(source.getNodesJson()) ? defaultNodes() : source.getNodesJson());
        target.setEdgesJson(isBlank(source.getEdgesJson()) ? defaultEdges() : source.getEdgesJson());
    }

    private Map<String, Object> trace(String name, long durationMs, String status, String errorMessage) {
        Map<String, Object> row = new HashMap<>();
        row.put("name", name);
        row.put("durationMs", durationMs);
        row.put("status", status);
        row.put("errorMessage", errorMessage);
        return row;
    }

    private String defaultNodes() {
        return """
            [{"id":"start","type":"开始","name":"开始","x":120,"y":120},{"id":"kb","type":"知识库检索","name":"知识库检索","x":330,"y":120},{"id":"mcp","type":"MCP工具","name":"MCP工具","x":540,"y":120},{"id":"llm","type":"LLM","name":"LLM生成","x":750,"y":120},{"id":"end","type":"结束","name":"输出结果","x":960,"y":120}]
            """;
    }

    private String defaultEdges() {
        return """
            [{"source":"start","target":"kb"},{"source":"kb","target":"mcp"},{"source":"mcp","target":"llm"},{"source":"llm","target":"end"}]
            """;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private long safeLong(Long value) {
        return value == null ? 0L : value;
    }

    private Map<String, Object> toToolResponse(McpToolConfig tool) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", tool.getId());
        row.put("toolId", tool.getToolId());
        row.put("name", tool.getName());
        row.put("type", tool.getType());
        row.put("status", tool.getStatus());
        row.put("endpoint", tool.getEndpoint());
        row.put("authType", tool.getAuthType());
        row.put("apiKeyMasked", maskApiKey(tool.getApiKey()));
        row.put("description", tool.getDescription());
        row.put("callCount", tool.getCallCount());
        row.put("createdAt", tool.getCreatedAt());
        row.put("updatedAt", tool.getUpdatedAt());
        return row;
    }

    private String maskApiKey(String value) {
        if (isBlank(value)) {
            return "";
        }
        if (value.contains("****")) {
            return value;
        }
        return value.length() <= 8 ? "****" : value.substring(0, 3) + "****" + value.substring(value.length() - 4);
    }

    private List<WorkflowNode> sortNodes(AgentWorkflow workflow) {
        List<WorkflowNode> nodes = readNodes(workflow.getNodesJson());
        List<WorkflowEdge> edges = readEdges(workflow.getEdgesJson());
        if (nodes.isEmpty()) {
            return readNodes(defaultNodes());
        }
        if (edges.isEmpty()) {
            return nodes;
        }

        Map<String, WorkflowNode> byId = new HashMap<>();
        nodes.forEach(node -> byId.put(node.id(), node));
        List<WorkflowNode> sorted = new ArrayList<>();
        String current = nodes.stream()
            .filter(node -> node.type().contains("开始"))
            .map(WorkflowNode::id)
            .findFirst()
            .orElse(nodes.get(0).id());
        while (current != null && byId.containsKey(current) && missingNode(sorted, current)) {
            sorted.add(byId.get(current));
            String next = null;
            for (WorkflowEdge edge : edges) {
                if (edge.source().equals(current)) {
                    next = edge.target();
                    break;
                }
            }
            current = next;
        }
        for (WorkflowNode node : nodes) {
            if (missingNode(sorted, node.id())) {
                sorted.add(node);
            }
        }
        return sorted;
    }

    private boolean missingNode(List<WorkflowNode> nodes, String nodeId) {
        for (WorkflowNode node : nodes) {
            if (node.id().equals(nodeId)) {
                return false;
            }
        }
        return true;
    }

    private void executeNode(WorkflowNode node, AgentWorkflow workflow, Map<String, Object> variables, String username) {
        String type = node.type();
        if (type.contains("开始") || type.contains("变量")) {
            variables.putIfAbsent("query", "");
            return;
        }
        if (type.contains("知识库")) {
            String query = String.valueOf(variables.getOrDefault("query", ""));
            List<SearchResult> documents = hybridSearchService.searchWithPermission(query, username, 5);
            variables.put("documents", documents);
            variables.put("context", documents.stream()
                .map(doc -> "来源：" + (doc.getFileName() == null ? doc.getFileMd5() : doc.getFileName()) + "\n" + doc.getTextContent())
                .toList());
            return;
        }
        if (type.contains("Prompt")) {
            variables.put("llmPrompt", renderPrompt(workflow, variables));
            return;
        }
        if (type.contains("MCP") || type.contains("HTTP")) {
            variables.put("toolResult", executeTool(workflow, variables));
            return;
        }
        if (type.contains("LLM")) {
            Optional<ApiKeyConfig> activeConfig = apiKeyConfigService.getActiveConfig();
            if (activeConfig.isEmpty()) {
                throw new IllegalStateException("未配置激活模型，请先在模型管理/API Key 管理中激活模型");
            }
            String query = String.valueOf(variables.getOrDefault("llmPrompt", variables.getOrDefault("query", "")));
            String context = String.join("\n\n", ((List<?>) variables.getOrDefault("context", List.of())).stream().map(String::valueOf).toList());
            String answer = modelClient.chat(query, context, List.of(), activeConfig.get());
            variables.put("answer", answer);
            return;
        }
        if (type.contains("条件")) {
            variables.put("conditionMatched", String.valueOf(variables.getOrDefault("query", "")).contains("请假"));
            return;
        }
        if (type.contains("结束") || type.contains("输出")) {
            variables.putIfAbsent("answer", variables.getOrDefault("toolResult", "工作流执行完成"));
        }
    }

    private Object executeTool(AgentWorkflow workflow, Map<String, Object> variables) {
        String configuredName = workflow.getMcpTools();
        McpToolConfig tool = toolRepository.findAll().stream()
            .filter(item -> isBlank(configuredName) || item.getName().equals(configuredName) || configuredName.contains(item.getName()))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("未配置可用 MCP 工具"));
        if (isBlank(tool.getEndpoint())) {
            throw new IllegalStateException("MCP 工具 Endpoint 未配置");
        }
        Object result = webClient.post()
            .uri(tool.getEndpoint())
            .headers(headers -> {
                if (!isBlank(tool.getApiKey())) {
                    String authType = tool.getAuthType() == null ? "" : tool.getAuthType().toLowerCase();
                    if (authType.contains("api key")) {
                        headers.set("X-API-Key", tool.getApiKey());
                    } else if (authType.contains("bearer")) {
                        headers.setBearerAuth(tool.getApiKey());
                    }
                }
            })
            .bodyValue(variables)
            .retrieve()
            .bodyToMono(Object.class)
            .block();
        tool.setCallCount(tool.getCallCount() + 1);
        toolRepository.save(tool);
        return result;
    }

    private String renderPrompt(AgentWorkflow workflow, Map<String, Object> variables) {
        String promptName = workflow.getPromptRefs();
        PromptTemplate template = promptRepository.findAll().stream()
            .filter(item -> isBlank(promptName) || item.getName().equals(promptName) || promptName.contains(item.getName()))
            .findFirst()
            .orElse(null);
        String content = template == null ? "{{query}}\n\n{{context}}" : template.getContent();
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            content = content.replace("{{" + entry.getKey() + "}}", String.valueOf(entry.getValue()));
        }
        content = content.replace("{{input.query}}", String.valueOf(variables.getOrDefault("query", "")));
        return content;
    }

    private int estimateTokens(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return Math.max(1, (int) Math.ceil(text.length() / 4.0));
    }

    private List<WorkflowNode> readNodes(String json) {
        try {
            return objectMapper.readValue(isBlank(json) ? defaultNodes() : json, new TypeReference<>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<WorkflowEdge> readEdges(String json) {
        try {
            return objectMapper.readValue(isBlank(json) ? defaultEdges() : json, new TypeReference<>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private record WorkflowNode(String id, String type, String name, int x, int y) {
    }

    private record WorkflowEdge(String source, String target) {
    }
}

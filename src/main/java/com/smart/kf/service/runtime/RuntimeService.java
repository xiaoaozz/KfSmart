package com.smart.kf.service.runtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.kf.model.agent.Agent;
import com.smart.kf.model.workflow.Workflow;
import com.smart.kf.repository.agent.AgentRepository;
import com.smart.kf.repository.workflow.WorkflowRepository;
import com.smart.kf.service.ChatHandler;
import com.smart.kf.service.agent.AgentExecutionService;
import com.smart.kf.service.workflow.WorkflowService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class RuntimeService {

    private final AgentRepository agentRepository;
    private final WorkflowRepository workflowRepository;
    private final AgentExecutionService agentExecutionService;
    private final WorkflowService workflowService;
    private final ChatHandler chatHandler;
    private final ObjectMapper objectMapper;

    public RuntimeService(
            AgentRepository agentRepository,
            WorkflowRepository workflowRepository,
            AgentExecutionService agentExecutionService,
            WorkflowService workflowService,
            ChatHandler chatHandler,
            ObjectMapper objectMapper
    ) {
        this.agentRepository = agentRepository;
        this.workflowRepository = workflowRepository;
        this.agentExecutionService = agentExecutionService;
        this.workflowService = workflowService;
        this.chatHandler = chatHandler;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> getCatalog() {
        List<Map<String, Object>> agents = agentRepository.findAll().stream()
                .filter(this::isPublishedAgent)
                .sorted(Comparator.comparing(Agent::getPublishedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(Agent::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(this::toAgentCard)
                .toList();

        List<Map<String, Object>> workflows = workflowRepository.findAll().stream()
                .filter(this::isPublishedWorkflow)
                .sorted(Comparator.comparing(Workflow::getPublishedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(Workflow::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(this::toWorkflowCard)
                .toList();

        Map<String, Object> result = new HashMap<>();
        result.put("agents", agents);
        result.put("workflows", workflows);
        result.put("agentCount", agents.size());
        result.put("workflowCount", workflows.size());
        return result;
    }

    public Map<String, Object> execute(String username, Map<String, Object> request) {
        String targetType = stringValue(request.get("targetType"));
        String targetId = stringValue(request.get("targetId"));
        String message = stringValue(request.get("message"));
        String conversationId = stringValue(request.get("conversationId"));

        if (targetType.isBlank() || targetId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请先选择要运行的 Agent 或 Workflow");
        }
        if (message.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请输入运行内容");
        }

        Map<String, Object> target = getTargetMeta(targetType, targetId);
        if (conversationId.isBlank()) {
            Map<String, Object> session = chatHandler.createConversationSession(username, buildSessionMeta(targetType, target));
            conversationId = String.valueOf(session.get("id"));
        }

        Map<String, Object> sessionMeta = chatHandler.getConversationSessionMeta(username, conversationId);
        if (sessionMeta.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "运行会话不存在");
        }
        validateSessionBinding(sessionMeta, targetType, targetId);

        try {
            Map<String, Object> execution = executeByType(targetType, targetId, message, username, conversationId);
            String answer = stringValue(execution.get("displayContent"));
            chatHandler.appendConversationTurn(username, conversationId, message, answer);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("conversationId", conversationId);
            result.put("session", chatHandler.getConversationSessionMeta(username, conversationId));
            result.put("execution", execution);
            return result;
        } catch (Exception e) {
            chatHandler.appendConversationError(username, conversationId, message, e.getMessage());
            throw e;
        }
    }

    private Map<String, Object> executeByType(String targetType, String targetId, String message, String username, String conversationId) {
        if ("agent".equals(targetType)) {
            List<Map<String, Object>> historyMessages = chatHandler.getConversationMessages(username, conversationId);
            List<Map<String, String>> history = historyMessages.stream()
                    .filter(item -> "user".equals(item.get("role")) || "assistant".equals(item.get("role")))
                    .map(item -> Map.of(
                            "role", String.valueOf(item.get("role")),
                            "content", String.valueOf(item.getOrDefault("content", ""))
                    ))
                    .toList();

            Map<String, Object> execution = agentExecutionService.chat(targetId, message, history, Map.of(), username);
            execution.put("displayContent", stringValue(execution.get("answer")));
            execution.put("targetType", targetType);
            execution.put("targetId", targetId);
            return execution;
        }

        if ("workflow".equals(targetType)) {
            Map<String, Object> input = new LinkedHashMap<>();
            input.put("query", message);
            Map<String, Object> execution = workflowService.debugWorkflow(targetId, input, username);
            execution.put("displayContent", buildWorkflowDisplayContent(execution));
            execution.put("targetType", targetType);
            execution.put("targetId", targetId);
            return execution;
        }

        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的运行类型: " + targetType);
    }

    private String buildWorkflowDisplayContent(Map<String, Object> execution) {
        Object output = execution.get("output");
        if (output == null) {
            String errorMessage = stringValue(execution.get("errorMessage"));
            return errorMessage.isBlank() ? "工作流执行完成，但没有返回结果。" : errorMessage;
        }

        if (output instanceof Map<?, ?> outputMap) {
            Object answer = outputMap.get("answer");
            if (answer != null && !String.valueOf(answer).isBlank()) {
                return String.valueOf(answer);
            }
        }

        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(output);
        } catch (JsonProcessingException e) {
            return String.valueOf(output);
        }
    }

    private Map<String, Object> getTargetMeta(String targetType, String targetId) {
        if ("agent".equals(targetType)) {
            Agent agent = agentRepository.findByAgentId(targetId)
                    .filter(this::isPublishedAgent)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Agent 不存在或未发布"));
            return toAgentCard(agent);
        }

        if ("workflow".equals(targetType)) {
            Workflow workflow = workflowRepository.findByWorkflowId(targetId)
                    .filter(this::isPublishedWorkflow)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workflow 不存在或未发布"));
            return toWorkflowCard(workflow);
        }

        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的运行类型: " + targetType);
    }

    private Map<String, Object> buildSessionMeta(String targetType, Map<String, Object> target) {
        Map<String, Object> meta = new HashMap<>();
        meta.put("sessionType", "runtime");
        meta.put("targetType", targetType);
        meta.put("targetId", stringValue(target.get("id")));
        meta.put("targetName", stringValue(target.get("name")));
        meta.put("targetDescription", stringValue(target.get("description")));
        return meta;
    }

    private void validateSessionBinding(Map<String, Object> sessionMeta, String targetType, String targetId) {
        if (!"runtime".equals(stringValue(sessionMeta.get("sessionType")))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前会话不是运行会话");
        }
        if (!targetType.equals(stringValue(sessionMeta.get("targetType")))
                || !targetId.equals(stringValue(sessionMeta.get("targetId")))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前会话与所选运行对象不匹配");
        }
    }

    private boolean isPublishedAgent(Agent agent) {
        return agent.getPublishedAt() != null || "运行中".equals(agent.getStatus());
    }

    private boolean isPublishedWorkflow(Workflow workflow) {
        return workflow.getPublishedAt() != null || "运行中".equals(workflow.getStatus());
    }

    private Map<String, Object> toAgentCard(Agent agent) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", agent.getAgentId());
        item.put("type", "agent");
        item.put("name", agent.getName());
        item.put("description", agent.getDescription());
        item.put("status", agent.getStatus());
        item.put("ownerName", agent.getOwnerName());
        item.put("tags", agent.getTags());
        item.put("models", agent.getModels());
        item.put("avatarEmoji", agent.getAvatarEmoji());
        item.put("callCount", agent.getCallCount());
        item.put("successRate", agent.getSuccessRate());
        item.put("publishedAt", agent.getPublishedAt());
        item.put("updatedAt", agent.getUpdatedAt());
        return item;
    }

    private Map<String, Object> toWorkflowCard(Workflow workflow) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", workflow.getWorkflowId());
        item.put("type", "workflow");
        item.put("name", workflow.getName());
        item.put("description", workflow.getDescription());
        item.put("status", workflow.getStatus());
        item.put("ownerName", workflow.getOwnerName());
        item.put("tags", workflow.getTags());
        item.put("models", workflow.getModels());
        item.put("callCount", workflow.getCallCount());
        item.put("successRate", workflow.getSuccessRate());
        item.put("publishedAt", workflow.getPublishedAt());
        item.put("updatedAt", workflow.getUpdatedAt());
        return item;
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}

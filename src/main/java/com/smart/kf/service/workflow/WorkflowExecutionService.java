package com.smart.kf.service.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.kf.model.workflow.Workflow;
import com.smart.kf.model.workflow.WorkflowExecutionLog;
import com.smart.kf.repository.workflow.WorkflowExecutionLogRepository;
import com.smart.kf.repository.workflow.WorkflowRepository;
import com.smart.kf.workflow.engine.ExecutionContext;
import com.smart.kf.workflow.engine.WorkflowExecutionEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Service
public class WorkflowExecutionService {

    private static final Logger logger = LoggerFactory.getLogger(WorkflowExecutionService.class);

    private final WorkflowExecutionEngine executionEngine;
    private final WorkflowExecutionLogRepository logRepository;
    private final WorkflowRepository workflowRepository;
    private final ObjectMapper objectMapper;
    private final com.smart.kf.handler.WorkflowProgressBroadcaster broadcaster;

    public WorkflowExecutionService(
        WorkflowExecutionEngine executionEngine,
        WorkflowExecutionLogRepository logRepository,
        WorkflowRepository workflowRepository,
        ObjectMapper objectMapper,
        com.smart.kf.handler.WorkflowProgressBroadcaster broadcaster
    ) {
        this.executionEngine = executionEngine;
        this.logRepository = logRepository;
        this.workflowRepository = workflowRepository;
        this.objectMapper = objectMapper;
        this.broadcaster = broadcaster;
    }

    @Transactional
    public Map<String, Object> executeSync(String workflowId, Map<String, Object> input, String username) {
        Workflow workflow = workflowRepository.findByWorkflowId(workflowId)
            .orElseThrow(() -> new IllegalArgumentException("工作流不存在"));

        ExecutionContext.ExecutionResult result = executionEngine.execute(
            workflow.getNodesJson(),
            workflow.getEdgesJson(),
            workflowId,
            username,
            input
        );

        updateWorkflowStats(workflow, result);
        saveExecutionLog(result, workflowId, username, "sync_debug", input);

        return buildResponse(result);
    }

    public String executeAsync(String workflowId, Map<String, Object> input, String username) {
        Workflow workflow = workflowRepository.findByWorkflowId(workflowId)
            .orElseThrow(() -> new IllegalArgumentException("工作流不存在"));

        String executionId = "exec_" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 12);

        WorkflowExecutionLog log = new WorkflowExecutionLog();
        log.setExecutionId(executionId);
        log.setWorkflowId(workflowId);
        log.setTriggerType("async_run");
        log.setStatus("running");
        log.setStartedBy(username);
        try {
            log.setInputJson(objectMapper.writeValueAsString(input));
        } catch (Exception ignored) {}
        logRepository.save(log);

        final String nodesJson = workflow.getNodesJson();
        final String edgesJson = workflow.getEdgesJson();

        new Thread(() -> {
            try {
                ExecutionContext.ExecutionResult result = executionEngine.execute(
                    nodesJson, edgesJson, workflowId, username, input,
                    trace -> {
                        try {
                            broadcaster.broadcast(executionId, objectMapper.writeValueAsString(Map.of(
                                "type", "node_completed",
                                "executionId", executionId,
                                "node", Map.of(
                                    "nodeId", trace.nodeId(),
                                    "nodeName", trace.nodeName(),
                                    "nodeType", trace.nodeType(),
                                    "status", trace.status(),
                                    "durationMs", trace.durationMs(),
                                    "errorMessage", trace.errorMessage() != null ? trace.errorMessage() : ""
                                )
                            )));
                        } catch (Exception ignored) {}
                    }
                );
                updateExecutionLog(executionId, result);

                broadcaster.broadcast(executionId, "{\"type\":\"execution_completed\",\"executionId\":\"" + executionId + "\",\"success\":" + result.success() + "}");
            } catch (Exception e) {
                logger.error("异步执行失败: {}", e.getMessage(), e);
                broadcaster.broadcast(executionId, "{\"type\":\"execution_failed\",\"executionId\":\"" + executionId + "\",\"error\":\"" + e.getMessage() + "\"}");
            }
        }).start();

        return executionId;
    }

    public WorkflowExecutionLog getExecutionLog(String executionId) {
        return logRepository.findByExecutionId(executionId).orElse(null);
    }

    public Page<WorkflowExecutionLog> listExecutionLogs(String workflowId, int page, int size) {
        return logRepository.findByWorkflowIdOrderByStartedAtDesc(
            workflowId,
            PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "startedAt"))
        );
    }

    public ExecutionContext.ExecutionResult execute(
        String nodesJson, String edgesJson, String workflowId, String username, Map<String, Object> input
    ) {
        return executionEngine.execute(nodesJson, edgesJson, workflowId, username, input);
    }

    private void updateWorkflowStats(Workflow workflow, ExecutionContext.ExecutionResult result) {
        long oldCalls = workflow.getCallCount();
        workflow.setCallCount(oldCalls + 1);
        if (result.success()) {
            workflow.setSuccessCount(workflow.getSuccessCount() + 1);
        } else {
            workflow.setFailureCount(workflow.getFailureCount() + 1);
        }
        workflow.setAvgDurationMs(Math.round(
            (workflow.getAvgDurationMs() * oldCalls + result.durationMs()) * 1.0 / workflow.getCallCount()
        ));
        workflowRepository.save(workflow);
    }

    public void saveExecutionLog(ExecutionContext.ExecutionResult result, String workflowId,
                                   String username, String triggerType, Map<String, Object> input) {
        try {
            WorkflowExecutionLog log = new WorkflowExecutionLog();
            log.setExecutionId(result.executionId());
            log.setWorkflowId(workflowId);
            log.setTriggerType(triggerType);
            log.setStatus(result.success() ? "success" : "failed");
            log.setStartedBy(username);
            log.setDurationMs(result.durationMs());
            log.setPromptTokens(result.tokenUsage().getPromptTokens());
            log.setCompletionTokens(result.tokenUsage().getCompletionTokens());
            log.setTotalTokens(result.tokenUsage().getTotalTokens());
            log.setCost(BigDecimal.valueOf(result.tokenUsage().getCost()));
            log.setCompletedAt(LocalDateTime.now());
            log.setErrorMessage(result.errorMessage());

            try {
                log.setInputJson(objectMapper.writeValueAsString(input));
                log.setTraceJson(objectMapper.writeValueAsString(result.trace()));
                log.setOutputJson(objectMapper.writeValueAsString(result.output()));
                log.setVariablesJson(objectMapper.writeValueAsString(result.variables()));
            } catch (Exception ignored) {}

            logRepository.save(log);
        } catch (Exception e) {
            logger.warn("保存执行日志失败: {}", e.getMessage());
        }
    }

    private void updateExecutionLog(String executionId, ExecutionContext.ExecutionResult result) {
        logRepository.findByExecutionId(executionId).ifPresent(log -> {
            log.setStatus(result.success() ? "success" : "failed");
            log.setDurationMs(result.durationMs());
            log.setPromptTokens(result.tokenUsage().getPromptTokens());
            log.setCompletionTokens(result.tokenUsage().getCompletionTokens());
            log.setTotalTokens(result.tokenUsage().getTotalTokens());
            log.setCost(BigDecimal.valueOf(result.tokenUsage().getCost()));
            log.setCompletedAt(LocalDateTime.now());
            log.setErrorMessage(result.errorMessage());

            try {
                log.setTraceJson(objectMapper.writeValueAsString(result.trace()));
                log.setOutputJson(objectMapper.writeValueAsString(result.output()));
                log.setVariablesJson(objectMapper.writeValueAsString(result.variables()));
            } catch (Exception ignored) {}

            logRepository.save(log);
        });
    }

    private Map<String, Object> buildResponse(ExecutionContext.ExecutionResult result) {
        return Map.of(
            "executionId", result.executionId(),
            "trace", result.trace(),
            "variables", result.variables(),
            "output", result.output(),
            "durationMs", result.durationMs(),
            "success", result.success(),
            "errorMessage", result.errorMessage() != null ? result.errorMessage() : "",
            "tokens", Map.of(
                "promptTokens", result.tokenUsage().getPromptTokens(),
                "completionTokens", result.tokenUsage().getCompletionTokens(),
                "totalTokens", result.tokenUsage().getTotalTokens(),
                "cost", result.tokenUsage().getCost()
            )
        );
    }
}

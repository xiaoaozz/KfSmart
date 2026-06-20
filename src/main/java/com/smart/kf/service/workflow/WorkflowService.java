package com.smart.kf.service.workflow;

import com.smart.kf.model.workflow.Workflow;
import com.smart.kf.repository.workflow.WorkflowRepository;
import com.smart.kf.utils.pagination.PageQuery;
import com.smart.kf.utils.pagination.PageResult;
import com.smart.kf.workflow.engine.ExecutionContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class WorkflowService {

    private final WorkflowRepository workflowRepository;
    private final WorkflowExecutionService executionService;
    private final WorkflowVersionService versionService;

    public WorkflowService(
        WorkflowRepository workflowRepository,
        WorkflowExecutionService executionService,
        WorkflowVersionService versionService
    ) {
        this.workflowRepository = workflowRepository;
        this.executionService = executionService;
        this.versionService = versionService;
    }

    public PageResult<Workflow> listWorkflows(String keyword, PageQuery query) {
        List<Workflow> source = isBlank(keyword)
            ? workflowRepository.findAll()
            : workflowRepository.findByNameContainingIgnoreCase(keyword);
        source.sort(Comparator.comparing(Workflow::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())));
        return PageResult.fromList(source, query);
    }

    public Map<String, Object> workflowStats() {
        List<Workflow> workflows = workflowRepository.findAll();
        long workflowCount = workflows.size();
        long calls = workflows.stream().mapToLong(Workflow::getCallCount).sum();
        long success = workflows.stream().mapToLong(Workflow::getSuccessCount).sum();
        long duration = workflows.stream().mapToLong(w -> w.getAvgDurationMs() * Math.max(1, w.getCallCount())).sum();
        long successRate = calls == 0 ? 100 : Math.round(success * 100.0 / calls);
        long avgDurationMs = calls == 0 ? 0 : Math.round(duration * 1.0 / calls);

        Map<String, Object> stats = new HashMap<>();
        stats.put("workflowCount", workflowCount);
        stats.put("runCount", calls);
        stats.put("successRate", successRate);
        stats.put("avgDurationMs", avgDurationMs);
        return stats;
    }

    public Workflow getWorkflow(String workflowId) {
        return workflowRepository.findByWorkflowId(workflowId)
            .orElseThrow(() -> new IllegalArgumentException("工作流不存在"));
    }

    @Transactional
    public Workflow saveWorkflow(Workflow request) {
        Workflow workflow = isBlank(request.getWorkflowId())
            ? new Workflow()
            : workflowRepository.findByWorkflowId(request.getWorkflowId()).orElse(new Workflow());
        boolean isNew = isBlank(workflow.getWorkflowId());
        if (isNew) {
            workflow.setWorkflowId("wf_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        }
        applyWorkflow(workflow, request);
        Workflow saved = workflowRepository.save(workflow);
        if (!isNew) {
            versionService.createVersion(saved, "system", "编辑保存");
        }
        return saved;
    }

    @Transactional
    public Workflow copyWorkflow(String workflowId) {
        Workflow source = getWorkflow(workflowId);
        Workflow copy = new Workflow();
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
    public Workflow publishWorkflow(String workflowId) {
        Workflow workflow = getWorkflow(workflowId);
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
        Workflow workflow = getWorkflow(workflowId);

        ExecutionContext.ExecutionResult execResult = executionService.execute(
            workflow.getNodesJson(),
            workflow.getEdgesJson(),
            workflowId,
            username,
            input
        );

        long duration = execResult.durationMs();
        boolean success = execResult.success();

        long oldCalls = workflow.getCallCount();
        workflow.setCallCount(oldCalls + 1);
        if (success) {
            workflow.setSuccessCount(workflow.getSuccessCount() + 1);
        } else {
            workflow.setFailureCount(workflow.getFailureCount() + 1);
        }
        workflow.setAvgDurationMs(Math.round((workflow.getAvgDurationMs() * oldCalls + duration) * 1.0 / workflow.getCallCount()));
        workflowRepository.save(workflow);

        executionService.saveExecutionLog(execResult, workflowId, username, "debug", input);

        Map<String, Object> tokens = new HashMap<>();
        tokens.put("promptTokens", execResult.tokenUsage().getPromptTokens());
        tokens.put("completionTokens", execResult.tokenUsage().getCompletionTokens());
        tokens.put("totalTokens", execResult.tokenUsage().getTotalTokens());
        tokens.put("cost", execResult.tokenUsage().getCost());

        List<Map<String, Object>> traceList = new ArrayList<>();
        for (var nodeTrace : execResult.trace()) {
            Map<String, Object> t = new HashMap<>();
            t.put("name", nodeTrace.nodeName());
            t.put("nodeId", nodeTrace.nodeId());
            t.put("type", nodeTrace.nodeType());
            t.put("durationMs", nodeTrace.durationMs());
            t.put("status", nodeTrace.status());
            t.put("errorMessage", nodeTrace.errorMessage());
            traceList.add(t);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("trace", traceList);
        result.put("variables", execResult.variables());
        result.put("tokens", tokens);
        result.put("output", execResult.output());
        result.put("durationMs", duration);
        result.put("success", success);
        result.put("errorMessage", execResult.errorMessage());
        result.put("executionId", execResult.executionId());
        return result;
    }

    private void applyWorkflow(Workflow target, Workflow source) {
        target.setName(source.getName());
        target.setDescription(source.getDescription());
        target.setStatus(isBlank(source.getStatus()) ? "草稿" : source.getStatus());
        target.setOwnerName(source.getOwnerName());
        target.setTags(source.getTags());
        target.setPermissionScope(isBlank(source.getPermissionScope()) ? "组织内" : source.getPermissionScope());
        target.setKnowledgeBases(source.getKnowledgeBases());
        target.setPromptRefs(source.getPromptRefs());
        target.setMcpTools(source.getMcpTools());
        target.setSkillRefs(source.getSkillRefs());
        target.setModels(source.getModels());
        target.setNodesJson(isBlank(source.getNodesJson()) ? com.smart.kf.workflow.engine.dag.GraphBuilder.defaultNodesJson() : source.getNodesJson());
        target.setEdgesJson(isBlank(source.getEdgesJson()) ? com.smart.kf.workflow.engine.dag.GraphBuilder.defaultEdgesJson() : source.getEdgesJson());
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private long safeLong(Long value) {
        return value == null ? 0L : value;
    }
}

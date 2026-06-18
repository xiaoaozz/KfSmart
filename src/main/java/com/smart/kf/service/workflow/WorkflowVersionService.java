package com.smart.kf.service.workflow;

import com.smart.kf.model.workflow.Workflow;
import com.smart.kf.model.workflow.WorkflowVersion;
import com.smart.kf.repository.workflow.WorkflowRepository;
import com.smart.kf.repository.workflow.WorkflowVersionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class WorkflowVersionService {

    private static final Logger logger = LoggerFactory.getLogger(WorkflowVersionService.class);

    private final WorkflowVersionRepository versionRepository;
    private final WorkflowRepository workflowRepository;

    public WorkflowVersionService(WorkflowVersionRepository versionRepository,
                                  WorkflowRepository workflowRepository) {
        this.versionRepository = versionRepository;
        this.workflowRepository = workflowRepository;
    }

    public List<WorkflowVersion> listVersions(String workflowId) {
        return versionRepository.findByWorkflowIdOrderByVersionNumberDesc(workflowId);
    }

    public WorkflowVersion getVersion(String versionId) {
        return versionRepository.findByVersionId(versionId)
            .orElseThrow(() -> new IllegalArgumentException("版本不存在: " + versionId));
    }

    @Transactional
    public WorkflowVersion createVersion(Workflow workflow, String username, String changeDescription) {
        int nextVersionNumber = getNextVersionNumber(workflow.getWorkflowId());

        WorkflowVersion version = new WorkflowVersion();
        version.setVersionId("wfv_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        version.setWorkflowId(workflow.getWorkflowId());
        version.setVersionNumber(nextVersionNumber);
        version.setName(workflow.getName());
        version.setDescription(workflow.getDescription());
        version.setStatus(workflow.getStatus());
        version.setKnowledgeBases(workflow.getKnowledgeBases());
        version.setPromptRefs(workflow.getPromptRefs());
        version.setMcpTools(workflow.getMcpTools());
        version.setModels(workflow.getModels());
        version.setNodesJson(workflow.getNodesJson());
        version.setEdgesJson(workflow.getEdgesJson());
        version.setSnapshotBy(username);
        version.setChangeDescription(changeDescription != null ? changeDescription : "编辑保存");
        version.setIsActive(false);

        logger.info("创建工作流版本快照: workflowId={}, version=v{}", workflow.getWorkflowId(), nextVersionNumber);
        return versionRepository.save(version);
    }

    @Transactional
    public Workflow rollback(String workflowId, String versionId, String username) {
        WorkflowVersion target = getVersion(versionId);
        if (!target.getWorkflowId().equals(workflowId)) {
            throw new IllegalArgumentException("版本不属于该工作流");
        }

        Workflow workflow = workflowRepository.findByWorkflowId(workflowId)
            .orElseThrow(() -> new IllegalArgumentException("工作流不存在"));

        createVersion(workflow, username, "回滚前自动保存（回滚到 v" + target.getVersionNumber() + "）");

        workflow.setName(target.getName());
        workflow.setDescription(target.getDescription());
        workflow.setStatus(target.getStatus());
        workflow.setKnowledgeBases(target.getKnowledgeBases());
        workflow.setPromptRefs(target.getPromptRefs());
        workflow.setMcpTools(target.getMcpTools());
        workflow.setModels(target.getModels());
        workflow.setNodesJson(target.getNodesJson());
        workflow.setEdgesJson(target.getEdgesJson());

        return workflowRepository.save(workflow);
    }

    @Transactional
    public void activateVersion(String versionId) {
        WorkflowVersion version = getVersion(versionId);
        versionRepository.findByWorkflowIdAndIsActiveTrue(version.getWorkflowId())
            .ifPresent(active -> {
                active.setIsActive(false);
                versionRepository.save(active);
            });
        version.setIsActive(true);
        versionRepository.save(version);
    }

    private int getNextVersionNumber(String workflowId) {
        return versionRepository.findFirstByWorkflowIdOrderByVersionNumberDesc(workflowId)
            .map(v -> v.getVersionNumber() + 1)
            .orElse(1);
    }
}

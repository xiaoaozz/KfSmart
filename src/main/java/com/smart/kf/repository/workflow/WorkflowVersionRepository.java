package com.smart.kf.repository.workflow;

import com.smart.kf.model.workflow.WorkflowVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WorkflowVersionRepository extends JpaRepository<WorkflowVersion, Long> {

    List<WorkflowVersion> findByWorkflowIdOrderByVersionNumberDesc(String workflowId);

    Optional<WorkflowVersion> findByVersionId(String versionId);

    Optional<WorkflowVersion> findFirstByWorkflowIdOrderByVersionNumberDesc(String workflowId);

    Optional<WorkflowVersion> findByWorkflowIdAndIsActiveTrue(String workflowId);
}

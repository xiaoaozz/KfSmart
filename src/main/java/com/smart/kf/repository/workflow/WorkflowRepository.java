package com.smart.kf.repository.workflow;

import com.smart.kf.model.workflow.Workflow;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorkflowRepository extends JpaRepository<Workflow, Long> {
    Optional<Workflow> findByWorkflowId(String workflowId);

    List<Workflow> findByNameContainingIgnoreCase(String name);
}

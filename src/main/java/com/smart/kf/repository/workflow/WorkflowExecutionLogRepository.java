package com.smart.kf.repository.workflow;

import com.smart.kf.model.workflow.WorkflowExecutionLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WorkflowExecutionLogRepository extends JpaRepository<WorkflowExecutionLog, Long> {

    Optional<WorkflowExecutionLog> findByExecutionId(String executionId);

    Page<WorkflowExecutionLog> findByWorkflowIdOrderByStartedAtDesc(String workflowId, Pageable pageable);
}

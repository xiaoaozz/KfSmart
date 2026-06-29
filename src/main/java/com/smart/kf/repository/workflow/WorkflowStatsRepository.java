package com.smart.kf.repository.workflow;

import com.smart.kf.model.workflow.WorkflowStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WorkflowStatsRepository extends JpaRepository<WorkflowStats, Long> {

    Optional<WorkflowStats> findByWorkflowId(String workflowId);
}

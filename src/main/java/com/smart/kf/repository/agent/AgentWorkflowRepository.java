package com.smart.kf.repository.agent;

import com.smart.kf.model.agent.AgentWorkflow;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AgentWorkflowRepository extends JpaRepository<AgentWorkflow, Long> {
    Optional<AgentWorkflow> findByWorkflowId(String workflowId);

    List<AgentWorkflow> findByNameContainingIgnoreCaseOrTypeContainingIgnoreCase(String name, String type);
}

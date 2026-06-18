package com.smart.kf.repository.agent;

import com.smart.kf.model.agent.AgentExecutionLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AgentExecutionLogRepository extends JpaRepository<AgentExecutionLog, Long> {

    Optional<AgentExecutionLog> findByExecutionId(String executionId);

    Page<AgentExecutionLog> findByAgentIdOrderByStartedAtDesc(String agentId, Pageable pageable);
}

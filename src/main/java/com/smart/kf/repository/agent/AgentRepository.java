package com.smart.kf.repository.agent;

import com.smart.kf.model.agent.Agent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AgentRepository extends JpaRepository<Agent, Long> {
    Optional<Agent> findByAgentId(String agentId);

    List<Agent> findByNameContainingIgnoreCase(String name);
}

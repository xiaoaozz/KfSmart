package com.smart.kf.repository.agent;

import com.smart.kf.model.agent.AgentStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AgentStatsRepository extends JpaRepository<AgentStats, Long> {

    Optional<AgentStats> findByAgentId(String agentId);
}

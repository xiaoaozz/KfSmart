package com.smart.kf.repository.agent;

import com.smart.kf.model.agent.AgentDailyStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface AgentDailyStatsRepository extends JpaRepository<AgentDailyStats, Long> {

    Optional<AgentDailyStats> findByAgentIdAndSnapshotDate(String agentId, LocalDate snapshotDate);

    List<AgentDailyStats> findByAgentIdOrderBySnapshotDateDesc(String agentId);

    List<AgentDailyStats> findBySnapshotDateBetween(LocalDate from, LocalDate to);
}

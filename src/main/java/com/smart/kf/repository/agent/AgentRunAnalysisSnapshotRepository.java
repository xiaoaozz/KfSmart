package com.smart.kf.repository.agent;

import com.smart.kf.model.agent.AgentRunAnalysisSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface AgentRunAnalysisSnapshotRepository extends JpaRepository<AgentRunAnalysisSnapshot, Long> {

    Optional<AgentRunAnalysisSnapshot> findBySnapshotDate(LocalDate snapshotDate);

}

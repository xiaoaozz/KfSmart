package com.smart.kf.repository.agent;

import com.smart.kf.model.agent.AgentVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AgentVersionRepository extends JpaRepository<AgentVersion, Long> {

    List<AgentVersion> findByAgentIdOrderByVersionNumberDesc(String agentId);

    Optional<AgentVersion> findByVersionId(String versionId);

    Optional<AgentVersion> findFirstByAgentIdOrderByVersionNumberDesc(String agentId);

    Optional<AgentVersion> findByAgentIdAndIsActiveTrue(String agentId);
}

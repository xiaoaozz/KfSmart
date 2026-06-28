package com.smart.kf.repository.agent;

import com.smart.kf.model.agent.AgentI18n;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AgentI18nRepository extends JpaRepository<AgentI18n, Long> {

    Optional<AgentI18n> findByAgentIdAndLang(String agentId, String lang);

    List<AgentI18n> findByAgentId(String agentId);

    void deleteByAgentId(String agentId);
}

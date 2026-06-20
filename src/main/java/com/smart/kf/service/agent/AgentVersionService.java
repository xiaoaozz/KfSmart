package com.smart.kf.service.agent;

import com.smart.kf.model.agent.AgentVersion;
import com.smart.kf.model.agent.Agent;
import com.smart.kf.repository.agent.AgentRepository;
import com.smart.kf.repository.agent.AgentVersionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class AgentVersionService {

    private static final Logger logger = LoggerFactory.getLogger(AgentVersionService.class);

    private final AgentVersionRepository versionRepository;
    private final AgentRepository agentRepository;

    public AgentVersionService(AgentVersionRepository versionRepository, AgentRepository agentRepository) {
        this.versionRepository = versionRepository;
        this.agentRepository = agentRepository;
    }

    public List<AgentVersion> listVersions(String agentId) {
        return versionRepository.findByAgentIdOrderByVersionNumberDesc(agentId);
    }

    public AgentVersion getVersion(String versionId) {
        return versionRepository.findByVersionId(versionId)
            .orElseThrow(() -> new IllegalArgumentException("版本不存在: " + versionId));
    }

    @Transactional
    public AgentVersion createVersion(Agent agent, String username, String changeDescription) {
        int nextVersionNumber = getNextVersionNumber(agent.getAgentId());

        AgentVersion version = new AgentVersion();
        version.setVersionId("agtver_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        version.setAgentId(agent.getAgentId());
        version.setVersionNumber(nextVersionNumber);
        version.setName(agent.getName());
        version.setDescription(agent.getDescription());
        version.setStatus(agent.getStatus());
        version.setSystemPrompt(agent.getSystemPrompt());
        version.setUserPrompt(agent.getUserPrompt());
        version.setTemperature(agent.getTemperature());
        version.setTopP(agent.getTopP());
        version.setMaxTokens(agent.getMaxTokens());
        version.setMaxIterations(agent.getMaxIterations());
        version.setMemoryTypes(agent.getMemoryTypes());
        version.setKnowledgeBases(agent.getKnowledgeBases());
        version.setPromptRefs(agent.getPromptRefs());
        version.setMcpTools(agent.getMcpTools());
        version.setModels(agent.getModels());
        version.setSnapshotBy(username);
        version.setChangeDescription(changeDescription != null ? changeDescription : "编辑保存");
        version.setIsActive(false);

        logger.info("创建Agent版本快照: agentId={}, version=v{}", agent.getAgentId(), nextVersionNumber);
        return versionRepository.save(version);
    }

    @Transactional
    public Agent rollback(String agentId, String versionId, String username) {
        AgentVersion target = getVersion(versionId);
        if (!target.getAgentId().equals(agentId)) {
            throw new IllegalArgumentException("版本不属于该Agent");
        }

        Agent agent = agentRepository.findByAgentId(agentId)
            .orElseThrow(() -> new IllegalArgumentException("Agent不存在"));

        createVersion(agent, username, "回滚前自动保存（回滚到 v" + target.getVersionNumber() + "）");

        agent.setName(target.getName());
        agent.setDescription(target.getDescription());
        agent.setStatus(target.getStatus());
        agent.setSystemPrompt(target.getSystemPrompt());
        agent.setUserPrompt(target.getUserPrompt());
        agent.setTemperature(target.getTemperature());
        agent.setTopP(target.getTopP());
        agent.setMaxTokens(target.getMaxTokens());
        agent.setMaxIterations(target.getMaxIterations());
        agent.setMemoryTypes(target.getMemoryTypes());
        agent.setKnowledgeBases(target.getKnowledgeBases());
        agent.setPromptRefs(target.getPromptRefs());
        agent.setMcpTools(target.getMcpTools());
        agent.setModels(target.getModels());

        return agentRepository.save(agent);
    }

    @Transactional
    public void activateVersion(String versionId) {
        AgentVersion version = getVersion(versionId);
        versionRepository.findByAgentIdAndIsActiveTrue(version.getAgentId())
            .ifPresent(active -> {
                active.setIsActive(false);
                versionRepository.save(active);
            });
        version.setIsActive(true);
        versionRepository.save(version);
    }

    private int getNextVersionNumber(String agentId) {
        return versionRepository.findFirstByAgentIdOrderByVersionNumberDesc(agentId)
            .map(v -> v.getVersionNumber() + 1)
            .orElse(1);
    }
}

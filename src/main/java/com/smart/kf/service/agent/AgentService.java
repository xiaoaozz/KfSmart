package com.smart.kf.service.agent;

import com.smart.kf.model.agent.Agent;
import com.smart.kf.repository.agent.AgentRepository;
import com.smart.kf.utils.pagination.PageQuery;
import com.smart.kf.utils.pagination.PageResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AgentService {

    private final AgentRepository agentRepository;
    private final AgentVersionService versionService;

    public AgentService(AgentRepository agentRepository, AgentVersionService versionService) {
        this.agentRepository = agentRepository;
        this.versionService = versionService;
    }

    public PageResult<Agent> listAgents(String keyword, PageQuery query) {
        List<Agent> source = isBlank(keyword)
            ? agentRepository.findAll()
            : agentRepository.findByNameContainingIgnoreCase(keyword);
        source.sort(Comparator.comparing(Agent::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())));
        return PageResult.fromList(source, query);
    }

    public Map<String, Object> agentStats() {
        List<Agent> agents = agentRepository.findAll();
        long agentCount = agents.size();
        long calls = agents.stream().mapToLong(Agent::getCallCount).sum();
        long success = agents.stream().mapToLong(Agent::getSuccessCount).sum();
        long duration = agents.stream().mapToLong(a -> a.getAvgDurationMs() * Math.max(1, a.getCallCount())).sum();
        long successRate = calls == 0 ? 100 : Math.round(success * 100.0 / calls);
        long avgDurationMs = calls == 0 ? 0 : Math.round(duration * 1.0 / calls);

        Map<String, Object> stats = new HashMap<>();
        stats.put("agentCount", agentCount);
        stats.put("runCount", calls);
        stats.put("successRate", successRate);
        stats.put("avgDurationMs", avgDurationMs);
        return stats;
    }

    public Agent getAgent(String agentId) {
        return agentRepository.findByAgentId(agentId)
            .orElseThrow(() -> new IllegalArgumentException("Agent不存在"));
    }

    @Transactional
    public Agent saveAgent(Agent request) {
        Agent agent = isBlank(request.getAgentId())
            ? new Agent()
            : agentRepository.findByAgentId(request.getAgentId()).orElse(new Agent());
        boolean isNew = isBlank(agent.getAgentId());
        if (isNew) {
            agent.setAgentId("agt_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        }
        applyAgent(agent, request);
        Agent saved = agentRepository.save(agent);
        if (!isNew) {
            versionService.createVersion(saved, "system", "编辑保存");
        }
        return saved;
    }

    @Transactional
    public Agent copyAgent(String agentId) {
        Agent source = getAgent(agentId);
        Agent copy = new Agent();
        applyAgent(copy, source);
        copy.setAgentId("agt_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        copy.setName(source.getName() + " 副本");
        copy.setStatus("草稿");
        copy.setCallCount(0);
        copy.setSuccessCount(0);
        copy.setFailureCount(0);
        copy.setPublishedAt(null);
        source.setInstallCount(safeLong(source.getInstallCount()) + 1);
        agentRepository.save(source);
        return agentRepository.save(copy);
    }

    @Transactional
    public Agent publishAgent(String agentId) {
        Agent agent = getAgent(agentId);
        agent.setStatus("运行中");
        agent.setPublishedAt(LocalDateTime.now());
        return agentRepository.save(agent);
    }

    @Transactional
    public void deleteAgent(String agentId) {
        agentRepository.delete(getAgent(agentId));
    }

    public List<Map<String, Object>> marketplace() {
        return agentRepository.findAll().stream()
            .filter(item -> "运行中".equals(item.getStatus()))
            .map(item -> {
                Map<String, Object> row = new HashMap<>();
                row.put("agentId", item.getAgentId());
                row.put("name", item.getName());
                row.put("description", item.getDescription());
                row.put("installCount", safeLong(item.getInstallCount()));
                row.put("tags", item.getTags());
                return row;
            })
            .toList();
    }

    public Map<String, Object> runAnalysis() {
        List<Agent> agents = agentRepository.findAll();
        long calls = agents.stream().mapToLong(Agent::getCallCount).sum();
        long success = agents.stream().mapToLong(Agent::getSuccessCount).sum();
        long failures = agents.stream().mapToLong(Agent::getFailureCount).sum();

        Map<String, Object> result = agentStats();
        result.put("failureRate", calls == 0 ? 0 : Math.round(failures * 100.0 / calls));
        result.put("successRate", calls == 0 ? 100 : Math.round(success * 100.0 / calls));
        result.put("hotAgents", agents.stream()
            .sorted(Comparator.comparingLong(Agent::getCallCount).reversed())
            .limit(5)
            .map(item -> Map.of("name", item.getName(), "callCount", item.getCallCount()))
            .toList());
        result.put("cost", Map.of("tokenUsage", calls * 1280, "modelCost", calls * 0.0128, "toolCost", calls * 0.004));
        return result;
    }

    private void applyAgent(Agent target, Agent source) {
        target.setName(source.getName());
        target.setDescription(source.getDescription());
        target.setStatus(isBlank(source.getStatus()) ? "草稿" : source.getStatus());
        target.setOwnerName(source.getOwnerName());
        target.setTags(source.getTags());
        target.setPermissionScope(isBlank(source.getPermissionScope()) ? "组织内" : source.getPermissionScope());
        target.setKnowledgeBases(source.getKnowledgeBases());
        target.setPromptRefs(source.getPromptRefs());
        target.setMcpTools(source.getMcpTools());
        target.setModels(source.getModels());
        if (!isBlank(source.getSystemPrompt())) {
            target.setSystemPrompt(source.getSystemPrompt());
        }
        if (!isBlank(source.getAvatarEmoji())) {
            target.setAvatarEmoji(source.getAvatarEmoji());
        }
        if (source.getTemperature() != null) {
            target.setTemperature(source.getTemperature());
        }
        if (source.getTopP() != null) {
            target.setTopP(source.getTopP());
        }
        if (source.getMaxTokens() != null) {
            target.setMaxTokens(source.getMaxTokens());
        }
        if (source.getMaxIterations() != null) {
            target.setMaxIterations(source.getMaxIterations());
        }
        if (!isBlank(source.getMemoryTypes())) {
            target.setMemoryTypes(source.getMemoryTypes());
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private long safeLong(Long value) {
        return value == null ? 0L : value;
    }
}

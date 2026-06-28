package com.smart.kf.service.agent;

import com.smart.kf.config.LocaleContext;
import com.smart.kf.model.agent.Agent;
import com.smart.kf.model.agent.AgentI18n;
import com.smart.kf.repository.agent.AgentI18nRepository;
import com.smart.kf.repository.agent.AgentRepository;
import com.smart.kf.service.I18nTranslationService;
import com.smart.kf.utils.pagination.PageQuery;
import com.smart.kf.utils.pagination.PageResult;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final AgentRunAnalysisService runAnalysisService;
    private final AgentI18nRepository agentI18nRepository;

    @Autowired
    private I18nTranslationService i18nTranslationService;

    public AgentService(AgentRepository agentRepository, AgentVersionService versionService,
                        AgentRunAnalysisService runAnalysisService, AgentI18nRepository agentI18nRepository) {
        this.agentRepository = agentRepository;
        this.versionService = versionService;
        this.runAnalysisService = runAnalysisService;
        this.agentI18nRepository = agentI18nRepository;
    }

    public PageResult<Agent> listAgents(String keyword, PageQuery query) {
        List<Agent> source = isBlank(keyword)
            ? agentRepository.findAll()
            : agentRepository.findByNameContainingIgnoreCase(keyword);
        source.sort(Comparator.comparing(Agent::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())));
        source.forEach(this::applyI18n);
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
        Agent agent = agentRepository.findByAgentId(agentId)
            .orElseThrow(() -> new IllegalArgumentException("Agent不存在"));
        applyI18n(agent);
        return agent;
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
            i18nTranslationService.retranslateAgentAsync(saved.getAgentId(), saved.getName(), saved.getDescription());
        } else {
            i18nTranslationService.translateAgentAsync(saved.getAgentId(), saved.getName(), saved.getDescription());
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
        copy.setStatus("draft");
        copy.setCallCount(0);
        copy.setSuccessCount(0);
        copy.setFailureCount(0);
        copy.setPublishedAt(null);
        source.setInstallCount(safeLong(source.getInstallCount()) + 1);
        agentRepository.save(source);
        Agent savedCopy = agentRepository.save(copy);
        i18nTranslationService.translateAgentAsync(savedCopy.getAgentId(), savedCopy.getName(), savedCopy.getDescription());
        return savedCopy;
    }

    @Transactional
    public Agent publishAgent(String agentId) {
        Agent agent = getAgent(agentId);
        agent.setStatus("published");
        agent.setPublishedAt(LocalDateTime.now());
        return agentRepository.save(agent);
    }

    @Transactional
    public void deleteAgent(String agentId) {
        agentRepository.delete(getAgent(agentId));
    }

    public List<Map<String, Object>> marketplace() {
        return agentRepository.findAll().stream()
            .filter(item -> "published".equals(item.getStatus()))
            .map(item -> {
                applyI18n(item);
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

    public AgentI18n upsertAgentI18n(String agentId, String lang, String name, String description) {
        AgentI18n i18n = agentI18nRepository.findByAgentIdAndLang(agentId, lang)
            .orElseGet(() -> {
                AgentI18n newI18n = new AgentI18n();
                newI18n.setAgentId(agentId);
                newI18n.setLang(lang);
                return newI18n;
            });
        if (name != null) i18n.setName(name);
        if (description != null) i18n.setDescription(description);
        return agentI18nRepository.save(i18n);
    }

    public List<AgentI18n> getAgentI18n(String agentId) {
        return agentI18nRepository.findByAgentId(agentId);
    }

    private void applyI18n(Agent agent) {
        String lang = LocaleContext.get();
        if (lang == null || lang.equals("zh-CN")) return;
        agentI18nRepository.findByAgentIdAndLang(agent.getAgentId(), lang).ifPresent(i18n -> {
            if (i18n.getName() != null && !i18n.getName().isBlank()) agent.setName(i18n.getName());
            if (i18n.getDescription() != null && !i18n.getDescription().isBlank()) agent.setDescription(i18n.getDescription());
        });
    }

    public Map<String, Object> runAnalysis() {
        return runAnalysisService.buildRunAnalysis();
    }

    private void applyAgent(Agent target, Agent source) {
        target.setName(source.getName());
        target.setDescription(source.getDescription());
        target.setStatus(isBlank(source.getStatus()) ? "draft" : source.getStatus());
        target.setOwnerName(source.getOwnerName());
        target.setTags(source.getTags());
        target.setPermissionScope(isBlank(source.getPermissionScope()) ? "组织内" : source.getPermissionScope());
        target.setKnowledgeBases(source.getKnowledgeBases());
        target.setPromptRefs(source.getPromptRefs());
        target.setMcpTools(source.getMcpTools());
        target.setSkillRefs(source.getSkillRefs());
        target.setModels(source.getModels());
        target.setSystemPrompt(source.getSystemPrompt());
        target.setUserPrompt(source.getUserPrompt());
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

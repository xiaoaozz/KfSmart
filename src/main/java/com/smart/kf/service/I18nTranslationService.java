package com.smart.kf.service;

import com.smart.kf.client.ModelClient;
import com.smart.kf.model.ApiKeyConfig;
import com.smart.kf.model.KnowledgeBaseI18n;
import com.smart.kf.model.OrganizationTagI18n;
import com.smart.kf.model.agent.AgentI18n;
import com.smart.kf.repository.KnowledgeBaseI18nRepository;
import com.smart.kf.repository.KnowledgeBaseRepository;
import com.smart.kf.repository.OrganizationTagI18nRepository;
import com.smart.kf.repository.OrganizationTagRepository;
import com.smart.kf.repository.agent.AgentI18nRepository;
import com.smart.kf.repository.agent.AgentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Automatically translates dynamic entity names/descriptions to non-Chinese locales
 * using the configured AI model.  zh-CN content lives on the primary entity; all other
 * languages are stored in the corresponding _i18n table and looked up at query time
 * via {@link com.smart.kf.config.LocaleContext}.
 */
@Service
public class I18nTranslationService {

    private static final Logger log = LoggerFactory.getLogger(I18nTranslationService.class);

    private static final List<String> TARGET_LANGS = List.of("en-US", "ja-JP");
    private static final Map<String, String> LANG_NAMES = Map.of(
            "en-US", "English",
            "ja-JP", "Japanese"
    );

    @Autowired private ModelClient modelClient;
    @Autowired private ApiKeyConfigService apiKeyConfigService;
    @Autowired private KnowledgeBaseRepository knowledgeBaseRepository;
    @Autowired private KnowledgeBaseI18nRepository knowledgeBaseI18nRepository;
    @Autowired private AgentRepository agentRepository;
    @Autowired private AgentI18nRepository agentI18nRepository;
    @Autowired private OrganizationTagRepository organizationTagRepository;
    @Autowired private OrganizationTagI18nRepository organizationTagI18nRepository;

    // ─── Public async entry-points (called after entity saves) ────────────────

    @Async
    public void translateKbAsync(String kbId, String name, String description) {
        doTranslateKb(kbId, name, description, false);
    }

    @Async
    public void retranslateKbAsync(String kbId, String name, String description) {
        doTranslateKb(kbId, name, description, true);
    }

    @Async
    public void translateAgentAsync(String agentId, String name, String description) {
        doTranslateAgent(agentId, name, description, false);
    }

    @Async
    public void retranslateAgentAsync(String agentId, String name, String description) {
        doTranslateAgent(agentId, name, description, true);
    }

    @Async
    public void translateOrgTagAsync(String tagId, String name, String description) {
        doTranslateOrgTag(tagId, name, description, false);
    }

    @Async
    public void retranslateOrgTagAsync(String tagId, String name, String description) {
        doTranslateOrgTag(tagId, name, description, true);
    }

    /**
     * Batch-translate all existing entities, overwriting existing i18n records.
     * Runs asynchronously so the admin endpoint returns immediately.
     */
    @Async
    public void syncAllI18n() {
        log.info("[i18n-sync] Starting full i18n sync");

        knowledgeBaseRepository.findAll().forEach(kb ->
                doTranslateKb(kb.getKbId(), kb.getName(), kb.getDescription(), true));

        agentRepository.findAll().forEach(agent ->
                doTranslateAgent(agent.getAgentId(), agent.getName(), agent.getDescription(), true));

        organizationTagRepository.findAll().forEach(tag ->
                doTranslateOrgTag(tag.getTagId(), tag.getName(), tag.getDescription(), true));

        log.info("[i18n-sync] Full i18n sync complete");
    }

    // ─── Private translation helpers ──────────────────────────────────────────

    private void doTranslateKb(String kbId, String name, String description) {
        doTranslateKb(kbId, name, description, false);
    }

    private void doTranslateKb(String kbId, String name, String description, boolean force) {
        for (String lang : TARGET_LANGS) {
            if (!force && knowledgeBaseI18nRepository.findByKbIdAndLang(kbId, lang).isPresent()) continue;
            try {
                KnowledgeBaseI18n record = knowledgeBaseI18nRepository
                        .findByKbIdAndLang(kbId, lang).orElseGet(KnowledgeBaseI18n::new);
                record.setKbId(kbId);
                record.setLang(lang);
                if (name != null && !name.isBlank()) record.setName(translateText(name, lang));
                if (description != null && !description.isBlank()) record.setDescription(translateText(description, lang));
                knowledgeBaseI18nRepository.save(record);
                log.info("[i18n] KB translated: kbId={}, lang={}", kbId, lang);
            } catch (Exception e) {
                log.error("[i18n] KB translation failed: kbId={}, lang={}", kbId, lang, e);
            }
        }
    }

    private void doTranslateAgent(String agentId, String name, String description) {
        doTranslateAgent(agentId, name, description, false);
    }

    private void doTranslateAgent(String agentId, String name, String description, boolean force) {
        for (String lang : TARGET_LANGS) {
            if (!force && agentI18nRepository.findByAgentIdAndLang(agentId, lang).isPresent()) continue;
            try {
                AgentI18n record = agentI18nRepository
                        .findByAgentIdAndLang(agentId, lang).orElseGet(AgentI18n::new);
                record.setAgentId(agentId);
                record.setLang(lang);
                if (name != null && !name.isBlank()) record.setName(translateText(name, lang));
                if (description != null && !description.isBlank()) record.setDescription(translateText(description, lang));
                agentI18nRepository.save(record);
                log.info("[i18n] Agent translated: agentId={}, lang={}", agentId, lang);
            } catch (Exception e) {
                log.error("[i18n] Agent translation failed: agentId={}, lang={}", agentId, lang, e);
            }
        }
    }

    private void doTranslateOrgTag(String tagId, String name, String description) {
        doTranslateOrgTag(tagId, name, description, false);
    }

    private void doTranslateOrgTag(String tagId, String name, String description, boolean force) {
        for (String lang : TARGET_LANGS) {
            if (!force && organizationTagI18nRepository.findByTagIdAndLang(tagId, lang).isPresent()) continue;
            try {
                OrganizationTagI18n record = organizationTagI18nRepository
                        .findByTagIdAndLang(tagId, lang).orElseGet(OrganizationTagI18n::new);
                record.setTagId(tagId);
                record.setLang(lang);
                if (name != null && !name.isBlank()) record.setName(translateText(name, lang));
                if (description != null && !description.isBlank()) record.setDescription(translateText(description, lang));
                organizationTagI18nRepository.save(record);
                log.info("[i18n] OrgTag translated: tagId={}, lang={}", tagId, lang);
            } catch (Exception e) {
                log.error("[i18n] OrgTag translation failed: tagId={}, lang={}", tagId, lang, e);
            }
        }
    }

    private String translateText(String text, String lang) {
        String langName = LANG_NAMES.getOrDefault(lang, lang);
        String systemPrompt = "You are a professional translator. Translate the following text to " + langName +
                ". Reply with ONLY the translated text, no explanations.";
        try {
            ApiKeyConfig activeConfig = apiKeyConfigService.getActiveConfig().orElse(null);
            String result = modelClient.chat(text, null, null, activeConfig, systemPrompt);
            return result != null ? result.trim() : text;
        } catch (Exception e) {
            log.warn("[i18n] translateText failed for lang={}: {}", lang, e.getMessage());
            return text;
        }
    }
}

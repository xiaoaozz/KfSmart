package com.smart.kf.service;

import com.smart.kf.model.agent.McpToolConfig;
import com.smart.kf.model.ApiKeyConfig;
import com.smart.kf.model.agent.PromptTemplate;
import com.smart.kf.model.agent.PromptTemplateHistory;
import com.smart.kf.repository.agent.McpToolConfigRepository;
import com.smart.kf.repository.agent.PromptTemplateHistoryRepository;
import com.smart.kf.repository.agent.PromptTemplateRepository;
import com.smart.kf.utils.pagination.PageQuery;
import com.smart.kf.utils.pagination.PageResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class SharedResourceService {

    private final PromptTemplateRepository promptRepository;
    private final PromptTemplateHistoryRepository historyRepository;
    private final McpToolConfigRepository toolRepository;
    private final ApiKeyConfigService apiKeyConfigService;

    public SharedResourceService(
        PromptTemplateRepository promptRepository,
        PromptTemplateHistoryRepository historyRepository,
        McpToolConfigRepository toolRepository,
        ApiKeyConfigService apiKeyConfigService
    ) {
        this.promptRepository = promptRepository;
        this.historyRepository = historyRepository;
        this.toolRepository = toolRepository;
        this.apiKeyConfigService = apiKeyConfigService;
    }

    // ── Prompt 模板管理 ──

    public PageResult<PromptTemplate> listPrompts(String keyword, String category, PageQuery query) {
        List<PromptTemplate> source;
        if (!isBlank(category) && !isBlank(keyword)) {
            source = promptRepository.findByCategoryAndNameContainingIgnoreCase(category, keyword);
        } else if (!isBlank(category)) {
            source = promptRepository.findByCategory(category);
        } else if (!isBlank(keyword)) {
            source = promptRepository.findByNameContainingIgnoreCaseOrCategoryContainingIgnoreCase(keyword, keyword);
        } else {
            source = promptRepository.findAll();
        }
        source.sort(Comparator.comparing(PromptTemplate::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())));
        return PageResult.fromList(source, query);
    }

    public List<String> listPromptCategories() {
        return promptRepository.findAll().stream()
            .map(PromptTemplate::getCategory)
            .filter(c -> c != null && !c.isBlank())
            .distinct()
            .sorted()
            .toList();
    }

    public PromptTemplate getPrompt(String templateId) {
        return promptRepository.findByTemplateId(templateId)
            .orElseThrow(() -> new IllegalArgumentException("Prompt 模板不存在"));
    }

    @Transactional
    public PromptTemplate savePrompt(PromptTemplate request, String username) {
        if (!isBlank(request.getTemplateId())) {
            PromptTemplate existing = promptRepository.findByTemplateId(request.getTemplateId()).orElse(null);
            if (existing != null) {
                PromptTemplateHistory snapshot = new PromptTemplateHistory();
                snapshot.setTemplateId(existing.getTemplateId());
                snapshot.setVersion(existing.getVersion());
                snapshot.setName(existing.getName());
                snapshot.setDescription(existing.getDescription());
                snapshot.setCategory(existing.getCategory());
                snapshot.setSystemContent(existing.getSystemContent());
                snapshot.setContent(existing.getContent());
                snapshot.setVariables(existing.getVariables());
                snapshot.setTags(existing.getTags());
                snapshot.setStatus(existing.getStatus());
                snapshot.setSnapshotBy(username);
                snapshot.setChangeDescription("编辑保存（" + existing.getVersion() + " → 下一版本）");
                historyRepository.save(snapshot);
            }
        }
        PromptTemplate prompt = isBlank(request.getTemplateId())
            ? new PromptTemplate()
            : promptRepository.findByTemplateId(request.getTemplateId()).orElse(new PromptTemplate());
        if (isBlank(prompt.getTemplateId())) {
            prompt.setTemplateId("pt_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        }
        prompt.setName(request.getName());
        prompt.setDescription(request.getDescription());
        prompt.setCategory(request.getCategory());
        String baseVersion = prompt.getVersion();
        int nextVersion;
        if (baseVersion != null && !baseVersion.isBlank()) {
            String v = baseVersion.trim();
            if (v.startsWith("v") || v.startsWith("V")) {
                v = v.substring(1);
            }
            String major = v.split("\\.")[0];
            try {
                nextVersion = Integer.parseInt(major) + 1;
            } catch (NumberFormatException ignored) {
                nextVersion = 1;
            }
        } else {
            nextVersion = 1;
        }
        prompt.setVersion("v" + nextVersion);
        prompt.setSystemContent(request.getSystemContent());
        prompt.setContent(request.getContent());
        prompt.setVariables(request.getVariables());
        prompt.setTags(request.getTags());
        prompt.setStatus(isBlank(request.getStatus()) ? "启用" : request.getStatus());
        return promptRepository.save(prompt);
    }

    public List<PromptTemplateHistory> getPromptHistories(String templateId) {
        return historyRepository.findByTemplateIdOrderBySnapshotAtDesc(templateId);
    }

    public PromptTemplateHistory getPromptHistory(String templateId, Long snapshotId) {
        PromptTemplateHistory snapshot = historyRepository.findById(snapshotId)
            .orElseThrow(() -> new IllegalArgumentException("历史版本不存在"));
        if (!snapshot.getTemplateId().equals(templateId)) {
            throw new IllegalArgumentException("历史版本不属于该模板");
        }
        return snapshot;
    }

    @Transactional
    public PromptTemplate rollbackPrompt(String templateId, Long snapshotId, String username) {
        PromptTemplateHistory snapshot = historyRepository.findById(snapshotId)
            .orElseThrow(() -> new IllegalArgumentException("历史版本不存在"));
        if (!snapshot.getTemplateId().equals(templateId)) {
            throw new IllegalArgumentException("历史版本不属于该模板，禁止跨模板回滚");
        }
        PromptTemplate template = promptRepository.findByTemplateId(templateId)
            .orElseThrow(() -> new IllegalArgumentException("Prompt 模板不存在"));

        PromptTemplateHistory preRollbackSnapshot = new PromptTemplateHistory();
        preRollbackSnapshot.setTemplateId(template.getTemplateId());
        preRollbackSnapshot.setVersion(template.getVersion());
        preRollbackSnapshot.setName(template.getName());
        preRollbackSnapshot.setDescription(template.getDescription());
        preRollbackSnapshot.setCategory(template.getCategory());
        preRollbackSnapshot.setSystemContent(template.getSystemContent());
        preRollbackSnapshot.setContent(template.getContent());
        preRollbackSnapshot.setVariables(template.getVariables());
        preRollbackSnapshot.setTags(template.getTags());
        preRollbackSnapshot.setStatus(template.getStatus());
        preRollbackSnapshot.setSnapshotBy(username);
        preRollbackSnapshot.setChangeDescription("回滚前自动保存（回滚到 " + snapshot.getVersion() + "）");
        historyRepository.save(preRollbackSnapshot);

        String oldVersion = template.getVersion();
        template.setName(snapshot.getName());
        template.setDescription(snapshot.getDescription());
        template.setCategory(snapshot.getCategory());
        template.setSystemContent(snapshot.getSystemContent());
        template.setContent(snapshot.getContent());
        template.setVariables(snapshot.getVariables());
        template.setTags(snapshot.getTags());
        template.setStatus(snapshot.getStatus());

        String newVersion;
        if (oldVersion != null && oldVersion.startsWith("v")) {
            try {
                int num = Integer.parseInt(oldVersion.substring(1).split("\\.")[0]);
                newVersion = "v" + (num + 1);
            } catch (NumberFormatException e) {
                newVersion = "v1";
            }
        } else {
            newVersion = "v1";
        }
        template.setVersion(newVersion);
        return promptRepository.save(template);
    }

    @Transactional
    public void togglePromptStatus(String templateId) {
        PromptTemplate prompt = promptRepository.findByTemplateId(templateId)
            .orElseThrow(() -> new IllegalArgumentException("Prompt 模板不存在"));
        prompt.setStatus("启用".equals(prompt.getStatus()) ? "禁用" : "启用");
        promptRepository.save(prompt);
    }

    @Transactional
    public void deletePrompt(String templateId) {
        promptRepository.delete(promptRepository.findByTemplateId(templateId)
            .orElseThrow(() -> new IllegalArgumentException("Prompt 模板不存在")));
    }

    // ── MCP 工具管理 ──

    public PageResult<Map<String, Object>> listTools(String keyword, PageQuery query) {
        List<McpToolConfig> source = isBlank(keyword)
            ? toolRepository.findAll()
            : toolRepository.findByNameContainingIgnoreCaseOrTypeContainingIgnoreCase(keyword, keyword);
        source.sort(Comparator.comparing(McpToolConfig::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())));
        return PageResult.fromList(source.stream().map(this::toToolResponse).toList(), query);
    }

    public Map<String, Object> saveTool(McpToolConfig request) {
        McpToolConfig tool = isBlank(request.getToolId())
            ? new McpToolConfig()
            : toolRepository.findByToolId(request.getToolId()).orElse(new McpToolConfig());
        if (isBlank(tool.getToolId())) {
            tool.setToolId("mcp_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        }
        tool.setName(request.getName());
        tool.setType(isBlank(request.getType()) ? "MCP" : request.getType());
        tool.setStatus(isBlank(request.getStatus()) ? "在线" : request.getStatus());
        tool.setEndpoint(request.getEndpoint());
        tool.setAuthType(request.getAuthType());
        if (!isBlank(request.getApiKey()) && !request.getApiKey().contains("****")) {
            tool.setApiKey(request.getApiKey());
        }
        tool.setDescription(request.getDescription());
        return toToolResponse(toolRepository.save(tool));
    }

    @Transactional
    public void deleteTool(String toolId) {
        toolRepository.delete(toolRepository.findByToolId(toolId)
            .orElseThrow(() -> new IllegalArgumentException("MCP 工具不存在")));
    }

    // ── 模型管理 ──

    public List<Map<String, Object>> listModels() {
        return apiKeyConfigService.findAll().stream().map(config -> {
            Map<String, Object> row = new HashMap<>();
            String provider = normalizeProvider(config.getProvider());
            String modelName = config.getModelName();
            String category = resolveModelCategory(provider, modelName);
            List<String> tags = resolveModelTags(provider, modelName, config);

            row.put("id", config.getId());
            row.put("name", config.getName());
            row.put("provider", config.getProvider());
            row.put("providerLabel", resolveProviderLabel(provider));
            row.put("apiUrl", config.getApiUrl());
            row.put("modelName", modelName);
            row.put("active", config.getActive());
            row.put("authType", config.getAuthType());
            row.put("temperature", config.getTemperature());
            row.put("maxTokens", config.getMaxTokens());
            row.put("topP", config.getTopP());
            row.put("remark", config.getRemark());
            row.put("status", Boolean.TRUE.equals(config.getActive()) ? "激活中" : "可用");
            row.put("scene", config.getRemark() == null ? "" : config.getRemark());
            row.put("icon", resolveModelIcon(provider));
            row.put("category", category);
            row.put("description", resolveModelDescription(config, category));
            row.put("tags", tags);
            row.put("createdAt", config.getCreatedAt());
            row.put("updatedAt", config.getUpdatedAt());
            return row;
        }).toList();
    }

    // ── 私有方法 ──

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String normalizeProvider(String provider) {
        return provider == null ? "other" : provider.trim().toLowerCase();
    }

    private String resolveProviderLabel(String provider) {
        return switch (provider) {
            case "deepseek" -> "DeepSeek";
            case "openai" -> "OpenAI";
            case "qwen" -> "通义千问";
            case "zhipu" -> "智谱 AI";
            case "ernie" -> "文心一言";
            case "anthropic" -> "Anthropic";
            case "ollama" -> "Ollama";
            default -> "其他";
        };
    }

    private String resolveModelIcon(String provider) {
        return switch (provider) {
            case "deepseek" -> "DS";
            case "openai" -> "AI";
            case "qwen" -> "QW";
            case "zhipu" -> "GLM";
            case "ernie" -> "ERN";
            case "anthropic" -> "CL";
            case "ollama" -> "OL";
            default -> "LLM";
        };
    }

    private String resolveModelCategory(String provider, String modelName) {
        String normalizedModel = modelName == null ? "" : modelName.toLowerCase();
        if (normalizedModel.contains("embed")) {
            return "向量模型";
        }
        if (normalizedModel.contains("vision") || normalizedModel.contains("vl") || normalizedModel.contains("omni")) {
            return "多模态模型";
        }
        if (normalizedModel.contains("coder") || normalizedModel.contains("code")) {
            return "代码模型";
        }
        if ("ollama".equals(provider)) {
            return "本地模型";
        }
        return "对话模型";
    }

    private String resolveModelDescription(ApiKeyConfig config, String category) {
        if (!isBlank(config.getRemark())) {
            return config.getRemark();
        }
        return "%s 由 %s API Key 配置提供，适用于%s、工作流 LLM 节点和 Agent 调用。"
            .formatted(config.getModelName(), resolveProviderLabel(normalizeProvider(config.getProvider())), category);
    }

    private List<String> resolveModelTags(String provider, String modelName, ApiKeyConfig config) {
        String category = resolveModelCategory(provider, modelName);
        String authType = isBlank(config.getAuthType()) ? "bearer" : config.getAuthType();
        return List.of(
            resolveProviderLabel(provider),
            category,
            Boolean.TRUE.equals(config.getActive()) ? "激活中" : "可用",
            authType
        );
    }

    private Map<String, Object> toToolResponse(McpToolConfig tool) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", tool.getId());
        row.put("toolId", tool.getToolId());
        row.put("name", tool.getName());
        row.put("type", tool.getType());
        row.put("status", tool.getStatus());
        row.put("endpoint", tool.getEndpoint());
        row.put("authType", tool.getAuthType());
        row.put("apiKeyMasked", maskApiKey(tool.getApiKey()));
        row.put("description", tool.getDescription());
        row.put("callCount", tool.getCallCount());
        row.put("createdAt", tool.getCreatedAt());
        row.put("updatedAt", tool.getUpdatedAt());
        return row;
    }

    private String maskApiKey(String value) {
        if (isBlank(value)) {
            return "";
        }
        if (value.contains("****")) {
            return value;
        }
        return value.length() <= 8 ? "****" : value.substring(0, 3) + "****" + value.substring(value.length() - 4);
    }
}

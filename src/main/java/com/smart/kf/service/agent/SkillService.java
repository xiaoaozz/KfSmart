package com.smart.kf.service.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.kf.model.agent.SkillDefinition;
import com.smart.kf.model.agent.SkillVersionHistory;
import com.smart.kf.model.agent.McpToolConfig;
import com.smart.kf.model.agent.PromptTemplate;
import com.smart.kf.model.agent.Agent;
import com.smart.kf.model.workflow.Workflow;
import com.smart.kf.repository.agent.AgentRepository;
import com.smart.kf.repository.agent.McpToolConfigRepository;
import com.smart.kf.repository.agent.PromptTemplateRepository;
import com.smart.kf.repository.agent.SkillDefinitionRepository;
import com.smart.kf.repository.agent.SkillVersionHistoryRepository;
import com.smart.kf.repository.workflow.WorkflowRepository;
import com.smart.kf.utils.pagination.PageQuery;
import com.smart.kf.utils.pagination.PageResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class SkillService {

    private final SkillDefinitionRepository skillRepository;
    private final SkillVersionHistoryRepository historyRepository;
    private final PromptTemplateRepository promptRepository;
    private final McpToolConfigRepository toolRepository;
    private final AgentRepository agentRepository;
    private final WorkflowRepository workflowRepository;
    private final ObjectMapper objectMapper;

    public SkillService(
        SkillDefinitionRepository skillRepository,
        SkillVersionHistoryRepository historyRepository,
        PromptTemplateRepository promptRepository,
        McpToolConfigRepository toolRepository,
        AgentRepository agentRepository,
        WorkflowRepository workflowRepository,
        ObjectMapper objectMapper
    ) {
        this.skillRepository = skillRepository;
        this.historyRepository = historyRepository;
        this.promptRepository = promptRepository;
        this.toolRepository = toolRepository;
        this.agentRepository = agentRepository;
        this.workflowRepository = workflowRepository;
        this.objectMapper = objectMapper;
    }

    public PageResult<SkillDefinition> listSkills(String keyword, String category, String status, PageQuery query) {
        List<SkillDefinition> source = skillRepository.findAll().stream()
            .filter(item -> isBlank(category) || category.equals(item.getCategory()))
            .filter(item -> isBlank(status) || status.equals(item.getStatus()))
            .filter(item -> matchesKeyword(item, keyword))
            .sorted(Comparator.comparing(SkillDefinition::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
            .toList();
        return PageResult.fromList(source, query);
    }

    public Map<String, Object> stats() {
        List<SkillDefinition> skills = skillRepository.findAll();
        long total = skills.size();
        long published = skills.stream().filter(item -> "已发布".equals(item.getStatus())).count();
        long draft = skills.stream().filter(item -> "草稿".equals(item.getStatus())).count();
        long disabled = skills.stream().filter(item -> "已停用".equals(item.getStatus())).count();
        long totalCalls = skills.stream().mapToLong(SkillDefinition::getCallCount).sum();
        long avgDuration = skills.isEmpty() ? 0 : Math.round(skills.stream().mapToLong(SkillDefinition::getAvgDurationMs).average().orElse(0));

        Map<String, Long> categories = new LinkedHashMap<>();
        skills.stream()
            .map(SkillDefinition::getCategory)
            .filter(value -> value != null && !value.isBlank())
            .sorted()
            .forEach(value -> categories.put(value, categories.getOrDefault(value, 0L) + 1));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("published", published);
        result.put("draft", draft);
        result.put("disabled", disabled);
        result.put("totalCalls", totalCalls);
        result.put("avgDurationMs", avgDuration);
        result.put("categories", categories);
        return result;
    }

    public SkillDefinition getSkill(String skillId) {
        return skillRepository.findBySkillId(skillId)
            .orElseThrow(() -> new IllegalArgumentException("技能不存在"));
    }

    public List<SkillVersionHistory> getHistories(String skillId) {
        return historyRepository.findBySkillIdOrderBySnapshotAtDesc(skillId);
    }

    @Transactional
    public SkillDefinition saveSkill(SkillDefinition request, String username) {
        SkillDefinition skill = isBlank(request.getSkillId())
            ? new SkillDefinition()
            : skillRepository.findBySkillId(request.getSkillId()).orElse(new SkillDefinition());

        boolean isNew = isBlank(skill.getSkillId());
        if (isNew) {
            skill.setSkillId("skl_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
            skill.setVersion("v1");
        } else {
            historyRepository.save(snapshotOf(skill, username, "编辑保存（" + skill.getVersion() + " → 下一版本）"));
            skill.setVersion(nextVersion(skill.getVersion()));
        }

        applySkill(skill, request);
        return skillRepository.save(skill);
    }

    @Transactional
    public SkillDefinition publishSkill(String skillId) {
        SkillDefinition skill = getSkill(skillId);
        skill.setStatus("已发布");
        skill.setPublishedAt(LocalDateTime.now());
        return skillRepository.save(skill);
    }

    @Transactional
    public SkillDefinition toggleSkillStatus(String skillId) {
        SkillDefinition skill = getSkill(skillId);
        if ("已停用".equals(skill.getStatus())) {
            skill.setStatus(skill.getPublishedAt() != null ? "已发布" : "草稿");
        } else {
            skill.setStatus("已停用");
        }
        return skillRepository.save(skill);
    }

    @Transactional
    public void deleteSkill(String skillId) {
        skillRepository.delete(getSkill(skillId));
    }

    @Transactional
    public SkillDefinition rollback(String skillId, Long snapshotId, String username) {
        SkillVersionHistory snapshot = historyRepository.findById(snapshotId)
            .orElseThrow(() -> new IllegalArgumentException("技能历史不存在"));
        if (!snapshot.getSkillId().equals(skillId)) {
            throw new IllegalArgumentException("历史版本不属于该技能");
        }
        SkillDefinition skill = getSkill(skillId);
        historyRepository.save(snapshotOf(skill, username, "回滚前自动保存（回滚到 " + snapshot.getVersion() + "）"));

        skill.setName(snapshot.getName());
        skill.setCategory(snapshot.getCategory());
        skill.setStatus(snapshot.getStatus());
        skill.setOwnerName(snapshot.getOwnerName());
        skill.setDescription(snapshot.getDescription());
        skill.setTags(snapshot.getTags());
        skill.setInstruction(snapshot.getInstruction());
        skill.setSystemPrompt(snapshot.getSystemPrompt());
        skill.setInputSchema(snapshot.getInputSchema());
        skill.setOutputSchema(snapshot.getOutputSchema());
        skill.setRuntimeConfig(snapshot.getRuntimeConfig());
        skill.setExampleInput(snapshot.getExampleInput());
        skill.setExampleOutput(snapshot.getExampleOutput());
        skill.setPromptRefs(snapshot.getPromptRefs());
        skill.setMcpToolRefs(snapshot.getMcpToolRefs());
        skill.setVersion(nextVersion(skill.getVersion()));
        return skillRepository.save(skill);
    }

    public Map<String, Object> testSkill(String skillId, Map<String, Object> input) {
        SkillDefinition skill = getSkill(skillId);
        Map<String, Object> inputSchema = parseJsonMap(skill.getInputSchema());
        Map<String, Object> outputSchema = parseJsonMap(skill.getOutputSchema());
        Map<String, Object> runtimeConfig = parseJsonMap(skill.getRuntimeConfig());

        List<String> missingFields = validateRequiredFields(inputSchema, input);
        List<String> typeErrors = validatePropertyTypes(inputSchema, input);
        boolean valid = missingFields.isEmpty() && typeErrors.isEmpty();

        List<Map<String, Object>> prompts = resolvePrompts(skill.getPromptRefs());
        List<Map<String, Object>> tools = resolveTools(skill.getMcpToolRefs());
        List<String> plan = buildExecutionPlan(skill, prompts, tools);
        List<String> warnings = new ArrayList<>();
        if (prompts.isEmpty()) warnings.add("未绑定 Prompt 模板，试运行结果仅校验输入输出契约。");
        if (tools.isEmpty()) warnings.add("未绑定 MCP 工具，试运行不会执行外部能力调用。");

        Map<String, Object> validation = new LinkedHashMap<>();
        validation.put("valid", valid);
        validation.put("missingFields", missingFields);
        validation.put("typeErrors", typeErrors);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", valid);
        result.put("message", valid ? "试运行校验通过" : "试运行校验未通过");
        result.put("validation", validation);
        result.put("resolvedPrompts", prompts);
        result.put("resolvedTools", tools);
        result.put("runtimeConfig", runtimeConfig);
        result.put("executionPlan", plan);
        result.put("warnings", warnings);
        result.put("mockOutput", buildMockOutput(outputSchema, input));
        result.put("echoInput", input == null ? Map.of() : input);
        return result;
    }

    public List<Map<String, Object>> listUsages(String skillId) {
        List<Map<String, Object>> usages = new ArrayList<>();
        for (Agent agent : agentRepository.findAll()) {
            if (splitCsv(agent.getSkillRefs()).contains(skillId)) {
                usages.add(Map.of(
                    "type", "Agent",
                    "refId", agent.getAgentId(),
                    "name", agent.getName(),
                    "status", agent.getStatus(),
                    "ownerName", agent.getOwnerName() == null ? "" : agent.getOwnerName(),
                    "updatedAt", agent.getUpdatedAt()
                ));
            }
        }
        for (Workflow workflow : workflowRepository.findAll()) {
            if (splitCsv(workflow.getSkillRefs()).contains(skillId)) {
                usages.add(Map.of(
                    "type", "Workflow",
                    "refId", workflow.getWorkflowId(),
                    "name", workflow.getName(),
                    "status", workflow.getStatus(),
                    "ownerName", workflow.getOwnerName() == null ? "" : workflow.getOwnerName(),
                    "updatedAt", workflow.getUpdatedAt()
                ));
            }
        }
        usages.sort((left, right) -> String.valueOf(right.get("updatedAt")).compareTo(String.valueOf(left.get("updatedAt"))));
        return usages;
    }

    private void applySkill(SkillDefinition target, SkillDefinition source) {
        if (isBlank(source.getName())) {
            throw new IllegalArgumentException("技能名称不能为空");
        }
        target.setName(source.getName().trim());
        target.setCategory(isBlank(source.getCategory()) ? "通用技能" : source.getCategory().trim());
        target.setOwnerName(source.getOwnerName());
        target.setDescription(source.getDescription());
        target.setTags(cleanCsv(source.getTags()));
        target.setInstruction(source.getInstruction());
        target.setSystemPrompt(source.getSystemPrompt());
        target.setInputSchema(source.getInputSchema());
        target.setOutputSchema(source.getOutputSchema());
        target.setRuntimeConfig(source.getRuntimeConfig());
        target.setExampleInput(source.getExampleInput());
        target.setExampleOutput(source.getExampleOutput());
        target.setPromptRefs(cleanCsv(source.getPromptRefs()));
        target.setMcpToolRefs(cleanCsv(source.getMcpToolRefs()));
        if (!isBlank(source.getStatus())) {
            target.setStatus(source.getStatus());
        }
    }

    private boolean matchesKeyword(SkillDefinition item, String keyword) {
        if (isBlank(keyword)) {
            return true;
        }
        String value = keyword.trim().toLowerCase();
        return contains(item.getName(), value)
            || contains(item.getDescription(), value)
            || contains(item.getCategory(), value)
            || contains(item.getTags(), value)
            || contains(item.getOwnerName(), value);
    }

    private boolean contains(String source, String keyword) {
        return source != null && source.toLowerCase().contains(keyword);
    }

    private String cleanCsv(String value) {
        if (isBlank(value)) {
            return null;
        }
        return List.of(value.split(",")).stream()
            .map(String::trim)
            .filter(item -> !item.isBlank())
            .distinct()
            .reduce((left, right) -> left + "," + right)
            .orElse(null);
    }

    private String nextVersion(String currentVersion) {
        if (currentVersion == null || currentVersion.isBlank()) {
            return "v1";
        }
        String versionText = currentVersion.trim();
        if (versionText.startsWith("v") || versionText.startsWith("V")) {
            versionText = versionText.substring(1);
        }
        try {
            int version = Integer.parseInt(versionText.split("\\.")[0]);
            return "v" + (version + 1);
        } catch (NumberFormatException ex) {
            return "v1";
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private SkillVersionHistory snapshotOf(SkillDefinition skill, String username, String description) {
        SkillVersionHistory snapshot = new SkillVersionHistory();
        snapshot.setSkillId(skill.getSkillId());
        snapshot.setName(skill.getName());
        snapshot.setCategory(skill.getCategory());
        snapshot.setVersion(skill.getVersion());
        snapshot.setStatus(skill.getStatus());
        snapshot.setOwnerName(skill.getOwnerName());
        snapshot.setDescription(skill.getDescription());
        snapshot.setTags(skill.getTags());
        snapshot.setInstruction(skill.getInstruction());
        snapshot.setSystemPrompt(skill.getSystemPrompt());
        snapshot.setInputSchema(skill.getInputSchema());
        snapshot.setOutputSchema(skill.getOutputSchema());
        snapshot.setRuntimeConfig(skill.getRuntimeConfig());
        snapshot.setExampleInput(skill.getExampleInput());
        snapshot.setExampleOutput(skill.getExampleOutput());
        snapshot.setPromptRefs(skill.getPromptRefs());
        snapshot.setMcpToolRefs(skill.getMcpToolRefs());
        snapshot.setSnapshotBy(username);
        snapshot.setChangeDescription(description);
        return snapshot;
    }

    private Map<String, Object> parseJsonMap(String json) {
        if (isBlank(json)) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    private List<String> validateRequiredFields(Map<String, Object> schema, Map<String, Object> input) {
        List<String> required = toStringList(schema.get("required"));
        List<String> missing = new ArrayList<>();
        Map<String, Object> safeInput = input == null ? Map.of() : input;
        for (String field : required) {
            Object value = safeInput.get(field);
            if (value == null || (value instanceof String text && text.isBlank())) {
                missing.add(field);
            }
        }
        return missing;
    }

    @SuppressWarnings("unchecked")
    private List<String> validatePropertyTypes(Map<String, Object> schema, Map<String, Object> input) {
        List<String> errors = new ArrayList<>();
        if (!(schema.get("properties") instanceof Map<?, ?> props)) {
            return errors;
        }
        Map<String, Object> safeInput = input == null ? Map.of() : input;
        for (Map.Entry<?, ?> entry : props.entrySet()) {
            if (!(entry.getKey() instanceof String key) || !(entry.getValue() instanceof Map<?, ?> property)) {
                continue;
            }
            Object value = safeInput.get(key);
            if (value == null) {
                continue;
            }
            Object type = ((Map<String, Object>) property).get("type");
            if (type instanceof String typeName && !matchesType(typeName, value)) {
                errors.add(key + " 应为 " + typeName);
            }
        }
        return errors;
    }

    private boolean matchesType(String type, Object value) {
        return switch (type) {
            case "string" -> value instanceof String;
            case "integer" -> value instanceof Integer || value instanceof Long;
            case "number" -> value instanceof Number;
            case "boolean" -> value instanceof Boolean;
            case "array" -> value instanceof List<?>;
            case "object" -> value instanceof Map<?, ?>;
            default -> true;
        };
    }

    private List<String> toStringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().filter(String.class::isInstance).map(String.class::cast).toList();
    }

    private List<Map<String, Object>> resolvePrompts(String refs) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (String ref : splitCsv(refs)) {
            PromptTemplate prompt = promptRepository.findByTemplateId(ref).orElse(null);
            if (prompt != null) {
                items.add(Map.of(
                    "templateId", prompt.getTemplateId(),
                    "name", prompt.getName(),
                    "version", prompt.getVersion(),
                    "status", prompt.getStatus()
                ));
            }
        }
        return items;
    }

    private List<Map<String, Object>> resolveTools(String refs) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (String ref : splitCsv(refs)) {
            McpToolConfig tool = toolRepository.findByToolId(ref).orElse(null);
            if (tool != null) {
                items.add(Map.of(
                    "toolId", tool.getToolId(),
                    "name", tool.getName(),
                    "status", tool.getStatus(),
                    "endpoint", tool.getEndpoint() == null ? "" : tool.getEndpoint()
                ));
            }
        }
        return items;
    }

    private List<String> buildExecutionPlan(SkillDefinition skill, List<Map<String, Object>> prompts, List<Map<String, Object>> tools) {
        List<String> plan = new ArrayList<>();
        plan.add("1. 解析输入并按技能契约校验参数。");
        if (!prompts.isEmpty()) {
            plan.add("2. 载入 " + prompts.size() + " 个 Prompt 模板，为技能执行提供上下文。");
        }
        if (!isBlank(skill.getSystemPrompt())) {
            plan.add((prompts.isEmpty() ? "2" : "3") + ". 应用技能级 System Prompt 约束输出风格与边界。");
        }
        if (!tools.isEmpty()) {
            plan.add((prompts.isEmpty() && isBlank(skill.getSystemPrompt()) ? "2" : "4") + ". 根据需要调用 " + tools.size() + " 个 MCP 工具完成外部能力扩展。");
        }
        plan.add((tools.isEmpty() ? "3" : "5") + ". 输出结果并按照输出契约返回结构化内容。");
        return plan;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildMockOutput(Map<String, Object> outputSchema, Map<String, Object> input) {
        Map<String, Object> output = new LinkedHashMap<>();
        if (!(outputSchema.get("properties") instanceof Map<?, ?> props)) {
            output.put("answer", "试运行通过");
            return output;
        }
        for (Map.Entry<?, ?> entry : props.entrySet()) {
            if (!(entry.getKey() instanceof String key) || !(entry.getValue() instanceof Map<?, ?> property)) {
                continue;
            }
            Object type = ((Map<String, Object>) property).get("type");
            output.put(key, sampleValue(type instanceof String text ? text : "string", input));
        }
        return output;
    }

    private Object sampleValue(String type, Map<String, Object> input) {
        return switch (type) {
            case "integer" -> 1;
            case "number" -> 0.95;
            case "boolean" -> true;
            case "array" -> List.of();
            case "object" -> new HashMap<>();
            default -> {
                if (input != null && input.containsKey("query")) {
                    yield "已根据输入 [" + input.get("query") + "] 完成试运行预览";
                }
                yield "试运行输出示例";
            }
        };
    }

    private List<String> splitCsv(String value) {
        if (isBlank(value)) {
            return List.of();
        }
        return List.of(value.split(",")).stream()
            .map(String::trim)
            .filter(item -> !item.isBlank())
            .toList();
    }
}

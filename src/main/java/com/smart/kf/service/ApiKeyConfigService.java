package com.smart.kf.service;

import com.smart.kf.model.ApiKeyConfig;
import com.smart.kf.repository.ApiKeyConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * API Key 配置服务，提供 API Key 和模型配置的 CRUD 及激活操作。
 */
@Service
public class ApiKeyConfigService {

    private static final Logger logger = LoggerFactory.getLogger(ApiKeyConfigService.class);

    @Autowired
    private ApiKeyConfigRepository repository;

    /** 查询所有配置（API Key 脱敏） */
    public List<Map<String, Object>> listAll() {
        return repository.findAll().stream()
                .map(this::toMaskedMap)
                .collect(Collectors.toList());
    }

    /** 根据 ID 查询单条配置（API Key 脱敏） */
    public Map<String, Object> getById(Long id) {
        ApiKeyConfig config = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("配置不存在，id=" + id));
        return toMaskedMap(config);
    }

    /** 根据 ID 查询单条配置（完整，含 API Key，供内部服务使用） */
    public ApiKeyConfig getEntityById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RuntimeException("配置不存在，id=" + id));
    }

    /** 创建新配置 */
    @Transactional
    public ApiKeyConfig create(ApiKeyConfig config) {
        // 新建时默认不激活
        config.setActive(false);
        ApiKeyConfig saved = repository.save(config);
        logger.info("创建 API Key 配置，id={}, name={}", saved.getId(), saved.getName());
        return saved;
    }

    /** 更新配置（不允许通过此接口修改 active 状态） */
    @Transactional
    public ApiKeyConfig update(Long id, ApiKeyConfig patch) {
        ApiKeyConfig existing = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("配置不存在，id=" + id));
        existing.setName(patch.getName());
        existing.setProvider(patch.getProvider());
        existing.setApiUrl(patch.getApiUrl());
        // 只有当 apiKey 不为空且不是掩码时才更新
        if (patch.getApiKey() != null && !patch.getApiKey().isEmpty() && !patch.getApiKey().contains("*")) {
            existing.setApiKey(patch.getApiKey());
        }
        existing.setModelName(patch.getModelName());
        existing.setTemperature(patch.getTemperature());
        existing.setMaxTokens(patch.getMaxTokens());
        existing.setTopP(patch.getTopP());
        existing.setRemark(patch.getRemark());
        if (patch.getAuthType() != null && !patch.getAuthType().isEmpty()) {
            existing.setAuthType(patch.getAuthType());
        }
        ApiKeyConfig saved = repository.save(existing);
        logger.info("更新 API Key 配置，id={}", id);
        return saved;
    }

    /** 删除配置 */
    @Transactional
    public void delete(Long id) {
        ApiKeyConfig config = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("配置不存在，id=" + id));
        if (Boolean.TRUE.equals(config.getActive())) {
            throw new RuntimeException("不能删除当前激活的配置，请先切换到其他配置");
        }
        repository.deleteById(id);
        logger.info("删除 API Key 配置，id={}", id);
    }

    /** 激活指定配置（同时将其他配置设为未激活） */
    @Transactional
    public void activate(Long id) {
        ApiKeyConfig config = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("配置不存在，id=" + id));
        repository.deactivateAll();
        config.setActive(true);
        repository.save(config);
        logger.info("激活 API Key 配置，id={}, name={}", id, config.getName());
    }

    /** 获取当前激活配置（完整，含 API Key，供内部服务使用） */
    public Optional<ApiKeyConfig> getActiveConfig() {
        return repository.findByActiveTrue();
    }

    /** 查询所有配置（完整实体，供内部服务匹配模型使用） */
    public List<ApiKeyConfig> findAll() {
        return repository.findAll();
    }

    /** 将配置转换为脱敏后的 Map */
    private Map<String, Object> toMaskedMap(ApiKeyConfig config) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", config.getId());
        map.put("name", config.getName());
        map.put("provider", config.getProvider());
        map.put("apiUrl", config.getApiUrl());
        map.put("apiKey", maskApiKey(config.getApiKey()));
        map.put("modelName", config.getModelName());
        map.put("active", config.getActive());
        map.put("temperature", config.getTemperature());
        map.put("maxTokens", config.getMaxTokens());
        map.put("topP", config.getTopP());
        map.put("remark", config.getRemark());
        map.put("authType", config.getAuthType());
        map.put("createdAt", config.getCreatedAt());
        map.put("updatedAt", config.getUpdatedAt());
        return map;
    }

    /** 对 API Key 脱敏，保留前4后4位，中间用 * 填充 */
    private String maskApiKey(String key) {
        if (key == null || key.length() <= 8) {
            return "****";
        }
        return key.substring(0, 4) + "****" + key.substring(key.length() - 4);
    }
}

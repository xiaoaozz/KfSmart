package com.smart.kf.controller;

import com.smart.kf.model.ApiKeyConfig;
import com.smart.kf.service.ApiKeyConfigService;
import com.smart.kf.utils.JwtUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * API Key 配置管理 Controller（仅管理员可访问）
 * 路径：/api/v1/admin/api-keys/**
 */
@RestController
@RequestMapping("/api/v1/admin/api-keys")
public class ApiKeyConfigController {

    private static final Logger logger = LoggerFactory.getLogger(ApiKeyConfigController.class);

    @Autowired
    private ApiKeyConfigService apiKeyConfigService;

    @Autowired
    private JwtUtils jwtUtils;

    /** 获取所有 API Key 配置列表（脱敏） */
    @GetMapping
    public ResponseEntity<?> listAll(@RequestHeader("Authorization") String token) {
        try {
            validateAdmin(token);
            List<Map<String, Object>> list = apiKeyConfigService.listAll();
            return ResponseEntity.ok(Map.of("code", 200, "message", "获取 API Key 配置列表成功", "data", list));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("code", 403, "message", e.getMessage()));
        } catch (Exception e) {
            logger.error("查询 API Key 配置列表失败", e);
            return ResponseEntity.status(500).body(Map.of("code", 500, "message", "查询 API Key 配置列表失败: " + e.getMessage()));
        }
    }

    /** 根据 ID 查询单条配置（脱敏） */
    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable Long id,
                                     @RequestHeader("Authorization") String token) {
        try {
            validateAdmin(token);
            Map<String, Object> config = apiKeyConfigService.getById(id);
            return ResponseEntity.ok(Map.of("code", 200, "message", "获取 API Key 配置成功", "data", config));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("code", 403, "message", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", e.getMessage()));
        } catch (Exception e) {
            logger.error("查询 API Key 配置失败，id={}", id, e);
            return ResponseEntity.status(500).body(Map.of("code", 500, "message", "查询 API Key 配置失败: " + e.getMessage()));
        }
    }

    /** 创建新 API Key 配置 */
    @PostMapping
    public ResponseEntity<?> create(@RequestHeader("Authorization") String token,
                                    @RequestBody ApiKeyConfig config) {
        try {
            validateAdmin(token);
            if (config.getName() == null || config.getName().isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("code", 400, "message", "配置名称不能为空"));
            }
            if (config.getApiKey() == null || config.getApiKey().isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("code", 400, "message", "API Key 不能为空"));
            }
            if (config.getApiUrl() == null || config.getApiUrl().isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("code", 400, "message", "API 地址不能为空"));
            }
            if (config.getModelName() == null || config.getModelName().isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("code", 400, "message", "模型名称不能为空"));
            }
            ApiKeyConfig saved = apiKeyConfigService.create(config);
            Map<String, Object> result = new HashMap<>();
            result.put("code", 200);
            result.put("message", "创建成功");
            result.put("id", saved.getId());
            return ResponseEntity.ok(result);
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("code", 403, "message", e.getMessage()));
        } catch (Exception e) {
            logger.error("创建 API Key 配置失败", e);
            return ResponseEntity.status(500).body(Map.of("code", 500, "message", "创建 API Key 配置失败: " + e.getMessage()));
        }
    }

    /** 更新 API Key 配置 */
    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id,
                                    @RequestHeader("Authorization") String token,
                                    @RequestBody ApiKeyConfig config) {
        try {
            validateAdmin(token);
            apiKeyConfigService.update(id, config);
            return ResponseEntity.ok(Map.of("code", 200, "message", "更新成功"));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("code", 403, "message", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", e.getMessage()));
        } catch (Exception e) {
            logger.error("更新 API Key 配置失败，id={}", id, e);
            return ResponseEntity.status(500).body(Map.of("code", 500, "message", "更新 API Key 配置失败: " + e.getMessage()));
        }
    }

    /** 删除 API Key 配置 */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id,
                                    @RequestHeader("Authorization") String token) {
        try {
            validateAdmin(token);
            apiKeyConfigService.delete(id);
            return ResponseEntity.ok(Map.of("code", 200, "message", "删除成功"));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("code", 403, "message", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", e.getMessage()));
        } catch (Exception e) {
            logger.error("删除 API Key 配置失败，id={}", id, e);
            return ResponseEntity.status(500).body(Map.of("code", 500, "message", "删除 API Key 配置失败: " + e.getMessage()));
        }
    }

    /** 激活指定 API Key 配置 */
    @PostMapping("/{id}/activate")
    public ResponseEntity<?> activate(@PathVariable Long id,
                                      @RequestHeader("Authorization") String token) {
        try {
            validateAdmin(token);
            apiKeyConfigService.activate(id);
            return ResponseEntity.ok(Map.of("code", 200, "message", "激活成功"));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("code", 403, "message", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", e.getMessage()));
        } catch (Exception e) {
            logger.error("激活 API Key 配置失败，id={}", id, e);
            return ResponseEntity.status(500).body(Map.of("code", 500, "message", "激活 API Key 配置失败: " + e.getMessage()));
        }
    }

    /** 获取当前激活的配置（脱敏） */
    @GetMapping("/active")
    public ResponseEntity<?> getActive(@RequestHeader("Authorization") String token) {
        try {
            validateAdmin(token);
            return apiKeyConfigService.getActiveConfig()
                    .map(c -> {
                        Map<String, Object> result = apiKeyConfigService.getById(c.getId());
                        Map<String, Object> response = new HashMap<>();
                        response.put("code", 200);
                        response.put("message", "获取激活配置成功");
                        response.put("data", result);
                        return ResponseEntity.ok(response);
                    })
                    .orElseGet(() -> {
                        Map<String, Object> response = new HashMap<>();
                        response.put("code", 200);
                        response.put("message", "暂无激活配置");
                        response.put("data", null);
                        return ResponseEntity.ok(response);
                    });
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("code", 403, "message", e.getMessage()));
        } catch (Exception e) {
            logger.error("获取激活 API Key 配置失败", e);
            return ResponseEntity.status(500).body(Map.of("code", 500, "message", "获取激活 API Key 配置失败: " + e.getMessage()));
        }
    }

    /** 验证是否为管理员 */
    private void validateAdmin(String token) {
        String jwt = token.replace("Bearer ", "");
        String role = jwtUtils.extractRoleFromToken(jwt);
        if (!"ADMIN".equals(role)) {
            throw new SecurityException("无权限，仅管理员可访问");
        }
    }
}

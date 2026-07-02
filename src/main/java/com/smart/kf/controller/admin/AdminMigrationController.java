package com.smart.kf.controller.admin;

import com.smart.kf.exception.CustomException;
import com.smart.kf.service.AdminAuditService;
import com.smart.kf.service.AdminAuthHelper;
import com.smart.kf.utils.JwtUtils;
import com.smart.kf.utils.LogUtils;
import com.smart.kf.utils.MinioMigrationUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 管理员危险操作控制器：MinIO 迁移 / 清空所有数据（从 AdminController 拆分）。
 */
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasAuthority('system:admin')")
@RequiredArgsConstructor
public class AdminMigrationController {

    private final JwtUtils jwtUtils;
    private final AdminAuthHelper adminAuthHelper;
    private final MinioMigrationUtil migrationUtil;
    private final AdminAuditService adminAuditService;

    /** 危险操作二次确认密钥（来自配置/环境变量，留空则端点禁用）。 */
    @Value("${admin.dangerous-operation.clear-key:}")
    private String clearAllDataKey;

    @Value("${admin.dangerous-operation.migrate-key:}")
    private String migrateMinioKey;

    /**
     * 迁移MinIO文件
     *
     * @param token JWT token
     * @param adminKey 管理员密钥（简单验证）
     * @return 迁移报告
     */
    @PostMapping("/migrate-minio")
    public ResponseEntity<?> migrateMinioFiles(
            @RequestHeader("Authorization") String token,
            @RequestParam String adminKey) {

        LogUtils.PerformanceMonitor monitor = LogUtils.startPerformanceMonitor("MIGRATE_MINIO");
        String adminUsername = null;

        try {
            // 验证管理员权限
            String bearerToken = token.replace("Bearer ", "");
            adminUsername = jwtUtils.extractUsernameFromToken(bearerToken);
            adminAuthHelper.validateAdmin(adminUsername);
            Long adminUserId = adminAuthHelper.extractAdminUserId(bearerToken);

            // 二次确认密钥验证（密钥来自配置/环境变量，留空则端点禁用）
            if (migrateMinioKey == null || migrateMinioKey.isBlank() || !migrateMinioKey.equals(adminKey)) {
                LogUtils.logBusiness("MIGRATE_MINIO", adminUsername, "危险操作被拒绝：密钥不匹配或未配置");
                adminAuditService.record("MIGRATE_MINIO", adminUsername, adminUserId,
                        AdminAuditService.STATUS_DENIED, "密钥不匹配或端点未配置");
                Map<String, Object> response = new HashMap<>();
                response.put("code", 403);
                response.put("message", "无效的管理员密钥或该操作已被禁用");
                return ResponseEntity.status(403).body(response);
            }

            LogUtils.logBusiness("MIGRATE_MINIO", adminUsername, "开始MinIO文件迁移");

            MinioMigrationUtil.MigrationReport report = migrationUtil.migrateAllFiles();

            LogUtils.logBusiness("MIGRATE_MINIO", adminUsername,
                "迁移完成: 成功=%d, 跳过=%d, 失败=%d",
                report.successCount, report.skipCount, report.errorCount);

            adminAuditService.record("MIGRATE_MINIO", adminUsername, adminUserId,
                    AdminAuditService.STATUS_SUCCESS,
                    String.format("成功=%d, 跳过=%d, 失败=%d", report.successCount, report.skipCount, report.errorCount));

            monitor.end("MinIO文件迁移完成");

            Map<String, Object> response = new HashMap<>();
            response.put("code", 200);
            response.put("message", "迁移完成");
            response.put("data", Map.of(
                "successCount", report.successCount,
                "skipCount", report.skipCount,
                "errorCount", report.errorCount,
                "errors", report.getErrors()
            ));
            return ResponseEntity.ok(response);

        } catch (CustomException e) {
            LogUtils.logBusinessError("MIGRATE_MINIO", adminUsername, "MinIO文件迁移失败: %s", e, e.getMessage());
            monitor.end("MinIO文件迁移失败: " + e.getMessage());
            adminAuditService.record("MIGRATE_MINIO", adminUsername, null,
                    AdminAuditService.STATUS_FAILED, "CustomException: " + e.getMessage());

            Map<String, Object> response = new HashMap<>();
            response.put("code", e.getStatus().value());
            response.put("message", e.getMessage());
            return ResponseEntity.status(e.getStatus()).body(response);
        } catch (Exception e) {
            LogUtils.logBusinessError("MIGRATE_MINIO", adminUsername, "MinIO文件迁移异常: %s", e, e.getMessage());
            monitor.end("MinIO文件迁移失败: " + e.getMessage());
            adminAuditService.record("MIGRATE_MINIO", adminUsername, null,
                    AdminAuditService.STATUS_FAILED, "Exception: " + e.getMessage());

            Map<String, Object> response = new HashMap<>();
            response.put("code", 500);
            response.put("message", "迁移失败: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * 清空所有数据（危险操作，仅用于测试环境）
     *
     * @param token JWT token
     * @param adminKey 管理员密钥
     * @return 操作结果
     */
    @PostMapping("/clear-all-data")
    public ResponseEntity<?> clearAllData(
            @RequestHeader("Authorization") String token,
            @RequestParam String adminKey) {

        LogUtils.PerformanceMonitor monitor = LogUtils.startPerformanceMonitor("CLEAR_ALL_DATA");
        String adminUsername = null;

        try {
            // 验证管理员权限
            String bearerToken = token.replace("Bearer ", "");
            adminUsername = jwtUtils.extractUsernameFromToken(bearerToken);
            adminAuthHelper.validateAdmin(adminUsername);
            Long adminUserId = adminAuthHelper.extractAdminUserId(bearerToken);

            // 二次确认密钥验证（密钥来自配置/环境变量，留空则端点禁用）
            if (clearAllDataKey == null || clearAllDataKey.isBlank() || !clearAllDataKey.equals(adminKey)) {
                LogUtils.logBusiness("CLEAR_ALL_DATA", adminUsername, "危险操作被拒绝：密钥不匹配或未配置");
                adminAuditService.record("CLEAR_ALL_DATA", adminUsername, adminUserId,
                        AdminAuditService.STATUS_DENIED, "密钥不匹配或端点未配置");
                Map<String, Object> response = new HashMap<>();
                response.put("code", 403);
                response.put("message", "无效的管理员密钥或该操作已被禁用");
                return ResponseEntity.status(403).body(response);
            }

            LogUtils.logBusiness("CLEAR_ALL_DATA", adminUsername, "开始清空所有数据");

            migrationUtil.clearAllData();

            LogUtils.logBusiness("CLEAR_ALL_DATA", adminUsername, "所有数据已清空");

            adminAuditService.record("CLEAR_ALL_DATA", adminUsername, adminUserId,
                    AdminAuditService.STATUS_SUCCESS, "所有数据已清空");

            monitor.end("数据清空完成");

            Map<String, Object> response = new HashMap<>();
            response.put("code", 200);
            response.put("message", "所有数据已清空");
            return ResponseEntity.ok(response);

        } catch (CustomException e) {
            LogUtils.logBusinessError("CLEAR_ALL_DATA", adminUsername, "清空数据失败: %s", e, e.getMessage());
            monitor.end("数据清空失败: " + e.getMessage());
            adminAuditService.record("CLEAR_ALL_DATA", adminUsername, null,
                    AdminAuditService.STATUS_FAILED, "CustomException: " + e.getMessage());

            Map<String, Object> response = new HashMap<>();
            response.put("code", e.getStatus().value());
            response.put("message", e.getMessage());
            return ResponseEntity.status(e.getStatus()).body(response);
        } catch (Exception e) {
            LogUtils.logBusinessError("CLEAR_ALL_DATA", adminUsername, "清空数据异常: %s", e, e.getMessage());
            monitor.end("数据清空失败: " + e.getMessage());
            adminAuditService.record("CLEAR_ALL_DATA", adminUsername, null,
                    AdminAuditService.STATUS_FAILED, "Exception: " + e.getMessage());

            Map<String, Object> response = new HashMap<>();
            response.put("code", 500);
            response.put("message", "清空失败: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
}

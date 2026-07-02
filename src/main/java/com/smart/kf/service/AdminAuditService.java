package com.smart.kf.service;

import com.smart.kf.model.AdminOperationLog;
import com.smart.kf.repository.AdminOperationLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 管理员危险操作审计服务。
 *
 * <p>将 {@code clearAllData} / {@code migrateMinioFiles} 等高危端点的访问与结果
 * 持久化到 {@code admin_operation_logs} 表，与 {@code LogUtils} 文本日志互补。
 *
 * <p><b>容错原则</b>：审计写入失败不得中断被审计的主流程，所有异常被吞掉并记日志，
 * 避免审计副作用导致危险操作行为本身发生改变。
 */
@Service
public class AdminAuditService {

    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_DENIED = "DENIED";
    public static final String STATUS_FAILED = "FAILED";

    private static final Logger logger = LoggerFactory.getLogger(AdminAuditService.class);

    private final AdminOperationLogRepository repository;

    public AdminAuditService(AdminOperationLogRepository repository) {
        this.repository = repository;
    }

    /**
     * 记录一条管理员危险操作审计。
     *
     * @param operation 操作类型（如 CLEAR_ALL_DATA / MIGRATE_MINIO）
     * @param username  管理员用户名（必填）
     * @param userId    管理员用户 ID（可空）
     * @param status    结果：{@link #STATUS_SUCCESS} / {@link #STATUS_DENIED} / {@link #STATUS_FAILED}
     * @param detail    详情（可空）
     * @param ipAddress 来源 IP（可空）
     */
    public void record(String operation, String username, Long userId, String status, String detail, String ipAddress) {
        try {
            AdminOperationLog log = new AdminOperationLog();
            log.setOperation(operation);
            log.setUsername(username != null ? username : "unknown");
            log.setUserId(userId);
            log.setStatus(status);
            log.setDetail(detail);
            log.setIpAddress(ipAddress);
            repository.save(log);
        } catch (Exception e) {
            // 审计落库失败不影响主流程，仅记录日志
            logger.error("写入管理员操作审计失败: operation={}, username={}, status={}",
                    operation, username, status, e);
        }
    }

    /** 简化重载（无 IP）。 */
    public void record(String operation, String username, Long userId, String status, String detail) {
        record(operation, username, userId, status, detail, null);
    }
}

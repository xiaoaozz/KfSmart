package com.smart.kf.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 管理员危险操作审计日志（持久化）。
 *
 * <p>用于记录 {@code clearAllData} / {@code migrateMinioFiles} 等高危端点的访问与结果，
 * 与 {@code LogUtils} 文本日志互补，提供可查询、可追溯的结构化审计落库。
 */
@Data
@Entity
@Table(name = "admin_operation_logs")
public class AdminOperationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 操作类型，如 CLEAR_ALL_DATA / MIGRATE_MINIO */
    @Column(nullable = false, length = 64)
    private String operation;

    /** 触发操作的管理员用户名 */
    @Column(nullable = false, length = 255)
    private String username;

    /** 管理员用户 ID（可空，token 解析失败时为 null） */
    @Column(name = "user_id")
    private Long userId;

    /**
     * 操作结果：
     * <ul>
     *   <li>SUCCESS — 执行成功</li>
     *   <li>DENIED  — 二次确认密钥校验被拒或端点禁用</li>
     *   <li>FAILED  — 执行过程异常</li>
     * </ul>
     */
    @Column(nullable = false, length = 20)
    private String status;

    /** 详情（如被拒原因、迁移统计、异常摘要），上限 TEXT */
    @Column(columnDefinition = "TEXT")
    private String detail;

    /** 来源 IP（可空） */
    @Column(name = "ip_address", length = 255)
    private String ipAddress;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}

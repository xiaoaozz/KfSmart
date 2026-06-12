package com.smart.kf.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 资源权限实体（行级数据权限）
 * 记录特定资源对特定用户/角色/组织的授权信息
 * 示例：
 *   知识库 kb_abc123 对用户 user_123 授予 read 权限
 *   知识库 kb_abc123 对角色 ROLE_KB_MANAGER 授予 admin 权限
 *   知识库 kb_abc123 对组织 org_hr 授予 read 权限
 */
@Data
@Entity
@Table(
    name = "resource_permissions",
    indexes = {
        @Index(name = "idx_resource", columnList = "resource_type, resource_id"),
        @Index(name = "idx_grantee", columnList = "grantee_type, grantee_id")
    }
)
public class ResourcePermission {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "resource_type", nullable = false, length = 64)
    private String resourceType; // 资源类型：kb、doc、agent

    @Column(name = "resource_id", nullable = false, length = 128)
    private String resourceId; // 资源ID（如知识库的 kbId，文档的 fileMd5）

    @Column(name = "grantee_type", nullable = false, length = 32)
    private String granteeType; // 被授权对象类型：user、role、org

    @Column(name = "grantee_id", nullable = false, length = 128)
    private String granteeId; // 被授权对象ID（用户ID/角色编码/组织标签）

    @Column(name = "permission", nullable = false, length = 32)
    private String permission; // 权限级别：read、write、delete、admin

    @Column(name = "granted_by")
    private Long grantedBy; // 授权人用户ID

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;
}

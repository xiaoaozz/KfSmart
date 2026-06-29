package com.smart.kf.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 用户-组织关联表实体
 * 替代 User.orgTags 逗号分隔字符串，支持正确的 FK 约束和高效查询。
 * 迁移步骤：从 users.org_tags 解析逗号分隔数据插入本表。
 */
@Data
@Entity
@Table(
    name = "user_org_memberships",
    uniqueConstraints = @UniqueConstraint(name = "uk_user_org", columnNames = {"user_id", "org_tag"})
)
public class UserOrgMembership {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "org_tag", nullable = false, length = 255)
    private String orgTag;

    @Column(name = "is_primary", nullable = false)
    private boolean isPrimary = false;

    @CreationTimestamp
    @Column(name = "joined_at", updatable = false)
    private LocalDateTime joinedAt;
}

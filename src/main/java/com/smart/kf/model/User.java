package com.smart.kf.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * 注意：使用 @EqualsAndHashCode(of = "id") 而非 @Data，
 * 避免 Hibernate 加载 PersistentSet（roles 集合）时因 hashCode 依赖集合字段
 * 引发 ConcurrentModificationException。
 */
@Getter
@Setter
@ToString(exclude = {"roles"})
@EqualsAndHashCode(of = "id")
@Entity
@Table(name = "users", uniqueConstraints = @UniqueConstraint(columnNames = "username"))
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @JsonIgnore
    @Column(nullable = false)
    private String password;

    /**
     * 兼容字段，保留用于过渡期。
     * 新代码应通过 roles 集合判断用户角色。
     * getter getLegacyRole() 已标记为 @Deprecated，外部应避免直接调用。
     */
    @Getter(onMethod_ = {@Deprecated})
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private Role legacyRole;

    /**
     * RBAC 角色集合（新）
     * 替代 legacyRole 枚举字段，支持多角色
     */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "user_roles",
        joinColumns = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    @JsonIgnoreProperties({"permissions", "users"})
    private Set<com.smart.kf.model.Role> roles = new HashSet<>();

    @Column(name = "org_tags")
    private String orgTags; // 用户所属组织标签，多个用逗号分隔

    @Column(name = "primary_org")
    private String primaryOrg; // 用户主组织标签

    @Column(name = "avatar_url")
    private String avatarUrl; // 用户头像访问地址

    @Column(name = "email", length = 128)
    private String email;

    @Column(name = "phone", length = 32)
    private String phone;

    @Column(name = "bio", columnDefinition = "TEXT")
    private String bio;

    @Column(name = "notification_preferences", columnDefinition = "TEXT")
    private String notificationPreferences;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    /**
     * 兼容旧角色枚举（用于过渡期判断，逐步废弃）
     */
    public enum Role {
        USER, ADMIN
    }

    /**
     * 兼容方法：获取旧 role 字段
     * 优先从 roles 集合推断，若集合为空则返回 legacyRole
     */
    public Role getRole() {
        if (roles != null && !roles.isEmpty()) {
            boolean isAdmin = roles.stream().anyMatch(r -> "ROLE_ADMIN".equals(r.getRoleCode()));
            return isAdmin ? Role.ADMIN : Role.USER;
        }
        return legacyRole;
    }

    /**
     * 兼容方法：设置旧 role 字段（同时同步 legacyRole）
     */
    public void setRole(Role role) {
        this.legacyRole = role;
    }
}

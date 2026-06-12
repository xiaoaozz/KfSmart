package com.smart.kf.model;

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
 * 角色实体（RBAC核心实体）
 * 系统内置角色：ROLE_ADMIN、ROLE_KB_MANAGER、ROLE_USER、ROLE_VIEWER
 * 注意：使用 @EqualsAndHashCode(of = "id") 而非 @Data，
 * 避免 Hibernate 加载 PersistentSet 时因 hashCode 依赖集合字段
 * 引发 ConcurrentModificationException。
 */
@Getter
@Setter
@ToString(exclude = {"permissions"})
@EqualsAndHashCode(of = "id")
@Entity
@Table(name = "roles")
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "role_code", nullable = false, unique = true, length = 64)
    private String roleCode; // 角色编码，如 ROLE_ADMIN、ROLE_KB_MANAGER

    @Column(name = "role_name", nullable = false, length = 128)
    private String roleName; // 角色名称，如 系统管理员、知识库管理员

    @Column(columnDefinition = "TEXT")
    private String description; // 角色描述

    @Column(name = "is_system", nullable = false)
    private boolean isSystem = false; // 是否内置角色（内置角色不可删除）

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "role_permissions",
        joinColumns = @JoinColumn(name = "role_id"),
        inverseJoinColumns = @JoinColumn(name = "perm_id")
    )
    @JsonIgnoreProperties({"roles"})
    private Set<Permission> permissions = new HashSet<>();

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}

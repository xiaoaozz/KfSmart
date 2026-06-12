package com.smart.kf.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * 权限实体（RBAC核心实体）
 * 权限编码规范：<resource_type>:<action>
 *   kb:read       知识库读取
 *   kb:write      知识库创建/编辑
 *   kb:delete     知识库删除
 *   kb:admin      知识库管理（含权限分配）
 *   doc:read      文档读取/下载
 *   doc:write     文档上传/编辑
 *   doc:delete    文档删除
 *   agent:read    Agent读取
 *   agent:write   Agent创建/编辑
 *   agent:run     Agent执行
 *   user:read     用户信息读取
 *   user:write    用户管理
 *   system:admin  系统配置管理
 *   chat:use      聊天功能使用
 * 注意：使用 @EqualsAndHashCode(of = "id") 而非 @Data，
 * 避免 Hibernate 加载 PersistentSet 时因 hashCode 依赖集合字段
 * 引发 ConcurrentModificationException。
 */
@Getter
@Setter
@ToString(exclude = {"roles"})
@EqualsAndHashCode(of = "id")
@Entity
@Table(name = "permissions")
public class Permission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "perm_code", nullable = false, unique = true, length = 128)
    private String permCode; // 权限编码，如 kb:read

    @Column(name = "perm_name", nullable = false, length = 128)
    private String permName; // 权限名称，如 知识库读取

    @Column(name = "resource_type", length = 64)
    private String resourceType; // 资源类型：kb、doc、agent、user、system、chat

    @Column(name = "action", length = 32)
    private String action; // 操作：read、write、delete、admin、use、run

    @Column(columnDefinition = "TEXT")
    private String description; // 权限描述

    @ManyToMany(mappedBy = "permissions")
    @JsonIgnoreProperties({"permissions"})
    private Set<Role> roles = new HashSet<>();

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;
}

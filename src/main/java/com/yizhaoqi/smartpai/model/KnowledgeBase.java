package com.yizhaoqi.smartpai.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 知识库实体类
 * 知识库是独立的文档集合管理单元，与组织标签(OrganizationTag)是不同的概念。
 * 组织标签用于权限控制，知识库用于文档分组管理。
 */
@Data
@Entity
@Table(name = "knowledge_bases")
public class KnowledgeBase {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "kb_id", nullable = false, unique = true)
    private String kbId; // 知识库唯一标识

    @Column(nullable = false)
    private String name; // 知识库名称

    @Column(columnDefinition = "TEXT")
    private String description; // 知识库描述

    @Column(name = "org_tag")
    private String orgTag; // 关联的组织标签（用于权限控制）

    @Column(name = "is_public", nullable = false)
    private boolean isPublic = false; // 是否公开

    @Column(name = "icon")
    private String icon; // 知识库图标标识

    @ManyToOne
    @JoinColumn(name = "created_by", nullable = false)
    @JsonIgnoreProperties({"password", "orgTags", "primaryOrg", "createdAt", "updatedAt", "role"})
    private User createdBy; // 创建者

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
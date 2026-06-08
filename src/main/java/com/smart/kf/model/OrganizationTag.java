package com.smart.kf.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "organization_tags")
public class OrganizationTag {
    @Id
    @Column(name = "tag_id")
    private String tagId; // 标签唯一标识

    @Column(nullable = false)
    private String name; // 标签名称

    @Column(columnDefinition = "TEXT")
    private String description; // 描述

    @Column(name = "parent_tag")
    private String parentTag; // 父标签ID

    @ManyToOne
    @JoinColumn(name = "created_by", nullable = false)
    @JsonIgnoreProperties({"password", "orgTags", "primaryOrg", "createdAt", "updatedAt", "role"})
    private User createdBy; // 创建者

    @CreationTimestamp
    private LocalDateTime createdAt; // 创建时间

    @UpdateTimestamp
    private LocalDateTime updatedAt; // 更新时间
} 
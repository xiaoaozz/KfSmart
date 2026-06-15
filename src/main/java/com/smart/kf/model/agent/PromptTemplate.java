package com.smart.kf.model.agent;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "prompt_templates")
public class PromptTemplate {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "template_id", nullable = false, unique = true, length = 64)
    private String templateId;

    @Column(nullable = false)
    private String name;

    @Column
    private String description;

    @Column(nullable = false)
    private String category;

    @Column(nullable = false)
    private String version = "v1.0";

    @Column(columnDefinition = "LONGTEXT")
    private String systemContent;

    @Column(columnDefinition = "LONGTEXT")
    private String content;

    @Column(columnDefinition = "TEXT")
    private String variables;

    @Column(columnDefinition = "TEXT")
    private String tags;

    @Column(nullable = false)
    private String status = "启用";

    @Column(name = "is_public", nullable = false)
    private Boolean isPublic = false;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}

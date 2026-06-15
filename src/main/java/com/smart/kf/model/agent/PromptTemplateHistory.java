package com.smart.kf.model.agent;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

@Data
@Entity
@Table(name = "prompt_template_histories")
public class PromptTemplateHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "template_id", nullable = false, length = 64)
    private String templateId;

    @Column(nullable = false)
    private String name;

    @Column
    private String description;

    @Column(nullable = false)
    private String category;

    @Column(nullable = false)
    private String version;

    @Column(columnDefinition = "LONGTEXT")
    private String systemContent;

    @Column(columnDefinition = "LONGTEXT")
    private String content;

    @Column(length = 500)
    private String variables;

    @Column(columnDefinition = "TEXT")
    private String tags;

    @Column(length = 20)
    private String status;

    @Column(name = "snapshot_by", length = 100)
    private String snapshotBy;

    @Column(name = "change_description", length = 500)
    private String changeDescription;

    @CreationTimestamp
    @Column(name = "snapshot_at", nullable = false, updatable = false)
    private java.time.LocalDateTime snapshotAt;
}
package com.smart.kf.model.workflow;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "workflows")
public class Workflow {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workflow_id", nullable = false, unique = true, length = 64)
    private String workflowId;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private String status = "草稿";

    @Column(name = "owner_name")
    private String ownerName;

    @Column
    private String tags;

    @Column(name = "nodes_json", columnDefinition = "LONGTEXT")
    private String nodesJson;

    @Column(name = "edges_json", columnDefinition = "LONGTEXT")
    private String edgesJson;

    @Column(name = "permission_scope")
    private String permissionScope = "组织内";

    @Column(name = "knowledge_bases", columnDefinition = "TEXT")
    private String knowledgeBases;

    @Column(name = "prompt_refs", columnDefinition = "TEXT")
    private String promptRefs;

    @Column(name = "mcp_tools", columnDefinition = "TEXT")
    private String mcpTools;

    @Column(name = "models", columnDefinition = "TEXT")
    private String models;

    @Column(name = "call_count")
    private long callCount = 0;

    @Column(name = "success_count")
    private long successCount = 0;

    @Column(name = "failure_count")
    private long failureCount = 0;

    @Column(name = "avg_duration_ms")
    private long avgDurationMs = 0;

    @Column(name = "install_count", nullable = false)
    private Long installCount = 0L;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    public void normalizeDefaults() {
        if (installCount == null) {
            installCount = 0L;
        }
    }

    @Transient
    @Getter(AccessLevel.NONE)
    private Long successRate;

    public Long getSuccessRate() {
        if (successRate != null) return successRate;
        return callCount == 0 ? 100L : Math.round(successCount * 100.0 / callCount);
    }
}

package com.smart.kf.model.agent;

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
@Table(name = "agents")
public class Agent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "agent_id", nullable = false, unique = true, length = 64)
    private String agentId;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private String status = "draft";

    @Column(name = "owner_name")
    private String ownerName;

    /**
     * 所有者用户 FK（替代 ownerName 字符串，Phase 2 新增）
     */
    @Column(name = "owner_id")
    private Long ownerId;

    @Column
    private String tags;

    @Column(name = "system_prompt", columnDefinition = "TEXT")
    private String systemPrompt;

    @Column(name = "user_prompt", columnDefinition = "TEXT")
    private String userPrompt;

    @Column(name = "avatar_emoji", length = 32)
    private String avatarEmoji = "🤖";

    @Column(name = "temperature")
    private Double temperature = 0.7;

    @Column(name = "top_p")
    private Double topP = 0.8;

    @Column(name = "max_tokens")
    private Integer maxTokens = 4000;

    @Column(name = "max_iterations")
    private Integer maxIterations = 10;

    @Column(name = "memory_types")
    private String memoryTypes;

    @Column(name = "permission_scope")
    private String permissionScope = "组织内";

    @Column(name = "knowledge_bases", columnDefinition = "TEXT")
    private String knowledgeBases;

    @Column(name = "prompt_refs", columnDefinition = "TEXT")
    private String promptRefs;

    @Column(name = "mcp_tools", columnDefinition = "TEXT")
    private String mcpTools;

    @Column(name = "skill_refs", columnDefinition = "TEXT")
    private String skillRefs;

    @Column(name = "models", columnDefinition = "TEXT")
    private String models;

    @Column(name = "call_count")
    private Long callCount = 0L;

    @Column(name = "success_count")
    private Long successCount = 0L;

    @Column(name = "failure_count")
    private Long failureCount = 0L;

    @Column(name = "avg_duration_ms")
    private Long avgDurationMs = 0L;

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

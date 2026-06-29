package com.smart.kf.model.agent;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "skill_definitions")
public class SkillDefinition {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "skill_id", nullable = false, unique = true, length = 64)
    private String skillId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 50)
    private String category;

    @Column(nullable = false, length = 20)
    private String status = "草稿";

    @Column(name = "owner_name", length = 100)
    private String ownerName;

    /**
     * 所有者用户 FK（替代 ownerName 字符串，Phase 2 新增）
     */
    @Column(name = "owner_id")
    private Long ownerId;

    @Column(length = 500)
    private String description;

    @Column(columnDefinition = "TEXT")
    private String tags;

    @Column(name = "instruction", columnDefinition = "LONGTEXT")
    private String instruction;

    @Column(name = "system_prompt", columnDefinition = "LONGTEXT")
    private String systemPrompt;

    @Column(name = "input_schema", columnDefinition = "LONGTEXT")
    private String inputSchema;

    @Column(name = "output_schema", columnDefinition = "LONGTEXT")
    private String outputSchema;

    @Column(name = "runtime_config", columnDefinition = "LONGTEXT")
    private String runtimeConfig;

    @Column(name = "example_input", columnDefinition = "LONGTEXT")
    private String exampleInput;

    @Column(name = "example_output", columnDefinition = "LONGTEXT")
    private String exampleOutput;

    @Column(name = "prompt_refs", columnDefinition = "TEXT")
    private String promptRefs;

    @Column(name = "mcp_tool_refs", columnDefinition = "TEXT")
    private String mcpToolRefs;

    @Column(nullable = false, length = 20)
    private String version = "v1";

    @Column(name = "call_count")
    private long callCount = 0;

    @Column(name = "success_count")
    private long successCount = 0;

    @Column(name = "avg_duration_ms")
    private long avgDurationMs = 0;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void onCreate() {
        if (version == null || version.isBlank()) {
            version = "v1";
        }
        if (status == null || status.isBlank()) {
            status = "草稿";
        }
    }
}

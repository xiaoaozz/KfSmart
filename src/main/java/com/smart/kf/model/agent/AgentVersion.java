package com.smart.kf.model.agent;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "agent_versions")
public class AgentVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "version_id", nullable = false, unique = true, length = 64)
    private String versionId;

    @Column(name = "agent_id", nullable = false, length = 64)
    private String agentId;

    @Column(name = "version_number")
    private Integer versionNumber;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column
    private String status = "草稿";

    @Column(name = "system_prompt", columnDefinition = "TEXT")
    private String systemPrompt;

    @Column(name = "user_prompt", columnDefinition = "TEXT")
    private String userPrompt;

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

    @Column(name = "knowledge_bases", columnDefinition = "TEXT")
    private String knowledgeBases;

    @Column(name = "prompt_refs", columnDefinition = "TEXT")
    private String promptRefs;

    @Column(name = "mcp_tools", columnDefinition = "TEXT")
    private String mcpTools;

    @Column(name = "models", columnDefinition = "TEXT")
    private String models;

    @Column(name = "snapshot_by", length = 100)
    private String snapshotBy;

    @Column(name = "change_description", length = 500)
    private String changeDescription;

    @Column(name = "is_active")
    private Boolean isActive = false;

    @CreationTimestamp
    @Column(name = "snapshot_at")
    private LocalDateTime snapshotAt;
}

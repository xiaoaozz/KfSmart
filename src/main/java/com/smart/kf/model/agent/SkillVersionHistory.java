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
@Table(name = "skill_version_histories")
public class SkillVersionHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "skill_id", nullable = false, length = 64)
    private String skillId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 50)
    private String category;

    @Column(nullable = false, length = 20)
    private String version;

    @Column(length = 20)
    private String status;

    @Column(name = "owner_name", length = 100)
    private String ownerName;

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

    @Column(name = "snapshot_by", length = 100)
    private String snapshotBy;

    @Column(name = "change_description", length = 500)
    private String changeDescription;

    @CreationTimestamp
    @Column(name = "snapshot_at", nullable = false, updatable = false)
    private LocalDateTime snapshotAt;
}

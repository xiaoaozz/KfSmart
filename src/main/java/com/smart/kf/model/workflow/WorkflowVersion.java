package com.smart.kf.model.workflow;

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
@Table(name = "workflow_versions")
public class WorkflowVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "version_id", nullable = false, unique = true, length = 64)
    private String versionId;

    @Column(name = "workflow_id", nullable = false, length = 64)
    private String workflowId;

    @Column(name = "version_number")
    private Integer versionNumber;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private String status = "草稿";

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

    @Column(name = "nodes_json", columnDefinition = "LONGTEXT")
    private String nodesJson;

    @Column(name = "edges_json", columnDefinition = "LONGTEXT")
    private String edgesJson;

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

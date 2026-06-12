package com.smart.kf.model.agent;

import com.fasterxml.jackson.annotation.JsonProperty;
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
@Table(name = "mcp_tool_configs")
public class McpToolConfig {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tool_id", nullable = false, unique = true, length = 64)
    private String toolId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String type = "MCP";

    @Column(nullable = false)
    private String status = "在线";

    @Column(nullable = false)
    private String endpoint;

    @Column(name = "auth_type")
    private String authType = "API Key";

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Column(name = "api_key", length = 500)
    private String apiKey;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "call_count")
    private long callCount = 0;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

}

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

    @Column(name = "tool_name", length = 128)
    private String toolName;

    @Column(name = "request_mode", length = 32)
    private String requestMode = "MCP_JSON_RPC";

    @Column(name = "protocol_version", length = 32)
    private String protocolVersion = "2024-11-05";

    @Column
    private String endpoint;

    @Column(name = "auth_type")
    private String authType = "API Key";

    @Column(name = "auth_header_name", length = 100)
    private String authHeaderName = "X-API-Key";

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Column(name = "api_key", length = 500)
    private String apiKey;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "input_schema", columnDefinition = "TEXT")
    private String inputSchema;

    @Column(name = "last_test_status", length = 20)
    private String lastTestStatus;

    @Column(name = "last_test_message", length = 1000)
    private String lastTestMessage;

    @Column(name = "last_test_at")
    private LocalDateTime lastTestAt;

    @Column(name = "call_count")
    private long callCount = 0;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

}

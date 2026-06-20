package com.smart.kf.model.agent;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "agent_execution_logs")
public class AgentExecutionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "execution_id", nullable = false, unique = true, length = 64)
    private String executionId;

    @Column(name = "agent_id", nullable = false, length = 64)
    private String agentId;

    @Column(name = "version_id", length = 64)
    private String versionId;

    @Column(name = "trigger_type", length = 30)
    private String triggerType;

    @Column(nullable = false, length = 20)
    private String status = "running";

    @Column(name = "input_json", columnDefinition = "LONGTEXT")
    private String inputJson;

    @Column(name = "output_json", columnDefinition = "LONGTEXT")
    private String outputJson;

    @Column(name = "trace_json", columnDefinition = "LONGTEXT")
    private String traceJson;

    @Column(name = "iterations")
    private Integer iterations = 0;

    @Column(name = "started_by", length = 100)
    private String startedBy;

    @CreationTimestamp
    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "duration_ms")
    private Long durationMs = 0L;

    @Column(name = "prompt_tokens")
    private Integer promptTokens = 0;

    @Column(name = "completion_tokens")
    private Integer completionTokens = 0;

    @Column(name = "total_tokens")
    private Integer totalTokens = 0;

    @Column(name = "cost", precision = 10, scale = 6)
    private BigDecimal cost = BigDecimal.ZERO;

    @Column(name = "model_cost", precision = 10, scale = 6)
    private BigDecimal modelCost = BigDecimal.ZERO;

    @Column(name = "tool_cost", precision = 10, scale = 6)
    private BigDecimal toolCost = BigDecimal.ZERO;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;
}

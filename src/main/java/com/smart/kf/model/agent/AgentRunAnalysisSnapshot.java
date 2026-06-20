package com.smart.kf.model.agent;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "agent_run_analysis_snapshots")
public class AgentRunAnalysisSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "snapshot_date", nullable = false, unique = true)
    private LocalDate snapshotDate;

    @Column(name = "run_count", nullable = false)
    private Long runCount = 0L;

    @Column(name = "success_count", nullable = false)
    private Long successCount = 0L;

    @Column(name = "failure_count", nullable = false)
    private Long failureCount = 0L;

    @Column(name = "duration_total_ms", nullable = false)
    private Long durationTotalMs = 0L;

    @Column(name = "token_usage", nullable = false)
    private Long tokenUsage = 0L;

    @Column(name = "model_cost", precision = 16, scale = 6, nullable = false)
    private BigDecimal modelCost = BigDecimal.ZERO;

    @Column(name = "tool_cost", precision = 16, scale = 6, nullable = false)
    private BigDecimal toolCost = BigDecimal.ZERO;

    @Column(name = "hot_agents_json", columnDefinition = "LONGTEXT")
    private String hotAgentsJson;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    public void normalizeDefaults() {
        if (runCount == null) runCount = 0L;
        if (successCount == null) successCount = 0L;
        if (failureCount == null) failureCount = 0L;
        if (durationTotalMs == null) durationTotalMs = 0L;
        if (tokenUsage == null) tokenUsage = 0L;
        if (modelCost == null) modelCost = BigDecimal.ZERO;
        if (toolCost == null) toolCost = BigDecimal.ZERO;
    }
}

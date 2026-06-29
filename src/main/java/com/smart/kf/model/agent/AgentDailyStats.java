package com.smart.kf.model.agent;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Agent 每日明细统计表
 * 替代 AgentRunAnalysisSnapshot 的全局聚合，支持按 agent_id 维度查询历史表现。
 * UNIQUE KEY (agent_id, snapshot_date) 保证每个 agent 每天一条。
 */
@Data
@Entity
@Table(
    name = "agent_daily_stats",
    uniqueConstraints = @UniqueConstraint(name = "uk_agent_daily", columnNames = {"agent_id", "snapshot_date"})
)
public class AgentDailyStats {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "agent_id", nullable = false, length = 64)
    private String agentId;

    @Column(name = "snapshot_date", nullable = false)
    private LocalDate snapshotDate;

    @Column(name = "run_count", nullable = false)
    private long runCount = 0;

    @Column(name = "success_count", nullable = false)
    private long successCount = 0;

    @Column(name = "failure_count", nullable = false)
    private long failureCount = 0;

    @Column(name = "duration_total_ms", nullable = false)
    private long durationTotalMs = 0;

    @Column(name = "token_usage", nullable = false)
    private long tokenUsage = 0;

    @Column(name = "model_cost", precision = 16, scale = 6, nullable = false)
    private BigDecimal modelCost = BigDecimal.ZERO;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}

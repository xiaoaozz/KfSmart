package com.smart.kf.model.agent;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Agent 独立统计表
 * 将高频更新的统计字段从 agents 主表剥离，避免热点行竞争。
 * 写入方式：每次执行完成后 UPSERT 本表，agents 主表统计字段仅作展示缓存。
 */
@Data
@Entity
@Table(name = "agent_stats")
public class AgentStats {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "agent_id", nullable = false, unique = true, length = 64)
    private String agentId;

    @Column(name = "call_count", nullable = false)
    private long callCount = 0;

    @Column(name = "success_count", nullable = false)
    private long successCount = 0;

    @Column(name = "failure_count", nullable = false)
    private long failureCount = 0;

    @Column(name = "avg_duration_ms", nullable = false)
    private long avgDurationMs = 0;

    @Column(name = "total_duration_ms", nullable = false)
    private long totalDurationMs = 0;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public long getSuccessRate() {
        return callCount == 0 ? 100L : Math.round(successCount * 100.0 / callCount);
    }
}

package com.smart.kf.model.workflow;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Workflow 独立统计表
 * 将高频更新的统计字段从 workflows 主表剥离，避免热点行竞争。
 */
@Data
@Entity
@Table(name = "workflow_stats")
public class WorkflowStats {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workflow_id", nullable = false, unique = true, length = 64)
    private String workflowId;

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

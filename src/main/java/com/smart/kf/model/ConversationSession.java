package com.smart.kf.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 对话会话表
 * 将多轮 Q&A（Conversation）归组到同一次会话。
 * sessionId 与 Redis 中的对话 key 保持一致。
 */
@Data
@Entity
@Table(
    name = "conversation_sessions",
    indexes = {
        @Index(name = "idx_cs_user_id",      columnList = "user_id"),
        @Index(name = "idx_cs_session_type", columnList = "session_type")
    }
)
public class ConversationSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false, unique = true, length = 64)
    private String sessionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * 会话标题（首条问题截断 50 字）
     */
    @Column(name = "title", length = 255)
    private String title;

    /**
     * 会话类型：chat / kb / agent / workflow
     */
    @Column(name = "session_type", length = 32)
    private String sessionType = "chat";

    /**
     * 关联资源 ID（kbId / agentId / workflowId，与 sessionType 对应）
     */
    @Column(name = "target_id", length = 128)
    private String targetId;

    @Column(name = "message_count", nullable = false)
    private int messageCount = 0;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}

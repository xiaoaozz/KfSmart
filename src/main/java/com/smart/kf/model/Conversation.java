package com.smart.kf.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "conversations", indexes = {
        @Index(name = "idx_conv_user_id",    columnList = "user_id"),
        @Index(name = "idx_conv_session_id", columnList = "session_id"),
        @Index(name = "idx_conv_timestamp",  columnList = "timestamp")
})
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * 所属会话 ID，关联 ConversationSession.sessionId（Phase 3 新增）
     */
    @Column(name = "session_id", length = 64)
    private String sessionId;

    /**
     * 关联知识库 ID（可空，表示普通聊天时为 null）
     */
    @Column(name = "kb_id", length = 255)
    private String kbId;

    /**
     * 关联 Agent ID（可空）
     */
    @Column(name = "agent_id", length = 64)
    private String agentId;

    /**
     * 本次对话使用的模型名称
     */
    @Column(name = "model_name", length = 100)
    private String modelName;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String question;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String answer;

    @CreationTimestamp
    private LocalDateTime timestamp;
}
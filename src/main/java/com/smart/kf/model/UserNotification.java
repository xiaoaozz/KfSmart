package com.smart.kf.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 用户通知消息实体
 * 当管理员操作用户的知识库或文档时，系统自动生成此类通知告知对应用户
 */
@Data
@Entity
@Table(name = "user_notifications")
public class UserNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 通知接收者（目标用户）用户名（历史审计保留，新代码请使用 recipientId）
     */
    @Column(name = "recipient_username", nullable = false)
    private String recipientUsername;

    /**
     * 操作执行者（admin）用户名（历史审计保留，新代码请使用 operatorId）
     */
    @Column(name = "operator_username", nullable = false)
    private String operatorUsername;

    /**
     * 接收者 FK（替代 recipientUsername，Phase 2 新增）
     */
    @Column(name = "recipient_id")
    private Long recipientId;

    /**
     * 操作者 FK（替代 operatorUsername，Phase 2 新增）
     */
    @Column(name = "operator_id")
    private Long operatorId;

    /**
     * 操作类型：UPDATE_KB / DELETE_KB / UPDATE_DOCUMENT / DELETE_DOCUMENT
     */
    @Column(name = "action_type", nullable = false)
    private String actionType;

    /**
     * 被操作的资源标识（kbId 或 fileMd5）
     */
    @Column(name = "resource_id")
    private String resourceId;

    /**
     * 被操作的资源名称（知识库名称 或 文件名）
     */
    @Column(name = "resource_name")
    private String resourceName;

    /**
     * 通知内容
     */
    @Column(name = "message", nullable = false, columnDefinition = "TEXT")
    private String message;

    /**
     * 是否已读
     * 使用 @JsonProperty 确保 Jackson 序列化时字段名为 "isRead" 而非 "read"
     * （Lombok @Data 为 primitive boolean 生成 isXxx() getter，Jackson 默认会去掉 is 前缀）
     */
    @JsonProperty("isRead")
    @Column(name = "is_read", nullable = false)
    private boolean isRead = false;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;
}

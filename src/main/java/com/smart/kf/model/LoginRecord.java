package com.smart.kf.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 用户登录记录实体
 * 记录每次用户登录的详细信息，包括 IP、设备、地点、状态等
 */
@Data
@Entity
@Table(name = "login_records")
public class LoginRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false)
    private String username;

    @Column(name = "login_time", nullable = false)
    private LocalDateTime loginTime;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "device_info")
    private String deviceInfo;

    @Column(name = "location")
    private String location;

    /**
     * 登录状态：SUCCESS / FAILED
     */
    @Column(nullable = false, length = 20)
    private String status;

    /**
     * 失败原因（仅在 status=FAILED 时记录）
     */
    @Column(name = "fail_reason", columnDefinition = "TEXT")
    private String failReason;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
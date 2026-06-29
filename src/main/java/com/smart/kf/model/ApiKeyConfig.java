package com.smart.kf.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * API Key 配置实体，用于存储问答模型的 API Key 和模型配置信息。
 * 仅管理员可管理此配置。
 */
@Data
@Entity
@Table(name = "api_key_configs")
public class ApiKeyConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 配置名称，便于识别 */
    @Column(nullable = false, length = 100)
    private String name;

    /** 模型提供商，如 deepseek、openai、qwen 等 */
    @Column(nullable = false, length = 50)
    private String provider;

    /** API 请求地址 */
    @Column(nullable = false, length = 500)
    private String apiUrl;

    /** API Key（存储时脱敏展示，查询不返回完整值） */
    @Column(nullable = false, length = 500)
    private String apiKey;

    /** 模型名称，如 deepseek-chat、gpt-4o、qwen-max 等 */
    @Column(name = "model_name", nullable = false, length = 100)
    private String modelName;

    /** 是否为当前激活的配置（全局唯一激活） */
    @Column(nullable = false)
    private Boolean active = false;

    /** 采样温度 */
    @Column
    private Double temperature = 0.3;

    /** 最大输出 tokens */
    @Column
    private Integer maxTokens = 2000;

    /** nucleus top-p */
    @Column(name = "top_p")
    private Double topP = 0.9;

    /**
     * 身份验证方式：
     * - bearer       : 标准 HTTP Bearer Token（Authorization: Bearer {key}），适用于智谱AI、大多数自托管模型等
     * - openai       : OpenAI 兼容格式（同 Bearer，但路径固定走 /v1/chat/completions，适用于 OpenAI、DeepSeek、通义千问等）
     * - anthropic    : Anthropic 格式（x-api-key: {key} + anthropic-version 请求头，适用于 Claude 系列）
     */
    @Column(nullable = false, length = 20)
    private String authType = "bearer";

    /** 备注说明 */
    @Column(length = 500)
    private String remark;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}

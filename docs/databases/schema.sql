-- =============================================================
-- KfSmart / knowflow 数据库 Schema
-- 此文件为唯一 DDL 来源，原 ddl.sql 已废弃删除
-- 维护说明：修改 JPA Entity 后同步更新此文件
-- 最后更新：2026-06-28
-- MySQL 8.0+  ENGINE=InnoDB  CHARSET=utf8mb4_unicode_ci
-- =============================================================
--
-- ★ 新库初始化：完整执行本文件
-- ★ 存量库升级：仅执行文件末尾 [存量库修复脚本] 部分
-- =============================================================

-- =====================================================
-- Section 1: 用户与权限体系
-- =====================================================

CREATE TABLE `users` (
  `id`                        bigint       NOT NULL AUTO_INCREMENT,
  `username`                  varchar(255) NOT NULL,
  `password`                  varchar(255) NOT NULL,
  `role`                      enum('ADMIN','USER') NOT NULL COMMENT '兼容旧版单角色字段，RBAC 权限以 user_roles 为准',
  `org_tags`                  varchar(255)          DEFAULT NULL  COMMENT '所属组织标签，逗号分隔',
  `primary_org`               varchar(255)          DEFAULT NULL,
  `avatar_url`                varchar(255)          DEFAULT NULL,
  `email`                     varchar(128)          DEFAULT NULL,
  `phone`                     varchar(32)           DEFAULT NULL,
  `bio`                       text,
  `notification_preferences`  text,
  `created_at`                datetime(6)           DEFAULT NULL,
  `updated_at`                datetime(6)           DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

CREATE TABLE `roles` (
  `id`          bigint       NOT NULL AUTO_INCREMENT,
  `role_code`   varchar(64)  NOT NULL,
  `role_name`   varchar(128) NOT NULL,
  `description` text,
  `is_system`   bit(1)       NOT NULL,
  `created_at`  datetime(6)           DEFAULT NULL,
  `updated_at`  datetime(6)           DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_code` (`role_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色表';

CREATE TABLE `permissions` (
  `id`            bigint       NOT NULL AUTO_INCREMENT,
  `perm_code`     varchar(128) NOT NULL,
  `perm_name`     varchar(128) NOT NULL,
  `resource_type` varchar(64)           DEFAULT NULL,
  `action`        varchar(32)           DEFAULT NULL,
  `description`   text,
  `created_at`    datetime(6)           DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_perm_code` (`perm_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='权限定义表';

CREATE TABLE `user_roles` (
  `user_id` bigint NOT NULL,
  `role_id` bigint NOT NULL,
  PRIMARY KEY (`user_id`, `role_id`),
  KEY `idx_user_roles_role` (`role_id`),
  CONSTRAINT `fk_user_roles_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_user_roles_role` FOREIGN KEY (`role_id`) REFERENCES `roles` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户-角色关联表';

CREATE TABLE `role_permissions` (
  `role_id` bigint NOT NULL,
  `perm_id` bigint NOT NULL,
  PRIMARY KEY (`role_id`, `perm_id`),
  KEY `idx_role_perms_perm` (`perm_id`),
  CONSTRAINT `fk_role_perms_role` FOREIGN KEY (`role_id`) REFERENCES `roles` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_role_perms_perm` FOREIGN KEY (`perm_id`) REFERENCES `permissions` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色-权限关联表';

CREATE TABLE `resource_permissions` (
  `id`            bigint       NOT NULL AUTO_INCREMENT,
  `resource_type` varchar(64)  NOT NULL,
  `resource_id`   varchar(128) NOT NULL,
  `grantee_type`  varchar(32)  NOT NULL,
  `grantee_id`    varchar(128) NOT NULL,
  `permission`    varchar(32)  NOT NULL,
  `granted_by`    bigint                DEFAULT NULL,
  `created_at`    datetime(6)           DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_resource` (`resource_type`, `resource_id`),
  KEY `idx_grantee`  (`grantee_type`, `grantee_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='资源级权限授权表';

-- =====================================================
-- Section 2: 组织与知识库
-- =====================================================

CREATE TABLE `organization_tags` (
  `tag_id`      varchar(255) NOT NULL COMMENT '自然主键，由业务层生成',
  `name`        varchar(255) NOT NULL,
  `description` text,
  `parent_tag`  varchar(255)          DEFAULT NULL,
  `created_by`  bigint       NOT NULL,
  `created_at`  datetime(6)           DEFAULT NULL,
  `updated_at`  datetime(6)           DEFAULT NULL,
  PRIMARY KEY (`tag_id`),
  KEY `idx_org_tag_created_by` (`created_by`),
  CONSTRAINT `fk_org_tag_created_by` FOREIGN KEY (`created_by`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='组织标签表';

CREATE TABLE `organization_tag_i18n` (
  `id`          bigint       NOT NULL AUTO_INCREMENT,
  `tag_id`      varchar(100) NOT NULL,
  `lang`        varchar(10)  NOT NULL,
  `name`        varchar(200)          DEFAULT NULL,
  `description` text,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tag_lang` (`tag_id`, `lang`),
  KEY `idx_tagi18n_tag_id` (`tag_id`),
  CONSTRAINT `fk_tagi18n_tag` FOREIGN KEY (`tag_id`) REFERENCES `organization_tags` (`tag_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='组织标签国际化表';

CREATE TABLE `knowledge_bases` (
  `id`          bigint       NOT NULL AUTO_INCREMENT,
  `kb_id`       varchar(255) NOT NULL,
  `name`        varchar(255) NOT NULL,
  `description` text,
  `org_tag`     varchar(255)          DEFAULT NULL,
  `is_public`   bit(1)       NOT NULL,
  `icon`        varchar(255)          DEFAULT NULL,
  `created_by`  bigint       NOT NULL,
  `created_at`  datetime(6)           DEFAULT NULL,
  `updated_at`  datetime(6)           DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_kb_id` (`kb_id`),
  KEY `idx_kb_org_tag`    (`org_tag`),
  KEY `idx_kb_created_by` (`created_by`),
  CONSTRAINT `fk_kb_created_by` FOREIGN KEY (`created_by`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库表';

CREATE TABLE `knowledge_base_i18n` (
  `id`          bigint       NOT NULL AUTO_INCREMENT,
  `kb_id`       varchar(255) NOT NULL,
  `lang`        varchar(10)  NOT NULL,
  `name`        varchar(255)          DEFAULT NULL,
  `description` text,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_kb_lang` (`kb_id`, `lang`),
  KEY `idx_kbi18n_kb_id` (`kb_id`),
  CONSTRAINT `fk_kbi18n_kb` FOREIGN KEY (`kb_id`) REFERENCES `knowledge_bases` (`kb_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库国际化表';

-- =====================================================
-- Section 3: 文件与向量存储
-- =====================================================

CREATE TABLE `file_upload` (
  `id`         bigint       NOT NULL AUTO_INCREMENT,
  `file_md5`   varchar(32)  NOT NULL,
  `file_name`  varchar(255)          DEFAULT NULL,
  `total_size` bigint       NOT NULL,
  `status`     int          NOT NULL,
  `user_id`    varchar(64)  NOT NULL  COMMENT '存储 users.id 字符串形式（遗留类型不匹配，待迁移）',
  `org_tag`    varchar(255)          DEFAULT NULL,
  `kb_id`      varchar(255)          DEFAULT NULL,
  `is_public`  bit(1)       NOT NULL,
  `created_at` datetime(6)           DEFAULT NULL,
  `merged_at`  datetime(6)           DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_md5_user`    (`file_md5`, `user_id`),
  KEY `idx_file_user_id` (`user_id`),
  KEY `idx_file_org_tag`  (`org_tag`),
  KEY `idx_file_kb_id`    (`kb_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文件上传记录';

CREATE TABLE `chunk_info` (
  `id`           bigint       NOT NULL AUTO_INCREMENT,
  `file_md5`     varchar(255)          DEFAULT NULL,
  `chunk_index`  int          NOT NULL,
  `chunk_md5`    varchar(255)          DEFAULT NULL,
  `storage_path` varchar(255)          DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_chunk_file_md5` (`file_md5`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文件分块信息表';

CREATE TABLE `document_vectors` (
  `vector_id`     bigint      NOT NULL AUTO_INCREMENT,
  `file_md5`      varchar(32) NOT NULL,
  `chunk_id`      int         NOT NULL,
  `text_content`  longtext,
  `model_version` varchar(32)          DEFAULT NULL,
  `user_id`       varchar(64) NOT NULL,
  `org_tag`       varchar(50)          DEFAULT NULL,
  `is_public`     bit(1)      NOT NULL,
  PRIMARY KEY (`vector_id`),
  KEY `idx_dv_file_md5` (`file_md5`),
  KEY `idx_dv_user_id`  (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文档向量存储表';

-- =====================================================
-- Section 4: 对话、登录记录与通知
-- =====================================================

CREATE TABLE `conversations` (
  `id`        bigint NOT NULL AUTO_INCREMENT,
  `user_id`   bigint NOT NULL,
  `question`  text   NOT NULL,
  `answer`    text   NOT NULL,
  `timestamp` datetime(6)     DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_conv_user_id`  (`user_id`),
  KEY `idx_conv_timestamp` (`timestamp`),
  CONSTRAINT `fk_conv_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='对话记录表';

CREATE TABLE `login_records` (
  `id`          bigint       NOT NULL AUTO_INCREMENT,
  `user_id`     bigint       NOT NULL,
  `username`    varchar(255) NOT NULL,
  `login_time`  datetime(6)  NOT NULL,
  `ip_address`  varchar(255)          DEFAULT NULL,
  `device_info` varchar(255)          DEFAULT NULL,
  `location`    varchar(255)          DEFAULT NULL,
  `status`      varchar(20)  NOT NULL,
  `fail_reason` text,
  `created_at`  datetime(6)           DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_lr_user_id`    (`user_id`),
  KEY `idx_lr_login_time` (`login_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='登录记录表';

CREATE TABLE `user_notifications` (
  `id`                 bigint       NOT NULL AUTO_INCREMENT,
  `recipient_username` varchar(255) NOT NULL,
  `operator_username`  varchar(255) NOT NULL,
  `action_type`        varchar(255) NOT NULL,
  `resource_id`        varchar(255)          DEFAULT NULL,
  `resource_name`      varchar(255)          DEFAULT NULL,
  `message`            text         NOT NULL,
  `is_read`            bit(1)       NOT NULL,
  `created_at`         datetime(6)           DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_notif_recipient`       (`recipient_username`),
  KEY `idx_notif_recipient_unread` (`recipient_username`, `is_read`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户通知表';

CREATE TABLE `user_favorites` (
  `id`          bigint       NOT NULL AUTO_INCREMENT,
  `user_id`     bigint       NOT NULL,
  `type`        varchar(32)  NOT NULL,
  `target_id`   varchar(128) NOT NULL,
  `title`       varchar(255) NOT NULL,
  `description` text,
  `meta`        varchar(128)          DEFAULT NULL,
  `starred`     bit(1)       NOT NULL,
  `created_at`  datetime(6)           DEFAULT NULL,
  `updated_at`  datetime(6)           DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_favorite_target` (`user_id`, `type`, `target_id`),
  KEY `idx_user_favorite_user` (`user_id`),
  KEY `idx_user_favorite_type` (`type`),
  CONSTRAINT `fk_favorite_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户收藏表';

-- =====================================================
-- Section 5: API Key 配置
-- =====================================================

CREATE TABLE `api_key_configs` (
  `id`          bigint       NOT NULL AUTO_INCREMENT,
  `name`        varchar(100) NOT NULL,
  `provider`    varchar(50)  NOT NULL,
  `api_url`     varchar(500) NOT NULL,
  `api_key`     varchar(500) NOT NULL,
  `model_name`  varchar(100) NOT NULL,
  `active`      bit(1)       NOT NULL,
  `temperature` double                DEFAULT NULL,
  `max_tokens`  int                   DEFAULT NULL,
  `topp`        double                DEFAULT NULL,
  `auth_type`   varchar(20)  NOT NULL,
  `remark`      varchar(500)          DEFAULT NULL,
  `created_at`  datetime(6)           DEFAULT NULL,
  `updated_at`  datetime(6)           DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='API Key 配置表';

-- =====================================================
-- Section 6: Agent 智能体体系
-- =====================================================

CREATE TABLE `agents` (
  `id`               bigint       NOT NULL AUTO_INCREMENT,
  `agent_id`         varchar(64)  NOT NULL,
  `name`             varchar(255) NOT NULL,
  `description`      text,
  `status`           varchar(255) NOT NULL,
  `owner_name`       varchar(255)          DEFAULT NULL,
  `tags`             varchar(255)          DEFAULT NULL,
  `system_prompt`    text,
  `user_prompt`      text,
  `avatar_emoji`     varchar(32)           DEFAULT NULL,
  `temperature`      double                DEFAULT NULL,
  `top_p`            double                DEFAULT NULL,
  `max_tokens`       int                   DEFAULT NULL,
  `max_iterations`   int                   DEFAULT NULL,
  `memory_types`     varchar(255)          DEFAULT NULL,
  `permission_scope` varchar(255)          DEFAULT NULL,
  `knowledge_bases`  text,
  `prompt_refs`      text,
  `mcp_tools`        text,
  `skill_refs`       text,
  `models`           text,
  `call_count`       bigint                DEFAULT NULL,
  `success_count`    bigint                DEFAULT NULL,
  `failure_count`    bigint                DEFAULT NULL,
  `avg_duration_ms`  bigint                DEFAULT NULL,
  `install_count`    bigint       NOT NULL,
  `published_at`     datetime(6)           DEFAULT NULL,
  `created_at`       datetime(6)           DEFAULT NULL,
  `updated_at`       datetime(6)           DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_id` (`agent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent 智能体表';

CREATE TABLE `agent_i18n` (
  `id`          bigint      NOT NULL AUTO_INCREMENT,
  `agent_id`    varchar(64) NOT NULL,
  `lang`        varchar(10) NOT NULL,
  `name`        varchar(255)         DEFAULT NULL,
  `description` text,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_lang` (`agent_id`, `lang`),
  KEY `idx_agenti18n_agent_id` (`agent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent 国际化表';

CREATE TABLE `agent_versions` (
  `id`                 bigint       NOT NULL AUTO_INCREMENT,
  `version_id`         varchar(64)  NOT NULL,
  `agent_id`           varchar(64)  NOT NULL,
  `version_number`     int                   DEFAULT NULL,
  `name`               varchar(255) NOT NULL,
  `description`        text,
  `status`             varchar(255)          DEFAULT NULL,
  `system_prompt`      text,
  `user_prompt`        text,
  `temperature`        double                DEFAULT NULL,
  `top_p`              double                DEFAULT NULL,
  `max_tokens`         int                   DEFAULT NULL,
  `max_iterations`     int                   DEFAULT NULL,
  `memory_types`       varchar(255)          DEFAULT NULL,
  `knowledge_bases`    text,
  `prompt_refs`        text,
  `mcp_tools`          text,
  `skill_refs`         text,
  `models`             text,
  `snapshot_by`        varchar(100)          DEFAULT NULL,
  `change_description` varchar(500)          DEFAULT NULL,
  `is_active`          bit(1)                DEFAULT NULL,
  `snapshot_at`        datetime(6)           DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_version_id` (`version_id`),
  KEY `idx_agent_versions_agent_id` (`agent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent 版本历史表';

CREATE TABLE `agent_execution_logs` (
  `id`                bigint      NOT NULL AUTO_INCREMENT,
  `execution_id`      varchar(64) NOT NULL,
  `agent_id`          varchar(64) NOT NULL,
  `version_id`        varchar(64)          DEFAULT NULL,
  `trigger_type`      varchar(30)          DEFAULT NULL,
  `status`            varchar(20) NOT NULL,
  `input_json`        longtext,
  `output_json`       longtext,
  `trace_json`        longtext,
  `iterations`        int                  DEFAULT NULL,
  `started_by`        varchar(100)         DEFAULT NULL,
  `started_at`        datetime(6)          DEFAULT NULL,
  `completed_at`      datetime(6)          DEFAULT NULL,
  `duration_ms`       bigint               DEFAULT NULL,
  `prompt_tokens`     int                  DEFAULT NULL,
  `completion_tokens` int                  DEFAULT NULL,
  `total_tokens`      int                  DEFAULT NULL,
  `cost`              decimal(10,6)        DEFAULT NULL,
  `model_cost`        decimal(10,6)        DEFAULT NULL,
  `tool_cost`         decimal(10,6)        DEFAULT NULL,
  `error_message`     text,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_exec_id` (`execution_id`),
  KEY `idx_ael_agent_id`   (`agent_id`),
  KEY `idx_ael_status`     (`status`),
  KEY `idx_ael_started_at` (`started_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent 执行日志表';

CREATE TABLE `agent_run_analysis_snapshots` (
  `id`                bigint        NOT NULL AUTO_INCREMENT,
  `snapshot_date`     date          NOT NULL,
  `run_count`         bigint        NOT NULL,
  `success_count`     bigint        NOT NULL,
  `failure_count`     bigint        NOT NULL,
  `duration_total_ms` bigint        NOT NULL,
  `token_usage`       bigint        NOT NULL,
  `model_cost`        decimal(16,6) NOT NULL,
  `tool_cost`         decimal(16,6) NOT NULL,
  `hot_agents_json`   longtext,
  `created_at`        datetime(6)            DEFAULT NULL,
  `updated_at`        datetime(6)            DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_snapshot_date` (`snapshot_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent 运行分析快照表';

-- =====================================================
-- Section 7: Workflow 工作流体系
-- =====================================================

CREATE TABLE `workflows` (
  `id`               bigint       NOT NULL AUTO_INCREMENT,
  `workflow_id`      varchar(64)  NOT NULL,
  `name`             varchar(255) NOT NULL,
  `description`      text,
  `status`           varchar(255) NOT NULL,
  `owner_name`       varchar(255)          DEFAULT NULL,
  `tags`             varchar(255)          DEFAULT NULL,
  `nodes_json`       longtext,
  `edges_json`       longtext,
  `permission_scope` varchar(255)          DEFAULT NULL,
  `knowledge_bases`  text,
  `prompt_refs`      text,
  `mcp_tools`        text,
  `skill_refs`       text,
  `models`           text,
  `call_count`       bigint                DEFAULT NULL,
  `success_count`    bigint                DEFAULT NULL,
  `failure_count`    bigint                DEFAULT NULL,
  `avg_duration_ms`  bigint                DEFAULT NULL,
  `install_count`    bigint       NOT NULL,
  `published_at`     datetime(6)           DEFAULT NULL,
  `created_at`       datetime(6)           DEFAULT NULL,
  `updated_at`       datetime(6)           DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_workflow_id` (`workflow_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Workflow 工作流表';

CREATE TABLE `workflow_versions` (
  `id`                 bigint       NOT NULL AUTO_INCREMENT,
  `version_id`         varchar(64)  NOT NULL,
  `workflow_id`        varchar(64)  NOT NULL,
  `version_number`     int                   DEFAULT NULL,
  `name`               varchar(255) NOT NULL,
  `description`        text,
  `status`             varchar(255) NOT NULL,
  `knowledge_bases`    text,
  `prompt_refs`        text,
  `mcp_tools`          text,
  `skill_refs`         text,
  `models`             text,
  `nodes_json`         longtext,
  `edges_json`         longtext,
  `snapshot_by`        varchar(100)          DEFAULT NULL,
  `change_description` varchar(500)          DEFAULT NULL,
  `is_active`          bit(1)                DEFAULT NULL,
  `snapshot_at`        datetime(6)           DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_workflow_version_id` (`version_id`),
  KEY `idx_wv_workflow_id` (`workflow_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作流版本历史表';

CREATE TABLE `workflow_execution_logs` (
  `id`                bigint      NOT NULL AUTO_INCREMENT,
  `execution_id`      varchar(64) NOT NULL,
  `workflow_id`       varchar(64) NOT NULL,
  `version_id`        varchar(64)          DEFAULT NULL,
  `trigger_type`      varchar(30)          DEFAULT NULL,
  `status`            varchar(20) NOT NULL,
  `input_json`        longtext,
  `output_json`       longtext,
  `trace_json`        longtext,
  `variables_json`    longtext,
  `started_by`        varchar(100)         DEFAULT NULL,
  `started_at`        datetime(6)          DEFAULT NULL,
  `completed_at`      datetime(6)          DEFAULT NULL,
  `duration_ms`       bigint               DEFAULT NULL,
  `prompt_tokens`     int                  DEFAULT NULL,
  `completion_tokens` int                  DEFAULT NULL,
  `total_tokens`      int                  DEFAULT NULL,
  `cost`              decimal(10,6)        DEFAULT NULL,
  `error_message`     text,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_workflow_exec_id` (`execution_id`),
  KEY `idx_wel_workflow_id` (`workflow_id`),
  KEY `idx_wel_status`      (`status`),
  KEY `idx_wel_started_at`  (`started_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作流执行日志表';

-- =====================================================
-- Section 8: MCP 工具 / Prompt 模板 / Skills 技能
-- =====================================================

CREATE TABLE `mcp_tool_configs` (
  `id`                 bigint       NOT NULL AUTO_INCREMENT,
  `tool_id`            varchar(64)  NOT NULL,
  `name`               varchar(255) NOT NULL,
  `type`               varchar(255) NOT NULL,
  `status`             varchar(255) NOT NULL,
  `tool_name`          varchar(128)          DEFAULT NULL,
  `request_mode`       varchar(32)           DEFAULT NULL,
  `protocol_version`   varchar(32)           DEFAULT NULL,
  `endpoint`           varchar(255)          DEFAULT NULL,
  `auth_type`          varchar(255)          DEFAULT NULL,
  `auth_header_name`   varchar(100)          DEFAULT NULL,
  `api_key`            varchar(500)          DEFAULT NULL,
  `description`        text,
  `input_schema`       text,
  `last_test_status`   varchar(20)           DEFAULT NULL,
  `last_test_message`  varchar(1000)         DEFAULT NULL,
  `last_test_at`       datetime(6)           DEFAULT NULL,
  `call_count`         bigint                DEFAULT NULL,
  `created_at`         datetime(6)           DEFAULT NULL,
  `updated_at`         datetime(6)           DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tool_id` (`tool_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MCP 工具配置表';

CREATE TABLE `prompt_templates` (
  `id`             bigint       NOT NULL AUTO_INCREMENT,
  `template_id`    varchar(64)  NOT NULL,
  `name`           varchar(255) NOT NULL,
  `description`    varchar(255)          DEFAULT NULL,
  `category`       varchar(255) NOT NULL,
  `version`        varchar(255) NOT NULL,
  `system_content` longtext,
  `content`        longtext,
  `variables`      text,
  `tags`           text,
  `status`         varchar(255) NOT NULL,
  `is_public`      bit(1)       NOT NULL,
  `created_at`     datetime(6)           DEFAULT NULL,
  `updated_at`     datetime(6)           DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_template_id` (`template_id`),
  KEY `idx_pt_category` (`category`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Prompt 模板表';

CREATE TABLE `prompt_template_histories` (
  `id`                 bigint       NOT NULL AUTO_INCREMENT,
  `template_id`        varchar(64)  NOT NULL,
  `name`               varchar(255) NOT NULL,
  `description`        varchar(255)          DEFAULT NULL,
  `category`           varchar(255) NOT NULL,
  `version`            varchar(255) NOT NULL,
  `system_content`     longtext,
  `content`            longtext,
  `variables`          varchar(500)          DEFAULT NULL,
  `tags`               text,
  `status`             varchar(20)           DEFAULT NULL,
  `snapshot_by`        varchar(100)          DEFAULT NULL,
  `change_description` varchar(500)          DEFAULT NULL,
  `snapshot_at`        datetime(6)  NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_pth_template_id` (`template_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Prompt 模板版本历史表';

CREATE TABLE `skill_definitions` (
  `id`              bigint      NOT NULL AUTO_INCREMENT,
  `skill_id`        varchar(64) NOT NULL,
  `name`            varchar(100) NOT NULL,
  `category`        varchar(50)          DEFAULT NULL,
  `status`          varchar(20) NOT NULL,
  `owner_name`      varchar(100)         DEFAULT NULL,
  `description`     varchar(500)         DEFAULT NULL,
  `tags`            text,
  `instruction`     longtext,
  `system_prompt`   longtext,
  `input_schema`    longtext,
  `output_schema`   longtext,
  `runtime_config`  longtext,
  `example_input`   longtext,
  `example_output`  longtext,
  `prompt_refs`     text,
  `mcp_tool_refs`   text,
  `version`         varchar(20) NOT NULL,
  `call_count`      bigint               DEFAULT NULL,
  `success_count`   bigint               DEFAULT NULL,
  `avg_duration_ms` bigint               DEFAULT NULL,
  `published_at`    datetime(6)          DEFAULT NULL,
  `created_at`      datetime(6)          DEFAULT NULL,
  `updated_at`      datetime(6)          DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_skill_id` (`skill_id`),
  KEY `idx_sd_category` (`category`),
  KEY `idx_sd_status`   (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Skills 技能定义表';

CREATE TABLE `skill_version_histories` (
  `id`                 bigint      NOT NULL AUTO_INCREMENT,
  `skill_id`           varchar(64) NOT NULL,
  `name`               varchar(100) NOT NULL,
  `category`           varchar(50)          DEFAULT NULL,
  `version`            varchar(20) NOT NULL,
  `status`             varchar(20)          DEFAULT NULL,
  `owner_name`         varchar(100)         DEFAULT NULL,
  `description`        varchar(500)         DEFAULT NULL,
  `tags`               text,
  `instruction`        longtext,
  `system_prompt`      longtext,
  `input_schema`       longtext,
  `output_schema`      longtext,
  `runtime_config`     longtext,
  `example_input`      longtext,
  `example_output`     longtext,
  `prompt_refs`        text,
  `mcp_tool_refs`      text,
  `snapshot_by`        varchar(100)         DEFAULT NULL,
  `change_description` varchar(500)         DEFAULT NULL,
  `snapshot_at`        datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_svh_skill_id` (`skill_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Skills 版本历史表';


-- =============================================================
-- [存量库修复脚本]
-- 仅对已有生产库执行，新库已包含以上正确定义，无需执行此段
-- =============================================================

-- 1. 删除测试残留表
DROP TABLE IF EXISTS `test_entity`;

-- 2. 补全 file_upload 缺失的唯一约束和索引
ALTER TABLE `file_upload`
  ADD UNIQUE KEY `uk_md5_user`    (`file_md5`, `user_id`),
  ADD KEY `idx_file_user_id` (`user_id`),
  ADD KEY `idx_file_org_tag`  (`org_tag`),
  ADD KEY `idx_file_kb_id`    (`kb_id`);

-- 3. 补全 user_notifications 缺失的索引
ALTER TABLE `user_notifications`
  ADD KEY `idx_notif_recipient`        (`recipient_username`),
  ADD KEY `idx_notif_recipient_unread` (`recipient_username`, `is_read`);

-- 4. 补全 chunk_info 缺失的索引
ALTER TABLE `chunk_info`
  ADD KEY `idx_chunk_file_md5` (`file_md5`);

-- 5. 补全 login_records 缺失的索引
ALTER TABLE `login_records`
  ADD KEY `idx_lr_user_id`    (`user_id`),
  ADD KEY `idx_lr_login_time` (`login_time`);

-- 6. 补全 document_vectors 缺失的索引
ALTER TABLE `document_vectors`
  ADD KEY `idx_dv_file_md5` (`file_md5`),
  ADD KEY `idx_dv_user_id`  (`user_id`);

-- 7. 补全 knowledge_bases 缺失的索引（存量库 JPA 自建，索引名可能不同，如报 Duplicate key 可忽略）
ALTER TABLE `knowledge_bases`
  ADD KEY `idx_kb_org_tag` (`org_tag`);

-- 8. 新增三张 i18n 表（若不存在则创建）
CREATE TABLE IF NOT EXISTS `organization_tag_i18n` (
  `id`          bigint       NOT NULL AUTO_INCREMENT,
  `tag_id`      varchar(100) NOT NULL,
  `lang`        varchar(10)  NOT NULL,
  `name`        varchar(200)          DEFAULT NULL,
  `description` text,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tag_lang` (`tag_id`, `lang`),
  KEY `idx_tagi18n_tag_id` (`tag_id`),
  CONSTRAINT `fk_tagi18n_tag` FOREIGN KEY (`tag_id`) REFERENCES `organization_tags` (`tag_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='组织标签国际化表';

CREATE TABLE IF NOT EXISTS `knowledge_base_i18n` (
  `id`          bigint       NOT NULL AUTO_INCREMENT,
  `kb_id`       varchar(255) NOT NULL,
  `lang`        varchar(10)  NOT NULL,
  `name`        varchar(255)          DEFAULT NULL,
  `description` text,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_kb_lang` (`kb_id`, `lang`),
  KEY `idx_kbi18n_kb_id` (`kb_id`),
  CONSTRAINT `fk_kbi18n_kb` FOREIGN KEY (`kb_id`) REFERENCES `knowledge_bases` (`kb_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库国际化表';

CREATE TABLE IF NOT EXISTS `agent_i18n` (
  `id`          bigint      NOT NULL AUTO_INCREMENT,
  `agent_id`    varchar(64) NOT NULL,
  `lang`        varchar(10) NOT NULL,
  `name`        varchar(255)         DEFAULT NULL,
  `description` text,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_lang` (`agent_id`, `lang`),
  KEY `idx_agenti18n_agent_id` (`agent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent 国际化表';

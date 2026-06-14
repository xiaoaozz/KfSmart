CREATE TABLE users (
                       id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '用户唯一标识',
                       username VARCHAR(255) NOT NULL UNIQUE COMMENT '用户名，唯一',
                       password VARCHAR(255) NOT NULL COMMENT '加密后的密码',
                       role ENUM('USER', 'ADMIN') NOT NULL DEFAULT 'USER' COMMENT '用户角色',
                       org_tags VARCHAR(255) DEFAULT NULL COMMENT '用户所属组织标签，多个用逗号分隔',
                       primary_org VARCHAR(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '用户主组织标签',
                       avatar_url VARCHAR(512) DEFAULT NULL COMMENT '用户头像访问地址',
                       created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                       updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                       INDEX idx_username (username) COMMENT '用户名索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';
CREATE TABLE organization_tags (
                                   tag_id VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin PRIMARY KEY COMMENT '标签唯一标识',
                                   name VARCHAR(100) NOT NULL COMMENT '标签名称',
                                   description TEXT COMMENT '描述',
                                   parent_tag VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '父标签ID',
                                   created_by BIGINT NOT NULL COMMENT '创建者ID',
                                   created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                   updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                   FOREIGN KEY (parent_tag) REFERENCES organization_tags(tag_id) ON DELETE SET NULL,
                                   FOREIGN KEY (created_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='组织标签表';


CREATE TABLE file_upload (
                             id           BIGINT           NOT NULL AUTO_INCREMENT COMMENT '主键',
                             file_md5     VARCHAR(32)      NOT NULL COMMENT '文件 MD5',
                             file_name    VARCHAR(255)     NOT NULL COMMENT '文件名称',
                             total_size   BIGINT           NOT NULL COMMENT '文件大小',
                             status       TINYINT          NOT NULL DEFAULT 0 COMMENT '上传状态',
                             user_id      VARCHAR(64)      NOT NULL COMMENT '用户 ID',
                             org_tag      VARCHAR(50)      DEFAULT NULL COMMENT '组织标签',
                             kb_id        VARCHAR(50)      DEFAULT NULL COMMENT '知识库ID',
                             is_public    BOOLEAN          NOT NULL DEFAULT FALSE COMMENT '是否公开',                             created_at   TIMESTAMP        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                             merged_at    TIMESTAMP        NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '合并时间',
                             PRIMARY KEY (id),
                             UNIQUE KEY uk_md5_user (file_md5, user_id),
                             INDEX idx_user (user_id),
                             INDEX idx_org_tag (org_tag),
                             INDEX idx_kb_id (kb_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件上传记录';
CREATE TABLE chunk_info (
                            id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '分块记录唯一标识',
                            file_md5 VARCHAR(32) NOT NULL COMMENT '关联的文件MD5值',
                            chunk_index INT NOT NULL COMMENT '分块序号',
                            chunk_md5 VARCHAR(32) NOT NULL COMMENT '分块的MD5值',
                            storage_path VARCHAR(255) NOT NULL COMMENT '分块在存储系统中的路径'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件分块信息表';

CREATE TABLE document_vectors (
                                  vector_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '向量记录唯一标识',
                                  file_md5 VARCHAR(32) NOT NULL COMMENT '关联的文件MD5值',
                                  chunk_id INT NOT NULL COMMENT '文本分块序号',
                                  text_content TEXT COMMENT '文本内容',
                                  model_version VARCHAR(32) COMMENT '向量模型版本',
                                  user_id VARCHAR(64) NOT NULL COMMENT '上传用户ID',
                                  org_tag VARCHAR(50) COMMENT '文件所属组织标签',
                                  is_public BOOLEAN NOT NULL DEFAULT FALSE COMMENT '文件是否公开'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档向量存储表';

CREATE TABLE knowledge_bases (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '自增主键',
    kb_id VARCHAR(50) NOT NULL COMMENT '知识库唯一标识',
    name VARCHAR(100) NOT NULL COMMENT '知识库名称',
    description TEXT COMMENT '知识库描述',
    org_tag VARCHAR(50) DEFAULT NULL COMMENT '关联的组织标签（可选，用于权限控制）',
    is_public BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否公开',
    icon VARCHAR(50) DEFAULT 'folder' COMMENT '知识库图标标识',
    created_by BIGINT NOT NULL COMMENT '创建者ID',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_kb_id (kb_id),
    INDEX idx_org_tag (org_tag),
    INDEX idx_created_by (created_by),
    CONSTRAINT fk_kb_created_by FOREIGN KEY (created_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库表';

CREATE TABLE user_notifications (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '通知唯一标识',
    recipient_username VARCHAR(255) NOT NULL COMMENT '通知接收者用户名',
    operator_username VARCHAR(255) NOT NULL COMMENT '操作者（admin）用户名',
    action_type VARCHAR(50) NOT NULL COMMENT '操作类型：UPDATE_KB / DELETE_KB / UPDATE_DOCUMENT / DELETE_DOCUMENT',
    resource_id VARCHAR(255) DEFAULT NULL COMMENT '被操作的资源标识（kbId 或 fileMd5）',
    resource_name VARCHAR(255) DEFAULT NULL COMMENT '被操作的资源名称（知识库名称 或 文件名）',
    message TEXT NOT NULL COMMENT '通知内容',
    is_read BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否已读',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_recipient (recipient_username) COMMENT '接收者用户名索引',
    INDEX idx_recipient_unread (recipient_username, is_read) COMMENT '未读通知查询索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户通知表';

-- ===== Agent 能力中心相关表 =====

CREATE TABLE agent_workflows (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '自增主键',
    workflow_id     VARCHAR(64) NOT NULL COMMENT 'Agent 唯一标识',
    name            VARCHAR(100) NOT NULL COMMENT 'Agent 名称',
    description     TEXT COMMENT '描述',
    type            VARCHAR(50) NOT NULL DEFAULT '工作流' COMMENT '类型',
    status          VARCHAR(20) NOT NULL DEFAULT '草稿' COMMENT '状态：草稿/运行中/已停止',
    owner_name      VARCHAR(100) COMMENT '负责人',
    tags            VARCHAR(255) COMMENT '标签，逗号分隔',
    call_count      BIGINT NOT NULL DEFAULT 0 COMMENT '调用次数',
    success_count   BIGINT NOT NULL DEFAULT 0 COMMENT '成功次数',
    failure_count   BIGINT NOT NULL DEFAULT 0 COMMENT '失败次数',
    avg_duration_ms BIGINT NOT NULL DEFAULT 0 COMMENT '平均响应时间(ms)',
    install_count   BIGINT NOT NULL DEFAULT 0 COMMENT '安装次数',
    permission_scope VARCHAR(20) NOT NULL DEFAULT '组织内' COMMENT '权限范围：公开/组织内/指定部门',
    knowledge_bases TEXT COMMENT '关联知识库，逗号分隔名称',
    prompt_refs     TEXT COMMENT '关联 Prompt 模板，逗号分隔名称',
    mcp_tools       TEXT COMMENT '关联 MCP 工具，逗号分隔名称',
    models          TEXT COMMENT '关联模型，逗号分隔',
    nodes_json      LONGTEXT COMMENT '工作流节点 JSON',
    edges_json      LONGTEXT COMMENT '工作流边 JSON',
    system_prompt   TEXT COMMENT 'System Prompt 内容',
    avatar_emoji    VARCHAR(32) DEFAULT '🤖' COMMENT '头像 Emoji',
    temperature     DOUBLE DEFAULT 0.7 COMMENT '模型温度参数',
    top_p           DOUBLE DEFAULT 0.8 COMMENT '模型 Top-P 参数',
    max_tokens      INT DEFAULT 4000 COMMENT '最大输出 Token 数',
    memory_types    VARCHAR(255) COMMENT '记忆类型，逗号分隔',
    published_at    TIMESTAMP NULL COMMENT '发布时间',
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_workflow_id (workflow_id),
    INDEX idx_status (status),
    INDEX idx_type (type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent 工作流表';

CREATE TABLE prompt_templates (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '自增主键',
    template_id VARCHAR(64) NOT NULL COMMENT 'Prompt 模板唯一标识',
    name        VARCHAR(100) NOT NULL COMMENT '模板名称',
    category    VARCHAR(50) COMMENT '分类',
    version     VARCHAR(20) DEFAULT 'v1.0' COMMENT '版本',
    content     LONGTEXT COMMENT '模板内容（支持 {{变量}} 占位符）',
    variables   VARCHAR(500) COMMENT '变量列表，逗号分隔',
    status      VARCHAR(20) DEFAULT '启用' COMMENT '状态：启用/禁用',
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_template_id (template_id),
    INDEX idx_category (category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Prompt 模板表';

CREATE TABLE mcp_tool_configs (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '自增主键',
    tool_id     VARCHAR(64) NOT NULL COMMENT 'MCP 工具唯一标识',
    name        VARCHAR(100) NOT NULL COMMENT '工具名称',
    type        VARCHAR(50) DEFAULT 'MCP' COMMENT '类型：MCP/HTTP',
    status      VARCHAR(20) DEFAULT '在线' COMMENT '状态：在线/离线',
    endpoint    VARCHAR(500) COMMENT '工具 Endpoint URL',
    auth_type   VARCHAR(50) COMMENT '鉴权类型：API Key/Bearer Token/无鉴权',
    api_key     VARCHAR(500) COMMENT 'API Key（加密存储）',
    description TEXT COMMENT '工具描述',
    call_count  BIGINT NOT NULL DEFAULT 0 COMMENT '调用次数',
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_tool_id (tool_id),
    INDEX idx_type (type),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MCP 工具配置表';

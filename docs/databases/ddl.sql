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

-- ===== Agent & Workflow 拆分后的独立表 =====

CREATE TABLE agents (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '自增主键',
    agent_id        VARCHAR(64) NOT NULL COMMENT 'Agent 唯一标识',
    name            VARCHAR(100) NOT NULL COMMENT 'Agent 名称',
    description     TEXT COMMENT '描述',
    status          VARCHAR(20) NOT NULL DEFAULT '草稿' COMMENT '状态：草稿/运行中/已停止',
    owner_name      VARCHAR(100) COMMENT '负责人',
    tags            VARCHAR(255) COMMENT '标签，逗号分隔',
    system_prompt   TEXT COMMENT 'System Prompt 内容',
    user_prompt     TEXT COMMENT 'User Prompt 内容（支持 {{query}} 或 {{input}} 占位符）',
    avatar_emoji    VARCHAR(32) DEFAULT '🤖' COMMENT '头像 Emoji',
    temperature     DOUBLE DEFAULT 0.7 COMMENT '模型温度参数',
    top_p           DOUBLE DEFAULT 0.8 COMMENT '模型 Top-P 参数',
    max_tokens      INT DEFAULT 4000 COMMENT '最大输出 Token 数',
    max_iterations  INT DEFAULT 10 COMMENT 'ReAct 最大循环次数',
    memory_types    VARCHAR(255) COMMENT '记忆类型，逗号分隔',
    permission_scope VARCHAR(20) NOT NULL DEFAULT '组织内' COMMENT '权限范围：公开/组织内/指定部门',
    knowledge_bases TEXT COMMENT '关联知识库，逗号分隔名称',
    prompt_refs     TEXT COMMENT '关联 Prompt 模板，逗号分隔名称',
    mcp_tools       TEXT COMMENT '关联 MCP 工具，逗号分隔名称',
    models          TEXT COMMENT '关联模型，逗号分隔',
    call_count      BIGINT NOT NULL DEFAULT 0 COMMENT '调用次数',
    success_count   BIGINT NOT NULL DEFAULT 0 COMMENT '成功次数',
    failure_count   BIGINT NOT NULL DEFAULT 0 COMMENT '失败次数',
    avg_duration_ms BIGINT NOT NULL DEFAULT 0 COMMENT '平均响应时间(ms)',
    install_count   BIGINT NOT NULL DEFAULT 0 COMMENT '安装次数',
    published_at    TIMESTAMP NULL COMMENT '发布时间',
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_agent_id (agent_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent 智能体表';

CREATE TABLE workflows (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '自增主键',
    workflow_id     VARCHAR(64) NOT NULL COMMENT 'Workflow 唯一标识',
    name            VARCHAR(100) NOT NULL COMMENT '工作流名称',
    description     TEXT COMMENT '描述',
    status          VARCHAR(20) NOT NULL DEFAULT '草稿' COMMENT '状态：草稿/运行中/已停止',
    owner_name      VARCHAR(100) COMMENT '负责人',
    tags            VARCHAR(255) COMMENT '标签，逗号分隔',
    nodes_json      LONGTEXT COMMENT '工作流节点 JSON',
    edges_json      LONGTEXT COMMENT '工作流边 JSON',
    permission_scope VARCHAR(20) NOT NULL DEFAULT '组织内' COMMENT '权限范围：公开/组织内/指定部门',
    knowledge_bases TEXT COMMENT '关联知识库，逗号分隔名称',
    prompt_refs     TEXT COMMENT '关联 Prompt 模板，逗号分隔名称',
    mcp_tools       TEXT COMMENT '关联 MCP 工具，逗号分隔名称',
    models          TEXT COMMENT '关联模型，逗号分隔',
    call_count      BIGINT NOT NULL DEFAULT 0 COMMENT '调用次数',
    success_count   BIGINT NOT NULL DEFAULT 0 COMMENT '成功次数',
    failure_count   BIGINT NOT NULL DEFAULT 0 COMMENT '失败次数',
    avg_duration_ms BIGINT NOT NULL DEFAULT 0 COMMENT '平均响应时间(ms)',
    install_count   BIGINT NOT NULL DEFAULT 0 COMMENT '安装次数',
    published_at    TIMESTAMP NULL COMMENT '发布时间',
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_workflow_id (workflow_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Workflow 工作流表';

CREATE TABLE prompt_templates (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '自增主键',
    template_id VARCHAR(64) NOT NULL COMMENT 'Prompt 模板唯一标识',
    name        VARCHAR(100) NOT NULL COMMENT '模板名称',
    description VARCHAR(500) COMMENT '模板描述',
    category    VARCHAR(50) COMMENT '分类',
    version     VARCHAR(20) DEFAULT 'v1.0' COMMENT '版本',
    content     LONGTEXT COMMENT 'User Prompt 内容（支持 {{变量}} 占位符）',
    system_content LONGTEXT COMMENT 'System Prompt 内容（角色设定、行为约束等）',
    variables   VARCHAR(500) COMMENT '变量列表，逗号分隔',
    tags        TEXT COMMENT '标签列表，逗号分隔',
    status      VARCHAR(20) DEFAULT '启用' COMMENT '状态：启用/禁用',
    is_public   BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否公开',
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_template_id (template_id),
    INDEX idx_category (category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Prompt 模板表';

CREATE TABLE prompt_template_histories (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '自增主键',
    template_id VARCHAR(64) NOT NULL COMMENT '关联的 Prompt 模板 ID',
    version     VARCHAR(20) NOT NULL COMMENT '快照时的版本号',
    name        VARCHAR(100) NOT NULL COMMENT '快照时的名称',
    description VARCHAR(500) COMMENT '快照时的描述',
    category    VARCHAR(50) COMMENT '快照时的分类',
    system_content LONGTEXT COMMENT 'System Prompt 内容',
    content     LONGTEXT COMMENT 'User Prompt 内容',
    variables   VARCHAR(500) COMMENT '变量列表，逗号分隔',
    tags        TEXT COMMENT '标签列表，逗号分隔',
    status      VARCHAR(20) COMMENT '快照时的状态',
    snapshot_by VARCHAR(100) COMMENT '快照操作人',
    change_description VARCHAR(500) COMMENT '变更说明',
    snapshot_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '快照时间',
    INDEX idx_template_id (template_id),
    CONSTRAINT fk_history_template_id FOREIGN KEY (template_id) REFERENCES prompt_templates(template_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Prompt 模板版本历史表';

CREATE TABLE mcp_tool_configs (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '自增主键',
    tool_id     VARCHAR(64) NOT NULL COMMENT 'MCP 工具唯一标识',
    name        VARCHAR(100) NOT NULL COMMENT '工具名称',
    type        VARCHAR(50) DEFAULT 'MCP' COMMENT '类型：MCP/HTTP',
    status      VARCHAR(20) DEFAULT '在线' COMMENT '状态：在线/离线',
    tool_name   VARCHAR(128) COMMENT 'MCP tools/call 使用的工具名',
    request_mode VARCHAR(32) DEFAULT 'MCP_JSON_RPC' COMMENT '请求模式：MCP_JSON_RPC/HTTP_COMPAT',
    protocol_version VARCHAR(32) DEFAULT '2024-11-05' COMMENT 'MCP 协议版本',
    endpoint    VARCHAR(500) COMMENT '工具 Endpoint URL',
    auth_type   VARCHAR(50) COMMENT '鉴权类型：API Key/Bearer Token/无鉴权',
    auth_header_name VARCHAR(100) DEFAULT 'X-API-Key' COMMENT 'API Key Header 名称',
    api_key     VARCHAR(500) COMMENT 'API Key（加密存储）',
    description TEXT COMMENT '工具描述',
    input_schema TEXT COMMENT '工具输入 JSON Schema',
    last_test_status VARCHAR(20) COMMENT '最近测试状态',
    last_test_message VARCHAR(1000) COMMENT '最近测试信息',
    last_test_at TIMESTAMP NULL COMMENT '最近测试时间',
    call_count  BIGINT NOT NULL DEFAULT 0 COMMENT '调用次数',
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_tool_id (tool_id),
    INDEX idx_type (type),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MCP 工具配置表';

CREATE TABLE agent_versions (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '自增主键',
    version_id      VARCHAR(64) NOT NULL COMMENT '版本唯一标识',
    agent_id        VARCHAR(64) NOT NULL COMMENT '关联Agent ID',
    version_number  INT COMMENT '版本号',
    name            VARCHAR(100) NOT NULL COMMENT 'Agent 名称快照',
    description     TEXT COMMENT '描述快照',
    status          VARCHAR(20) DEFAULT '草稿' COMMENT '状态快照',
    system_prompt   TEXT COMMENT 'System Prompt 快照',
    user_prompt     TEXT COMMENT 'User Prompt 快照',
    temperature     DOUBLE COMMENT '温度参数快照',
    top_p           DOUBLE COMMENT 'Top-P 快照',
    max_tokens      INT COMMENT '最大 Token 快照',
    max_iterations  INT COMMENT '最大迭代次数快照',
    memory_types    VARCHAR(255) COMMENT '记忆类型快照',
    knowledge_bases TEXT COMMENT '知识库快照',
    prompt_refs     TEXT COMMENT 'Prompt 模板快照',
    mcp_tools       TEXT COMMENT 'MCP 工具快照',
    models          TEXT COMMENT '模型快照',
    snapshot_by     VARCHAR(100) COMMENT '快照操作人',
    change_description VARCHAR(500) COMMENT '变更说明',
    is_active       BOOLEAN DEFAULT FALSE COMMENT '是否为当前激活版本',
    snapshot_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '快照时间',
    UNIQUE KEY uk_version_id (version_id),
    INDEX idx_agent_id (agent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent 版本历史表';

CREATE TABLE agent_execution_logs (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '自增主键',
    execution_id    VARCHAR(64) NOT NULL COMMENT '执行唯一标识',
    agent_id        VARCHAR(64) NOT NULL COMMENT '关联Agent ID',
    version_id      VARCHAR(64) COMMENT '使用的版本ID',
    trigger_type    VARCHAR(30) COMMENT '触发类型: debug/conversation/api',
    status          VARCHAR(20) NOT NULL DEFAULT 'running' COMMENT '状态: running/success/failed/cancelled',
    input_json      LONGTEXT COMMENT '用户输入快照',
    output_json     LONGTEXT COMMENT 'Agent 输出快照',
    trace_json      LONGTEXT COMMENT 'ReAct 步骤 trace (thought/action/observation)',
    iterations      INT DEFAULT 0 COMMENT '实际迭代次数',
    started_by      VARCHAR(100) COMMENT '执行人',
    started_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '开始时间',
    completed_at    TIMESTAMP NULL COMMENT '完成时间',
    duration_ms     BIGINT DEFAULT 0 COMMENT '执行耗时(ms)',
    prompt_tokens   INT DEFAULT 0,
    completion_tokens INT DEFAULT 0,
    total_tokens    INT DEFAULT 0,
    cost            DECIMAL(10,6) DEFAULT 0 COMMENT '执行费用',
    error_message   TEXT COMMENT '错误信息',
    UNIQUE KEY uk_execution_id (execution_id),
    INDEX idx_agent_id (agent_id),
    INDEX idx_status (status),
    INDEX idx_started_at (started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent 执行日志表';

CREATE TABLE agent_run_analysis_snapshots (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '自增主键',
    snapshot_date       DATE NOT NULL COMMENT '统计日期',
    run_count           BIGINT NOT NULL DEFAULT 0 COMMENT '当日调用次数',
    success_count       BIGINT NOT NULL DEFAULT 0 COMMENT '当日成功次数',
    failure_count       BIGINT NOT NULL DEFAULT 0 COMMENT '当日失败次数',
    duration_total_ms   BIGINT NOT NULL DEFAULT 0 COMMENT '当日总耗时(ms)',
    token_usage         BIGINT NOT NULL DEFAULT 0 COMMENT '当日 Token 消耗',
    model_cost          DECIMAL(16,6) NOT NULL DEFAULT 0 COMMENT '当日模型费用',
    tool_cost           DECIMAL(16,6) NOT NULL DEFAULT 0 COMMENT '当日工具费用',
    hot_agents_json     LONGTEXT COMMENT '当日热门 Agent 快照',
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_snapshot_date (snapshot_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent 运行分析快照表';

CREATE TABLE workflow_versions (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '自增主键',
    version_id      VARCHAR(64) NOT NULL COMMENT '版本唯一标识',
    workflow_id     VARCHAR(64) NOT NULL COMMENT '关联工作流ID',
    version_number  INT COMMENT '版本号',
    name            VARCHAR(100) NOT NULL COMMENT '工作流名称快照',
    description     TEXT COMMENT '描述快照',
    status          VARCHAR(20) DEFAULT '草稿',
    knowledge_bases TEXT,
    prompt_refs     TEXT,
    mcp_tools       TEXT,
    models          TEXT,
    nodes_json      LONGTEXT COMMENT '节点 JSON 快照',
    edges_json      LONGTEXT COMMENT '边 JSON 快照',
    snapshot_by     VARCHAR(100) COMMENT '快照操作人',
    change_description VARCHAR(500) COMMENT '变更说明',
    is_active       BOOLEAN DEFAULT FALSE COMMENT '是否为当前激活版本',
    snapshot_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '快照时间',
    UNIQUE KEY uk_version_id (version_id),
    INDEX idx_workflow_id (workflow_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作流版本历史表';

-- ===== 兼容旧表结构：移除已废弃的 type 列（如果存在）=====
-- 旧表 workflow_versions 可能残留 type 列（NOT NULL 无默认值），导致 INSERT 失败。
-- 该列在重构拆分 Agent / Workflow 后已不再使用。
ALTER TABLE workflow_versions DROP COLUMN IF EXISTS type;

CREATE TABLE workflow_execution_logs (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '自增主键',
    execution_id    VARCHAR(64) NOT NULL COMMENT '执行唯一标识',
    workflow_id     VARCHAR(64) NOT NULL COMMENT '关联工作流ID',
    version_id      VARCHAR(64) COMMENT '使用的版本ID',
    trigger_type    VARCHAR(30) COMMENT '触发类型: sync_debug/async_run/api_trigger',
    status          VARCHAR(20) NOT NULL DEFAULT 'running' COMMENT '状态: running/success/failed/cancelled',
    input_json      LONGTEXT COMMENT '执行输入快照',
    output_json     LONGTEXT COMMENT '执行输出快照',
    trace_json      LONGTEXT COMMENT '节点级执行trace数组',
    variables_json  LONGTEXT COMMENT '最终变量状态',
    started_by      VARCHAR(100) COMMENT '执行人',
    started_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '开始时间',
    completed_at    TIMESTAMP NULL COMMENT '完成时间',
    duration_ms     BIGINT DEFAULT 0 COMMENT '执行耗时(ms)',
    prompt_tokens   INT DEFAULT 0,
    completion_tokens INT DEFAULT 0,
    total_tokens    INT DEFAULT 0,
    cost            DECIMAL(10,6) DEFAULT 0 COMMENT '执行费用',
    error_message   TEXT COMMENT '错误信息',
    UNIQUE KEY uk_execution_id (execution_id),
    INDEX idx_workflow_id (workflow_id),
    INDEX idx_status (status),
    INDEX idx_started_at (started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作流执行日志表';

-- ===== 增量升级脚本：Agent 支持独立 User Prompt =====
-- 已有库执行；新建库已包含在上方 CREATE TABLE 中。
ALTER TABLE agents ADD COLUMN IF NOT EXISTS user_prompt TEXT COMMENT 'User Prompt 内容（支持 {{query}} 或 {{input}} 占位符）' AFTER system_prompt;
ALTER TABLE agent_versions ADD COLUMN IF NOT EXISTS user_prompt TEXT COMMENT 'User Prompt 快照' AFTER system_prompt;

-- ===== 数据迁移脚本：从旧表 agent_workflows 迁移到新表 =====
-- 注意：执行前请先备份数据库！新表需已通过上述 DDL 创建完成。

-- 1. 迁移 Agent 数据（type 包含 '智能体' 或 'Agent' 的记录）
INSERT INTO agents (
    agent_id, name, description, status, owner_name, tags,
    system_prompt, avatar_emoji, temperature, top_p, max_tokens, memory_types,
    permission_scope, knowledge_bases, prompt_refs, mcp_tools, models,
    call_count, success_count, failure_count, avg_duration_ms, install_count,
    published_at, created_at, updated_at
)
SELECT
    workflow_id, name, description, status, owner_name, tags,
    system_prompt, avatar_emoji, temperature, top_p, max_tokens, memory_types,
    permission_scope, knowledge_bases, prompt_refs, mcp_tools, models,
    call_count, success_count, failure_count, avg_duration_ms, install_count,
    published_at, created_at, updated_at
FROM agent_workflows
WHERE type LIKE '%智能体%' OR type LIKE '%Agent%' OR type = '知识问答';

-- 2. 迁移 Workflow 数据（不属于 Agent 的记录）
INSERT INTO workflows (
    workflow_id, name, description, status, owner_name, tags,
    nodes_json, edges_json,
    permission_scope, knowledge_bases, prompt_refs, mcp_tools, models,
    call_count, success_count, failure_count, avg_duration_ms, install_count,
    published_at, created_at, updated_at
)
SELECT
    workflow_id, name, description, status, owner_name, tags,
    nodes_json, edges_json,
    permission_scope, knowledge_bases, prompt_refs, mcp_tools, models,
    call_count, success_count, failure_count, avg_duration_ms, install_count,
    published_at, created_at, updated_at
FROM agent_workflows
WHERE type NOT LIKE '%智能体%' AND type NOT LIKE '%Agent%' AND type != '知识问答';

-- 3. 迁移 workflow_versions（移除 Agent 专有字段 type/system_prompt/temperature/top_p/max_tokens）
INSERT INTO workflow_versions (
    version_id, workflow_id, version_number, name, description, status,
    knowledge_bases, prompt_refs, mcp_tools, models,
    nodes_json, edges_json,
    snapshot_by, change_description, is_active, snapshot_at
)
SELECT
    version_id, workflow_id, version_number, name, description, status,
    knowledge_bases, prompt_refs, mcp_tools, models,
    nodes_json, edges_json,
    snapshot_by, change_description, is_active, snapshot_at
FROM workflow_versions;

-- 4. 迁移执行日志（保留 Workflow 相关）
-- workflow_execution_logs 表结构不变，数据保持原样。

-- 5. 验证数据完整性后，删除旧表（请确认无误后执行）
-- DROP TABLE agent_workflows;

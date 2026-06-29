# KfSmart 数据库设计规范

## 概览

- **数据库**：MySQL 8.0+，库名 `knowflow`
- **字符集**：`utf8mb4 COLLATE utf8mb4_unicode_ci`（全库统一）
- **存储引擎**：InnoDB
- **DDL 来源**：`docs/databases/schema.sql`（唯一权威来源）
- **Schema 管理**：Hibernate `ddl-auto=update`，无 Flyway/Liquibase
- **JPA Entity 路径**：`src/main/java/com/smart/kf/model/`

---

## 表清单（31 张）

| # | 表名 | 说明 | Section |
|---|---|---|---|
| 1 | `users` | 用户账户 | 用户与权限 |
| 2 | `roles` | RBAC 角色 | 用户与权限 |
| 3 | `permissions` | 权限定义 | 用户与权限 |
| 4 | `user_roles` | 用户-角色关联 | 用户与权限 |
| 5 | `role_permissions` | 角色-权限关联 | 用户与权限 |
| 6 | `resource_permissions` | 资源级授权 | 用户与权限 |
| 7 | `organization_tags` | 组织标签（多租户） | 组织与知识库 |
| 8 | `organization_tag_i18n` | 组织标签国际化 | 组织与知识库 |
| 9 | `knowledge_bases` | 知识库 | 组织与知识库 |
| 10 | `knowledge_base_i18n` | 知识库国际化 | 组织与知识库 |
| 11 | `file_upload` | 文件上传记录 | 文件与向量 |
| 12 | `chunk_info` | 文件分块信息 | 文件与向量 |
| 13 | `document_vectors` | 文档向量（MySQL 侧元数据） | 文件与向量 |
| 14 | `conversations` | 对话问答记录 | 对话与通知 |
| 15 | `login_records` | 登录审计日志 | 对话与通知 |
| 16 | `user_notifications` | 用户站内通知 | 对话与通知 |
| 17 | `user_favorites` | 用户收藏 | 对话与通知 |
| 18 | `api_key_configs` | LLM API Key 配置 | API 配置 |
| 19 | `agents` | Agent 智能体 | Agent |
| 20 | `agent_i18n` | Agent 国际化 | Agent |
| 21 | `agent_versions` | Agent 版本快照 | Agent |
| 22 | `agent_execution_logs` | Agent 执行日志 | Agent |
| 23 | `agent_run_analysis_snapshots` | Agent 运行统计快照 | Agent |
| 24 | `workflows` | Workflow 工作流 | Workflow |
| 25 | `workflow_versions` | 工作流版本快照 | Workflow |
| 26 | `workflow_execution_logs` | 工作流执行日志 | Workflow |
| 27 | `mcp_tool_configs` | MCP 工具配置 | 工具链 |
| 28 | `prompt_templates` | Prompt 模板 | 工具链 |
| 29 | `prompt_template_histories` | Prompt 模板版本历史 | 工具链 |
| 30 | `skill_definitions` | Skills 技能定义 | 工具链 |
| 31 | `skill_version_histories` | Skills 版本历史 | 工具链 |

---

## Section 1：用户与权限体系

### users

| 列 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | bigint | PK, AUTO_INCREMENT | |
| `username` | varchar(255) | NOT NULL, UNIQUE | |
| `password` | varchar(255) | NOT NULL | BCrypt 加密存储 |
| `role` | enum('ADMIN','USER') | NOT NULL | **遗留字段**，RBAC 权限以 `user_roles` 为准 |
| `org_tags` | varchar(255) | | 所属组织，逗号分隔（遗留反范式字段） |
| `primary_org` | varchar(255) | | 主组织标签 |
| `avatar_url` | varchar(255) | | |
| `email` | varchar(128) | | |
| `phone` | varchar(32) | | |
| `bio` | text | | 个人简介 |
| `notification_preferences` | text | | JSON 格式通知偏好 |
| `created_at` | datetime(6) | | |
| `updated_at` | datetime(6) | | |

**注意**：`users.role` 是历史遗留字段，与 `roles/user_roles` RBAC 体系并存，代码判断权限应以 RBAC 为准。

### roles / permissions / user_roles / role_permissions

标准 RBAC 三层模型：用户 → 角色 → 权限。

- `roles`：角色定义，`role_code` 唯一（如 `ADMIN`、`USER`、`KB_MANAGER`）
- `permissions`：权限点，`perm_code` 唯一（如 `kb:write`、`doc:delete`）
- `user_roles`：用户-角色多对多关联表
- `role_permissions`：角色-权限多对多关联表

### resource_permissions

细粒度资源授权，支持将特定资源（知识库、文档等）授权给特定用户或组织。

| 关键列 | 说明 |
|---|---|
| `resource_type` | 资源类型：`kb` / `document` 等 |
| `resource_id` | 资源业务 ID |
| `grantee_type` | 被授权方类型：`user` / `org` |
| `grantee_id` | 被授权方 ID（username 或 tag_id） |
| `permission` | 权限级别：`read` / `write` / `admin` |

索引：`(resource_type, resource_id)`、`(grantee_type, grantee_id)`

---

## Section 2：组织与知识库

### organization_tags

多租户隔离的核心表。`tag_id` 为业务主键（自然键，由业务层生成）。支持通过 `parent_tag` 自引用构建层级结构（DB 层无外键约束，由业务层保证）。

| 关键列 | 说明 |
|---|---|
| `tag_id` | varchar(255)，自然主键 |
| `parent_tag` | 父标签 tag_id，空表示根标签 |
| `created_by` | FK → users.id |

### knowledge_bases

知识库与组织标签关联，通过 `org_tag` 控制多租户访问，`is_public` 控制公开访问。

| 关键列 | 说明 |
|---|---|
| `kb_id` | varchar(255)，业务唯一 ID |
| `org_tag` | 所属组织（可为空表示平台级） |
| `is_public` | 是否公开 |
| `created_by` | FK → users.id |

索引：`uk_kb_id`、`idx_kb_org_tag`、`idx_kb_created_by`

### i18n 表（organization_tag_i18n / knowledge_base_i18n / agent_i18n）

同一模式：`(business_id, lang)` 联合唯一，通过 FK 级联删除。

---

## Section 3：文件与向量存储

### file_upload

记录文件上传状态，支持分片上传（通过 `chunk_info`）。

| 关键列 | 说明 |
|---|---|
| `file_md5` | 文件指纹，与 `user_id` 联合唯一 |
| `user_id` | **varchar(64)（遗留类型不匹配）**，实际存 users.id 的字符串值 |
| `status` | int：0=上传中，1=已完成，2=处理中，3=已向量化 |
| `kb_id` | 归属知识库 |

**已知技术债**：`user_id` 类型为 varchar，与 `users.id`（bigint）不一致，无法建外键。

索引：`uk_md5_user(file_md5, user_id)`、`idx_file_user_id`、`idx_file_org_tag`、`idx_file_kb_id`

### chunk_info

分片上传的每个 chunk 的元数据，通过 `file_md5` 与 `file_upload` 关联。

索引：`idx_chunk_file_md5`

### document_vectors

向量化后的文档块元数据（向量本体存在 Elasticsearch）。

| 关键列 | 说明 |
|---|---|
| `file_md5` | 关联文件 |
| `chunk_id` | 文本块序号 |
| `text_content` | 原始文本（冗余存储，供检索结果回显） |
| `model_version` | 生成向量的模型版本 |
| `user_id` | varchar(64)，同 file_upload 遗留问题 |

索引：`idx_dv_file_md5`、`idx_dv_user_id`

---

## Section 4：对话、登录记录与通知

### conversations

平铺的单轮问答记录，每行一个 Q&A 对。

> **已知局限**：无会话（session）分组，无 Agent 绑定，不支持多轮上下文关联。

### login_records

登录审计日志，`user_id` 为 bigint（与 users.id 类型一致，但无外键约束）。

索引：`idx_lr_user_id`、`idx_lr_login_time`

### user_notifications

站内通知，通过 `recipient_username` 关联用户（字符串，非 FK）。

> **注意**：`is_read` 使用 `bit(1)` 存储，JPA 映射为 boolean。

索引：`idx_notif_recipient`、`idx_notif_recipient_unread(recipient_username, is_read)`

### user_favorites

用户收藏，支持多类型（chat / document / knowledge）。

- 联合唯一：`(user_id, type, target_id)`
- `target_id` 为被收藏对象的业务 ID（varchar(128)，非外键）

---

## Section 5：Agent 智能体体系

### agents

Agent 主表。

| 关键列 | 说明 |
|---|---|
| `agent_id` | varchar(64)，业务唯一 ID |
| `status` | 草稿 / 运行中 / 已停止（自由字符串，无 ENUM 约束） |
| `system_prompt` / `user_prompt` | 提示词，text 类型 |
| `knowledge_bases` | 关联知识库，逗号分隔（反范式） |
| `prompt_refs` / `mcp_tools` / `skill_refs` / `models` | 同上，逗号分隔 |
| `call_count` / `success_count` / `failure_count` | 运行统计计数器 |
| `install_count` | 安装次数，NOT NULL |

**设计说明**：关联字段（knowledge_bases 等）使用逗号分隔存储，便于快速开发，但无法做精确关联查询，后续可拆为关联表。

### agent_versions

Agent 的版本快照。每次发布时对整个 Agent 配置做全量快照，`is_active` 标识当前生效版本。

### agent_execution_logs

每次 Agent 执行的完整记录，包含 ReAct 步骤的 trace JSON。

| 关键统计列 | 说明 |
|---|---|
| `iterations` | 实际 ReAct 循环次数 |
| `prompt_tokens` / `completion_tokens` / `total_tokens` | Token 消耗 |
| `cost` / `model_cost` / `tool_cost` | 费用明细 |
| `trace_json` | ReAct thought/action/observation 步骤序列 |

索引：`uk_agent_exec_id`、`idx_ael_agent_id`、`idx_ael_status`、`idx_ael_started_at`

### agent_run_analysis_snapshots

按日聚合的 Agent 运行统计，`snapshot_date` 唯一，用于图表展示。

---

## Section 6：Workflow 工作流体系

结构与 Agent 体系镜像对称，区别在于：
- `workflows` 使用 `nodes_json` / `edges_json` 存储流程图定义
- `workflow_execution_logs` 增加 `variables_json` 记录节点间变量传递状态

---

## Section 7：工具链（MCP / Prompt / Skills）

### mcp_tool_configs

MCP 工具注册表，支持 MCP_JSON_RPC 和 HTTP_COMPAT 两种请求模式。

| 关键列 | 说明 |
|---|---|
| `type` | MCP / HTTP |
| `auth_type` | API Key / Bearer Token / 无鉴权 |
| `api_key` | 加密存储（代码层处理） |
| `input_schema` | JSON Schema 格式的工具输入定义 |

### prompt_templates / prompt_template_histories

模板主表 + 版本历史快照，结构与 Agent 版本机制相同：每次更新前对当前版本做全量快照。

### skill_definitions / skill_version_histories

技能定义 + 版本历史，字段包含完整的输入/输出 Schema 和运行时配置。

---

## 索引设计原则

| 场景 | 已覆盖的索引 |
|---|---|
| 按用户查文件 | `file_upload.idx_file_user_id` |
| 按知识库查文件 | `file_upload.idx_file_kb_id` |
| 按文件查分块 | `chunk_info.idx_chunk_file_md5` |
| 按用户查通知（未读） | `user_notifications.idx_notif_recipient_unread` |
| 按用户查登录记录 | `login_records.idx_lr_user_id` |
| Agent 执行历史查询 | `agent_execution_logs.idx_ael_agent_id` + `idx_ael_started_at` |
| 工作流执行历史查询 | `workflow_execution_logs.idx_wel_workflow_id` + `idx_wel_started_at` |
| 资源授权查询 | `resource_permissions.idx_resource`、`idx_grantee` |

---

## 已知技术债

| 级别 | 问题 | 影响 |
|---|---|---|
| P1 | `users.role` 与 RBAC 双重角色体系并存 | 权限判断不一致风险，需明确以 RBAC 为准并废弃 `users.role` |
| P1 | `file_upload.user_id` / `document_vectors.user_id` 为 varchar，与 `users.id`（bigint）类型不匹配 | 无法建外键，JOIN 需类型转换 |
| P2 | Agent/Workflow 的关联字段（knowledge_bases、prompt_refs 等）逗号分隔存储 | 无法按单值过滤，无参照完整性 |
| P2 | `conversations` 无会话分组，无 Agent 绑定 | 不支持多轮上下文，与 Agent 体系割裂 |
| P3 | status 字段为自由字符串，无 ENUM 约束 | 任意字符串可写入，需业务层校验 |
| P3 | `owner_name` 存字符串而非 FK | 用户改名后数据不同步 |

---

## 维护规范

1. **修改 JPA Entity 后必须同步更新 `docs/databases/schema.sql`**
2. 新建表时：CREATE TABLE 加入 schema.sql 主体部分；对存量库的变更加入末尾修复脚本
3. 不使用 `ddl-auto=create` 或 `ddl-auto=create-drop`，生产环境保持 `update`
4. 索引命名规范：`idx_{表名缩写}_{列名}` 或 `uk_{表名缩写}_{列名}`（唯一键）

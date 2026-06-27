# KfSmart AI Platform — 后端规范文档

> **用途**：本文档是后端的技术规范与功能现状记录。新功能开发、重构、技术决策均须同步至此文档。

---

## 目录

1. [技术选型](#技术选型)
2. [目录结构](#目录结构)
3. [统一响应格式](#统一响应格式)
4. [认证与安全](#认证与安全)
5. [RBAC 权限体系](#rbac-权限体系)
6. [分页规范](#分页规范)
7. [异常处理规范](#异常处理规范)
8. [日志规范](#日志规范)
9. [数据模型](#数据模型)
10. [核心服务](#核心服务)
11. [AI 集成](#ai-集成)
12. [消息队列与异步处理](#消息队列与异步处理)
13. [WebSocket 规范](#websocket-规范)
14. [工程规范](#工程规范)
15. [已实现 API 清单](#已实现-api-清单)
16. [Release Checklist](#release-checklist)

---

## 技术选型

| 层 | 选择 | 说明 |
|---|---|---|
| 框架 | Spring Boot 3.4.2 + Java 17 | |
| ORM | Spring Data JPA + Hibernate | DDL 策略：`ddl-auto: update` |
| 安全 | Spring Security 6 | JWT 无状态，`@EnableMethodSecurity` |
| Token | jjwt 0.11.5 | HS256，1h 过期 + 7d Refresh Token |
| 数据库 | MySQL 8.0 | 数据库名：`knowflow` |
| 缓存 | Redis 7 + Spring Data Redis | Token 缓存 + 基础缓存 |
| 搜索/向量 | Elasticsearch 8.10 | 文档检索 + 向量相似度搜索 |
| 消息队列 | Kafka 3.2 | 文件异步处理，死信队列（DLT）支持 |
| 文件存储 | MinIO 8.5.12 | 对象存储，与本地 `/avatars/` 混合 |
| 文件解析 | Apache Tika 2.9.1 | 支持 PDF/Word/Excel 等多格式 |
| HTTP 客户端 | Spring WebFlux WebClient | 请求 AI 服务（DeepSeek / Embedding） |
| 邮件 | Spring Mail（QQ SMTP / SSL 465） | OTP 验证码发送 |
| 构建 | Maven 3.8.6+ | 坐标：`com.smart:KnowFlow:0.0.1-SNAPSHOT` |

---

## 目录结构

```
src/main/java/com/smart/kf/
├── SmartKfApplication.java           # 入口（包名：com.smart.kf）
├── agent/
│   └── engine/                       # ReAct Agent 引擎
│       ├── AgentContext.java
│       ├── AgentStep.java
│       ├── ReActEngine.java
│       ├── ToolDefinition.java / ToolRegistry.java / ToolResult.java
│       └── tools/
│           ├── KnowledgeBaseTool.java
│           └── McpToolExecutor.java
├── client/
│   ├── EmbeddingClient.java          # text-embedding-v4 调用
│   └── ModelClient.java              # DeepSeek LLM 调用（SSE 流式）
├── config/
│   ├── AdminUserInitializer.java     # 启动时创建 admin 账号（Order=1）
│   ├── AiProperties.java             # AI 服务配置绑定（endpoint/key）
│   ├── EsConfig.java / EsIndexInitializer.java
│   ├── JwtAuthenticationFilter.java  # 每请求一次，解析 JWT / 自动刷新
│   ├── KafkaConfig.java
│   ├── LoggingInterceptor.java       # 请求/响应日志拦截器
│   ├── MinioConfig.java
│   ├── OrgTagAuthorizationFilter.java# 组织标签鉴权过滤
│   ├── OrgTagInitializer.java        # 启动时初始化组织标签
│   ├── RbacDataInitializer.java      # 初始化内置角色权限（Order=2）
│   ├── RedisConfig.java
│   ├── SecurityConfig.java           # Spring Security 主配置
│   ├── WebClientConfig.java
│   ├── WebConfig.java                # CORS + 静态资源
│   └── WebSocketConfig.java
├── consumer/
│   └── FileProcessingConsumer.java   # Kafka 文件处理消费者
├── controller/
│   ├── AdminController.java          # /api/v1/admin/** （需 system:admin）
│   ├── AgentController.java
│   ├── AgentExecutionController.java
│   ├── AgentVersionController.java
│   ├── ApiKeyConfigController.java
│   ├── AuthController.java           # /api/v1/auth/refreshToken
│   ├── ChatController.java           # /api/v1/chat（WebSocket token 发放）
│   ├── ConversationController.java
│   ├── DocumentController.java
│   ├── KnowledgeBaseController.java
│   ├── NotificationController.java
│   ├── ParseController.java
│   ├── RuntimeController.java
│   ├── SearchController.java
│   ├── SharedResourceController.java
│   ├── SkillController.java
│   ├── UploadController.java
│   ├── UserController.java
│   ├── WorkflowController.java
│   ├── WorkflowExecutionController.java
│   └── WorkflowVersionController.java
├── entity/                           # ES 实体
│   ├── EsDocument.java
│   ├── SearchRequest.java / SearchResult.java
│   ├── Message.java
│   └── TextChunk.java
├── exception/
│   ├── CustomException.java          # 带 HttpStatus 的业务异常
│   └── InvalidTokenException.java
├── handler/
│   ├── ChatWebSocketHandler.java     # 聊天 WebSocket
│   ├── WorkflowProgressBroadcaster.java
│   └── WorkflowWebSocketHandler.java # 工作流进度 WebSocket
├── model/                            # JPA 实体（MySQL）
│   ├── ApiKeyConfig.java
│   ├── ChunkInfo.java
│   ├── Conversation.java
│   ├── DocumentVector.java
│   ├── FileProcessingTask.java
│   ├── FileUpload.java
│   ├── KnowledgeBase.java
│   ├── LoginRecord.java
│   ├── OrganizationTag.java
│   ├── Permission.java
│   ├── ResourcePermission.java
│   ├── Role.java
│   ├── User.java
│   ├── UserFavorite.java
│   ├── UserNotification.java
│   └── agent/
│       ├── Agent.java
│       ├── AgentExecutionLog.java
│       ├── AgentRunAnalysisSnapshot.java
│       ├── AgentVersion.java
│       ├── McpToolConfig.java
│       └── PromptTemplate.java
│   └── workflow/
│       ├── Workflow.java
│       ├── WorkflowExecution.java
│       └── WorkflowVersion.java
├── repository/                       # Spring Data JPA
├── service/                          # 业务逻辑层
├── utils/
│   ├── JwtUtils.java
│   ├── LogUtils.java
│   ├── PasswordUtil.java
│   └── pagination/
│       ├── PageQuery.java
│       └── PageResult.java
├── workflow/
│   ├── engine/                       # 工作流 DAG 引擎
│   │   ├── WorkflowExecutionEngine.java
│   │   ├── dag/                      # GraphBuilder / TopologicalSorter
│   │   └── NodeExecutor.java / NodeExecutorRegistry.java
│   ├── executor/                     # 节点执行器（17 种节点类型）
│   └── model/
│       ├── WorkflowNode.java
│       └── WorkflowEdge.java
└── test/                             # 事务测试（开发用，非生产）
```

---

## 统一响应格式

所有 REST 接口统一返回如下 JSON 结构：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": { ... }
}
```

- `code`：与 HTTP 状态码一致（200 / 400 / 403 / 404 / 500）
- `message`：人类可读的操作结果描述
- `data`：业务数据，失败时可省略

**不使用全局 `@ControllerAdvice`**，各 Controller 手动 try/catch 返回标准格式。

分页接口的 `data` 字段使用 `PageResult<T>` 包装：

```json
{
  "records": [...],
  "total": 100,
  "page": 1,
  "size": 10,
  "totalPages": 10,
  "hasNext": true,
  "nextCursor": "b2Zmc2V0OjEw"  
}
```

---

## 认证与安全

### JWT Token 体系

```
Access Token   ：1 小时过期（EXPIRATION_TIME = 3600000ms）
Refresh Token  ：7 天过期（REFRESH_TOKEN_EXPIRATION_TIME = 604800000ms）
预刷新阈值     ：剩余 < 5 分钟时在 Filter 内自动刷新
宽限期         ：过期后 10 分钟内仍可通过 Filter 刷新
算法           ：HS256，密钥来自 jwt.secret-key（Base64 编码）
```

**Token 自动刷新流程（JwtAuthenticationFilter）：**

1. 提取 `Authorization: Bearer <token>`
2. 若 Token 有效且剩余 < 5 分钟 → 生成新 Token 写入响应头 `New-Token`
3. 若 Token 过期但在宽限期内 → 刷新后写入 `New-Token`，用刷新后的用户身份继续请求
4. 前端主动刷新：`POST /api/v1/auth/refreshToken`（body: `{ "refreshToken": "..." }`）

**Token Payload（Claims）：**

```
tokenId    - 唯一 ID，用于 Redis 缓存
role       - 兼容枚举 USER/ADMIN
userId     - 用户 ID（字符串）
orgTags    - 逗号分隔组织标签（如 "default,team-a"）
primaryOrg - 主组织标签
permissions - 逗号分隔权限码（如 "kb:read,kb:write,chat:use"）
```

### 路由安全三层

| 层级 | 机制 | 说明 |
|---|---|---|
| URL 路由级 | `SecurityConfig.authorizeHttpRequests` | 粗粒度白名单/管理员路径 |
| 方法级 | `@PreAuthorize("hasAuthority('perm:code')")` | 细粒度权限控制 |
| 数据行级 | `RbacService.hasResourcePermission()` | Service 层数据过滤 |

**公开路径（无需认证）：**

```
/api/v1/users/register
/api/v1/users/login
/api/v1/users/send-email-code
/api/v1/chat/websocket-token
/chat/**, /ws/**
/api/v1/test/**
/avatars/**, /static/**
```

**管理员专属（URL 级）：**

```
/api/v1/admin/**  →  hasAuthority('system:admin')
```

---

## RBAC 权限体系

### 内置权限码

| 权限码 | 描述 |
|---|---|
| `kb:read` | 查看和搜索知识库 |
| `kb:write` | 创建和修改知识库 |
| `kb:delete` | 删除知识库 |
| `kb:admin` | 管理知识库权限和成员 |
| `doc:read` | 查看和下载文档 |
| `doc:write` | 上传和修改文档 |
| `doc:delete` | 删除文档 |
| `agent:read` | 查看 Agent |
| `agent:write` | 创建和修改 Agent |
| `agent:run` | 运行 Agent |
| `user:read` | 查看用户列表 |
| `user:write` | 创建/修改/删除用户 |
| `system:admin` | 管理系统配置、API Key、组织标签 |
| `chat:use` | 使用 AI 聊天功能 |

### 内置角色

| 角色码 | 角色名 | 权限范围 |
|---|---|---|
| `ROLE_ADMIN` | 系统管理员 | 全部权限 |
| `ROLE_KB_MANAGER` | 知识库管理员 | kb:* + doc:* + agent:* + chat:use |
| `ROLE_USER` | 普通用户 | kb:read + doc:read/write + agent:read/run + chat:use |
| `ROLE_VIEWER` | 只读用户 | kb:read + doc:read |

### 资源级权限（ResourcePermission）

知识库支持细粒度授权，可对 `user / role / org` 三种授权对象设置 `read / write / delete / admin` 权限：

```
POST   /api/v1/knowledge-bases/{kbId}/permissions   # 授权
DELETE /api/v1/knowledge-bases/{kbId}/permissions   # 撤销
GET    /api/v1/knowledge-bases/{kbId}/permissions   # 查询列表
```

### 用户 Model（User.java）

- `legacyRole`（Deprecated）：保留兼容旧枚举字段，新代码通过 `roles` 集合判断
- `roles`（`Set<Role>`）：多角色，EAGER 加载，`@ManyToMany`
- `orgTags`：逗号分隔的组织标签字符串
- `primaryOrg`：主组织标签

---

## 分页规范

### PageQuery（请求）

```
?page=1&size=10&cursor=b2Zmc2V0OjEw
```

| 参数 | 默认值 | 约束 |
|---|---|---|
| `page` | 1 | 最小 1 |
| `size` | 10 | 最大 100（`MAX_SIZE`） |
| `cursor` | null | Base64 编码的 `offset:N` 字符串 |

深翻页限制：不使用 cursor 时，offset > 10000 抛出 `IllegalArgumentException`。

### PageResult（响应）

Java Record，含 `records / total / page / size / totalPages / hasNext / nextCursor`。

工厂方法：
- `PageResult.fromPage(Page<T> page)`：适用于 Spring Data JPA 分页结果
- `PageResult.fromList(List<T> source, PageQuery query)`：适用于内存分页

---

## 异常处理规范

### CustomException

```java
throw new CustomException("知识库不存在", HttpStatus.NOT_FOUND);
```

Controller 层 catch 后按 `e.getStatus()` 返回对应 HTTP 状态码，格式：

```json
{ "code": 404, "message": "知识库不存在" }
```

### Controller 异常分类处理

| 异常类型 | HTTP 状态 | 场景 |
|---|---|---|
| `SecurityException` | 403 | 数据行级权限检查失败 |
| `IllegalArgumentException` | 400 / 404 | 入参校验失败、记录不存在 |
| `CustomException` | 视 `e.getStatus()` | 业务逻辑主动抛出 |
| `Exception`（兜底） | 500 | 未预期异常 |

---

## 日志规范

使用 `LogUtils` 工具类，底层基于 SLF4J + MDC。

```java
// 业务日志（com.smart.kf.business Logger）
LogUtils.logBusiness("OPERATION_CODE", username, "描述: param=%s", value);
LogUtils.logBusinessError("OPERATION_CODE", username, "失败原因: %s", exception, e.getMessage());

// 用户操作日志
LogUtils.logUserOperation(username, "OPERATION_CODE", "step", "SUCCESS/FAILED");

// 聊天日志
LogUtils.logChat(userId, sessionId, "USER_MESSAGE", messageLength);

// 性能监控（每个接口的标准写法）
LogUtils.PerformanceMonitor monitor = LogUtils.startPerformanceMonitor("OPERATION_CODE");
try {
    // 业务逻辑
    monitor.end("操作成功");
} catch (Exception e) {
    monitor.end("操作失败: " + e.getMessage());
}
```

**规范：**
- 每个 Controller 方法头部调用 `startPerformanceMonitor`，结尾调用 `monitor.end()`
- 操作码（OPERATION_CODE）使用大写下划线，与 LogUtils 的常量保持一致
- 日志中不记录密码、Token 明文

---

## 数据模型

### 核心 JPA 实体（MySQL，数据库 `knowflow`）

| 实体 | 表名 | 说明 |
|---|---|---|
| `User` | `users` | 用户账号，含 RBAC 角色、组织标签 |
| `Role` | `roles` | 角色定义 |
| `Permission` | `permissions` | 细粒度权限码 |
| `ResourcePermission` | `resource_permissions` | 资源级授权（kb/doc） |
| `KnowledgeBase` | `knowledge_bases` | 知识库，关联 orgTag |
| `FileUpload` | `file_uploads` | 文件元数据，含 MD5 指纹 |
| `ChunkInfo` | `chunk_infos` | 文档切片记录 |
| `DocumentVector` | `document_vectors` | 向量化记录（指向 ES） |
| `Conversation` | `conversations` | 对话会话 |
| `OrganizationTag` | `organization_tags` | 多租户组织标签 |
| `LoginRecord` | `login_records` | 用户登录记录 |
| `UserNotification` | `user_notifications` | 站内通知 |
| `UserFavorite` | `user_favorites` | 用户收藏 |
| `ApiKeyConfig` | `api_key_configs` | 自定义 API Key 配置 |
| `Agent` | `agents` | Agent 定义 |
| `AgentVersion` | `agent_versions` | Agent 版本快照 |
| `AgentExecutionLog` | `agent_execution_logs` | Agent 执行日志 |
| `PromptTemplate` | `prompt_templates` | Prompt 模板 |
| `McpToolConfig` | `mcp_tool_configs` | MCP 工具配置 |

### Elasticsearch 文档结构

`EsDocument`：存储文档分块，含向量字段，支持 KNN 相似度搜索。

---

## 核心服务

| 服务 | 说明 |
|---|---|
| `UserService` | 注册、登录、用户管理、邮箱 OTP |
| `ChatHandler` | RAG 对话处理，协调知识库检索 + LLM 调用 |
| `KnowledgeBaseService` | 知识库 CRUD，含统计和访问控制 |
| `DocumentService` | 文档元数据管理，删除文档时清理 ES 索引和 MinIO 文件 |
| `UploadService` | 分片上传协调，MD5 秒传，发送 Kafka 消息触发解析 |
| `VectorizationService` | 获取分块 → 调用 Embedding API → 写入 ES |
| `ElasticsearchService` | ES 索引管理、向量搜索、文档同步 |
| `RbacService` | 权限检查、资源级授权管理、用户权限查询 |
| `EmailService` | 发送邮箱验证码（Redis 缓存 OTP，5 分钟过期） |
| `TokenCacheService` | JWT Token Redis 缓存（黑名单/刷新记录） |
| `SystemActivityService` | 系统操作记录（管理后台 ActivityLog） |
| `ApiKeyConfigService` | 自定义 API Key 增删查 |
| `AgentService` | Agent CRUD + 版本管理 |
| `AgentExecutionService` | 驱动 ReActEngine 执行，记录执行日志 |
| `WorkflowService` | 工作流 CRUD + 版本管理 |
| `WorkflowExecutionService` | 驱动 WorkflowExecutionEngine，推送 WebSocket 进度 |
| `SkillService` | 技能定义 CRUD + 版本历史 |
| `SharedResourceService` | Prompt 模板 / MCP 工具共享资源管理 |

---

## AI 集成

### LLM（ModelClient）

- 调用 DeepSeek API（SSE 流式），通过 Spring WebFlux `WebClient`
- 配置：`application.yml` → `ai.*`（endpoint、api-key、model-name）
- 支持运行时通过 `ApiKeyConfig` 覆盖默认配置

### Embedding（EmbeddingClient）

- 调用 DashScope text-embedding-v4
- 批量文本 → `List<float[]>` 向量
- 由 `VectorizationService` 调用，Kafka 消费者触发

### RAG Pipeline

```
用户消息
  → ElasticsearchService.search(query, orgTags)   # 向量相似度检索
  → 检索到的 chunks 拼入 context
  → ModelClient.streamChat(systemPrompt + context + message)  # SSE 流式
  → ChatWebSocketHandler.sendMessage(chunk)        # 逐 token 推送前端
```

### Agent（ReAct 引擎）

```
AgentContext
  → ReActEngine.execute(agent, query, history, debugOverrides)
    → Thought → Action(tool) → Observation → ...  # 最多 N 轮
    → Final Answer
  → AgentExecutionLog 记录每一步
```

内置工具：`KnowledgeBaseTool`（知识库检索）、`McpToolExecutor`（调用外部 MCP 工具）

### 工作流引擎（DAG）

17 种节点类型，通过 `NodeExecutorRegistry` 分发：

| 节点类型 | 说明 |
|---|---|
| `start / end` | 开始/结束节点 |
| `llm` | LLM 调用节点 |
| `knowledge_base` | 知识库检索节点 |
| `condition` | 条件分支节点 |
| `loop` | 循环节点 |
| `code / python` | 代码执行节点 |
| `sql` | SQL 查询节点 |
| `http / webhook` | HTTP 请求节点 |
| `mcp_tool` | MCP 工具节点 |
| `agent_call` | Agent 调用节点 |
| `prompt` | Prompt 模板节点 |
| `variable` | 变量节点 |
| `approval` | 人工审批节点 |
| `delay` | 延迟节点 |
| `email` | 邮件通知节点 |
| `feishu_notification` | 飞书通知节点 |
| `wechat_work_notification` | 企业微信通知节点 |
| `message_notification` | 站内消息节点 |

---

## 消息队列与异步处理

### Kafka

- **Topic**：`file-processing-topic1`（配置 `kafka.topic.file-processing`）
- **DLT**：`file-processing-dlt`（死信队列）
- **幂等生产者**：`enable-idempotence: true`，`transactional-id-prefix: file-upload-tx-`
- **消费者组**：`file-processing-group`

### 文件处理流程

```
UploadController.upload()
  → 保存文件元数据到 MySQL（FileUpload）
  → 发送 KafkaTemplate 消息（FileProcessingTask）
  
FileProcessingConsumer（@KafkaListener）
  → 解析文件（Apache Tika）
  → 切分文本（chunk-size: 1500 字符）
  → 保存 ChunkInfo 到 MySQL
  → VectorizationService.vectorize()
    → EmbeddingClient.embed()
    → ElasticsearchService.indexDocument()
```

---

## WebSocket 规范

### 聊天 WebSocket（`ChatWebSocketHandler`）

**连接路径**：`/chat/{token}`（无需额外认证头，token 在路径中）

**获取 WebSocket 停止令牌**：
```
GET /api/v1/chat/websocket-token
```

**客户端 → 服务端消息：**

```json
// 聊天消息
{ "type": "chat", "conversationId": "...", "message": "用户输入", "apiKeyConfigId": null }

// 停止响应（需 websocket-token）
{ "type": "stop", "_internal_cmd_token": "<cmdToken>" }
```

**服务端 → 客户端消息：**

```json
// 连接建立
{ "type": "connection", "sessionId": "...", "message": "WebSocket连接已建立" }

// 流式内容块
{ "type": "chunk", "content": "..." }

// 完成
{ "type": "done", "conversationId": "..." }

// 错误
{ "type": "error", "message": "..." }
```

### 工作流 WebSocket（`WorkflowWebSocketHandler`）

`WorkflowProgressBroadcaster` 广播工作流节点执行进度，格式：

```json
{ "type": "node_progress", "nodeId": "...", "status": "running|success|error", "output": "..." }
```

---

## 工程规范

### 新增接口标准模式

```java
@PostMapping("/some-resource")
@PreAuthorize("hasAuthority('resource:write')")
public ResponseEntity<?> createSomething(
        @RequestHeader("Authorization") String token,
        @RequestBody CreateRequest request) {
    
    LogUtils.PerformanceMonitor monitor = LogUtils.startPerformanceMonitor("CREATE_SOMETHING");
    String username = null;
    try {
        username = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
        LogUtils.logBusiness("CREATE_SOMETHING", username, "创建请求: name=%s", request.name());
        
        var result = someService.create(request.name(), username);
        
        monitor.end("创建成功");
        return ResponseEntity.ok(Map.of("code", 200, "message", "创建成功", "data", result));
    } catch (SecurityException e) {
        monitor.end("权限不足");
        return ResponseEntity.status(403).body(Map.of("code", 403, "message", e.getMessage()));
    } catch (IllegalArgumentException e) {
        monitor.end("参数错误: " + e.getMessage());
        return ResponseEntity.badRequest().body(Map.of("code", 400, "message", e.getMessage()));
    } catch (Exception e) {
        LogUtils.logBusinessError("CREATE_SOMETHING", username, "创建异常", e);
        monitor.end("创建异常: " + e.getMessage());
        return ResponseEntity.status(500).body(Map.of("code", 500, "message", "创建失败: " + e.getMessage()));
    }
}

// 请求体使用 Java Record
public record CreateRequest(String name, String description) {}
```

### 关键约定

- **Request DTO**：统一使用 Java Record（`record XxxRequest(...) {}`），定义在 Controller 内部
- **Token 解析**：从 `@RequestHeader("Authorization")` 提取，调用 `jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""))`
- **权限注解**：方法级 `@PreAuthorize("hasAuthority('perm:code')")`，`/admin/**` 路径已在路由级保护
- **分页参数**：使用 `PageQuery.of(page, size, cursor)` 规范化，最大 100 条/页
- **测试代码隔离**：`test/` 包下的类（`TransactionTestController` 等）仅用于本地调试，**禁止在生产调用**

### 配置管理

| 配置文件 | 用途 |
|---|---|
| `application.yml` | 默认配置（本地开发） |
| `application-dev.yml` | 开发环境覆盖 |
| `application-docker.yml` | Docker 部署覆盖 |

生产部署必须覆盖的配置：

```
jwt.secret-key
ai.endpoint / ai.api-key / ai.embedding-api-key
spring.datasource.*
spring.data.redis.*
minio.endpoint / minio.accessKey / minio.secretKey
spring.kafka.bootstrap-servers
spring.mail.username / spring.mail.password
admin.username / admin.password
```

### 文件上传限制

```yaml
spring.servlet.multipart.max-file-size: 50MB
spring.servlet.multipart.max-request-size: 100MB
```

文档分块：`file.parsing.chunk-size: 1500`（字符数）

---

## 已实现 API 清单

> 最后更新：2026-06-27

### 认证（`/api/v1/auth`）

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| POST | `/auth/refreshToken` | 公开 | 主动刷新 Token |

### 用户（`/api/v1/users`）

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| POST | `/users/register` | 公开 | 注册（含邮箱 OTP 验证） |
| POST | `/users/login` | 公开 | 登录（用户名/邮箱 + 密码） |
| POST | `/users/send-email-code` | 公开 | 发送邮箱验证码 |
| GET | `/users/me` | 已登录 | 获取当前用户信息 + 权限列表 |
| PUT | `/users/me` | 已登录 | 更新个人资料（昵称/邮箱/手机/简介） |
| PUT | `/users/me/password` | 已登录 | 修改密码 |
| POST | `/users/me/avatar` | 已登录 | 上传头像（2MB 限制） |
| GET | `/users/me/login-records` | 已登录 | 登录记录 |
| GET | `/users/me/favorites` | 已登录 | 收藏列表 |
| POST | `/users/me/favorites` | 已登录 | 添加收藏 |
| DELETE | `/users/me/favorites/{id}` | 已登录 | 取消收藏 |
| GET | `/users/me/notification-preferences` | 已登录 | 通知偏好设置 |
| PUT | `/users/me/notification-preferences` | 已登录 | 更新通知偏好 |

### 知识库（`/api/v1/knowledge-bases`）

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| POST | `/knowledge-bases` | `kb:write` | 创建知识库 |
| GET | `/knowledge-bases` | 已登录 | 列表（支持 keyword/orgTag/isPublic/createdBy/updatedAfter/分页） |
| GET | `/knowledge-bases/stats` | 已登录 | 统计概览 |
| GET | `/knowledge-bases/filter-options` | 已登录 | 筛选选项（orgTag 列表等） |
| POST | `/knowledge-bases/refresh` | 已登录 | 手动刷新统计数据 |
| GET | `/knowledge-bases/{kbId}` | 已登录 | 知识库详情 |
| PUT | `/knowledge-bases/{kbId}` | `kb:write` | 更新知识库 |
| DELETE | `/knowledge-bases/{kbId}` | `kb:delete` | 删除知识库 |
| GET | `/knowledge-bases/{kbId}/documents` | 已登录 | 该知识库下的文档列表 |
| GET | `/knowledge-bases/{kbId}/permissions` | `kb:admin\|system:admin` | 权限列表 |
| POST | `/knowledge-bases/{kbId}/permissions` | `kb:admin\|system:admin` | 授权 |
| DELETE | `/knowledge-bases/{kbId}/permissions` | `kb:admin\|system:admin` | 撤销授权 |

### 文档（`/api/v1/documents`）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/documents` | 文档列表（支持分页/筛选） |
| GET | `/documents/{id}` | 文档详情 |
| DELETE | `/documents/{id}` | 删除文档（同步清理 ES + MinIO） |
| POST | `/documents/parse` | 触发重新解析 |

### 文件上传（`/api/v1/upload`）

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/upload` | 上传文件（multipart，含 MD5 秒传） |
| POST | `/upload/chunk` | 分片上传 |

### 对话（`/api/v1/conversations` + WebSocket）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/conversations` | 对话列表 |
| POST | `/conversations` | 创建对话 |
| GET | `/conversations/{id}` | 对话详情 + 消息历史 |
| DELETE | `/conversations/{id}` | 删除对话 |
| GET | `/chat/websocket-token` | 获取 WebSocket 停止令牌 |
| WS | `/chat/{token}` | 聊天 WebSocket 连接 |

### Agent（`/api/v1/agents`）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET/POST | `/agents` | 列表/创建 |
| GET/PUT/DELETE | `/agents/{id}` | 详情/更新/删除 |
| POST | `/agents/{id}/execute` | 执行 Agent（同步） |
| GET | `/agents/{id}/executions` | 执行日志列表 |
| GET/POST/PUT | `/agents/{id}/versions` | 版本管理 |

### 工作流（`/api/v1/workflows`）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET/POST | `/workflows` | 列表/创建 |
| GET/PUT/DELETE | `/workflows/{id}` | 详情/更新/删除 |
| POST | `/workflows/{id}/execute` | 异步执行（进度通过 WebSocket 推送） |
| GET | `/workflows/{id}/executions` | 执行历史 |
| GET/POST | `/workflows/{id}/versions` | 版本管理 |

### 技能（`/api/v1/skills`）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET/POST | `/skills` | 列表/创建 |
| GET/PUT/DELETE | `/skills/{id}` | 详情/更新/删除 |

### 共享资源（`/api/v1/shared-resources`）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET/POST | `/shared-resources/prompts` | Prompt 模板列表/创建 |
| GET/PUT/DELETE | `/shared-resources/prompts/{id}` | Prompt 模板操作 |
| GET/POST | `/shared-resources/mcp-tools` | MCP 工具列表/创建 |
| GET/PUT/DELETE | `/shared-resources/mcp-tools/{id}` | MCP 工具操作 |
| GET/POST | `/shared-resources/api-keys` | API Key 列表/创建 |

### 通知（`/api/v1/notifications`）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/notifications` | 通知列表 |
| PUT | `/notifications/{id}/read` | 标记已读 |
| PUT | `/notifications/read-all` | 全部已读 |

### 搜索（`/api/v1/search`）

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/search` | 全文/向量混合搜索 |

### 解析（`/api/v1/parse`）

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/parse` | 手动触发文档解析（支持重试） |

### 运行时（`/api/v1/runtime`）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/runtime/status` | 服务健康状态（ES/Kafka/Redis/MinIO） |

### 管理后台（`/api/v1/admin`，需 `system:admin`）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET/POST/PUT/DELETE | `/admin/users` | 用户管理 CRUD |
| GET/POST/PUT/DELETE | `/admin/roles` | 角色管理 |
| GET | `/admin/permissions` | 权限列表 |
| GET/POST/PUT/DELETE | `/admin/org-tags` | 组织标签管理 |
| GET | `/admin/system-status` | 系统状态监控 |
| GET | `/admin/activity-log` | 操作记录 |
| GET/POST/DELETE | `/admin/api-keys` | API Key 管理 |

---

## Release Checklist

每次发布前逐项确认：

**代码质量**
- [ ] `mvn clean verify` 全部通过
- [ ] 无 `TODO/FIXME` 遗留在关键路径
- [ ] 无硬编码敏感信息（密码、API Key）

**配置核查**
- [ ] `application.yml` 占位符（`your_qq@qq.com` 等）已在生产配置替换
- [ ] `jwt.secret-key` 已更换为强随机密钥
- [ ] `admin.password` 已修改
- [ ] Elasticsearch / Kafka / MinIO 连接信息已更新

**功能验证**
- [ ] 注册/登录/Token 刷新正常
- [ ] 知识库 CRUD + 权限隔离正常
- [ ] 文件上传 → Kafka → 向量化 → ES 索引 全流程正常
- [ ] Chat WebSocket 流式输出正常
- [ ] 管理后台权限拦截正常

**部署动作**
- [ ] `mvn clean package` 生成 JAR
- [ ] 数据库迁移（`ddl-auto: update` 自动执行，重要变更需确认）
- [ ] ES 索引初始化（`EsIndexInitializer` 启动时自动创建）
- [ ] 启动后检查日志：`RBAC 内置数据初始化完成`、`Security configuration loaded successfully`

---

*最后更新：2026-06-27*

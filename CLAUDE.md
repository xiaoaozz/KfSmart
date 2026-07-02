# CLAUDE.md

本文件为 Claude Code (claude.ai/code) 在本仓库工作时提供指引。**本仓库的真实技术栈以本文件为准**（旧文档曾误记为 Vue 3 / `com.yizhaoqi.smartpai`，已纠正）。

## Project Overview

KfSmart AI Platform（Maven artifactId: `KnowFlow`）是基于 RAG（检索增强生成）的企业级 AI 知识管理平台，提供文档处理/混合检索/流式问答、Agent（ReAct）、Workflow（DAG 工作流）、Skill、MCP 工具、多租户 RBAC、API Key 动态配置等能力。

- 后端：Spring Boot 3.4.2 / Java 17 / 包路径 `com.smart.kf`
- 前端：React 19 + TypeScript + Vite + Ant Design + Zustand + React Query + React Flow（`@xyflow/react`）+ i18next
- 存储：MySQL 8 + Redis 7 + MinIO 8.5 + Elasticsearch 8.10（含 IK 中文分词）
- 消息：Kafka 3.2（文件处理异步流水线）
- AI：OpenAI/Anthropic 协议兼容的 `ModelClient` + DashScope `text-embedding-v4`

## Prerequisites

Java 17、Maven 3.8.6+、Node.js 18.20+、pnpm 8.7+、MySQL 8、Elasticsearch 8.10、MinIO 8.5、Kafka 3.2、Redis 7。

## Quick Start

```bash
# 启动基础设施（docker-compose 会自动读取同目录 .env；首次请 cp .env.example .env 并填值）
cd docs && docker-compose up -d

# 后端
mvn spring-boot:run                    # 默认 profile（ddl-auto=validate，需先建表）
mvn spring-boot:run -Dspring-boot.run.profiles=dev   # dev profile（ddl-auto=update，便于本地建表）

# 前端
cd frontend && pnpm install && pnpm dev
```

## Common Commands

### Backend (Spring Boot)
```bash
mvn spring-boot:run                    # 运行
mvn clean package                      # 打包
mvn test                               # 测试（部分 @SpringBootTest 需 MySQL/Redis/ES 在线）
mvn -DskipTests compile                # 仅编译
```

### Frontend (React + Vite + pnpm)
```bash
cd frontend
pnpm install
pnpm dev                # 开发服务器
pnpm build              # 生产构建
pnpm typecheck          # 类型检查
pnpm lint               # ESLint + oxlint
pnpm test               # Vitest 单测
pnpm test:e2e           # Playwright E2E
```

## Architecture Overview

### 后端结构 `src/main/java/com/smart/kf/`
```
SmartKfApplication.java        # @SpringBootApplication @EnableAsync
agent/engine/                  # ReActEngine + ToolRegistry + ToolDefinition + AgentContext/Step
client/                        # ModelClient(OpenAI/Anthropic 兼容) + EmbeddingClient + TokenCost
config/                        # Security/JWT/ES/Kafka/MinIO/Web/AiProperties...
consumer/                      # FileProcessingConsumer（Kafka）
controller/                    # 20 个 Controller（AdminController 2202 行，待拆分）
entity/                        # ES 文档 / TextChunk / SearchResult
exception/                     # CustomException / InvalidTokenException
handler/                       # 3 个 WebSocket Handler（Chat/Workflow 进度广播）
model/                         # JPA Entity + agent/workflow 子包
repository/                    # JPA Repository + RedisRepository
service/                       # 22+ Service（含 service/workflow、service/agent 子包）
utils/                         # JwtUtils / LogUtils / MinioMigrationUtil / Rsa / Password
workflow/                      # engine(DAG: GraphBuilder/TopologicalSorter/ExecutionContext/NodeTrace) + executor(21 种 NodeExecutor) + model
```

### 前端结构 `frontend/src/`
```
api/        # 11 个 axios 模块 + http.ts（Token 刷新队列 + 请求去重）
stores/     # Zustand（auth/layout/locale/theme/ui）
router/     # React Router（懒加载 + 守卫 + ErrorBoundary）
pages/      # 12 个业务域（auth/dashboard/chat/agent/workflow/skill/admin/...）
components/ layouts/ hooks/ utils/ types/ theme/ locales/ monitoring/ config/
i18n.ts main.tsx App.tsx
```

### 主链路
- **RAG**：Upload(分片)→MinIO→Kafka→`FileProcessingConsumer`→`ParseService`(Tika)→切块→`EmbeddingClient`(text-embedding-v4)→ES `knowledge_base` 索引。Query→`ChatHandler.processMessage`→`HybridSearchService`(KNN 召回 + BM25 Rescore + 权限过滤)→`ModelClient.streamResponse`→WebSocket 流式推送。
- **Agent**：`ReActEngine.execute`→`ToolRegistry.resolveTools`(KB+MCP)→`ModelClient.chatWithFunctions`→tool_calls→observation→直到 final_answer 或 maxIterations。
- **Workflow**：`WorkflowExecutionEngine.execute`→`GraphBuilder`→`TopologicalSorter`(环检测)→BFS `executeFromNode`(扇入等待 `isReadyToExecute` + 条件路由 `routingPort`)→`NodeExecutorRegistry` 分发到 21 种 `NodeExecutor`。

## Configuration

- `src/main/resources/application.yml`（base，生产默认 `ddl-auto: validate`）
- `application-dev.yml`（dev，`ddl-auto: update` 便于本地）
- `application-docker.yml`（docker 部署）
- **所有密钥通过环境变量注入**：见 `.env.example`。`application*.yml` 中 `${ENV:默认}` 仅作本地开发兜底，生产必须覆盖。`.env` 已被 `.gitignore` 忽略（勿提交）。
- 关键环境变量：`JWT_SECRET_KEY`、`MYSQL_PASSWORD`、`REDIS_PASSWORD`、`MINIO_ACCESS_KEY/SECRET_KEY`、`ES_PASSWORD`、`ADMIN_PASSWORD`、`AI_DEFAULT_KEY`、`EMBEDDING_API_KEY`、`ADMIN_DANGEROUS_CLEAR_KEY/MIGRATE_KEY`、`WORKFLOW_CODE_NODE_ENABLED`、`WORKFLOW_SQL_NODE_QUERY_TIMEOUT_SECONDS` 等。

## Database

MySQL + JPA/Hibernate，`ddl-auto: validate`（base/docker）/ `update`（dev）。`docs/databases/schema.sql` + `migrations/` 提供建表脚本（待接入 Flyway 自动化迁移）。核心实体：`User`、`FileUpload`、`Conversation`、`OrganizationTag`、`ChunkInfo`、`ApiKeyConfig`、`Agent`、`Workflow`、`WorkflowExecutionLog` 等。

## External Services

Elasticsearch 8.10（文档搜索 + 向量存储 + IK 分词）、Kafka 3.2（异步文件处理 + DLT）、Redis 7（缓存 + 会话历史 + Token 吊销）、MinIO 8.5（对象存储）、MySQL 8（主库）。AI：DeepSeek/Anthropic 兼容 API（LLM）、DashScope `text-embedding-v4`（向量化）。

## Development Workflow

- 后端新增能力：entity→repository→service→controller；AI 引擎扩展优先用注册机制（`NodeExecutor`+`@Component` 自动扫描 / `ToolRegistry`）。
- 前端：api 模块→store→page→router；用 React 19 + TS + Zustand + React Query，遵循 `components/` 既有模式。
- **安全红线**：密钥不入 VCS（用环境变量）、Kafka `trusted.packages` 禁止 `"*"`、工作流 Code/SQL 节点默认禁用/加固（见 `workflow.code-node.enabled`、`SqlNodeExecutor` 白名单）、危险端点（`clearAllData`/`migrateMinioFiles`）密钥外置。

## Testing

后端：`mvn test`（Service 单测为主，Controller 集成测试待补）。前端：`pnpm test`（Vitest）+ `pnpm test:e2e`（Playwright）。

## Deployment

```bash
mvn clean package                       # 后端 jar
cd frontend && pnpm build               # 前端 dist
cd docs && docker-compose up -d         # 基础设施
```

`Dockerfile.frontend` 提供前端镜像。生产环境变量见 `.env.example`。

## Security Considerations

JWT（Spring Security + jjwt）+ 角色/方法级 `@PreAuthorize` + 行级 `RbacService`/`OrgTagAuthorizationFilter` 三层权限；多租户组织标签隔离；文件上传校验；CORS 配置；输入校验。**整改状态见 `.claude/plan/remediation-progress.md`**。

## 整改进度

`.claude/plan/` 下四份 review 报告 + 本进度跟踪文件 `remediation-progress.md`（✅ 已完成 / 🚧 部分完成 / ⏳ 后续会话）。后续会话据此认领未完成项。

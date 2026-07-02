# KfSmart AI Platform

企业级 RAG（检索增强生成）AI 知识管理平台：文档处理 → 切块 → 向量化 → 混合检索 → LLM 流式问答，并扩展出 Agent（ReAct）、Workflow（DAG 工作流）、Skill、MCP 工具、多租户 RBAC、API Key 动态配置等能力。

## 技术栈

| 层 | 选型 |
|----|------|
| 后端 | Spring Boot 3.4.2 / Java 17 / 包 `com.smart.kf` |
| 前端 | React 19 + TypeScript + Vite + Ant Design + Zustand + React Query + React Flow + i18next |
| 存储 | MySQL 8 · Redis 7 · MinIO 8.5 · Elasticsearch 8.10（含 IK 中文分词） |
| 消息 | Kafka 3.2（文件处理异步流水线 + DLT） |
| AI | OpenAI/Anthropic 协议兼容 `ModelClient` + DashScope `text-embedding-v4` |
| 测试 | JUnit / Vitest / Playwright |

## 架构

```
Frontend (React 19) ──HTTP/WebSocket(JWT)──► Backend (Spring Boot 3.4.2)
  Pages→api(axios)→stores(Zustand)→ReactQuery      Controllers → Services → Repositories
  Router(懒加载+守卫+ErrorBoundary)                  AI Engine: ModelClient / ReActEngine / WorkflowExecutionEngine
  i18n / Theme / Web Vitals                          Async: Kafka → FileProcessingConsumer → Parse/Embed → ES
                                                    Data: JPA + Redis + ElasticsearchService
                                                       ↓        ↓        ↓        ↓
                                                     MySQL    Redis    MinIO    ES 8.10
```

主链路：
- **RAG**：分片上传 → MinIO → Kafka → Tika 解析 → 切块 → Embedding → ES 索引；查询 → KNN 召回 + BM25 Rescore + 权限过滤 → LLM 流式。
- **Agent**：ReAct 循环（LLM → tool_calls → observation → final_answer），工具来自 `ToolRegistry`（KnowledgeBase + MCP）。
- **Workflow**：DAG 拓扑排序 + 扇入等待 + 条件路由 + 21 种节点执行器（LLM/Code/SQL/HTTP/Condition/Loop/AgentCall/MCP/...）+ Trace + Token 计费。

## 快速开始

### 1. 基础设施
```bash
cp .env.example .env          # 填入真实密钥（.env 已被 git 忽略）
cd docs && docker-compose up -d   # MySQL/Redis/MinIO/Kafka/ES（自动读取 .env）
```

### 2. 后端
```bash
mvn spring-boot:run                              # 默认 profile（ddl-auto=validate，需先建表）
mvn spring-boot:run -Dspring-boot.run.profiles=dev   # dev profile（ddl-auto=update，便于本地）
mvn clean package
```

### 3. 前端
```bash
cd frontend
pnpm install
pnpm dev          # 开发服务器
pnpm build        # 生产构建
```

## 配置与环境变量

所有密钥通过环境变量注入，**禁止入 VCS**。完整清单见 [`.env.example`](./.env.example)。`application*.yml` 中的 `${ENV:默认}` 仅作本地开发兜底，生产必须覆盖。

| 变量 | 用途 |
|------|------|
| `JWT_SECRET_KEY` | JWT 签名密钥（生产必须为高熵随机串并定期轮换） |
| `MYSQL_PASSWORD` / `MYSQL_ROOT_PASSWORD` | MySQL 口令 |
| `REDIS_PASSWORD` | Redis 口令 |
| `MINIO_ACCESS_KEY` / `MINIO_SECRET_KEY` / `MINIO_ROOT_USER` / `MINIO_ROOT_PASSWORD` | MinIO 凭证 |
| `ES_PASSWORD` | Elasticsearch 口令 |
| `ADMIN_PASSWORD` | 初始化管理员口令 |
| `AI_DEFAULT_KEY` / `EMBEDDING_API_KEY` | LLM / Embedding API Key |
| `ADMIN_DANGEROUS_CLEAR_KEY` / `ADMIN_DANGEROUS_MIGRATE_KEY` | 危险操作（清空数据/MinIO 迁移）二次确认密钥，留空则端点禁用 |
| `WORKFLOW_CODE_NODE_ENABLED` | 工作流代码执行节点开关（默认 `false`，沙箱未接入前禁用） |
| `WORKFLOW_SQL_NODE_QUERY_TIMEOUT_SECONDS` | SQL 节点查询超时（默认 10） |

配置文件：`application.yml`（base）/ `application-dev.yml`（dev）/ `application-docker.yml`（docker）。

## 安全提示

- 已完成止血整改：密钥外置、Kafka 反序列化白名单、Code/SQL 节点加固、`Thread.sleep` 移除、`ddl-auto: validate`、日志降级、JWT 日志脱敏、危险端点密钥外置。详见 [`.claude/plan/remediation-progress.md`](./.claude/plan/remediation-progress.md)。
- **待办**：轮换历史已泄漏的 JWT 密钥与 docker MinIO 凭证（已从 VCS 移除，但历史记录仍存）。

## 文档

- [CLAUDE.md](./CLAUDE.md) — 代码导航与开发指引
- [.claude/plan/](./.claude/plan/) — 架构审查报告（主报告 + AI Runtime + Workflow Runtime + 演进路线图）
- [.claude/plan/remediation-progress.md](./.claude/plan/remediation-progress.md) — 整改进度跟踪（✅/🚧/⏳）

## 部署

```bash
mvn clean package                # 后端 jar → target/KnowFlow-0.0.1-SNAPSHOT.jar
cd frontend && pnpm build        # 前端 dist
cd docs && docker-compose up -d  # 基础设施
```

`Dockerfile.frontend` 提供前端镜像。

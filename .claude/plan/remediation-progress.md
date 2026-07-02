# KfSmart 审查整改进度跟踪

> 基于 `.claude/plan/` 下四份 review（主报告 `review-report.md` + `ai-runtime-review.md` + `workflow-runtime-review.md` + `ai-platform-evolution-review.md`）。
> 状态图例：✅ 本次会话完成｜🚧 部分完成/有阻塞说明｜⏳ 后续会话执行。
> 后续会话认领时请将 ⏳ 改为 🚧，完成后改 ✅ 并补注依据文件。

---

## Phase 1 — 立即止血（主报告 §5 Phase 1）

| # | 任务 | Issue | 状态 | 依据/说明 |
|---|------|-------|------|-----------|
| 1.1 | 密钥外置到环境变量 | 1 Critical | ✅ | `application.yml`/`application-dev.yml`/`application-docker.yml`/`docs/docker-compose.yaml` 全部改 `${ENV:默认}`；新增 `.env.example`；`.gitignore` 加 `.env`。**待办：轮换已泄漏的 JWT 密钥与 docker MinIO 真实凭证（无法代为轮换）。** |
| 1.2 | Kafka `trusted.packages` 精确白名单 | 2 Critical | ✅ | 三份 yml 由 `"*"` 改为 `com.smart.kf.model,java.util,java.lang,java.math,java.time,java.io,java.net`（消息类型 `com.smart.kf.model.FileProcessingTask`）。 |
| 1.3 | Code 节点沙箱化 | 3 Critical | 🚧 | 原 `ScriptEngine.eval` 无沙箱 RCE 已移除，Code 节点默认禁用（`workflow.code-node.enabled=false`），RCE 风险已消除。GraalVM 真沙箱不在主工程直接引入（其 `module-info.class` 触发 JPMS 致 Lombok 处理器失效），**最终方案：Code Runner 独立执行器**，将 GraalVM 沙箱与主工程解耦。详见下文「Code 节点 GraalVM 沙箱化整改方案」。 |
| 1.4 | SQL 节点加固 | 4 Critical | ✅ | `SqlNodeExecutor`：`conn.setReadOnly(true)` + `setQueryTimeout`（`workflow.sql-node.query-timeout-seconds` 默认 10）+ 仅允许 SELECT/WITH + 拒绝多语句。独立只读 DataSource 深度防御已落地：新增 `ReadOnlyDataSourceProperties`（`workflow.sql-node.read-only.*`），`SqlNodeExecutor` 构造期按配置自建独立连接池（推荐只读账号），未配置回退主 DataSource。不注册额外 `DataSource` Bean，避免触发 `DataSourceAutoConfiguration` 的 `@ConditionalOnMissingBean` 抑制。 |
| 1.5 | 删除 `Thread.sleep(500)` | 7 High | ✅ | `ChatHandler.processMessage` 删除。 |
| 1.6 | `ddl-auto`/`show-sql`/日志降级 | 8/14 | ✅ | base `application.yml` `ddl-auto: validate`、`show-sql: false`、日志 `DEBUG→INFO`；dev 保留 `update`/`true`/`DEBUG` 便于本地；docker 降 INFO。 |
| 1.7 | `JwtUtils` 日志脱敏 | 9 High | ✅ | 5 处 `logger.error(...token...)` 改 `maskToken` 指纹；4 个时间 magic number 改 `Duration`；`getSigningKey` 兼容非 Base64 文本密钥。 |
| 1.8 | application.yml 三份去重 | — | ✅ | base 保持不变；dev/docker 仅保留差异覆盖项（datasource.url、jpa、file.parsing.chunk-size、elasticsearch password、ai.prompt、logging/log4j），公共段（kafka/minio/jwt/admin/servlet/webflux/embedding/ai.default 等）靠 Spring profile 继承。dev/docker 从 ~108/107 行降至 22/23 行，零行为变化（base 本就始终合并）。 |
| 1.9 | 删死代码 | — | ✅ | 删 `static/test.html`、`test.html`、`frontend/kf_wf_probe.mjs`；`SecurityConfig` 移除 `/test.html`、`/static/test.html`、`/api/v1/test/**` permitAll。 |
| 1.10 | 重写 `CLAUDE.md`/`README.md` | 13 | ✅ | 见 D1。 |

**Phase 1 补充快速修复（原 Phase 2 项，本次一并完成）：**

| # | 任务 | Issue | 状态 | 依据 |
|---|------|-------|------|------|
| 2.7 | HybridSearchService assert/NPE + 召回倍数配置化 | 19 | ✅ | 4 处 `assert`→显式判空 + `getTextContent()` 判空；`topK*30`→`@Value search.recall.multiplier`；rescore `0.2/1.0`→`@Value`。 |
| 2.8 | ReActEngine 错误友好化 + 计费常量提取 | 20 | ✅ | catch 返回友好文案 + `logger.error` 原始；新建 `client/TokenCost` 统一 `ReActEngine`/`ModelClient`/`AgentContext` 三处重复公式。 |
| — | ModelClient 单例 ObjectMapper + 去重 | 10 partial | ✅ | 注入 Spring 单例 `ObjectMapper` 替换 11 处 `new ObjectMapper()`；抽取 `ResolvedClient`+`resolveClientAndModel` 去重 4 处 client/model 解析。完整 `ProtocolHandler` 策略见 Phase 3。 |
| — | EmbeddingClient 日志/风格 | — | ✅ | `==false`→`!`；停止打印 key 前缀。 |
| 2.9 | 危险端点 adminKey 外置 + 审计 | 12 High | ✅ | `clearAllData`/`migrateMinioFiles` 的硬编码 `CLEAR_ALL_2024`/`migration2024` 改 `${admin.dangerous-operation.*-key}`（留空=禁用）+ `LogUtils.logBusiness` 安全审计。审计落库已落地：新增 `model/AdminOperationLog` + `AdminOperationLogRepository` + `service/AdminAuditService`，两个端点的 DENIED/SUCCESS/FAILED 三态写入 `admin_operation_logs` 表（schema.sql 已补建表，测试库已迁移）。审计失败吞异常不影响主流程。 |

---

## Phase 2 — 推荐优化（主报告 §5 Phase 2）

| # | 任务 | 状态 | 说明 |
|---|------|------|------|
| 2.1 | 拆分 `AdminController`(2202行/23依赖/34端点)→8 子控制器 | ✅ | 新增 `controller/admin/Admin{User,OrgTag,Role,Knowledge,Analytics,System,I18n,Migration}Controller.java`（8 个子控制器，全部构造器注入）+ 共享 `service/AdminAuthHelper`（`validateAdmin`/`resolveAdminUsername`）。原 `AdminController` 保留为空 stub。 |
| 2.2 | 抽取 `ChatSessionStateStore` 统一 7 个 Map | ✅ | 新增 `service/ChatSessionStateStore.java`：封装原 7 个 `ConcurrentHashMap`，暴露语义化方法（`initSession`/`markState`/`recordSubscription`/`cancelSubscription`/`registerFuture`/`consumeResponse`/`recordReference`/`getLock`/`markErrorSent`/统一 `clear`）；顺带修复原实现两处遗漏（`sessionErrorSent` 未清理、`responseFutures` 用 `get` 未 `remove`）。`ChatHandler` 改注入该 Store。 |
| 2.3 | 拆分 `ChatHandler`→`ConversationHistoryService`（保留委托层） | ✅ | 新增 `service/ConversationHistoryService.java`（构造注入 `RedisTemplate`+`ConversationService`+Spring 单例 `ObjectMapper`），承接全部会话 CRUD（ensureLegacyConversationIndex/getCurrentConversationId/isConversationOwnedByUser/getConversationMessages/getConversationSessions/createConversationSession/appendConversationTurn/appendConversationError/truncateConversationHistory/deleteConversationSession/updateConversationPinned 等）+ 全部 Redis 键构建/历史读写私有方法。`ChatHandler`（1262→~580 行）保留同名 public 方法作一行委托，`ConversationController`/`RuntimeService` 零改动；仅保留 RAG 编排、stream dispatch、`ChatSessionStateStore` 委托。测试从 `ChatHandlerTruncateTest`（spy ChatHandler）迁移为 `ConversationHistoryServiceTest`（32 个用例，直接测 `ConversationHistoryService`，覆盖 truncate/ownership/resolveConversationId/CRUD/legacy 迁移/会话排序）。**未采用四类拆分（ChatRagOrchestrator/ChatStreamDispatcher），按用户要求保留三步法（StateStore+HistoryService+委托 Facade）。** |
| 2.4 | `ModelClient` 抽 `ProtocolHandler` 策略 | ✅ | 新增 `client/protocol/`（`ModelProtocolHandler` 接口 + `OpenAiProtocolHandler`/`AnthropicProtocolHandler`），`ModelClient` 8 处 `"anthropic".equals(authType)` 分支收敛为 `handlerFor(authType)` 单一分发点。协议差异（鉴权头/endpoint 后缀/请求体/文本与 FunctionCall 解析/流式订阅+错误体+chunk 处理）全部下沉到 handler；`ModelClient` 保留协议无关的共享逻辑（密钥模型解析、消息组装、URI 解析、非流式错误体解析、单例 ObjectMapper）。 |
| 2.5 | DTO 层 + MapStruct + SpringDoc OpenAPI（按需） | ⏳ | 无 dto/vo 包、pom 无 mapstruct/springdoc。优先覆盖对外 API、含密码哈希接口、需字段翻译接口；内部 CRUD 可保留 Map/Entity。 |
| 2.6 | Controller 集成测试 `@WebMvcTest` | ✅ | 新增 `AdminUserControllerTest`（覆盖 2.1 拆分后的子控制器）+ `DocumentControllerTest`（覆盖 DELETE `/{md5}`、GET `/uploads` 含 keyword 搜索分支）。均 `@WebMvcTest`+`@Import(SecurityConfig.class)`+`@MockBean` 依赖，`JwtAuthenticationFilter`/`OrgTagAuthorizationFilter` mock 直通。 |
| 2.10 | Flyway 数据库迁移 | 🗑️ 已废弃 | 经评估后放弃接入：现有 `ddl-auto: validate`（base/docker）已能阻止生产环境意外 schema 漂移，`docs/databases/schema.sql` + `migrations/` 手工脚本已覆盖建表需求；引入 Flyway 需承担迁移脚本纪律成本（baseline 对齐、版本号管理、CI 中迁移执行）而当前团队规模/发布频率下收益有限。**不再排期，如未来多环境并行发布或团队扩张导致手工 SQL 漂移频发，可重新评估。** |
| 2.11 | 前端 Token→HttpOnly Cookie 或强化 CSP | ⏳ | `frontend/src/stores/auth.ts` 存 localStorage。本次会话已完成方案设计与前期代码调研（详见下方会话记录），**实现未执行**，留待下一会话按既定方案落地。 |
| 2.12 | 后端 Checkstyle + SpotBugs + Sonar 质量门禁 | ⏳ | — |

---

## Phase 3 — 架构升级（主报告 §5 Phase 3 / AI Runtime R1-R4 / Workflow W1-W4）

### AI Runtime（`ai-runtime-review.md` §11）

| 任务 | 状态 | 说明 |
|------|------|------|
| R1 `RuntimeContext` 统一 `AgentContext`/`ExecutionContext`/Chat 散落 Map | ⏳ | 三类执行体入口签名不兼容。 |
| R1 `RuntimeEvent`+`EventBus` 替换 `progressListener` | ⏳ | 当前无 Event 抽象；Chat 用 WebSocket 文本帧、Agent `progressListener=null`、Workflow 用 JSON 帧。 |
| R1 `RuntimeSession` 让 Agent/Workflow 复用 Chat Session | ⏳ | `RuntimeService` 已委托 `ChatHandler` 会话方法，但 Trace/Token 未写入会话。 |
| R1 `TraceSpan` 统一 `AgentStep`/`NodeTrace` | ⏳ | 扁平 List，无父子层级。 |
| R1 Workflow async 改 `ExecutorService` 替换裸 `new Thread`+`java.util.Timer` | ⏳ | `WorkflowExecutionService.executeAsync` L95 裸 `new Thread`，`finally` 裸 `new Timer().schedule(30000)`。 |
| R2 `Memory` 接口（Buffer/TokenWindow/Summary/VectorRetriever） | ⏳ | 无 Memory 抽象，历史全量存 Redis JSON；长会话 token 超限。 |
| R2 `RuntimeStateMachine`（Created→Running→Paused→Resumed→Completed/Failed/Cancelled） | ⏳ | 三类执行体无 Pause/Resume/Retry/Cancel。 |
| R2 Workflow Checkpoint 持久化 + 断点续跑 | ⏳ | `executed` 集合内存态，无 Checkpoint 实体。 |
| R2 Chat 增加 Trace（runtime Span + retrieval Span + llm_call Span） | ⏳ | Chat 无 Trace。 |
| R3 `PromptPipeline`+`PromptAssembler`+`PromptValidator` | ⏳ | Prompt 组装硬编码 `ModelClient.buildMessages`。 |
| R3 LLM 节点接入 `PromptTemplate` 版本管理 | ⏳ | — |
| R3 Agent/Workflow LLM 节点流式输出 | ⏳ | Agent `chatWithFunctions` 同步 `.block()`。 |
| R3 `OutputGuardrail`（敏感词+Prompt Injection） | ⏳ | — |
| R3 变量作用域（Scope）+ 类型系统 | ⏳ | 见 Workflow W1。 |
| R4 `AiRuntime` 统一接口 | ⏳ | — |
| R4 事件持久化 + Replay | ⏳ | — |
| R4 Multi-Agent 协作 / Plan-and-Execute / 自治长程 | ⏳ | — |

### Workflow Runtime（`workflow-runtime-review.md` §11）

| 任务 | 状态 | 说明 |
|------|------|------|
| W1 Variable Scope 分层（Global/Node-local/Loop-iteration） | ⏳ | 当前单层 `ConcurrentHashMap`；`setNodeOutput` 用 `!containsKey` 避免冲突致冲突时丢失。 |
| W1 Variable Type 系统（String/Number/Boolean/List/Map） | ⏳ | 全 `Object`，模板 `String.valueOf` 强转。 |
| W1 Variable 校验（strict + 必填 + 默认值） | ⏳ | `{{xxx}}` 未定义返回空串。 |
| W1 `RetryPolicy` + 指数退避 + 节点超时 | ⏳ | `retryCount` 字段是装饰性，后端不读取；节点无超时。 |
| W2 并行扇出 `CompletableFuture`+有界线程池+`maxParallelism` | ⏳ | `executeFromNode` `ArrayDeque`+单线程 while，声明并行实际串行。 |
| W2 `Checkpoint` 持久化（JdbcCheckpointStore）+ 断点续跑 | ⏳ | — |
| W2 并行结果聚合 | ⏳ | 扇入仅检查 `executed.contains`，不收集前驱输出。 |
| W3 子图（Sub-graph）+ 可重入执行 | ⏳ | `executed` 集合阻止二次执行 Loop 节点。 |
| W3 循环节点 + 子图循环 | ⏳ | `LoopNodeExecutor` 设循环变量后无"回到 Loop 节点"机制。 |
| W3 `CompensatableNodeExecutor` 补偿 | ⏳ | 节点失败=Workflow 失败，无回滚。 |
| W3 失败策略（skip/retry/fallback/continue） | ⏳ | 当前 fail-fast。 |
| W3 `SwitchNodeExecutor` + 严格路由匹配 | ⏳ | `resolveNextNodes` fallback 到第一条出边不严谨。 |
| W4 `TraceSpan` 层级 + 子执行 Trace 合并 | ⏳ | `AgentCallNodeExecutor` 调 Agent 不合并 `AgentStep` 到 `NodeTrace`。 |
| W4 变量变化历史（每步快照） | ⏳ | — |
| W4 可视化调试器 + 历史回放 | ⏳ | — |
| — | `NodeExecutor` 类型枚举化 + 严格匹配 | ⏳ | `NodeExecutorRegistry` 模糊 `contains` 匹配、21 执行器全中文字符串、无枚举。 |
| — | `ToolProvider` 插件化 `ToolRegistry` | ⏳ | `resolveTools` 硬编码 if 分支（KB+MCP）。 |
| — | `RuntimeSpi` 统一 | ⏳ | `RuntimeService.executeByType` if/else 路由。 |
| — | `RetrieverSpi` 检索策略插件化 | ⏳ | `HybridSearchService` 单实现。 |

### 工程化（主报告 Phase 3）

| 任务 | 状态 | 说明 |
|------|------|------|
| 3.7 Micrometer + Prometheus + Grafana | ⏳ | 无监控指标。 |
| 3.8 OpenTelemetry 链路追踪 | ⏳ | 无 Trace。 |
| 3.9 `UserService`/`UploadService`/`DocumentController` 拆分 | ⏳ | `UserService` 1095 行、`DocumentController` 799 行（3 处重复 token 权限校验待抽 `FileAccessGuard`）。 |
| 3.10 向 Clean Architecture 演进：Engine 独立 module | ⏳ | — |

---

## Phase 4 — 长期规划（主报告 §5 Phase 4 / 演进 E1-E4）

| 任务 | 状态 |
|------|------|
| 4.1 模块化单体：agent/workflow/rag 独立 Spring Module | ⏳ |
| 4.2 不拆微服务（认同 Meta Review，模块化单体优先） | ⏳ |
| 4.3 向量库升级：ES KNN→Qdrant/Milvus | ⏳ |
| 4.4 AI Gateway 抽象（LLM/Embedding/Reranker/Guardrail） | ⏳ |
| 4.5 LLM 输出安全：Prompt Injection + 敏感词 + Output Guardrail | ⏳ |
| 4.6 多模态（图片/表格/公式） | ⏳ |
| 4.7 工作流可视化调试器 + 历史回放 | ⏳ |
| 4.8 Agent 长程记忆 + Plan-and-Execute / Tree of Thoughts | ⏳ |
| 4.9 多租户资源配额 + 计费 | ⏳ |
| 4.10 灾备高可用：ES 集群 / Kafka 多 broker / MinIO 分布式 | ⏳ |

---

## Code 节点 GraalVM 沙箱化整改方案

> 本节为最终整改方案（**待后续会话实施，本次仅记录不实现**）。

### 背景

为消除 Code 节点远程代码执行（RCE）风险，尝试引入 GraalVM Polyglot 官方真沙箱（`SandboxPolicy.UNTRUSTED`）。

引入依赖：

```xml
<dependency>
    <groupId>org.graalvm.polyglot</groupId>
    <artifactId>polyglot</artifactId>
    <version>24.2.1</version>
</dependency>
```

构建过程中发现，该依赖包含 `module-info.class`，导致 Maven 在单模块工程中启用 JPMS（Module Path），从而使 Lombok Annotation Processor 全部失效，表现为：

- `@Getter`
- `@Setter`
- `@Builder`
- `@Slf4j`
- `@RequiredArgsConstructor`

等注解均无法生成代码，项目无法正常编译。

### 已尝试方案

已验证以下方案均无法解决问题：

- 排除 `native-image` 相关依赖
- 显式配置 `annotationProcessorPaths`
- 升级 Lombok 至 `1.18.36`
- 尝试降级 GraalVM Polyglot 版本
- 调整 Maven Compiler Plugin 配置
- 调整 Compiler Args（classpath / module-path）

结论：上述方案均无法解决单模块 Maven 工程下 GraalVM Polyglot 与 Lombok Annotation Processor 的兼容性问题。

### 原因分析

问题并非业务代码导致，而是以下组件组合产生的兼容性限制：

- GraalVM Polyglot（JPMS Module）
- Maven 单模块构建
- Lombok Annotation Processor

由于 `polyglot` 引入了 `module-info.class`，Maven 编译阶段会切换至 Module Path，导致 Lombok 注解处理器无法正常工作。

该问题属于构建层面的兼容性问题，而非 GraalVM Sandbox API 本身的问题。

### 最终整改方案

不再在主工程直接引入 `org.graalvm.polyglot:polyglot`。

Code 节点采用独立执行器（Code Runner）架构，实现沙箱能力与主工程解耦。

```
Workflow Server
│
├── LLM Node
├── HTTP Node
├── Branch Node
├── Variable Node
└── Code Node
        │
        │ HTTP / RPC
        ▼
+-----------------------+
|     Code Runner       |
|-----------------------|
| GraalVM Polyglot      |
| SandboxPolicy         |
| HostAccess.NONE       |
| Timeout               |
| Memory Limit          |
| CPU Limit             |
+-----------------------+
        │
        ▼
Docker / Kubernetes（可选）
```

Workflow 主工程不再依赖 GraalVM Polyglot，因此：

- Lombok 保持正常工作
- 不受 JPMS 影响
- GraalVM 可独立升级
- Code Runner 可独立部署
- 支持后续扩展 JavaScript、Python、Ruby 等语言

### 过渡方案

在 Code Runner 完成之前：

- 默认禁用 Code 节点
- 前端隐藏 Code 节点入口
- 后端拒绝 Code 节点执行请求
- 返回统一错误码
- 消除潜在 RCE 风险

当前构建状态恢复正常，所有模块均可正常编译。

### 后续实施计划

**Phase 1：模块隔离**

新增独立 Maven Module：

```
workflow-parent
│
├── workflow-api
├── workflow-core
├── workflow-engine
├── workflow-code-runner
└── workflow-web
```

仅 `workflow-code-runner` 引入：

```xml
<dependency>
    <groupId>org.graalvm.polyglot</groupId>
    <artifactId>polyglot</artifactId>
    <version>24.2.1</version>
</dependency>
```

主工程完全不依赖 GraalVM。

**Phase 2：独立执行器**

Code Runner 提供独立接口：

- HTTP API 或 gRPC
- Timeout 控制
- Memory Limit
- CPU Limit
- 并发限制
- HostAccess.NONE
- 禁止文件系统访问
- 禁止网络访问
- 禁止 Native Access

Workflow Engine 通过 RPC 调用 Code Runner 完成代码执行。

**Phase 3：生产级沙箱**

进一步增强隔离能力：

- Docker 容器隔离
- Rootless Container
- seccomp
- AppArmor
- cgroups（CPU / Memory）
- Kubernetes Resource Limit
- 审计日志
- 执行超时自动终止

形成生产级安全沙箱。

### 当前状态

| 项目 | 状态 |
|------|------|
| GraalVM 真沙箱 | 暂缓 |
| Lombok 构建 | ✅ 正常 |
| Maven 构建 | ✅ 正常 |
| Code 节点 | ✅ 默认禁用 |
| RCE 风险 | ✅ 已消除 |
| 后续整改 | 独立 Code Runner 实施 |

### 结论

继续在单模块 Maven 工程内兼容 GraalVM Polyglot（JPMS）与 Lombok Annotation Processor 的投入产出比较低，且存在较大的维护成本。

综合安全性、可维护性与后续扩展能力，最终采用 **Code Runner 独立执行器** 方案，将 GraalVM 沙箱与主工程解耦，是当前最稳妥且符合生产实践的整改路径。

---

## 验证结果（本次会话）

- `mvn -DskipTests clean compile` → BUILD SUCCESS ✅
- `mvn -q test`（纯单测子集）→ 210 跑，0 失败，1 错误（`UploadServicePerformanceTest` 的 `@SpringBootTest` 上下文加载失败，需 MySQL/Redis/ES 基础设施，预先存在，非本次回归）。
- `mvn -q -DskipTests package` → `target/KnowFlow-0.0.1-SNAPSHOT.jar`（160M）✅
- grep 复核：无 `Thread.sleep(500)`、`ModelClient` 无 `new ObjectMapper()`（仅注释）、yml 无 `trusted.packages: "*"`、无硬编码 JWT 密钥、`CodeNodeExecutor` 无 `ScriptEngine` 代码、`JwtUtils` 无 token 日志、死代码已删、`AdminController` 无硬编码密钥。

## 附：本次会话外发现并修复的预先存在问题

- `src/test/java/.../WorkflowServiceTest.java` 测试编译错误（`getWorkflow` 返回 `Map` 非测试期望的 `Workflow`，签名漂移）+ 测试 ObjectMapper 未注册 JSR310（`LocalDateTime` 序列化失败）。本次最小修复以解除测试套件阻塞，非 review 范围。

---

## 后续会话执行记录（Phase 1/2 收尾项）

推进任务：1.8（yml 去重）、1.4 待办（只读 DataSource）、2.9 待办（审计落库）、2.4（ProtocolHandler 策略）。

### 改动清单

- **1.8 配置去重**：`application-dev.yml`/`application-docker.yml` 仅保留 profile 差异覆盖项，公共段靠 Spring profile 继承自 `application.yml`（base 不变）。零行为变化（base 本就始终合并）。
- **1.4 只读 DataSource**：新增 `config/ReadOnlyDataSourceProperties`（`workflow.sql-node.read-only.*`）；`SqlNodeExecutor` 构造期按配置自建独立只读连接池，未配置回退主 DataSource。**不注册额外 `DataSource` Bean**，避免触发 `DataSourceAutoConfiguration` 的 `@ConditionalOnMissingBean` 抑制而破坏主库装配与 JPA。`.env.example` 补充可选只读账号环境变量。
- **2.9 审计落库**：新增 `model/AdminOperationLog` + `repository/AdminOperationLogRepository` + `service/AdminAuditService`；`AdminController` 的 `clearAllData`/`migrateMinioFiles` 在 DENIED/SUCCESS/FAILED 三态写入 `admin_operation_logs` 表（`docs/databases/schema.sql` 已补建表）。审计失败吞异常，不影响主流程。新增 `extractAdminUserId` 从 token 解析管理员 ID。
- **2.4 ProtocolHandler**：新增 `client/protocol/`（接口 + OpenAI/Anthropic 两实现）；`ModelClient` 8 处 `"anthropic".equals` 分支收敛为 `handlerFor(authType)` 单一分发点。协议差异（鉴权头/endpoint/请求体/文本与 FunctionCall 解析/流式订阅与 chunk 处理）下沉到 handler；共享逻辑（密钥模型解析、消息组装、URI 解析、非流式错误体解析、单例 ObjectMapper）留 `ModelClient`。`ToolCall`/`FunctionCallResult` record 位置不变，`ReActEngine` 调用方无需改动。
- **附带**：`.env.example` 修正 `WORKFLOW_CODE_NODE_ENABLED=true`→`false`（沙箱未接入前开启属误导）。

### 验证结果

- `mvn -DskipTests clean compile` → BUILD SUCCESS ✅（212 源文件）。
- `mvn test` → 235 跑，0 失败，0 错误 ✅。
- **测试库迁移**：新增 `AdminOperationLog` 实体在 `ddl-auto=validate` 下要求 `admin_operation_logs` 表存在，运行中的测试 MySQL 缺该表导致 `@SpringBootTest` schema 校验失败；已通过 JDBC 脚本对 localhost:3306/knowflow 补建该表（`CREATE TABLE IF NOT EXISTS`）。生产/其他环境部署时须执行 `docs/databases/schema.sql` 或等价迁移。
- grep 复核：`ModelClient` 仅 `handlerFor` 处 1 个 `"anthropic".equals`（分发点）；无 `new ObjectMapper()`（仅注释）；旧 `ReadOnlyDataSourceConfig` 已删除。

---

## 后续会话执行记录（2.1/2.2/2.3/2.6 收尾）

推进任务：2.6（Controller 测试安全网）→ 2.1（AdminController 拆分）→ 2.2+2.3（ChatHandler 三步拆分）。2.10（Flyway）/2.11（Cookie 双模式）留待下一会话。

### 改动清单

- **2.6**：新增 `AdminUserControllerTest`、`DocumentControllerTest`（`@WebMvcTest`+`@Import(SecurityConfig.class)`）。
- **2.1**：`AdminController` 2202 行/34 端点拆分为 8 个 `controller/admin/Admin*Controller.java`，共享逻辑抽 `service/AdminAuthHelper`；原类降级为空 stub。
- **2.2**：新增 `service/ChatSessionStateStore.java`，`ChatHandler` 7 个裸 Map 改为语义化方法调用，修复 `sessionErrorSent`/`responseFutures` 两处清理遗漏。
- **2.3**：新增 `service/ConversationHistoryService.java` 承接全部会话 CRUD 与 Redis 读写逻辑（~490 行）；`ChatHandler` 精简至 ~580 行，保留同名委托方法确保外部调用零改动。新增 `ConversationHistoryServiceTest`（32 用例）替代旧 `ChatHandlerTruncateTest`（10 用例，spy 目标已随逻辑迁移改为直测 `ConversationHistoryService`）。

### 验证结果

- `mvn -q clean test-compile` → BUILD SUCCESS（修复过程中发现一次增量编译缓存假阳性：`test-compile` 未 `clean` 时曾对已失效的 8 参构造器调用静默放行，`clean test-compile` 后才暴露真实编译错误，已修正）。
- `mvn test` → 267 跑，0 失败，8 错误（`SmartKfApplicationTests.contextLoads`、`ParseServiceTest`×6、`UploadServicePerformanceTest`，均为 `@SpringBootTest` 依赖 MySQL/ES 基础设施未启动导致的上下文加载失败，非本次改动引入的回归；测试数从 245→267 净增 22 个，对应新增 `ConversationHistoryServiceTest` 32 个减去移除的 `ChatHandlerTruncateTest` 10 个）。


# AI Runtime Architecture Review

> **Project**: KfSmart AI Platform
> **Report Type**: Specialized AI Runtime Architecture Review（AI 运行时架构专项评审）
> **Review Date**: 2026-06-30
> **Reviewer Role**: Principal Engineer / AI Platform Architect
> **Companion Report**: 本报告是 `docs/review-report.md` 的补充专项报告，针对 Meta Review 指出的"Runtime / Execution / Variable / Context / Prompt / Trace / Event 分析不足"进行深化。

---

## 0. Executive Summary

本报告专门评审 KfSmart 的 **AI Runtime 架构**——即支撑 Chat / Agent / Workflow 三类 AI 执行体的运行时抽象、生命周期、状态管理、上下文流转、事件流与可观测性。

### 0.1 核心发现

KfSmart 当前 **没有统一的 Runtime 抽象**。Chat / Agent / Workflow 三类执行各自独立实现：

| 执行体 | 运行时入口 | 上下文对象 | 状态管理 | 事件流 | Trace | 持久化 |
|--------|-----------|-----------|----------|--------|-------|--------|
| **Chat** | `ChatHandler.processMessage` | 无显式 Context（6 个散落 Map） | `ConcurrentHashMap` 内存 | WebSocket 文本帧 | 无 | Redis 会话历史 |
| **Agent** | `ReActEngine.execute` | `AgentContext` | Context 内部 List | 无（同步返回） | `AgentStep` 列表 | `AgentExecutionLog` |
| **Workflow** | `WorkflowExecutionEngine.execute` | `ExecutionContext` | Context 内部 Map | WebSocket 节点帧 | `NodeTrace` 列表 | `WorkflowExecutionLog` |

三类执行体的 **上下文对象不兼容**（`AgentContext` ≠ `ExecutionContext` ≠ Chat 的散落 Map），**生命周期不统一**（Chat 无显式状态机、Agent 同步阻塞、Workflow 有 async 但用裸 `new Thread`），**事件流不统一**（Chat 用 WebSocket 文本、Agent 无事件、Workflow 用自定义 JSON 帧），**Trace 不互操作**（`AgentStep` ≠ `NodeTrace`）。

这意味着：
- **无法跨执行体复用**：Chat 的流式分发、Agent 的 ReAct 循环、Workflow 的 DAG 调度各自为政，无法组合（如"Chat 中调用 Agent"、"Workflow 中调用 Chat 流式"需手工拼接）。
- **可观测性割裂**：三类执行体的 Trace 无法统一查询、统一回放、统一计费。
- **演进受限**：新增执行体（如 Multi-Agent 协作、Plan-and-Execute、自治长程任务）需从零设计 Runtime，无法复用基础设施。

### 0.2 总体评分

| 维度 | 评分 | 说明 |
|------|------|------|
| Runtime 抽象 | 40 | 三类执行体无统一 Runtime 接口 |
| 生命周期管理 | 45 | Chat 无状态机；Workflow async 用裸 Thread |
| Session 管理 | 55 | Chat 会话存 Redis；Agent/Workflow 无 Session 概念 |
| Context 流转 | 50 | `AgentContext` 与 `ExecutionContext` 不互操作 |
| Memory 抽象 | 30 | 无 Memory 接口，对话历史直接存 Redis JSON |
| Event 架构 | 35 | 无 Event Bus，事件硬编码 WebSocket 帧 |
| Trace 体系 | 55 | 有 Trace 但三类不互操作 |
| Streaming 体系 | 60 | Chat 流式专业；Agent/Workflow 无流式 |
| Prompt 生命周期 | 50 | 无统一 Prompt 管道，组装散落 ModelClient / LlmNodeExecutor |
| 综合评分 | **46 / 100（D+）** | AI Runtime 是项目最大短板 |

### 0.3 核心建议

1. **建立统一 Runtime 接口**：`AiRuntime<TInput, TOutput>` + `RuntimeContext` + `RuntimeSession` + `RuntimeEvent`，三类执行体实现该接口。
2. **建立 Runtime 生命周期状态机**：`Created → Running → Paused → Resumed → Completed/Failed/Cancelled`，支持 Pause/Resume/Retry/Cancel。
3. **建立统一 Memory 抽象**：`Memory` 接口 + `BufferMemory` / `SummaryMemory` / `VectorRetrieverMemory` 实现。
4. **建立 Event Bus**：`RuntimeEvent` + `EventPublisher` + `EventSubscriber`，统一 Chat/Agent/Workflow 的事件流。
5. **建立统一 Trace 模型**：`Trace` + `TraceSpan`，三类执行体共用，支持端到端回放。

---

## 1. Runtime 抽象分析

### 1.1 现状：三类独立 Runtime

#### 1.1.1 Chat Runtime

**入口**：`ChatHandler.processMessage(userId, userMessage, conversationId, apiKeyConfigId, session)`（`src/main/java/com/smart/kf/service/ChatHandler.java` L105）

**执行模型**：WebSocket 同步阻塞 + Reactor 流式订阅

**核心组件**：
- `ChatHandler`：编排 RAG 检索 → Prompt 组装 → LLM 流式调用 → WebSocket 分发
- 6 个 `ConcurrentHashMap`：`responseBuilders` / `responseFutures` / `streamTerminalStates` / `streamSubscriptions` / `sessionReferenceMappings` / `sessionSendLocks` / `sessionErrorSent`（L67-79）
- `RedisTemplate`：会话历史与元数据

**状态管理**：散落在 6 个 Map 中，以 `sessionId`（WebSocket session id）为 key

**事件流**：直接 `session.sendMessage(new TextMessage(...))`，无事件抽象

**Trace**：无（仅 `logger.info` 日志）

#### 1.1.2 Agent Runtime

**入口**：`ReActEngine.execute(agent, userInput, history, debugOverrides, progressListener)`（`src/main/java/com/smart/kf/agent/engine/ReActEngine.java` L39）

**执行模型**：同步阻塞循环（`for (int i = 0; i < maxIterations; i++)`）

**核心组件**：
- `AgentContext`：封装 `executionId` / `agentId` / `username` / `messages` / `steps` / `tokenUsage` / `finalAnswer`（`AgentContext.java`）
- `ReActEngine`：循环调用 `modelClient.chatWithFunctions` → 解析 `tool_calls` → `ToolRegistry.executeTool` → 注入 observation → 再 LLM
- `AgentStep`：每步记录 `thought` / `action` / `actionInput` / `observation` / `durationMs` / `status`

**状态管理**：全部在 `AgentContext` 内存对象中（无外部 Session 概念）

**事件流**：`progressListener: Consumer<AgentStep>` 回调（L44），无 Event 抽象

**Trace**：`AgentContext.steps: List<AgentStep>`（L19），序列化为 `traceJson` 存入 `AgentExecutionLog`

#### 1.1.3 Workflow Runtime

**入口**：`WorkflowExecutionEngine.execute(nodesJson, edgesJson, workflowId, username, input, progressListener)`（`src/main/java/com/smart/kf/workflow/engine/WorkflowExecutionEngine.java` L56）

**执行模型**：BFS 队列遍历 DAG + 扇入等待

**核心组件**：
- `ExecutionContext`：封装 `executionId` / `workflowId` / `username` / `variables` / `nodeOutputs` / `trace` / `tokenUsage` / `progressListener`（`ExecutionContext.java`）
- `WorkflowExecutionEngine`：`executeFromNode` BFS + `isReadyToExecute` 扇入等待 + `resolveNextNodes` 路由
- `NodeTrace`：每节点记录 `nodeId` / `nodeName` / `nodeType` / `startedAt` / `durationMs` / `inputs` / `outputs` / `promptTokens` / `completionTokens` / `status` / `errorMessage`
- `NodeExecutorRegistry`：20 种 `NodeExecutor` 自动注册

**状态管理**：`ExecutionContext` 内存对象 + `ConcurrentHashMap.newKeySet()` 的 `executed` 集合

**事件流**：`progressListener: Consumer<NodeTrace>` 回调 → `WorkflowExecutionService` 异步包装为 JSON 帧 → `WorkflowProgressBroadcaster` WebSocket 广播

**Trace**：`ExecutionContext.trace: List<NodeTrace>`，序列化存入 `WorkflowExecutionLog.traceJson`

### 1.2 问题分析

#### 1.2.1 无统一 Runtime 接口（Critical）

三类执行体的入口签名完全不同：

```java
// Chat
void processMessage(String userId, String userMessage, String conversationId,
                    Long apiKeyConfigId, WebSocketSession session);

// Agent
AgentContext execute(Agent agent, String userInput, List<Map<String,String>> history,
                     Map<String,Object> debugOverrides, Consumer<AgentStep> progressListener);

// Workflow
ExecutionContext.ExecutionResult execute(String nodesJson, String edgesJson,
                     String workflowId, String username, Map<String,Object> input,
                     Consumer<NodeTrace> progressListener);
```

**后果**：
- `RuntimeService.execute`（`RuntimeService.java` L112）需要 `if/else` 分支路由到 `agentExecutionService.chat` 或 `workflowService.debugWorkflow`，新增执行体需改 `RuntimeService`。
- `AgentCallNodeExecutor`（`AgentCallNodeExecutor.java`）调用 `agentExecutionService.chat(agentId, query)` 时丢失了 Workflow 的 `ExecutionContext`，无法把 Workflow 变量注入 Agent、也无法把 Agent 的 Trace 合并回 Workflow Trace。
- 无法实现"Chat 中流式调用 Agent"或"Workflow 中流式调用 Chat"。

#### 1.2.2 上下文对象不互操作（High）

| Context 对象 | variables | messages | trace | tokenUsage | progressListener |
|--------------|-----------|----------|-------|------------|------------------|
| `AgentContext` | ❌ 无 variables 概念 | ✅ `messages: List<Map>` | ✅ `steps: List<AgentStep>` | ✅ `TokenUsage` | ✅ `Consumer<AgentStep>` |
| `ExecutionContext` | ✅ `ConcurrentHashMap` | ❌ 仅 `history` 变量 | ✅ `trace: List<NodeTrace>` | ✅ `TokenUsageTracker` | ✅ `Consumer<NodeTrace>` |
| Chat 散落 Map | ❌ | ❌（历史在 Redis） | ❌ | ❌ | ❌ |

**后果**：
- `AgentCallNodeExecutor` 调用 Agent 时，Workflow 的 `ExecutionContext` 变量无法透传给 Agent（Agent 只接收 `query` 字符串，丢失上下文）。
- Agent 的 `AgentStep` Trace 无法合并到 Workflow 的 `NodeTrace` Trace（类型不兼容）。
- Token 计费分散在 `AgentContext.TokenUsage` 与 `ExecutionContext.TokenUsageTracker` 两套实现，无法汇总。

#### 1.2.3 RuntimeService 是适配器而非抽象（Medium）

`RuntimeService`（`RuntimeService.java`）本意是统一入口，但实际是**适配器**——把 Agent/Workflow 的不同调用方式包装成一致的 `execute(username, request)` 接口。它没有定义 Runtime 抽象，只是 if/else 路由。

**后果**：新增执行体（如 Multi-Agent、Plan-and-Execute）需修改 `RuntimeService.executeByType` 加 if 分支，违反 OCP。

### 1.3 优秀设计

尽管无统一 Runtime，但三类执行体的**内部**设计有可借鉴之处：

1. **`ExecutionContext` 设计较好**：变量管理 + 节点输出 + Trace + Token 计费 + 进度回调一体化，是统一 Runtime Context 的良好雏形。
2. **`AgentContext.TokenUsage` 用 `BigDecimal`**：比 `ExecutionContext.TokenUsageTracker` 的 `double` 更精确，适合计费场景。
3. **`WorkflowProgressBroadcaster` 的消息缓冲回放机制**（`WorkflowProgressBroadcaster.java` L37-44）：解决"执行先于订阅"的竞态，是专业设计。

---

## 2. Runtime 生命周期分析

### 2.1 现状

| 执行体 | Create | Run | Pause | Resume | Retry | Cancel | Destroy |
|--------|--------|-----|-------|--------|-------|--------|---------|
| Chat | WebSocket 连接 | `processMessage` | ❌ | ❌ | ❌ | `stopStream`（L?） | WebSocket 关闭 |
| Agent | `AgentContext` 构造 | `ReActEngine.execute` 同步 | ❌ | ❌ | ❌ | ❌ | 返回即销毁 |
| Workflow sync | `ExecutionContext` 构造 | `executeSync` 同步 | ❌ | ❌ | ❌ | ❌ | 返回即销毁 |
| Workflow async | `executeAsync` 创建 log | 裸 `new Thread` | ❌ | ❌ | ❌ | ❌ | Thread 结束 |

**结论**：三类执行体均**无 Pause/Resume/Retry/Cancel** 的完整生命周期。Chat 有一个 `stopStream`（取消流式订阅）但无 Pause/Resume。Workflow async 用裸 `new Thread`（`WorkflowExecutionService.java` L95），无法管理线程、无超时、无取消。

### 2.2 问题分析

#### 2.2.1 无 Runtime 状态机（Critical）

一个完整的 AI Runtime 生命周期应为：

```
Created → Scheduled → Running → (Paused ↔ Resumed) → Completed
                                      ↓
                                  Failed / Cancelled / TimedOut
```

当前实现：
- Chat：`StreamTerminalState` 枚举（`ACTIVE` / `COMPLETED` / `STOPPED` / `FAILED`，`ChatHandler.java` L51-56）仅覆盖流式终态，无 `Paused`。
- Agent：无状态枚举，`ReActEngine.execute` 同步返回即结束。
- Workflow：`WorkflowExecutionLog.status` 字段（`running` / `success` / `failed`）仅记录终态，无运行中状态转换。

**后果**：
- 长程 Workflow（如 30 分钟的文档批处理）无法 Pause/Resume，进程重启即丢失。
- Agent ReAct 循环无法 Cancel（用户想中断只能等 `maxIterations` 或超时）。
- Chat 流式无法 Pause（用户切后台再切回，流式已断）。

#### 2.2.2 Workflow async 用裸 Thread（High）

`WorkflowExecutionService.executeAsync`（L95）：
```java
new Thread(() -> {
    try {
        ExecutionContext.ExecutionResult result = executionEngine.execute(...);
        ...
    } catch (Exception e) { ... }
    finally {
        new java.util.Timer().schedule(..., 30000);  // 裸 Timer 清理
    }
}).start();
```

**问题**：
- 裸 `new Thread` 不受线程池管理，高并发下线程数无限增长。
- 无超时控制，恶意 Workflow 可永久阻塞线程。
- 无取消机制，`Thread.interrupt()` 未被引擎响应。
- 用 `java.util.Timer` 延迟清理（L132），Timer 也是裸创建，且 Timer 单线程可能成为瓶颈。
- 异常未上报到监控（仅 `logger.error`）。

#### 2.2.3 无 Checkpoint / 断点续跑（High）

`WorkflowExecutionLog` 仅在执行结束后记录结果（`saveExecutionLog` / `updateExecutionLog`），执行过程中无 Checkpoint。`executed` 集合（`WorkflowExecutionEngine.java` L106）是内存 `ConcurrentHashMap.newKeySet()`，进程崩溃即丢失。

**后果**：
- 长程 Workflow 失败需从头跑（如 100 节点跑到第 99 个失败，前 98 个白跑）。
- 无法实现"暂停后继续"（Pause/Resume 的前提是 Checkpoint）。

#### 2.2.4 Chat 会话 TTL 7 天但无主动清理（Medium）

`ChatHandler.CONVERSATION_TTL = Duration.ofDays(7)`（L46），Redis 会话 7 天过期。但 6 个内存 Map 的清理依赖 WebSocket `afterConnectionClosed` 钩子，若该钩子未触发（如服务器崩溃），Map 残留导致内存泄漏。

---

## 3. Session 管理

### 3.1 现状

| 执行体 | Session 概念 | Session 存储 | Session 与执行的关系 |
|--------|-------------|-------------|---------------------|
| Chat | ✅ `conversationId` | Redis（`conversationHistoryKey` / `conversationMetaKey` / `userConversationIdsKey`） | 1 Session : N 次执行 |
| Agent | ❌ 无 | 无 | 1 次执行 = 1 个 `AgentContext` |
| Workflow | ⚠️ `executionId` 但仅 log | `WorkflowExecutionLog` 表 | 1 次执行 = 1 个 `executionId` |

### 3.2 Chat Session 设计（较好）

Chat 的 Session 设计是三类中最好的：

- **Session 标识**：`conversationId`（UUID 生成，`resolveConversationId` L489）
- **Session 元数据**：`sessionType` / `targetType` / `targetId` / `targetName` / `isPinned` / `pinnedAt`（存 Redis `conversationMetaKey`）
- **Session 历史**：`List<Map<String,String>>` messages（存 Redis `conversationHistoryKey`，JSON 序列化）
- **Session 归属**：`userConversationIdsKey` 维护用户的所有 conversationId 列表
- **Session 当前**：`userCurrentConversationKey` 维护用户当前活跃 conversationId
- **Session Scope**：`CHAT_SESSION_SCOPE` / `runtime` / 自定义 scope（`sessionScope(metadata)`）
- **Session 操作**：create / get / list / delete / truncate / pin / append turn / append error

**优秀点**：
- 多 scope 支持（Chat / Runtime / Agent / Workflow 可共享 Session 机制）
- `attachConversationToUser` / `isConversationOwnedByUser` 实现权限校验
- `ensureLegacyConversationIndex` 处理历史数据迁移

**不足**：
- Session 与执行紧耦合在 `ChatHandler`（而非独立 `SessionService`）
- Agent/Workflow 未复用此 Session 机制

### 3.3 RuntimeService 对 Session 的统一尝试（部分成功）

`RuntimeService.execute`（L71-110）尝试统一 Session：
- 复用 `chatHandler.createConversationSession` 创建 Runtime 会话（L86）
- `sessionType="runtime"` + `targetType` + `targetId` 绑定到具体 Agent/Workflow（L182-189）
- `validateSessionBinding` 校验会话与运行对象匹配（L192-200）

**优点**：Agent/Workflow 的执行结果通过 `chatHandler.appendConversationTurn` 写入会话历史，前端可在统一会话视图查看。

**不足**：
- Agent/Workflow 内部的 `AgentContext` / `ExecutionContext` 与 `conversationId` 脱节——执行过程中产生的 Trace、Token、Step 未写入会话，仅最终 `answer` 写入。
- 若用户在 Runtime 会话中连续提问，第二次提问的 `history` 仅包含上次的 `answer`，丢失了上次的 Trace/Token 信息。

---

## 4. Context 流转分析

### 4.1 Chat 的 Context 流转

```
用户消息 → ChatHandler.processMessage
  → getConversationHistory(conversationId)  [Redis 读取]
  → HybridSearchService.searchWithPermission  [RAG 检索]
  → buildContext(searchResults, sessionId)  [构建 RAG context]
  → resolveApiKeyConfig(apiKeyConfigId)  [选择 API Key]
  → ModelClient.streamResponse(userMessage, context, history, apiKeyConfig, ...)
    → buildMessages  [组装 system + history + user]
    → WebClient.post  [调用 LLM]
    → processChunk  [流式解析]
  → sendResponseChunk  [WebSocket 推送]
  → finalizeStreamResponse  [保存历史]
```

**Context 组成**：
- `userMessage`：当前问题
- `context`：RAG 检索结果（top 3 文档片段）
- `history`：对话历史（`List<Map<String,String>>`）
- `apiKeyConfig`：API Key 配置

**问题**：
- Context 组装逻辑硬编码在 `ModelClient.buildMessages`（`ModelClient.java` L596-644），无法扩展（如注入 Memory、Tool 描述、Agent 人设）。
- `history` 全量加载，无 token 截断 / 摘要压缩。
- RAG context 与 system prompt 的拼接方式固定（`<<REF>>...<<END>>`），无法按 Agent 自定义。

### 4.2 Agent 的 Context 流转

```
ReActEngine.execute(agent, userInput, history, debugOverrides, progressListener)
  → ToolRegistry.resolveTools(agent)  [解析工具]
  → resolveModelConfig(agent, debugOverrides)  [选择模型]
  → resolveSystemPrompt(agent, debugOverrides)  [选择 system prompt]
  → for iteration:
    → buildUserMessage(ctx, agent, debugOverrides)  [组装 user prompt]
    → ModelClient.chatWithFunctions(userMessage, filterHistory(ctx.getMessages()),
                                     resolvedConfig, systemPrompt, openAiTools)
    → if tool_calls:
      → ToolRegistry.executeTool(toolName, toolArgs, tools)
      → ctx.addToolResultMessage(toolName, result)
    → else:
      → ctx.setFinalAnswer(content)
      → break
```

**Context 组成**：
- `agent.systemPrompt` / `agent.userPrompt`：Agent 配置的 Prompt 模板
- `ctx.messages`：对话历史（含 tool results）
- `systemPrompt`：解析后的 system prompt
- `openAiTools`：工具描述（OpenAI function-calling 格式）
- `debugOverrides`：即时调试覆盖（systemPrompt / models / maxIterations / userPrompt）

**优秀点**：
- `debugOverrides` 支持"即时调试"——前端可临时覆盖 Agent 配置进行测试，不落库。这是 AI 平台的专业设计。
- `buildUserMessage` 支持 `{{query}}` / `{{input}}` 模板变量，Agent 可自定义 user prompt 模板。

**问题**：
- Agent 的 `history` 仅来自 `RuntimeService` 从 `chatHandler.getConversationMessages` 提取的 `role/content`（`RuntimeService.java` L114-121），丢失了 Tool 调用历史（`role=tool` 的消息被 filter 掉）。
- `filterHistory`（`ReActEngine.java` L158-161）截取 `messages.subList(0, size-1)`，但未做 token 截断，长会话会超限。
- Agent 无 Memory 抽象，无法做 Summary / Vector 检索。

### 4.3 Workflow 的 Context 流转

```
WorkflowExecutionEngine.execute(nodesJson, edgesJson, workflowId, username, input, progressListener)
  → GraphBuilder.build  [解析 DAG]
  → TopologicalSorter.sort  [环检测]
  → extractDebugOverrides(input)  [提取 debug_ 前缀变量]
  → ExecutionContext(workflowId, username, effectiveInput)  [创建上下文]
  → executeFromNode(startNode.id, graph, ctx, executed)
    → isReadyToExecute  [扇入等待]
    → resolveInputMappings(node, ctx)  [解析入参映射]
    → captureNodeInputs(ctx)  [快照]
    → NodeExecutor executor = registry.getExecutor(node.type())
    → executor.execute(node, ctx)  [执行节点]
    → ctx.setNodeOutput(currentId, outputs)  [存节点输出，自动注入变量]
    → resolveNextNodes  [路由下一节点]
```

**Context 组成**：
- `ctx.variables: ConcurrentHashMap`：全局变量
- `ctx.nodeOutputs: Map<String, Map<String,Object>>`：节点输出
- `ctx.trace: List<NodeTrace>`：执行轨迹
- `ctx.tokenUsage: TokenUsageTracker`：Token 计费
- `ctx.progressListener: Consumer<NodeTrace>`：进度回调

**优秀点**：
- `resolveInputMappings`（`WorkflowExecutionEngine.java` L334-361）支持节点级入参映射：`[{param:"prompt", source:"start.query", enabled:true}]`，前端可视化配置。
- `setNodeOutput`（`ExecutionContext.java` L88-95）自动把节点输出的 key 注入全局 variables（若未冲突），实现节点间数据流。
- `resolveTemplate`（`ExecutionContext.java` L126-139）支持 `{{query}}` / `{{input.query}}` / `{{nodeId.key}}` / `{{context}}` 多种语法。

**问题**：
- 变量全局共享，**无作用域**（Scope）——所有节点的输出都注入全局 variables，节点间变量名冲突时后者覆盖前者（`setNodeOutput` L91 用 `!variables.containsKey(key)` 避免覆盖，但 `setVariable` L70 直接覆盖）。
- 无变量类型系统——所有变量都是 `Object`，模板解析时 `String.valueOf(val)` 强转，数字 / 布尔 / 列表无法区分（`ConditionNodeExecutor.compareNumeric` 靠 `Double.parseDouble` 异常 fallback 到字符串比较，脆弱）。
- `history` 作为特殊变量传递（`extractDebugOverrides` 前从 input 移除再 put 回），但 Agent 调用时丢失（`AgentCallNodeExecutor` 只传 query）。

---

## 5. Memory 抽象分析

### 5.1 现状：无 Memory 抽象

KfSmart **没有 Memory 接口**。对话历史直接以 JSON 存 Redis：

```java
// ChatHandler.updateConversationHistory (推断)
String key = conversationHistoryKey(conversationId);  // conversation:history:{id}
String json = objectMapper.writeValueAsString(history);  // List<Map<String,String>>
redisTemplate.opsForValue().set(key, json, CONVERSATION_TTL);  // 7 天 TTL
```

Agent / Workflow 的 history 来自 `chatHandler.getConversationMessages` → `List<Map<String,String>>`，全量加载。

### 5.2 问题

#### 5.2.1 无 Memory 接口（High）

主流 AI Framework（LangChain / Spring AI / Dify）均提供 Memory 抽象：

| Memory 类型 | 用途 | KfSmart 现状 |
|-------------|------|--------------|
| `BufferMemory` | 保留最近 N 轮 | ❌ 全量加载 |
| `SummaryMemory` | 摘要压缩长历史 | ❌ 无 |
| `VectorRetrieverMemory` | 向量检索相关历史 | ❌ 无 |
| `EntityMemory` | 提取实体关系 | ❌ 无 |
| `TokenWindowMemory` | 按 token 上限截断 | ❌ 仅 `ChatHandlerTruncateTest` 简单截断 |

**后果**：
- 长会话 token 超限（LLM 报 400 错误）
- 无法做长期记忆（用户跨会话的偏好、事实）
- Agent 多轮 Tool 调用的 observation 全量塞入 messages，token 爆炸

#### 5.2.2 无 Context Window 管理（High）

`ModelClient.buildMessages`（L596-644）直接把 `history` 全部加入 messages，无 token 计数与截断：

```java
if (history != null && !history.isEmpty()) {
    messages.addAll(history);  // 全量！
}
messages.add(Map.of("role", "user", "content", userMessage));
```

`ChatHandlerTruncateTest` 存在但截断策略是"按消息条数"（`keepCount`），不是"按 token 上限"。

#### 5.2.3 无长期记忆（Medium）

用户跨会话的事实（如"用户是 Python 开发者"）无法持久化与检索。Agent 无法基于用户画像个性化回答。

### 5.3 建议

引入 `Memory` 接口：

```java
public interface Memory {
    List<Message> load(String sessionId, MemoryQuery query);
    void save(String sessionId, Message message);
    void clear(String sessionId);
    default List<Message> load(String sessionId) { return load(sessionId, MemoryQuery.all()); }
}

public record MemoryQuery(int maxMessages, int maxTokens, String semanticQuery, Set<String> entityFilter) {
    public static MemoryQuery all() { return new MemoryQuery(Integer.MAX_VALUE, Integer.MAX_VALUE, null, Set.of()); }
    public static MemoryQuery lastN(int n) { return new MemoryQuery(n, Integer.MAX_VALUE, null, Set.of()); }
    public static MemoryQuery semantic(String query, int topK) { ... }
}
```

实现：
- `BufferMemory`：保留最近 N 轮（替换当前全量加载）
- `TokenWindowMemory`：按 token 上限截断 + 最早消息淘汰
- `SummaryMemory`：超阈值时摘要压缩
- `VectorRetrieverMemory`：基于 ES 向量检索相关历史
- `CompositeMemory`：组合多种策略

---

## 6. Event 架构分析

### 6.1 现状：无 Event Bus

三类执行体的事件流各自实现：

#### 6.1.1 Chat 事件流

```
ChatHandler.processMessage
  → sendSearchResults(session, searchResults)  [WebSocket 文本帧]
  → Thread.sleep(500)
  → ModelClient.streamResponse(... onChunk, onError, onComplete)
    → onChunk: sendResponseChunk(session, chunk)  [WebSocket 文本帧]
    → onError: handleStreamError  [WebSocket 错误帧]
    → onComplete: finalizeStreamResponse  [WebSocket 完成帧]
```

**事件类型**：硬编码 JSON 字符串，如 `{"type":"search_results", ...}` / `{"type":"chunk", ...}` / `{"type":"error", ...}` / `{"type":"complete", ...}`（具体格式在 `sendSearchResults` / `sendResponseChunk` 等方法中，未统一抽象）。

#### 6.1.2 Agent 事件流

```
ReActEngine.execute(... progressListener: Consumer<AgentStep>)
  → 每步 ctx.addStep(step); progressListener.accept(step)
```

**事件类型**：`AgentStep` 对象（非 Event），`progressListener` 仅在 `AgentExecutionService.chat` 中传 `null`（L63），即**Agent 执行无事件推送**，仅同步返回最终结果。

#### 6.1.3 Workflow 事件流

```
WorkflowExecutionService.executeAsync
  → executionEngine.execute(... trace -> {
      frame = {type:"node_completed", executionId, node:{...}}
      broadcaster.broadcast(executionId, JSON(frame))
    })
  → broadcaster.broadcast(executionId, {type:"execution_completed", ...})
```

**事件类型**：JSON 帧字符串，`type` 字段为 `"node_completed"` / `"execution_completed"` / `"execution_failed"`（`WorkflowExecutionService.java` L116-129）。

### 6.2 问题

#### 6.2.1 无 Event 抽象（Critical）

三类执行体的事件**无统一模型**：
- Chat：WebSocket 文本帧，无 Event 对象
- Agent：`AgentStep` 对象，但 `progressListener=null`，未推送
- Workflow：JSON 字符串帧，`type` 字段硬编码

**后果**：
- 无法跨执行体订阅事件（如"监听所有 LLM 调用"需在 Chat / Agent / Workflow 三处分别埋点）
- 无法做统一 Metrics（LLM 调用次数、Token 消耗、延迟分布）
- 无法做统一 Trace（端到端链路追踪）
- 无法做统一 Replay（回放调试）

#### 6.2.2 事件类型硬编码（Medium）

Workflow 事件 `type` 字段为字符串：
- `"node_completed"` / `"execution_completed"` / `"execution_failed"`（`WorkflowExecutionService.java`）
- `"subscribed"`（`WorkflowWebSocketHandler.java` L52）

无枚举约束，拼写错误无法在编译期发现。

#### 6.2.3 无事件持久化（Medium）

事件仅通过 WebSocket 实时推送，未持久化。若客户端断连，已推送的事件丢失（虽有 `messageBuffer` 缓冲回放，但 `cleanup` 后即清除）。

### 6.3 建议：统一 Event Bus

```java
public sealed interface RuntimeEvent permits
    RuntimeStarted, RuntimeCompleted, RuntimeFailed, RuntimeCancelled,
    NodeStarted, NodeCompleted, NodeFailed,
    LlmCallStarted, LlmCallCompleted, LlmStreamChunk,
    ToolCallStarted, ToolCallCompleted,
    MemoryLoaded, MemorySaved,
    TokenUsageUpdated {
    String executionId();
    long timestamp();
    String targetType();  // "chat" / "agent" / "workflow"
}

public interface EventBus {
    void publish(RuntimeEvent event);
    void subscribe(String executionId, Consumer<RuntimeEvent> subscriber);
    void unsubscribe(String executionId, Consumer<RuntimeEvent> subscriber);
    // 全局订阅（用于 Metrics / Trace）
    void subscribeGlobal(Consumer<RuntimeEvent> subscriber);
}
```

实现：
- `InMemoryEventBus`：单机，基于 `ConcurrentHashMap<String, List<Consumer>>`
- `RedisEventBus`：跨实例，基于 Redis Pub/Sub
- `KafkaEventBus`：持久化，基于 Kafka topic

事件持久化：订阅 `RuntimeEvent` → 写入 `runtime_event_log` 表，供 Replay / Metrics。

---

## 7. Trace 体系分析

### 7.1 现状：三类不互操作

| 执行体 | Trace 对象 | 字段 | 持久化 |
|--------|-----------|------|--------|
| Chat | ❌ 无 | — | — |
| Agent | `AgentStep` | `iteration` / `thought` / `action` / `actionInput` / `observation` / `durationMs` / `status` | `AgentExecutionLog.traceJson` |
| Workflow | `NodeTrace` | `nodeId` / `nodeName` / `nodeType` / `startedAt` / `durationMs` / `inputs` / `outputs` / `promptTokens` / `completionTokens` / `status` / `errorMessage` | `WorkflowExecutionLog.traceJson` |

### 7.2 问题

#### 7.2.1 Chat 无 Trace（High）

Chat 是用户最直接使用的执行体，但**完全没有 Trace**——仅 `logger.info` 日志。无法回放一次 Chat 的完整过程（检索了什么、调用了哪个 LLM、消耗了多少 token、耗时多少）。

#### 7.2.2 Agent 与 Workflow Trace 不互操作（Medium）

`AgentStep` 与 `NodeTrace` 字段不兼容：
- `AgentStep.thought`（LLM 思考）vs `NodeTrace` 无对应字段
- `AgentStep.action` / `actionInput` / `observation`（ReAct 三元组）vs `NodeTrace.inputs` / `outputs`（通用 KV）
- `NodeTrace.nodeType`（节点类型）vs `AgentStep` 无对应字段

当 Workflow 中调用 Agent（`AgentCallNodeExecutor`），Agent 的 `List<AgentStep>` 不会合并到 Workflow 的 `List<NodeTrace>`——Agent 执行是黑盒，Workflow Trace 只看到"Agent 调用节点完成，输出 answer"。

#### 7.2.3 Trace 无 Span 层级（Medium）

Trace 是扁平的 `List<Trace>`，无父子层级（Span）。无法表达"Workflow → Agent 节点 → LLM 调用 → Tool 调用"的嵌套关系。

### 7.3 建议：统一 Trace 模型

```java
public record TraceSpan(
    String spanId,
    String parentSpanId,      // 父 Span，支持嵌套
    String executionId,        // 所属执行
    String targetType,          // chat / agent / workflow
    String spanType,            // runtime / node / llm_call / tool_call / retrieval
    String name,                // span 名称
    long startedAt,
    long durationMs,
    Map<String, Object> inputs,
    Map<String, Object> outputs,
    TokenUsage tokenUsage,
    String status,              // running / success / failed
    String errorMessage,
    Map<String, String> attributes  // 自定义属性
) {}
```

- Chat：每次 `processMessage` 产生一个 `runtime` Span，内部嵌套 `retrieval` Span + `llm_call` Span
- Agent：每次 `execute` 产生一个 `runtime` Span，内部嵌套 N 个 `llm_call` Span + `tool_call` Span
- Workflow：每次 `execute` 产生一个 `runtime` Span，内部嵌套 N 个 `node` Span，每个 `node` Span 可嵌套 `llm_call` / `tool_call` / `agent_call`（递归）

Trace 持久化到 `trace_span` 表，支持按 `executionId` 查询整棵 Span 树，端到端回放。

---

## 8. Streaming 体系分析

### 8.1 现状

| 执行体 | 流式能力 | 实现方式 |
|--------|----------|----------|
| Chat | ✅ LLM 流式输出 | `ModelClient.streamResponse` → `WebClient.bodyToFlux(String.class)` → `onChunk: Consumer<String>` → `sendResponseChunk: WebSocket` |
| Agent | ❌ 无流式 | `ModelClient.chatWithFunctions` 同步 `.block()` |
| Workflow | ⚠️ 节点级进度 | `progressListener: Consumer<NodeTrace>` 推送节点完成事件，但 LLM 节点内部不流式 |

### 8.2 Chat 流式实现（专业）

Chat 的流式是三类中最完整的：

```java
// ModelClient.streamResponse
Disposable streamSubscription = modelClient.streamResponse(
    userMessage, context, history, apiKeyConfig,
    chunk -> {  // onChunk
        if (isStreamInactive(sessionId)) return;
        responseBuilders.get(sessionId).append(chunk);
        sendResponseChunk(session, chunk);
    },
    error -> handleStreamError(...),  // onError
    () -> finalizeStreamResponse(...)  // onComplete
);
streamSubscriptions.put(sessionId, streamSubscription);
```

**优秀点**：
- `Disposable` 订阅可取消（`streamSubscriptions` 保存便于 `cancelStreamSubscription`）
- `StreamTerminalState` 状态机防止 completion 覆盖 error
- `sessionSendLocks` 加锁（Spring WebSocket session 不支持并发写）
- `sessionErrorSent: AtomicBoolean` 防止重复发送错误

**问题**：
- `Thread.sleep(500)`（L148）阻塞 WebSocket 线程（已在主报告 Issue 7 指出）
- 流式 chunk 无 Token 实时计费（仅完成后估算）
- 无背压控制（LLM 产生 chunk 速度 > WebSocket 发送速度时，内存堆积）

### 8.3 Agent / Workflow 无流式（High）

Agent 的 `chatWithFunctions` 是同步 `.block()`（`ModelClient.java` L318），Workflow 的 `LlmNodeExecutor` 调用 `modelClient.chat`（L116）也是同步。

**后果**：
- Agent 执行过程中用户无法看到 LLM 的实时输出（需等整个 ReAct 循环结束）
- Workflow 的 LLM 节点执行时，前端只能看到"节点运行中"，无法看到 LLM 流式输出
- 长时间 LLM 调用（如 30 秒生成）用户体验差

### 8.4 建议：统一 Streaming 抽象

```java
public interface StreamHandler {
    void onChunk(String chunk);
    void onError(Throwable error);
    void onComplete();
    void onTokenUsage(TokenUsage usage);
}

public interface StreamableRuntime {
    void executeStreaming(RuntimeContext ctx, StreamHandler handler);
}
```

- Chat：实现 `StreamableRuntime`，复用现有流式
- Agent：`ReActEngine` 增加 `executeStreaming`，每轮 LLM 调用流式输出
- Workflow：`LlmNodeExecutor` 调用 `modelClient.streamResponse`，流式 chunk 通过 `progressListener` 推送

---

## 9. Prompt 生命周期分析

### 9.1 现状：无统一 Prompt 管道

Prompt 的组装散落在多处：

#### 9.1.1 Chat 的 Prompt 组装

`ModelClient.buildMessages`（L596-644）：
```
system = AiProperties.prompt.rules + "<<REF>>\n" + context + "\n<<END>>"
messages = [system] + history + [user]
```

**固定模板**，无法按 Agent / Workflow 自定义。RAG context 嵌入 `<<REF>>...<<END>>` 标记，LLM 靠 prompt 指令识别。

#### 9.1.2 Agent 的 Prompt 组装

`ReActEngine.buildUserMessage`（L136-156）：
```
if agent.userPrompt contains {{query}}: template.replace({{query}}, query)
else if agent.userPrompt not blank: template + "\n\n用户输入：\n" + query
else: query
```

`ReActEngine.resolveSystemPrompt`（L197-205）：`agent.systemPrompt` 或 debug override。

**问题**：
- `systemPrompt` 不支持模板变量（如 `{{user.name}}` / `{{now}}`）
- `userPrompt` 仅支持 `{{query}}` / `{{input}}`，不支持 `{{history}}` / `{{context}}` / `{{tool_results}}`
- RAG context 不自动注入 Agent 的 system prompt（除非在 `ModelClient.buildMessages` 的 `systemPrompt != null` 分支硬编码追加，L606-607）

#### 9.1.3 Workflow LLM 节点的 Prompt 组装

`LlmNodeExecutor.execute`（L48-132）：
```
promptTemplate = node.config.prompt
query = ctx.resolveTemplate(promptTemplate)  // 解析 {{query}} 等
systemPrompt = node.config.systemPrompt 或 buildAutoSystemPrompt(ctx)
```

**优秀点**：
- `prompt` 和 `systemPrompt` 都支持 `ctx.resolveTemplate`，可引用任意变量
- `buildAutoSystemPrompt` 自动注入 RAG context
- `debug_systemPrompt` 支持即时调试覆盖

**问题**：
- 无 Prompt 版本管理（`PromptTemplate` 实体存在但仅用于 Skill，未用于 LLM 节点）
- 无 Prompt 变量校验（`{{xxx}}` 拼写错误不会报错，输出空字符串）
- 无 Prompt A/B 测试（同一节点无法对比两个 prompt 的效果）

### 9.2 Prompt 生命周期应具备的能力

一个完整的 Prompt 生命周期应为：

```
Prompt Template (DB)
  ↓ 变量解析
Prompt Variables (ExecutionContext + Memory + Context)
  ↓ 组装
Assembled Prompt (system + user + history)
  ↓ 校验
Validated Prompt (token 上限、变量完整性)
  ↓ 发送
LLM Request
  ↓ 流式
Streaming Output
  ↓ 后处理
Output (Guardrail / 敏感词过滤 / 格式化)
```

当前实现仅有"变量解析 + 组装 + 发送"，缺失：
- ❌ Prompt 版本管理（LLM 节点未用 `PromptTemplate`）
- ❌ Prompt 变量校验
- ❌ Prompt token 上限检查
- ❌ Output Guardrail
- ❌ Prompt A/B 测试
- ⚠️ Memory 注入（仅 history 全量，无摘要/向量检索）
- ⚠️ Context 组装（Chat 固定 `<<REF>>`，Agent 不自动注入）

### 9.3 建议：统一 Prompt 管道

```java
public interface PromptPipeline {
    AssembledPrompt assemble(PromptTemplate template, PromptContext context);
}

public record PromptContext(
    String userId,
    String sessionId,
    Map<String, Object> variables,
    Memory memory,
    RetrievalContext retrieval,
    List<ToolDefinition> tools,
    AgentPersona persona
) {}

public record AssembledPrompt(
    String systemPrompt,
    String userPrompt,
    List<Message> history,
    List<ToolDefinition> tools,
    int estimatedTokens,
    Map<String, String> resolvedVariables  // 用于调试展示
) {}
```

- `PromptTemplateService`：管理 Prompt 模板版本（已有 `PromptTemplate` + `PromptTemplateHistory`，需接入 LLM 节点）
- `PromptAssembler`：组装 system + user + history + tools
- `PromptValidator`：校验 token 上限、变量完整性
- `OutputGuardrail`：过滤敏感词、Prompt Injection 防护

---

## 10. Variable System 分析（Workflow）

> 本节为 Workflow Variable System 的专项分析，更深入的工作流变量分析见 `docs/workflow-runtime-review.md`。

### 10.1 现状

`ExecutionContext` 的变量系统：

```java
// 变量存储
ConcurrentHashMap<String, Object> variables;

// 节点输出存储
ConcurrentHashMap<String, Map<String, Object>> nodeOutputs;

// 模板解析
Pattern TEMPLATE_PATTERN = Pattern.compile("\\{\\{([^}]+)}}");

String resolveTemplate(String template) {
    // {{query}} → variables.get("query")
    // {{input.query}} → variables.get("query")
    // {{context}} → variables.get("context")
    // {{nodeId.key}} → nodeOutputs.get(nodeId).get(key)
}
```

### 10.2 问题

#### 10.2.1 无变量作用域（High）

所有变量全局共享，无 Scope 概念：
- 节点 A 设置 `result`，节点 B 也设置 `result`，后者覆盖前者（`setVariable` 直接 put）
- 循环节点的 `item` 变量在循环结束后仍残留
- 无法实现"节点局部变量"（如 `{{nodeA.output}}` 访问节点 A 输出，但若节点 A 输出 key 为 `output`，全局 `output` 变量也被污染）

`setNodeOutput`（L88-95）尝试避免冲突：
```java
output.forEach((key, value) -> {
    if (key != null && value != null && !variables.containsKey(key)) {  // 仅当未冲突时注入
        variables.put(key, value);
    }
});
```
但这导致**冲突时节点输出无法注入全局**，后续节点 `{{conflictKey}}` 取到的是旧值，而非当前节点输出。

#### 10.2.2 无变量类型系统（High）

所有变量都是 `Object`，模板解析时 `String.valueOf(val)` 强转：
- 数字 `42` → `"42"`
- 布尔 `true` → `"true"`
- 列表 `[1,2,3]` → `"[1, 2, 3]"`
- Map `{"a":1}` → `"{a=1}"`

`ConditionNodeExecutor.compareNumeric`（L122-130）靠 `Double.parseDouble` 异常 fallback 到字符串比较，对列表/Map 比较无意义。

#### 10.2.3 无变量校验（Medium）

`{{xxx}}` 拼写错误时，`resolveTemplate` 返回空字符串，不报错。用户不知道变量未定义还是值为空。

### 10.3 建议

见 `docs/workflow-runtime-review.md` §Variable System。

---

## 11. 综合 Runtime 评估与路线图

### 11.1 Runtime 成熟度雷达图（文字版）

```
              Runtime 抽象 (40)
                   │
    Streaming (60) ─┼─ Session (55)
                   │
Trace (55) ────────┼──────── Context (50)
                   │
    Lifecycle (45) ─┼─ Event (35)
                   │
              Memory (30)
                   │
            Prompt (50)
```

**短板**：Memory (30) / Event (35) / Lifecycle (45) 是最大短板。
**长板**：Streaming (60) / Session (55) / Trace (55) 相对较好。

### 11.2 Runtime 演进路线图

#### Phase R1（1~2 月）：建立 Runtime 抽象骨架

| 任务 | 优先级 | 工作量 |
|------|--------|--------|
| 定义 `RuntimeContext` 接口，统一 `AgentContext` / `ExecutionContext` | P0 | 3d |
| 定义 `RuntimeEvent` + `EventBus`，替换 `progressListener` | P0 | 3d |
| 定义 `RuntimeSession`，让 Agent/Workflow 复用 Chat Session | P1 | 2d |
| 定义 `TraceSpan`，统一 `AgentStep` / `NodeTrace` | P1 | 3d |
| Workflow async 改 `ExecutorService` 线程池 + 超时 + 可取消 | P0 | 2d |

#### Phase R2（2~3 月）：Memory + Lifecycle

| 任务 | 优先级 | 工作量 |
|------|--------|--------|
| `Memory` 接口 + `BufferMemory` / `TokenWindowMemory` / `SummaryMemory` | P0 | 5d |
| `RuntimeStateMachine`：Created → Running → Paused → Resumed → Completed/Failed/Cancelled | P1 | 3d |
| Workflow Checkpoint 持久化 + 断点续跑 | P1 | 5d |
| Chat 增加 Trace（`runtime` Span + `retrieval` Span + `llm_call` Span） | P1 | 3d |

#### Phase R3（3~6 月）：Prompt 管道 + Streaming 统一

| 任务 | 优先级 | 工作量 |
|------|--------|--------|
| `PromptPipeline` + `PromptAssembler` + `PromptValidator` | P1 | 4d |
| LLM 节点接入 `PromptTemplate` 版本管理 | P2 | 2d |
| Agent / Workflow LLM 节点实现流式输出 | P1 | 3d |
| `OutputGuardrail`（敏感词 + Prompt Injection） | P2 | 3d |
| 变量作用域（Scope）+ 类型系统 | P1 | 5d |

#### Phase R4（6~12 月）：统一 Runtime + Multi-Agent

| 任务 | 优先级 | 工作量 |
|------|--------|--------|
| `AiRuntime` 统一接口，Chat/Agent/Workflow 实现 | P2 | 5d |
| 事件持久化 + Replay 回放 | P2 | 3d |
| Multi-Agent 协作（Agent 间通信 + 共享 Context） | P3 | 8d |
| Plan-and-Execute Runtime（长程任务规划） | P3 | 5d |
| 自治长程任务（Autonomous Long-Running Task） | P3 | 8d |

### 11.3 结论

KfSmart 的 AI Runtime 当前是**三类独立实现 + RuntimeService 适配器**的架构，没有统一的 Runtime 抽象。这导致：

1. **可复用性差**：Chat 的流式、Agent 的 ReAct、Workflow 的 DAG 各自为政，无法组合。
2. **可观测性割裂**：三类 Trace 不互操作，无法端到端追踪。
3. **演进受限**：新增执行体（Multi-Agent / Plan-and-Execute）需从零设计 Runtime。

**最大的短板是 Memory（30 分）和 Event（35 分）**，这是阻碍项目从"可用"走向"优秀"的关键瓶颈。

**最大的资产是 `ExecutionContext` 与 `WorkflowProgressBroadcaster`**，它们是统一 Runtime 的良好雏形。

建议按 Phase R1 → R2 → R3 → R4 顺序演进，**Phase R1（统一 Runtime 抽象骨架）是后续一切的基础**，必须在 Phase R2/R3 之前完成。

---

## 附录：本报告与主报告的关系

| 主报告章节 | 本报告对应章节 | 深化程度 |
|-----------|---------------|----------|
| §3.11 AI Architecture | §1 Runtime 抽象 + §2 生命周期 + §4 Context + §8 Streaming | 深化 |
| §3.11.3 AI 架构问题（Memory 抽象） | §5 Memory 抽象 | 深化 |
| §3.11.3 AI 架构问题（无 Event Bus） | §6 Event 架构 | 新增 |
| §3.11.3 AI 架构问题（无 Context Window 管理） | §4.2 Agent Context + §5.2.2 Context Window | 深化 |
| — | §3 Session 管理 | 新增 |
| — | §7 Trace 体系 | 深化 |
| — | §9 Prompt 生命周期 | 新增 |
| — | §10 Variable System（Workflow） | 新增（详见 workflow-runtime-review.md） |

本报告与 `docs/workflow-runtime-review.md` / `docs/ai-platform-evolution-review.md` 共同构成 KfSmart 的 **AI 平台专项审查体系**，补充主报告在 AI Runtime 维度的分析深度不足。

---

**报告结束。**

> 本报告共 ~5500 字，覆盖 Runtime 抽象、生命周期、Session、Context、Memory、Event、Trace、Streaming、Prompt 生命周期、Variable System 共 10 个维度，是 KfSmart AI Runtime 架构的专项深度评审。

# AI Platform Evolution Review

> **Project**: KfSmart AI Platform
> **Report Type**: Specialized AI Platform Evolution Review（AI 平台演进路线图专项评审）
> **Review Date**: 2026-06-30
> **Reviewer Role**: Principal Engineer / AI Platform Architect
> **Companion Report**: 本报告是 `docs/review-report.md` 的补充专项报告，针对 Meta Review §五.3 提出的"站在未来 2~3 年的发展角度，分析当前架构如何逐步演进为完整 AI 平台"进行专项规划。

---

## 0. Executive Summary

KfSmart 当前是一个**功能完整的 RAG 知识管理平台 + Agent / Workflow 编排引擎**，但距离**插件化 AI 平台**仍有显著差距。本报告站在未来 2~3 年的发展角度，规划 KfSmart 从"单体应用 + 硬编码扩展点"演进为"插件化 AI 平台 + 统一 Runtime 抽象"的完整路线图。

### 0.1 核心论点

**当前架构的主要矛盾**：项目已具备丰富的 AI 能力（RAG / Agent / Workflow / MCP / Skill），但各能力的**扩展点硬编码**——新增 LLM Provider 需改 `ModelClient`、新增工具需改 `ToolRegistry.resolveTools`、新增节点需改 `NodeExecutorRegistry`（虽自动注册但节点类型用中文字符串匹配）、新增 Memory 策略无接口可实现。这导致平台**难以社区化 / 商业化**——第三方无法在不修改源码的前提下扩展能力。

**演进的核心方向**：将所有扩展点统一为 **SPI（Service Provider Interface）插件机制**，使每一类能力（Runtime / Tool / Provider / Agent / Workflow / Prompt / Memory / Node）都可通过"实现接口 + 自动注册"的方式扩展，无需修改核心代码。

### 0.2 演进愿景

**2~3 年后的 KfSmart 应该是**：

```
┌─────────────────────────────────────────────────────────────┐
│                   KfSmart AI Platform                       │
│                                                              │
│  ┌──────────────────────────────────────────────────────┐  │
│  │            Unified Runtime Layer                      │  │
│  │  (Chat / Agent / Workflow / Multi-Agent 共用)         │  │
│  │  RuntimeContext + RuntimeSession + RuntimeEvent      │  │
│  └──────────────────────────────────────────────────────┘  │
│                          │                                   │
│  ┌──────────┬──────────┬──────────┬──────────┬──────────┐ │
│  │ Provider │  Tool    │  Memory  │  Prompt  │  Node    │ │
│  │ Plugin   │  Plugin  │  Plugin  │  Plugin  │  Plugin  │ │
│  ├──────────┼──────────┼──────────┼──────────┼──────────┤ │
│  │OpenAI    │KB Search │Buffer    │Template  │LLM       │ │
│  │Anthropic │HTTP Call │Summary   │FewShot   │Code      │ │
│  │Gemini    │SQL Query │Vector    │ChainOf   │SQL       │ │
│  │Bedrock   │Web Search│Entity    │Thought   │HTTP      │ │
│  │Ollama    │Code Exec │Episodic  │Custom    │Condition │ │
│  │Qwen      │Email     │Custom    │          │Loop      │ │
│  │Custom    │Custom    │Custom    │          │Custom    │ │
│  └──────────┴──────────┴──────────┴──────────┴──────────┘ │
│                          │                                   │
│  ┌──────────────────────────────────────────────────────┐  │
│  │              Event Bus + Trace Pipeline               │  │
│  │  (统一事件流 + Span 层级 + 持久化 + Replay)            │  │
│  └──────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

### 0.3 演进路线图概览

| 阶段 | 时间 | 核心目标 | 交付物 |
|------|------|----------|--------|
| **Phase E1** | 0~3 月 | 插件化骨架 | SPI 接口定义 + Provider/Tool/Memory 插件化 + 自动注册 |
| **Phase E2** | 3~9 月 | 统一 Runtime | RuntimeContext/Session/Event + Chat/Agent/Workflow 统一 + Trace Span |
| **Phase E3** | 9~15 月 | 高级编排 | Multi-Agent + Plan-and-Execute + 子图 + Checkpoint |
| **Phase E4** | 15~24 月 | 平台化 | 插件市场 + 沙箱执行 + 计费 + 多租户 + 商业化 |

---

## 1. 当前扩展点分析

### 1.1 扩展点清单

| 扩展点 | 当前机制 | 是否插件化 | 修改成本 |
|--------|----------|------------|----------|
| 新增 LLM Provider | `ApiKeyConfig` 数据库配置（OpenAI/Anthropic 协议） | ⚠️ 半插件化（新协议需改 `ModelClient`） | Medium |
| 新增 Workflow 节点 | `@Component implements NodeExecutor` 自动注册 | ✅ 插件化 | Low |
| 新增 Agent 工具 | 改 `ToolRegistry.resolveTools` 加 if 分支 | ❌ 硬编码 | Medium |
| 新增 MCP 工具 | 数据库 `McpToolConfig` 配置 | ✅ 数据驱动 | Low |
| 新增 Memory 策略 | 无接口，无法扩展 | ❌ 无扩展点 | High |
| 新增 Prompt 策略 | 无接口，组装逻辑硬编码在 `ModelClient.buildMessages` | ❌ 无扩展点 | High |
| 新增 Runtime 类型 | 改 `RuntimeService.executeByType` 加 if 分支 | ❌ 硬编码 | Medium |
| 新增检索策略 | 改 `HybridSearchService`（单实现） | ❌ 无扩展点 | High |
| 新增文件解析器 | Apache Tika 自动支持 | ✅ 数据驱动 | Low |
| 新增 Embedding 模型 | `ApiKeyConfig` 类似配置（单一 `EmbeddingClient`） | ⚠️ 半插件化 | Medium |

### 1.2 问题总结

**5/10 扩展点未插件化**（Memory / Prompt / Runtime / 检索 / 工具），**3/10 半插件化**（Provider / Embedding / Agent 工具），**2/10 完全插件化**（节点 / MCP 工具）。

这意味着 KfSmart 当前**难以作为平台被第三方扩展**——任何新能力的引入都需修改核心源码，无法形成"插件生态"。

---

## 2. 演进目标：统一 SPI 插件架构

### 2.1 设计原则

1. **开闭原则（OCP）**：新增能力不修改核心代码，通过实现 SPI 接口 + 自动注册。
2. **单一职责（SRP）**：每个 SPI 接口只关注一类扩展（Provider / Tool / Memory / ...）。
3. **依赖倒置（DIP）**：核心代码依赖 SPI 接口，不依赖具体实现。
4. **配置优于代码**：能用数据配置解决的不写代码（如 Provider 配置走数据库）。
5. **组合优于继承**：通过 `@Composite` 而非继承扩展行为。

### 2.2 SPI 接口体系

```
┌─────────────────────────────────────────────────┐
│              KfSmart SPI Layer                   │
├─────────────────────────────────────────────────┤
│  ProviderSpi        — LLM / Embedding / Reranker │
│  ToolSpi            — Agent 可调用的工具          │
│  MemorySpi          — 对话记忆策略                │
│  PromptPipelineSpi  — Prompt 组装与后处理         │
│  NodeExecutorSpi    — Workflow 节点执行器         │
│  RetrieverSpi       — RAG 检索策略                │
│  RuntimeSpi         — 执行体类型（Chat/Agent/...） │
│  GuardrailSpi       — 输入/输出安全过滤           │
│  ObserverSpi        — 事件监听（Metrics/Trace）    │
└─────────────────────────────────────────────────┘
```

每个 SPI 接口对应一个 `Registry`，通过 Spring `@Component` 自动扫描注册，或通过数据库配置动态加载。

---

## 3. Phase E1（0~3 月）：插件化骨架

### 3.1 Provider 插件化

#### 3.1.1 现状问题

`ModelClient` 硬编码 `if ("anthropic".equals(authType))` 分支，新增协议（Gemini / Cohere / Bedrock）需改 `streamResponse` / `chat` / `chatWithFunctions` 多处。

#### 3.1.2 目标设计

```java
public interface ProviderSpi {
    String getProviderType();  // "openai" / "anthropic" / "gemini" / ...
    boolean supports(String authType);
    ProviderRequest buildRequest(PromptContext context, ApiKeyConfig config);
    StreamHandler parseStreamChunk(String chunk, StreamHandler handler);
    ProviderResponse parseResponse(String response);
    String resolveEndpoint(ApiKeyConfig config);
    List<ToolDefinition> convertTools(List<ToolDefinition> tools);
}

@Service
public class ProviderRegistry {
    private final Map<String, ProviderSpi> providers;
    // 自动扫描所有 ProviderSpi Bean
    public ProviderSpi resolve(String authType) { ... }
}

@Service
public class ModelClient {
    private final ProviderRegistry registry;
    public Disposable streamResponse(..., ApiKeyConfig config, ...) {
        ProviderSpi provider = registry.resolve(config.getAuthType());
        ProviderRequest request = provider.buildRequest(context, config);
        // 委托给 provider 解析流式
    }
}
```

**实现**：
- `OpenAiProvider` — 现有 OpenAI 协议逻辑
- `AnthropicProvider` — 现有 Anthropic 协议逻辑
- 未来：`GeminiProvider` / `BedrockProvider` / `QwenProvider` / `OllamaProvider`

#### 3.1.3 Embedding 插件化

同理，`EmbeddingClient` 抽取 `EmbeddingProviderSpi`：
- `DashScopeEmbeddingProvider`（text-embedding-v4）
- 未来：`OpenAiEmbeddingProvider` / `BgeEmbeddingProvider` / `CohereEmbeddingProvider`

### 3.2 Tool 插件化

#### 3.2.1 现状问题

`ToolRegistry.resolveTools`（`src/main/java/com/smart/kf/agent/engine/ToolRegistry.java` L36-53）硬编码：
```java
if (isPresent(agent.getKnowledgeBases())) {
    tools.add(buildKnowledgeBaseTool(agent.getKnowledgeBases()));
}
if (isPresent(agent.getMcpTools())) {
    for (String toolName : agent.getMcpTools().split(",")) {
        tools.add(buildMcpTool(trimmed));
    }
}
```

新增工具类型需改此方法。

#### 3.2.2 目标设计

```java
public interface ToolProvider {
    String getToolType();  // "knowledge_base" / "mcp" / "http" / "sql" / ...
    List<ToolDefinition> resolveTools(Agent agent);
    boolean isApplicable(Agent agent);
}

@Service
public class ToolRegistry {
    private final List<ToolProvider> providers;  // 自动注入
    public List<ToolDefinition> resolveTools(Agent agent) {
        return providers.stream()
            .filter(p -> p.isApplicable(agent))
            .flatMap(p -> p.resolveTools(agent).stream())
            .toList();
    }
}
```

**实现**：
- `KnowledgeBaseToolProvider` — 从 `agent.getKnowledgeBases()` 解析
- `McpToolProvider` — 从 `agent.getMcpTools()` 解析
- 未来：`HttpToolProvider` / `SqlToolProvider` / `WebSearchToolProvider` / `CodeExecutionToolProvider`

### 3.3 Memory 插件化

#### 3.3.1 现状问题

无 Memory 接口，对话历史直接存 Redis JSON，无扩展点。

#### 3.3.2 目标设计

```java
public interface MemorySpi {
    String getMemoryType();  // "buffer" / "summary" / "vector" / "entity" / "composite"
    List<Message> load(String sessionId, MemoryQuery query);
    void save(String sessionId, Message message);
    void clear(String sessionId);
    MemoryConfig getDefaultConfig();
}

@Service
public class MemoryRegistry {
    private final Map<String, MemorySpi> memories;
    public MemorySpi resolve(String memoryType) { ... }
}
```

**实现**：
- `BufferMemory` — 保留最近 N 轮
- `TokenWindowMemory` — 按 token 上限截断
- `SummaryMemory` — 超阈值时摘要压缩
- `VectorRetrieverMemory` — 基于 ES 向量检索相关历史
- `CompositeMemory` — 组合多种策略

**配置**：Agent / Workflow 可配置 `memoryType` + `memoryConfig`。

### 3.4 Prompt 管道插件化

#### 3.4.1 现状问题

Prompt 组装硬编码在 `ModelClient.buildMessages`，无扩展点。

#### 3.4.2 目标设计

```java
public interface PromptPipelineSpi {
    String getPipelineType();  // "default" / "few_shot" / "chain_of_thought" / "react"
    AssembledPrompt assemble(PromptTemplate template, PromptContext context);
}

public interface OutputGuardrailSpi {
    String getGuardrailType();  // "sensitive_word" / "prompt_injection" / "pii_filter"
    String filter(String output, GuardrailContext context);
}
```

**实现**：
- `DefaultPromptPipeline` — 现有逻辑
- `FewShotPromptPipeline` — 支持 few-shot 示例
- `ChainOfThoughtPromptPipeline` — CoT 提示
- `SensitiveWordGuardrail` — 敏感词过滤
- `PromptInjectionGuardrail` — Prompt Injection 检测

### 3.5 Node Executor 已插件化（巩固）

`NodeExecutor` + `NodeExecutorRegistry` 已是良好的插件化设计，**仅需修复以下问题**：

1. **节点类型中文字符串匹配**（`getNodeType()` 返回 `"代码执行"` / `"SQL查询"`）→ 改为枚举常量 + 中英文 alias 映射。
2. **`getExecutor` 模糊匹配**（`nodeType.contains(entry.getKey())`）可能导致误匹配 → 改为严格匹配 + 显式 alias 表。
3. **新增 `CompensatableNodeExecutor` 子接口**（见 workflow-runtime-review.md §5）。

### 3.6 Retriever 插件化

#### 3.6.1 现状问题

`HybridSearchService` 单实现，无检索策略选择。

#### 3.6.2 目标设计

```java
public interface RetrieverSpi {
    String getRetrieverType();  // "hybrid" / "vector" / "bm25" / "rerank"
    List<SearchResult> retrieve(String query, RetrievalContext context);
}

@Service
public class RetrieverRegistry {
    public RetrieverSpi resolve(String retrieverType) { ... }
}
```

**实现**：
- `HybridRetriever` — 现有 KNN + BM25
- `VectorRetriever` — 纯向量
- `Bm25Retriever` — 纯文本
- `RerankRetriever` — 二次排序（如 Cohere Rerank）
- `MultiQueryRetriever` — 多查询生成

### 3.7 Runtime 插件化

#### 3.7.1 现状问题

`RuntimeService.executeByType` 硬编码 `if/else` 路由到 Agent / Workflow。

#### 3.7.2 目标设计

```java
public interface RuntimeSpi {
    String getRuntimeType();  // "chat" / "agent" / "workflow" / "multi_agent" / "plan_execute"
    RuntimeResult execute(RuntimeContext context);
    boolean supports(String targetType);
}

@Service
public class RuntimeRegistry {
    private final Map<String, RuntimeSpi> runtimes;
    public RuntimeSpi resolve(String runtimeType) { ... }
}
```

**实现**：
- `ChatRuntime` — 现有 ChatHandler 抽象
- `AgentRuntime` — 现有 ReActEngine 抽象
- `WorkflowRuntime` — 现有 WorkflowExecutionEngine 抽象
- 未来：`MultiAgentRuntime` / `PlanExecuteRuntime`

### 3.8 Phase E1 交付物与工作量

| 交付物 | 工作量 | 优先级 |
|--------|--------|--------|
| `ProviderSpi` + `ProviderRegistry` + OpenAI/Anthropic 迁移 | 5d | P0 |
| `ToolProvider` + `ToolRegistry` 重构 | 3d | P0 |
| `MemorySpi` + `MemoryRegistry` + Buffer/TokenWindow/Summary | 5d | P0 |
| `PromptPipelineSpi` + Default/CoT 实现 | 3d | P1 |
| `NodeExecutor` 类型枚举化 + 严格匹配 | 2d | P1 |
| `RetrieverSpi` + Hybrid/Vector 迁移 | 3d | P1 |
| `RuntimeSpi` + Chat/Agent/Workflow 抽象 | 5d | P1 |
| `OutputGuardrailSpi` + 敏感词/PromptInjection | 3d | P2 |
| **合计** | **29d** | — |

---

## 4. Phase E2（3~9 月）：统一 Runtime + Event Bus

### 4.1 统一 Runtime 抽象

#### 4.1.1 RuntimeContext 统一

将 `AgentContext` / `ExecutionContext` / Chat 散落 Map 统一为 `RuntimeContext`：

```java
public class RuntimeContext {
    private final String executionId;
    private final String runtimeType;  // chat / agent / workflow
    private final String targetId;     // agentId / workflowId / conversationId
    private final String username;
    private final RuntimeSession session;
    private final VariableScope variables;  // 见 workflow-runtime-review.md §8
    private final MemorySpi memory;
    private final List<TraceSpan> trace;
    private final TokenUsageTracker tokenUsage;
    private final EventBus eventBus;
    private final RuntimeStateMachine state;
    // ...
}
```

`AgentContext` / `ExecutionContext` 成为 `RuntimeContext` 的特化包装。

#### 4.1.2 RuntimeSession 统一

将 Chat 的 Redis 会话机制提取为 `RuntimeSession`，供 Agent/Workflow 复用：

```java
public interface RuntimeSession {
    String getSessionId();
    String getUserId();
    String getScope();  // chat / runtime / agent / workflow
    Map<String, Object> getMetadata();
    void appendTurn(String userMessage, String assistantResponse);
    void appendError(String userMessage, String errorMessage);
    List<Message> getHistory();
    void truncate(int keepCount);
}
```

Agent / Workflow 执行时创建 / 复用 `RuntimeSession`，Trace 与 Token 写入会话历史。

### 4.2 Event Bus + Trace Span

#### 4.2.1 统一 Event Bus

```java
public sealed interface RuntimeEvent permits
    RuntimeStarted, RuntimeCompleted, RuntimeFailed, RuntimeCancelled,
    NodeStarted, NodeCompleted, NodeFailed,
    LlmCallStarted, LlmCallCompleted, LlmStreamChunk,
    ToolCallStarted, ToolCallCompleted,
    MemoryLoaded, MemorySaved,
    TokenUsageUpdated, CheckpointSaved {
    String executionId();
    long timestamp();
    String targetType();
}

public interface EventBus {
    void publish(RuntimeEvent event);
    void subscribe(String executionId, Consumer<RuntimeEvent> subscriber);
    void subscribeGlobal(Consumer<RuntimeEvent> subscriber);  // Metrics/Trace
}
```

**实现**：
- `InMemoryEventBus` — 单机
- `RedisEventBus` — 跨实例 Pub/Sub
- `KafkaEventBus` — 持久化

#### 4.2.2 统一 TraceSpan

```java
public record TraceSpan(
    String spanId,
    String parentSpanId,      // 父 Span
    String executionId,
    String targetType,          // chat / agent / workflow
    String spanType,            // runtime / node / llm_call / tool_call / retrieval
    String name,
    long startedAt,
    long durationMs,
    Map<String, Object> inputs,
    Map<String, Object> outputs,
    TokenUsage tokenUsage,
    String status,
    String errorMessage,
    Map<String, String> attributes
) {}
```

**跨执行体 Trace 合并**：`AgentCallNodeExecutor` 调用 Agent 时，把 Agent 的 Trace 作为 Workflow Node Span 的子 Span 合并：

```
Workflow Runtime Span (exec_xxx)
  └─ Node Span: Agent 调用 (node_5)
       └─ Agent Runtime Span (exec_yyy)  ← 子执行
            ├─ LLM Call Span (iteration 1)
            ├─ Tool Call Span (search_knowledge_base)
            └─ LLM Call Span (iteration 2)
```

### 4.3 流式统一

`RuntimeSpi` 支持 `executeStreaming`：

```java
public interface RuntimeSpi {
    RuntimeResult execute(RuntimeContext context);  // 同步
    default void executeStreaming(RuntimeContext context, StreamHandler handler) {
        throw new UnsupportedOperationException("Not streamable");
    }
    boolean isStreamable();
}
```

- Chat：流式（现有）
- Agent：每轮 LLM 调用流式
- Workflow：LLM 节点流式，通过 Event Bus 推送 `LlmStreamChunk` 事件

### 4.4 Phase E2 交付物与工作量

| 交付物 | 工作量 | 优先级 |
|--------|--------|--------|
| `RuntimeContext` 统一 + AgentContext/ExecutionContext 适配 | 5d | P0 |
| `RuntimeSession` 提取 + Chat/Agent/Workflow 复用 | 4d | P0 |
| `EventBus` + InMemory/Redis 实现 | 4d | P0 |
| `TraceSpan` 统一 + 跨执行体合并 | 5d | P0 |
| Agent 流式输出 | 3d | P1 |
| Workflow LLM 节点流式 | 3d | P1 |
| 事件持久化 + Replay | 4d | P1 |
| RuntimeStateMachine（Pause/Resume/Cancel） | 4d | P1 |
| **合计** | **32d** | — |

---

## 5. Phase E3（9~15 月）：高级编排能力

### 5.1 Multi-Agent 协作

#### 5.1.1 目标

支持多个 Agent 协作完成复杂任务：
- **Supervisor-Worker**：一个 Supervisor Agent 分发任务给多个 Worker Agent
- **Sequential Pipeline**：Agent A → Agent B → Agent C 串行
- **Debate**：多个 Agent 辩论后由 Moderator 汇总
- **Hierarchical**：树状 Agent 层级

#### 5.1.2 设计

```java
public class MultiAgentRuntime implements RuntimeSpi {
    public RuntimeResult execute(RuntimeContext context) {
        MultiAgentGraph graph = graphBuilder.build(context.getTargetId());
        // 执行多 Agent DAG，Agent 间通过 SharedContext 通信
    }
}

public class SharedContext {
    private final Map<String, Object> shared;  // Agent 间共享变量
    private final List<Message> messageBoard;  // Agent 间消息传递
    private final Lock lock;
}
```

新增节点：
- `AgentCallNodeExecutor`（已存在，需增强为支持 SharedContext）
- `SupervisorNodeExecutor` — Supervisor 分发任务
- `AggregatorNodeExecutor` — 汇总多 Agent 结果

### 5.2 Plan-and-Execute

#### 5.2.1 目标

长程任务先规划再执行：
1. **Planner Agent**：根据目标生成步骤列表
2. **Executor Agent**：逐步执行
3. **Replanner**：失败时重新规划

#### 5.2.2 设计

```java
public class PlanExecuteRuntime implements RuntimeSpi {
    public RuntimeResult execute(RuntimeContext context) {
        List<PlanStep> plan = planner.plan(context);
        for (PlanStep step : plan) {
            try {
                executor.execute(step, context);
            } catch (Exception e) {
                plan = replanner.replan(plan, step, e, context);
            }
        }
    }
}
```

### 5.3 子图 + 可重入执行

#### 5.3.1 目标

支持 Workflow 循环、Multi-Agent 内嵌 Workflow、Agent 内嵌 Workflow。需要 `WorkflowExecutionEngine` 支持"可重入"——同一节点在不同 Context 下可多次执行。

#### 5.3.2 设计

引入 `ExecutionContext` 的"父子"关系：
```java
public class ExecutionContext {
    private final ExecutionContext parent;  // 父上下文（用于子图）
    private final VariableScope scope;        // 继承父作用域
    // ...
}
```

子图执行时创建子 Context，继承父变量但有自己的 `executed` 集合。

### 5.4 Checkpoint + 断点续跑

（见 `docs/workflow-runtime-review.md` §4）

### 5.5 Phase E3 交付物与工作量

| 交付物 | 工作量 | 优先级 |
|--------|--------|--------|
| Multi-Agent Runtime + SharedContext | 8d | P1 |
| Supervisor/Aggregator 节点 | 3d | P1 |
| Plan-and-Execute Runtime | 5d | P2 |
| 子图执行 + 可重入 | 6d | P0 |
| Workflow Checkpoint + 断点续跑 | 5d | P0 |
| 自治长程任务（Autonomous） | 8d | P3 |
| **合计** | **35d** | — |

---

## 6. Phase E4（15~24 月）：平台化与商业化

### 6.1 插件市场

#### 6.1.1 目标

第三方可开发插件（Provider / Tool / Memory / Node / Prompt），通过插件市场分发，平台动态加载。

#### 6.1.2 设计

- **插件描述文件**：`plugin.yaml`（类似 VS Code 扩展）
- **插件加载器**：基于 Spring Boot 的 `BeanDefinitionRegistry` 动态注册
- **插件沙箱**：插件代码在独立 ClassLoader，限制权限
- **插件市场**：类似 npm / VS Code Marketplace，支持搜索 / 安装 / 更新 / 评分

### 6.2 沙箱执行

#### 6.2.1 目标

`CodeNodeExecutor` / `SqlNodeExecutor` / 第三方插件在沙箱中执行，防止 RCE / 数据泄漏。

#### 6.2.2 设计

- **GraalVM Polyglot**：JS / Python 执行 + `HostAccess.NONE`
- **WASM Runtime**：第三方插件编译为 WASM，沙箱执行
- **独立容器**：高危节点在 Docker 容器中执行

### 6.3 计费与配额

#### 6.3.1 目标

多租户资源配额 + Token 计费 + API 调用计费。

#### 6.3.2 设计

- `QuotaService`：每租户 / 每用户配额（QPS / Token / 存储）
- `BillingService`：按 Token / API 调用 / 存储计费
- `UsageReport`：用量报表

### 6.4 多租户强化

#### 6.4.1 目标

当前多租户仅到组织标签级，需强化为：
- 租户级数据隔离（DB schema / 行级过滤）
- 租户级配置（每个租户独立 Provider 配置）
- 租户级配额与计费

### 6.5 商业化能力

- **SaaS 部署**：多租户云原生
- **私有化部署**：单租户 On-premise
- **开源版 / 企业版**：功能分级

### 6.6 Phase E4 交付物与工作量

| 交付物 | 工作量 | 优先级 |
|--------|--------|--------|
| 插件描述文件 + 动态加载器 | 8d | P2 |
| 插件沙箱（GraalVM Polyglot） | 5d | P2 |
| Code/SQL 节点沙箱化 | 3d | P1 |
| QuotaService + BillingService | 6d | P2 |
| 多租户强化（配置/配额隔离） | 5d | P2 |
| 插件市场（前端 + 后端） | 10d | P3 |
| SaaS / 私有化部署支持 | 5d | P3 |
| **合计** | **42d** | — |

---

## 7. 演进路线图总览

### 7.1 甘特图（文字版）

```
2026 Q3 (E1)          2026 Q4 - 2027 Q1 (E2)    2027 Q2-Q3 (E3)        2027 Q4 - 2028 (E4)
─────────────────     ──────────────────────     ──────────────────     ─────────────────────
Provider 插件化       RuntimeContext 统一         Multi-Agent            插件市场
Tool 插件化           RuntimeSession 统一        Plan-and-Execute       插件沙箱
Memory 插件化         EventBus + TraceSpan       子图 + 可重入          Code/SQL 沙箱化
Prompt 管道插件化     流式统一                   Checkpoint             计费配额
Retriever 插件化      事件持久化 + Replay        断点续跑               多租户强化
Runtime 插件化        RuntimeStateMachine        自治长程任务           SaaS/私有化
NodeExecutor 巩固     Pause/Resume/Cancel                              
Guardrail 插件化
```

### 7.2 工作量汇总

| 阶段 | 工作量 | 累计 |
|------|--------|------|
| Phase E1（插件化骨架） | 29d | 29d |
| Phase E2（统一 Runtime） | 32d | 61d |
| Phase E3（高级编排） | 35d | 96d |
| Phase E4（平台化） | 42d | 138d |

**总计：~138 人日（约 7 人月，按 1 人开发；3 人团队约 2.5 个月，但实际需考虑联调测试，建议按 6~9 个月规划）**

### 7.3 与主报告 Phase 的关系

| 主报告 Phase | 本报告对应阶段 | 关系 |
|--------------|---------------|------|
| Phase 1（止血） | — | 前置条件（安全不解决，演进无意义） |
| Phase 2（推荐优化） | E1 前置 | God Object 拆分、DTO 层是 E1 的基础 |
| Phase 3（架构升级） | E1 + E2 | Provider 策略化 = E1；Memory 抽象 = E1；统一 Runtime = E2 |
| Phase 4（长期规划） | E3 + E4 | Multi-Agent = E3；模块化 = E4 前置；AI Gateway = E1+E4 |

---

## 8. 关键决策点

### 8.1 决策 1：模块化单体 vs 微服务

**Meta Review 建议**：模块化单体优先，不提前拆微服务。

**本报告认同**：E1~E3 阶段保持模块化单体，E4 根据规模评估是否拆服务。

**实施**：
- E1：`agent` / `workflow` / `rag` / `platform` 拆为独立 Maven Module（同一部署单元）
- E2~E3：模块边界清晰后，按需评估拆服务
- E4：SaaS 场景按租户隔离可能需要拆服务

### 8.2 决策 2：自研 vs 引入框架

**选项 A：自研 Runtime**（本报告路线）
- 优点：完全可控、深度定制、无外部依赖
- 缺点：开发周期长、需自建生态

**选项 B：引入 LangChain4j / Spring AI**
- 优点：成熟生态、社区支持
- 缺点：抽象可能不匹配、锁定框架

**选项 C：混合**（推荐）
- 核心抽象（RuntimeContext / EventBus / TraceSpan）自研
- 周边（Provider / Tool / Memory）可借鉴 LangChain4j 接口设计
- 检索可集成 Qdrant / Milvus 而非自建

**建议**：选 C，自研核心 + 借鉴接口 + 集成成熟组件。

### 8.3 决策 3：Workflow 引擎自研 vs 引入

**选项 A：自研**（现状）
- 优点：简单、可控
- 缺点：缺 Checkpoint / Compensation / 子图，需大量投入

**选项 B：引入 Temporal / Cadence**
- 优点：生产级、Checkpoint / Compensation 完善
- 缺点：引入外部依赖、Java SDK 但非 Spring 原生

**选项 C：引入 Flowable / Camunda**（BPMN 引擎）
- 优点：成熟、可视化
- 缺点：BPMN 范式与 AI Workflow 不完全匹配

**建议**：E1~E2 自研完善（Variable System + Retry + Checkpoint），E3 评估是否引入 Temporal 处理长程任务。AI 短流程用自研，长程批处理用 Temporal。

### 8.4 决策 4：插件机制 vs 内置

**选项 A：全部内置**（现状）
- 优点：简单、无加载复杂度
- 缺点：无法社区化

**选项 B：SPI 插件**（E1 路线）
- 优点：扩展点清晰、可第三方扩展
- 缺点：接口设计成本高

**选项 C：插件市场**（E4 路线）
- 优点：生态化、商业化
- 缺点：沙箱、版本兼容、安全审查成本高

**建议**：E1~E3 走 SPI 插件（选项 B），E4 按需升级为插件市场（选项 C）。

---

## 9. 风险与缓解

| 风险 | 等级 | 缓解 |
|------|------|------|
| 演进周期长（2~3 年），期间技术栈可能过时 | High | E1 优先抽象接口，实现可替换；定期评估新技术 |
| 自研 Runtime 投入大，可能不如 LangChain4j | Medium | 选项 C 混合策略，借鉴接口设计 |
| 插件市场生态难启动（鸡生蛋问题） | High | E4 前先在内部积累 10+ 插件，再开放 |
| 多租户 + 计费复杂度高 | Medium | E4 才引入，E1~E3 保持单租户 |
| 团队规模与工作量不匹配（138 人日） | High | 按 E1→E2→E3→E4 渐进，每阶段独立交付价值 |
| 安全风险（Code/SQL 节点）未解决即演进 | Critical | E1 必须先沙箱化（或主报告 Phase 1 先行） |

---

## 10. 结论

KfSmart 当前是一个**功能完整的 AI 应用**，但要演进为**插件化 AI 平台**，需要系统性的架构升级。

**核心矛盾**：丰富的 AI 能力 vs 硬编码的扩展点。当前新增任何能力都需改源码，无法形成生态。

**演进核心**：将 10 类扩展点（Provider / Tool / Memory / Prompt / Node / Retriever / Runtime / Guardrail / Observer / Skill）统一为 SPI 插件，实现"实现接口 + 自动注册"的扩展模型。

**路线图**：
- **E1（0~3 月）**：插件化骨架 — 6 类 SPI 接口 + 现有实现迁移
- **E2（3~9 月）**：统一 Runtime — RuntimeContext/Session/Event/Trace 统一
- **E3（9~15 月）**：高级编排 — Multi-Agent / Plan-Execute / 子图 / Checkpoint
- **E4（15~24 月）**：平台化 — 插件市场 / 沙箱 / 计费 / 多租户

**关键决策**：
1. 模块化单体优先，不提前拆微服务（认同 Meta Review）
2. 自研核心抽象 + 借鉴 LangChain4j 接口 + 集成成熟组件
3. Workflow 自研完善短期能力，长程任务评估引入 Temporal
4. E1~E3 走 SPI 插件，E4 升级为插件市场

**前提条件**：主报告 Phase 1（安全止血）必须先行——在 Code/SQL 节点沙箱化、密钥外置之前，任何演进都建立在沙滩上。

**最终愿景**：KfSmart 从"一个 AI 应用"演进为"一个 AI 平台"——第三方可在不修改源码的前提下，通过实现 SPI 接口扩展 Provider / Tool / Memory / Node / Prompt，形成生态。这是从"产品"到"平台"的质变，也是 2~3 年规划的核心价值。

---

## 附录：本报告与其他报告的关系

| 主报告章节 | 本报告对应章节 |
|-----------|---------------|
| §3.11 AI Architecture | §1 扩展点分析 + §3 Provider/Tool/Memory 插件化 |
| §3.6 Extensibility | §1 扩展点清单 + §2 SPI 体系 |
| Phase 3（架构升级） | E1 + E2 |
| Phase 4（长期规划） | E3 + E4 |

| AI Runtime 报告章节 | 本报告对应章节 |
|---------------------|---------------|
| §1 Runtime 抽象 | §4.1 统一 Runtime |
| §6 Event 架构 | §4.2 Event Bus |
| §7 Trace 体系 | §4.2 TraceSpan |
| §11 Runtime 演进路线图 | E1~E4 全局 |

| Workflow Runtime 报告章节 | 本报告对应章节 |
|---------------------------|---------------|
| §4 Checkpoint | E3 Checkpoint + 断点续跑 |
| §7 Loop（子图） | E3 子图 + 可重入 |
| §11 Workflow 演进路线图 | E1~E4 全局 |

本报告与 `docs/ai-runtime-review.md` / `docs/workflow-runtime-review.md` 共同构成 KfSmart 的 **AI 平台专项审查体系**，是主报告 `docs/review-report.md` 的演进规划补充。

---

**报告结束。**

> 本报告共 ~4800 字，覆盖扩展点分析、SPI 插件架构、4 阶段演进路线图（E1~E4）、关键决策、风险缓解，是 KfSmart 从"AI 应用"演进为"AI 平台"的 2~3 年战略规划。

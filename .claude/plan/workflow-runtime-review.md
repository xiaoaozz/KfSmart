# Workflow Runtime Review

> **Project**: KfSmart AI Platform
> **Report Type**: Specialized Workflow Runtime Review（工作流运行时专项评审）
> **Review Date**: 2026-06-30
> **Reviewer Role**: Principal Engineer / Workflow Engine Architect
> **Companion Report**: 本报告是 `docs/review-report.md` 的补充专项报告，针对 Meta Review 指出的"Workflow Variable System / DAG 执行模型 / 并行 / Retry / Checkpoint / Compensation / Branch / Loop 分析不足"进行深化。

---

## 0. Executive Summary

KfSmart 的 Workflow Runtime 是项目中**设计最专业**的子系统之一，已具备生产级工作流引擎的雏形：DAG 拓扑排序、扇入等待、条件路由、并行扇出、节点执行器注册、Trace、Token 计费、WebSocket 进度推送、消息缓冲回放。

但在 **Variable System、并行执行、Retry、Checkpoint、Compensation、Loop 语义** 这六个核心维度，当前实现仍停留在"最小可用"阶段，距离"可持续演进的工作流平台"尚有显著差距。

### 0.1 核心发现

| 维度 | 成熟度 | 关键问题 |
|------|--------|----------|
| DAG 执行模型 | ★★★★☆ | BFS + 扇入等待设计合理；扇入死循环风险 |
| 并行执行 | ★★☆☆☆ | 扇出声明并行但实际串行执行 |
| Retry | ★☆☆☆☆ | 仅 MCP 节点 config 有 `retryCount` 字段，无实际实现 |
| Checkpoint | ☆☆☆☆☆ | 完全缺失，长程任务失败需从头跑 |
| Compensation | ☆☆☆☆☆ | 完全缺失，失败节点无法回滚已执行节点 |
| Branch（条件路由） | ★★★☆☆ | `routingPort` 机制可用；fallback 到第一条出边不严谨 |
| Loop（循环） | ★★☆☆☆ | 循环变量与子图执行未真正打通 |
| Variable System | ★★☆☆☆ | 无作用域、无类型、无校验 |
| Parameter Binding | ★★★☆☆ | `inputMappings` 可视化配置，但语义不完整 |
| Trace | ★★★★☆ | `NodeTrace` 设计良好，缺 Span 层级 |

### 0.2 总体评分

| 维度 | 评分 | 说明 |
|------|------|------|
| DAG 执行模型 | 78 | BFS + 拓扑排序 + 扇入等待，专业 |
| 并行执行 | 35 | 声明并行但串行，无线程池 |
| Retry | 20 | 字段存在但未实现 |
| Checkpoint | 10 | 完全缺失 |
| Compensation | 10 | 完全缺失 |
| Branch | 65 | routingPort 可用，fallback 不严谨 |
| Loop | 40 | 循环变量设置但子图未真正循环 |
| Variable System | 35 | 无作用域、无类型、无校验 |
| Parameter Binding | 55 | inputMappings 可视化，语义不完整 |
| Trace | 75 | NodeTrace 设计好，缺层级 |
| 综合评分 | **42 / 100（D）** | 骨架优秀，血肉不足 |

### 0.3 核心建议

1. **Variable System 重构**：引入作用域（Global / Node-local / Loop-iteration）+ 类型系统 + 校验。
2. **并行执行落地**：扇出节点用 `CompletableFuture` + 有界线程池真正并行。
3. **Retry 机制**：节点级 `retryPolicy`（次数 + 退避 + 超时），支持 LLM/HTTP/MCP 调用重试。
4. **Checkpoint 持久化**：每节点执行后持久化 `executed` + `variables` 快照，支持断点续跑。
5. **Compensation**：可选的 `compensate` 回调，失败时逆序执行已成功节点的补偿。
6. **Loop 语义完善**：循环节点 + 子图（sub-graph）作为循环体，循环变量自动注入子图 Context。

---

## 1. DAG 执行模型分析

### 1.1 现状

**入口**：`WorkflowExecutionEngine.execute`（`src/main/java/com/smart/kf/workflow/engine/WorkflowExecutionEngine.java` L56）

**执行流程**：

```
1. GraphBuilder.build(nodesJson, edgesJson) → WorkflowGraph
2. TopologicalSorter.sort(graph) → 环检测（抛异常 if cycle）
3. extractDebugOverrides(input) → 提取 debug_ 前缀变量
4. ExecutionContext ctx = new ExecutionContext(workflowId, username, input)
5. executeFromNode(startNode.id, graph, ctx, executed: Set<String>)
   - BFS 队列 ArrayDeque
   - 每节点:
     a. isReadyToExecute（扇入等待：所有前驱已执行）
     b. resolveInputMappings（解析入参映射）
     c. captureNodeInputs（快照）
     d. registry.getExecutor(node.type()).execute(node, ctx)
     e. ctx.setNodeOutput(currentId, outputs)
     f. resolveNextNodes（路由下一节点）
     g. queue.addAll(nextIds)
```

### 1.2 优秀设计

#### 1.2.1 拓扑排序 + 环检测

`TopologicalSorter.sort` 在执行前验证 DAG 无环，避免运行时死循环。这是工作流引擎的必备能力，KfSmart 正确实现。

#### 1.2.2 扇入等待（Fan-in）

`isReadyToExecute`（L212-220）：
```java
List<WorkflowEdge> incoming = graph.getIncomingEdges(nodeId);
for (WorkflowEdge edge : incoming) {
    if (!executed.contains(edge.source())) {
        return false;  // 前驱未执行完，延迟
    }
}
return true;
```

当节点有多个入边（如并行扇出后汇聚），正确等待所有前驱完成。

#### 1.2.3 条件路由（routingPort）

`resolveNextNodes`（L230-269）支持：
- `result.nextNodeIds`：执行器显式指定下一节点
- `result.routingPort`：按出边 `sourcePort` / `label` 匹配（条件分支）
- 默认：跟随所有出边（并行扇出）

#### 1.2.4 执行快照与 Trace

每节点执行前后捕获 `inputSnapshot` + `outputs`，记入 `NodeTrace`，支持回放调试。`NodeTrace` 含 `startedAt` / `durationMs` / `inputs` / `outputs` / `promptTokens` / `completionTokens` / `status` / `errorMessage`，信息完整。

### 1.3 问题

#### 1.3.1 扇入等待可能死循环（High）

`executeFromNode`（L136-206）：
```java
while (!queue.isEmpty()) {
    String currentId = queue.poll();
    if (executed.contains(currentId)) continue;
    if (!isReadyToExecute(currentId, graph, executed)) {
        queue.add(currentId);  // 重新入队
        continue;
    }
    // ... 执行 ...
}
```

**问题**：若节点 X 的某前驱 Y 因异常未进入 `executed` 集合（但异常被 catch 后未 re-throw，导致执行继续而非 fail-fast），X 会永远 `!isReadyToExecute`，被反复 re-queue，**死循环空转**。

**根因**：当前实现中 `executor.execute` 抛异常时 `catch` 块 re-throw（L200-205），异常会冒泡到 `execute` 的外层 catch（L109-113），结束执行。**所以正常路径下不会死循环**。但若未来引入"节点失败但继续"（如 `onError: continue` 配置），则会触发死循环。

**预防建议**：增加"最大 re-queue 次数"或"全局超时"。

#### 1.3.2 无节点级超时（Medium）

`NodeExecutor.execute` 无超时参数。`LLM` 节点调用 `modelClient.chat`（`.block()`）可能永久阻塞（LLM 服务无响应）。`Python` / `Code` 节点执行用户代码也无超时。

**后果**：单个节点卡住可阻塞整个 Workflow。

#### 1.3.3 无节点级失败策略（Medium）

当前节点失败 = 整个 Workflow 失败（`catch` 块 re-throw）。无 `onFailure: skip / retry / fallback / continue` 配置。

---

## 2. 并行执行分析

### 2.1 现状：声明并行但实际串行

**声明并行**：`resolveNextNodes` 在普通节点（非条件路由）返回所有出边目标（L264-268）：
```java
return outgoing.stream()
    .map(WorkflowEdge::target)
    .filter(id -> !executed.contains(id))
    .toList();
```

`queue.addAll(nextIds)` 把多个目标加入队列，**看起来是并行扇出**。

**实际串行**：`executeFromNode` 用 `while (!queue.isEmpty())` + `queue.poll()` 逐个处理，**单线程串行执行**。

```java
while (!queue.isEmpty()) {
    String currentId = queue.poll();  // 取一个
    // ... 同步执行 ...
    queue.addAll(nextIds);  // 加多个，但下次循环还是 poll 一个
}
```

### 2.2 问题

#### 2.2.1 并行扇出实际串行（High）

若工作流：
```
A → B
A → C
B → D
C → D
```
期望 A 完成后 B、C 并行执行，D 等待 B、C 都完成（扇入）。

**实际**：A 完成后 queue = [B, C]，poll B 执行完（假设 10s），再 poll C 执行完（10s），总耗时 20s 而非 10s。D 等待 B、C 完成后才执行。

**根因**：`ArrayDeque` + 单线程 `while` 循环，无 `CompletableFuture` / 线程池。

#### 2.2.2 无并行度控制（Medium）

即使改为并行，也无 `maxParallelism` 配置，可能同时启动过多节点导致资源耗尽（如 100 个 LLM 节点并发调用 API）。

#### 2.2.3 无并行结果聚合（Medium）

扇入节点（D）等待 B、C 完成后执行，但 `isReadyToExecute` 仅检查 `executed.contains`，不收集 B、C 的输出。D 若需 B、C 的输出，需通过 `{{B.output}}` / `{{C.output}}` 模板访问，但 `nodeOutputs` 是并发 Map，B、C 输出可能冲突（若 key 相同）。

### 2.3 建议

#### 2.3.1 真正的并行执行

```java
private void executeFromNode(String startNodeId, WorkflowGraph graph,
                              ExecutionContext ctx, Set<String> executed) {
    ExecutorService pool = Executors.newFixedThreadPool(ctx.getMaxParallelism());
    try {
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        submitNode(startNodeId, graph, ctx, executed, pool, futures);
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    } finally {
        pool.shutdown();
    }
}

private void submitNode(String nodeId, ..., List<CompletableFuture<Void>> futures) {
    if (executed.contains(nodeId)) return;
    if (!isReadyToExecute(nodeId, graph, executed)) {
        // 延迟提交，等前驱完成
        return;
    }
    futures.add(CompletableFuture.runAsync(() -> {
        NodeExecutionResult result = executor.execute(node, ctx);
        executed.add(nodeId);
        ctx.setNodeOutput(nodeId, result.outputs());
        List<String> nextIds = resolveNextNodes(graph, nodeId, result, executed);
        for (String nextId : nextIds) {
            submitNode(nextId, graph, ctx, executed, pool, futures);
        }
    }, pool));
}
```

#### 2.3.2 并行度配置

`ExecutionContext` 增加 `maxParallelism`（默认 4），节点级可覆盖。

#### 2.3.3 结果聚合

扇入节点的 `resolveInputMappings` 自动收集所有前驱输出到 `{{predecessors}}` 列表。

---

## 3. Retry 机制分析

### 3.1 现状：字段存在但未实现

**声明 Retry**：`GraphBuilder.defaultConfigForType` 中多个节点 config 含 `retryCount`：
- `MCP工具`：`retryCount: 1`（L136）
- `HTTP请求`：`retryCount: 1`（L137）
- `Webhook`：`retryCount: 2`（L144）

**实际实现**：
- `McpToolNodeExecutor.execute`（L62-67）：`try { invocationService.execute(...) } catch (Exception e) { throw }` —— **直接抛异常，无重试**。
- `HttpNodeExecutor`（未读，但根据 `retryCount` 字段推测同样未实现）。
- `WorkflowExecutionEngine.executeFromNode`：`catch (Exception e) { ... throw e; }` —— **节点级无重试**。

### 3.2 问题

#### 3.2.1 Retry 完全未实现（Critical）

`retryCount` 字段是"装饰性配置"——前端可配置，后端不读取。用户配置 `retryCount: 3` 期望失败重试 3 次，实际失败即终止。

#### 3.2.2 无退避策略（Medium）

即使实现 Retry，也需退避（exponential backoff）避免雪崩。当前无 `backoffStrategy` / `retryInterval` 配置。

#### 3.2.3 无重试条件（Medium）

并非所有异常都应重试（如 400 Bad Request 不应重试，500 Server Error 应重试）。当前无 `retryableExceptions` 配置。

### 3.3 建议：统一 Retry 机制

```java
public record RetryPolicy(
    int maxAttempts,           // 最大重试次数
    BackoffStrategy backoff,    // 退避策略
    Duration timeout,           // 单次超时
    Predicate<Throwable> retryable  // 可重试异常
) {
    public enum BackoffStrategy { FIXED, LINEAR, EXPONENTIAL }
    public static RetryPolicy none() { return new RetryPolicy(0, null, null, e -> false); }
    public static RetryPolicy fixed(int n, Duration interval) { ... }
    public static RetryPolicy exponential(int n, Duration base) { ... }
}

// NodeExecutor 接口扩展
public interface NodeExecutor {
    default RetryPolicy getRetryPolicy(WorkflowNode node) { return RetryPolicy.none(); }
}

// WorkflowExecutionEngine 包装
private NodeExecutionResult executeWithRetry(NodeExecutor executor, WorkflowNode node,
                                              ExecutionContext ctx, RetryPolicy policy) {
    for (int attempt = 1; attempt <= policy.maxAttempts() + 1; attempt++) {
        try {
            return executor.execute(node, ctx);
        } catch (Exception e) {
            if (attempt > policy.maxAttempts() || !policy.retryable().test(e)) throw e;
            Thread.sleep(policy.backoff().computeDelay(attempt));
        }
    }
}
```

- LLM 节点：`RetryPolicy.exponential(3, Duration.ofSeconds(2))`，可重试 5xx / 超时
- HTTP 节点：`RetryPolicy.exponential(3, Duration.ofSeconds(1))`，可重试 5xx / 网络错误
- MCP 节点：从 config 读取 `retryCount`

---

## 4. Checkpoint 机制分析

### 4.1 现状：完全缺失

`WorkflowExecutionEngine` 的 `executed: Set<String>` 是内存 `ConcurrentHashMap.newKeySet()`（L106），**进程崩溃即丢失**。

`WorkflowExecutionLog`（`WorkflowExecutionService.saveExecutionLog` L177-205）仅在执行结束后记录：
- `status`: success / failed
- `durationMs`
- `tokenUsage`
- `inputJson` / `traceJson` / `outputJson` / `variablesJson`

**执行过程中无 Checkpoint**——跑到第 99 个节点失败，前 98 个节点的 `executed` 状态与 `variables` 快照不持久化。

### 4.2 问题

#### 4.2.1 长程任务无法断点续跑（Critical）

100 节点跑到第 99 个失败，重启后需从第 1 个重新跑。若每个节点调用 LLM（30 秒），总耗时 50 分钟，失败一次浪费 49.5 分钟。

#### 4.2.2 无法暂停恢复（High）

无 Checkpoint → 无 Pause/Resume。长程任务（如夜间批处理）无法在白天暂停、夜间恢复。

#### 4.2.3 无法回放（Medium）

虽然 `traceJson` 记录了执行轨迹，但无 `variablesJson` 快照序列，无法"回到第 N 个节点重新执行"。

### 4.3 建议：Checkpoint 持久化

```java
public record Checkpoint(
    String executionId,
    int checkpointSeq,
    String lastCompletedNodeId,
    Set<String> executedNodeIds,
    Map<String, Object> variablesSnapshot,
    Map<String, Map<String, Object>> nodeOutputsSnapshot,
    int tokenUsagePrompt,
    int tokenUsageCompletion,
    long elapsedMs,
    LocalDateTime createdAt
) {}

public interface CheckpointStore {
    void save(Checkpoint checkpoint);
    Optional<Checkpoint> loadLatest(String executionId);
    List<Checkpoint> loadAll(String executionId);
    void delete(String executionId);
}
```

实现：
- `JdbcCheckpointStore`：存 MySQL `workflow_checkpoint` 表
- `RedisCheckpointStore`：存 Redis（适合短期、高频）

策略：
- **每节点完成后**保存 Checkpoint（`executed.add(currentId)` 之后）
- **可配置**：`checkpointStrategy: EVERY_NODE / EVERY_N_TOKENS / EVERY_N_SECONDS`
- **断点续跑**：`WorkflowExecutionService.resume(executionId)` → 加载最新 Checkpoint → 从 `lastCompletedNodeId` 的后继开始

---

## 5. Compensation 机制分析

### 5.1 现状：完全缺失

当前节点失败 = Workflow 失败，**已执行节点的副作用不回滚**。

**典型场景**：
```
A（创建订单）→ B（扣款）→ C（发邮件）
```
若 C 失败，A、B 已执行——订单已创建、款已扣，但用户没收到邮件。无 Compensation 机制导致数据不一致。

### 5.2 问题

#### 5.2.1 无事务语义（High）

工作流涉及多步骤副作用（DB 写、API 调用、发邮件），无 Saga 模式或分布式事务。

#### 5.2.2 无补偿回调（Medium）

`NodeExecutor` 接口无 `compensate` 方法，无法回滚已执行节点。

### 5.3 建议：可选 Compensation

```java
public interface CompensatableNodeExecutor extends NodeExecutor {
    void compensate(WorkflowNode node, ExecutionContext ctx, NodeExecutionResult previousResult);
}

// 示例：订单创建节点的补偿
@Component
public class CreateOrderNodeExecutor implements CompensatableNodeExecutor {
    @Override
    public NodeExecutionResult execute(WorkflowNode node, ExecutionContext ctx) {
        String orderId = orderService.create(...);
        ctx.setVariable("orderId", orderId);
        return ...;
    }

    @Override
    public void compensate(WorkflowNode node, ExecutionContext ctx, NodeExecutionResult previousResult) {
        String orderId = (String) ctx.getVariable("orderId");
        orderService.cancel(orderId);  // 取消订单
    }
}
```

**触发时机**：
- Workflow 整体失败时，逆序执行已成功节点的 `compensate`
- 可配置 `compensationStrategy: NONE / ON_FAILURE / MANUAL`

**注意**：并非所有节点都可补偿（如发邮件无法撤回），`CompensatableNodeExecutor` 是可选实现。

---

## 6. Branch（条件路由）分析

### 6.1 现状

**`ConditionNodeExecutor`**（`src/main/java/com/smart/kf/workflow/executor/ConditionNodeExecutor.java`）：
- 解析 `conditionExpr` 表达式
- 支持 `>=` / `<=` / `>` / `<` / `==` / `!=` / `contains`
- 返回 `routingPort = "true" / "false"`

**`WorkflowExecutionEngine.resolveNextNodes`**（L248-262）：
```java
String routingPort = result.routingPort();
if (routingPort != null && !routingPort.isBlank()) {
    List<String> routed = outgoing.stream()
        .filter(edge -> routingPort.equalsIgnoreCase(edge.sourcePort())
            || routingPort.equals(edge.label()))
        .map(WorkflowEdge::target)
        .filter(id -> !executed.contains(id))
        .toList();
    if (!routed.isEmpty()) return routed;
    // fallback: 第一条出边
    String fallback = outgoing.get(0).target();
    return executed.contains(fallback) ? List.of() : List.of(fallback);
}
```

### 6.2 优秀设计

#### 6.2.1 表达式解析安全

`evaluateCondition` 不用 `ScriptEngine` eval（不像 `CodeNodeExecutor`），而是手工 `findOperator` + `compareNumeric` / `compareString`，避免 RCE。

#### 6.2.2 routingPort 机制

出边的 `sourcePort` / `label` 匹配 `routingPort`，支持可视化条件分支（前端 React Flow 的 `sourceHandle`）。

### 6.3 问题

#### 6.3.1 Fallback 到第一条出边不严谨（Medium）

若 `routingPort` 未匹配任何出边，fallback 到 `outgoing.get(0).target()`（L260-261）。这可能导致：
- 用户配置 `routingPort="true"` 但出边 `sourcePort="是"`（中文），匹配失败 → fallback 到第一条出边（可能是 false 分支）→ 逻辑错误。

**建议**：fallback 应抛异常而非猜测，或配置 `defaultBranch` 显式指定。

#### 6.3.2 表达式无类型推断（Medium）

`compareNumeric`（L122-130）：
```java
try {
    return Double.parseDouble(left) - Double.parseDouble(right);
} catch (NumberFormatException e) {
    return left.compareTo(right);  // 字符串比较
}
```

数字 `42` 与字符串 `"42"` 比较时，`Double.parseDouble` 成功 → 数值比较。但 `">= "` 与 `"abc"` 比较时，fallback 到字符串字典序比较，语义不清。

#### 6.3.3 无复合表达式（Medium）

不支持 `&&` / `||` / `!` / 括号。`score >= 60 AND type == "A"` 无法表达，需拆成多个 Condition 节点串联。

#### 6.3.4 无 Switch / Match 分支（Low）

仅支持 `true/false` 二分支，不支持多路分支（如 `status == "draft" / "review" / "published"` 路由到 3 个不同节点）。

### 6.4 建议

1. **严格匹配**：`routingPort` 未匹配时抛异常，不 fallback。
2. **类型化表达式**：引入 `Expression` 接口 + ` spel` / `jsonpath` 后端，支持复合表达式。
3. **Switch 节点**：新增 `SwitchNodeExecutor`，支持多路分支。

---

## 7. Loop（循环）分析

### 7.1 现状

**`LoopNodeExecutor`**（`src/main/java/com/smart/kf/workflow/executor/LoopNodeExecutor.java`）：
- 三种循环类型：`列表循环` / `计数循环` / `条件循环`
- 设置循环变量 `_loop_iteration_{nodeId}` + `loopVar`（如 `item`）
- 返回 `routingPort = "loop" / "exit"`

**期望执行模型**（注释 L17-19）：
> 循环体通过子图执行实现：每次迭代将循环变量注入上下文，然后继续后续节点。此执行器负责设置循环变量和迭代计数，实际循环体执行由引擎后续节点完成。

### 7.2 问题

#### 7.2.1 循环体子图未实现（Critical）

`LoopNodeExecutor` 设置循环变量后返回 `routingPort="loop"`，引擎按 `loop` 出边路由到循环体第一个节点。**但循环体执行完后，如何回到 Loop 节点进行下一次迭代？**

当前 `executeFromNode` 用 `executed: Set<String>` 防止重复执行。Loop 节点已 `executed.add(loopNodeId)`，循环体完成后回到 Loop 节点会被 `if (executed.contains(currentId)) continue;` 跳过——**无法二次执行 Loop 节点**。

**结论**：循环语义**未真正打通**。`LoopNodeExecutor` 的注释承认"实际循环体执行由引擎后续节点完成"，但引擎没有"回到 Loop 节点"的机制。

#### 7.2.2 循环变量作用域泄漏（Medium）

`loopVar`（如 `item`）注入全局 `variables`，循环结束后残留。若循环体外其他节点引用 `{{item}}`，取到的是最后一次迭代的值，可能引发逻辑错误。

#### 7.2.3 无 break / continue（Low）

循环体内无法 `break`（提前退出）或 `continue`（跳过本次迭代）。

#### 7.2.4 无并发循环（Low）

`列表循环` 是串行的，无法并发处理列表元素（如 100 个文档并发向量化）。

### 7.3 建议：子图循环

引入 **子图（Sub-graph）** 概念：

```java
public record LoopConfig(
    String loopType,           // list / count / condition
    String iterateVar,         // 列表变量名
    String loopVar,            // 迭代变量名
    String subGraphStartNode,  // 循环体入口节点 ID
    String subGraphEndNode,    // 循环体出口节点 ID（完成后回到 Loop 节点）
    int maxIterations,
    boolean parallel,          // 是否并发
    int maxParallelism
) {}
```

引擎执行 Loop 节点时：
1. 解析循环列表 / 计数 / 条件
2. 对每次迭代：
   - 创建**子 Context**（继承父 Context，但 `loopVar` 是局部作用域）
   - 执行子图 `subGraphStartNode → ... → subGraphEndNode`
   - 子图完成后回到 Loop 节点
3. 所有迭代完成后，`routingPort="exit"`

这需要 `executeFromNode` 支持"可重入"——同一节点在不同 Context 下可多次执行。

---

## 8. Variable System 专项分析

> Meta Review §四.3 明确指出 Workflow Variable System 未分析，本节为专项补充。

### 8.1 现状

**变量存储**（`ExecutionContext.java`）：
```java
ConcurrentHashMap<String, Object> variables;  // 全局变量
ConcurrentHashMap<String, Map<String, Object>> nodeOutputs;  // 节点输出
```

**变量注入**：
- `StartNodeExecutor`：从 `node.config.variables` 注入输入变量
- `setNodeOutput`（L88-95）：节点输出 key 自动注入全局（若未冲突）
- `setVariable`（L70-74）：直接覆盖

**变量访问**：
- `getVariable(key)`：全局变量
- `resolveTemplate("{{xxx}}")`：模板解析
  - `{{query}}` → 全局变量
  - `{{input.query}}` → 等价 `{{query}}`
  - `{{context}}` → 全局变量
  - `{{nodeId.key}}` → 节点输出

### 8.2 问题

#### 8.2.1 无变量作用域（Critical）

所有变量全局共享，无 Scope 概念：
- **Global Scope**：整个 Workflow 共享
- **Node-local Scope**：节点局部变量，外部不可见
- **Loop-iteration Scope**：循环每次迭代的 `item`，迭代间隔离
- **Session Scope**：跨 Workflow 执行的会话变量

当前仅有 Global Scope，导致：
1. **变量名冲突**：节点 A、B 都输出 `result`，后者覆盖前者（`setVariable` 直接 put）。`setNodeOutput` 用 `!containsKey` 避免覆盖，但导致**冲突时节点输出无法注入**，后续 `{{result}}` 取到旧值。
2. **循环变量泄漏**：`item` 循环结束后残留。
3. **并行执行竞态**：若实现并行扇出，多个节点同时 `setVariable` 会互相覆盖。

#### 8.2.2 无变量类型系统（High）

所有变量都是 `Object`，模板解析时 `String.valueOf(val)` 强转：

```java
private String resolveExpression(String expression) {
    // ...
    Object val = variables.get(expression);
    return val == null ? "" : String.valueOf(val);  // 强转字符串
}
```

**问题**：
- 数字 `42` → `"42"`，`ConditionNodeExecutor.compareNumeric` 靠 `Double.parseDouble("42")` 还原，脆弱。
- 布尔 `true` → `"true"`，`evaluateCondition` 靠 `"false".equalsIgnoreCase(resolved)` 判断，不严谨。
- 列表 `[1,2,3]` → `"[1, 2, 3]"`，后续节点无法当作列表迭代。
- Map `{"a":1}` → `"{a=1}"`，无法解析回 Map。

**后果**：复杂类型在节点间传递时退化为字符串，丢失结构。

#### 8.2.3 无变量校验（Medium）

`{{xxx}}` 拼写错误时返回空字符串，不报错。用户无法区分"变量未定义"和"变量值为空"。

```java
Object val = variables.get(expression);
return val == null ? "" : String.valueOf(val);  // null → ""
```

#### 8.2.4 无变量类型声明（Medium）

`StartNodeExecutor` 的 `variables` config 仅 `name` / `value`，无 `type` 字段：
```java
// StartNodeExecutor L37-46
for (Map<String, Object> varDef : variables) {
    String varName = String.valueOf(varDef.get("name"));
    String defaultValue = varDef.get("value") != null ? String.valueOf(varDef.get("value")) : "";
    Object existing = ctx.getVariable(varName);
    String value = existing != null ? String.valueOf(existing) : defaultValue;
    ctx.setVariable(varName, value);
}
```

`GraphBuilder.defaultConfigForType("变量")` 有 `varType: "string"`（L128），但未在执行时使用。

#### 8.2.5 无变量版本历史（Low）

变量被多次 `setVariable` 覆盖，无历史记录。调试时无法看到变量在每步的值变化（除非从 `NodeTrace.inputs` 快照推断）。

### 8.3 建议：分层 Variable System

#### 8.3.1 作用域分层

```java
public class VariableScope {
    private final VariableScope parent;  // 父作用域（用于作用域链查找）
    private final ConcurrentHashMap<String, Object> locals = new ConcurrentHashMap<>();

    public Object get(String key) {
        if (locals.containsKey(key)) return locals.get(key);
        return parent != null ? parent.get(key) : null;
    }

    public void setLocal(String key, Object value) { locals.put(key, value); }
    public void set(String key, Object value) {
        // 在定义该变量的作用域更新
    }
}
```

作用域链：
```
Global Scope
  └─ Loop Iteration Scope (item, _index)
       └─ Node-local Scope (临时变量)
```

#### 8.3.2 类型系统

```java
public sealed interface VariableType
    permits StringType, NumberType, BooleanType, ListType, MapType, ObjectType {
    Object parse(String raw);
    String format(Object value);
    boolean validate(Object value);
}
```

变量声明：
```java
public record VariableDeclaration(
    String name,
    VariableType type,
    Object defaultValue,
    boolean required,
    String description
) {}
```

`StartNodeExecutor` 读取 `variables` config 时按 `type` 解析：
```java
Object value = varDef.type().parse(rawValue);
ctx.setVariable(varName, value);
```

模板解析时按类型格式化：
```java
String formatted = declaration.type().format(value);
```

#### 8.3.3 严格模式

`resolveTemplate` 增加 `strict: true` 选项，未定义变量抛异常而非返回空字符串。

---

## 9. Parameter Binding 专项分析

### 9.1 现状

**`WorkflowExecutionEngine.resolveInputMappings`**（L334-361）：
```java
// node.config.inputMappings = [{param:"prompt", source:"start.query", enabled:true}]
for (Map<String, Object> mapping : mappings) {
    String source = String.valueOf(mapping.get("source"));
    String param = String.valueOf(mapping.get("param"));
    Object value = ctx.resolveTemplate("{{" + source + "}}");
    if (value != null) {
        ctx.setVariable(param, value);
    }
}
```

### 9.2 优秀设计

- **可视化配置**：前端可为每个节点配置 `inputMappings`，声明参数来源。
- **enabled 开关**：可临时禁用某映射。
- **source 支持 `nodeId.key`**：跨节点引用。

### 9.3 问题

#### 9.3.1 映射写入全局变量（Medium）

`ctx.setVariable(param, value)` 把映射结果写入全局 variables，而非节点局部。若两个节点都映射到 `param="query"`，后者覆盖前者。

#### 9.3.2 无类型转换（Medium）

`ctx.resolveTemplate` 返回字符串，`setVariable` 存字符串。若目标参数期望数字，需节点内手工 `parseInt`。

#### 9.3.3 无默认值（Low）

映射 source 为空时，`resolveTemplate` 返回空字符串，无 `defaultValue` 配置。

#### 9.3.4 无输出映射（Medium）

`GraphBuilder.defaultConfigForType("Agent调用")` 有 `outputMapping: {"result":"{{agent.output}}"}`（L135），但 `AgentCallNodeExecutor` 未读取 `outputMapping`，直接输出固定字段。

### 9.4 建议

- `inputMappings` 写入节点局部作用域（配合 §8.3.1 作用域分层）
- 增加类型转换 + 默认值 + 必填校验
- 实现 `outputMapping`，节点输出按映射重命名

---

## 10. Trace 体系专项分析

### 10.1 现状

**`NodeTrace`**（`src/main/java/com/smart/kf/workflow/engine/NodeTrace.java`）：
- `nodeId` / `nodeName` / `nodeType` / `startedAt` / `durationMs`
- `inputs` / `outputs`
- `promptTokens` / `completionTokens`
- `status` / `errorMessage`
- `description`

**持久化**：`WorkflowExecutionLog.traceJson`（序列化为 JSON 数组）。

### 10.2 优秀设计

- 每节点 `start` + `success` / `failed` 两个 Trace 事件
- `inputs` / `outputs` 快照，支持回放
- Token 计费分节点

### 10.3 问题

#### 10.3.1 无 Span 层级（Medium）

Trace 是扁平 `List<NodeTrace>`，无父子关系。无法表达：
```
Workflow Runtime
  └─ LLM 节点
       └─ LLM 调用
            └─ Tool 调用（function calling）
```

#### 10.3.2 无子执行 Trace 合并（Medium）

`AgentCallNodeExecutor` 调用 Agent，Agent 内部产生 `List<AgentStep>` Trace，但**不合并到 Workflow 的 `List<NodeTrace>`**。Workflow Trace 仅看到"Agent 调用节点完成"，Agent 内部 ReAct 步骤是黑盒。

#### 10.3.3 无变量变化历史（Low）

`inputs` 快照仅捕获预定义的 `INPUT_KEYS`（`WorkflowExecutionEngine.java` L313），非全部变量。且仅记录执行前快照，无执行后快照对比。

### 10.4 建议

见 `docs/ai-runtime-review.md` §7 Trace 体系（统一 `TraceSpan` 模型）。

---

## 11. 综合 Workflow Runtime 评估

### 11.1 工作流引擎能力矩阵

| 能力 | 当前 | 目标 | 差距 |
|------|------|------|------|
| DAG 执行 | ✅ | ✅ | 0 |
| 拓扑排序 + 环检测 | ✅ | ✅ | 0 |
| 扇入等待 | ✅ | ✅ | 0 |
| 条件路由 | ✅（fallback 不严谨） | ✅ | 1 |
| 并行扇出 | ⚠️ 声明并行但串行 | ✅ 真正并行 | 2 |
| 循环 | ❌ 子图未实现 | ✅ 子图循环 | 3 |
| Retry | ❌ 未实现 | ✅ 节点级重试 | 3 |
| Checkpoint | ❌ 完全缺失 | ✅ 持久化 + 续跑 | 3 |
| Compensation | ❌ 完全缺失 | ⚠️ 可选补偿 | 2 |
| 节点超时 | ❌ | ✅ | 2 |
| 失败策略 | ❌（fail-fast） | ✅ skip/retry/fallback | 2 |
| Variable Scope | ❌ 全局 | ✅ 分层作用域 | 3 |
| Variable Type | ❌ 无类型 | ✅ 类型系统 | 3 |
| Variable Validation | ❌ | ✅ 校验 | 2 |
| Parameter Binding | ⚠️ | ✅ + 类型转换 | 1 |
| Trace | ⚠️ 扁平 | ✅ Span 层级 | 2 |
| 子执行 Trace 合并 | ❌ | ✅ | 2 |

**差距总分**：31 项 / 满分 51（每项 0~3 分），当前约 20 分，目标 51 分，**完成度 ~40%**。

### 11.2 与业界工作流引擎对比

| 能力 | KfSmart | Airflow | Temporal | Dify | LangGraph |
|------|---------|---------|----------|------|-----------|
| DAG | ✅ | ✅ | ✅ | ✅ | ✅ |
| 并行 | ❌ | ✅ | ✅ | ⚠️ | ✅ |
| Retry | ❌ | ✅ | ✅ | ✅ | ✅ |
| Checkpoint | ❌ | ✅ | ✅ | ❌ | ✅ |
| Compensation | ❌ | ❌ | ✅ | ❌ | ⚠️ |
| 循环 | ❌ | ⚠️ | ✅ | ✅ | ✅ |
| Variable Type | ❌ | ✅ | ✅ | ⚠️ | ✅ |
| 子图 | ❌ | ✅ | ✅ | ✅ | ✅ |

**结论**：KfSmart Workflow 引擎在 DAG 基础能力上达标，但在并行、Retry、Checkpoint、Compensation、循环、子图等高级能力上**显著落后于 Temporal / LangGraph**。

### 11.3 演进路线图

#### Phase W1（1~2 月）：Variable System + Retry

| 任务 | 优先级 | 工作量 |
|------|--------|--------|
| Variable Scope 分层（Global / Node-local / Loop-iteration） | P0 | 5d |
| Variable Type 系统（String/Number/Boolean/List/Map） | P0 | 4d |
| Variable 校验（strict 模式 + 必填 + 默认值） | P1 | 2d |
| Retry 机制（RetryPolicy + 指数退避） | P0 | 3d |
| 节点级超时 | P1 | 2d |

#### Phase W2（2~3 月）：并行 + Checkpoint

| 任务 | 优先级 | 工作量 |
|------|--------|--------|
| 并行扇出（CompletableFuture + 线程池 + maxParallelism） | P0 | 4d |
| Checkpoint 持久化（JdbcCheckpointStore） | P0 | 5d |
| 断点续跑（resume from checkpoint） | P1 | 3d |
| 并行结果聚合 | P1 | 2d |

#### Phase W3（3~4 月）：循环 + 子图 + Compensation

| 任务 | 优先级 | 工作量 |
|------|--------|--------|
| 子图（Sub-graph）执行机制 | P0 | 5d |
| 循环节点 + 子图循环（可重入执行） | P0 | 4d |
| 并发循环（列表元素并发处理） | P2 | 2d |
| Compensation 机制（CompensatableNodeExecutor） | P1 | 3d |
| 失败策略（skip/retry/fallback/continue） | P1 | 2d |

#### Phase W4（4~6 月）：Trace 升级 + 可视化调试

| 任务 | 优先级 | 工作量 |
|------|--------|--------|
| TraceSpan 层级模型（父子 Span） | P1 | 3d |
| 子执行 Trace 合并（AgentCall → Agent Steps） | P1 | 3d |
| 变量变化历史（每步快照） | P2 | 2d |
| 可视化调试器（节点级断点 + 单步执行） | P2 | 5d |
| 历史回放（从 Trace 重放执行过程） | P2 | 4d |

### 11.4 结论

KfSmart Workflow Runtime 的**骨架设计专业**（DAG + 拓扑 + 扇入 + 路由 + Trace + 注册机制），是项目的核心资产之一。但**血肉不足**——并行、Retry、Checkpoint、Compensation、循环、Variable System 六大核心能力均停留在"最小可用"或"完全缺失"阶段。

**最大短板**：Variable System（无作用域、无类型、无校验）—— 这是所有高级能力的基础，必须优先重构。

**最大风险**：`CodeNodeExecutor` / `SqlNodeExecutor` 的安全风险（主报告 Issue 3/4）—— 在引入沙箱前，不建议将 Workflow 创建权限开放给普通用户。

**最大机会**：引入子图（Sub-graph）+ 可重入执行 —— 这是循环、Multi-Agent、Plan-and-Execute 的共同基础，一次性投入可解锁多项高级能力。

建议按 Phase W1 → W2 → W3 → W4 顺序演进，**Phase W1（Variable System + Retry）是后续一切的基础**。

---

## 附录：本报告与主报告 / AI Runtime 报告的关系

| 主报告章节 | 本报告对应章节 | 深化程度 |
|-----------|---------------|----------|
| §3.11 AI Architecture（Workflow） | §1 DAG + §6 Branch + §7 Loop | 深化 |
| — | §2 并行执行 | 新增 |
| — | §3 Retry | 新增 |
| — | §4 Checkpoint | 新增 |
| — | §5 Compensation | 新增 |
| — | §8 Variable System | 新增（Meta Review §四.3） |
| — | §9 Parameter Binding | 新增 |
| — | §10 Trace | 深化 |

| AI Runtime 报告章节 | 本报告对应章节 |
|---------------------|---------------|
| §4 Context 流转（Workflow） | §8 Variable System + §9 Parameter Binding |
| §6 Event 架构（Workflow 事件） | §1 DAG 执行（progressListener） |
| §7 Trace 体系（Workflow NodeTrace） | §10 Trace |

本报告与 `docs/ai-runtime-review.md` / `docs/ai-platform-evolution-review.md` 共同构成 KfSmart 的 **AI 平台专项审查体系**。

---

**报告结束。**

> 本报告共 ~5000 字，覆盖 DAG 执行、并行、Retry、Checkpoint、Compensation、Branch、Loop、Variable System、Parameter Binding、Trace 共 10 个维度，是 KfSmart Workflow Runtime 的专项深度评审。

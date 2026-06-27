# KfSmart 前后端接口规范

> 本文档是 KfSmart 项目前后端接口的长期规范文档，供所有开发成员在设计、实现和联调时遵守。
> 新增接口、修改接口或发现不一致时，请同步更新本文档。

---

## 一、基础约定

| 项目 | 约定 |
|---|---|
| BaseURL | `VITE_API_BASE_URL ?? '/api/v1'`，前端所有路径均相对于此前缀 |
| 响应信封 | `{ code: number, message: string, data: T }` — 前端 interceptor 自动解包为 `.data` |
| 蛇形转驼峰 | response interceptor 对所有响应执行 `deepCamel`，后端无需处理命名风格 |
| Token 注入 | request interceptor 读 `localStorage['kf-auth'].state.token`，注入 `Authorization: Bearer` |
| Token 刷新 | 401 时自动调 `POST /auth/refreshToken`，原请求进入队列等待重放 |
| 权限码 | 来自 `GET /users/me` 的 `permissions[]`，由 `usePermission` hook 读取 |

---

## 二、URL 设计规范

**标准 REST 结构（必须遵守）**

```
GET    /resources           — 列表
GET    /resources/{id}      — 单条
POST   /resources           — 创建
PUT    /resources/{id}      — 全量更新
PATCH  /resources/{id}      — 部分更新
DELETE /resources/{id}      — 删除
```

**禁止**在路径中使用动词（`/list`、`/get`、`/remove`、`/update`、`/query`）。

子资源操作示例：
```
POST /resources/{id}/publish     — 对某条资源执行动作
GET  /resources/{id}/histories   — 获取子资源列表
```

---

## 三、请求 / 响应规范

**分页参数**（统一，禁止混用 `limit` / `rows` / `offset`）

```json
{ "current": 1, "size": 20 }
```

后端返回：

```json
{
  "records": [],
  "total": 100,
  "current": 1,
  "size": 20,
  "pages": 5
}
```

**排序参数**

```json
{ "sortBy": "createTime", "sortOrder": "desc" }
```

**标准响应格式**

```json
{ "code": 200, "message": "success", "data": {} }
```

**错误码约定**

| Code | 含义 |
|---|---|
| 200 | 成功 |
| 400 | 请求参数错误 |
| 401 | 未认证（Token 缺失或过期）|
| 403 | 权限不足 |
| 404 | 资源不存在 |
| 409 | 冲突（如重复注册）|
| 429 | 请求过于频繁 |
| 500 | 服务器内部错误 |

---

## 四、接口兼容矩阵

> 每次联调结束后更新此表。✅ 已对齐 | ⚠ 待处理 | ❌ 缺失

| 模块 | 前端 | 后端 | 状态 |
|---|---|---|---|
| Auth（登录 / 注册 / 刷新 / 登出）| ✅ | ✅ | 已对齐 |
| Auth（邮箱登录 + 注册验证码）| ✅ | ✅ | 已对齐 |
| Conversation（会话 + 消息）| ✅ | ✅ | 已对齐 |
| Document（列表 / 删除 / 解析 / 下载）| ✅ | ✅ | 已对齐 |
| Upload（分片上传 / 合并 / 状态查询）| ✅ | ✅ | 已对齐 |
| Profile（个人信息 / 收藏 / 操作记录）| ✅ | ✅ | 已对齐 |
| Skill / Prompt / MCP / Model | ✅ | ✅ | 已对齐 |
| Admin（用户 CRUD + 重置密码）| ✅ | ✅ | 已对齐 |
| Knowledge Base | ✅ | ✅ | 已对齐 |
| Workflow（执行 / 执行历史）| ✅ | ✅ | 已对齐 |
| Agent（chat 执行）| ✅ | ✅ | 已对齐 |

---

## 五、Breaking Change 记录

> 所有影响多个调用方的接口变更必须记录在此，并标注影响范围和迁移方式。

### 2026-06-27 Document 标识符迁移

| 变更 | 旧值 | 新值 |
|---|---|---|
| 文档主键类型 | `id: number` | `fileMd5: string` |
| 删除 | `DELETE /documents/:id` | `DELETE /documents/{fileMd5}` |
| 下载 | `GET /documents/:id/download` | `GET /documents/download?fileName=xxx` |
| 重解析 | `POST /documents/:id/reparse` | `POST /parse?file_md5=xxx` |

影响范围：ChunkedUploader、DocPreviewDrawer、DocumentListPage。迁移策略：前端 `Document` 类型同时保留 `id`（展示用）和 `fileMd5`（接口调用用）。

---

## 六、联调完成标准（Definition of Done）

新增或修改接口完成联调，必须满足以下所有检查项：

### Auth
- [ ] 用户名 / 邮箱均可登录
- [ ] Token 存入 Zustand store，页面跳转正常
- [ ] 401 时自动刷新 Token 并重放请求
- [ ] 登出后 Token 失效，跳转登录页
- [ ] 注册：邮箱验证码正确才能建账号，邮箱重复返回 409

### Conversation / Chat
- [ ] 新建会话、获取列表、删除、置顶
- [ ] WebSocket 握手成功（需先获取 `cmdToken`）
- [ ] 流式消息正常渲染，结束标识正确
- [ ] 网络中断后可重连

### Upload / Document
- [ ] 分片上传 → 状态轮询 → 合并 → 文档列表可见
- [ ] 下载、删除、重解析均正常
- [ ] 大文件（>50MB）分片不丢包

### 全局回归（每次发版前必跑）
```bash
pnpm typecheck   # 0 TypeScript 错误
pnpm lint        # 0 ESLint 错误
pnpm build       # 构建成功
mvn compile -q   # 后端 0 编译错误
```

---

## 七、DTO 分层策略

后端新增接口时，禁止直接将 JPA Entity 作为响应体返回。应使用独立 DTO 类：

```
Controller  →  Service  →  Repository (Entity)
                ↓
           ResponseDTO  →  前端 TypeScript interface
```

优点：
- Entity 字段变更不影响 API 合约
- 可精确控制哪些字段对外暴露（如 `password` 永远不出现在响应中）
- 统一 snake_case → camelCase 转换点

---

## 八、WebSocket 协议规范

### 连接流程

```
1. 前端调 GET /chat/websocket-token  →  获取 cmdToken（5 分钟有效）
2. 建立 WS 连接，握手时携带 cmdToken
3. 发送消息帧（JSON）
4. 接收流式响应帧，直到收到结束标识
```

### 消息格式（待完善）

建议单独维护 `websocket-contract.md`，明确：消息类型枚举、心跳机制（ping/pong 间隔）、Token 过期处理、重连退避策略、流式结束标识格式。

---

## 九、邮箱功能接口说明

### 9.1 邮箱登录

`POST /users/login` 的 `username` 字段同时接受**用户名**和**邮箱地址**。

后端查找顺序：先精确匹配用户名，再匹配邮箱（均 case-insensitive）。
无论哪个字段不匹配，统一返回 `"用户名或密码错误"`，不区分原因。

### 9.2 发送注册验证码

```
POST /users/send-email-code   （公开，无需认证）

请求体：{ "email": "xxx@qq.com" }
响应：  { "code": 200, "message": "验证码已发送，请查收邮件" }
限速：  同一邮箱 60 秒内只能发送一次，超出返回 429
有效期：5 分钟
SMTP：  QQ 邮箱 smtp.qq.com:465 SSL
```

### 9.3 注册（含邮箱验证）

```
POST /users/register

请求体：
{
  "username":  "zhangsan",
  "password":  "Pass1234!",
  "email":     "zhangsan@qq.com",
  "emailCode": "381924"        ← 必填，6 位数字，来自 9.2
}
```

后端先验证 OTP，通过后创建账号，邮箱以小写形式存入 `User.email`。

### 9.4 Redis Key 规范（邮件 OTP）

| Key | 值 | TTL | 用途 |
|---|---|---|---|
| `email:code:{email}` | 6 位数字 | 5 min | OTP 本体（验证后立即删除）|
| `email:code:limit:{email}` | `"1"` | 60 s | 发送频率限制 |

### 9.5 SMTP 配置

编辑 `src/main/resources/application.yml`，填入真实 QQ 邮箱信息后重启后端：

```yaml
spring:
  mail:
    username: your_qq@qq.com       # 真实 QQ 邮箱地址
    password: your_smtp_authcode   # QQ 邮箱授权码（非 QQ 登录密码）
```

开启方式：QQ 邮箱 → 设置 → 账户 → POP3/IMAP/SMTP 服务 → 开启 → 生成授权码。

---

## 十、联调原则

1. **前端优先对齐**：路径、参数名不一致时，优先改前端，保持后端稳定性
2. **禁止修改已上线接口**：后端缺失端点时新增，不修改已有端点的路径和响应结构
3. **接口变更必须更新文档**：修改任何接口后同步更新本文档及接口兼容矩阵
4. **Breaking Change 必须记录**：所有影响多个调用方的变更记录到第五章
5. **逐模块验证**：每完成一个模块立即联调，不积压到最后统一验证

---

*文档创建：2026-06-27 | 最后更新：2026-06-27*

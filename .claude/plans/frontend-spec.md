# KfSmart AI Platform — 前端规范文档

> **用途**：本文档是前端的技术规范与功能现状记录。新功能开发、重构、技术决策均须同步至此文档。

---

## 目录

1. [技术选型](#技术选型)
2. [目录结构](#目录结构)
3. [设计令牌系统](#设计令牌系统)
4. [状态管理规范](#状态管理规范)
5. [路由与权限](#路由与权限)
6. [HTTP 层规范](#http-层规范)
7. [组件规范](#组件规范)
8. [Feature Flag](#feature-flag)
9. [错误处理体系](#错误处理体系)
10. [测试规范](#测试规范)
11. [性能预算与监控](#性能预算与监控)
12. [工程规范](#工程规范)
13. [已实现功能清单](#已实现功能清单)
14. [Architecture Decision Records](#architecture-decision-records)
15. [Release Checklist](#release-checklist)

---

## 技术选型

| 层 | 选择 | 说明 |
|---|---|---|
| 框架 | React 19 + TypeScript 6 + Vite 8 | 函数组件 + Hooks |
| UI 底座 | Ant Design 6.x | ConfigProvider + CSS 变量模式（`cssVar: true`）|
| CSS 系统 | CSS Custom Properties | antd v5/v6 原生支持，自定义变量同层管理 |
| 服务端状态 | React Query（@tanstack/react-query） | 接口数据、缓存、loading/error 统一管理 |
| 客户端状态 | Zustand | token、主题、侧栏等纯前端状态 |
| 路由 | React Router v6 | `createBrowserRouter` + lazy + 路由守卫 |
| HTTP | Axios | 统一拦截器 + Token 自动刷新队列 |
| 实时通信 | 原生 WebSocket | 封装 `useWebSocket` Hook |
| 工作流编辑器 | @xyflow/react（React Flow） | 节点图编辑，参考 Dify 设计 |
| 动画 | Framer Motion | 入场动画 + 持续动画 |
| 字体 | Google Fonts | Inter + Calistoga + JetBrains Mono |
| Markdown | react-markdown + remark-gfm | AI 回复渲染 |
| 代码高亮 | react-syntax-highlighter | |
| 测试 | Vitest + React Testing Library + Playwright | 单测 + 集成 + E2E |
| 工程规范 | ESLint + Prettier + Husky + lint-staged | 提交前自动检查 |
| 包管理 | pnpm 8 | |

---

## 目录结构

```
frontend/
├── index.html                      # Google Fonts + meta
├── vite.config.ts
├── tsconfig.json
├── .eslintrc.cjs
├── .prettierrc
├── .husky/pre-commit
└── src/
    ├── main.tsx                    # initErrorMonitor() → render → initVitals()
    ├── App.tsx                     # ConfigProvider + QueryClient + RouterProvider
    ├── config/
    │   └── features.ts             # Feature Flag 统一管理
    ├── api/
    │   ├── http.ts                 # axios 实例 + 拦截器
    │   ├── mappers/                # 特殊字段手动映射
    │   ├── auth.ts
    │   ├── user.ts
    │   ├── knowledge-base.ts
    │   ├── document.ts
    │   ├── chat.ts
    │   ├── agent.ts
    │   ├── workflow.ts
    │   ├── skill.ts
    │   ├── resource.ts
    │   ├── notification.ts
    │   └── admin.ts
    ├── assets/
    ├── components/
    │   ├── base/                   # 原子级：无业务依赖
    │   │   ├── GradientText.tsx
    │   │   ├── SectionBadge.tsx
    │   │   ├── GradientButton.tsx
    │   │   ├── GradientCard.tsx
    │   │   ├── ThemeSwitch.tsx
    │   │   └── AppLogo.tsx
    │   ├── layout/                 # 布局相关
    │   │   ├── PageHeader.tsx
    │   │   ├── MotionList.tsx
    │   │   └── NotificationBell.tsx
    │   ├── business/               # 通用业务组件
    │   │   ├── PageTable.tsx
    │   │   ├── SearchForm.tsx
    │   │   ├── ModalForm.tsx
    │   │   ├── PermissionButton.tsx
    │   │   ├── LoadingOverlay.tsx
    │   │   ├── EmptyState.tsx
    │   │   ├── ErrorRetry.tsx
    │   │   └── ErrorBoundary.tsx
    │   └── workflow/               # 工作流专用
    │       ├── nodes/
    │       └── edges/
    ├── hooks/
    │   ├── useTheme.ts
    │   ├── useWebSocket.ts
    │   ├── useAuth.ts
    │   ├── usePermission.ts
    │   └── useNotification.ts
    ├── layouts/
    │   ├── AuthLayout.tsx          # 左品牌区 + 右表单区
    │   └── BasicLayout.tsx         # antd Layout + Sider + Header
    ├── monitoring/
    │   ├── errors.ts               # 自定义错误监控，sendBeacon 上报
    │   └── vitals.ts               # Web Vitals 采集
    ├── router/
    │   └── index.tsx               # createBrowserRouter + PermissionRoute
    ├── stores/
    │   ├── auth.ts                 # token, refreshToken（持久化）
    │   ├── theme.ts                # isDark（持久化）
    │   ├── layout.ts               # siderCollapsed
    │   └── ui.ts                   # 全局 modal/overlay 状态
    ├── styles/
    │   ├── variables.css           # 完整 CSS 变量（light + dark）
    │   ├── global.css
    │   └── antd-override.css
    ├── theme/
    │   └── antd.ts                 # getAntdTheme(isDark): ThemeConfig
    ├── types/
    │   ├── api.ts                  # PageResult<T>, ApiResponse<T>
    │   ├── auth.ts
    │   ├── user.ts
    │   ├── knowledge-base.ts
    │   ├── document.ts
    │   ├── chat.ts
    │   ├── agent.ts
    │   ├── workflow.ts
    │   └── admin.ts
    └── pages/
        ├── error/                  # 403, 404, 500
        ├── auth/                   # LoginPage, RegisterPage
        ├── dashboard/
        ├── knowledge-base/         # KbListPage, KbDetailPage
        ├── document/
        ├── chat/                   # ChatPage, ChatSidebar, MessageBubble
        ├── agent/                  # AgentListPage, AgentEditorPage, AgentExecutionPage
        ├── workflow/               # WorkflowListPage, WorkflowEditorPage
        ├── skill/
        ├── resource/               # PromptListPage, McpToolPage, ModelConfigPage
        ├── profile/
        └── admin/                  # UserManage, RoleManage, OrgTag, SystemStatus, ApiKey, ActivityLog
```

---

## 设计令牌系统

### CSS 变量（`src/styles/variables.css`）

所有颜色、阴影、圆角、字体均通过 CSS 变量管理，组件直接引用变量，**禁止硬编码颜色值**。

Light / Dark 两套变量定义在同一文件中：

```css
:root, [data-theme="light"] {
  --kf-accent: #0052FF;
  --kf-accent-secondary: #4D7CFF;
  --kf-accent-gradient: linear-gradient(135deg, #0052FF, #4D7CFF);
  --kf-accent-gradient-r: linear-gradient(to right, #0052FF, #4D7CFF);
  --kf-bg: #FAFAFA;
  --kf-card: #FFFFFF;
  --kf-muted: #F1F5F9;
  --kf-foreground: #0F172A;
  --kf-muted-foreground: #64748B;
  --kf-accent-foreground: #FFFFFF;
  --kf-border: #E2E8F0;
  --kf-ring: #0052FF;
  --kf-sidebar-bg: #FFFFFF;
  --kf-sidebar-border: #E2E8F0;
  --kf-sidebar-item-hover: #F1F5F9;
  --kf-sidebar-item-active-bg: rgba(0, 82, 255, 0.08);
  --kf-sidebar-item-active-text: #0052FF;
  --kf-shadow-sm: 0 1px 3px rgba(0,0,0,0.06);
  --kf-shadow-md: 0 4px 6px rgba(0,0,0,0.07);
  --kf-shadow-lg: 0 10px 15px rgba(0,0,0,0.08);
  --kf-shadow-xl: 0 20px 25px rgba(0,0,0,0.10);
  --kf-shadow-accent: 0 4px 14px rgba(0,82,255,0.25);
  --kf-shadow-accent-lg: 0 8px 24px rgba(0,82,255,0.35);
  --kf-font-display: "Calistoga", Georgia, serif;
  --kf-font-ui: "Inter", system-ui, sans-serif;
  --kf-font-mono: "JetBrains Mono", monospace;
  --kf-radius-sm: 8px;
  --kf-radius-md: 12px;
  --kf-radius-lg: 16px;
  --kf-radius-xl: 24px;
}

[data-theme="dark"] {
  --kf-accent: #4D7CFF;
  --kf-accent-secondary: #6B93FF;
  --kf-bg: #0A0A0F;
  --kf-card: #141420;
  --kf-muted: #1A1A2E;
  --kf-foreground: #F0F4FF;
  --kf-muted-foreground: #8892AA;
  --kf-border: #252540;
  --kf-sidebar-bg: #0F0F1A;
  /* ... 完整 dark 变量见 variables.css */
}
```

### antd ConfigProvider Token（`src/theme/antd.ts`）

```typescript
export const getAntdTheme = (isDark: boolean): ThemeConfig => ({
  cssVar: true,
  algorithm: isDark ? antdTheme.darkAlgorithm : antdTheme.defaultAlgorithm,
  token: {
    colorPrimary: isDark ? '#4D7CFF' : '#0052FF',
    colorBgBase: isDark ? '#0A0A0F' : '#FAFAFA',
    fontFamily: '"Inter", system-ui, sans-serif',
    borderRadius: 8, borderRadiusLG: 12, borderRadiusSM: 6,
    // ...
  },
})
```

主题切换通过 `useTheme` hook，写入 `document.documentElement.dataset.theme`，antd ConfigProvider 随 `isDark` 状态重渲染。

---

## 状态管理规范

### Zustand — 仅存客户端状态

| Store | 存储内容 | 持久化 |
|---|---|---|
| `auth` | token, refreshToken | ✅ localStorage（key: `kf-auth`） |
| `theme` | isDark | ✅ localStorage |
| `layout` | siderCollapsed | ❌ 内存 |
| `ui` | 全局 Modal 开关、loading overlay | ❌ 内存 |

### React Query — 仅存服务端数据

所有来自接口的数据（user 信息、知识库列表、对话历史、Agent 列表等）全部用 React Query 管理。

> **禁止将接口返回数据放入 Zustand。**

---

## 路由与权限

### 路由结构

```
/login, /register            → AuthLayout
/403, /404, /500             → 无需 Layout

/                            → BasicLayout（requiresAuth）
  /dashboard
  /chat, /chat/:sessionId
  /knowledge-bases, /knowledge-bases/:kbId
  /documents
  /agents, /agents/new, /agents/:id/edit, /agents/:id/executions
  /workflows, /workflows/new, /workflows/:id/edit
  /skills, /skills/:id/edit
  /resources/prompts, /resources/mcp, /resources/models
  /profile
  /admin/*                   → requiresAdmin（system:admin 权限）
```

所有页面组件用 `React.lazy` 懒加载。

### 权限体系

```typescript
PermissionRoute      // 路由级：未登录跳 /login，无权限跳 /403
PermissionButton     // 按钮级：无权限自动隐藏或禁用（props: permission, mode: 'hide'|'disable'）
AccessControl        // 任意 JSX 级别的权限包裹
usePermission()      // Hook：hasPermission('kb:write')
```

权限码来自 `/users/me` 返回，存入 React Query 缓存，`usePermission` hook 读取。

---

## HTTP 层规范

### axios 实例（`src/api/http.ts`）

- `baseURL` 读 `import.meta.env.VITE_API_BASE_URL`
- Request interceptor：自动注入 `Authorization: Bearer <token>`
- Response interceptor：
  - 统一解包 `data.data`（后端 `ApiResponse<T>` 外层）
  - snake_case → camelCase 转换
  - 4xx/5xx：统一 `message.error`
- 401 处理：Token 刷新队列（并发 401 只发一次刷新请求）

```typescript
let refreshPromise: Promise<string> | null = null

// response interceptor 中：
if (error.response?.status === 401) {
  if (!refreshPromise) {
    refreshPromise = refreshToken().finally(() => { refreshPromise = null })
  }
  const newToken = await refreshPromise
  // 重放原请求
}
```

### API 文件规范

- 文件位于 `src/api/`，按业务域分文件（`auth.ts`、`knowledge-base.ts` 等）
- 每个方法返回 `.then(r => r.data)` 解包，类型明确声明
- DTO 类型定义在 `src/types/` 目录，`src/api/` 只引用类型，不重复定义

### DTO / snake_case 处理

后端字段可能为 snake_case（如 `create_time`），统一在 axios interceptor 层转驼峰，页面层只使用 camelCase。特殊字段（如日期格式化）放 `src/api/mappers/`。

---

## 组件规范

### 组件分层

| 目录 | 用途 | 约束 |
|---|---|---|
| `components/base/` | 原子组件（GradientButton、GradientCard 等） | 无业务逻辑，无 API 依赖 |
| `components/layout/` | 布局组件（PageHeader、NotificationBell 等） | 可依赖路由，不依赖具体业务 |
| `components/business/` | 通用业务组件（PageTable、ModalForm 等） | 跨页面复用，参数化 |
| `components/workflow/` | 工作流专用节点/边组件 | 仅 WorkflowEditorPage 使用 |
| `pages/` | 页面组件 | 每个路由一个目录 |

### 通用业务组件

```
PageTable       // 带分页、搜索、列配置的通用表格
SearchForm      // 筛选表单（支持折叠）
ModalForm       // 带 Form 的弹窗（create/edit 复用）
PermissionButton// 权限按钮（无权限自动 disabled/hidden）
LoadingOverlay  // 全屏 loading 遮罩
EmptyState      // 空状态（带图标 + 文案 + 可选操作按钮）
ErrorRetry      // 错误 + 重试按钮
ErrorBoundary   // 捕获渲染错误，显示 fallback UI
```

### 样式规范

- CSS 变量优先（`var(--kf-accent)`），**禁止 inline style 硬编码颜色**
- 复杂样式用 CSS Module（`.module.css`），简单布局用 inline style（仅 layout 属性）
- 全局 message/notification 统一用 `App.useApp()` 获取，**不直接调用 antd 静态方法**

---

## Feature Flag

统一管理未完成或灰度功能（`src/config/features.ts`）：

```typescript
export const FEATURE = {
  upload:   true,
  agent:    true,
  workflow: true,
  skill:    true,
  admin:    true,
} as const
```

使用方式：`{FEATURE.workflow && <WorkflowMenu />}`

每次新功能开发时，先在此文件添加开关（默认 `false`），功能就绪后改为 `true`。**不直接删除条目。**

---

## 错误处理体系

| 场景 | 处理方式 |
|---|---|
| JS 渲染错误 | `ErrorBoundary` 组件包裹页面，显示 fallback UI |
| 403 无权限 | 路由守卫跳 `/403` 页 |
| 404 路由不存在 | `*` 路由匹配跳 `/404` 页 |
| 500 服务器错误 | axios interceptor 统一 toast + 上报 |
| 网络离线 | 监听 `offline` 事件 + 全局 Banner 提示 |
| 超时 | axios timeout 配置，统一提示 |
| 接口 loading | React Query 的 `isLoading` + `Skeleton` |
| 接口 empty | 统一 `<EmptyState>` 组件 |
| 接口 error | 统一 `<ErrorRetry>` 组件（含重试按钮） |

---

## 测试规范

### 单元 / 集成测试（Vitest + RTL）

- 配置：`vitest.config.ts`，`environment: jsdom`，`setupFiles: src/test/setup.ts`
- 覆盖范围：
  - `src/stores/__tests__/`：auth、theme、layout store 单测
  - `src/components/business/__tests__/`：ErrorBoundary、PermissionButton
- 运行：`npx vitest run`（从 `frontend/` 目录），当前 25 个测试全部通过

### E2E（Playwright）

- 配置：`playwright.config.ts`
- Spec 文件：`e2e/auth.spec.ts`（5 tests）、`e2e/navigation.spec.ts`（5 tests）
- 运行：`npx playwright test`

### Mock 规范

- 单测中 hook mock 用 `vi.mocked(useHook).mockImplementation(...)` 模式
- **不 mock 数据库**（见 api-spec.md 集成测试原则）

---

## 性能预算与监控

### 性能预算

| 指标 | 目标 |
|------|------|
| 首屏加载时间 | ≤ 2s |
| 首次 JS（gzip） | ≤ 500KB |
| 路由切换时间 | ≤ 300ms |
| Chat 首次响应（前端） | ≤ 1s |
| Lighthouse Performance | ≥ 90 |
| Lighthouse Accessibility | ≥ 90 |

### 监控

- **错误监控**（`src/monitoring/errors.ts`）：捕获 `window.error` + `unhandledrejection`，debounce 2000ms 批量 `sendBeacon` 上报 `/api/metrics/errors`
- **Web Vitals**（`src/monitoring/vitals.ts`）：采集 CLS/FCP/INP/LCP/TTFB，生产环境 `sendBeacon → /api/metrics/vitals`
- **Bundle 分析**：`ANALYZE=true pnpm build` 生成 `bundle-stats.html`（`rollup-plugin-visualizer`）

### CI/CD

- `frontend-ci.yml`：PR/push（frontend/** 路径）→ typecheck → lint → test → build
- `backend-ci.yml`：PR/push（src/**, pom.xml）→ `mvn clean verify`
- `release.yml`：semver tag → 并行前后端构建 → Docker push ghcr.io

---

## 工程规范

### 代码规范

- ESLint（typescript-eslint + react-hooks）+ Prettier
- Husky pre-commit：`cd frontend && pnpm lint-staged`
- **注释**：仅在 WHY 非显而易见时添加，不写 WHAT 注释

### 技术债管理

```typescript
// TODO: 待实现功能
// FIXME: 已知 bug，需修复
// DEPRECATED: 即将移除的代码
// REFACTOR: 需要重构的逻辑
```

每个迭代版本至少处理 1 项技术债。

### 长期维护原则

1. 始终保持一个可演示的 MVP 版本
2. 客户端状态与服务端状态严格分离（Zustand / React Query）
3. 统一权限、错误处理、Loading 和通知机制，禁止在各页面独立实现
4. 优先复用 `components/business/` 公共组件，避免重复开发
5. Feature Flag 控制灰度功能，不以"功能未完成"阻塞发布
6. ADR 记录每一次重要架构决策

---

## 已实现功能清单

> 最后更新：2026-06-27

### 认证

| 功能 | 文件 | 备注 |
|------|------|------|
| 登录（用户名/邮箱 + 密码） | `pages/auth/LoginPage.tsx` | 支持用户名或邮箱登录 |
| 注册（含邮箱 OTP 验证） | `pages/auth/RegisterPage.tsx` | 60s 倒计时，`Form.useForm()` 控制 |
| Token 刷新 | `api/http.ts` | 并发安全，刷新队列 |
| 登录态持久化 | `stores/auth.ts` | localStorage `kf-auth` |

### 核心功能

| 功能 | 页面 | 备注 |
|------|------|------|
| 仪表盘 | `pages/dashboard/DashboardPage.tsx` | 统计卡片 + 时间线 + 快捷入口 |
| 知识库管理 | `KbListPage` / `KbDetailPage` | 完整 CRUD，网格卡片 |
| AI 对话 | `ChatPage` + `MessageBubble` | WebSocket 流式输出，Markdown 渲染 |
| 文档管理 | `DocumentListPage.tsx` | 分片上传（Web Worker + 断点续传） |
| Agent 管理 | `AgentListPage` / `AgentEditorPage` / `AgentExecutionPage` | 实时预览聊天 |
| 工作流编辑器 | `WorkflowListPage` / `WorkflowEditorPage` | React Flow 可视化，节点拖拽 |
| 技能库 | `SkillListPage` / `SkillEditorPage` | 分类 Tab，测试 Modal |
| 共享资源 | `PromptListPage` / `McpToolPage` / `ModelConfigPage` | |
| 个人中心 | `ProfilePage.tsx` | 资料/密码/通知/收藏/操作记录/登录记录 |
| 管理后台 | `admin/` 目录 6 个页面 | 需 `system:admin` 权限 |

### 工程基础

| 功能 | 状态 |
|------|------|
| 主题切换（明/暗） | ✅ |
| CSS 变量令牌系统 | ✅ |
| 权限路由守卫 | ✅ |
| PermissionButton 权限按钮 | ✅ |
| ErrorBoundary | ✅ |
| Vitest 单测（25 个） | ✅ |
| Playwright E2E Spec | ✅ |
| GitHub Actions CI | ✅ |
| 错误监控 + Web Vitals | ✅ |
| nginx 生产配置 + Dockerfile | ✅ |

### WebSocket Chat 协议

```json
// 发送
{ "type": "chat", "conversationId": "...", "message": "...", "knowledgeBaseId": "..." }
// 接收（流式）
{ "type": "chunk", "content": "..." }
{ "type": "done",  "conversationId": "..." }
{ "type": "error", "message": "..." }
```

WebSocket 连接流程：GET token → 连接 `ws://.../chat/{token}` → 心跳 30s → 断线指数退避重连。

---

## Architecture Decision Records

ADR 文件位于 `docs/architecture/adr/`，格式：

```markdown
# ADR-XXX 标题
## 状态：已采用 / 已废弃
## 背景
## 决策
## 放弃的方案
## 影响
```

已落地的 ADR：

| 编号 | 标题 |
|------|------|
| ADR-001 | 为什么选 React Query 而不是 Redux Toolkit Query |
| ADR-002 | 为什么选 Zustand 而不是 Redux |
| ADR-003 | 为什么采用 Feature-by-page 目录结构 |
| ADR-004 | 为什么选择 Ant Design |

新增重要架构决策时，必须同步写入 ADR 文件。

---

## Release Checklist

每次发布前逐项确认：

**代码质量**
- [ ] ESLint 无错误
- [ ] TypeScript 无错误
- [ ] Prettier 已格式化

**功能验证**
- [ ] 登录/注册正常（含邮箱 OTP）
- [ ] 权限路由守卫正常
- [ ] API 联调正常
- [ ] Chat WebSocket 正常
- [ ] Feature Flag 开关状态与发布计划一致

**构建验证**
- [ ] `pnpm build` 成功
- [ ] 环境变量（`.env.prod`）正确
- [ ] 路由直接访问不 404（nginx SPA fallback 配置）

**发布动作**
- [ ] 更新 `package.json` 版本号
- [ ] 更新 `CHANGELOG.md`
- [ ] 打 Git Tag（`git tag v{version}`）
- [ ] 发布 Release Notes

---

*最后更新：2026-06-27*

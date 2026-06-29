# ADR-003 路由方案：React Router v6

## 状态
已采用

## 背景
需要支持嵌套路由（BasicLayout 内的页面路由）、懒加载、基于权限的路由守卫。

## 决策
使用 React Router v6 的 `createBrowserRouter` API，配合 `React.lazy` 实现按需加载。

## 放弃的方案
- **TanStack Router**：类型安全更强，但生态尚不成熟，学习曲线较陡
- **Next.js 文件路由**：本项目后端已有 Spring Boot，不需要 SSR，引入 Next.js 是过度工程

## 路由守卫实现
```tsx
// PermissionRoute 组件包裹需要鉴权的路由
// 未登录 → /login
// 无权限 → /403
// 通过 → 渲染子路由
```

## 懒加载策略
- 所有页面组件均用 `React.lazy(() => import('./pages/...'))` 包裹
- 配合 `Suspense` + `LoadingOverlay` 占位
- `manualChunks` 在 vite.config.ts 中拆分 vendor

## 影响
- History 模式需要服务器配置 fallback 到 `index.html`（Nginx: `try_files $uri /index.html`）
- 路由定义集中在 `src/router/index.tsx`，单一数据源

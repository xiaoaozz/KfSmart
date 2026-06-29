# ADR-001 服务端状态管理：@tanstack/react-query

## 状态
已采用

## 背景
项目有大量来自后端 API 的数据（知识库、文档、对话历史、Agent 等），需要统一管理 loading/error 状态、数据缓存和自动刷新。

## 决策
使用 `@tanstack/react-query` 管理所有服务端状态。

## 放弃的方案
- **Redux Toolkit Query（RTK Query）**：与 RTK 生态绑定紧，样板代码多，且项目不使用 Redux 体系
- **SWR**：功能与 React Query 类似，但 React Query 的 DevTools、`useInfiniteQuery`、`useMutation` 等 API 更完善
- **手写 useState + useEffect**：无缓存、无自动失效、无并发请求去重，维护成本高

## 职责划分规则
- React Query：存储所有来自接口的数据（user、知识库、对话等）
- Zustand：只存 token、主题、侧栏折叠等纯前端状态
- **禁止将接口返回数据手动同步到 Zustand**

## 影响
- 接口数据自动缓存，相同 queryKey 不重复请求
- 全局 QueryClientProvider 需在 App.tsx 根层注入
- DevTools 仅在 development 模式加载

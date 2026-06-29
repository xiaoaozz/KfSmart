# ADR-004 项目目录结构

## 状态
已采用

## 背景
需要在可维护性、可扩展性和查找效率之间取得平衡，同时满足单人开发和潜在多人协作的需求。

## 决策
采用 **按职责分层（Layer-first）** 结合 **功能页面集中（Feature pages）** 的混合结构：

```
src/
├── api/           # HTTP 请求层（按模块文件）
├── components/    # 组件（按粒度分 base/layout/business/workflow）
├── config/        # Feature Flag 等静态配置
├── hooks/         # 自定义 Hooks
├── layouts/       # 应用布局
├── pages/         # 页面组件（按业务模块分目录）
├── router/        # 路由配置
├── stores/        # Zustand stores
├── styles/        # 全局样式
├── theme/         # antd ConfigProvider token
└── types/         # TypeScript 类型
```

## 放弃的方案
- **Feature-first（每个模块一个文件夹，含 components/hooks/api/store）**：对于本项目规模过于繁琐，跨模块复用组件时路径混乱
- **完全扁平化 components/**：随着组件增多难以区分业务组件与通用组件

## 组件分层规则
| 目录 | 原则 |
|---|---|
| `components/base/` | 无业务依赖，可移植到任意 React 项目 |
| `components/layout/` | 与应用布局相关，不含业务逻辑 |
| `components/business/` | 封装通用业务模式（PageTable、ModalForm 等） |
| `components/workflow/` | 工作流编辑器专用节点/边组件 |

## 影响
- `@/` 别名指向 `src/`，避免相对路径地狱
- 新增业务模块时：`src/api/` 加文件 → `src/types/` 加类型 → `src/pages/` 加页面目录

# ADR-002 客户端状态管理：Zustand

## 状态
已采用

## 背景
项目需要管理少量全局客户端状态：JWT token、主题（明/暗）、侧栏折叠、全局 Modal 开关等。这些状态与后端无关，需要跨组件共享并持久化到 localStorage。

## 决策
使用 Zustand 管理客户端状态，搭配 `persist` 中间件做本地持久化。

## 放弃的方案
- **Redux / Redux Toolkit**：对于本项目规模的客户端状态，Redux 样板代码（action/reducer/selector）过多，引入成本不值得
- **React Context**：频繁更新会触发全树重渲染，性能差；不内置持久化支持
- **Jotai / Recoil**：原子化状态对本项目复杂度是过度设计

## Store 列表
| Store | 内容 |
|---|---|
| `auth` | token, refreshToken |
| `theme` | isDark |
| `layout` | siderCollapsed |
| `ui` | 全局 loading overlay、modal 状态 |

## 影响
- 每个 store 独立文件，不混合业务数据
- persist 中间件统一用 localStorage，key 前缀 `kf-`

import type { AxiosResponse } from 'axios'
import { http } from './http'

const data = <T>(r: AxiosResponse<T>) => r.data

// ---- shared ----
interface PageResult<T> {
  records: T[]
  total: number
  current: number
  size: number
}

// ---- User management ----
export interface OrgTagRef {
  tagId: string
  name: string
}

export interface AdminUser {
  id: number
  username: string
  email?: string
  avatar?: string
  role?: string
  /** 0=管理员, 1=普通用户（来自列表接口 status 字段） */
  status?: number
  /** RBAC 角色列表（新版列表接口返回） */
  roles?: { roleCode: string; roleName: string }[]
  orgTags: OrgTagRef[]
  primaryOrg?: string
  permissions?: string[]
  createTime?: string
  createdAt?: string
  lastLoginTime?: string
}

// ---- Role / Permission ----
/** Matches backend Permission entity */
export interface Permission {
  id: number
  permCode: string
  permName: string
  resourceType: string
  action: string
  description?: string
}

/** Matches backend Role entity */
export interface Role {
  id: number
  roleCode: string
  roleName: string
  description?: string
  isSystem: boolean
  permissions: { permCode: string; permName: string }[]
}

// ---- Org Tag ----
export interface OrgTag {
  tagId: string
  name: string
  description?: string
  parentTag?: string | null
  children?: OrgTag[]
}

export interface TagI18n {
  lang: string
  name?: string
  description?: string
}

// ---- System Status ----
export type ServiceStatus = 'up' | 'down' | 'degraded'
export type SystemStatus = 'normal' | 'warning' | 'error'
export type AlertLevel = 'warning' | 'error' | 'critical'

export interface SystemOverview {
  status: SystemStatus
  uptime: number
  hostname: string
  ipAddress: string
  osName: string
  javaVersion: string
  appVersion: string
  lastUpdated: string
}

export interface SystemAlert {
  id: string
  level: AlertLevel
  title: string
  message: string
  time: string
}

export interface SystemMetrics {
  overview: SystemOverview
  cpu: { usage: number; cores: number; loadAvg: number }
  memory: {
    systemTotal: number
    systemUsed: number
    jvmHeapUsed: number
    jvmHeapMax: number
    jvmNonHeapUsed: number
  }
  disk: { used: number; total: number; path: string }
  /** @deprecated 保留以兼容旧代码，请使用 memory 字段 */
  jvm: { heapUsed: number; heapMax: number; nonHeapUsed: number; uptime: number }
  online: { onlineUsers: number; activeConnections: number }
  db: {
    activeConnections: number
    maxConnections: number
    queryCount: number
    status: ServiceStatus
    latencyMs: number
  }
  cache: {
    hitRate: number
    keyCount: number
    memoryUsed: number
    status: ServiceStatus
    latencyMs: number
  }
  services: Array<{ name: string; status: ServiceStatus; latencyMs: number }>
  alerts: SystemAlert[]
}

// ---- AI Model Config (API Key Management) ----
export interface AiModelConfig {
  id: number
  name: string
  provider: string
  apiUrl: string
  apiKey: string // masked in list response
  modelName: string
  active: boolean
  temperature?: number
  maxTokens?: number
  topP?: number
  authType?: string
  remark?: string
  createdAt?: string
  updatedAt?: string
}

// ---- Activity Log ----
export interface AdminActivityLog {
  id: string
  type: string
  icon?: string
  title: string
  description: string
  occurredAt: string
  timestamp: number
  color?: string
}

// ===================== API =====================

export const adminUserApi = {
  /** 列表：后端 /admin/users/list 支持 keyword/orgTag/status(Integer)/page/size 筛选 */
  list(params: { page?: number; size?: number; keyword?: string; status?: number }) {
    return http.get<PageResult<AdminUser>>('/admin/users/list', { params }).then(data)
  },
  /** 更新：后端 PUT /admin/users/{id} 仅接受 username/email/role（legacy 角色） */
  update(id: number, payload: { username?: string; email?: string; role?: string }) {
    return http.put(`/admin/users/${id}`, payload).then(data)
  },
  /** 分配组织标签：独立端点 PUT /admin/users/{id}/org-tags */
  assignOrgTags(id: number, orgTags: string[]) {
    return http.put(`/admin/users/${id}/org-tags`, { orgTags }).then(data)
  },
  /** 获取用户 RBAC 角色列表 */
  getRoles(id: number) {
    return http.get<Role[]>(`/admin/users/${id}/roles`).then(data)
  },
  /** 分配用户 RBAC 角色 */
  assignRoles(id: number, roleCodes: string[]) {
    return http.put(`/admin/users/${id}/roles`, { roleCodes }).then(data)
  },
  resetPassword(id: number) {
    return http.post<{ newPassword: string }>(`/admin/users/${id}/reset-password`).then(data)
  },
  delete(id: number) {
    return http.delete<void>(`/admin/users/${id}`).then(data)
  },
}

export const adminRoleApi = {
  list() {
    return http.get<Role[]>('/admin/roles').then(data)
  },
  permissions() {
    return http.get<Permission[]>('/admin/permissions').then(data)
  },
  /** 创建角色（不含权限，需再调 update 分配权限） */
  create(payload: { roleCode: string; roleName: string; description?: string }) {
    return http.post<{ code: string; message: string }>('/admin/roles', payload).then(data)
  },
  /** 更新角色名称/描述/权限（permCodes 为全量替换） */
  update(id: number, payload: { roleName?: string; description?: string; permCodes?: string[] }) {
    return http.put<{ code: string; message: string }>(`/admin/roles/${id}`, payload).then(data)
  },
  delete(id: number) {
    return http.delete<void>(`/admin/roles/${id}`).then(data)
  },
}

export const adminOrgApi = {
  tree() {
    return http.get<OrgTag[]>('/admin/org-tags/tree').then(data)
  },
  create(payload: { tagId: string; name: string; description?: string; parentTag?: string }) {
    return http.post<OrgTag>('/admin/org-tags', payload).then(data)
  },
  update(
    tagId: string,
    payload: { name?: string; description?: string; parentTag?: string | null },
  ) {
    return http.put<OrgTag>(`/admin/org-tags/${tagId}`, payload).then(data)
  },
  delete(tagId: string) {
    return http.delete<void>(`/admin/org-tags/${tagId}`).then(data)
  },
  getI18n(tagId: string) {
    return http.get<TagI18n[]>(`/admin/org-tags/${tagId}/i18n`).then(data)
  },
  upsertI18n(tagId: string, payload: TagI18n) {
    return http.put<TagI18n>(`/admin/org-tags/${tagId}/i18n`, payload).then(data)
  },
}

export const adminSystemApi = {
  metrics() {
    return http.get<SystemMetrics>('/admin/system/metrics').then(data)
  },
  clearCache() {
    return http.post<{ deletedKeys: number }>('/admin/system/clear-cache').then(data)
  },
}

export const adminApiKeyApi = {
  list() {
    return http.get<AiModelConfig[]>('/admin/api-keys').then(data)
  },
  create(payload: {
    name: string
    provider: string
    apiUrl: string
    apiKey: string
    modelName: string
    authType?: string
    remark?: string
  }) {
    return http.post<{ id: number }>('/admin/api-keys', payload).then(data)
  },
  update(
    id: number,
    payload: {
      name?: string
      provider?: string
      apiUrl?: string
      apiKey?: string
      modelName?: string
      authType?: string
      temperature?: number
      maxTokens?: number
      topP?: number
      remark?: string
    },
  ) {
    return http.put<void>(`/admin/api-keys/${id}`, payload).then(data)
  },
  delete(id: number) {
    return http.delete<void>(`/admin/api-keys/${id}`).then(data)
  },
  activate(id: number) {
    return http.post<void>(`/admin/api-keys/${id}/activate`).then(data)
  },
}

export const adminLogApi = {
  list(params: {
    current?: number
    size?: number
    keyword?: string
    action?: string
    status?: string
    startTime?: string
    endTime?: string
  }) {
    return http.get<PageResult<AdminActivityLog>>('/admin/activity-logs', { params }).then(data)
  },
}

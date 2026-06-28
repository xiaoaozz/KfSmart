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
  /** 0=普通用户, 1=管理员（来自列表接口） */
  status?: number
  orgTags: OrgTagRef[]
  primaryOrg?: string
  permissions?: string[]
  createTime?: string
  createdAt?: string
  lastLoginTime?: string
}

// ---- Role / Permission ----
export interface Permission {
  key: string
  label: string
  group: string
}

export type PermissionString = string | { permCode: string; permName: string }

export interface Role {
  id: number
  name: string
  description?: string
  permissions: PermissionString[]
  userCount: number
  createTime: string
}

// ---- Org Tag ----
export interface OrgTag {
  id: number
  tagId?: string
  name: string
  code: string
  description?: string
  parentId?: number
  children?: OrgTag[]
  userCount: number
  createTime: string
}

export interface TagI18n {
  lang: string
  name?: string
  description?: string
}

// ---- System Status ----
export interface SystemMetrics {
  jvm: { heapUsed: number; heapMax: number; nonHeapUsed: number; uptime: number }
  cpu: { usage: number; cores: number; loadAvg: number }
  disk: { used: number; total: number; path: string }
  db: { activeConnections: number; maxConnections: number; queryCount: number }
  cache: { hitRate: number; keyCount: number; memoryUsed: number }
  services: Array<{ name: string; status: 'up' | 'down' | 'degraded'; latencyMs: number }>
}

// ---- API Key ----
export interface ApiKey {
  id: number
  name: string
  keyPrefix: string // first 8 chars, e.g. "kf_12345..."
  scopes: string[]
  status: 'active' | 'disabled' | 'expired'
  lastUsedTime?: string
  expiresAt?: string
  createTime: string
}

// ---- Activity Log ----
export interface AdminActivityLog {
  id: number
  userId: number
  username: string
  action: string
  resource: string
  resourceId?: number
  detail: string
  ip: string
  status: 'success' | 'failed'
  createTime: string
}

// ===================== API =====================

export const adminUserApi = {
  /** 列表：后端 /admin/users/list 支持 keyword/orgTag/status(Integer)/page/size 筛选 */
  list(params: { page?: number; size?: number; keyword?: string; status?: number }) {
    return http.get<PageResult<AdminUser>>('/admin/users/list', { params }).then(data)
  },
  /** 更新：后端 PUT /admin/users/{id} 仅接受 username/email/role */
  update(id: number, payload: { username?: string; email?: string; role?: string }) {
    return http.put(`/admin/users/${id}`, payload).then(data)
  },
  /** 分配组织标签：独立端点 PUT /admin/users/{id}/org-tags */
  assignOrgTags(id: number, orgTags: string[]) {
    return http.put(`/admin/users/${id}/org-tags`, { orgTags }).then(data)
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
  create(payload: { name: string; description?: string; permissions: string[] }) {
    return http.post<Role>('/admin/roles', payload).then(data)
  },
  update(id: number, payload: { name?: string; description?: string; permissions?: string[] }) {
    return http.put<Role>(`/admin/roles/${id}`, payload).then(data)
  },
  delete(id: number) {
    return http.delete<void>(`/admin/roles/${id}`).then(data)
  },
}

export const adminOrgApi = {
  tree() {
    return http.get<OrgTag[]>('/admin/org-tags/tree').then(data)
  },
  create(payload: { name: string; code: string; description?: string; parentId?: number }) {
    return http.post<OrgTag>('/admin/org-tags', payload).then(data)
  },
  update(id: number, payload: { name?: string; code?: string; description?: string }) {
    return http.put<OrgTag>(`/admin/org-tags/${id}`, payload).then(data)
  },
  delete(id: number) {
    return http.delete<void>(`/admin/org-tags/${id}`).then(data)
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
}

export const adminApiKeyApi = {
  list() {
    return http.get<ApiKey[]>('/admin/api-keys').then(data)
  },
  create(payload: { name: string; scopes: string[]; expiresAt?: string }) {
    return http.post<ApiKey & { fullKey: string }>('/admin/api-keys', payload).then(data)
  },
  update(id: number, payload: { name?: string; status?: string; scopes?: string[] }) {
    return http.put<ApiKey>(`/admin/api-keys/${id}`, payload).then(data)
  },
  delete(id: number) {
    return http.delete<void>(`/admin/api-keys/${id}`).then(data)
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

import type { AxiosResponse } from 'axios'
import { http } from './http'
import type { UserInfo } from '@/types/user'

const data = <T>(r: AxiosResponse<T>) => r.data

/** 通知偏好设置 — 与后端 defaultNotificationPreferences() 键名一致 */
export interface NotificationSettings {
  systemAlert: boolean
  newMessage: boolean
  knowledgeUpdate: boolean
  uploadComplete: boolean
  mentionMe: boolean
  weeklyReport: boolean
  emailDigest: boolean
  browserPush: boolean
}

/** 收藏条目 — 与后端 buildFavoriteData() 字段一致 */
export interface Favorite {
  id: number
  type: string
  targetId: string
  title: string
  desc?: string
  meta?: string
  starred: boolean
  createdAt: string
  updatedAt?: string
}

export interface AddFavoriteParams {
  type: string
  targetId: string
  title: string
  description?: string
  meta?: string
  starred?: boolean
}

export interface ActivityLog {
  id: string
  type: string
  action: string
  detail: string
  ip: string
  device: string
  time: string
  status: 'success' | 'failed'
}

/** 登录记录条目 — 与后端 getLoginRecords() 字段一致 */
export interface LoginRecord {
  id: number
  username: string
  loginTime: string
  ipAddress: string
  deviceInfo: string
  location?: string
  status: string
  failReason?: string
}

/** getLoginRecords 返回结构 */
export interface LoginRecordPage {
  content: LoginRecord[]
  totalElements: number
  totalPages: number
  size: number
  number: number
}

/** getLoginStats 返回结构 */
export interface LoginStats {
  totalLogins: number
  successLogins: number
  failedLogins: number
  recentRecords: LoginRecord[]
}

/** getUsageStats 返回结构（days=7 或 30） */
export interface UsageStats {
  totalConversations: number
  todayConversations: number
  totalDocuments: number
  todayUploads: number
  knowledgeBaseCount: number
  weekActiveDays: number
  totalStorage: number
  favoriteCount: number
  usageTrends: Array<{ date: string; label: string; questions: number }>
  topKnowledgeBases: Array<{ kbId: string; name: string; count: number }>
  featureUsage: Array<{ label: string; count: number; value: number; color: string }>
  rangeDays: number
}

export const profileApi = {
  getMe() {
    return http.get<UserInfo>('/users/me').then(data)
  },

  updateProfile(payload: { email?: string; phone?: string; bio?: string }) {
    return http.put<Partial<UserInfo>>('/users/me', payload).then(data)
  },

  uploadAvatar(file: File) {
    const form = new FormData()
    form.append('file', file)
    return http
      .post<{ avatar: string }>('/users/me/avatar', form, {
        headers: { 'Content-Type': 'multipart/form-data' },
      })
      .then(data)
  },

  changePassword(payload: { oldPassword: string; newPassword: string }) {
    return http.put<void>('/users/me/password', payload).then(data)
  },

  getNotifications() {
    return http.get<NotificationSettings>('/users/notification-preferences').then(data)
  },

  updateNotifications(payload: Partial<NotificationSettings>) {
    return http.put<NotificationSettings>('/users/notification-preferences', payload).then(data)
  },

  getFavorites() {
    return http.get<Favorite[]>('/users/favorites').then(data)
  },

  addFavorite(payload: AddFavoriteParams) {
    return http.post<Favorite>('/users/favorites', payload).then(data)
  },

  updateFavoriteStarred(id: number, starred: boolean) {
    return http.put<Favorite>(`/users/favorites/${id}/starred`, { starred }).then(data)
  },

  removeFavorite(id: number) {
    return http.delete<void>(`/users/favorites/${id}`).then(data)
  },

  getActivityLogs() {
    return http.get<ActivityLog[]>('/users/operation-records').then(data)
  },

  getLoginRecords(params?: { page?: number; size?: number }) {
    return http.get<LoginRecordPage>('/users/login-records', { params }).then(data)
  },

  getLoginStats() {
    return http.get<LoginStats>('/users/login-stats').then(data)
  },

  getUsageStats(days: 7 | 30 = 7) {
    return http.get<UsageStats>('/users/usage-stats', { params: { days } }).then(data)
  },
}

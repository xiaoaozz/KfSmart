import type { AxiosResponse } from 'axios'
import { http } from './http'
import type { UserInfo } from '@/types/user'

const data = <T>(r: AxiosResponse<T>) => r.data

export interface NotificationSettings {
  emailOnNewDoc: boolean
  emailOnKbUpdate: boolean
  emailOnAgentFinish: boolean
  browserPush: boolean
  weeklyReport: boolean
}

export interface Favorite {
  id: number
  resourceType: 'knowledge_base' | 'document' | 'agent' | 'workflow' | 'prompt'
  resourceId: number
  resourceName: string
  resourceDesc?: string
  createTime: string
}

export interface ActivityLog {
  id: number
  action: string
  detail: string
  ip: string
  createTime: string
}

export interface LoginRecord {
  id: number
  ip: string
  device: string
  location?: string
  status: 'success' | 'failed'
  createTime: string
}

export const profileApi = {
  getMe() {
    return http.get<UserInfo>('/users/me').then(data)
  },

  updateProfile(payload: { username?: string; email?: string; avatar?: string }) {
    return http.put<UserInfo>('/users/me', payload).then(data)
  },

  uploadAvatar(file: File) {
    const form = new FormData()
    form.append('file', file)
    return http
      .post<{ url: string }>('/users/me/avatar', form, {
        headers: { 'Content-Type': 'multipart/form-data' },
      })
      .then(data)
  },

  changePassword(payload: { oldPassword: string; newPassword: string }) {
    return http.post<void>('/users/me/password', payload).then(data)
  },

  getNotifications() {
    return http.get<NotificationSettings>('/users/notification-preferences').then(data)
  },

  updateNotifications(payload: Partial<NotificationSettings>) {
    return http.put<NotificationSettings>('/users/notification-preferences', payload).then(data)
  },

  getFavorites(params?: { resourceType?: string; current?: number; size?: number }) {
    return http
      .get<{ records: Favorite[]; total: number }>('/users/favorites', { params })
      .then(data)
  },

  removeFavorite(id: number) {
    return http.delete<void>(`/users/favorites/${id}`).then(data)
  },

  getActivityLogs(params?: { current?: number; size?: number }) {
    return http
      .get<{ records: ActivityLog[]; total: number }>('/users/operation-records', { params })
      .then(data)
  },

  getLoginRecords(params?: { current?: number; size?: number }) {
    return http
      .get<{ records: LoginRecord[]; total: number }>('/users/login-records', { params })
      .then(data)
  },
}

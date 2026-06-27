export interface UserInfo {
  id: number
  username: string
  email?: string
  phone?: string
  bio?: string
  avatar?: string
  role: string
  orgTags: string[]
  primaryOrg?: string
  permissions: string[]
  notificationPreferences?: Record<string, boolean>
  createdAt: string
  updatedAt?: string
}

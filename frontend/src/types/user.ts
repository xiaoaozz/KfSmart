export interface UserInfo {
  id: number
  username: string
  email: string
  avatar?: string
  role: 'admin' | 'user'
  permissions: string[]
  organizationTags: string[]
  createTime: string
}

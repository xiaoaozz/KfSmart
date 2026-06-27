export interface KnowledgeBase {
  id: string
  name: string
  description?: string
  organizationTag?: string
  isPublic: boolean
  docCount: number
  createdBy: string
  createTime: string
  updateTime: string
}

export interface KbListParams {
  keyword?: string
  organizationTag?: string
  isPublic?: boolean
  current?: number
  size?: number
}

export interface KbFormValues {
  name: string
  description?: string
  organizationTag?: string
  isPublic: boolean
}

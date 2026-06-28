export interface KnowledgeBase {
  id: number
  kbId: string
  name: string
  description?: string
  orgTag?: string
  isPublic: boolean
  icon?: string
  fileCount: number
  totalSize: number
  chunkCount: number
  status: string
  createdBy: string
  createdAt: string
  updatedAt: string
}

export interface KbDocument {
  id: number
  fileMd5: string
  fileName: string
  totalSize: number
  /** 0 = uploading / 1 = done */
  status: number
  userId: string
  isPublic: boolean
  createdAt: string
  mergedAt?: string
  orgTag?: string
  kbId?: string
}

export interface KbListParams {
  keyword?: string
  orgTag?: string
  isPublic?: boolean
  current?: number
  size?: number
}

export interface KbFormValues {
  name: string
  description?: string
  orgTag?: string
  isPublic: boolean
}

export interface KbI18n {
  lang: string
  name?: string
  description?: string
}

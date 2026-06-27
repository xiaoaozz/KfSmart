export type DocStatus = 'pending' | 'processing' | 'done' | 'error'

export interface Document {
  id: number
  fileMd5: string
  fileName: string
  fileType: string
  fileSize: number
  status: DocStatus
  knowledgeBaseId?: number
  knowledgeBaseName?: string
  chunkCount: number
  uploadedBy: string
  createTime: string
  updateTime: string
  downloadUrl?: string
}

export interface DocListParams {
  keyword?: string
  status?: DocStatus
  knowledgeBaseId?: number
  current?: number
  size?: number
}

export interface UploadTaskStatus {
  fileMd5: string
  fileName: string
  uploadedChunks: number[]
  totalChunks: number
  finished: boolean
}

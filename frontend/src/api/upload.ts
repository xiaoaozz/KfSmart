import { http } from './http'
import type { UploadTaskStatus } from '@/types/document'

export interface ChunkUploadParams {
  fileMd5: string
  fileName: string
  chunkIndex: number
  totalSize: number // total file bytes, not chunk count
  chunk: Blob
  knowledgeBaseId?: number
}

export const uploadApi = {
  /** Check existing chunks for resumable upload */
  status: (fileMd5: string) =>
    http
      .get<UploadTaskStatus>('/upload/status', { params: { file_md5: fileMd5 } })
      .then((r) => r.data),

  /** Upload a single chunk */
  uploadChunk: ({
    fileMd5,
    fileName,
    chunkIndex,
    totalSize,
    chunk,
    knowledgeBaseId,
  }: ChunkUploadParams) => {
    const form = new FormData()
    form.append('fileMd5', fileMd5)
    form.append('fileName', fileName)
    form.append('chunkIndex', String(chunkIndex))
    form.append('totalSize', String(totalSize))
    form.append('chunk', chunk)
    if (knowledgeBaseId != null) form.append('knowledgeBaseId', String(knowledgeBaseId))
    return http.post('/upload/chunk', form, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
  },

  /** Merge chunks + trigger parse */
  merge: (fileMd5: string, fileName: string, knowledgeBaseId?: number) =>
    http
      .post<{ documentId: number }>('/upload/merge', { fileMd5, fileName, knowledgeBaseId })
      .then((r) => r.data),

  /** Simple single-request upload (small files) */
  simple: (file: File, knowledgeBaseId?: number) => {
    const form = new FormData()
    form.append('file', file)
    if (knowledgeBaseId != null) form.append('knowledgeBaseId', String(knowledgeBaseId))
    return http
      .post<{ documentId: number }>('/upload/simple', form, {
        headers: { 'Content-Type': 'multipart/form-data' },
      })
      .then((r) => r.data)
  },
}

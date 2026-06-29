import { http } from './http'
import type { UploadTaskStatus } from '@/types/document'

export interface ChunkUploadParams {
  fileMd5: string
  fileName: string
  chunkIndex: number
  totalSize: number // total file bytes, not chunk count
  chunk: Blob
  kbId?: string
}

export const uploadApi = {
  /** Check existing chunks for resumable upload */
  status: (fileMd5: string) =>
    http
      .get<UploadTaskStatus>('/upload/status', { params: { file_md5: fileMd5 } })
      .then((r) => r.data),

  /** Upload a single chunk */
  uploadChunk: ({ fileMd5, fileName, chunkIndex, totalSize, chunk, kbId }: ChunkUploadParams) => {
    const form = new FormData()
    form.append('fileMd5', fileMd5)
    form.append('fileName', fileName)
    form.append('chunkIndex', String(chunkIndex))
    form.append('totalSize', String(totalSize))
    form.append('chunk', chunk)
    if (kbId != null) form.append('kbId', kbId)
    return http.post('/upload/chunk', form, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
  },

  /** Merge chunks + trigger parse */
  merge: (fileMd5: string, fileName: string) =>
    http.post<{ documentId: number }>('/upload/merge', { fileMd5, fileName }).then((r) => r.data),
}

import { http } from './http'
import type { Document, DocListParams } from '@/types/document'
import type { PageResult } from '@/types/api'

export const docApi = {
  list: (params?: DocListParams) =>
    http.get<PageResult<Document>>('/documents/uploads', { params }).then((r) => r.data),

  delete: (fileMd5: string) => http.delete(`/documents/${fileMd5}`),

  // Backend re-triggers parsing from the stored MinIO file via fileMd5
  reparse: (fileMd5: string) => http.post('/parse', null, { params: { file_md5: fileMd5 } }),

  download: (fileName: string) =>
    http
      .get<Blob>('/documents/download', {
        params: { fileName },
        responseType: 'blob',
      })
      .then((r) => r.data),
}

import { http } from './http'
import type {
  KnowledgeBase,
  KbListParams,
  KbFormValues,
  KbDocument,
  KbI18n,
} from '@/types/knowledge-base'
import type { PageResult } from '@/types/api'

export const kbApi = {
  list: (params?: KbListParams) =>
    http.get<PageResult<KnowledgeBase>>('/knowledge-bases', { params }).then((r) => r.data),

  get: (kbId: string) => http.get<KnowledgeBase>(`/knowledge-bases/${kbId}`).then((r) => r.data),

  getDocuments: (kbId: string) =>
    http.get<KbDocument[]>(`/knowledge-bases/${kbId}/documents`).then((r) => r.data),

  create: (data: KbFormValues) =>
    http.post<KnowledgeBase>('/knowledge-bases', data).then((r) => r.data),

  update: (kbId: string, data: Partial<KbFormValues>) =>
    http.put<KnowledgeBase>(`/knowledge-bases/${kbId}`, data).then((r) => r.data),

  delete: (kbId: string) => http.delete(`/knowledge-bases/${kbId}`),

  getI18n: (kbId: string) =>
    http.get<KbI18n[]>(`/knowledge-bases/${kbId}/i18n`).then((r) => r.data),

  upsertI18n: (kbId: string, payload: KbI18n) =>
    http.put<KbI18n>(`/knowledge-bases/${kbId}/i18n`, payload).then((r) => r.data),
}

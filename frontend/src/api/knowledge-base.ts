import { http } from './http'
import type { KnowledgeBase, KbListParams, KbFormValues } from '@/types/knowledge-base'
import type { PageResult } from '@/types/api'

export const kbApi = {
  list: (params?: KbListParams) =>
    http.get<PageResult<KnowledgeBase>>('/knowledge-bases', { params }).then((r) => r.data),

  get: (id: string) => http.get<KnowledgeBase>(`/knowledge-bases/${id}`).then((r) => r.data),

  create: (data: KbFormValues) =>
    http.post<KnowledgeBase>('/knowledge-bases', data).then((r) => r.data),

  update: (id: string, data: Partial<KbFormValues>) =>
    http.put<KnowledgeBase>(`/knowledge-bases/${id}`, data).then((r) => r.data),

  delete: (id: string) => http.delete(`/knowledge-bases/${id}`),
}

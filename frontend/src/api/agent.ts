import { http } from './http'
import type { Agent, AgentFormValues, AgentExecution, AgentI18n } from '@/types/agent'
import type { PageResult } from '@/types/api'

export const agentApi = {
  list: (params?: { keyword?: string; status?: string; current?: number; size?: number }) =>
    http.get<PageResult<Agent>>('/agents', { params }).then((r) => r.data),

  get: (id: number) => http.get<Agent>(`/agents/${id}`).then((r) => r.data),

  create: (data: AgentFormValues) => http.post<Agent>('/agents', data).then((r) => r.data),

  update: (id: number, data: Partial<AgentFormValues>) =>
    http.put<Agent>(`/agents/${id}`, data).then((r) => r.data),

  publish: (id: number) => http.post<Agent>(`/agents/${id}/publish`).then((r) => r.data),

  disable: (id: number) => http.post(`/agents/${id}/disable`),

  delete: (id: number) => http.delete(`/agents/${id}`),

  listExecutions: (agentId: number, params?: { current?: number; size?: number }) =>
    http
      .get<PageResult<AgentExecution>>(`/agents/${agentId}/executions`, { params })
      .then((r) => r.data),

  /** Quick test run — triggers a single-turn chat execution */
  testRun: (agentId: number, input: string) =>
    http.post<{ executionId: string }>(`/agents/${agentId}/chat`, { input }).then((r) => r.data),

  getI18n: (agentId: string) =>
    http.get<AgentI18n[]>(`/agents/${agentId}/i18n`).then((r) => r.data),

  upsertI18n: (agentId: string, payload: AgentI18n) =>
    http.put<AgentI18n>(`/agents/${agentId}/i18n`, payload).then((r) => r.data),
}

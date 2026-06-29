import type { AxiosResponse } from 'axios'
import { http } from './http'
import type {
  Workflow,
  WorkflowSummary,
  WorkflowRun,
  WorkflowNode,
  WorkflowEdge,
} from '@/types/workflow'

interface PageResult<T> {
  records: T[]
  total: number
  pages: number
  current: number
  size: number
}

interface WorkflowGraphPayload {
  nodes: WorkflowNode[]
  edges: WorkflowEdge[]
}

const data = <T>(r: AxiosResponse<T>) => r.data

export const workflowApi = {
  list(params: {
    current?: number
    size?: number
    keyword?: string
    status?: 'draft' | 'published' | 'disabled'
  }) {
    return http.get<PageResult<WorkflowSummary>>('/workflows', { params }).then(data)
  },

  get(id: number) {
    return http.get<Workflow>(`/workflows/${id}`).then(data)
  },

  create(payload: { name: string; description?: string }) {
    return http.post<Workflow>('/workflows', payload).then(data)
  },

  update(id: number, payload: { name?: string; description?: string }) {
    return http.put<Workflow>(`/workflows/${id}`, payload).then(data)
  },

  saveGraph(id: number, graph: WorkflowGraphPayload) {
    return http.put<Workflow>(`/workflows/${id}/graph`, graph).then(data)
  },

  publish(id: number) {
    return http.post<Workflow>(`/workflows/${id}/publish`).then(data)
  },

  disable(id: number) {
    return http.post<Workflow>(`/workflows/${id}/disable`).then(data)
  },

  delete(id: number) {
    return http.delete<Workflow>(`/workflows/${id}`).then(data)
  },

  run(id: number, input: string) {
    return http
      .post<{ executionId: string }>(`/workflows/${id}/execute-async`, { input })
      .then(data)
  },

  listRuns(id: number, params: { current?: number; size?: number }) {
    return http.get<PageResult<WorkflowRun>>(`/workflows/${id}/executions`, { params }).then(data)
  },
}

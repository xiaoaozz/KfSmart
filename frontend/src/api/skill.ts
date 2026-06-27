import type { AxiosResponse } from 'axios'
import { http } from './http'
import type {
  Skill,
  SkillSummary,
  SkillParam,
  Prompt,
  PromptSummary,
  McpTool,
  ModelProvider,
} from '@/types/skill'

interface PageResult<T> {
  records: T[]
  total: number
  current: number
  size: number
}

const data = <T>(r: AxiosResponse<T>) => r.data

// ---- Skill ----
export const skillApi = {
  list(params: { current?: number; size?: number; keyword?: string; category?: string }) {
    return http.get<PageResult<SkillSummary>>('/skills', { params }).then(data)
  },
  get(id: number) {
    return http.get<Skill>(`/skills/${id}`).then(data)
  },
  create(payload: {
    name: string
    description?: string
    category: string
    language: string
    code: string
    params: SkillParam[]
    outputType: string
  }) {
    return http.post<Skill>('/skills', payload).then(data)
  },
  update(
    id: number,
    payload: Partial<{
      name: string
      description: string
      code: string
      params: SkillParam[]
      outputType: string
    }>,
  ) {
    return http.put<Skill>(`/skills/${id}`, payload).then(data)
  },
  publish(id: number) {
    return http.post<Skill>(`/skills/${id}/publish`).then(data)
  },
  disable(id: number) {
    return http.post<Skill>(`/skills/${id}/disable`).then(data)
  },
  delete(id: number) {
    return http.delete<Skill>(`/skills/${id}`).then(data)
  },
  test(id: number, args: Record<string, unknown>) {
    return http
      .post<{ output: string; durationMs: number }>(`/skills/${id}/test`, { args })
      .then(data)
  },
}

// ---- Prompt ----
export const promptApi = {
  list(params: { current?: number; size?: number; keyword?: string; category?: string }) {
    return http.get<PageResult<PromptSummary>>('/resources/prompts', { params }).then(data)
  },
  get(id: number) {
    return http.get<Prompt>(`/resources/prompts/${id}`).then(data)
  },
  create(payload: { name: string; description?: string; category: string; content: string }) {
    return http.post<Prompt>('/resources/prompts', payload).then(data)
  },
  update(
    id: number,
    payload: Partial<{
      name: string
      description: string
      category: string
      content: string
      note: string
    }>,
  ) {
    return http.put<Prompt>(`/resources/prompts/${id}`, payload).then(data)
  },
  delete(id: number) {
    return http.delete<Prompt>(`/resources/prompts/${id}`).then(data)
  },
  histories(id: number) {
    return http.get<Prompt['versions']>(`/resources/prompts/${id}/histories`).then(data)
  },
}

// ---- MCP Tool ----
export const mcpApi = {
  list() {
    return http.get<McpTool[]>('/resources/mcp-tools').then(data)
  },
  create(payload: {
    name: string
    description?: string
    transport: string
    endpoint: string
    apiKey?: string
  }) {
    return http.post<McpTool>('/resources/mcp-tools', payload).then(data)
  },
  update(
    id: number,
    payload: Partial<{ name: string; description: string; endpoint: string; apiKey: string }>,
  ) {
    return http.put<McpTool>(`/resources/mcp-tools/${id}`, payload).then(data)
  },
  delete(id: number) {
    return http.delete<McpTool>(`/resources/mcp-tools/${id}`).then(data)
  },
  test(id: number) {
    return http
      .post<{
        success: boolean
        message: string
        toolCount: number
      }>(`/resources/mcp-tools/${id}/test`)
      .then(data)
  },
}

// ---- Model Config (read-only) ----
export const modelApi = {
  list() {
    return http.get<ModelProvider[]>('/resources/models').then(data)
  },
}

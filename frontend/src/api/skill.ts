import type { AxiosResponse } from 'axios'
import { http } from './http'
import type {
  Skill,
  SkillSummary,
  Prompt,
  PromptSummary,
  PromptVersion,
  McpTool,
  ModelConfig,
  SkillTestResult,
} from '@/types/skill'

interface PageResult<T> {
  records: T[]
  total: number
  page: number
  size: number
  totalPages: number
  hasNext: boolean
  nextCursor: string | null
}

const data = <T>(r: AxiosResponse<T>) => r.data

// ---- Skill ----
export const skillApi = {
  list(params: {
    page?: number
    size?: number
    keyword?: string
    category?: string
    status?: string
  }) {
    return http.get<PageResult<SkillSummary>>('/skills', { params }).then(data)
  },
  get(skillId: string) {
    return http.get<Skill>(`/skills/${skillId}`).then(data)
  },
  create(payload: {
    name: string
    description?: string
    category: string
    instruction?: string
    systemPrompt?: string
    inputSchema?: string
    outputSchema?: string
  }) {
    return http.post<Skill>('/skills', payload).then(data)
  },
  update(
    skillId: string,
    payload: Partial<{
      name: string
      description: string
      category: string
      instruction: string
      systemPrompt: string
      inputSchema: string
      outputSchema: string
      runtimeConfig: string
      tags: string
      promptRefs: string
      mcpToolRefs: string
    }>,
  ) {
    return http.put<Skill>(`/skills/${skillId}`, payload).then(data)
  },
  publish(skillId: string) {
    return http.post<Skill>(`/skills/${skillId}/publish`).then(data)
  },
  toggleStatus(skillId: string) {
    return http.put<Skill>(`/skills/${skillId}/toggle-status`).then(data)
  },
  delete(skillId: string) {
    return http.delete<void>(`/skills/${skillId}`).then(data)
  },
  test(skillId: string, args: Record<string, unknown>) {
    return http.post<SkillTestResult>(`/skills/${skillId}/test`, args).then(data)
  },
}

// ---- Prompt ----
export const promptApi = {
  list(params: { page?: number; size?: number; keyword?: string; category?: string }) {
    return http.get<PageResult<PromptSummary>>('/resources/prompts', { params }).then(data)
  },
  get(templateId: string) {
    return http.get<Prompt>(`/resources/prompts/${templateId}`).then(data)
  },
  create(payload: {
    name: string
    description?: string
    category: string
    content: string
    systemContent?: string
  }) {
    return http.post<Prompt>('/resources/prompts', payload).then(data)
  },
  update(
    templateId: string,
    payload: Partial<{
      name: string
      description: string
      category: string
      content: string
      systemContent: string
      variables: string
      tags: string
      status: string
      changeDescription: string
    }>,
  ) {
    return http.put<Prompt>(`/resources/prompts/${templateId}`, payload).then(data)
  },
  delete(templateId: string) {
    return http.delete<void>(`/resources/prompts/${templateId}`).then(data)
  },
  histories(templateId: string) {
    return http.get<PromptVersion[]>(`/resources/prompts/${templateId}/histories`).then(data)
  },
  categories() {
    return http.get<string[]>('/resources/prompts/categories').then(data)
  },
}

// ---- MCP Tool ----
export const mcpApi = {
  list(params?: { page?: number; size?: number; keyword?: string }) {
    return http.get<PageResult<McpTool>>('/resources/mcp-tools', { params }).then(data)
  },
  create(payload: {
    name: string
    description?: string
    type?: string
    endpoint: string
    authType?: string
    authHeaderName?: string
    apiKey?: string
    inputSchema?: string
  }) {
    return http.post<McpTool>('/resources/mcp-tools', payload).then(data)
  },
  update(
    toolId: string,
    payload: Partial<{
      name: string
      description: string
      endpoint: string
      authType: string
      authHeaderName: string
      apiKey: string
      inputSchema: string
    }>,
  ) {
    return http.put<McpTool>(`/resources/mcp-tools/${toolId}`, payload).then(data)
  },
  delete(toolId: string) {
    return http.delete<void>(`/resources/mcp-tools/${toolId}`).then(data)
  },
  test(toolId: string, args: Record<string, unknown>) {
    return http
      .post<{ success: boolean; message: string }>(`/resources/mcp-tools/${toolId}/test`, args)
      .then(data)
  },
}

// ---- Model Config (read-only) ----
export const modelApi = {
  list() {
    return http.get<ModelConfig[]>('/resources/models').then(data)
  },
}

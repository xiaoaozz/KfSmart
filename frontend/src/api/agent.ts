import { http } from './http'
import type { Agent, AgentFormValues, AgentExecution, AgentI18n } from '@/types/agent'
import type { PageResult } from '@/types/api'

/** Convert UI form values to the flat payload the backend expects. */
function toPayload(data: Partial<AgentFormValues>) {
  return {
    name: data.name,
    description: data.description,
    systemPrompt: data.systemPrompt,
    models: data.models,
    temperature: data.temperature,
    maxTokens: data.maxTokens,
    knowledgeBases: data.knowledgeBaseIds?.length ? data.knowledgeBaseIds.join(',') : null,
    skillRefs: data.skillIds?.length ? data.skillIds.join(',') : null,
  }
}

/** A single ReAct reasoning step returned by the agent chat endpoint. */
export interface AgentTraceStep {
  iteration: number
  thought: string | null
  action: string | null
  actionInput: string | null
  observation: string | null
  durationMs: number
  status: string
}

/** Token accounting returned by the agent chat endpoint. */
export interface AgentTokenUsage {
  promptTokens: number
  completionTokens: number
  totalTokens: number
  cost: number
  modelCost: number
  toolCost: number
}

/** Full synchronous response of POST /agents/:id/chat. */
export interface AgentChatResponse {
  executionId: string
  answer: string
  success: boolean
  iterations: number
  durationMs: number
  trace: AgentTraceStep[]
  tokens: AgentTokenUsage
}

export const agentApi = {
  list: (params?: { keyword?: string; status?: string; current?: number; size?: number }) =>
    http.get<PageResult<Agent>>('/agents', { params }).then((r) => r.data),

  get: (id: number) => http.get<Agent>(`/agents/${id}`).then((r) => r.data),

  create: (data: AgentFormValues) =>
    http.post<Agent>('/agents', toPayload(data)).then((r) => r.data),

  update: (id: number, data: Partial<AgentFormValues>) =>
    http.put<Agent>(`/agents/${id}`, toPayload(data)).then((r) => r.data),

  publish: (id: number) => http.post<Agent>(`/agents/${id}/publish`).then((r) => r.data),

  disable: (id: number) => http.post(`/agents/${id}/disable`),

  delete: (id: number) => http.delete(`/agents/${id}`),

  listExecutions: (agentId: number, params?: { current?: number; size?: number }) =>
    http
      .get<PageResult<AgentExecution>>(`/agents/${agentId}/executions`, { params })
      .then((r) => r.data),

  /** Quick test run — synchronous single-turn chat execution.
   *  The backend runs the ReAct loop to completion and returns the full result
   *  (answer, trace, token usage, duration) immediately — no polling needed. */
  testRun: (
    agentId: number | string,
    input: string,
    history?: Array<{ role: 'user' | 'assistant'; content: string }>,
  ) =>
    http
      .post<AgentChatResponse>(`/agents/${agentId}/chat`, {
        query: input,
        history: history?.map((m) => ({ role: m.role, content: m.content })),
      })
      .then((r) => r.data),

  /** Fetch a single execution record by its ID */
  getExecution: (agentId: number, executionId: string) =>
    http.get<AgentExecution>(`/agents/${agentId}/executions/${executionId}`).then((r) => r.data),

  getI18n: (agentId: string) =>
    http.get<AgentI18n[]>(`/agents/${agentId}/i18n`).then((r) => r.data),

  upsertI18n: (agentId: string, payload: AgentI18n) =>
    http.put<AgentI18n>(`/agents/${agentId}/i18n`, payload).then((r) => r.data),
}

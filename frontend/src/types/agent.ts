export type AgentStatus = 'draft' | 'published' | 'disabled'

export interface AgentI18n {
  lang: string
  name?: string
  description?: string
}

/** Matches the backend Agent entity (flat fields, no nested model object). */
export interface Agent {
  id: number
  agentId?: string
  name: string
  description?: string
  status: AgentStatus
  systemPrompt?: string
  avatarEmoji?: string
  // flat model config
  models?: string // e.g. "deepseek-chat"
  temperature?: number
  maxTokens?: number
  topP?: number
  // tool associations (comma-separated IDs, e.g. "1,2,3")
  knowledgeBases?: string
  skillRefs?: string
  // stats
  callCount?: number
  successRate?: number
  createdAt?: string
  updatedAt?: string
}

/** Form shape for the agent editor UI.
 *  knowledgeBaseIds / skillIds are UI-only arrays; they are converted to
 *  comma-separated strings (knowledgeBases / skillRefs) before being sent
 *  to the API (see agentApi.create / agentApi.update). */
export interface AgentFormValues {
  name: string
  description?: string
  systemPrompt: string
  models?: string
  temperature?: number
  maxTokens?: number
  knowledgeBaseIds?: number[]
  skillIds?: number[]
}

/** Matches the backend AgentExecutionLog entity. */
export interface AgentExecution {
  id: number
  executionId: string
  agentId: string
  versionId?: string
  triggerType?: string
  status: 'running' | 'success' | 'failed'
  inputJson?: string
  outputJson?: string
  traceJson?: string
  iterations?: number
  startedBy?: string
  startedAt?: string
  completedAt?: string
  durationMs?: number
  promptTokens?: number
  completionTokens?: number
  totalTokens?: number
  cost?: number
  modelCost?: number
  toolCost?: number
  errorMessage?: string
}

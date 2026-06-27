export type AgentStatus = 'draft' | 'published' | 'disabled'

export interface AgentModel {
  provider: string
  name: string
  temperature: number
  maxTokens: number
}

export interface Agent {
  id: number
  name: string
  description?: string
  status: AgentStatus
  systemPrompt: string
  model: AgentModel
  knowledgeBaseIds: number[]
  skillIds: number[]
  version: number
  createdBy: string
  createTime: string
  updateTime: string
}

export interface AgentFormValues {
  name: string
  description?: string
  systemPrompt: string
  model: AgentModel
  knowledgeBaseIds: number[]
  skillIds: number[]
}

export interface AgentExecution {
  id: string
  agentId: number
  agentName: string
  status: 'running' | 'success' | 'failed'
  input: string
  output?: string
  tokens: number
  durationMs: number
  createTime: string
}

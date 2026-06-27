export type SkillCategory = 'http' | 'database' | 'file' | 'ai' | 'custom'
export type SkillStatus = 'draft' | 'published' | 'disabled'

export interface SkillParam {
  name: string
  type: 'string' | 'number' | 'boolean' | 'object' | 'array'
  required: boolean
  description?: string
  defaultValue?: string
}

export interface Skill {
  id: number
  name: string
  description?: string
  category: SkillCategory
  status: SkillStatus
  language: 'javascript' | 'python'
  code: string
  params: SkillParam[]
  outputType: string
  runCount: number
  createTime: string
  updateTime: string
}

export interface SkillSummary {
  id: number
  name: string
  description?: string
  category: SkillCategory
  status: SkillStatus
  language: 'javascript' | 'python'
  runCount: number
  createTime: string
  updateTime: string
}

export type PromptCategory = 'chat' | 'summary' | 'translation' | 'code' | 'analysis' | 'custom'

export interface PromptVersion {
  version: number
  content: string
  createTime: string
  note?: string
}

export interface Prompt {
  id: number
  name: string
  description?: string
  category: PromptCategory
  content: string
  variables: string[]
  useCount: number
  versions?: PromptVersion[]
  createTime: string
  updateTime: string
}

export interface PromptSummary {
  id: number
  name: string
  description?: string
  category: PromptCategory
  useCount: number
  createTime: string
  updateTime: string
}

export type McpTransport = 'stdio' | 'sse' | 'http'
export type McpStatus = 'connected' | 'disconnected' | 'error' | 'testing'

export interface McpTool {
  id: number
  name: string
  description?: string
  transport: McpTransport
  endpoint: string
  apiKey?: string
  status: McpStatus
  toolCount: number
  lastTestTime?: string
  createTime: string
  updateTime: string
}

export interface ModelProvider {
  id: string
  name: string
  icon?: string
  models: ModelInfo[]
}

export interface ModelInfo {
  id: string
  name: string
  description?: string
  contextLength: number
  inputPrice: number
  outputPrice: number
  capabilities: string[]
  isDefault: boolean
}

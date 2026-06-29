export type SkillStatus = '草稿' | '已发布' | '已停用'

export interface SkillSummary {
  id: number
  skillId: string
  name: string
  description?: string
  category: string
  status: string
  ownerName?: string
  tags?: string
  version: string
  callCount: number
  successCount: number
  avgDurationMs: number
  publishedAt?: string
  createdAt: string
  updatedAt: string
}

export interface Skill {
  id: number
  skillId: string
  name: string
  description?: string
  category: string
  status: string
  ownerName?: string
  tags?: string
  instruction?: string
  systemPrompt?: string
  inputSchema?: string
  outputSchema?: string
  runtimeConfig?: string
  exampleInput?: string
  exampleOutput?: string
  promptRefs?: string
  mcpToolRefs?: string
  version: string
  callCount: number
  successCount: number
  avgDurationMs: number
  publishedAt?: string
  createdAt: string
  updatedAt: string
}

export interface SkillTestResult {
  success: boolean
  message: string
  validation: {
    valid: boolean
    missingFields: string[]
    typeErrors: string[]
  }
  resolvedPrompts: Array<Record<string, unknown>>
  resolvedTools: Array<Record<string, unknown>>
  runtimeConfig: Record<string, unknown>
  executionPlan: string[]
  warnings: string[]
  mockOutput: Record<string, unknown>
  echoInput: Record<string, unknown>
}

export type PromptCategory = 'chat' | 'summary' | 'translation' | 'code' | 'analysis' | 'custom'

export interface PromptVersion {
  templateId: string
  version: string
  name: string
  description?: string
  category: string
  systemContent?: string
  content: string
  variables?: string
  tags?: string
  status: string
  snapshotBy?: string
  snapshotAt: string
  changeDescription?: string
}

export interface Prompt {
  id: number
  templateId: string
  name: string
  description?: string
  category: string
  version: string
  systemContent?: string
  content: string
  variables?: string
  tags?: string
  status: string
  useCount: number
  createdAt: string
  updatedAt: string
  versions?: PromptVersion[]
}

export interface PromptSummary {
  id: number
  templateId: string
  name: string
  description?: string
  category: string
  version: string
  status: string
  useCount: number
  createdAt: string
  updatedAt: string
}

export type McpTransport = 'stdio' | 'sse' | 'http'
export type McpStatus = '在线' | '离线' | '错误' | '测试中'

export interface McpTool {
  id: number
  toolId: string
  name: string
  description?: string
  type: string
  status: string
  toolName: string
  requestMode: string
  protocolVersion: string
  endpoint: string
  authType: string
  authHeaderName: string
  apiKeyMasked: string
  inputSchema?: string
  lastTestStatus?: string
  lastTestMessage?: string
  lastTestAt?: string
  callCount: number
  createdAt: string
  updatedAt: string
}

export interface ModelConfig {
  id: number
  name: string
  provider: string
  providerLabel: string
  apiUrl: string
  modelName: string
  active: boolean
  authType: string
  temperature: number
  maxTokens: number
  topP: number
  remark: string
  status: string
  scene: string
  icon: string
  category: string
  description: string
  tags: string[]
  createdAt: string
  updatedAt: string
}

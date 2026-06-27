export type WorkflowStatus = 'draft' | 'published' | 'disabled'
export type NodeType = 'start' | 'end' | 'llm' | 'kb' | 'code' | 'condition'

export interface WorkflowNode {
  id: string
  type: NodeType
  position: { x: number; y: number }
  data: Record<string, unknown>
  label?: string
}

export interface WorkflowEdge {
  id: string
  source: string
  target: string
  sourceHandle?: string
  targetHandle?: string
  label?: string
}

export interface Workflow {
  id: number
  name: string
  description?: string
  status: WorkflowStatus
  nodes: WorkflowNode[]
  edges: WorkflowEdge[]
  runCount: number
  createTime: string
  updateTime: string
}

export interface WorkflowSummary {
  id: number
  name: string
  description?: string
  status: WorkflowStatus
  runCount: number
  createTime: string
  updateTime: string
}

export interface WorkflowRun {
  id: number
  workflowId: number
  status: 'running' | 'success' | 'failed'
  input: string
  output?: string
  tokens: number
  durationMs: number
  createTime: string
}

// ---- node-specific data shapes ----
export interface StartNodeData {
  inputVariable: string
}

export interface LlmNodeData {
  model: string
  systemPrompt: string
  temperature: number
  maxTokens: number
}

export interface KbNodeData {
  knowledgeBaseId: number
  topK: number
  threshold: number
}

export interface CodeNodeData {
  language: 'javascript' | 'python'
  code: string
}

export interface ConditionNodeData {
  variable: string
  operator: 'eq' | 'neq' | 'contains' | 'not_contains' | 'gt' | 'lt'
  value: string
}

export interface EndNodeData {
  outputVariable: string
}

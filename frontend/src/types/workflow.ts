export type WorkflowStatus = 'draft' | 'published' | 'disabled'
export type NodeType =
  | 'start'
  | 'end'
  | 'llm'
  | 'kb'
  | 'code'
  | 'condition'
  | 'http'
  | 'loop'
  | 'variable'
  | 'agent_call'
  | 'delay'

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
  workflowId?: string
  name: string
  description?: string
  status: WorkflowStatus
  nodes: WorkflowNode[]
  edges: WorkflowEdge[]
  callCount?: number
  successCount?: number
  failureCount?: number
  createdAt?: string
  updatedAt?: string
}

export interface WorkflowSummary {
  id: number
  workflowId?: string
  name: string
  description?: string
  status: WorkflowStatus
  callCount?: number
  successCount?: number
  failureCount?: number
  createdAt?: string
  updatedAt?: string
}

/** Matches the backend WorkflowExecutionLog entity. */
export interface WorkflowRun {
  id: number
  executionId: string
  workflowId: string
  versionId?: string
  triggerType?: string
  status: 'running' | 'success' | 'failed'
  inputJson?: string
  outputJson?: string
  traceJson?: string
  variablesJson?: string
  startedBy?: string
  startedAt?: string
  completedAt?: string
  durationMs?: number
  promptTokens?: number
  completionTokens?: number
  totalTokens?: number
  cost?: number
  errorMessage?: string
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

// ---- new node types ----
export interface HttpNodeData {
  method: 'GET' | 'POST' | 'PUT' | 'DELETE'
  url: string
  headers: Array<{ key: string; value: string }>
  body: string
  timeout: number
}

export interface LoopNodeData {
  mode: 'count' | 'array'
  count: number
  arrayVariable: string
  maxIterations: number
}

export interface VariableNodeData {
  key: string
  value: string
}

export interface AgentCallNodeData {
  agentId: number
  inputVariable: string
}

export interface DelayNodeData {
  seconds: number
}

export interface NodeRunStatusData {
  _runStatus?: string
}

import type { Node } from '@xyflow/react'
import type { StartNodeData } from '@/types/workflow'

export interface NodeOutputDef {
  key: string
  label: string
  type: string
}

export interface NodeInputDef {
  key: string
  label: string
  type: 'text' | 'textarea'
  required: boolean
}

export const NODE_OUTPUTS: Record<string, NodeOutputDef[]> = {
  start: [],
  end: [{ key: 'output', label: '最终输出', type: 'string' }],
  llm: [
    { key: 'output', label: '回答内容', type: 'string' },
    { key: 'answer', label: '回答内容', type: 'string' },
  ],
  kb: [
    { key: 'documents', label: '检索文档列表', type: 'array' },
    { key: 'context', label: '拼接上下文', type: 'string' },
  ],
  code: [{ key: 'result', label: '执行结果', type: 'any' }],
  condition: [{ key: 'conditionMatched', label: '匹配结果', type: 'boolean' }],
  http: [
    { key: 'response', label: '响应体', type: 'string' },
    { key: 'result', label: '结果', type: 'string' },
  ],
  loop: [
    { key: 'iteration', label: '当前迭代', type: 'number' },
    { key: 'continue', label: '是否继续', type: 'boolean' },
  ],
  variable: [],
  agent_call: [{ key: 'output', label: 'Agent 输出', type: 'string' }],
  delay: [{ key: 'delayedMs', label: '延迟毫秒', type: 'number' }],
}

export const NODE_INPUTS: Record<string, NodeInputDef[]> = {
  start: [],
  end: [{ key: 'outputTemplate', label: '输出模板', type: 'textarea', required: false }],
  llm: [
    { key: 'systemPrompt', label: '系统 Prompt', type: 'textarea', required: false },
    { key: 'prompt', label: '用户消息', type: 'textarea', required: false },
  ],
  kb: [{ key: 'query', label: '检索内容', type: 'textarea', required: false }],
  code: [{ key: 'inputs', label: '输入映射', type: 'textarea', required: false }],
  condition: [{ key: 'conditionExpr', label: '条件表达式', type: 'text', required: true }],
  http: [
    { key: 'url', label: 'URL', type: 'text', required: true },
    { key: 'body', label: '请求体', type: 'textarea', required: false },
  ],
  loop: [],
  variable: [{ key: 'value', label: '变量值', type: 'textarea', required: true }],
  agent_call: [{ key: 'query', label: '调用输入', type: 'textarea', required: false }],
  delay: [],
}

export function getDynamicOutputs(node: Node): NodeOutputDef[] {
  if (node.type === 'start') {
    const data = node.data as unknown as StartNodeData
    const vars = (data?.variables ?? []).filter((v) => v.name)
    return vars.length > 0
      ? vars.map((v) => ({ key: v.name, label: v.name, type: 'string' }))
      : [{ key: 'query', label: 'query', type: 'string' }]
  }
  if (node.type === 'variable') {
    const data = node.data as Record<string, unknown>
    const key = (data?.key as string) || 'value'
    return [{ key, label: key, type: 'string' }]
  }
  return NODE_OUTPUTS[node.type ?? ''] ?? []
}

export function getNodeOutputs(node: Node): NodeOutputDef[] {
  return getDynamicOutputs(node)
}

export const NODE_COLORS: Record<string, string> = {
  start: '#10b981',
  end: '#6366f1',
  llm: '#0052ff',
  kb: '#f59e0b',
  code: '#8b5cf6',
  condition: '#ec4899',
  http: '#06b6d4',
  loop: '#f97316',
  variable: '#64748b',
  agent_call: '#a855f7',
  delay: '#84cc16',
}

export const NODE_LABELS: Record<string, string> = {
  start: '开始',
  end: '结束',
  llm: 'LLM 模型',
  kb: '知识库',
  code: '代码执行',
  condition: '条件判断',
  http: 'HTTP 请求',
  loop: '循环',
  variable: '变量赋值',
  agent_call: 'Agent 调用',
  delay: '延迟等待',
}

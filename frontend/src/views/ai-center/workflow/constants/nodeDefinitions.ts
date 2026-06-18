import type { WorkflowNode, WorkflowEdge } from '../types/workflow';

export const nodeGroups = [
  { title: '基础节点', nodes: ['开始', '结束', '变量', '条件判断', '循环', '延迟'] },
  { title: 'AI节点', nodes: ['LLM', '知识库检索', 'Prompt模板', 'Agent调用'] },
  { title: '工具节点', nodes: ['MCP工具', 'HTTP请求', 'SQL查询', 'Python执行', '代码执行'] },
  { title: '企业节点', nodes: ['审批', '消息通知', '邮件发送', 'Webhook', '飞书通知', '企业微信通知'] }
];

export const nodeIcons: Record<string, string> = {
  '开始': 'ph:play-circle-fill',
  '结束': 'ph:stop-circle-fill',
  '变量': 'ph:variable-fill',
  '条件判断': 'ph:split-horizontal',
  '循环': 'ph:arrows-clockwise',
  '延迟': 'ph:timer',
  'LLM': 'ph:brain',
  '知识库检索': 'ph:books',
  'Prompt模板': 'ph:text-aa',
  'Agent调用': 'ph:robot',
  'MCP工具': 'ph:wrench',
  'HTTP请求': 'ph:globe',
  'SQL查询': 'ph:database',
  'Python执行': 'ph:file-code',
  '代码执行': 'ph:code',
  '审批': 'ph:check-circle',
  '消息通知': 'ph:bell',
  '邮件发送': 'ph:envelope',
  'Webhook': 'ph:webhooks-logo',
  '飞书通知': 'ph:chat-circle',
  '企业微信通知': 'ph:chat-circle-dots'
};

export function defaultNodeConfig(type: string): Record<string, any> {
  switch (type) {
    case '开始':
      return { triggerType: '手动触发', inputSchema: '{"query": "string"}', timeout: 300 };
    case '结束':
      return { outputMode: '直接输出', outputTemplate: '{{llm.output}}' };
    case '变量':
      return { varName: '', varType: 'string', varValue: '', scope: '全局' };
    case '条件判断':
      return { conditionExpr: '{{input.type}} == "A"', trueLabel: '是', falseLabel: '否', elseEnabled: false };
    case '循环':
      return { loopType: '列表循环', iterateVar: '{{input.items}}', loopVar: 'item', maxIterations: 100 };
    case '延迟':
      return { delayMs: 1000, delayType: '固定延迟', unit: 'ms' };
    case 'LLM':
      return { model: '', temperature: 0.7, maxTokens: 2048, topP: 1.0, systemPrompt: '', stream: true, timeout: 60 };
    case '知识库检索':
      return { knowledgeBase: '', searchMode: '混合检索', topK: 5, scoreThreshold: 0.5, rerankEnabled: false };
    case 'Prompt模板':
      return { templateId: '', templateContent: '', inputVars: '{"query": "{{input.query}}"}' };
    case 'Agent调用':
      return { agentId: '', inputMapping: '{"query": "{{input.query}}"}', outputMapping: '{"result": "{{agent.output}}"}', timeout: 120 };
    case 'MCP工具':
      return { toolId: '', inputMapping: '{"query": "{{input.query}}"}', outputField: 'result', retryCount: 1, timeout: 30 };
    case 'HTTP请求':
      return { url: '', method: 'GET', headers: '{"Content-Type": "application/json"}', body: '', authType: 'none', timeout: 30, retryCount: 1 };
    case 'SQL查询':
      return { datasource: '', sql: 'SELECT * FROM table WHERE id = {{input.id}}', resultType: '列表', maxRows: 100 };
    case 'Python执行':
      return { code: '# 可使用 input 变量\nresult = input.get("query", "")', requirements: '', timeout: 30, inputVars: 'input', outputVar: 'result' };
    case '代码执行':
      return { language: 'JavaScript', code: '// 可使用 input 变量\nconst result = input.query;\nreturn { result };', timeout: 30 };
    case '审批':
      return { approvers: '', approvalType: '任一审批', formFields: '{"reason": "string"}', timeout: 86400, autoApprove: false };
    case '消息通知':
      return { channel: '系统通知', recipients: '', title: '', content: '{{input.message}}', priority: '普通' };
    case '邮件发送':
      return { to: '', cc: '', subject: '', body: '', bodyType: 'html', attachments: '' };
    case 'Webhook':
      return { url: '', method: 'POST', headers: '{"Content-Type": "application/json"}', payload: '{{input}}', secret: '', retryCount: 2 };
    case '飞书通知':
      return { webhookUrl: '', msgType: 'text', title: '', content: '{{input.message}}', atAll: false, atUsers: '' };
    case '企业微信通知':
      return { webhookUrl: '', msgType: 'text', content: '{{input.message}}', atUsers: '', atAll: false };
    default:
      return {};
  }
}

export function defaultNodes(): WorkflowNode[] {
  return [
    { id: 'start', type: '开始', name: '开始', x: 80, y: 140, config: defaultNodeConfig('开始') },
    { id: 'kb', type: '知识库检索', name: '知识库检索', x: 280, y: 140, config: defaultNodeConfig('知识库检索') },
    { id: 'mcp', type: 'MCP工具', name: 'MCP工具', x: 480, y: 140, config: defaultNodeConfig('MCP工具') },
    { id: 'llm', type: 'LLM', name: 'LLM生成', x: 680, y: 140, config: defaultNodeConfig('LLM') },
    { id: 'end', type: '结束', name: '输出结果', x: 880, y: 140, config: defaultNodeConfig('结束') }
  ];
}

export function defaultEdges(): WorkflowEdge[] {
  return [
    { source: 'start', target: 'kb' },
    { source: 'kb', target: 'mcp' },
    { source: 'mcp', target: 'llm' },
    { source: 'llm', target: 'end' }
  ];
}

export function nodeClass(type: string): string {
  if (type.includes('LLM') || type.includes('知识库') || type.includes('Agent') || type.includes('Prompt')) return 'border-blue-200 bg-blue-50 text-blue-700';
  if (type.includes('MCP') || type.includes('HTTP') || type.includes('SQL') || type.includes('代码') || type.includes('Python')) return 'border-emerald-200 bg-emerald-50 text-emerald-700';
  if (type.includes('审批') || type.includes('通知') || type.includes('邮件') || type.includes('Webhook')) return 'border-amber-200 bg-amber-50 text-amber-700';
  return 'border-gray-200 bg-white text-gray-700';
}

import { fakePaginationRequest, request } from '../request';

type PageParams = {
  page?: number;
  size?: number;
  cursor?: string;
};

export function fetchAgentWorkflowStats() {
  return request<Api.AgentCenter.WorkflowStats>({ url: '/agent-center/workflows/stats' });
}

export function fetchAgentWorkflows(params?: { keyword?: string } & PageParams) {
  return request<Api.Common.PaginatingQueryRecord<Api.AgentCenter.Workflow>>({
    url: '/agent-center/workflows',
    params
  });
}

export function fetchAgentWorkflowDetail(workflowId: string) {
  return request<Api.AgentCenter.Workflow>({ url: `/agent-center/workflows/${workflowId}` });
}

export function fetchSaveAgentWorkflow(data: Partial<Api.AgentCenter.Workflow>) {
  const method = data.workflowId ? 'PUT' : 'POST';
  const url = data.workflowId ? `/agent-center/workflows/${data.workflowId}` : '/agent-center/workflows';
  return request<Api.AgentCenter.Workflow>({ url, method, data });
}

export function fetchCopyAgentWorkflow(workflowId: string) {
  return request<Api.AgentCenter.Workflow>({ url: `/agent-center/workflows/${workflowId}/copy`, method: 'POST' });
}

export function fetchPublishAgentWorkflow(workflowId: string) {
  return request<Api.AgentCenter.Workflow>({ url: `/agent-center/workflows/${workflowId}/publish`, method: 'POST' });
}

export function fetchDeleteAgentWorkflow(workflowId: string) {
  return request({ url: `/agent-center/workflows/${workflowId}`, method: 'DELETE' });
}

export function fetchDebugAgentWorkflow(workflowId: string, data: Record<string, any>) {
  return request<Api.AgentCenter.DebugResult>({ url: `/agent-center/workflows/${workflowId}/debug`, method: 'POST', data });
}

export function fetchPromptTemplates(params?: { keyword?: string } & PageParams) {
  return request<Api.Common.PaginatingQueryRecord<Api.AgentCenter.PromptTemplate>>({
    url: '/agent-center/prompts',
    params
  });
}

export function fetchSavePromptTemplate(data: Partial<Api.AgentCenter.PromptTemplate>) {
  const method = data.templateId ? 'PUT' : 'POST';
  const url = data.templateId ? `/agent-center/prompts/${data.templateId}` : '/agent-center/prompts';
  return request<Api.AgentCenter.PromptTemplate>({ url, method, data });
}

export function fetchDeletePromptTemplate(templateId: string) {
  return request({ url: `/agent-center/prompts/${templateId}`, method: 'DELETE' });
}

export function fetchMcpTools(params?: { keyword?: string } & PageParams) {
  return request<Api.Common.PaginatingQueryRecord<Api.AgentCenter.McpTool>>({
    url: '/agent-center/mcp-tools',
    params
  });
}

export function fetchSaveMcpTool(data: Partial<Api.AgentCenter.McpTool>) {
  const method = data.toolId ? 'PUT' : 'POST';
  const url = data.toolId ? `/agent-center/mcp-tools/${data.toolId}` : '/agent-center/mcp-tools';
  return request<Api.AgentCenter.McpTool>({ url, method, data });
}

export function fetchDeleteMcpTool(toolId: string) {
  return request({ url: `/agent-center/mcp-tools/${toolId}`, method: 'DELETE' });
}

export function fetchAgentMarketplace() {
  return request<Api.AgentCenter.MarketplaceItem[]>({ url: '/agent-center/marketplace' });
}

export function fetchRunAnalysis() {
  return request<Api.AgentCenter.RunAnalysis>({ url: '/agent-center/analysis' });
}

export function fetchAgentModels() {
  return request<Api.AgentCenter.ModelConfig[]>({ url: '/agent-center/models' });
}

export function fetchAgentModelsPage(params?: PageParams) {
  return fakePaginationRequest<Api.Common.PaginatingQueryRecord<Api.AgentCenter.ModelConfig>>({
    url: '/agent-center/models',
    params
  });
}

import { fakePaginationRequest, request } from '../request';

type PageParams = {
  page?: number;
  size?: number;
  cursor?: string;
};

export function fetchPromptTemplates(params?: { keyword?: string; category?: string } & PageParams) {
  return request<Api.Common.PaginatingQueryRecord<Api.AgentCenter.PromptTemplate>>({
    url: '/resources/prompts',
    params
  });
}

export function fetchPromptCategories() {
  return request<string[]>({ url: '/resources/prompts/categories' });
}

export function fetchPromptDetail(templateId: string) {
  return request<Api.AgentCenter.PromptTemplate>({ url: `/resources/prompts/${templateId}` });
}

export function fetchSavePromptTemplate(data: Partial<Api.AgentCenter.PromptTemplate>) {
  const method = data.templateId ? 'PUT' : 'POST';
  const url = data.templateId ? `/resources/prompts/${data.templateId}` : '/resources/prompts';
  return request<Api.AgentCenter.PromptTemplate>({ url, method, data });
}

export function fetchTogglePromptStatus(templateId: string) {
  return request({ url: `/resources/prompts/${templateId}/toggle-status`, method: 'PUT' });
}

export function fetchDeletePromptTemplate(templateId: string) {
  return request({ url: `/resources/prompts/${templateId}`, method: 'DELETE' });
}

export function fetchPromptHistories(templateId: string) {
  return request<Api.AgentCenter.PromptHistory[]>({ url: `/resources/prompts/${templateId}/histories` });
}

export function fetchPromptHistory(templateId: string, snapshotId: number) {
  return request<Api.AgentCenter.PromptHistory>({ url: `/resources/prompts/${templateId}/histories/${snapshotId}` });
}

export function fetchRollbackPrompt(templateId: string, snapshotId: number) {
  return request<Api.AgentCenter.PromptTemplate>({ url: `/resources/prompts/${templateId}/rollback/${snapshotId}`, method: 'POST' });
}

export function fetchMcpTools(params?: { keyword?: string } & PageParams) {
  return request<Api.Common.PaginatingQueryRecord<Api.AgentCenter.McpTool>>({
    url: '/resources/mcp-tools',
    params
  });
}

export function fetchSaveMcpTool(data: Partial<Api.AgentCenter.McpTool>) {
  const method = data.toolId ? 'PUT' : 'POST';
  const url = data.toolId ? `/resources/mcp-tools/${data.toolId}` : '/resources/mcp-tools';
  return request<Api.AgentCenter.McpTool>({ url, method, data });
}

export function fetchDeleteMcpTool(toolId: string) {
  return request({ url: `/resources/mcp-tools/${toolId}`, method: 'DELETE' });
}

export function fetchTestMcpTool(toolId: string, data: Record<string, any>) {
  return request<{ success: boolean; message: string; result?: any }>({
    url: `/resources/mcp-tools/${toolId}/test`,
    method: 'POST',
    data
  });
}

export function fetchAgentModels() {
  return request<Api.AgentCenter.ModelConfig[]>({ url: '/resources/models' });
}

export function fetchAgentModelsPage(params?: PageParams) {
  return fakePaginationRequest<Api.Common.PaginatingQueryRecord<Api.AgentCenter.ModelConfig>>({
    url: '/resources/models',
    params
  });
}

export function fetchMarketplace() {
  return request<Api.AgentCenter.MarketplaceItem[]>({ url: '/resources/marketplace' });
}

export function fetchRunAnalysis() {
  return request<Api.AgentCenter.RunAnalysis>({ url: '/resources/analysis' });
}

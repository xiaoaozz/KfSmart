import { fakePaginationRequest, request } from '../request';

type PageParams = {
  page?: number;
  size?: number;
  cursor?: string;
};

export function fetchAgentStats() {
  return request<Api.AgentCenter.WorkflowStats>({ url: '/agents/stats' });
}

export function fetchAgents(params?: { keyword?: string } & PageParams) {
  return request<Api.Common.PaginatingQueryRecord<Api.AgentCenter.Workflow>>({
    url: '/agents',
    params
  });
}

export function fetchAgentDetail(agentId: string) {
  return request<Api.AgentCenter.Workflow>({ url: `/agents/${agentId}` });
}

export function fetchSaveAgent(data: Partial<Api.AgentCenter.Workflow> & { agentId?: string }) {
  const agentId = data.agentId || data.workflowId;
  const method = agentId ? 'PUT' : 'POST';
  const url = agentId ? `/agents/${agentId}` : '/agents';
  return request<Api.AgentCenter.Workflow>({ url, method, data });
}

export function fetchCopyAgent(agentId: string) {
  return request<Api.AgentCenter.Workflow>({ url: `/agents/${agentId}/copy`, method: 'POST' });
}

export function fetchPublishAgent(agentId: string) {
  return request<Api.AgentCenter.Workflow>({ url: `/agents/${agentId}/publish`, method: 'POST' });
}

export function fetchDeleteAgent(agentId: string) {
  return request({ url: `/agents/${agentId}`, method: 'DELETE' });
}

export function fetchChatAgent(agentId: string, data: Record<string, any>) {
  return request<Api.AgentCenter.DebugResult>({ url: `/agents/${agentId}/chat`, method: 'POST', data });
}

export function fetchAgentVersions(agentId: string) {
  return request<Api.AgentCenter.WorkflowVersion[]>({ url: `/agents/${agentId}/versions` });
}

export function fetchAgentVersionDetail(agentId: string, versionId: string) {
  return request<Api.AgentCenter.WorkflowVersion>({ url: `/agents/${agentId}/versions/${versionId}` });
}

export function fetchRollbackAgentVersion(agentId: string, versionId: string) {
  return request<Api.AgentCenter.Workflow>({ url: `/agents/${agentId}/versions/${versionId}/rollback`, method: 'POST' });
}

export function fetchActivateAgentVersion(agentId: string, versionId: string) {
  return request({ url: `/agents/${agentId}/versions/${versionId}/activate`, method: 'POST' });
}

export function fetchAgentExecutions(agentId: string, page = 0, size = 20) {
  return request({ url: `/agents/${agentId}/executions`, params: { page, size } });
}

export function fetchAgentExecutionDetail(agentId: string, executionId: string) {
  return request({ url: `/agents/${agentId}/executions/${executionId}` });
}

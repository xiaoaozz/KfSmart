import { request } from '../request';

type PageParams = {
  page?: number;
  size?: number;
  cursor?: string;
};

export function fetchWorkflowStats() {
  return request<Api.AgentCenter.WorkflowStats>({ url: '/workflows/stats' });
}

export function fetchWorkflows(params?: { keyword?: string } & PageParams) {
  return request<Api.Common.PaginatingQueryRecord<Api.AgentCenter.Workflow>>({
    url: '/workflows',
    params
  });
}

export function fetchWorkflowDetail(workflowId: string) {
  return request<Api.AgentCenter.Workflow>({ url: `/workflows/${workflowId}` });
}

export function fetchSaveWorkflow(data: Partial<Api.AgentCenter.Workflow>) {
  const method = data.workflowId ? 'PUT' : 'POST';
  const url = data.workflowId ? `/workflows/${data.workflowId}` : '/workflows';
  return request<Api.AgentCenter.Workflow>({ url, method, data });
}

export function fetchCopyWorkflow(workflowId: string) {
  return request<Api.AgentCenter.Workflow>({ url: `/workflows/${workflowId}/copy`, method: 'POST' });
}

export function fetchPublishWorkflow(workflowId: string) {
  return request<Api.AgentCenter.Workflow>({ url: `/workflows/${workflowId}/publish`, method: 'POST' });
}

export function fetchDeleteWorkflow(workflowId: string) {
  return request({ url: `/workflows/${workflowId}`, method: 'DELETE' });
}

export function fetchDebugWorkflow(workflowId: string, data: Record<string, any>) {
  return request<Api.AgentCenter.DebugResult>({ url: `/workflows/${workflowId}/debug`, method: 'POST', data });
}

export function fetchWorkflowVersions(workflowId: string) {
  return request<Api.AgentCenter.WorkflowVersion[]>({ url: `/workflows/${workflowId}/versions` });
}

export function fetchWorkflowVersionDetail(workflowId: string, versionId: string) {
  return request<Api.AgentCenter.WorkflowVersion>({ url: `/workflows/${workflowId}/versions/${versionId}` });
}

export function fetchRollbackWorkflowVersion(workflowId: string, versionId: string) {
  return request<Api.AgentCenter.Workflow>({ url: `/workflows/${workflowId}/versions/${versionId}/rollback`, method: 'POST' });
}

export function fetchActivateWorkflowVersion(workflowId: string, versionId: string) {
  return request({ url: `/workflows/${workflowId}/versions/${versionId}/activate`, method: 'POST' });
}

export function fetchWorkflowExecutions(workflowId: string, page = 0, size = 20) {
  return request({ url: `/workflows/${workflowId}/executions`, params: { page, size } });
}

export function fetchWorkflowExecutionDetail(workflowId: string, executionId: string) {
  return request({ url: `/workflows/${workflowId}/executions/${executionId}` });
}

export function fetchExecuteWorkflowAsync(workflowId: string, data: Record<string, any>) {
  return request<{ executionId: string }>({ url: `/workflows/${workflowId}/execute-async`, method: 'POST', data });
}

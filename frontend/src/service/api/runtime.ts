import { request } from '../request';

export function fetchRuntimeCatalog() {
  return request<Api.Runtime.Catalog>({
    url: '/runtime/catalog'
  });
}

export function fetchRuntimeExecute(data: {
  conversationId?: string;
  targetType: 'agent' | 'workflow';
  targetId: string;
  message: string;
}) {
  return request<Api.Runtime.ExecuteResult>({
    url: '/runtime/execute',
    method: 'post',
    data
  });
}

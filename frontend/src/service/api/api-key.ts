import { request } from '../request';

export interface ApiKeyConfig {
  id?: number;
  name: string;
  provider: string;
  apiUrl: string;
  apiKey: string;
  modelName: string;
  active?: boolean;
  temperature?: number;
  maxTokens?: number;
  topP?: number;
  remark?: string;
  createdAt?: string;
  updatedAt?: string;
}

/** 获取所有 API Key 配置（脱敏，普通用户可访问） */
export function fetchGetModelConfigs() {
  return request<ApiKeyConfig[]>({ url: '/chat/model-configs' });
}

/** 获取所有 API Key 配置（脱敏，仅管理员） */
export function fetchGetApiKeyList() {
  return request<ApiKeyConfig[]>({ url: '/admin/api-keys' });
}

/** 创建 API Key 配置 */
export function fetchCreateApiKey(data: ApiKeyConfig) {
  return request<{ success: boolean; message: string; id: number }>({
    url: '/admin/api-keys',
    method: 'POST',
    data
  });
}

/** 更新 API Key 配置 */
export function fetchUpdateApiKey(id: number, data: ApiKeyConfig) {
  return request<{ success: boolean; message: string }>({
    url: `/admin/api-keys/${id}`,
    method: 'PUT',
    data
  });
}

/** 删除 API Key 配置 */
export function fetchDeleteApiKey(id: number) {
  return request<{ success: boolean; message: string }>({
    url: `/admin/api-keys/${id}`,
    method: 'DELETE'
  });
}

/** 激活指定 API Key 配置 */
export function fetchActivateApiKey(id: number) {
  return request<{ success: boolean; message: string }>({
    url: `/admin/api-keys/${id}/activate`,
    method: 'POST'
  });
}

/** 获取当前激活的配置 */
export function fetchGetActiveApiKey() {
  return request<ApiKeyConfig | null>({
    url: '/admin/api-keys/active'
  });
}

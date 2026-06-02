import { request } from '../request';

/** 获取用户可访问的知识库列表 */
export function fetchGetKnowledgeBases() {
  return request<Api.KnowledgeBase.KnowledgeBaseInfo[]>({ url: '/knowledge-bases' });
}

/** 获取知识库统计概览 */
export function fetchGetKnowledgeBaseStats() {
  return request<Api.KnowledgeBase.KnowledgeBaseStats>({ url: '/knowledge-bases/stats' });
}

/** 创建知识库 */
export function fetchCreateKnowledgeBase(data: {
  name: string;
  description: string;
  orgTag?: string | null;
  isPublic: boolean;
  icon?: string;
}) {
  return request<Api.KnowledgeBase.KnowledgeBaseInfo>({ url: '/knowledge-bases', method: 'POST', data });
}

/** 更新知识库 */
export function fetchUpdateKnowledgeBase(kbId: string, data: {
  name?: string;
  description?: string;
  orgTag?: string | null;
  isPublic?: boolean;
  icon?: string;
}) {
  return request<Api.KnowledgeBase.KnowledgeBaseInfo>({ url: `/knowledge-bases/${kbId}`, method: 'PUT', data });
}

/** 删除知识库 */
export function fetchDeleteKnowledgeBase(kbId: string) {
  return request({ url: `/knowledge-bases/${kbId}`, method: 'DELETE' });
}

/** 获取知识库详情 */
export function fetchGetKnowledgeBaseDetail(kbId: string) {
  return request<Api.KnowledgeBase.KnowledgeBaseInfo>({ url: `/knowledge-bases/${kbId}` });
}
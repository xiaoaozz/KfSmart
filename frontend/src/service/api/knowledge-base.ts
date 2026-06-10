import { request } from '../request';

type PageParams = {
  page?: number;
  size?: number;
  cursor?: string;
};

/** 获取用户可访问的知识库列表 */
export function fetchGetKnowledgeBases(params?: {
  keyword?: string;
  orgTag?: string;
  isPublic?: boolean;
  createdBy?: string;
  updatedAfter?: string;
} & PageParams) {
  return request<Api.Common.PaginatingQueryRecord<Api.KnowledgeBase.KnowledgeBaseInfo>>({
    url: '/knowledge-bases',
    params
  });
}

/** 获取知识库统计概览 */
export function fetchGetKnowledgeBaseStats() {
  return request<Api.KnowledgeBase.KnowledgeBaseStats>({ url: '/knowledge-bases/stats' });
}

/** 获取知识库筛选选项 */
export function fetchGetKnowledgeBaseFilterOptions() {
  return request<Api.KnowledgeBase.KnowledgeBaseFilterOptions>({ url: '/knowledge-bases/filter-options' });
}

/** 刷新知识库统计信息 */
export function fetchRefreshKnowledgeBaseStats() {
  return request<Api.KnowledgeBase.KnowledgeBaseStats>({ url: '/knowledge-bases/refresh', method: 'POST' });
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

/** 获取指定知识库下的文档列表 */
export function fetchGetKnowledgeBaseDocuments(kbId: string) {
  return request<Api.KnowledgeBase.UploadTask[]>({ url: `/knowledge-bases/${kbId}/documents` });
}

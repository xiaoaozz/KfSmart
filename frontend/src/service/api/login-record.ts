import { request } from '../request';

/**
 * 获取当前用户登录记录（分页）
 */
export function fetchLoginRecords(page: number = 1, size: number = 20) {
  return request<Api.LoginRecord.PaginatedResult>({
    url: '/users/login-records',
    method: 'get',
    params: { page, size }
  });
}

/**
 * 获取当前用户登录统计 + 最近记录
 */
export function fetchLoginStatistics() {
  return request<Api.LoginRecord.Statistics>({
    url: '/users/login-stats',
    method: 'get'
  });
}
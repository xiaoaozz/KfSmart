import { request } from '../request';

/** 获取系统统计数据（需管理员权限） */
export function fetchGetSystemStats() {
  return request<Api.System.Stats>({ url: '/admin/stats' });
}

/** 获取系统状态信息 */
export function fetchGetSystemStatus() {
  return request<Api.System.Status>({ url: '/admin/system/status' });
}

/** 获取用户活动日志 */
export function fetchGetUserActivities(params?: {
  username?: string;
  start_date?: string;
  end_date?: string;
}) {
  return request<Api.System.UserActivity[]>({ url: '/admin/user-activities', params });
}

/** 获取所有用户列表（管理员） */
export function fetchGetAllUsers() {
  return request<Api.Auth.UserInfo[]>({ url: '/admin/users' });
}
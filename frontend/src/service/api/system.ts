import { request } from '../request';

/** 获取系统统计数据（需管理员权限） */
export function fetchGetSystemStats() {
  return request<Api.System.Stats>({ url: '/admin/stats' });
}

/** 获取系统状态信息 */
export function fetchGetSystemStatus() {
  return request<Api.System.Status>({ url: '/admin/system/status' });
}

/** 获取系统最近活动 */
export function fetchGetRecentActivities() {
  return request<Api.System.RecentActivitiesResponse>({ url: '/admin/activities' });
}

/** 获取用户活动日志 */
export function fetchGetUserActivities(params?: { username?: string; start_date?: string; end_date?: string }) {
  return request<Api.System.UserActivity[]>({ url: '/admin/user-activities', params });
}

/** 获取所有用户列表（管理员） */
export function fetchGetAllUsers() {
  return request<Api.Auth.UserInfo[]>({ url: '/admin/users' });
}

/** 获取所有权限列表 */
export function fetchGetAllPermissions() {
  return request<Api.Rbac.Permission[]>({ url: '/admin/permissions' });
}

/** 创建角色 */
export function fetchCreateRole(data: { roleCode: string; roleName: string; description: string }) {
  return request({ url: '/admin/roles', method: 'POST', data });
}

/** 更新角色（含权限列表全量替换） */
export function fetchUpdateRole(
  roleId: number,
  data: { roleName?: string; description?: string; permCodes?: string[] }
) {
  return request({ url: `/admin/roles/${roleId}`, method: 'PUT', data });
}

/** 删除角色 */
export function fetchDeleteRole(roleId: number) {
  return request({ url: `/admin/roles/${roleId}`, method: 'DELETE' });
}

/** 获取所有角色列表 */
export function fetchGetAllRoles() {
  return request<Api.Rbac.Role[]>({ url: '/admin/roles' });
}

/** 获取指定用户的角色列表 */
export function fetchGetUserRoles(userId: number) {
  return request<Api.Rbac.Role[]>({ url: `/admin/users/${userId}/roles` });
}

/** 为用户分配角色（全量替换） */
export function fetchAssignUserRoles(userId: number, roleCodes: string[]) {
  return request({ url: `/admin/users/${userId}/roles`, method: 'PUT', data: { roleCodes } });
}

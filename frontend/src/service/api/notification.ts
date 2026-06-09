import { request } from '../request';

export type NotificationActionType =
  | 'UPDATE_KB'
  | 'DELETE_KB'
  | 'UPDATE_DOCUMENT'
  | 'DELETE_DOCUMENT';

export interface UserNotification {
  id: number;
  recipientUsername: string;
  operatorUsername: string;
  actionType: NotificationActionType;
  resourceId: string;
  resourceName: string;
  message: string;
  isRead: boolean;
  createdAt: string;
}

/** 获取当前用户的所有通知（倒序） */
export function fetchGetNotifications() {
  return request<UserNotification[]>({ url: '/notifications' });
}

/** 获取未读通知数量 */
export function fetchGetUnreadCount() {
  return request<{ unreadCount: number }>({ url: '/notifications/unread-count' });
}

/** 将单条通知标记为已读 */
export function fetchMarkAsRead(id: number) {
  return request({ url: `/notifications/${id}/read`, method: 'PUT' });
}

/** 将所有通知标记为已读 */
export function fetchMarkAllAsRead() {
  return request<{ updatedCount: number }>({ url: '/notifications/read-all', method: 'PUT' });
}

import { ref, computed } from 'vue';
import { defineStore } from 'pinia';
import { SetupStoreId } from '@/enum';
import {
  fetchGetNotifications,
  fetchGetUnreadCount,
  fetchMarkAsRead,
  fetchMarkAllAsRead,
  type UserNotification
} from '@/service/api/notification';

export type { UserNotification };

export const useNotificationStore = defineStore(SetupStoreId.Notification, () => {
  const notifications = ref<UserNotification[]>([]);
  const unreadCount = ref(0);
  const loading = ref(false);

  /** 未读通知列表（计算属性） */
  const unreadNotifications = computed(() => notifications.value.filter(n => !n.isRead));

  /** 获取通知类型对应的 icon */
  function getActionIcon(actionType: string) {
    const map: Record<string, string> = {
      UPDATE_KB: 'carbon:edit',
      DELETE_KB: 'carbon:trash-can',
      UPDATE_DOCUMENT: 'carbon:document',
      DELETE_DOCUMENT: 'carbon:document-unknown'
    };
    return map[actionType] ?? 'carbon:notification';
  }

  /** 获取通知类型对应的背景色 class */
  function getActionBgClass(actionType: string) {
    const map: Record<string, string> = {
      UPDATE_KB: 'bg-blue-50 dark:bg-blue-900/20',
      DELETE_KB: 'bg-red-50 dark:bg-red-900/20',
      UPDATE_DOCUMENT: 'bg-amber-50 dark:bg-amber-900/20',
      DELETE_DOCUMENT: 'bg-red-50 dark:bg-red-900/20'
    };
    return map[actionType] ?? 'bg-gray-50 dark:bg-gray-800';
  }

  /** 获取通知类型对应的图标颜色 class */
  function getActionIconClass(actionType: string) {
    const map: Record<string, string> = {
      UPDATE_KB: 'text-blue-500',
      DELETE_KB: 'text-red-500',
      UPDATE_DOCUMENT: 'text-amber-500',
      DELETE_DOCUMENT: 'text-red-500'
    };
    return map[actionType] ?? 'text-gray-400';
  }

  /** 拉取未读数量（轻量轮询用） */
  async function fetchUnreadCount() {
    try {
      const { data } = await fetchGetUnreadCount();
      if (data) {
        unreadCount.value = (data as any).unreadCount ?? 0;
      }
    } catch {
      // 静默失败
    }
  }

  /** 拉取全量通知列表 */
  async function fetchNotifications() {
    loading.value = true;
    try {
      const { data } = await fetchGetNotifications();
      if (data) {
        notifications.value = (data as unknown as UserNotification[]) ?? [];
        unreadCount.value = notifications.value.filter(n => !n.isRead).length;
      }
    } catch {
      // 静默失败
    } finally {
      loading.value = false;
    }
  }

  /** 标记单条通知为已读 */
  async function markAsRead(id: number) {
    await fetchMarkAsRead(id);
    const target = notifications.value.find(n => n.id === id);
    if (target && !target.isRead) {
      target.isRead = true;
      unreadCount.value = Math.max(0, unreadCount.value - 1);
    }
  }

  /** 全部标记为已读 */
  async function markAllAsRead() {
    await fetchMarkAllAsRead();
    notifications.value.forEach(n => (n.isRead = true));
    unreadCount.value = 0;
  }

  /** 格式化时间为相对时间字符串 */
  function formatRelativeTime(dateStr: string): string {
    const now = Date.now();
    const diff = now - new Date(dateStr).getTime();
    const minutes = Math.floor(diff / 60000);
    if (minutes < 1) return '刚刚';
    if (minutes < 60) return `${minutes}分钟前`;
    const hours = Math.floor(minutes / 60);
    if (hours < 24) return `${hours}小时前`;
    const days = Math.floor(hours / 24);
    if (days < 30) return `${days}天前`;
    return new Date(dateStr).toLocaleDateString('zh-CN');
  }

  return {
    notifications,
    unreadCount,
    unreadNotifications,
    loading,
    getActionIcon,
    getActionBgClass,
    getActionIconClass,
    fetchUnreadCount,
    fetchNotifications,
    markAsRead,
    markAllAsRead,
    formatRelativeTime
  };
});

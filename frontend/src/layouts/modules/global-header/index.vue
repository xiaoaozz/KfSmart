<script setup lang="ts">
import { computed, ref, onMounted, onUnmounted } from 'vue';
import { useRouter } from 'vue-router';
import { useAuthStore } from '@/store/modules/auth';
import { useNotificationStore } from '@/store/modules/notification';
import GlobalSearch from '../global-search/index.vue';
import UserAvatar from './components/user-avatar.vue';

defineOptions({
  name: 'GlobalHeader'
});

interface Props {
  /** Whether to show the menu toggler */
  showMenuToggler?: App.Global.HeaderProps['showMenuToggler'];
}

defineProps<Props>();

const router = useRouter();
const authStore = useAuthStore();
const notificationStore = useNotificationStore();

// 获取当前组织名称（从本地存储或 store）
const currentOrg = computed(() => {
  return authStore.userInfo?.organization || '默认租户';
});

// 通知下拉面板控制
const notifyVisible = ref(false);

// 轮询定时器
let pollTimer: ReturnType<typeof setInterval> | null = null;

onMounted(async () => {
  // 首次加载时拉取未读数
  await notificationStore.fetchUnreadCount();
  // 每 60 秒轮询一次未读数
  pollTimer = setInterval(() => {
    notificationStore.fetchUnreadCount();
  }, 60000);
});

onUnmounted(() => {
  if (pollTimer) clearInterval(pollTimer);
});

async function openNotifyPanel() {
  notifyVisible.value = !notifyVisible.value;
  if (notifyVisible.value) {
    await notificationStore.fetchNotifications();
  }
}

function closeNotifyPanel() {
  notifyVisible.value = false;
}

async function handleMarkAllRead() {
  await notificationStore.markAllAsRead();
}

async function handleMarkRead(id: number) {
  await notificationStore.markAsRead(id);
}

function goToNotificationCenter() {
  closeNotifyPanel();
  router.push({ name: 'personal-center', query: { tab: 'notification' } });
}
</script>

<template>
  <DarkModeContainer class="h-full flex-y-center bg-white dark:bg-gray-900 border-b border-gray-100 dark:border-gray-800 px-0">
    <!-- 左侧：组织切换 -->
    <div class="flex items-center gap-6 flex-shrink-0 px-6">
      <!-- 租户/组织切换器 -->
      <div class="flex items-center gap-2">
        <NDropdown :options="[
          { label: '默认租户', key: 'default' },
          { label: '企业A', key: 'company_a' },
          { label: '企业B', key: 'company_b' }
        ]">
          <NButton text class="flex items-center gap-2 px-3 py-1.5 hover:bg-gray-50 dark:hover:bg-gray-800 rounded-lg transition-all">
            <icon-carbon:enterprise class="text-blue-500 text-lg" />
            <span class="text-sm font-medium text-gray-700 dark:text-gray-300">{{ currentOrg }}</span>
            <icon-carbon:chevron-down class="text-gray-400 text-sm" />
          </NButton>
        </NDropdown>
      </div>
    </div>

    <!-- 中间：占位 -->
    <div class="flex-1" />

    <!-- 右侧：操作按钮组 -->
    <div class="flex items-center gap-3 px-6">
      <!-- 全局搜索 -->
      <GlobalSearch />

      <!-- 通知按钮 + 下拉面板 -->
      <div class="relative">
        <NButton
          text
          class="relative p-2 hover:bg-gray-50 dark:hover:bg-gray-800 rounded-lg transition-all"
          @click="openNotifyPanel"
        >
          <icon-carbon:notification class="text-gray-600 dark:text-gray-400 text-xl" />
          <!-- 未读角标 -->
          <span
            v-if="notificationStore.unreadCount > 0"
            class="absolute -top-1 -right-1 min-w-18px h-18px flex items-center justify-center bg-red-500 text-white text-10px font-bold rounded-full px-1"
          >
            {{ notificationStore.unreadCount > 99 ? '99+' : notificationStore.unreadCount }}
          </span>
        </NButton>

        <!-- 通知下拉面板 -->
        <Transition name="notify-dropdown">
          <div
            v-if="notifyVisible"
            class="notify-panel absolute right-0 top-full mt-2 w-96 bg-white dark:bg-gray-800 rounded-xl shadow-xl border border-gray-200 dark:border-gray-700 z-50 overflow-hidden"
            style="max-height: 480px;"
          >
            <!-- 面板头部 -->
            <div class="flex items-center justify-between px-4 py-3 border-b border-gray-100 dark:border-gray-700">
              <div class="flex items-center gap-2">
                <span class="font-semibold text-gray-800 dark:text-gray-100 text-sm">消息通知</span>
                <NTag v-if="notificationStore.unreadCount > 0" type="error" size="small" round>
                  {{ notificationStore.unreadCount }}
                </NTag>
              </div>
              <NButton
                v-if="notificationStore.unreadCount > 0"
                text
                size="small"
                type="primary"
                @click="handleMarkAllRead"
              >
                全部已读
              </NButton>
            </div>

            <!-- 通知列表 -->
            <div class="overflow-y-auto" style="max-height: 360px;">
              <!-- 加载中 -->
              <div v-if="notificationStore.loading" class="flex items-center justify-center py-8">
                <NSpin size="small" />
              </div>

              <!-- 空状态 -->
              <div
                v-else-if="notificationStore.notifications.length === 0"
                class="flex flex-col items-center justify-center py-10 text-gray-400"
              >
                <icon-carbon:notification-off class="text-4xl mb-2 text-gray-300" />
                <span class="text-sm">暂无消息通知</span>
              </div>

              <!-- 通知条目 -->
              <div
                v-for="item in notificationStore.notifications.slice(0, 10)"
                v-else
                :key="item.id"
                class="flex items-start gap-3 px-4 py-3 cursor-pointer transition-colors border-b border-gray-50 dark:border-gray-700/50 last:border-0"
                :class="item.isRead
                  ? 'hover:bg-gray-50 dark:hover:bg-gray-700/30'
                  : 'bg-blue-50/50 dark:bg-blue-900/10 hover:bg-blue-50 dark:hover:bg-blue-900/20'"
                @click="handleMarkRead(item.id)"
              >
                <!-- 图标 -->
                <div
                  class="w-8 h-8 rounded-lg flex items-center justify-center flex-shrink-0 mt-0.5"
                  :class="notificationStore.getActionBgClass(item.actionType)"
                >
                  <component
                    :is="notificationStore.getActionIcon(item.actionType)"
                    class="text-sm"
                    :class="notificationStore.getActionIconClass(item.actionType)"
                  />
                </div>
                <!-- 内容 -->
                <div class="flex-1 min-w-0">
                  <p
                    class="text-sm text-gray-800 dark:text-gray-100 leading-snug line-clamp-2"
                    :class="item.isRead ? 'font-normal' : 'font-medium'"
                  >
                    {{ item.message }}
                    <span
                      v-if="!item.isRead"
                      class="inline-block w-1.5 h-1.5 bg-blue-500 rounded-full align-middle ml-1 mb-0.5"
                    />
                  </p>
                  <span class="text-xs text-gray-400 mt-0.5 block">
                    {{ notificationStore.formatRelativeTime(item.createdAt) }}
                  </span>
                </div>
              </div>
            </div>

            <!-- 面板底部：查看全部 -->
            <div class="px-4 py-2.5 border-t border-gray-100 dark:border-gray-700">
              <NButton
                text
                class="w-full text-center text-sm text-blue-500 hover:text-blue-600"
                @click="goToNotificationCenter"
              >
                查看全部通知
                <template #icon><icon-carbon:arrow-right /></template>
              </NButton>
            </div>
          </div>
        </Transition>

        <!-- 点击外部关闭遮罩 -->
        <div
          v-if="notifyVisible"
          class="fixed inset-0 z-40"
          @click="closeNotifyPanel"
        />
      </div>

      <!-- 帮助 -->
      <NButton text class="p-2 hover:bg-gray-50 dark:hover:bg-gray-800 rounded-lg transition-all">
        <icon-carbon:help class="text-gray-600 dark:text-gray-400 text-xl" />
      </NButton>

      <!-- 用户头像和下拉菜单 -->
      <div class="ml-3 pl-3 border-l border-gray-200 dark:border-gray-700">
        <UserAvatar />
      </div>
    </div>
  </DarkModeContainer>
</template>

<style scoped>
.notify-dropdown-enter-active,
.notify-dropdown-leave-active {
  transition: opacity 0.15s ease, transform 0.15s ease;
}
.notify-dropdown-enter-from,
.notify-dropdown-leave-to {
  opacity: 0;
  transform: translateY(-6px) scale(0.98);
}
</style>

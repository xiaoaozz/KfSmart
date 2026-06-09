<script setup lang="ts">
import { ref, onMounted, watch } from 'vue';
import { useRoute } from 'vue-router';
import { useNotificationStore } from '@/store/modules/notification';
import PersonalProfile from './modules/profile.vue';
import AccountSecurity from './modules/account-security.vue';
import MessageNotification from './modules/message-notification.vue';
import MyFavorites from './modules/my-favorites.vue';
import UsageStatistics from './modules/usage-statistics.vue';
import OperationRecord from './modules/operation-record.vue';

defineOptions({ name: 'PersonalCenter' });

const route = useRoute();
const notificationStore = useNotificationStore();

const activeTab = ref('profile');

const tabs = [
  { key: 'profile', label: '个人资料', icon: 'carbon:user-avatar' },
  { key: 'security', label: '账号安全', icon: 'carbon:security' },
  { key: 'notification', label: '消息通知', icon: 'carbon:notification' },
  { key: 'favorites', label: '我的收藏', icon: 'carbon:star' },
  { key: 'statistics', label: '使用统计', icon: 'carbon:analytics' },
  { key: 'records', label: '操作记录', icon: 'carbon:time' },
];

onMounted(() => {
  // 支持 ?tab=xxx 跳转到指定 tab
  if (route.query.tab) {
    activeTab.value = route.query.tab as string;
  }
  // 加载未读数
  notificationStore.fetchUnreadCount();
});

watch(
  () => route.query.tab,
  (val) => {
    if (val) activeTab.value = val as string;
  }
);
</script>

<template>
  <div class="personal-center-page h-full flex flex-col overflow-hidden">
    <!-- 顶部 Tab 导航栏 -->
    <div class="flex-shrink-0 bg-layout border-b border-gray-200 dark:border-gray-700 px-4">
      <div class="flex items-center">
        <button
          v-for="tab in tabs"
          :key="tab.key"
          class="relative flex items-center gap-2 px-5 py-4 text-sm font-medium transition-all whitespace-nowrap cursor-pointer border-0 bg-transparent"
          :class="activeTab === tab.key
            ? 'text-blue-600 dark:text-blue-400'
            : 'text-gray-500 dark:text-gray-400 hover:text-gray-800 dark:hover:text-gray-200'"
          @click="activeTab = tab.key"
        >
          <component
            :is="tab.icon"
            class="text-base flex-shrink-0 transition-colors"
            :class="activeTab === tab.key ? 'text-blue-600 dark:text-blue-400' : 'text-gray-400 dark:text-gray-500'"
          />
          <span>{{ tab.label }}</span>
          <!-- 消息通知未读角标 -->
          <span
            v-if="tab.key === 'notification' && notificationStore.unreadCount > 0"
            class="inline-flex items-center justify-center min-w-16px h-16px bg-red-500 text-white text-9px font-bold rounded-full px-1 ml-0.5"
          >
            {{ notificationStore.unreadCount > 99 ? '99+' : notificationStore.unreadCount }}
          </span>
          <!-- 选中下划线 -->
          <span
            v-if="activeTab === tab.key"
            class="absolute bottom-0 left-0 right-0 h-[2px] bg-blue-500 dark:bg-blue-400"
          />
        </button>
      </div>
    </div>

    <!-- 内容区（可滚动） -->
    <div class="flex-1 overflow-y-auto px-6 py-6 bg-gray-50 dark:bg-gray-900">
      <Transition name="fade-tab" mode="out-in">
        <PersonalProfile v-if="activeTab === 'profile'" key="profile" />
        <AccountSecurity v-else-if="activeTab === 'security'" key="security" />
        <MessageNotification v-else-if="activeTab === 'notification'" key="notification" />
        <MyFavorites v-else-if="activeTab === 'favorites'" key="favorites" />
        <UsageStatistics v-else-if="activeTab === 'statistics'" key="statistics" />
        <OperationRecord v-else-if="activeTab === 'records'" key="records" />
      </Transition>
    </div>
  </div>
</template>

<style scoped lang="scss">
.fade-tab-enter-active,
.fade-tab-leave-active {
  transition: opacity 0.15s ease;
}
.fade-tab-enter-from,
.fade-tab-leave-to {
  opacity: 0;
}
</style>

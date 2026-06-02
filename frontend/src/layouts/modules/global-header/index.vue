<script setup lang="ts">
import { computed, ref } from 'vue';
import { useAuthStore } from '@/store/modules/auth';
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

const authStore = useAuthStore();

// 获取当前组织名称（从本地存储或 store）
const currentOrg = computed(() => {
  return authStore.userInfo?.organization || '默认租户';
});

// 通知数量
const notificationCount = ref(12);
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

      <!-- 通知 -->
      <NButton text class="relative p-2 hover:bg-gray-50 dark:hover:bg-gray-800 rounded-lg transition-all">
        <icon-carbon:notification class="text-gray-600 dark:text-gray-400 text-xl" />
        <span
          v-if="notificationCount > 0"
          class="absolute -top-1 -right-1 min-w-18px h-18px flex items-center justify-center bg-red-500 text-white text-10px font-bold rounded-full px-1"
        >
          {{ notificationCount > 99 ? '99+' : notificationCount }}
        </span>
      </NButton>

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

<style scoped></style>

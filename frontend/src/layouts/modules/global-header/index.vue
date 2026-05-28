<script setup lang="ts">
import { useFullscreen } from '@vueuse/core';
import { computed, ref } from 'vue';
import { useAppStore } from '@/store/modules/app';
import { useThemeStore } from '@/store/modules/theme';
import { useAuthStore } from '@/store/modules/auth';
import GlobalSearch from '../global-search/index.vue';
import ThemeButton from './components/theme-button.vue';
import UserAvatar from './components/user-avatar.vue';
import GlobalLogo from '../global-logo/index.vue';

defineOptions({
  name: 'GlobalHeader'
});

interface Props {
  /** Whether to show the menu toggler */
  showMenuToggler?: App.Global.HeaderProps['showMenuToggler'];
}

defineProps<Props>();

const appStore = useAppStore();
const themeStore = useThemeStore();
const authStore = useAuthStore();
const { isFullscreen, toggle } = useFullscreen();

const isDev = import.meta.env.DEV;

// 获取当前组织名称（从本地存储或 store）
const currentOrg = computed(() => {
  return authStore.userInfo?.organization || '默认租户';
});

// 通知数量
const notificationCount = ref(12);
</script>

<template>
  <DarkModeContainer class="h-full flex-y-center bg-white dark:bg-gray-900 border-b border-gray-100 dark:border-gray-800 px-6">
    <!-- 左侧：Logo 和组织切换 -->
    <div class="flex items-center gap-6 flex-shrink-0">
      <!-- Logo -->
      <div class="flex items-center gap-3">
        <SystemLogo class="text-32px text-primary" />
        <div class="flex flex-col">
          <span class="text-lg font-bold text-gray-900 dark:text-white">KnowFlow</span>
          <span class="text-xs text-gray-500 dark:text-gray-400">KnowFlow</span>
        </div>
      </div>

      <!-- 租户/组织切换器 -->
      <div class="flex items-center gap-2 pl-6 border-l border-gray-200 dark:border-gray-700">
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

    <!-- 中间：全局搜索 -->
    <div class="flex-1 flex justify-center px-12">
      <div class="w-full max-w-600px">
        <GlobalSearch />
      </div>
    </div>

    <!-- 右侧：操作按钮组 -->
    <div class="flex items-center gap-3">
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

      <!-- 全屏 -->
      <FullScreen v-if="!appStore.isMobile" :full="isFullscreen" @click="toggle" />

      <!-- 语言切换 -->
      <LangSwitch
        v-if="themeStore.header.multilingual.visible"
        :lang="appStore.locale"
        :lang-options="appStore.localeOptions"
        @change-lang="appStore.changeLocale"
      />

      <!-- 主题切换 -->
      <ThemeSchemaSwitch
        :theme-schema="themeStore.themeScheme"
        :is-dark="themeStore.darkMode"
        @switch="themeStore.toggleThemeScheme"
      />

      <!-- 主题设置按钮（开发模式） -->
      <ThemeButton v-if="isDev" />

      <!-- 用户头像和下拉菜单 -->
      <div class="ml-3 pl-3 border-l border-gray-200 dark:border-gray-700">
        <UserAvatar />
      </div>
    </div>
  </DarkModeContainer>
</template>

<style scoped></style>

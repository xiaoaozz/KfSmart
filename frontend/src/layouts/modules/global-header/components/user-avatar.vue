<script setup lang="ts">
import { computed } from 'vue';
import type { VNode } from 'vue';
import { useAuthStore } from '@/store/modules/auth';
import { useRouterPush } from '@/hooks/common/router';
import { useSvgIcon } from '@/hooks/common/icon';
import { $t } from '@/locales';

defineOptions({
  name: 'UserAvatar'
});

const authStore = useAuthStore();
const { routerPushByKey, toLogin } = useRouterPush();
const { SvgIconVNode } = useSvgIcon();

function loginOrRegister() {
  toLogin();
}

type DropdownKey = 'profile' | 'settings' | 'logout';

type DropdownOption =
  | {
      key: DropdownKey;
      label: string;
      icon?: () => VNode;
    }
  | {
      type: 'divider';
      key: string;
    };

const options = computed(() => {
  const opts: DropdownOption[] = [
    {
      label: '个人中心',
      key: 'profile',
      icon: SvgIconVNode({ icon: 'carbon:user', fontSize: 18 })
    },
    {
      label: '系统设置',
      key: 'settings',
      icon: SvgIconVNode({ icon: 'carbon:settings', fontSize: 18 })
    },
    {
      type: 'divider',
      key: 'd1'
    },
    {
      label: $t('common.logout'),
      key: 'logout',
      icon: SvgIconVNode({ icon: 'carbon:logout', fontSize: 18 })
    }
  ];

  return opts;
});

function logout() {
  window.$dialog?.info({
    title: $t('common.tip'),
    content: $t('common.logoutConfirm'),
    positiveText: $t('common.confirm'),
    negativeText: $t('common.cancel'),
    onPositiveClick: async () => {
      await authStore.logout();
    }
  });
}

function handleDropdown(key: DropdownKey) {
  if (key === 'logout') {
    logout();
  } else {
    // If your other options are jumps from other routes, they will be directly supported here
    routerPushByKey(key);
  }
}

// 获取用户角色标签
const userRoleLabel = computed(() => {
  return authStore.userInfo?.role === 'admin' ? '管理员' : '普通用户';
});
</script>

<template>
  <NButton v-if="!authStore.isLogin" quaternary @click="loginOrRegister">
    {{ $t('page.login.common.loginOrRegister') }}
  </NButton>
  <NDropdown v-else placement="bottom-end" trigger="click" :options="options" @select="handleDropdown">
    <div class="flex items-center gap-3 px-3 py-1.5 hover:bg-gray-50 dark:hover:bg-gray-800 rounded-xl cursor-pointer transition-all">
      <!-- 用户头像 -->
      <NAvatar 
        :size="36"
        round
        :src="authStore.userInfo?.avatar"
        :fallback-src="'https://api.dicebear.com/7.x/avataaars/svg?seed=' + authStore.userInfo?.username"
        class="flex-shrink-0"
      >
        {{ authStore.userInfo?.username?.charAt(0).toUpperCase() }}
      </NAvatar>
      
      <!-- 用户信息 -->
      <div class="flex flex-col items-start min-w-0">
        <span class="text-sm font-medium text-gray-900 dark:text-white truncate max-w-120px">
          {{ authStore.userInfo?.username || '张朋' }}
        </span>
        <span class="text-xs text-gray-500 dark:text-gray-400">
          {{ userRoleLabel }}
        </span>
      </div>
      
      <!-- 下拉箭头 -->
      <icon-carbon:chevron-down class="text-gray-400 text-sm flex-shrink-0" />
    </div>
  </NDropdown>
</template>

<style scoped></style>

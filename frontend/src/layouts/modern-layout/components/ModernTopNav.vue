<script setup lang="ts">
import { computed } from 'vue';
import { useRouter } from 'vue-router';
import { useThemeStore } from '@/store/modules/theme';
import { useAuthStore } from '@/store/modules/auth';
import { useRouterPush } from '@/hooks/common/router';
import AnimatedButton from '@/components/modern/AnimatedButton.vue';

defineOptions({
  name: 'ModernTopNav'
});

const router = useRouter();
const themeStore = useThemeStore();
const authStore = useAuthStore();
const { toLogin } = useRouterPush();

// 导航菜单配置
const navMenus = computed(() => [
  {
    name: '对话',
    path: '/chat',
    icon: 'i-carbon:chat'
  },
  {
    name: '知识库',
    path: '/knowledge-base',
    icon: 'i-carbon:document-multiple'
  },
  {
    name: '用户管理',
    path: '/user',
    icon: 'i-carbon:user-multiple'
  }
]);

const currentPath = computed(() => router.currentRoute.value.path);

function isActive(path: string) {
  return currentPath.value === path || currentPath.value.startsWith(path + '/');
}

function navigateTo(path: string) {
  router.push(path);
}

function handleLogout() {
  window.$message?.success('已退出登录', { duration: 2000 });
  authStore.resetStore();
  toLogin();
}

// 性能指标 (模拟数据，可接入真实API)
const performanceMetrics = computed(() => ({
  p99: '320ms',
  qps: '2.4k',
  successRate: '99.7%'
}));
</script>

<template>
  <header class="modern-top-nav">
    <div class="nav-container">
      <!-- 左侧: Logo + 标题 -->
      <div class="nav-left">
        <div class="logo-section">
          <SystemLogo class="logo-icon" />
          <div class="brand-info">
            <h1 class="brand-title">KnowFlow</h1>
            <span class="brand-subtitle">KnowFlow</span>
          </div>
        </div>
      </div>

      <!-- 中间: 导航菜单 -->
      <nav class="nav-center">
        <div class="nav-menu">
          <button
            v-for="menu in navMenus"
            :key="menu.path"
            :class="['nav-item', { active: isActive(menu.path) }]"
            @click="navigateTo(menu.path)"
          >
            <div :class="[menu.icon, 'nav-icon']" />
            <span class="nav-label">{{ menu.name }}</span>
          </button>
        </div>
      </nav>

      <!-- 右侧: 性能指标 + 用户信息 -->
      <div class="nav-right">
        <!-- 性能指标 -->
        <div class="metrics-group">
          <div class="metric-item">
            <span class="metric-label">P99</span>
            <span class="metric-value">{{ performanceMetrics.p99 }}</span>
          </div>
          <div class="metric-divider" />
          <div class="metric-item">
            <span class="metric-label">QPS</span>
            <span class="metric-value">{{ performanceMetrics.qps }}</span>
          </div>
          <div class="metric-divider" />
          <div class="metric-item">
            <span class="metric-label">成功率</span>
            <span class="metric-value success">{{ performanceMetrics.successRate }}</span>
          </div>
        </div>

        <!-- 主题切换 -->
        <button class="icon-btn" title="切换主题" @click="themeStore.toggleThemeScheme">
          <div v-if="themeStore.darkMode" class="i-carbon:moon text-lg" />
          <div v-else class="i-carbon:sun text-lg" />
        </button>

        <!-- 用户头像 -->
        <div class="user-avatar-section">
          <NDropdown :options="userMenuOptions" @select="handleUserMenuSelect">
            <div class="user-avatar">
              <div v-if="authStore.userInfo.avatar" class="avatar-img">
                <img :src="authStore.userInfo.avatar" alt="User Avatar" />
              </div>
              <div v-else class="avatar-placeholder gradient-primary">
                {{ authStore.userInfo.username?.charAt(0).toUpperCase() || 'U' }}
              </div>
            </div>
          </NDropdown>
        </div>
      </div>
    </div>
  </header>
</template>

<script setup lang="ts">
import { NDropdown } from 'naive-ui';

// 用户下拉菜单
const userMenuOptions = [
  {
    label: '个人中心',
    key: 'profile',
    icon: () => <div class="i-carbon:user" />
  },
  {
    label: '设置',
    key: 'settings',
    icon: () => <div class="i-carbon:settings" />
  },
  {
    type: 'divider',
    key: 'd1'
  },
  {
    label: '退出登录',
    key: 'logout',
    icon: () => <div class="i-carbon:logout" />
  }
];

function handleUserMenuSelect(key: string) {
  switch (key) {
    case 'profile':
      router.push('/personal-center');
      break;
    case 'settings':
      window.$message?.info('设置功能开发中...');
      break;
    case 'logout':
      handleLogout();
      break;
  }
}
</script>

<style scoped lang="scss">
.modern-top-nav {
  position: sticky;
  top: 0;
  z-index: 100;
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(10px);
  border-bottom: 1px solid rgba(0, 0, 0, 0.06);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
  transition: all 0.3s ease;

  .dark & {
    background: rgba(39, 39, 42, 0.95);
    border-bottom-color: rgba(255, 255, 255, 0.06);
  }
}

.nav-container {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 64px;
  padding: 0 24px;
  max-width: 1920px;
  margin: 0 auto;
}

/* 左侧Logo区域 */
.nav-left {
  flex-shrink: 0;
  min-width: 200px;

  .logo-section {
    display: flex;
    align-items: center;
    gap: 12px;
    cursor: pointer;
    transition: transform 0.2s ease;

    &:hover {
      transform: scale(1.02);
    }

    .logo-icon {
      font-size: 32px;
      color: #667eea;
    }

    .brand-info {
      display: flex;
      flex-direction: column;
      gap: 2px;

      .brand-title {
        font-size: 18px;
        font-weight: 700;
        color: #1f1f1f;
        margin: 0;
        line-height: 1;

        .dark & {
          color: #f1f1f1;
        }
      }

      .brand-subtitle {
        font-size: 11px;
        font-weight: 500;
        color: #999;
        letter-spacing: 0.5px;
      }
    }
  }
}

/* 中间导航菜单 */
.nav-center {
  flex: 1;
  display: flex;
  justify-content: center;
}

.nav-menu {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px;
  background: rgba(102, 126, 234, 0.05);
  border-radius: 12px;

  .nav-item {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 8px 16px;
    border: none;
    background: transparent;
    color: #666;
    font-size: 14px;
    font-weight: 500;
    border-radius: 8px;
    cursor: pointer;
    transition: all 0.2s ease;
    white-space: nowrap;

    .dark & {
      color: #999;
    }

    .nav-icon {
      font-size: 18px;
    }

    &:hover {
      background: rgba(102, 126, 234, 0.1);
      color: #667eea;
    }

    &.active {
      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
      color: white;
      box-shadow: 0 4px 12px rgba(102, 126, 234, 0.3);

      &:hover {
        color: white;
      }
    }
  }
}

/* 右侧区域 */
.nav-right {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-shrink: 0;
}

/* 性能指标组 */
.metrics-group {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 6px 16px;
  background: rgba(102, 126, 234, 0.05);
  border-radius: 20px;
  border: 1px solid rgba(102, 126, 234, 0.1);

  .metric-item {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 2px;

    .metric-label {
      font-size: 10px;
      font-weight: 600;
      color: #999;
      text-transform: uppercase;
      letter-spacing: 0.5px;
    }

    .metric-value {
      font-size: 13px;
      font-weight: 700;
      color: #667eea;
      font-family: 'Monaco', 'Courier New', monospace;

      &.success {
        color: #52c41a;
      }
    }
  }

  .metric-divider {
    width: 1px;
    height: 24px;
    background: rgba(0, 0, 0, 0.1);

    .dark & {
      background: rgba(255, 255, 255, 0.1);
    }
  }
}

/* 图标按钮 */
.icon-btn {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid rgba(102, 126, 234, 0.2);
  background: rgba(102, 126, 234, 0.05);
  color: #667eea;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s ease;

  &:hover {
    background: rgba(102, 126, 234, 0.15);
    border-color: rgba(102, 126, 234, 0.4);
    transform: scale(1.05);
  }

  &:active {
    transform: scale(0.95);
  }
}

/* 用户头像 */
.user-avatar-section {
  .user-avatar {
    width: 36px;
    height: 36px;
    border-radius: 50%;
    overflow: hidden;
    cursor: pointer;
    border: 2px solid rgba(102, 126, 234, 0.2);
    transition: all 0.2s ease;

    &:hover {
      border-color: #667eea;
      transform: scale(1.1);
      box-shadow: 0 4px 12px rgba(102, 126, 234, 0.3);
    }

    .avatar-img {
      width: 100%;
      height: 100%;

      img {
        width: 100%;
        height: 100%;
        object-fit: cover;
      }
    }

    .avatar-placeholder {
      width: 100%;
      height: 100%;
      display: flex;
      align-items: center;
      justify-content: center;
      color: white;
      font-weight: 700;
      font-size: 16px;
    }
  }
}

/* 响应式适配 */
@media (max-width: 1200px) {
  .metrics-group {
    display: none;
  }
}

@media (max-width: 768px) {
  .nav-container {
    padding: 0 16px;
  }

  .nav-left {
    min-width: auto;

    .brand-subtitle {
      display: none;
    }
  }

  .nav-menu {
    gap: 4px;

    .nav-item {
      padding: 8px 12px;

      .nav-label {
        display: none;
      }
    }
  }

  .nav-right {
    gap: 12px;
  }
}
</style>

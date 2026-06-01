<template>
  <div class="app-layout" :class="layoutClass">
    <!-- 顶部导航栏 -->
    <header class="app-layout__header">
      <slot name="header">
        <TopNavbar 
          :title="title"
          :user="currentUser"
          @toggle-sidebar="handleToggleSidebar"
          @logout="handleLogout"
        />
      </slot>
    </header>

    <!-- 主体内容区 -->
    <div class="app-layout__body">
      <!-- 侧边栏 (可选) -->
      <aside 
        v-if="showSidebar" 
        class="app-layout__sidebar"
        :class="{ 'is-collapsed': sidebarCollapsed }"
      >
        <slot name="sidebar" />
      </aside>

      <!-- 内容区域 -->
      <main class="app-layout__main">
        <div class="app-layout__content">
          <slot />
        </div>
      </main>
    </div>

    <!-- 底部 (可选) -->
    <footer v-if="showFooter" class="app-layout__footer">
      <slot name="footer">
        <div class="footer-content">
          <p class="text-gray-500 text-sm">
            © {{ currentYear }} KnowFlow. All rights reserved.
          </p>
        </div>
      </slot>
    </footer>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue';
import TopNavbar from './TopNavbar.vue';

interface User {
  id: string | number;
  name: string;
  avatar?: string;
  email?: string;
  role?: string;
}

interface AppLayoutProps {
  title?: string;
  showSidebar?: boolean;
  showFooter?: boolean;
  sidebarDefaultCollapsed?: boolean;
  currentUser?: User | null;
}

const props = withDefaults(defineProps<AppLayoutProps>(), {
  title: 'KnowFlow',
  showSidebar: false,
  showFooter: false,
  sidebarDefaultCollapsed: false,
  currentUser: null,
});

const emit = defineEmits<{
  logout: [];
  toggleSidebar: [collapsed: boolean];
}>();

// 侧边栏折叠状态
const sidebarCollapsed = ref(props.sidebarDefaultCollapsed);

// 当前年份
const currentYear = computed(() => new Date().getFullYear());

// 布局class
const layoutClass = computed(() => ({
  'has-sidebar': props.showSidebar,
  'has-footer': props.showFooter,
  'sidebar-collapsed': sidebarCollapsed.value,
}));

// 切换侧边栏
const handleToggleSidebar = () => {
  sidebarCollapsed.value = !sidebarCollapsed.value;
  emit('toggleSidebar', sidebarCollapsed.value);
};

// 退出登录
const handleLogout = () => {
  emit('logout');
};
</script>

<style scoped>
.app-layout {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  background: var(--gradient-bg);
  position: relative;
  overflow: hidden;
}

/* 背景装饰 */
.app-layout::before {
  content: '';
  position: fixed;
  top: -50%;
  right: -10%;
  width: 800px;
  height: 800px;
  background: radial-gradient(circle, rgba(102, 126, 234, 0.08) 0%, transparent 70%);
  pointer-events: none;
  z-index: 0;
}

.app-layout::after {
  content: '';
  position: fixed;
  bottom: -30%;
  left: -10%;
  width: 600px;
  height: 600px;
  background: radial-gradient(circle, rgba(79, 172, 254, 0.06) 0%, transparent 70%);
  pointer-events: none;
  z-index: 0;
}

/* 头部 */
.app-layout__header {
  position: sticky;
  top: 0;
  z-index: 100;
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(10px);
  border-bottom: 1px solid rgba(0, 0, 0, 0.06);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}

/* 主体 */
.app-layout__body {
  display: flex;
  flex: 1;
  position: relative;
  z-index: 1;
}

/* 侧边栏 */
.app-layout__sidebar {
  width: 240px;
  background: rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(10px);
  border-right: 1px solid rgba(0, 0, 0, 0.06);
  transition: all var(--duration-base) var(--ease-out-cubic);
  overflow-y: auto;
  overflow-x: hidden;
}

.app-layout__sidebar.is-collapsed {
  width: 64px;
}

/* 主内容区 */
.app-layout__main {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.app-layout__content {
  flex: 1;
  padding: var(--spacing-lg);
  overflow-y: auto;
  overflow-x: hidden;
}

/* 底部 */
.app-layout__footer {
  position: relative;
  z-index: 1;
  background: rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(10px);
  border-top: 1px solid rgba(0, 0, 0, 0.06);
  padding: var(--spacing-md) var(--spacing-lg);
}

.footer-content {
  display: flex;
  justify-content: center;
  align-items: center;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .app-layout__content {
    padding: var(--spacing-md);
  }

  .app-layout__sidebar {
    position: fixed;
    left: 0;
    top: 64px;
    bottom: 0;
    z-index: 90;
    transform: translateX(-100%);
  }

  .app-layout__sidebar:not(.is-collapsed) {
    transform: translateX(0);
    box-shadow: 2px 0 8px rgba(0, 0, 0, 0.1);
  }
}

/* 暗色模式 */
.dark .app-layout {
  background: linear-gradient(180deg, #1a1a1a 0%, #0f0f0f 100%);
}

.dark .app-layout::before {
  background: radial-gradient(circle, rgba(102, 126, 234, 0.12) 0%, transparent 70%);
}

.dark .app-layout::after {
  background: radial-gradient(circle, rgba(79, 172, 254, 0.08) 0%, transparent 70%);
}

.dark .app-layout__header {
  background: rgba(26, 26, 26, 0.95);
  border-bottom-color: rgba(255, 255, 255, 0.08);
}

.dark .app-layout__sidebar {
  background: rgba(26, 26, 26, 0.9);
  border-right-color: rgba(255, 255, 255, 0.08);
}

.dark .app-layout__footer {
  background: rgba(26, 26, 26, 0.9);
  border-top-color: rgba(255, 255, 255, 0.08);
}
</style>

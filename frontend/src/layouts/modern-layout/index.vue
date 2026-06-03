<script setup lang="ts">
import { computed } from 'vue';
import ModernTopNav from './components/ModernTopNav.vue';
import ChatGPTSidebar from './components/ChatGPTSidebar.vue';

defineOptions({
  name: 'ModernLayout'
});

interface Props {
  /** 是否显示侧边栏 */
  showSidebar?: boolean;
  /** 是否全宽布局 */
  fullWidth?: boolean;
  /** 内容最大宽度 */
  maxWidth?: string;
}

const props = withDefaults(defineProps<Props>(), {
  showSidebar: true,
  fullWidth: false,
  maxWidth: '1400px'
});

const layoutClasses = computed(() => {
  return [
    'modern-layout',
    {
      'with-sidebar': props.showSidebar,
      'full-width': props.fullWidth
    }
  ];
});

const contentStyle = computed(() => {
  if (props.fullWidth) {
    return {};
  }
  return {
    maxWidth: props.maxWidth
  };
});
</script>

<template>
  <div :class="layoutClasses">
    <!-- 侧边栏 (可选) -->
    <ChatGPTSidebar v-if="showSidebar" />

    <div class="layout-wrapper">
      <!-- 顶部导航栏 -->
      <ModernTopNav />

      <!-- 内容容器 -->
      <div class="layout-content" :style="contentStyle">
        <div class="content-wrapper">
          <slot />
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.modern-layout {
  display: flex;
  min-height: 100vh;
  background: #ffffff;
  transition: background 0.3s ease;

  &.dark {
    background: #0d0d0d;
  }
}

.layout-wrapper {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  overflow: hidden;
}

.layout-content {
  flex: 1;
  min-width: 0;
  margin: 0 auto;
  width: 100%;
  padding: 24px;
  transition: all 0.3s ease;
  overflow-y: auto;

  &.full-width {
    max-width: none;
  }
}

.content-wrapper {
  position: relative;
  animation: fadeIn 0.3s ease;
}

@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* 响应式适配 */
@media (max-width: 768px) {
  .layout-content {
    padding: 16px;
  }
}
</style>

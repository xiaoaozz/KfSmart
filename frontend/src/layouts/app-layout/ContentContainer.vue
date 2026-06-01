<template>
  <div class="content-container" :class="containerClass">
    <!-- 页面标题区 -->
    <header v-if="showHeader" class="content-container__header">
      <div class="header-main">
        <!-- 返回按钮 -->
        <IconButton
          v-if="showBack"
          icon="mdi:arrow-left"
          color="primary"
          size="medium"
          @click="handleBack"
          class="back-btn"
        />

        <!-- 标题和描述 -->
        <div class="header-text">
          <h2 v-if="title" class="header-title">
            <icon-component v-if="titleIcon" :name="titleIcon" class="text-icon-lg title-icon" />
            {{ title }}
          </h2>
          <p v-if="description" class="header-description">{{ description }}</p>
        </div>
      </div>

      <!-- 右侧操作区 -->
      <div v-if="$slots.actions" class="header-actions">
        <slot name="actions" />
      </div>
    </header>

    <!-- 面包屑导航 (可选) -->
    <div v-if="breadcrumbs.length > 0" class="content-container__breadcrumb">
      <a
        v-for="(item, index) in breadcrumbs"
        :key="index"
        :href="item.href"
        :class="['breadcrumb-item', { 'is-last': index === breadcrumbs.length - 1 }]"
        @click.prevent="handleBreadcrumbClick(item, index)"
      >
        <icon-component v-if="item.icon" :name="item.icon" class="text-icon-sm" />
        <span>{{ item.label }}</span>
        <icon-component v-if="index < breadcrumbs.length - 1" name="mdi:chevron-right" class="text-icon-sm separator" />
      </a>
    </div>

    <!-- 主内容区 -->
    <div class="content-container__body">
      <slot />
    </div>

    <!-- 底部操作区 (可选) -->
    <footer v-if="$slots.footer" class="content-container__footer">
      <slot name="footer" />
    </footer>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { IconButton } from '@/components/base';

interface BreadcrumbItem {
  label: string;
  href: string;
  icon?: string;
}

interface ContentContainerProps {
  title?: string;
  titleIcon?: string;
  description?: string;
  showHeader?: boolean;
  showBack?: boolean;
  breadcrumbs?: BreadcrumbItem[];
  maxWidth?: 'sm' | 'md' | 'lg' | 'xl' | 'full';
  padding?: 'none' | 'sm' | 'md' | 'lg';
  background?: 'transparent' | 'white' | 'gray';
}

const props = withDefaults(defineProps<ContentContainerProps>(), {
  title: '',
  titleIcon: '',
  description: '',
  showHeader: true,
  showBack: false,
  breadcrumbs: () => [],
  maxWidth: 'full',
  padding: 'md',
  background: 'transparent',
});

const emit = defineEmits<{
  back: [];
  breadcrumbClick: [item: BreadcrumbItem, index: number];
}>();

const containerClass = computed(() => ({
  [`max-width-${props.maxWidth}`]: true,
  [`padding-${props.padding}`]: true,
  [`background-${props.background}`]: true,
}));

const handleBack = () => {
  emit('back');
};

const handleBreadcrumbClick = (item: BreadcrumbItem, index: number) => {
  emit('breadcrumbClick', item, index);
};
</script>

<style scoped>
.content-container {
  width: 100%;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: var(--spacing-lg);
  animation: fadeIn var(--duration-base) var(--ease-out-cubic);
}

/* 最大宽度 */
.content-container.max-width-sm {
  max-width: 640px;
}

.content-container.max-width-md {
  max-width: 768px;
}

.content-container.max-width-lg {
  max-width: 1024px;
}

.content-container.max-width-xl {
  max-width: 1280px;
}

.content-container.max-width-full {
  max-width: 100%;
}

/* 内边距 */
.content-container.padding-none {
  padding: 0;
}

.content-container.padding-sm {
  padding: var(--spacing-sm);
}

.content-container.padding-md {
  padding: var(--spacing-md);
}

.content-container.padding-lg {
  padding: var(--spacing-lg);
}

/* 背景 */
.content-container.background-white {
  background: white;
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-card);
}

.content-container.background-gray {
  background: var(--color-gray-50);
  border-radius: var(--radius-xl);
}

/* 头部 */
.content-container__header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: var(--spacing-lg);
  flex-wrap: wrap;
}

.header-main {
  display: flex;
  align-items: flex-start;
  gap: var(--spacing-md);
  flex: 1;
  min-width: 0;
}

.back-btn {
  flex-shrink: 0;
  margin-top: 4px;
}

.header-text {
  flex: 1;
  min-width: 0;
}

.header-title {
  font-size: 28px;
  font-weight: 700;
  color: var(--text-primary);
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  margin: 0;
  line-height: 1.3;
}

.title-icon {
  color: var(--color-primary-500);
  flex-shrink: 0;
}

.header-description {
  font-size: 14px;
  color: var(--text-secondary);
  margin: var(--spacing-xs) 0 0;
  line-height: 1.6;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  flex-shrink: 0;
}

/* 面包屑 */
.content-container__breadcrumb {
  display: flex;
  align-items: center;
  gap: var(--spacing-xs);
  flex-wrap: wrap;
  margin-top: calc(var(--spacing-md) * -1);
}

.breadcrumb-item {
  display: flex;
  align-items: center;
  gap: var(--spacing-xs);
  color: var(--text-secondary);
  font-size: 14px;
  text-decoration: none;
  transition: all var(--duration-base) var(--ease-out-cubic);
  padding: var(--spacing-xs) var(--spacing-sm);
  border-radius: var(--radius-sm);
  cursor: pointer;
}

.breadcrumb-item:hover:not(.is-last) {
  background: rgba(102, 126, 234, 0.08);
  color: var(--color-primary-500);
}

.breadcrumb-item.is-last {
  color: var(--text-primary);
  font-weight: 600;
  cursor: default;
}

.separator {
  color: var(--text-tertiary);
}

/* 主体 */
.content-container__body {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: var(--spacing-lg);
}

/* 底部 */
.content-container__footer {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: var(--spacing-sm);
  padding-top: var(--spacing-lg);
  border-top: 1px solid var(--border-color);
}

/* 响应式设计 */
@media (max-width: 768px) {
  .content-container {
    gap: var(--spacing-md);
  }

  .content-container.padding-md {
    padding: var(--spacing-sm);
  }

  .content-container.padding-lg {
    padding: var(--spacing-md);
  }

  .header-title {
    font-size: 24px;
  }

  .content-container__header {
    flex-direction: column;
    align-items: stretch;
  }

  .header-actions {
    width: 100%;
    justify-content: flex-start;
  }
}

/* 暗色模式 */
.dark .content-container.background-white {
  background: var(--color-gray-900);
}

.dark .content-container.background-gray {
  background: rgba(255, 255, 255, 0.03);
}

.dark .breadcrumb-item:hover:not(.is-last) {
  background: rgba(102, 126, 234, 0.15);
}

.dark .content-container__footer {
  border-top-color: var(--border-color-dark);
}
</style>

<script setup lang="ts">
import { computed } from 'vue';

defineOptions({
  name: 'PageHeader'
});

interface Props {
  /** 页面标题 */
  title: string;
  /** 页面描述 */
  description?: string;
  /** 图标 */
  icon?: string;
  /** 是否显示返回按钮 */
  showBack?: boolean;
  /** 是否显示分隔线 */
  showDivider?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  showBack: false,
  showDivider: true
});

const emit = defineEmits<{
  back: [];
}>();

function handleBack() {
  emit('back');
}
</script>

<template>
  <div :class="['page-header', { 'with-divider': showDivider }]">
    <div class="header-main">
      <!-- 返回按钮 -->
      <button v-if="showBack" class="back-btn" @click="handleBack">
        <div class="i-carbon:arrow-left" />
      </button>

      <!-- 图标 -->
      <div v-if="icon" :class="[icon, 'header-icon']" />

      <!-- 标题和描述 -->
      <div class="header-content">
        <h1 class="header-title">{{ title }}</h1>
        <p v-if="description" class="header-description">{{ description }}</p>
      </div>
    </div>

    <!-- 操作区域插槽 -->
    <div v-if="$slots.actions" class="header-actions">
      <slot name="actions" />
    </div>
  </div>
</template>

<style scoped lang="scss">
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24px;
  animation: slideDown 0.3s ease;

  &.with-divider {
    padding-bottom: 24px;
    border-bottom: 1px solid rgba(0, 0, 0, 0.06);

    .dark & {
      border-bottom-color: rgba(255, 255, 255, 0.06);
    }
  }
}

.header-main {
  display: flex;
  align-items: center;
  gap: 16px;
  flex: 1;
  min-width: 0;
}

.back-btn {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid rgba(0, 0, 0, 0.1);
  background: white;
  color: #666;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s ease;
  font-size: 18px;

  .dark & {
    background: #27272a;
    border-color: rgba(255, 255, 255, 0.1);
    color: #999;
  }

  &:hover {
    background: rgba(102, 126, 234, 0.1);
    border-color: #667eea;
    color: #667eea;
  }
}

.header-icon {
  font-size: 32px;
  color: #667eea;
  flex-shrink: 0;
}

.header-content {
  flex: 1;
  min-width: 0;

  .header-title {
    font-size: 28px;
    font-weight: 700;
    color: #1f1f1f;
    margin: 0 0 4px 0;
    line-height: 1.2;

    .dark & {
      color: #f1f1f1;
    }
  }

  .header-description {
    font-size: 14px;
    color: #666;
    margin: 0;
    line-height: 1.5;

    .dark & {
      color: #999;
    }
  }
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}

@keyframes slideDown {
  from {
    opacity: 0;
    transform: translateY(-10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* 响应式适配 */
@media (max-width: 768px) {
  .page-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 16px;
  }

  .header-main {
    width: 100%;
  }

  .header-actions {
    width: 100%;
    justify-content: flex-end;
  }

  .header-content {
    .header-title {
      font-size: 24px;
    }

    .header-description {
      font-size: 13px;
    }
  }
}
</style>

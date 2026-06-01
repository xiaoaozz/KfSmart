<script setup lang="ts">
import { computed } from 'vue';

const props = defineProps({
  /** 标签类型 */
  type: {
    type: String as PropType<'primary' | 'success' | 'warning' | 'error' | 'info' | 'default'>,
    default: 'default'
  },
  /** 尺寸 */
  size: {
    type: String as PropType<'small' | 'medium' | 'large'>,
    default: 'medium'
  },
  /** 是否显示圆点 */
  dot: {
    type: Boolean,
    default: false
  },
  /** 是否可关闭 */
  closable: {
    type: Boolean,
    default: false
  },
  /** 前缀图标 */
  icon: {
    type: String,
    default: ''
  },
  /** 是否为圆形 */
  round: {
    type: Boolean,
    default: false
  },
  /** 是否为边框样式 */
  bordered: {
    type: Boolean,
    default: false
  }
});

const emit = defineEmits<{
  (e: 'close'): void;
}>();

const tagClass = computed(() => {
  return [
    'badge-tag',
    `badge-tag--${props.type}`,
    `badge-tag--${props.size}`,
    {
      'badge-tag--round': props.round,
      'badge-tag--bordered': props.bordered,
      'badge-tag--closable': props.closable
    }
  ];
});

const handleClose = (event: MouseEvent) => {
  event.stopPropagation();
  emit('close');
};
</script>

<template>
  <span :class="tagClass">
    <!-- 圆点指示器 -->
    <span v-if="dot" class="badge-tag__dot"></span>

    <!-- 前缀图标 -->
    <icon-component v-if="icon" :name="icon" class="badge-tag__icon" />

    <!-- 内容 -->
    <span class="badge-tag__content">
      <slot />
    </span>

    <!-- 关闭按钮 -->
    <icon-component
      v-if="closable"
      name="mdi:close"
      class="badge-tag__close"
      @click="handleClose"
    />
  </span>
</template>

<style scoped lang="scss">
.badge-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-weight: 500;
  border-radius: var(--radius-sm);
  transition: all var(--duration-base) var(--ease-smooth);
  white-space: nowrap;
  vertical-align: middle;

  // 尺寸变体
  &--small {
    padding: 2px 8px;
    font-size: var(--text-xs);
    height: 20px;

    .badge-tag__dot {
      width: 6px;
      height: 6px;
    }

    .badge-tag__icon {
      font-size: 12px;
    }

    .badge-tag__close {
      font-size: 12px;
      margin-right: -4px;
    }
  }

  &--medium {
    padding: 4px 12px;
    font-size: var(--text-sm);
    height: 24px;

    .badge-tag__dot {
      width: 8px;
      height: 8px;
    }

    .badge-tag__icon {
      font-size: 14px;
    }

    .badge-tag__close {
      font-size: 14px;
      margin-right: -4px;
    }
  }

  &--large {
    padding: 6px 16px;
    font-size: var(--text-base);
    height: 32px;

    .badge-tag__dot {
      width: 10px;
      height: 10px;
    }

    .badge-tag__icon {
      font-size: 16px;
    }

    .badge-tag__close {
      font-size: 16px;
      margin-right: -4px;
    }
  }

  // 圆形
  &--round {
    border-radius: var(--radius-full);
  }

  // 边框样式
  &--bordered {
    border: 1px solid currentColor;
  }

  &__dot {
    border-radius: 50%;
    flex-shrink: 0;
    animation: pulse 2s cubic-bezier(0.4, 0, 0.6, 1) infinite;
  }

  &__icon {
    flex-shrink: 0;
  }

  &__content {
    line-height: 1;
  }

  &__close {
    flex-shrink: 0;
    cursor: pointer;
    transition: all var(--duration-fast) var(--ease-smooth);

    &:hover {
      transform: scale(1.2);
    }

    &:active {
      transform: scale(0.9);
    }
  }

  // 颜色变体 - 实心样式
  &--primary:not(&--bordered) {
    background: rgba(var(--color-primary-500), 0.15);
    color: rgb(var(--color-primary-600));

    .badge-tag__dot {
      background: rgb(var(--color-primary-500));
    }

    &:hover {
      background: rgba(var(--color-primary-500), 0.25);
    }
  }

  &--success:not(&--bordered) {
    background: rgba(82, 196, 26, 0.15);
    color: #389e0d;

    .badge-tag__dot {
      background: #52c41a;
    }

    &:hover {
      background: rgba(82, 196, 26, 0.25);
    }
  }

  &--warning:not(&--bordered) {
    background: rgba(250, 173, 20, 0.15);
    color: #d48806;

    .badge-tag__dot {
      background: #faad14;
    }

    &:hover {
      background: rgba(250, 173, 20, 0.25);
    }
  }

  &--error:not(&--bordered) {
    background: rgba(245, 34, 45, 0.15);
    color: #cf1322;

    .badge-tag__dot {
      background: #f5222d;
    }

    &:hover {
      background: rgba(245, 34, 45, 0.25);
    }
  }

  &--info:not(&--bordered) {
    background: rgba(32, 128, 240, 0.15);
    color: #096dd9;

    .badge-tag__dot {
      background: #2080f0;
    }

    &:hover {
      background: rgba(32, 128, 240, 0.25);
    }
  }

  &--default:not(&--bordered) {
    background: rgba(0, 0, 0, 0.08);
    color: #595959;

    .badge-tag__dot {
      background: #8c8c8c;
    }

    &:hover {
      background: rgba(0, 0, 0, 0.12);
    }
  }

  // 颜色变体 - 边框样式
  &--primary.badge-tag--bordered {
    background: transparent;
    color: rgb(var(--color-primary-500));
    border-color: rgb(var(--color-primary-500));

    &:hover {
      background: rgba(var(--color-primary-500), 0.1);
    }
  }

  &--success.badge-tag--bordered {
    background: transparent;
    color: #52c41a;
    border-color: #52c41a;

    &:hover {
      background: rgba(82, 196, 26, 0.1);
    }
  }

  &--warning.badge-tag--bordered {
    background: transparent;
    color: #faad14;
    border-color: #faad14;

    &:hover {
      background: rgba(250, 173, 20, 0.1);
    }
  }

  &--error.badge-tag--bordered {
    background: transparent;
    color: #f5222d;
    border-color: #f5222d;

    &:hover {
      background: rgba(245, 34, 45, 0.1);
    }
  }

  &--info.badge-tag--bordered {
    background: transparent;
    color: #2080f0;
    border-color: #2080f0;

    &:hover {
      background: rgba(32, 128, 240, 0.1);
    }
  }

  &--default.badge-tag--bordered {
    background: transparent;
    color: #8c8c8c;
    border-color: #d9d9d9;

    &:hover {
      background: rgba(0, 0, 0, 0.04);
    }
  }
}

// 暗色模式
.dark .badge-tag {
  &--default:not(.badge-tag--bordered) {
    background: rgba(255, 255, 255, 0.1);
    color: #d9d9d9;
  }

  &--default.badge-tag--bordered {
    border-color: rgba(255, 255, 255, 0.3);
    color: #d9d9d9;
  }
}
</style>

<script setup lang="ts">
import { computed } from 'vue';

const props = defineProps({
  /** 图标名称 */
  icon: {
    type: String,
    required: true
  },
  /** 按钮尺寸 */
  size: {
    type: String as PropType<'small' | 'medium' | 'large'>,
    default: 'medium'
  },
  /** 按钮颜色类型 */
  color: {
    type: String as PropType<'primary' | 'success' | 'warning' | 'error' | 'info' | 'default'>,
    default: 'default'
  },
  /** 是否禁用 */
  disabled: {
    type: Boolean,
    default: false
  },
  /** 是否加载中 */
  loading: {
    type: Boolean,
    default: false
  },
  /** 提示文本 */
  tooltip: {
    type: String,
    default: ''
  }
});

const emit = defineEmits<{
  (e: 'click', event: MouseEvent): void;
}>();

const buttonClass = computed(() => {
  return [
    'icon-button',
    `icon-button--${props.size}`,
    `icon-button--${props.color}`,
    {
      'icon-button--disabled': props.disabled,
      'icon-button--loading': props.loading
    }
  ];
});

const handleClick = (event: MouseEvent) => {
  if (!props.disabled && !props.loading) {
    emit('click', event);
  }
};
</script>

<template>
  <button
    :class="buttonClass"
    :disabled="disabled || loading"
    :title="tooltip"
    @click="handleClick"
  >
    <icon-component
      v-if="!loading"
      :name="icon"
      class="icon-button__icon"
    />
    <icon-component
      v-else
      name="mdi:loading"
      class="icon-button__icon icon-button__loading"
    />
  </button>
</template>

<style scoped lang="scss">
.icon-button {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: none;
  background: transparent;
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: all var(--duration-base) var(--ease-smooth);
  outline: none;
  user-select: none;
  flex-shrink: 0;

  &:active:not(&--disabled):not(&--loading) {
    transform: scale(0.95);
  }

  // 尺寸变体
  &--small {
    width: 28px;
    height: 28px;

    .icon-button__icon {
      font-size: 16px;
    }
  }

  &--medium {
    width: 36px;
    height: 36px;

    .icon-button__icon {
      font-size: 20px;
    }
  }

  &--large {
    width: 44px;
    height: 44px;

    .icon-button__icon {
      font-size: 24px;
    }
  }

  // 禁用状态
  &--disabled {
    opacity: 0.4;
    cursor: not-allowed;
  }

  // 加载状态
  &--loading {
    cursor: default;
    pointer-events: none;
  }

  &__icon {
    transition: transform var(--duration-base) var(--ease-smooth);
  }

  &__loading {
    animation: spin 1s linear infinite;
  }

  &:hover:not(&--disabled):not(&--loading) {
    .icon-button__icon {
      transform: scale(1.1);
    }
  }

  // 颜色变体
  &--primary {
    color: rgb(var(--color-primary-500));

    &:hover:not(.icon-button--disabled):not(.icon-button--loading) {
      background: rgba(var(--color-primary-500), 0.1);
    }

    &:active:not(.icon-button--disabled):not(.icon-button--loading) {
      background: rgba(var(--color-primary-500), 0.15);
    }
  }

  &--success {
    color: #52c41a;

    &:hover:not(.icon-button--disabled):not(.icon-button--loading) {
      background: rgba(82, 196, 26, 0.1);
    }

    &:active:not(.icon-button--disabled):not(.icon-button--loading) {
      background: rgba(82, 196, 26, 0.15);
    }
  }

  &--warning {
    color: #faad14;

    &:hover:not(.icon-button--disabled):not(.icon-button--loading) {
      background: rgba(250, 173, 20, 0.1);
    }

    &:active:not(.icon-button--disabled):not(.icon-button--loading) {
      background: rgba(250, 173, 20, 0.15);
    }
  }

  &--error {
    color: #f5222d;

    &:hover:not(.icon-button--disabled):not(.icon-button--loading) {
      background: rgba(245, 34, 45, 0.1);
    }

    &:active:not(.icon-button--disabled):not(.icon-button--loading) {
      background: rgba(245, 34, 45, 0.15);
    }
  }

  &--info {
    color: #2080f0;

    &:hover:not(.icon-button--disabled):not(.icon-button--loading) {
      background: rgba(32, 128, 240, 0.1);
    }

    &:active:not(.icon-button--disabled):not(.icon-button--loading) {
      background: rgba(32, 128, 240, 0.15);
    }
  }

  &--default {
    color: #595959;

    &:hover:not(.icon-button--disabled):not(.icon-button--loading) {
      background: rgba(0, 0, 0, 0.06);
    }

    &:active:not(.icon-button--disabled):not(.icon-button--loading) {
      background: rgba(0, 0, 0, 0.1);
    }
  }
}

// 暗色模式
.dark .icon-button {
  &--default {
    color: #d9d9d9;

    &:hover:not(.icon-button--disabled):not(.icon-button--loading) {
      background: rgba(255, 255, 255, 0.08);
    }

    &:active:not(.icon-button--disabled):not(.icon-button--loading) {
      background: rgba(255, 255, 255, 0.12);
    }
  }
}
</style>

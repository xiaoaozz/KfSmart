<script setup lang="ts">
import { computed } from 'vue';

const props = defineProps({
  /** 按钮变体 */
  variant: {
    type: String as PropType<'solid' | 'outline' | 'ghost' | 'gradient'>,
    default: 'solid'
  },
  /** 按钮颜色类型 */
  color: {
    type: String as PropType<'primary' | 'secondary' | 'success' | 'warning' | 'error' | 'info'>,
    default: 'primary'
  },
  /** 按钮大小 */
  size: {
    type: String as PropType<'small' | 'medium' | 'large'>,
    default: 'medium'
  },
  /** 是否为圆形按钮 */
  circle: {
    type: Boolean,
    default: false
  },
  /** 是否为块级按钮 */
  block: {
    type: Boolean,
    default: false
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
  /** 图标名称 */
  icon: {
    type: String,
    default: ''
  },
  /** 图标位置 */
  iconPosition: {
    type: String as PropType<'left' | 'right'>,
    default: 'left'
  }
});

const emit = defineEmits<{
  (e: 'click', event: MouseEvent): void;
}>();

const buttonClass = computed(() => {
  return [
    'modern-btn',
    `modern-btn--${props.variant}`,
    `modern-btn--${props.color}`,
    `modern-btn--${props.size}`,
    {
      'modern-btn--circle': props.circle,
      'modern-btn--block': props.block,
      'modern-btn--disabled': props.disabled,
      'modern-btn--loading': props.loading,
      'modern-btn--icon-only': props.circle || (!props.$slots.default && props.icon)
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
  <button :class="buttonClass" :disabled="disabled || loading" @click="handleClick">
    <!-- 加载动画 -->
    <span v-if="loading" class="modern-btn__loading">
      <icon-component name="mdi:loading" class="loading-icon" />
    </span>

    <!-- 左侧图标 -->
    <icon-component
      v-if="icon && iconPosition === 'left' && !loading"
      :name="icon"
      class="modern-btn__icon modern-btn__icon--left"
    />

    <!-- 按钮内容 -->
    <span v-if="$slots.default && !circle" class="modern-btn__content">
      <slot />
    </span>

    <!-- 右侧图标 -->
    <icon-component
      v-if="icon && iconPosition === 'right' && !loading"
      :name="icon"
      class="modern-btn__icon modern-btn__icon--right"
    />

    <!-- 波纹效果 -->
    <span class="modern-btn__ripple"></span>
  </button>
</template>

<style scoped lang="scss">
.modern-btn {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  border: none;
  border-radius: var(--radius-md);
  font-weight: 500;
  cursor: pointer;
  transition: all var(--duration-base) var(--ease-smooth);
  overflow: hidden;
  outline: none;
  user-select: none;

  &:active:not(&--disabled):not(&--loading) {
    transform: translateY(1px);
  }

  // 大小变体
  &--small {
    padding: 6px 16px;
    font-size: var(--text-sm);
    height: 32px;

    &.modern-btn--circle {
      width: 32px;
      padding: 0;
    }
  }

  &--medium {
    padding: 10px 24px;
    font-size: var(--text-base);
    height: 40px;

    &.modern-btn--circle {
      width: 40px;
      padding: 0;
    }
  }

  &--large {
    padding: 14px 32px;
    font-size: var(--text-lg);
    height: 48px;

    &.modern-btn--circle {
      width: 48px;
      padding: 0;
    }
  }

  // 圆形按钮
  &--circle {
    border-radius: 50%;
  }

  // 块级按钮
  &--block {
    width: 100%;
    display: flex;
  }

  // 禁用状态
  &--disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }

  // 加载状态
  &--loading {
    cursor: default;
    pointer-events: none;
  }

  // 按钮内容
  &__content {
    position: relative;
    z-index: 1;
  }

  &__icon {
    position: relative;
    z-index: 1;
    font-size: 18px;
    transition: transform var(--duration-base) var(--ease-smooth);

    &--left {
      margin-right: -4px;
    }

    &--right {
      margin-left: -4px;
    }
  }

  &__loading {
    position: absolute;
    display: flex;
    align-items: center;
    justify-content: center;
    z-index: 2;

    .loading-icon {
      font-size: 18px;
      animation: spin 1s linear infinite;
    }
  }

  // 波纹效果
  &__ripple {
    position: absolute;
    top: 50%;
    left: 50%;
    width: 0;
    height: 0;
    border-radius: 50%;
    background: rgba(255, 255, 255, 0.5);
    transform: translate(-50%, -50%);
    pointer-events: none;
  }

  &:active:not(&--disabled):not(&--loading) &__ripple {
    animation: ripple 0.6s ease-out;
  }

  // ===== Solid 变体 =====
  &--solid {
    color: white;

    &.modern-btn--primary {
      background: rgb(var(--color-primary-500));

      &:hover:not(.modern-btn--disabled):not(.modern-btn--loading) {
        background: rgb(var(--color-primary-600));
        box-shadow: var(--shadow-lg);
      }
    }

    &.modern-btn--secondary {
      background: rgb(var(--color-secondary-400));

      &:hover:not(.modern-btn--disabled):not(.modern-btn--loading) {
        background: rgb(var(--color-secondary-500));
        box-shadow: var(--shadow-lg);
      }
    }

    &.modern-btn--success {
      background: #52c41a;

      &:hover:not(.modern-btn--disabled):not(.modern-btn--loading) {
        background: #73d13d;
        box-shadow: var(--shadow-lg);
      }
    }

    &.modern-btn--warning {
      background: #faad14;

      &:hover:not(.modern-btn--disabled):not(.modern-btn--loading) {
        background: #ffc53d;
        box-shadow: var(--shadow-lg);
      }
    }

    &.modern-btn--error {
      background: #f5222d;

      &:hover:not(.modern-btn--disabled):not(.modern-btn--loading) {
        background: #ff4d4f;
        box-shadow: var(--shadow-lg);
      }
    }

    &.modern-btn--info {
      background: #2080f0;

      &:hover:not(.modern-btn--disabled):not(.modern-btn--loading) {
        background: #4098fc;
        box-shadow: var(--shadow-lg);
      }
    }
  }

  // ===== Gradient 变体 =====
  &--gradient {
    color: white;
    background-size: 200% auto;
    animation: gradient-shift 3s ease infinite;

    &.modern-btn--primary {
      background: var(--gradient-primary);

      &:hover:not(.modern-btn--disabled):not(.modern-btn--loading) {
        box-shadow: var(--shadow-glow);
      }
    }

    &.modern-btn--secondary {
      background: var(--gradient-secondary);

      &:hover:not(.modern-btn--disabled):not(.modern-btn--loading) {
        box-shadow: 0 0 20px rgba(79, 172, 254, 0.4);
      }
    }

    &.modern-btn--success {
      background: var(--gradient-success);
    }

    &.modern-btn--warning {
      background: var(--gradient-warning);
    }

    &.modern-btn--error {
      background: var(--gradient-error);
    }

    &.modern-btn--info {
      background: var(--gradient-info);
    }

    &:hover:not(.modern-btn--disabled):not(.modern-btn--loading) {
      transform: translateY(-2px);
      box-shadow: var(--shadow-xl);
    }
  }

  // ===== Outline 变体 =====
  &--outline {
    background: transparent;
    border: 2px solid currentColor;

    &.modern-btn--primary {
      color: rgb(var(--color-primary-500));

      &:hover:not(.modern-btn--disabled):not(.modern-btn--loading) {
        background: rgba(var(--color-primary-500), 0.1);
      }
    }

    &.modern-btn--secondary {
      color: rgb(var(--color-secondary-400));

      &:hover:not(.modern-btn--disabled):not(.modern-btn--loading) {
        background: rgba(var(--color-secondary-400), 0.1);
      }
    }

    &.modern-btn--success {
      color: #52c41a;

      &:hover:not(.modern-btn--disabled):not(.modern-btn--loading) {
        background: rgba(82, 196, 26, 0.1);
      }
    }

    &.modern-btn--warning {
      color: #faad14;

      &:hover:not(.modern-btn--disabled):not(.modern-btn--loading) {
        background: rgba(250, 173, 20, 0.1);
      }
    }

    &.modern-btn--error {
      color: #f5222d;

      &:hover:not(.modern-btn--disabled):not(.modern-btn--loading) {
        background: rgba(245, 34, 45, 0.1);
      }
    }

    &.modern-btn--info {
      color: #2080f0;

      &:hover:not(.modern-btn--disabled):not(.modern-btn--loading) {
        background: rgba(32, 128, 240, 0.1);
      }
    }
  }

  // ===== Ghost 变体 =====
  &--ghost {
    background: transparent;
    border: none;

    &.modern-btn--primary {
      color: rgb(var(--color-primary-500));

      &:hover:not(.modern-btn--disabled):not(.modern-btn--loading) {
        background: rgba(var(--color-primary-500), 0.1);
      }
    }

    &.modern-btn--secondary {
      color: rgb(var(--color-secondary-400));

      &:hover:not(.modern-btn--disabled):not(.modern-btn--loading) {
        background: rgba(var(--color-secondary-400), 0.1);
      }
    }

    &.modern-btn--success {
      color: #52c41a;

      &:hover:not(.modern-btn--disabled):not(.modern-btn--loading) {
        background: rgba(82, 196, 26, 0.1);
      }
    }

    &.modern-btn--warning {
      color: #faad14;

      &:hover:not(.modern-btn--disabled):not(.modern-btn--loading) {
        background: rgba(250, 173, 20, 0.1);
      }
    }

    &.modern-btn--error {
      color: #f5222d;

      &:hover:not(.modern-btn--disabled):not(.modern-btn--loading) {
        background: rgba(245, 34, 45, 0.1);
      }
    }

    &.modern-btn--info {
      color: #2080f0;

      &:hover:not(.modern-btn--disabled):not(.modern-btn--loading) {
        background: rgba(32, 128, 240, 0.1);
      }
    }
  }
}

// 暗色模式
.dark .modern-btn {
  &--outline,
  &--ghost {
    &:hover:not(.modern-btn--disabled):not(.modern-btn--loading) {
      background: rgba(255, 255, 255, 0.05);
    }
  }
}
</style>

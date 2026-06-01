<script setup lang="ts">
import { computed, ref } from 'vue';

interface Props {
  /** 变体样式 */
  variant?: 'primary' | 'secondary' | 'success' | 'warning' | 'error' | 'info' | 'outline';
  /** 尺寸 */
  size?: 'xs' | 'sm' | 'md' | 'lg' | 'xl';
  /** 加载状态 */
  loading?: boolean;
  /** 禁用状态 */
  disabled?: boolean;
  /** 圆角 */
  rounded?: boolean;
  /** 是否块级 */
  block?: boolean;
  /** 图标位置 */
  iconPosition?: 'left' | 'right';
  /** 波纹效果 */
  ripple?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  variant: 'primary',
  size: 'md',
  loading: false,
  disabled: false,
  rounded: true,
  block: false,
  iconPosition: 'left',
  ripple: true
});

const emit = defineEmits<{
  click: [event: MouseEvent];
}>();

const isAnimating = ref(false);

const variantClass = computed(() => {
  const variants = {
    primary: 'gradient-primary text-white shadow-md hover:shadow-lg',
    secondary: 'gradient-secondary text-white shadow-md hover:shadow-lg',
    success: 'gradient-success text-white shadow-md hover:shadow-lg',
    warning: 'gradient-warning text-white shadow-md hover:shadow-lg',
    error: 'gradient-error text-white shadow-md hover:shadow-lg',
    info: 'bg-gradient-to-br from-info to-info/80 text-white shadow-md hover:shadow-lg',
    outline: 'bg-transparent border-2 border-primary-500 text-primary-500 hover:bg-primary-500 hover:text-white'
  };
  
  return variants[props.variant];
});

const sizeClass = computed(() => {
  const sizes = {
    xs: 'px-3 py-1.5 text-xs',
    sm: 'px-4 py-2 text-sm',
    md: 'px-6 py-3 text-base',
    lg: 'px-8 py-4 text-lg',
    xl: 'px-10 py-5 text-xl'
  };
  
  return sizes[props.size];
});

const buttonClasses = computed(() => {
  return [
    'animated-button',
    'relative',
    'overflow-hidden',
    'font-500',
    'transition-all',
    'duration-250',
    'inline-flex',
    'items-center',
    'justify-center',
    'gap-2',
    variantClass.value,
    sizeClass.value,
    {
      'rd-md': props.rounded,
      'w-full': props.block,
      'opacity-60 cursor-not-allowed': props.disabled || props.loading,
      'cursor-pointer hover:scale-105 active:scale-95': !props.disabled && !props.loading,
      'ripple-effect': props.ripple
    }
  ];
});

function handleClick(event: MouseEvent) {
  if (!props.disabled && !props.loading) {
    isAnimating.value = true;
    setTimeout(() => {
      isAnimating.value = false;
    }, 300);
    
    emit('click', event);
  }
}
</script>

<template>
  <button
    :class="buttonClasses"
    :disabled="disabled || loading"
    @click="handleClick"
  >
    <!-- 加载图标 -->
    <div v-if="loading" class="i-eos-icons:loading animate-spin" />
    
    <!-- 左侧图标插槽 -->
    <slot v-if="iconPosition === 'left' && $slots.icon && !loading" name="icon" />
    
    <!-- 按钮文本 -->
    <slot />
    
    <!-- 右侧图标插槽 -->
    <slot v-if="iconPosition === 'right' && $slots.icon && !loading" name="icon" />
    
    <!-- 波纹动画层 -->
    <span
      v-if="ripple"
      class="absolute inset-0 bg-white/20 rd-inherit pointer-events-none opacity-0 transition-opacity"
      :class="{ 'animate-ripple': isAnimating }"
    />
  </button>
</template>

<style scoped>
.animated-button {
  user-select: none;
}

@keyframes ripple {
  0% {
    transform: scale(0);
    opacity: 1;
  }
  100% {
    transform: scale(2.5);
    opacity: 0;
  }
}

.animate-ripple {
  animation: ripple 0.6s ease-out;
}
</style>

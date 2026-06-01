<script setup lang="ts">
import { computed } from 'vue';

interface Props {
  /** 变体样式 */
  variant?: 'primary' | 'secondary' | 'success' | 'warning' | 'error' | 'info';
  /** 尺寸 */
  size?: 'xs' | 'sm' | 'md' | 'lg' | 'xl';
  /** 是否可悬浮 */
  hoverable?: boolean;
  /** 是否可点击 */
  clickable?: boolean;
  /** 加载状态 */
  loading?: boolean;
  /** 禁用状态 */
  disabled?: boolean;
  /** 自定义渐变 */
  gradient?: string;
  /** 是否有阴影 */
  shadow?: boolean;
  /** 阴影等级 */
  shadowLevel?: 'sm' | 'md' | 'lg' | 'card';
  /** 圆角大小 */
  rounded?: 'sm' | 'md' | 'lg' | 'xl' | '2xl';
}

const props = withDefaults(defineProps<Props>(), {
  variant: 'primary',
  size: 'md',
  hoverable: true,
  clickable: false,
  loading: false,
  disabled: false,
  shadow: true,
  shadowLevel: 'card',
  rounded: 'lg'
});

const emit = defineEmits<{
  click: [event: MouseEvent];
  hover: [event: MouseEvent];
}>();

const gradientClass = computed(() => {
  if (props.gradient) return '';
  
  const gradients = {
    primary: 'gradient-primary',
    secondary: 'gradient-secondary',
    success: 'gradient-success',
    warning: 'gradient-warning',
    error: 'gradient-error',
    info: 'bg-gradient-to-br from-info to-info/80'
  };
  
  return gradients[props.variant];
});

const sizeClass = computed(() => {
  const sizes = {
    xs: 'p-3',
    sm: 'p-4',
    md: 'p-6',
    lg: 'p-8',
    xl: 'p-10'
  };
  
  return sizes[props.size];
});

const shadowClass = computed(() => {
  if (!props.shadow) return '';
  
  const shadows = {
    sm: 'shadow-sm',
    md: 'shadow-md',
    lg: 'shadow-lg',
    card: 'shadow-card'
  };
  
  return shadows[props.shadowLevel];
});

const roundedClass = computed(() => {
  const rounded = {
    sm: 'rd-sm',
    md: 'rd-md',
    lg: 'rd-lg',
    xl: 'rd-xl',
    '2xl': 'rd-2xl'
  };
  
  return rounded[props.rounded];
});

const cardClasses = computed(() => {
  return [
    'gradient-card',
    'text-white',
    'relative',
    'overflow-hidden',
    'transition-all',
    'duration-250',
    gradientClass.value,
    sizeClass.value,
    shadowClass.value,
    roundedClass.value,
    {
      'hover:-translate-y-1 hover:shadow-card-hover': props.hoverable && !props.disabled,
      'cursor-pointer': props.clickable && !props.disabled,
      'opacity-60 cursor-not-allowed': props.disabled,
      'animate-pulse': props.loading
    }
  ];
});

function handleClick(event: MouseEvent) {
  if (!props.disabled && !props.loading) {
    emit('click', event);
  }
}

function handleHover(event: MouseEvent) {
  if (!props.disabled) {
    emit('hover', event);
  }
}
</script>

<template>
  <div
    :class="cardClasses"
    :style="gradient ? { background: gradient } : {}"
    @click="handleClick"
    @mouseenter="handleHover"
  >
    <!-- 加载遮罩 -->
    <div
      v-if="loading"
      class="absolute inset-0 flex-cc bg-white/10 backdrop-blur-sm z-10"
    >
      <div class="i-eos-icons:loading text-4xl animate-spin" />
    </div>

    <!-- 头部插槽 -->
    <div v-if="$slots.header" class="card-header mb-4">
      <slot name="header" />
    </div>

    <!-- 主体内容 -->
    <div class="card-body">
      <slot />
    </div>

    <!-- 底部插槽 -->
    <div v-if="$slots.footer" class="card-footer mt-4 pt-4 border-t border-white/20">
      <slot name="footer" />
    </div>

    <!-- 装饰性渐变光晕 -->
    <div
      v-if="hoverable"
      class="absolute -top-20 -right-20 w-40 h-40 bg-white/10 rd-full blur-3xl transition-opacity opacity-0 group-hover:opacity-100"
    />
  </div>
</template>

<style scoped>
.gradient-card {
  position: relative;
}

.gradient-card::before {
  content: '';
  position: absolute;
  inset: 0;
  border-radius: inherit;
  padding: 1px;
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.3), rgba(255, 255, 255, 0.05));
  -webkit-mask: linear-gradient(#fff 0 0) content-box, linear-gradient(#fff 0 0);
  -webkit-mask-composite: xor;
  mask-composite: exclude;
  pointer-events: none;
}
</style>

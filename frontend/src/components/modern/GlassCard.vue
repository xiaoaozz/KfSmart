<script setup lang="ts">
import { computed } from 'vue';

interface Props {
  /** 玻璃效果强度 */
  blur?: number;
  /** 背景透明度 (0-1) */
  opacity?: number;
  /** 边框颜色透明度 (0-1) */
  borderOpacity?: number;
  /** 尺寸 */
  size?: 'xs' | 'sm' | 'md' | 'lg' | 'xl';
  /** 是否可悬浮 */
  hoverable?: boolean;
  /** 圆角大小 */
  rounded?: 'sm' | 'md' | 'lg' | 'xl' | '2xl';
  /** 暗色模式 */
  dark?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  blur: 10,
  opacity: 0.1,
  borderOpacity: 0.2,
  size: 'md',
  hoverable: false,
  rounded: 'lg',
  dark: false
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
    'glass-card',
    'relative',
    'transition-all',
    'duration-250',
    sizeClass.value,
    roundedClass.value,
    {
      'hover:-translate-y-1 hover:shadow-lg': props.hoverable
    }
  ];
});

const cardStyle = computed(() => {
  const bgColor = props.dark
    ? `rgba(0, 0, 0, ${props.opacity})`
    : `rgba(255, 255, 255, ${props.opacity})`;
  
  const borderColor = props.dark
    ? `rgba(255, 255, 255, ${props.borderOpacity})`
    : `rgba(255, 255, 255, ${props.borderOpacity})`;

  return {
    background: bgColor,
    backdropFilter: `blur(${props.blur}px)`,
    WebkitBackdropFilter: `blur(${props.blur}px)`,
    border: `1px solid ${borderColor}`
  };
});
</script>

<template>
  <div
    :class="cardClasses"
    :style="cardStyle"
  >
    <!-- 头部插槽 -->
    <div v-if="$slots.header" class="glass-card-header mb-4">
      <slot name="header" />
    </div>

    <!-- 主体内容 -->
    <div class="glass-card-body">
      <slot />
    </div>

    <!-- 底部插槽 -->
    <div v-if="$slots.footer" class="glass-card-footer mt-4 pt-4 border-t" :class="dark ? 'border-white/10' : 'border-black/10'">
      <slot name="footer" />
    </div>
  </div>
</template>

<style scoped>
.glass-card {
  position: relative;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.1);
}
</style>

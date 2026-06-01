<script setup lang="ts">
import { computed } from 'vue';

defineOptions({
  name: 'CardGridContainer'
});

interface Props {
  /** 网格列数 */
  columns?: number | 'auto';
  /** 最小列宽 (仅当columns为auto时生效) */
  minColumnWidth?: string;
  /** 网格间距 */
  gap?: string;
  /** 是否启用动画 */
  animated?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  columns: 'auto',
  minColumnWidth: '280px',
  gap: '20px',
  animated: true
});

const gridStyle = computed(() => {
  const baseStyle: Record<string, string> = {
    gap: props.gap
  };

  if (props.columns === 'auto') {
    baseStyle.gridTemplateColumns = `repeat(auto-fill, minmax(${props.minColumnWidth}, 1fr))`;
  } else {
    baseStyle.gridTemplateColumns = `repeat(${props.columns}, 1fr)`;
  }

  return baseStyle;
});

const containerClasses = computed(() => {
  return [
    'card-grid-container',
    {
      'animated': props.animated
    }
  ];
});
</script>

<template>
  <div :class="containerClasses" :style="gridStyle">
    <slot />
  </div>
</template>

<style scoped lang="scss">
.card-grid-container {
  display: grid;
  width: 100%;

  &.animated {
    > * {
      animation: fadeInUp 0.4s ease backwards;

      @for $i from 1 through 12 {
        &:nth-child(#{$i}) {
          animation-delay: #{$i * 0.05}s;
        }
      }
    }
  }
}

@keyframes fadeInUp {
  from {
    opacity: 0;
    transform: translateY(20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* 响应式适配 */
@media (max-width: 1200px) {
  .card-grid-container {
    grid-template-columns: repeat(auto-fill, minmax(240px, 1fr)) !important;
  }
}

@media (max-width: 768px) {
  .card-grid-container {
    grid-template-columns: 1fr !important;
    gap: 16px !important;
  }
}
</style>

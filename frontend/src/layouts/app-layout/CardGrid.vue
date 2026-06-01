<template>
  <div class="card-grid" :class="gridClass">
    <slot />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';

interface CardGridProps {
  columns?: 1 | 2 | 3 | 4 | 5 | 6 | 'auto';
  gap?: 'xs' | 'sm' | 'md' | 'lg' | 'xl';
  responsive?: boolean;
  align?: 'start' | 'center' | 'end' | 'stretch';
  justify?: 'start' | 'center' | 'end' | 'space-between' | 'space-around';
}

const props = withDefaults(defineProps<CardGridProps>(), {
  columns: 3,
  gap: 'lg',
  responsive: true,
  align: 'stretch',
  justify: 'start',
});

const gridClass = computed(() => ({
  [`columns-${props.columns}`]: true,
  [`gap-${props.gap}`]: true,
  [`align-${props.align}`]: true,
  [`justify-${props.justify}`]: true,
  'responsive': props.responsive,
}));
</script>

<style scoped>
.card-grid {
  display: grid;
  width: 100%;
}

/* 列数 */
.card-grid.columns-1 {
  grid-template-columns: repeat(1, 1fr);
}

.card-grid.columns-2 {
  grid-template-columns: repeat(2, 1fr);
}

.card-grid.columns-3 {
  grid-template-columns: repeat(3, 1fr);
}

.card-grid.columns-4 {
  grid-template-columns: repeat(4, 1fr);
}

.card-grid.columns-5 {
  grid-template-columns: repeat(5, 1fr);
}

.card-grid.columns-6 {
  grid-template-columns: repeat(6, 1fr);
}

.card-grid.columns-auto {
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
}

/* 间距 */
.card-grid.gap-xs {
  gap: var(--spacing-xs);
}

.card-grid.gap-sm {
  gap: var(--spacing-sm);
}

.card-grid.gap-md {
  gap: var(--spacing-md);
}

.card-grid.gap-lg {
  gap: var(--spacing-lg);
}

.card-grid.gap-xl {
  gap: var(--spacing-xl);
}

/* 对齐 */
.card-grid.align-start {
  align-items: start;
}

.card-grid.align-center {
  align-items: center;
}

.card-grid.align-end {
  align-items: end;
}

.card-grid.align-stretch {
  align-items: stretch;
}

/* 分布 */
.card-grid.justify-start {
  justify-content: start;
}

.card-grid.justify-center {
  justify-content: center;
}

.card-grid.justify-end {
  justify-content: end;
}

.card-grid.justify-space-between {
  justify-content: space-between;
}

.card-grid.justify-space-around {
  justify-content: space-around;
}

/* 响应式设计 */
.card-grid.responsive.columns-6 {
  @media (max-width: 1536px) {
    grid-template-columns: repeat(5, 1fr);
  }
  @media (max-width: 1280px) {
    grid-template-columns: repeat(4, 1fr);
  }
  @media (max-width: 1024px) {
    grid-template-columns: repeat(3, 1fr);
  }
  @media (max-width: 768px) {
    grid-template-columns: repeat(2, 1fr);
  }
  @media (max-width: 640px) {
    grid-template-columns: repeat(1, 1fr);
  }
}

.card-grid.responsive.columns-5 {
  @media (max-width: 1280px) {
    grid-template-columns: repeat(4, 1fr);
  }
  @media (max-width: 1024px) {
    grid-template-columns: repeat(3, 1fr);
  }
  @media (max-width: 768px) {
    grid-template-columns: repeat(2, 1fr);
  }
  @media (max-width: 640px) {
    grid-template-columns: repeat(1, 1fr);
  }
}

.card-grid.responsive.columns-4 {
  @media (max-width: 1024px) {
    grid-template-columns: repeat(3, 1fr);
  }
  @media (max-width: 768px) {
    grid-template-columns: repeat(2, 1fr);
  }
  @media (max-width: 640px) {
    grid-template-columns: repeat(1, 1fr);
  }
}

.card-grid.responsive.columns-3 {
  @media (max-width: 768px) {
    grid-template-columns: repeat(2, 1fr);
  }
  @media (max-width: 640px) {
    grid-template-columns: repeat(1, 1fr);
  }
}

.card-grid.responsive.columns-2 {
  @media (max-width: 640px) {
    grid-template-columns: repeat(1, 1fr);
  }
}

/* 响应式间距调整 */
@media (max-width: 768px) {
  .card-grid.gap-xl {
    gap: var(--spacing-lg);
  }

  .card-grid.gap-lg {
    gap: var(--spacing-md);
  }
}

@media (max-width: 640px) {
  .card-grid.gap-lg,
  .card-grid.gap-md {
    gap: var(--spacing-sm);
  }
}
</style>

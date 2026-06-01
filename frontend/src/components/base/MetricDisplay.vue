<script setup lang="ts">
import { computed } from 'vue';

const props = defineProps({
  /** 标签 */
  label: {
    type: String,
    required: true
  },
  /** 数值 */
  value: {
    type: [String, Number],
    required: true
  },
  /** 单位 */
  unit: {
    type: String,
    default: ''
  },
  /** 状态类型 */
  status: {
    type: String as PropType<'success' | 'warning' | 'error' | 'normal'>,
    default: 'normal'
  },
  /** 趋势 */
  trend: {
    type: String as PropType<'up' | 'down' | 'stable'>,
    default: null
  },
  /** 是否垂直布局 */
  vertical: {
    type: Boolean,
    default: false
  }
});

const statusColor = computed(() => {
  const colors = {
    success: '#52c41a',
    warning: '#faad14',
    error: '#f5222d',
    normal: '#1a202c'
  };
  return colors[props.status];
});
</script>

<template>
  <div :class="['metric-display', { 'metric-display--vertical': vertical }]">
    <span class="metric-display__label">{{ label }}</span>
    <div class="metric-display__value-wrapper">
      <span class="metric-display__value" :style="{ color: statusColor }">
        {{ value }}
      </span>
      <span v-if="unit" class="metric-display__unit">{{ unit }}</span>
      <icon-component
        v-if="trend === 'up'"
        name="mdi:trending-up"
        class="metric-display__trend metric-display__trend--up"
      />
      <icon-component
        v-if="trend === 'down'"
        name="mdi:trending-down"
        class="metric-display__trend metric-display__trend--down"
      />
    </div>
  </div>
</template>

<style scoped lang="scss">
.metric-display {
  display: inline-flex;
  align-items: center;
  gap: 8px;

  &--vertical {
    flex-direction: column;
    align-items: flex-start;
    gap: 4px;
  }

  &__label {
    font-size: var(--text-sm);
    color: #718096;
    font-weight: 500;
  }

  &__value-wrapper {
    display: flex;
    align-items: baseline;
    gap: 4px;
  }

  &__value {
    font-size: var(--text-lg);
    font-weight: 700;
    line-height: 1;
  }

  &__unit {
    font-size: var(--text-xs);
    color: #a0aec0;
    font-weight: 500;
  }

  &__trend {
    font-size: 16px;

    &--up {
      color: #52c41a;
    }

    &--down {
      color: #f5222d;
    }
  }
}

.dark .metric-display {
  &__label {
    color: #a0aec0;
  }
}
</style>

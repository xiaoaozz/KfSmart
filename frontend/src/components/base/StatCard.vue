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
  /** 图标 */
  icon: {
    type: String,
    default: ''
  },
  /** 趋势方向 */
  trend: {
    type: String as PropType<'up' | 'down' | 'stable'>,
    default: null
  },
  /** 趋势值 */
  trendValue: {
    type: String,
    default: ''
  },
  /** 颜色类型 */
  color: {
    type: String as PropType<'primary' | 'success' | 'warning' | 'error' | 'info'>,
    default: 'primary'
  },
  /** 是否显示为小卡片 */
  compact: {
    type: Boolean,
    default: false
  }
});

const cardClass = computed(() => {
  return [
    'stat-card',
    `stat-card--${props.color}`,
    {
      'stat-card--compact': props.compact
    }
  ];
});

const trendClass = computed(() => {
  if (!props.trend) return '';
  return [
    'stat-card__trend',
    `stat-card__trend--${props.trend}`
  ];
});

const trendIcon = computed(() => {
  if (props.trend === 'up') return 'mdi:trending-up';
  if (props.trend === 'down') return 'mdi:trending-down';
  return 'mdi:minus';
});
</script>

<template>
  <div :class="cardClass">
    <!-- 渐变背景装饰 -->
    <div class="stat-card__bg"></div>

    <!-- 图标 -->
    <div v-if="icon" class="stat-card__icon">
      <icon-component :name="icon" />
    </div>

    <!-- 内容 -->
    <div class="stat-card__content">
      <div class="stat-card__label">{{ label }}</div>
      <div class="stat-card__value-wrapper">
        <span class="stat-card__value">{{ value }}</span>
        <span v-if="unit" class="stat-card__unit">{{ unit }}</span>
      </div>

      <!-- 趋势指示器 -->
      <div v-if="trend" :class="trendClass">
        <icon-component :name="trendIcon" class="trend-icon" />
        <span v-if="trendValue">{{ trendValue }}</span>
      </div>
    </div>

    <!-- 操作插槽 -->
    <div v-if="$slots.action" class="stat-card__action">
      <slot name="action" />
    </div>
  </div>
</template>

<style scoped lang="scss">
.stat-card {
  position: relative;
  padding: 20px;
  background: white;
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
  transition: all var(--duration-base) var(--ease-out-cubic);
  overflow: hidden;

  &:hover {
    box-shadow: var(--shadow-card-hover);
    transform: translateY(-2px);
  }

  &__bg {
    position: absolute;
    top: 0;
    right: 0;
    width: 120px;
    height: 120px;
    border-radius: 50%;
    opacity: 0.1;
    transform: translate(30%, -30%);
    transition: all var(--duration-base) var(--ease-out-cubic);
  }

  &:hover &__bg {
    transform: translate(25%, -25%) scale(1.1);
    opacity: 0.15;
  }

  &__icon {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 48px;
    height: 48px;
    margin-bottom: 12px;
    border-radius: var(--radius-md);
    font-size: 24px;
    color: white;
    transition: all var(--duration-base) var(--ease-out-cubic);
  }

  &__content {
    position: relative;
    z-index: 1;
  }

  &__label {
    font-size: var(--text-sm);
    color: #718096;
    margin-bottom: 8px;
    font-weight: 500;
  }

  &__value-wrapper {
    display: flex;
    align-items: baseline;
    gap: 4px;
    margin-bottom: 8px;
  }

  &__value {
    font-size: var(--text-3xl);
    font-weight: 700;
    color: #1a202c;
    line-height: 1;
  }

  &__unit {
    font-size: var(--text-sm);
    color: #a0aec0;
    font-weight: 500;
  }

  &__trend {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    padding: 2px 8px;
    border-radius: var(--radius-sm);
    font-size: var(--text-xs);
    font-weight: 600;

    .trend-icon {
      font-size: 14px;
    }

    &--up {
      background: rgba(82, 196, 26, 0.1);
      color: #52c41a;
    }

    &--down {
      background: rgba(245, 34, 45, 0.1);
      color: #f5222d;
    }

    &--stable {
      background: rgba(140, 140, 140, 0.1);
      color: #8c8c8c;
    }
  }

  &__action {
    position: absolute;
    top: 16px;
    right: 16px;
    z-index: 2;
  }

  // 紧凑模式
  &--compact {
    padding: 16px;

    .stat-card__icon {
      width: 40px;
      height: 40px;
      font-size: 20px;
      margin-bottom: 8px;
    }

    .stat-card__value {
      font-size: var(--text-2xl);
    }
  }

  // 颜色变体
  &--primary {
    .stat-card__bg {
      background: rgb(var(--color-primary-500));
    }

    .stat-card__icon {
      background: var(--gradient-primary);
    }
  }

  &--success {
    .stat-card__bg {
      background: #52c41a;
    }

    .stat-card__icon {
      background: var(--gradient-success);
    }
  }

  &--warning {
    .stat-card__bg {
      background: #faad14;
    }

    .stat-card__icon {
      background: var(--gradient-warning);
    }
  }

  &--error {
    .stat-card__bg {
      background: #f5222d;
    }

    .stat-card__icon {
      background: var(--gradient-error);
    }
  }

  &--info {
    .stat-card__bg {
      background: #2080f0;
    }

    .stat-card__icon {
      background: var(--gradient-info);
    }
  }
}

// 暗色模式
.dark .stat-card {
  background: #2d3748;

  &__label {
    color: #a0aec0;
  }

  &__value {
    color: #f7fafc;
  }
}
</style>

<script setup lang="ts">
import { computed } from 'vue';

export interface StatItem {
  key: string;
  label: string;
  value: string | number;
}

const props = defineProps({
  /** 节点类型 */
  type: {
    type: String as PropType<'gateway' | 'agent' | 'tool' | 'output' | 'database'>,
    default: 'agent'
  },
  /** 图标 */
  icon: {
    type: String,
    required: true
  },
  /** 标题 */
  title: {
    type: String,
    required: true
  },
  /** 副标题 */
  subtitle: {
    type: String,
    default: ''
  },
  /** 标签列表 */
  tags: {
    type: Array as PropType<string[]>,
    default: () => []
  },
  /** 统计数据 */
  stats: {
    type: Array as PropType<StatItem[]>,
    default: () => []
  },
  /** 是否激活 */
  active: {
    type: Boolean,
    default: false
  }
});

const nodeClass = computed(() => {
  return [
    'flow-node',
    `flow-node--${props.type}`,
    {
      'flow-node--active': props.active
    }
  ];
});
</script>

<template>
  <div :class="nodeClass">
    <!-- 节点图标 -->
    <div class="flow-node__icon">
      <icon-component :name="icon" />
    </div>

    <!-- 节点内容 -->
    <div class="flow-node__content">
      <h4 class="flow-node__title">{{ title }}</h4>
      <p v-if="subtitle" class="flow-node__subtitle">{{ subtitle }}</p>

      <!-- 标签 -->
      <div v-if="tags.length > 0" class="flow-node__tags">
        <BadgeTag
          v-for="(tag, index) in tags"
          :key="index"
          size="small"
          type="primary"
        >
          {{ tag }}
        </BadgeTag>
      </div>
    </div>

    <!-- 统计数据 -->
    <div v-if="stats.length > 0" class="flow-node__stats">
      <div v-for="stat in stats" :key="stat.key" class="flow-node__stat">
        <span class="stat-value">{{ stat.value }}</span>
        <span class="stat-label">{{ stat.label }}</span>
      </div>
    </div>

    <!-- 连接点 -->
    <div class="flow-node__connector flow-node__connector--left"></div>
    <div class="flow-node__connector flow-node__connector--right"></div>
  </div>
</template>

<style scoped lang="scss">
.flow-node {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 20px;
  background: white;
  border: 2px solid rgba(0, 0, 0, 0.1);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
  transition: all var(--duration-base) var(--ease-smooth);
  min-width: 200px;

  &:hover {
    box-shadow: var(--shadow-card-hover);
    transform: translateY(-2px);
  }

  &--active {
    border-color: rgb(var(--color-primary-500));
    box-shadow: 0 0 0 3px rgba(var(--color-primary-500), 0.1);
  }

  &__icon {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 48px;
    height: 48px;
    border-radius: var(--radius-md);
    font-size: 24px;
    color: white;
    flex-shrink: 0;
  }

  &__content {
    flex: 1;
  }

  &__title {
    font-size: var(--text-base);
    font-weight: 600;
    color: #1a202c;
    margin: 0 0 4px;
  }

  &__subtitle {
    font-size: var(--text-sm);
    color: #718096;
    margin: 0 0 8px;
  }

  &__tags {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
  }

  &__stats {
    display: flex;
    gap: 16px;
    padding-top: 12px;
    border-top: 1px solid rgba(0, 0, 0, 0.06);
  }

  &__stat {
    display: flex;
    flex-direction: column;
    gap: 2px;

    .stat-value {
      font-size: var(--text-lg);
      font-weight: 700;
      color: #1a202c;
    }

    .stat-label {
      font-size: var(--text-xs);
      color: #a0aec0;
    }
  }

  &__connector {
    position: absolute;
    width: 12px;
    height: 12px;
    background: white;
    border: 2px solid rgb(var(--color-primary-500));
    border-radius: 50%;
    top: 50%;
    transform: translateY(-50%);
    z-index: 1;

    &--left {
      left: -6px;
    }

    &--right {
      right: -6px;
    }
  }

  // 节点类型样式
  &--gateway &__icon {
    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  }

  &--agent &__icon {
    background: linear-gradient(135deg, #4facfe 0%, #00f2fe 100%);
  }

  &--tool &__icon {
    background: linear-gradient(135deg, #52c41a 0%, #7cb305 100%);
  }

  &--output &__icon {
    background: linear-gradient(135deg, #faad14 0%, #fa8c16 100%);
  }

  &--database &__icon {
    background: linear-gradient(135deg, #2080f0 0%, #1890ff 100%);
  }
}

// 暗色模式
.dark .flow-node {
  background: #2d3748;
  border-color: rgba(255, 255, 255, 0.1);

  &__title {
    color: #f7fafc;
  }

  &__subtitle {
    color: #a0aec0;
  }

  &__stats {
    border-top-color: rgba(255, 255, 255, 0.1);
  }

  &__stat .stat-value {
    color: #f7fafc;
  }
}
</style>

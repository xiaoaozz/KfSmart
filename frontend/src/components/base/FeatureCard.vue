<script setup lang="ts">
import { computed } from 'vue';
import type { PropType } from 'vue';

export interface TagItem {
  label: string;
  type?: 'primary' | 'success' | 'warning' | 'error' | 'info' | 'default';
}

const props = defineProps({
  /** 图标名称 */
  icon: {
    type: String,
    default: ''
  },
  /** 标题 */
  title: {
    type: String,
    required: true
  },
  /** 描述 */
  description: {
    type: String,
    default: ''
  },
  /** 标签列表 */
  tags: {
    type: Array as PropType<TagItem[]>,
    default: () => []
  },
  /** 是否可点击 */
  clickable: {
    type: Boolean,
    default: true
  },
  /** 是否显示为激活状态 */
  active: {
    type: Boolean,
    default: false
  },
  /** 状态标签文字 */
  statusText: {
    type: String,
    default: ''
  },
  /** 状态类型 */
  statusType: {
    type: String as PropType<'running' | 'stopped' | 'pending' | 'error'>,
    default: 'stopped'
  }
});

const emit = defineEmits<{
  (e: 'click'): void;
}>();

const cardClass = computed(() => {
  return [
    'feature-card',
    {
      'feature-card--clickable': props.clickable,
      'feature-card--active': props.active
    }
  ];
});

const statusClass = computed(() => {
  return `status-badge status-badge--${props.statusType}`;
});

const handleClick = () => {
  if (props.clickable) {
    emit('click');
  }
};
</script>

<template>
  <div :class="cardClass" @click="handleClick">
    <!-- 状态标签 -->
    <div v-if="statusText" :class="statusClass">
      <span class="status-dot"></span>
      <span>{{ statusText }}</span>
    </div>

    <!-- 图标区域 -->
    <div v-if="icon" class="feature-card__icon">
      <div class="icon-wrapper">
        <icon-component :name="icon" class="text-icon-xl" />
      </div>
    </div>

    <!-- 内容区域 -->
    <div class="feature-card__content">
      <h3 class="feature-card__title">{{ title }}</h3>
      <p v-if="description" class="feature-card__description">{{ description }}</p>

      <!-- 标签区域 -->
      <div v-if="tags.length > 0" class="feature-card__tags">
        <NTag
          v-for="(tag, index) in tags"
          :key="index"
          :type="tag.type || 'default'"
          size="small"
          round
        >
          {{ tag.label }}
        </NTag>
      </div>
    </div>

    <!-- 底部插槽 -->
    <div v-if="$slots.footer" class="feature-card__footer">
      <slot name="footer" />
    </div>

    <!-- 右侧操作插槽 -->
    <div v-if="$slots.actions" class="feature-card__actions">
      <slot name="actions" />
    </div>
  </div>
</template>

<style scoped lang="scss">
.feature-card {
  position: relative;
  padding: 24px;
  background: linear-gradient(135deg, #ffffff 0%, #f8f9ff 100%);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
  transition: all var(--duration-base) var(--ease-out-cubic);
  overflow: hidden;

  &::before {
    content: '';
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    height: 3px;
    background: var(--gradient-primary);
    opacity: 0;
    transition: opacity var(--duration-base) var(--ease-out-cubic);
  }

  &--clickable {
    cursor: pointer;

    &:hover {
      transform: translateY(-4px);
      box-shadow: var(--shadow-card-hover);

      &::before {
        opacity: 1;
      }

      .feature-card__icon .icon-wrapper {
        transform: scale(1.1) rotate(5deg);
        background: var(--gradient-primary);
        color: white;
      }
    }

    &:active {
      transform: translateY(-2px);
    }
  }

  &--active {
    &::before {
      opacity: 1;
    }

    .feature-card__icon .icon-wrapper {
      background: var(--gradient-primary);
      color: white;
    }
  }

  &__icon {
    margin-bottom: 16px;

    .icon-wrapper {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 64px;
      height: 64px;
      background: rgba(var(--color-primary-500), 0.1);
      border-radius: var(--radius-lg);
      color: rgb(var(--color-primary-500));
      transition: all var(--duration-base) var(--ease-out-cubic);
    }
  }

  &__content {
    flex: 1;
  }

  &__title {
    font-size: var(--text-lg);
    font-weight: 600;
    color: #1a202c;
    margin-bottom: 8px;
    line-height: 1.4;
  }

  &__description {
    font-size: var(--text-sm);
    color: #718096;
    line-height: 1.6;
    margin-bottom: 12px;
  }

  &__tags {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
  }

  &__footer {
    margin-top: 16px;
    padding-top: 16px;
    border-top: 1px solid rgba(0, 0, 0, 0.06);
  }

  &__actions {
    position: absolute;
    top: 24px;
    right: 24px;
  }
}

// 状态标签
.status-badge {
  position: absolute;
  top: 12px;
  right: 12px;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 12px;
  border-radius: var(--radius-full);
  font-size: var(--text-xs);
  font-weight: 500;
  z-index: 1;

  .status-dot {
    width: 8px;
    height: 8px;
    border-radius: 50%;
    animation: pulse 2s cubic-bezier(0.4, 0, 0.6, 1) infinite;
  }

  &--running {
    background: rgba(82, 196, 26, 0.1);
    color: #52c41a;

    .status-dot {
      background: #52c41a;
    }
  }

  &--stopped {
    background: rgba(140, 140, 140, 0.1);
    color: #8c8c8c;

    .status-dot {
      background: #8c8c8c;
      animation: none;
    }
  }

  &--pending {
    background: rgba(250, 173, 20, 0.1);
    color: #faad14;

    .status-dot {
      background: #faad14;
    }
  }

  &--error {
    background: rgba(245, 34, 45, 0.1);
    color: #f5222d;

    .status-dot {
      background: #f5222d;
    }
  }
}

// 暗色模式
.dark .feature-card {
  background: linear-gradient(135deg, #2d3748 0%, #1a202c 100%);

  &__title {
    color: #f7fafc;
  }

  &__description {
    color: #a0aec0;
  }

  &__footer {
    border-top-color: rgba(255, 255, 255, 0.1);
  }
}
</style>

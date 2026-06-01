<script setup lang="ts">
import { ref, computed } from 'vue';

const props = defineProps({
  /** 标题 */
  title: {
    type: String,
    default: ''
  },
  /** 是否可折叠 */
  collapsible: {
    type: Boolean,
    default: false
  },
  /** 默认是否展开 */
  defaultExpanded: {
    type: Boolean,
    default: true
  },
  /** 是否显示边框 */
  bordered: {
    type: Boolean,
    default: true
  },
  /** 是否显示阴影 */
  shadow: {
    type: Boolean,
    default: true
  },
  /** 内边距大小 */
  padding: {
    type: String as PropType<'none' | 'small' | 'medium' | 'large'>,
    default: 'medium'
  }
});

const expanded = ref(props.defaultExpanded);

const toggleExpanded = () => {
  if (props.collapsible) {
    expanded.value = !expanded.value;
  }
};

const cardClass = computed(() => {
  return [
    'info-card',
    `info-card--padding-${props.padding}`,
    {
      'info-card--bordered': props.bordered,
      'info-card--shadow': props.shadow,
      'info-card--collapsible': props.collapsible,
      'info-card--collapsed': props.collapsible && !expanded.value
    }
  ];
});
</script>

<template>
  <div :class="cardClass">
    <!-- 头部 -->
    <div v-if="title || $slots.header || $slots.extra" class="info-card__header" @click="toggleExpanded">
      <div class="info-card__header-left">
        <!-- 折叠图标 -->
        <icon-component
          v-if="collapsible"
          :name="expanded ? 'mdi:chevron-down' : 'mdi:chevron-right'"
          class="info-card__collapse-icon"
        />

        <!-- 标题 -->
        <h3 v-if="title" class="info-card__title">{{ title }}</h3>

        <!-- 自定义header插槽 -->
        <slot name="header" />
      </div>

      <!-- 右侧操作区 -->
      <div v-if="$slots.extra" class="info-card__header-extra">
        <slot name="extra" />
      </div>
    </div>

    <!-- 内容区 -->
    <transition name="expand">
      <div v-show="expanded" class="info-card__content">
        <slot />
      </div>
    </transition>

    <!-- 底部 -->
    <div v-if="$slots.footer" class="info-card__footer">
      <slot name="footer" />
    </div>
  </div>
</template>

<style scoped lang="scss">
.info-card {
  background: white;
  border-radius: var(--radius-lg);
  transition: all var(--duration-base) var(--ease-out-cubic);

  &--bordered {
    border: 1px solid rgba(0, 0, 0, 0.06);
  }

  &--shadow {
    box-shadow: var(--shadow-card);

    &:hover {
      box-shadow: var(--shadow-card-hover);
    }
  }

  // 内边距变体
  &--padding-none {
    .info-card__content {
      padding: 0;
    }
  }

  &--padding-small {
    .info-card__content {
      padding: 12px;
    }

    .info-card__header {
      padding: 12px;
    }

    .info-card__footer {
      padding: 12px;
    }
  }

  &--padding-medium {
    .info-card__content {
      padding: 20px;
    }

    .info-card__header {
      padding: 20px;
    }

    .info-card__footer {
      padding: 20px;
    }
  }

  &--padding-large {
    .info-card__content {
      padding: 32px;
    }

    .info-card__header {
      padding: 32px;
    }

    .info-card__footer {
      padding: 32px;
    }
  }

  // 可折叠
  &--collapsible {
    .info-card__header {
      cursor: pointer;
      user-select: none;

      &:hover {
        background: rgba(0, 0, 0, 0.02);
      }
    }
  }

  &__header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    border-bottom: 1px solid rgba(0, 0, 0, 0.06);
    transition: all var(--duration-fast) var(--ease-out-cubic);
  }

  &__header-left {
    display: flex;
    align-items: center;
    gap: 8px;
    flex: 1;
  }

  &__collapse-icon {
    font-size: 20px;
    color: #718096;
    transition: transform var(--duration-base) var(--ease-out-cubic);
  }

  &__title {
    font-size: var(--text-lg);
    font-weight: 600;
    color: #1a202c;
    margin: 0;
  }

  &__header-extra {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  &__content {
    transition: all var(--duration-base) var(--ease-out-cubic);
  }

  &__footer {
    border-top: 1px solid rgba(0, 0, 0, 0.06);
  }
}

// 展开/折叠动画
.expand-enter-active,
.expand-leave-active {
  transition: all var(--duration-base) var(--ease-out-cubic);
  overflow: hidden;
}

.expand-enter-from,
.expand-leave-to {
  opacity: 0;
  max-height: 0;
}

.expand-enter-to,
.expand-leave-from {
  opacity: 1;
  max-height: 2000px;
}

// 暗色模式
.dark .info-card {
  background: #2d3748;

  &--bordered {
    border-color: rgba(255, 255, 255, 0.1);
  }

  &__header {
    border-bottom-color: rgba(255, 255, 255, 0.1);
  }

  &--collapsible .info-card__header:hover {
    background: rgba(255, 255, 255, 0.02);
  }

  &__title {
    color: #f7fafc;
  }

  &__footer {
    border-top-color: rgba(255, 255, 255, 0.1);
  }
}
</style>

<script setup lang="ts">
import { ref, computed } from 'vue';

const props = defineProps({
  /** 搜索文本 */
  modelValue: {
    type: String,
    default: ''
  },
  /** 占位符 */
  placeholder: {
    type: String,
    default: '搜索...'
  },
  /** 是否禁用 */
  disabled: {
    type: Boolean,
    default: false
  },
  /** 尺寸 */
  size: {
    type: String as PropType<'small' | 'medium' | 'large'>,
    default: 'medium'
  },
  /** 是否显示搜索按钮 */
  showSearchButton: {
    type: Boolean,
    default: false
  },
  /** 是否加载中 */
  loading: {
    type: Boolean,
    default: false
  }
});

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void;
  (e: 'search', value: string): void;
  (e: 'clear'): void;
  (e: 'focus'): void;
  (e: 'blur'): void;
}>();

const isFocused = ref(false);
const inputRef = ref<HTMLInputElement>();

const searchBarClass = computed(() => {
  return [
    'search-bar',
    `search-bar--${props.size}`,
    {
      'search-bar--focused': isFocused.value,
      'search-bar--disabled': props.disabled,
      'search-bar--with-button': props.showSearchButton
    }
  ];
});

const showClear = computed(() => {
  return props.modelValue && !props.disabled;
});

const handleInput = (event: Event) => {
  const target = event.target as HTMLInputElement;
  emit('update:modelValue', target.value);
};

const handleFocus = () => {
  isFocused.value = true;
  emit('focus');
};

const handleBlur = () => {
  isFocused.value = false;
  emit('blur');
};

const handleSearch = () => {
  if (!props.disabled) {
    emit('search', props.modelValue);
  }
};

const handleClear = () => {
  emit('update:modelValue', '');
  emit('clear');
  inputRef.value?.focus();
};

const handleKeydown = (event: KeyboardEvent) => {
  if (event.key === 'Enter' && !props.disabled) {
    handleSearch();
  }
};

const focus = () => {
  inputRef.value?.focus();
};

defineExpose({
  focus
});
</script>

<template>
  <div :class="searchBarClass">
    <!-- 搜索图标 -->
    <div class="search-bar__icon">
      <icon-component v-if="!loading" name="mdi:magnify" />
      <icon-component v-else name="mdi:loading" class="loading-icon" />
    </div>

    <!-- 输入框 -->
    <input
      ref="inputRef"
      class="search-bar__input"
      type="text"
      :value="modelValue"
      :placeholder="placeholder"
      :disabled="disabled"
      @input="handleInput"
      @focus="handleFocus"
      @blur="handleBlur"
      @keydown="handleKeydown"
    />

    <!-- 清除按钮 -->
    <transition name="fade">
      <div v-if="showClear" class="search-bar__clear" @click="handleClear">
        <icon-component name="mdi:close-circle" />
      </div>
    </transition>

    <!-- 搜索按钮 -->
    <button
      v-if="showSearchButton"
      class="search-bar__button"
      :disabled="disabled || loading"
      @click="handleSearch"
    >
      搜索
    </button>
  </div>
</template>

<style scoped lang="scss">
.search-bar {
  position: relative;
  display: flex;
  align-items: center;
  background: white;
  border: 1px solid rgba(0, 0, 0, 0.15);
  border-radius: var(--radius-full);
  transition: all var(--duration-base) var(--ease-smooth);
  overflow: hidden;

  &:hover:not(&--disabled) {
    border-color: rgba(var(--color-primary-500), 0.5);
    box-shadow: var(--shadow-sm);
  }

  &--focused {
    border-color: rgb(var(--color-primary-500));
    box-shadow: 0 0 0 3px rgba(var(--color-primary-500), 0.1);
  }

  &--disabled {
    background: rgba(0, 0, 0, 0.04);
    cursor: not-allowed;

    .search-bar__input {
      cursor: not-allowed;
    }
  }

  // 尺寸变体
  &--small {
    height: 32px;
    padding: 0 12px;

    .search-bar__icon {
      font-size: 16px;
    }

    .search-bar__input {
      font-size: var(--text-sm);
    }

    .search-bar__button {
      height: 24px;
      padding: 0 12px;
      font-size: var(--text-sm);
    }
  }

  &--medium {
    height: 40px;
    padding: 0 16px;

    .search-bar__icon {
      font-size: 18px;
    }

    .search-bar__input {
      font-size: var(--text-base);
    }

    .search-bar__button {
      height: 32px;
      padding: 0 16px;
      font-size: var(--text-base);
    }
  }

  &--large {
    height: 48px;
    padding: 0 20px;

    .search-bar__icon {
      font-size: 20px;
    }

    .search-bar__input {
      font-size: var(--text-lg);
    }

    .search-bar__button {
      height: 40px;
      padding: 0 20px;
      font-size: var(--text-lg);
    }
  }

  &--with-button {
    padding-right: 4px;
  }

  &__icon {
    display: flex;
    align-items: center;
    justify-content: center;
    color: #718096;
    margin-right: 8px;
    flex-shrink: 0;

    .loading-icon {
      animation: spin 1s linear infinite;
    }
  }

  &__input {
    flex: 1;
    border: none;
    outline: none;
    background: transparent;
    color: #1a202c;
    font-family: inherit;
    min-width: 0;

    &::placeholder {
      color: #a0aec0;
    }

    &:disabled {
      color: #a0aec0;
    }
  }

  &__clear {
    display: flex;
    align-items: center;
    justify-content: center;
    margin-left: 8px;
    color: #a0aec0;
    cursor: pointer;
    font-size: 16px;
    flex-shrink: 0;
    transition: all var(--duration-fast) var(--ease-smooth);

    &:hover {
      color: #718096;
      transform: scale(1.1);
    }
  }

  &__button {
    margin-left: 8px;
    padding: 0 16px;
    background: var(--gradient-primary);
    color: white;
    border: none;
    border-radius: var(--radius-full);
    font-weight: 500;
    cursor: pointer;
    flex-shrink: 0;
    transition: all var(--duration-base) var(--ease-smooth);

    &:hover:not(:disabled) {
      transform: translateX(2px);
      box-shadow: var(--shadow-md);
    }

    &:active:not(:disabled) {
      transform: translateX(0);
    }

    &:disabled {
      opacity: 0.5;
      cursor: not-allowed;
    }
  }
}

// 淡入淡出动画
.fade-enter-active,
.fade-leave-active {
  transition: all var(--duration-fast) var(--ease-smooth);
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
  transform: scale(0.8);
}

// 暗色模式
.dark {
  .search-bar {
    background: rgba(255, 255, 255, 0.05);
    border-color: rgba(255, 255, 255, 0.15);

    &:hover:not(.search-bar--disabled) {
      border-color: rgba(var(--color-primary-400), 0.5);
    }

    &--focused {
      border-color: rgb(var(--color-primary-400));
      box-shadow: 0 0 0 3px rgba(var(--color-primary-400), 0.2);
    }

    &--disabled {
      background: rgba(255, 255, 255, 0.02);
    }

    &__input {
      color: #f7fafc;

      &::placeholder {
        color: #718096;
      }
    }

    &__icon {
      color: #a0aec0;
    }
  }
}
</style>

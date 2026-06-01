<script setup lang="ts">
import { ref, computed } from 'vue';

const props = defineProps({
  /** 输入框值 */
  modelValue: {
    type: [String, Number],
    default: ''
  },
  /** 输入框类型 */
  type: {
    type: String,
    default: 'text'
  },
  /** 标签 */
  label: {
    type: String,
    default: ''
  },
  /** 占位符 */
  placeholder: {
    type: String,
    default: ''
  },
  /** 前缀图标 */
  prefixIcon: {
    type: String,
    default: ''
  },
  /** 后缀图标 */
  suffixIcon: {
    type: String,
    default: ''
  },
  /** 辅助文本 */
  helperText: {
    type: String,
    default: ''
  },
  /** 错误文本 */
  errorText: {
    type: String,
    default: ''
  },
  /** 是否禁用 */
  disabled: {
    type: Boolean,
    default: false
  },
  /** 是否只读 */
  readonly: {
    type: Boolean,
    default: false
  },
  /** 是否必填 */
  required: {
    type: Boolean,
    default: false
  },
  /** 是否多行 */
  multiline: {
    type: Boolean,
    default: false
  },
  /** 多行时的行数 */
  rows: {
    type: Number,
    default: 3
  },
  /** 尺寸 */
  size: {
    type: String as PropType<'small' | 'medium' | 'large'>,
    default: 'medium'
  },
  /** 是否显示清除按钮 */
  clearable: {
    type: Boolean,
    default: false
  },
  /** 是否显示字数统计 */
  showCount: {
    type: Boolean,
    default: false
  },
  /** 最大长度 */
  maxlength: {
    type: Number,
    default: undefined
  }
});

const emit = defineEmits<{
  (e: 'update:modelValue', value: string | number): void;
  (e: 'focus', event: FocusEvent): void;
  (e: 'blur', event: FocusEvent): void;
  (e: 'clear'): void;
}>();

const isFocused = ref(false);
const inputRef = ref<HTMLInputElement | HTMLTextAreaElement>();

const inputClass = computed(() => {
  return [
    'modern-input',
    `modern-input--${props.size}`,
    {
      'modern-input--focused': isFocused.value,
      'modern-input--disabled': props.disabled,
      'modern-input--error': props.errorText,
      'modern-input--has-prefix': props.prefixIcon || props.$slots.prefix,
      'modern-input--has-suffix': props.suffixIcon || props.$slots.suffix || (props.clearable && props.modelValue)
    }
  ];
});

const showClearIcon = computed(() => {
  return props.clearable && props.modelValue && !props.disabled && !props.readonly;
});

const characterCount = computed(() => {
  const length = String(props.modelValue).length;
  return props.maxlength ? `${length}/${props.maxlength}` : length;
});

const handleInput = (event: Event) => {
  const target = event.target as HTMLInputElement | HTMLTextAreaElement;
  emit('update:modelValue', target.value);
};

const handleFocus = (event: FocusEvent) => {
  isFocused.value = true;
  emit('focus', event);
};

const handleBlur = (event: FocusEvent) => {
  isFocused.value = false;
  emit('blur', event);
};

const handleClear = () => {
  emit('update:modelValue', '');
  emit('clear');
  inputRef.value?.focus();
};

const focus = () => {
  inputRef.value?.focus();
};

defineExpose({
  focus
});
</script>

<template>
  <div class="modern-input-wrapper">
    <!-- 标签 -->
    <label v-if="label" class="modern-input__label">
      {{ label }}
      <span v-if="required" class="modern-input__required">*</span>
    </label>

    <!-- 输入框容器 -->
    <div :class="inputClass">
      <!-- 前缀插槽 -->
      <div v-if="prefixIcon || $slots.prefix" class="modern-input__prefix">
        <slot name="prefix">
          <icon-component v-if="prefixIcon" :name="prefixIcon" class="modern-input__icon" />
        </slot>
      </div>

      <!-- 输入框 -->
      <textarea
        v-if="multiline"
        ref="inputRef"
        class="modern-input__field modern-input__textarea"
        :value="modelValue"
        :placeholder="placeholder"
        :disabled="disabled"
        :readonly="readonly"
        :rows="rows"
        :maxlength="maxlength"
        @input="handleInput"
        @focus="handleFocus"
        @blur="handleBlur"
      />

      <input
        v-else
        ref="inputRef"
        class="modern-input__field"
        :type="type"
        :value="modelValue"
        :placeholder="placeholder"
        :disabled="disabled"
        :readonly="readonly"
        :maxlength="maxlength"
        @input="handleInput"
        @focus="handleFocus"
        @blur="handleBlur"
      />

      <!-- 清除按钮 -->
      <div v-if="showClearIcon" class="modern-input__suffix" @click="handleClear">
        <icon-component name="mdi:close-circle" class="modern-input__icon modern-input__clear" />
      </div>

      <!-- 后缀插槽 -->
      <div v-else-if="suffixIcon || $slots.suffix" class="modern-input__suffix">
        <slot name="suffix">
          <icon-component v-if="suffixIcon" :name="suffixIcon" class="modern-input__icon" />
        </slot>
      </div>
    </div>

    <!-- 底部信息 -->
    <div v-if="helperText || errorText || (showCount && multiline)" class="modern-input__footer">
      <div class="modern-input__message">
        <span v-if="errorText" class="modern-input__error">{{ errorText }}</span>
        <span v-else-if="helperText" class="modern-input__helper">{{ helperText }}</span>
      </div>
      <div v-if="showCount && multiline" class="modern-input__count">{{ characterCount }}</div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.modern-input-wrapper {
  width: 100%;
}

.modern-input {
  position: relative;
  display: flex;
  align-items: center;
  background: white;
  border: 1px solid rgba(0, 0, 0, 0.15);
  border-radius: var(--radius-md);
  transition: all var(--duration-base) var(--ease-smooth);

  &:hover:not(&--disabled) {
    border-color: rgba(var(--color-primary-500), 0.5);
  }

  &--focused {
    border-color: rgb(var(--color-primary-500));
    box-shadow: 0 0 0 3px rgba(var(--color-primary-500), 0.1);
  }

  &--error {
    border-color: #f5222d;

    &.modern-input--focused {
      box-shadow: 0 0 0 3px rgba(245, 34, 45, 0.1);
    }
  }

  &--disabled {
    background: rgba(0, 0, 0, 0.04);
    cursor: not-allowed;

    .modern-input__field {
      cursor: not-allowed;
    }
  }

  // 尺寸变体
  &--small {
    .modern-input__field {
      padding: 6px 12px;
      font-size: var(--text-sm);
      height: 32px;
    }

    .modern-input__icon {
      font-size: 16px;
    }

    &.modern-input--has-prefix .modern-input__field {
      padding-left: 36px;
    }

    &.modern-input--has-suffix .modern-input__field {
      padding-right: 36px;
    }
  }

  &--medium {
    .modern-input__field {
      padding: 10px 16px;
      font-size: var(--text-base);
      height: 40px;
    }

    .modern-input__icon {
      font-size: 18px;
    }

    &.modern-input--has-prefix .modern-input__field {
      padding-left: 44px;
    }

    &.modern-input--has-suffix .modern-input__field {
      padding-right: 44px;
    }
  }

  &--large {
    .modern-input__field {
      padding: 14px 20px;
      font-size: var(--text-lg);
      height: 48px;
    }

    .modern-input__icon {
      font-size: 20px;
    }

    &.modern-input--has-prefix .modern-input__field {
      padding-left: 52px;
    }

    &.modern-input--has-suffix .modern-input__field {
      padding-right: 52px;
    }
  }

  &__label {
    display: block;
    font-size: var(--text-sm);
    font-weight: 500;
    color: #1a202c;
    margin-bottom: 8px;
  }

  &__required {
    color: #f5222d;
    margin-left: 4px;
  }

  &__field {
    flex: 1;
    border: none;
    outline: none;
    background: transparent;
    color: #1a202c;
    font-family: inherit;
    transition: all var(--duration-base) var(--ease-smooth);

    &::placeholder {
      color: #a0aec0;
    }

    &:disabled {
      color: #a0aec0;
    }
  }

  &__textarea {
    resize: vertical;
    min-height: auto;
    height: auto;
    padding-top: 10px;
    padding-bottom: 10px;
    line-height: 1.6;
  }

  &__prefix,
  &__suffix {
    display: flex;
    align-items: center;
    justify-content: center;
    position: absolute;
    top: 50%;
    transform: translateY(-50%);
    color: #718096;
    pointer-events: none;
    z-index: 1;
  }

  &__prefix {
    left: 12px;
  }

  &__suffix {
    right: 12px;
  }

  &__icon {
    transition: color var(--duration-base) var(--ease-smooth);
  }

  &__clear {
    pointer-events: all;
    cursor: pointer;
    color: #a0aec0;

    &:hover {
      color: #718096;
    }
  }

  &__footer {
    display: flex;
    justify-content: space-between;
    align-items: flex-start;
    margin-top: 8px;
    font-size: var(--text-xs);
  }

  &__message {
    flex: 1;
  }

  &__helper {
    color: #718096;
  }

  &__error {
    color: #f5222d;
  }

  &__count {
    color: #a0aec0;
    margin-left: 12px;
    white-space: nowrap;
  }
}

// 暗色模式
.dark {
  .modern-input {
    background: rgba(255, 255, 255, 0.05);
    border-color: rgba(255, 255, 255, 0.15);

    &:hover:not(.modern-input--disabled) {
      border-color: rgba(var(--color-primary-400), 0.5);
    }

    &--focused {
      border-color: rgb(var(--color-primary-400));
      box-shadow: 0 0 0 3px rgba(var(--color-primary-400), 0.2);
    }

    &--disabled {
      background: rgba(255, 255, 255, 0.02);
    }

    &__label {
      color: #f7fafc;
    }

    &__field {
      color: #f7fafc;

      &::placeholder {
        color: #718096;
      }
    }

    &__helper {
      color: #a0aec0;
    }
  }
}
</style>

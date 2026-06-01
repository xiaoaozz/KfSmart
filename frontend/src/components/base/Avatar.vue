<script setup lang="ts">
import { computed } from 'vue';

const props = defineProps({
  /** 头像URL */
  src: {
    type: String,
    default: ''
  },
  /** 头像文字(当没有src时显示) */
  text: {
    type: String,
    default: ''
  },
  /** 尺寸 */
  size: {
    type: [Number, String] as PropType<number | 'small' | 'medium' | 'large'>,
    default: 'medium'
  },
  /** 形状 */
  shape: {
    type: String as PropType<'circle' | 'square'>,
    default: 'circle'
  },
  /** 背景颜色 */
  bgColor: {
    type: String,
    default: ''
  }
});

const sizeMap = {
  small: 32,
  medium: 40,
  large: 48
};

const avatarSize = computed(() => {
  if (typeof props.size === 'number') return props.size;
  return sizeMap[props.size];
});

const avatarStyle = computed(() => {
  const style: Record<string, string> = {
    width: `${avatarSize.value}px`,
    height: `${avatarSize.value}px`,
    fontSize: `${avatarSize.value / 2}px`
  };
  
  if (props.bgColor) {
    style.background = props.bgColor;
  }
  
  return style;
});

const initials = computed(() => {
  if (!props.text) return '';
  const words = props.text.trim().split(' ');
  if (words.length >= 2) {
    return (words[0][0] + words[1][0]).toUpperCase();
  }
  return props.text.substring(0, 2).toUpperCase();
});
</script>

<template>
  <div
    :class="['avatar', `avatar--${shape}`]"
    :style="avatarStyle"
  >
    <img v-if="src" :src="src" :alt="text" class="avatar__image" />
    <span v-else class="avatar__text">{{ initials }}</span>
    <slot name="badge" />
  </div>
</template>

<style scoped lang="scss">
.avatar {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  background: var(--gradient-primary);
  color: white;
  font-weight: 600;
  overflow: hidden;
  user-select: none;

  &--circle {
    border-radius: 50%;
  }

  &--square {
    border-radius: var(--radius-md);
  }

  &__image {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }

  &__text {
    line-height: 1;
  }
}
</style>

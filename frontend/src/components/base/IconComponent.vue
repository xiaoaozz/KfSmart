<script setup lang="ts">
import { Icon } from '@iconify/vue';

interface IconComponentProps {
  name: string;
  size?: string | number;
  color?: string;
}

const props = withDefaults(defineProps<IconComponentProps>(), {
  size: '1em',
  color: 'currentColor',
});
</script>

<template>
  <Icon 
    :icon="name" 
    :style="{
      fontSize: typeof size === 'number' ? `${size}px` : size,
      color: color,
    }"
  />
</template>

<style scoped>
/* Icon 组件基础样式已由 Iconify 提供 */
</style>

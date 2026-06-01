<script lang="ts" setup>
import { computed, useAttrs } from 'vue';
import systemLogoUrl from '@/assets/svg-icon/logo.svg';

defineOptions({
  name: 'SystemLogo',
  inheritAttrs: false
});

const attrs = useAttrs();

const wrapperClass = computed(() => ['system-logo', attrs.class]);
const forwardedAttrs = computed(() => {
  const { class: _class, ...rest } = attrs;
  return rest;
});
</script>

<template>
  <span v-bind="forwardedAttrs" :class="wrapperClass">
    <img :src="systemLogoUrl" alt="System logo" class="system-logo__img">
  </span>
</template>

<style scoped>
.system-logo {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  line-height: 1;
}

.system-logo__img {
  width: 1em;
  height: 1em;
  display: block;
  flex-shrink: 0;
  object-fit: contain;
}
</style>

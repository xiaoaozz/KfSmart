<script setup lang="ts">
import { computed, useAttrs } from 'vue';
import { NPagination } from 'naive-ui';
import { PAGINATION_PAGE_SIZE_OPTIONS } from '@/constants/common';

defineOptions({
  name: 'ListPagination',
  inheritAttrs: false
});

interface ListPaginationProps {
  page?: number;
  pageSize?: number;
  itemCount?: number;
  pageCount?: number;
  disabled?: boolean;
  size?: 'small' | 'medium' | 'large';
  simple?: boolean;
  pageSlot?: number;
  showQuickJumper?: boolean;
  showSizePicker?: boolean;
}

const props = withDefaults(defineProps<ListPaginationProps>(), {
  showQuickJumper: false,
  showSizePicker: true
});

const emit = defineEmits<{
  (e: 'update:page', value: number): void;
  (e: 'update:page-size', value: number): void;
}>();

const attrs = useAttrs();

const rootClass = computed(() => [
  'mt-auto flex justify-end border-t border-gray-100 bg-white px-4 py-3 dark:border-gray-700 dark:bg-transparent',
  attrs.class
]);

const paginationAttrs = computed(() => {
  const { class: _class, ...rest } = attrs;
  return rest;
});
</script>

<template>
  <div :class="rootClass">
    <NPagination
      v-bind="paginationAttrs"
      :page="props.page"
      :page-size="props.pageSize"
      :item-count="props.itemCount"
      :page-count="props.pageCount"
      :disabled="props.disabled"
      :size="props.size"
      :simple="props.simple"
      :page-slot="props.pageSlot"
      :show-quick-jumper="props.showQuickJumper"
      :show-size-picker="props.showSizePicker"
      :page-sizes="PAGINATION_PAGE_SIZE_OPTIONS"
      @update:page="value => emit('update:page', value)"
      @update:page-size="value => emit('update:page-size', value)"
    />
  </div>
</template>

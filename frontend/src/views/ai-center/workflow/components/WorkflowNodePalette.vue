<script setup lang="ts">
import { NButton, NInput, NTag } from 'naive-ui';
import { nodeGroups } from '../constants/nodeDefinitions';
import { ref, computed } from 'vue';

const emit = defineEmits<{ addNode: [type: string]; autoLayout: [] }>();

const searchKeyword = ref('');
const filteredGroups = computed(() => {
  if (!searchKeyword.value) return nodeGroups;
  return nodeGroups.map(group => ({
    ...group,
    nodes: group.nodes.filter(node =>
      node.toLowerCase().includes(searchKeyword.value.toLowerCase())
    )
  })).filter(group => group.nodes.length > 0);
});
</script>

<template>
  <aside class="flex w-[220px] flex-shrink-0 flex-col border-r border-gray-100 bg-white dark:border-gray-700 dark:bg-[#18181c]">
    <!-- 标题 -->
    <div class="px-4 pt-4 pb-2 border-b border-gray-100 dark:border-gray-700">
      <div class="flex items-center gap-2 mb-3">
        <icon-carbon:category class="text-sm text-primary-500" />
        <h2 class="text-sm font-semibold text-gray-800 dark:text-gray-100">节点库</h2>
      </div>
      <NInput
        v-model:value="searchKeyword"
        size="tiny"
        placeholder="搜索节点..."
        clearable
      >
        <template #prefix>
          <icon-carbon:search class="text-gray-400 text-xs" />
        </template>
      </NInput>
    </div>

    <!-- 节点列表 -->
    <div class="flex-1 overflow-y-auto px-2 py-2">
      <div v-for="group in filteredGroups" :key="group.title" class="mb-4">
        <div class="px-2 mb-1.5 text-xs font-medium text-gray-400 dark:text-gray-500 uppercase tracking-wide">
          {{ group.title }}
        </div>
        <div class="space-y-1">
          <button
            v-for="node in group.nodes"
            :key="node"
            class="w-full flex items-center gap-2 rounded-lg border border-gray-100 bg-white px-2.5 py-2 text-left text-sm text-gray-700 transition-all hover:border-primary-300 hover:bg-primary-50/50 hover:text-primary-600 dark:border-gray-700 dark:bg-[#1e1e22] dark:text-gray-200 dark:hover:border-primary-600 dark:hover:bg-primary-900/10"
            @click="emit('addNode', node)"
          >
            <span class="flex-shrink-0 text-base text-gray-400 dark:text-gray-500">
              <icon-carbon:flow />
            </span>
            <span class="truncate">{{ node }}</span>
          </button>
        </div>
      </div>
    </div>

    <!-- 底部操作 -->
    <div class="border-t border-gray-100 dark:border-gray-700 p-2">
      <NButton size="small" quaternary block @click="emit('autoLayout')">
        <template #icon><icon-carbon:grid /></template>
        自动布局
      </NButton>
    </div>
  </aside>
</template>

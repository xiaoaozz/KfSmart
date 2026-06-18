<script setup lang="ts">
import { computed } from 'vue';
import { NEmpty, NTag } from 'naive-ui';
import type { NodeTraceItem } from '../types/workflow';

const props = defineProps<{
  trace: NodeTraceItem[];
  totalDurationMs?: number;
}>();

const filteredTrace = computed(() => {
  if (!props.trace || props.trace.length === 0) return [];
  const completed = props.trace.filter(item => item.status !== 'running');
  if (completed.length > 0) return completed;
  return props.trace;
});

function tagType(status: string): 'success' | 'error' | 'warning' {
  if (status === 'success') return 'success';
  if (status === 'error' || status === 'failed') return 'error';
  return 'warning';
}

function tagLabel(status: string): string {
  if (status === 'success') return '成功';
  if (status === 'error' || status === 'failed') return '失败';
  return status === 'running' ? '执行中' : status;
}

function indicatorClass(status: string): string {
  if (status === 'success') return 'bg-green-100 text-green-600';
  if (status === 'error' || status === 'failed') return 'bg-red-100 text-red-600';
  return 'bg-amber-100 text-amber-600';
}
</script>

<template>
  <div class="space-y-2">
    <div v-if="filteredTrace.length === 0">
      <NEmpty description="暂无执行轨迹" />
    </div>
    <div
      v-for="(item, index) in filteredTrace"
      :key="(item.nodeId || item.name) + index"
      class="flex items-start gap-3 rounded-lg border border-gray-100 p-3 dark:border-gray-700"
    >
      <div class="flex flex-col items-center">
        <div
          class="flex h-7 w-7 items-center justify-center rounded-full text-xs font-bold"
          :class="indicatorClass(item.status)"
        >
          {{ index + 1 }}
        </div>
        <div v-if="index < filteredTrace.length - 1" class="w-px h-8 bg-gray-200 dark:bg-gray-700" />
      </div>
      <div class="flex-1 min-w-0">
        <div class="flex items-center justify-between mb-1">
          <span class="text-sm font-medium">{{ item.name }}</span>
          <div class="flex items-center gap-2">
            <NTag :type="tagType(item.status)" size="small">
              {{ tagLabel(item.status) }}
            </NTag>
            <span class="text-xs text-gray-400">{{ (item.durationMs / 1000).toFixed(2) }}s</span>
          </div>
        </div>
        <div v-if="item.description" class="text-xs text-gray-500 dark:text-gray-400 mt-1 leading-relaxed">
          {{ item.description }}
        </div>
        <div v-if="item.errorMessage" class="text-xs text-red-500 mt-1">{{ item.errorMessage }}</div>
        <div v-if="item.type" class="text-xs text-gray-400 mt-0.5">类型: {{ item.type }}</div>
      </div>
    </div>
  </div>
</template>

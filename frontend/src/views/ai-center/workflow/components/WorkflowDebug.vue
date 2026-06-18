<script setup lang="ts">
import { computed } from 'vue';
import { NButton, NInput, NTag } from 'naive-ui';
import type { DebugResult } from '../types/workflow';

const props = defineProps<{
  debugResult: DebugResult | null;
  testQuery: string;
  debugLoading: boolean;
  hasWorkflow: boolean;
}>();

const emit = defineEmits<{
  runDebug: [];
  'update:testQuery': [value: string];
}>();

const filteredTrace = computed(() => {
  if (!props.debugResult?.trace) return [];
  const completed = props.debugResult.trace.filter(item => item.status !== 'running');
  return completed.length > 0 ? completed : props.debugResult.trace;
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
</script>

<template>
  <div class="grid grid-cols-[320px_1fr_320px] gap-5">
    <div class="rounded-lg border border-gray-100 bg-gray-50 p-4 dark:border-gray-700 dark:bg-gray-900">
      <div class="mb-3 text-sm font-semibold">输入区</div>
      <NInput
        :value="props.testQuery"
        type="textarea"
        placeholder="请输入测试问题"
        @update:value="emit('update:testQuery', $event)"
      />
      <NButton block type="primary" class="mt-3" :disabled="!props.hasWorkflow" :loading="props.debugLoading" @click="emit('runDebug')">
        执行
      </NButton>
    </div>
    <div class="rounded-lg border border-gray-100 p-4 dark:border-gray-700">
      <div class="mb-3 text-sm font-semibold">执行轨迹</div>
      <div v-if="props.debugResult" class="space-y-3">
        <div
          v-for="(item, idx) in filteredTrace"
          :key="(item.nodeId || item.name) + idx"
          class="rounded-lg bg-gray-50 px-3 py-2 dark:bg-gray-900"
        >
          <div class="flex items-center justify-between">
            <div class="flex items-center gap-2">
              <NTag :type="tagType(item.status)" size="small">
                {{ tagLabel(item.status) }}
              </NTag>
              <span class="text-sm font-medium">{{ item.name }}</span>
            </div>
            <NTag size="small">{{ (item.durationMs / 1000).toFixed(1) }}s</NTag>
          </div>
          <div v-if="item.description" class="mt-1 text-xs text-gray-500 dark:text-gray-400">
            {{ item.description }}
          </div>
        </div>
        <div class="rounded-lg bg-blue-50 p-3 text-sm text-blue-700">
          {{ props.debugResult.output.answer }}
        </div>
      </div>
      <div v-else class="py-16 text-center text-gray-400">暂无调试结果</div>
    </div>
    <div class="space-y-5">
      <div class="rounded-lg border border-gray-100 p-4 dark:border-gray-700">
        <div class="mb-3 text-sm font-semibold">变量监控</div>
        <pre class="whitespace-pre-wrap rounded bg-gray-50 p-3 text-xs dark:bg-gray-900">{{ JSON.stringify(props.debugResult?.variables || { query: props.testQuery }, null, 2) }}</pre>
      </div>
      <div class="rounded-lg border border-gray-100 p-4 dark:border-gray-700">
        <div class="mb-3 text-sm font-semibold">Token统计</div>
        <div class="space-y-2 text-sm">
          <div class="flex justify-between"><span>Prompt Token</span><span>{{ props.debugResult?.tokens.promptTokens || 0 }}</span></div>
          <div class="flex justify-between"><span>Completion Token</span><span>{{ props.debugResult?.tokens.completionTokens || 0 }}</span></div>
          <div class="flex justify-between"><span>总Token</span><span>{{ props.debugResult?.tokens.totalTokens || 0 }}</span></div>
          <div class="flex justify-between"><span>费用</span><span>${{ props.debugResult?.tokens.cost || 0 }}</span></div>
        </div>
      </div>
    </div>
  </div>
</template>

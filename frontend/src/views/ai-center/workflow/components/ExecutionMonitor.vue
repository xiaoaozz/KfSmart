<script setup lang="ts">
import { NModal, NSpin, NTag } from 'naive-ui';
import { ref, watch } from 'vue';
import { fetchWorkflowExecutionDetail } from '@/service/api/workflow';
import ExecutionTraceTimeline from './ExecutionTraceTimeline.vue';
import type { NodeTraceItem } from '../types/workflow';

const props = defineProps<{
  visible: boolean;
  workflowId: string;
  executionId?: string;
}>();

const emit = defineEmits<{ 'update:visible': [v: boolean] }>();

const trace = ref<NodeTraceItem[]>([]);
const variables = ref<Record<string, any>>({});
const loading = ref(false);
const totalDuration = ref(0);
const status = ref('');
const startedBy = ref('');
const startedAt = ref('');
const totalTokens = ref(0);
const promptTokens = ref(0);
const completionTokens = ref(0);

async function loadExecution() {
  if (!props.workflowId || !props.executionId) return;
  loading.value = true;
  const { error, data } = await fetchWorkflowExecutionDetail(props.workflowId, props.executionId);
  if (!error && data) {
    try {
      trace.value = data.traceJson ? JSON.parse(data.traceJson) : [];
      variables.value = data.variablesJson ? JSON.parse(data.variablesJson) : {};
    } catch {
      trace.value = [];
    }
    totalDuration.value = data.durationMs || 0;
    status.value = data.status || '';
    startedBy.value = data.startedBy || '';
    startedAt.value = data.startedAt || '';
    totalTokens.value = data.totalTokens || 0;
    promptTokens.value = data.promptTokens || 0;
    completionTokens.value = data.completionTokens || 0;
  }
  loading.value = false;
}

watch(() => props.visible, (val) => {
  if (val) loadExecution();
});

function statusTagType(s: string): 'success' | 'error' | 'warning' {
  if (s === 'success') return 'success';
  if (s === 'failed' || s === 'error') return 'error';
  return 'warning';
}

function statusLabel(s: string): string {
  if (s === 'success') return '成功';
  if (s === 'failed' || s === 'error') return '失败';
  if (s === 'running') return '执行中';
  return s;
}
</script>

<template>
  <NModal
    :show="visible"
    @update:show="emit('update:visible', $event)"
    preset="card"
    title="执行监控"
    style="width: 860px"
  >
    <NSpin :show="loading">
      <div class="max-h-600px overflow-y-auto space-y-4 p-1">
        <!-- 执行摘要 -->
        <div class="grid grid-cols-4 gap-3">
          <div class="rounded-lg border border-gray-100 p-3 dark:border-gray-700">
            <div class="mb-1 text-xs text-gray-400">状态</div>
            <NTag :type="statusTagType(status)" size="small">{{ statusLabel(status) }}</NTag>
          </div>
          <div class="rounded-lg border border-gray-100 p-3 dark:border-gray-700">
            <div class="mb-1 text-xs text-gray-400">总耗时</div>
            <div class="text-sm font-semibold">{{ (totalDuration / 1000).toFixed(2) }}s</div>
          </div>
          <div class="rounded-lg border border-gray-100 p-3 dark:border-gray-700">
            <div class="mb-1 text-xs text-gray-400">总Token</div>
            <div class="text-sm font-semibold">{{ totalTokens }}</div>
          </div>
          <div class="rounded-lg border border-gray-100 p-3 dark:border-gray-700">
            <div class="mb-1 text-xs text-gray-400">执行人</div>
            <div class="text-sm font-semibold truncate">{{ startedBy || '-' }}</div>
          </div>
        </div>

        <!-- 执行轨迹 -->
        <div>
          <div class="mb-2 text-sm font-semibold text-gray-700 dark:text-gray-300">执行轨迹</div>
          <ExecutionTraceTimeline :trace="trace" :total-duration-ms="totalDuration" />
        </div>

        <!-- 变量状态 -->
        <div>
          <div class="mb-2 text-sm font-semibold text-gray-700 dark:text-gray-300">变量状态</div>
          <pre class="max-h-300px overflow-auto whitespace-pre-wrap rounded-lg bg-gray-50 p-3 text-xs dark:bg-gray-900">{{ JSON.stringify(variables, null, 2) }}</pre>
        </div>
      </div>
    </NSpin>
  </NModal>
</template>

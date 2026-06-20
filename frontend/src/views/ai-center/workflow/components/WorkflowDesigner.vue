<script setup lang="ts">
import { computed, ref } from 'vue';
import { NButton, NInput, NSelect, NTag, NTooltip } from 'naive-ui';
import type { WorkflowNode } from '../types/workflow';
import WorkflowCanvas from './WorkflowCanvas.vue';
import WorkflowNodePalette from './WorkflowNodePalette.vue';
import NodePropertyPanel from './NodePropertyPanel.vue';
import WorkflowDebug from './WorkflowDebug.vue';

const canvasRef = ref<InstanceType<typeof WorkflowCanvas> | null>(null);
const showDebugPanel = ref(false);

const props = defineProps<{
  designer: any;
  hasSelectedWorkflow: boolean;
  selectedNode: any;
  modelOptions: { label: string; value: string }[];
  knowledgeBaseOptions: { label: string; value: string }[];
  promptOptions: { label: string; value: string }[];
  mcpToolOptions: { label: string; value: string }[];
  skillOptions: { label: string; value: string }[];
  saving: boolean;
  debugLoading: boolean;
  testQuery: string;
  debugResult: any;
}>();

const emit = defineEmits<{
  save: [];
  runDebug: [];
  'update:testQuery': [value: string];
  addNode: [type: string];
  selectNode: [id: string];
  deleteNode: [];
  autoLayout: [];
  startNodeDrag: [event: MouseEvent, node: WorkflowNode];
  addEdge: [edge: { source: string; target: string }];
  deleteEdge: [index: number];
}>();

function handleZoomIn() { canvasRef.value?.zoomCanvas(1.1); }
function handleZoomOut() { canvasRef.value?.zoomCanvas(0.9); }
function handleResetView() { canvasRef.value?.resetViewport(); }

function handleEdgeDraw(payload: any) {
  if (payload.sourceId && payload.targetId) {
    emit('addEdge', { source: payload.sourceId, target: payload.targetId })
  }
}

const filteredTrace = computed(() => {
  if (!props.debugResult?.trace) return []
  const completed = props.debugResult.trace.filter((item: any) => item.status !== 'running')
  return completed.length > 0 ? completed : props.debugResult.trace
})

function traceTagType(status: string): 'success' | 'error' | 'warning' {
  if (status === 'success') return 'success'
  if (status === 'error' || status === 'failed') return 'error'
  return 'warning'
}

function splitComma(value?: string) {
  return value ? value.split(',').map(item => item.trim()).filter(Boolean) : []
}

function joinComma(values: string[]) {
  return values.filter(Boolean).join(',')
}

const boundMcpToolOptions = computed(() => {
  const selected = splitComma(props.designer.mcpTools)
  if (!selected.length) return props.mcpToolOptions
  const selectedSet = new Set(selected)
  return props.mcpToolOptions.filter(item => selectedSet.has(item.value))
})
</script>

<template>
  <div class="designer-shell flex flex-1 overflow-hidden">
    <!-- 左侧节点库 -->
    <WorkflowNodePalette @add-node="emit('addNode', $event)" @auto-layout="emit('autoLayout')" />

    <!-- 中间画布 -->
    <main class="flex min-w-0 flex-1 flex-col bg-white dark:bg-gray-950">
      <!-- 工具栏 -->
      <div class="flex h-12 shrink-0 items-center justify-between border-b border-gray-100 px-4 dark:border-gray-700">
        <div class="flex items-center gap-2">
          <NInput v-model:value="props.designer.name" class="max-w-200px" size="small" :disabled="!hasSelectedWorkflow" placeholder="工作流名称" />
          <NTag v-if="props.designer.type" type="info" size="small" :bordered="false">{{ props.designer.type }}</NTag>
          <NTag v-if="props.designer.status" :type="props.designer.status === '运行中' ? 'success' : 'default'" size="small" :bordered="false">{{ props.designer.status }}</NTag>
          <NSelect
            :value="splitComma(props.designer.mcpTools)"
            multiple
            clearable
            filterable
            size="small"
            class="w-260px"
            :disabled="!hasSelectedWorkflow"
            :options="props.mcpToolOptions"
            max-tag-count="responsive"
            placeholder="绑定 MCP 工具"
            @update:value="(value: string[]) => (props.designer.mcpTools = joinComma(value))"
          />
          <NSelect
            :value="splitComma(props.designer.skillRefs)"
            multiple
            clearable
            filterable
            size="small"
            class="w-220px"
            :disabled="!hasSelectedWorkflow"
            :options="props.skillOptions"
            max-tag-count="responsive"
            placeholder="绑定 Skills"
            @update:value="(value: string[]) => (props.designer.skillRefs = joinComma(value))"
          />
        </div>
        <div class="flex items-center gap-1">
          <NTooltip placement="bottom">
            <template #trigger>
              <NButton size="tiny" quaternary :disabled="!hasSelectedWorkflow" @click="handleZoomOut">
                <template #icon><icon-carbon:zoom-out /></template>
              </NButton>
            </template>
            缩小
          </NTooltip>
          <NTag size="tiny" :bordered="false">{{ Math.round((canvasRef?.viewport.scale || 1) * 100) }}%</NTag>
          <NTooltip placement="bottom">
            <template #trigger>
              <NButton size="tiny" quaternary :disabled="!hasSelectedWorkflow" @click="handleZoomIn">
                <template #icon><icon-carbon:zoom-in /></template>
              </NButton>
            </template>
            放大
          </NTooltip>
          <NTooltip placement="bottom">
            <template #trigger>
              <NButton size="tiny" quaternary :disabled="!hasSelectedWorkflow" @click="handleResetView">
                <template #icon><icon-carbon:center-circle /></template>
              </NButton>
            </template>
            重置视图
          </NTooltip>
          <NTooltip placement="bottom">
            <template #trigger>
              <NButton size="tiny" quaternary :disabled="!hasSelectedWorkflow" @click="emit('autoLayout')">
                <template #icon><icon-carbon:grid /></template>
              </NButton>
            </template>
            自动布局
          </NTooltip>
          <NTooltip placement="bottom">
            <template #trigger>
              <NButton size="tiny" quaternary :disabled="!hasSelectedWorkflow" @click="showDebugPanel = !showDebugPanel">
                <template #icon><icon-carbon:debug /></template>
                {{ showDebugPanel ? '隐藏调试' : '调试' }}
              </NButton>
            </template>
            即时调试
          </NTooltip>
        </div>
      </div>

      <!-- 画布 -->
      <WorkflowCanvas
        v-if="hasSelectedWorkflow"
        ref="canvasRef"
        :nodes="props.designer.nodes"
        :edges="props.designer.edges"
        :selected-node-id="props.designer.selectedNodeId"
        @select-node="emit('selectNode', $event)"
        @start-node-drag="(event: MouseEvent, node: WorkflowNode) => emit('startNodeDrag', event, node)"
        @start-edge-draw="handleEdgeDraw"
        @delete-edge="emit('deleteEdge', $event)"
      />
      <div v-else class="flex flex-1 items-center justify-center text-sm text-gray-400">
        请先选择或创建一个工作流
      </div>
    </main>

    <!-- 右侧面板：属性 + 调试 -->
    <aside class="relative flex w-[340px] flex-shrink-0 flex-col border-l border-gray-100 bg-white dark:border-gray-700 dark:bg-[#18181c]">
      <!-- 属性面板（始终渲染） -->
      <div class="flex-1 overflow-y-auto p-4">
        <div class="mb-3 flex items-center gap-2">
          <icon-carbon:settings-adjust class="text-sm text-primary-500" />
          <div class="text-sm font-semibold text-gray-900 dark:text-white">属性配置</div>
        </div>
        <NodePropertyPanel
          v-if="hasSelectedWorkflow && selectedNode"
          :node="selectedNode"
          :model-options="modelOptions"
          :knowledge-base-options="knowledgeBaseOptions"
          :prompt-options="promptOptions"
          :mcp-tool-options="boundMcpToolOptions.length ? boundMcpToolOptions : mcpToolOptions"
          @delete="emit('deleteNode')"
        />
        <div v-else class="rounded-lg border border-dashed border-gray-200 py-10 text-center text-sm text-gray-400 dark:border-gray-700">
          {{ hasSelectedWorkflow ? '请选择一个节点' : '暂无可配置节点' }}
        </div>
      </div>

      <!-- 调试面板（覆盖在属性面板之上） -->
      <div v-if="showDebugPanel" class="absolute inset-0 z-10 flex flex-col bg-white dark:bg-[#18181c]">
        <div class="flex items-center justify-between border-b border-gray-100 px-4 py-2.5 dark:border-gray-700">
          <div class="flex items-center gap-2">
            <icon-carbon:debug class="text-sm text-primary-500" />
            <span class="text-sm font-semibold text-gray-700 dark:text-gray-300">即时调试</span>
          </div>
          <NButton size="tiny" quaternary @click="showDebugPanel = false">
            <template #icon><icon-carbon:close /></template>
          </NButton>
        </div>
        <div class="flex-1 space-y-2 overflow-y-auto p-3">
          <NInput
            :value="props.testQuery"
            type="textarea"
            :rows="3"
            size="small"
            placeholder="输入测试问题..."
            @update:value="emit('update:testQuery', $event)"
          />
          <NButton size="small" type="primary" block :loading="props.debugLoading" :disabled="!hasSelectedWorkflow" @click="emit('runDebug')">
            <template #icon><icon-carbon:send /></template>
            执行
          </NButton>
          <div v-if="props.debugResult" class="mt-2 space-y-1.5">
            <div
              v-for="(item, idx) in filteredTrace"
              :key="(item.nodeId || item.name) + idx"
              class="rounded bg-gray-50 px-2 py-1.5 text-xs dark:bg-[#1e1e22]"
            >
              <div class="flex items-center justify-between">
                <span>{{ item.name }}</span>
                <NTag :type="traceTagType(item.status)" size="tiny" :bordered="false">
                  {{ (item.durationMs / 1000).toFixed(1) }}s
                </NTag>
              </div>
              <div v-if="item.description" class="mt-0.5 text-gray-400 leading-relaxed">
                {{ item.description }}
              </div>
            </div>
            <div v-if="props.debugResult.output" class="rounded bg-blue-50 px-2 py-1.5 text-xs text-blue-700 dark:bg-blue-900/20 dark:text-blue-300">
              {{ props.debugResult.output.answer }}
            </div>
          </div>
        </div>
      </div>
    </aside>
  </div>
</template>

<script setup lang="ts">
import { nodeClass } from '../constants/nodeDefinitions';
import type { WorkflowNode } from '../types/workflow';

const props = defineProps<{
  node: WorkflowNode;
  selected: boolean;
  scale: number;
}>();

const emit = defineEmits<{
  select: [nodeId: string];
  startDrag: [event: MouseEvent, node: WorkflowNode];
  startEdgeDraw: [event: MouseEvent, nodeId: string];
}>();
</script>

<template>
  <button
    class="workflow-node absolute h-68px w-136px cursor-move rounded-lg border px-3 text-left shadow-sm transition select-none hover:shadow-md"
    :class="[nodeClass(node.type), selected ? 'ring-2 ring-blue-400' : '']"
    :style="{ left: `${node.x}px`, top: `${node.y}px` }"
    @click.stop="emit('select', node.id)"
    @mousedown="emit('startDrag', $event, node)"
  >
    <div class="flex items-center gap-2">
      <div class="text-sm font-semibold truncate">{{ node.name }}</div>
    </div>
    <div class="text-xs opacity-70 truncate">{{ node.type }}</div>

    <!-- Input connector (left) -->
    <div
      class="connector connector-input"
      @mousedown.stop.prevent="() => {}"
    />
    <!-- Output connector (right) -->
    <div
      class="connector connector-output"
      title="拖拽创建连线"
      @mousedown.stop.prevent="emit('startEdgeDraw', $event, node.id)"
    />
  </button>
</template>

<style scoped>
.workflow-node .connector {
  position: absolute;
  width: 12px;
  height: 12px;
  border-radius: 50%;
  background: #fff;
  border: 2px solid #94a3b8;
  top: 50%;
  transform: translateY(-50%);
  z-index: 2;
  transition: border-color 0.2s, transform 0.2s;
}
.workflow-node .connector:hover {
  border-color: #3b82f6;
  transform: translateY(-50%) scale(1.3);
}
.workflow-node .connector-input {
  left: -7px;
}
.workflow-node .connector-output {
  right: -7px;
  cursor: crosshair;
}
</style>

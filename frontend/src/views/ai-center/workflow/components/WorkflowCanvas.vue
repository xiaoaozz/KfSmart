<script setup lang="ts">
import { ref } from 'vue';
import type { WorkflowNode, WorkflowEdge } from '../types/workflow';
import { useCanvasViewport } from '../composables/useCanvasViewport';
import WorkflowCanvasNode from './WorkflowCanvasNode.vue';

const props = defineProps<{
  nodes: WorkflowNode[];
  edges: WorkflowEdge[];
  selectedNodeId: string;
}>();

const emit = defineEmits<{
  selectNode: [nodeId: string];
  startNodeDrag: [event: MouseEvent, node: WorkflowNode];
  startEdgeDraw: [event: MouseEvent, nodeId: string];
  deleteEdge: [index: number];
}>();

const { viewport, canvasRef, viewportStyle, zoomCanvas, handleCanvasWheel, startCanvasPan, resetViewport } = useCanvasViewport();

const drawingEdge = ref<{ sourceId: string; x: number; y: number } | null>(null);

function getNode(nodeId: string) {
  return props.nodes.find(n => n.id === nodeId);
}

function edgePath(edge: WorkflowEdge) {
  const source = getNode(edge.source);
  const target = getNode(edge.target);
  if (!source || !target) return '';

  const startX = source.x + 136;
  const startY = source.y + 34;
  const endX = target.x;
  const endY = target.y + 34;
  const distance = Math.max(80, Math.abs(endX - startX));
  const controlOffset = Math.min(180, distance * 0.55);

  if (endX >= startX) {
    return `M ${startX} ${startY} C ${startX + controlOffset} ${startY}, ${endX - controlOffset} ${endY}, ${endX} ${endY}`;
  }
  const loopOffset = Math.max(90, Math.abs(endY - startY) + 60);
  return `M ${startX} ${startY} C ${startX + loopOffset} ${startY}, ${endX - loopOffset} ${endY}, ${endX} ${endY}`;
}

function drawingEdgePath() {
  if (!drawingEdge.value) return '';
  const source = getNode(drawingEdge.value.sourceId);
  if (!source) return '';
  const startX = source.x + 136;
  const startY = source.y + 34;
  const endX = drawingEdge.value.x;
  const endY = drawingEdge.value.y;
  const controlOffset = Math.min(180, Math.max(40, Math.abs(endX - startX) * 0.55));
  return `M ${startX} ${startY} C ${startX + controlOffset} ${startY}, ${endX - controlOffset} ${endY}, ${endX} ${endY}`;
}

function handleEdgeDrawStart(event: MouseEvent, nodeId: string) {
  const rect = canvasRef.value?.getBoundingClientRect();
  if (!rect) return;
  const worldX = (event.clientX - rect.left - viewport.x) / viewport.scale;
  const worldY = (event.clientY - rect.top - viewport.y) / viewport.scale;
  drawingEdge.value = { sourceId: nodeId, x: worldX, y: worldY };

  const onMove = (e: MouseEvent) => {
    if (!drawingEdge.value || !rect) return;
    drawingEdge.value.x = (e.clientX - rect.left - viewport.x) / viewport.scale;
    drawingEdge.value.y = (e.clientY - rect.top - viewport.y) / viewport.scale;
  };
  const onUp = (e: MouseEvent) => {
    window.removeEventListener('mousemove', onMove);
    window.removeEventListener('mouseup', onUp);
    const target = (e.target as HTMLElement)?.closest('.workflow-node');
    if (target) {
      const targetId = target.getAttribute('data-node-id');
      if (targetId && targetId !== drawingEdge.value.sourceId) {
        emit('startEdgeDraw', { sourceId: drawingEdge.value.sourceId, targetId } as any);
      }
    }
    drawingEdge.value = null;
  };
  window.addEventListener('mousemove', onMove);
  window.addEventListener('mouseup', onUp);
}

defineExpose({ viewport, zoomCanvas, resetViewport });
</script>

<template>
  <div
    ref="canvasRef"
    class="workflow-canvas-empty relative min-h-0 flex-1 cursor-grab overflow-hidden bg-[#f8fafc] bg-[radial-gradient(circle,#d7dde6_1px,transparent_1px)] bg-[length:20px_20px] active:cursor-grabbing dark:bg-gray-950"
    tabindex="0"
    @mousedown="startCanvasPan"
    @wheel="handleCanvasWheel"
  >
    <div class="absolute left-0 top-0 h-2400px w-3200px" :style="viewportStyle">
      <svg class="pointer-events-none absolute inset-0 h-full w-full">
        <defs>
          <marker id="workflow-arrow" markerHeight="8" markerWidth="8" orient="auto" refX="8" refY="4">
            <path d="M0,0 L8,4 L0,8 Z" fill="#94a3b8" />
          </marker>
        </defs>
        <path
          v-for="(edge, index) in edges"
          :key="`${edge.source}-${edge.target}`"
          :d="edgePath(edge)"
          fill="none"
          stroke="#94a3b8"
          stroke-width="2"
          stroke-linecap="round"
          class="pointer-events-auto cursor-pointer"
          marker-end="url(#workflow-arrow)"
          @click.stop="emit('deleteEdge', index)"
        />
        <path
          v-if="drawingEdge"
          :d="drawingEdgePath()"
          fill="none"
          stroke="#3b82f6"
          stroke-width="2"
          stroke-dasharray="5,5"
        />
      </svg>
      <WorkflowCanvasNode
        v-for="node in nodes"
        :key="node.id"
        :node="node"
        :selected="selectedNodeId === node.id"
        :scale="viewport.scale"
        :data-node-id="node.id"
        @select="emit('selectNode', $event)"
        @start-drag="emit('startNodeDrag', $event, node)"
        @start-edge-draw="handleEdgeDrawStart($event, node.id)"
      />
    </div>
  </div>
</template>

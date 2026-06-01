<template>
  <div class="flow-chart" :class="chartClass">
    <!-- 工具栏 (可选) -->
    <div v-if="showToolbar" class="flow-chart__toolbar">
      <div class="toolbar-left">
        <slot name="toolbar-left">
          <h3 v-if="title" class="toolbar-title">{{ title }}</h3>
        </slot>
      </div>
      
      <div class="toolbar-right">
        <slot name="toolbar-right">
          <!-- 缩放控制 -->
          <div class="zoom-controls">
            <IconButton
              icon="mdi:minus"
              size="small"
              color="primary"
              @click="handleZoomOut"
              :disabled="zoom <= minZoom"
            />
            <span class="zoom-text">{{ Math.round(zoom * 100) }}%</span>
            <IconButton
              icon="mdi:plus"
              size="small"
              color="primary"
              @click="handleZoomIn"
              :disabled="zoom >= maxZoom"
            />
          </div>

          <!-- 视图控制 -->
          <IconButton
            icon="mdi:fit-to-page-outline"
            size="small"
            color="primary"
            title="适应画布"
            @click="handleFitView"
          />

          <!-- 全屏切换 -->
          <IconButton
            :icon="isFullscreen ? 'mdi:fullscreen-exit' : 'mdi:fullscreen'"
            size="small"
            color="primary"
            :title="isFullscreen ? '退出全屏' : '全屏'"
            @click="handleToggleFullscreen"
          />
        </slot>
      </div>
    </div>

    <!-- 画布容器 -->
    <div 
      ref="canvasRef" 
      class="flow-chart__canvas"
      :style="canvasStyle"
      @wheel="handleWheel"
      @mousedown="handleMouseDown"
    >
      <div 
        class="canvas-content"
        :style="contentStyle"
      >
        <slot />
      </div>

      <!-- 网格背景 (可选) -->
      <div v-if="showGrid" class="canvas-grid" />
    </div>

    <!-- 迷你地图 (可选) -->
    <div v-if="showMinimap" class="flow-chart__minimap">
      <div class="minimap-viewport" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue';
import { IconButton } from '@/components/base';

interface FlowChartProps {
  title?: string;
  showToolbar?: boolean;
  showGrid?: boolean;
  showMinimap?: boolean;
  defaultZoom?: number;
  minZoom?: number;
  maxZoom?: number;
  zoomStep?: number;
  direction?: 'horizontal' | 'vertical';
  height?: string;
}

const props = withDefaults(defineProps<FlowChartProps>(), {
  title: '',
  showToolbar: true,
  showGrid: true,
  showMinimap: false,
  defaultZoom: 1,
  minZoom: 0.5,
  maxZoom: 2,
  zoomStep: 0.1,
  direction: 'horizontal',
  height: '600px',
});

const emit = defineEmits<{
  zoomChange: [zoom: number];
  fullscreenChange: [fullscreen: boolean];
}>();

const canvasRef = ref<HTMLElement>();
const zoom = ref(props.defaultZoom);
const panX = ref(0);
const panY = ref(0);
const isPanning = ref(false);
const isFullscreen = ref(false);
const startX = ref(0);
const startY = ref(0);

const chartClass = computed(() => ({
  [`direction-${props.direction}`]: true,
  'is-fullscreen': isFullscreen.value,
}));

const canvasStyle = computed(() => ({
  height: props.height,
}));

const contentStyle = computed(() => ({
  transform: `translate(${panX.value}px, ${panY.value}px) scale(${zoom.value})`,
}));

// 缩放控制
const handleZoomIn = () => {
  const newZoom = Math.min(zoom.value + props.zoomStep, props.maxZoom);
  zoom.value = newZoom;
  emit('zoomChange', newZoom);
};

const handleZoomOut = () => {
  const newZoom = Math.max(zoom.value - props.zoomStep, props.minZoom);
  zoom.value = newZoom;
  emit('zoomChange', newZoom);
};

// 滚轮缩放
const handleWheel = (e: WheelEvent) => {
  if (e.ctrlKey || e.metaKey) {
    e.preventDefault();
    const delta = e.deltaY > 0 ? -props.zoomStep : props.zoomStep;
    const newZoom = Math.max(props.minZoom, Math.min(props.maxZoom, zoom.value + delta));
    zoom.value = newZoom;
    emit('zoomChange', newZoom);
  }
};

// 拖拽平移
const handleMouseDown = (e: MouseEvent) => {
  if (e.button === 0 && (e.ctrlKey || e.metaKey || e.shiftKey)) {
    e.preventDefault();
    isPanning.value = true;
    startX.value = e.clientX - panX.value;
    startY.value = e.clientY - panY.value;
    document.addEventListener('mousemove', handleMouseMove);
    document.addEventListener('mouseup', handleMouseUp);
  }
};

const handleMouseMove = (e: MouseEvent) => {
  if (isPanning.value) {
    panX.value = e.clientX - startX.value;
    panY.value = e.clientY - startY.value;
  }
};

const handleMouseUp = () => {
  isPanning.value = false;
  document.removeEventListener('mousemove', handleMouseMove);
  document.removeEventListener('mouseup', handleMouseUp);
};

// 适应视图
const handleFitView = () => {
  zoom.value = props.defaultZoom;
  panX.value = 0;
  panY.value = 0;
  emit('zoomChange', props.defaultZoom);
};

// 全屏切换
const handleToggleFullscreen = () => {
  isFullscreen.value = !isFullscreen.value;
  emit('fullscreenChange', isFullscreen.value);

  if (isFullscreen.value) {
    document.body.style.overflow = 'hidden';
  } else {
    document.body.style.overflow = '';
  }
};

// 清理
onUnmounted(() => {
  document.removeEventListener('mousemove', handleMouseMove);
  document.removeEventListener('mouseup', handleMouseUp);
  document.body.style.overflow = '';
});
</script>

<style scoped>
.flow-chart {
  display: flex;
  flex-direction: column;
  background: white;
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-card);
  overflow: hidden;
  transition: all var(--duration-base) var(--ease-out-cubic);
}

.flow-chart.is-fullscreen {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 9999;
  border-radius: 0;
}

/* 工具栏 */
.flow-chart__toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--spacing-md) var(--spacing-lg);
  border-bottom: 1px solid var(--border-color);
  background: var(--color-gray-50);
}

.toolbar-left,
.toolbar-right {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
}

.toolbar-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0;
}

.zoom-controls {
  display: flex;
  align-items: center;
  gap: var(--spacing-xs);
  padding: var(--spacing-xs) var(--spacing-sm);
  background: white;
  border-radius: var(--radius-md);
  border: 1px solid var(--border-color);
}

.zoom-text {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-secondary);
  min-width: 40px;
  text-align: center;
}

/* 画布 */
.flow-chart__canvas {
  position: relative;
  flex: 1;
  overflow: hidden;
  cursor: grab;
  background: var(--color-gray-50);
}

.flow-chart__canvas:active {
  cursor: grabbing;
}

.canvas-content {
  position: absolute;
  top: 50%;
  left: 50%;
  transform-origin: center center;
  transition: transform 0.2s ease-out;
  will-change: transform;
}

.flow-chart.direction-horizontal .canvas-content {
  display: flex;
  align-items: center;
  gap: var(--spacing-2xl);
}

.flow-chart.direction-vertical .canvas-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--spacing-2xl);
}

/* 网格背景 */
.canvas-grid {
  position: absolute;
  inset: 0;
  background-image: 
    linear-gradient(0deg, rgba(0, 0, 0, 0.03) 1px, transparent 1px),
    linear-gradient(90deg, rgba(0, 0, 0, 0.03) 1px, transparent 1px);
  background-size: 20px 20px;
  pointer-events: none;
}

/* 迷你地图 */
.flow-chart__minimap {
  position: absolute;
  bottom: var(--spacing-md);
  right: var(--spacing-md);
  width: 200px;
  height: 150px;
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(10px);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-card);
  pointer-events: none;
}

.minimap-viewport {
  position: absolute;
  border: 2px solid var(--color-primary-500);
  background: rgba(102, 126, 234, 0.1);
  pointer-events: auto;
  cursor: move;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .flow-chart__toolbar {
    padding: var(--spacing-sm) var(--spacing-md);
    flex-wrap: wrap;
    gap: var(--spacing-sm);
  }

  .toolbar-title {
    font-size: 14px;
  }

  .flow-chart__minimap {
    display: none;
  }
}

/* 暗色模式 */
.dark .flow-chart {
  background: var(--color-gray-900);
}

.dark .flow-chart__toolbar {
  background: var(--color-gray-800);
  border-bottom-color: var(--border-color-dark);
}

.dark .flow-chart__canvas {
  background: var(--color-gray-900);
}

.dark .canvas-grid {
  background-image: 
    linear-gradient(0deg, rgba(255, 255, 255, 0.05) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 255, 255, 0.05) 1px, transparent 1px);
}

.dark .zoom-controls {
  background: var(--color-gray-800);
  border-color: var(--border-color-dark);
}

.dark .flow-chart__minimap {
  background: rgba(26, 26, 26, 0.95);
  border-color: var(--border-color-dark);
}
</style>

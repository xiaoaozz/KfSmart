import { ref, reactive } from 'vue';

export function useCanvasViewport() {
  const viewport = reactive({
    x: 20,
    y: 20,
    scale: 1
  });

  const canvasRef = ref<HTMLElement | null>(null);
  const panning = ref<{ startX: number; startY: number; panX: number; panY: number } | null>(null);

  const viewportStyle = computed(() => ({
    transform: `translate(${viewport.x}px, ${viewport.y}px) scale(${viewport.scale})`,
    transformOrigin: '0 0'
  }));

  function snap(value: number) {
    return Math.round(value / 10) * 10;
  }

  function screenToWorld(clientX: number, clientY: number, rect: DOMRect) {
    return {
      x: (clientX - rect.left - viewport.x) / viewport.scale,
      y: (clientY - rect.top - viewport.y) / viewport.scale
    };
  }

  function zoomCanvas(factor: number, pivot?: { clientX?: number; clientY?: number; rect?: DOMRect }) {
    const canvasRect = pivot?.rect || canvasRef.value?.getBoundingClientRect();
    if (!canvasRect) return;
    const oldScale = viewport.scale;
    const nextScale = Math.min(2, Math.max(0.4, Number((oldScale * factor).toFixed(2))));
    if (nextScale === oldScale) return;
    const pivotX = (pivot?.clientX ?? canvasRect.left + canvasRect.width / 2) - canvasRect.left;
    const pivotY = (pivot?.clientY ?? canvasRect.top + canvasRect.height / 2) - canvasRect.top;
    const worldX = (pivotX - viewport.x) / oldScale;
    const worldY = (pivotY - viewport.y) / oldScale;
    viewport.scale = nextScale;
    viewport.x = pivotX - worldX * nextScale;
    viewport.y = pivotY - worldY * nextScale;
  }

  function handleCanvasWheel(event: WheelEvent) {
    event.preventDefault();
    const rect = canvasRef.value?.getBoundingClientRect();
    if (!rect) return;
    const factor = event.deltaY > 0 ? 0.9 : 1.1;
    zoomCanvas(factor, { clientX: event.clientX, clientY: event.clientY, rect });
  }

  function startCanvasPan(event: MouseEvent) {
    if (event.button !== 0 && event.button !== 1) return;
    const target = event.target as HTMLElement;
    if (!target.closest('.workflow-canvas-empty')) return;
    event.preventDefault();
    panning.value = {
      startX: event.clientX,
      startY: event.clientY,
      panX: viewport.x,
      panY: viewport.y
    };
    window.addEventListener('mousemove', moveCanvas);
    window.addEventListener('mouseup', stopCanvasPan);
  }

  function moveCanvas(event: MouseEvent) {
    if (!panning.value) return;
    viewport.x = panning.value.panX + event.clientX - panning.value.startX;
    viewport.y = panning.value.panY + event.clientY - panning.value.startY;
  }

  function stopCanvasPan() {
    panning.value = null;
    window.removeEventListener('mousemove', moveCanvas);
    window.removeEventListener('mouseup', stopCanvasPan);
  }

  function resetViewport() {
    viewport.x = 20;
    viewport.y = 20;
    viewport.scale = 1;
  }

  return {
    viewport,
    canvasRef,
    viewportStyle,
    snap,
    screenToWorld,
    zoomCanvas,
    handleCanvasWheel,
    startCanvasPan,
    resetViewport,
    stopCanvasPan
  };
}

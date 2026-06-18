import { ref, onBeforeUnmount } from 'vue';
import type { NodeTraceItem } from '../types/workflow';

export function useWorkflowSocket() {
  const socket = ref<WebSocket | null>(null);
  const connected = ref(false);
  const liveTrace = ref<NodeTraceItem[]>([]);
  const executionStatus = ref<'idle' | 'running' | 'success' | 'failed'>('idle');

  function connect(token: string, executionId: string) {
    if (socket.value?.readyState === WebSocket.OPEN) {
      subscribe(executionId);
      return;
    }

    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const host = window.location.host;
    const wsUrl = `${protocol}//${host}/ws/workflow/${token}`;

    socket.value = new WebSocket(wsUrl);

    socket.value.onopen = () => {
      connected.value = true;
      subscribe(executionId);
    };

    socket.value.onmessage = (event) => {
      try {
        const msg = JSON.parse(event.data);
        handleMessage(msg);
      } catch (ignored) {}
    };

    socket.value.onclose = () => {
      connected.value = false;
    };

    socket.value.onerror = () => {
      connected.value = false;
    };
  }

  function subscribe(executionId: string) {
    if (socket.value?.readyState === WebSocket.OPEN) {
      socket.value.send(JSON.stringify({ type: 'subscribe', executionId }));
      liveTrace.value = [];
      executionStatus.value = 'running';
    }
  }

  function handleMessage(msg: any) {
    if (msg.type === 'node_completed' && msg.node) {
      liveTrace.value.push({
        nodeId: msg.node.nodeId,
        name: msg.node.nodeName,
        type: msg.node.nodeType,
        durationMs: msg.node.durationMs,
        status: msg.node.status,
        errorMessage: msg.node.errorMessage
      });
    } else if (msg.type === 'execution_completed') {
      executionStatus.value = msg.success ? 'success' : 'failed';
    } else if (msg.type === 'execution_failed') {
      executionStatus.value = 'failed';
    }
  }

  function disconnect() {
    socket.value?.close();
    socket.value = null;
    connected.value = false;
  }

  onBeforeUnmount(() => {
    disconnect();
  });

  return {
    connected,
    liveTrace,
    executionStatus,
    connect,
    disconnect
  };
}

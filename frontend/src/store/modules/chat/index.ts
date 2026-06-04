import { useWebSocket } from '@vueuse/core';

export const useChatStore = defineStore(SetupStoreId.Chat, () => {
  const conversationId = ref<string>('');
  const input = ref<Api.Chat.Input>({ message: '' });

  const list = ref<Api.Chat.Message[]>([]);

  const store = useAuthStore();

  const sessionId = ref<string>(''); // WebSocket session ID

  /** 最新一次检索结果 */
  const searchResults = ref<Api.Chat.SearchResultItem[]>([]);
  /** 检索结果加载状态 */
  const searchLoading = ref<boolean>(false);

  const {
    status: wsStatus,
    data: wsData,
    send: wsSend,
    open: wsOpen,
    close: wsClose
  } = useWebSocket(`/proxy-ws/chat/${store.token}`, {
    autoReconnect: true
  });

  // 超时兜底定时器 id
  let searchLoadingTimer: ReturnType<typeof setTimeout> | null = null;

  function clearSearchLoadingTimer() {
    if (searchLoadingTimer !== null) {
      clearTimeout(searchLoadingTimer);
      searchLoadingTimer = null;
    }
  }

    function handleWsMessage(val: string | null) {
    if (!val) return;
    let data: any;
    try {
      data = JSON.parse(val);
    } catch {
      return;
    }
    // 只打印有 type 的消息，减少噪音
    if (data.type) {
      console.log('[ChatStore] 收到WS消息 type=', data.type, data);
    }

    if (data.type === 'connection' && data.sessionId) {
      sessionId.value = data.sessionId;
      console.log('WebSocket会话ID已更新:', sessionId.value);
    }
    // 收到检索结果：关闭加载态
    if (data.type === 'search_results') {
      clearSearchLoadingTimer();
      searchResults.value = data.results ?? [];
      searchLoading.value = false;
      console.log('[ChatStore] 收到检索结果，共', searchResults.value.length, '条');
    }
    // 收到完成/停止/错误通知：兜底关闭加载态
    if (data.type === 'completion' || data.type === 'stop' || data.error) {
      clearSearchLoadingTimer();
      if (searchLoading.value) {
        console.log('[ChatStore] 兜底关闭 searchLoading, 触发消息 type=', data.type || 'error');
        searchLoading.value = false;
      }
    }
  }

  // 监听WebSocket消息
  watch(wsData, (val) => handleWsMessage(val));

  const scrollToBottom = ref<null | (() => void)>(null);

  /** 触发检索加载状态（在发送消息时调用），20s 超时兜底 */
  function startSearchLoading() {
    clearSearchLoadingTimer();
    searchResults.value = [];
    searchLoading.value = true;
    console.log('[ChatStore] 开始检索加载');
    // 20 秒后若仍未收到检索结果，强制关闭加载态
    searchLoadingTimer = setTimeout(() => {
      if (searchLoading.value) {
        console.warn('[ChatStore] 超时兜底：强制关闭 searchLoading');
        searchLoading.value = false;
      }
      searchLoadingTimer = null;
    }, 20000);
  }

  return {
    input,
    conversationId,
    list,
    wsStatus,
    wsData,
    wsSend,
    wsOpen,
    wsClose,
    sessionId,
    scrollToBottom,
    searchResults,
    searchLoading,
    startSearchLoading,
    handleWsMessage
  };
});

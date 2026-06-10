import { useWebSocket } from '@vueuse/core';
import {
  fetchCreateConversationSession,
  fetchDeleteConversationSession,
  fetchGetConversationMessages,
  fetchGetConversationSessions,
  fetchUpdateConversationPin
} from '@/service/api';

export const useChatStore = defineStore(SetupStoreId.Chat, () => {
  const conversationId = ref<string>('');
  const input = ref<Api.Chat.Input>({ message: '' });
  const list = ref<Api.Chat.Message[]>([]);
  const sessions = ref<Api.Chat.Session[]>([]);

  const store = useAuthStore();
  const sessionId = ref<string>('');

  /** 最新一次检索结果 */
  const searchResults = ref<Api.Chat.SearchResultItem[]>([]);
  /** 检索结果加载状态 */
  const searchLoading = ref<boolean>(false);
  const conversationLoading = ref<boolean>(false);
  const sessionsLoading = ref<boolean>(false);
  const deletingSessionIds = ref<string[]>([]);
  const pinningSessionIds = ref<string[]>([]);

  const {
    status: wsStatus,
    data: wsData,
    send: wsSend,
    open: wsOpen,
    close: wsClose
  } = useWebSocket(`/proxy-ws/chat/${store.token}`, {
    autoReconnect: true
  });

  let searchLoadingTimer: ReturnType<typeof setTimeout> | null = null;

  function clearSearchLoadingTimer() {
    if (searchLoadingTimer !== null) {
      clearTimeout(searchLoadingTimer);
      searchLoadingTimer = null;
    }
  }

  function upsertSession(session: Api.Chat.Session) {
    const enhancedSession: Api.Chat.Session = {
      ...session,
      keywords: session.keywords || [session.title || '', session.lastMessage || ''],
      searchText: `${session.title || ''} ${session.lastMessage || ''}`.toLowerCase().replace(/\s+/g, ' ').trim(),
      isPinned: Boolean(session.isPinned),
      pinnedAt: session.pinnedAt || ''
    };

    const index = sessions.value.findIndex(item => item.id === session.id);
    if (index >= 0) {
      sessions.value[index] = { ...sessions.value[index], ...enhancedSession };
    } else {
      sessions.value.unshift(enhancedSession);
    }
  }

  function moveSessionToTop(targetConversationId: string) {
    const index = sessions.value.findIndex(item => item.id === targetConversationId);
    if (index > 0) {
      const [session] = sessions.value.splice(index, 1);
      sessions.value.unshift(session);
    }
  }

  function syncSessionPreview(message: string, role: 'user' | 'assistant') {
    if (!conversationId.value) return;

    const now = new Date().toISOString();
    const current = sessions.value.find(item => item.id === conversationId.value);
    const base: Api.Chat.Session = current || {
      id: conversationId.value,
      title: role === 'user' ? message.slice(0, 30) || '新会话' : '新会话',
      lastMessage: '',
      lastRole: '',
      time: now,
      messageCount: 0,
      createdAt: now,
      updatedAt: now
    };

    const nextMessageCount = role === 'assistant'
      ? Math.max(base.messageCount, list.value.length)
      : Math.max(base.messageCount, list.value.length + 1);

    const nextSession: Api.Chat.Session = {
      ...base,
      title: base.title && base.title !== '新会话' ? base.title : (role === 'user' ? message.slice(0, 30) || '新会话' : base.title || '新会话'),
      lastMessage: message.slice(0, 50),
      lastRole: role,
      time: now,
      messageCount: nextMessageCount,
      updatedAt: now
    };

    upsertSession(nextSession);
  }

  function handleWsMessage(val: string | null) {
    if (!val) return;
    let data: any;
    try {
      data = JSON.parse(val);
    } catch {
      return;
    }
    if (data.type) {
      console.log('[ChatStore] 收到WS消息 type=', data.type, data);
    }

    if (data.type === 'connection' && data.sessionId) {
      sessionId.value = data.sessionId;
      console.log('WebSocket会话ID已更新:', sessionId.value);
    }
    if (data.type === 'search_results') {
      clearSearchLoadingTimer();
      searchResults.value = data.results ?? [];
      searchLoading.value = false;
      console.log('[ChatStore] 收到检索结果，共', searchResults.value.length, '条');
    }
    if (data.type === 'completion' || data.type === 'stop' || data.error) {
      clearSearchLoadingTimer();
      if (searchLoading.value) {
        console.log('[ChatStore] 兜底关闭 searchLoading, 触发消息 type=', data.type || 'error');
        searchLoading.value = false;
      }
    }
  }

  watch(wsData, val => handleWsMessage(val));

  const scrollToBottom = ref<null | (() => void)>(null);

  function startSearchLoading() {
    clearSearchLoadingTimer();
    searchResults.value = [];
    searchLoading.value = true;
    console.log('[ChatStore] 开始检索加载');
    searchLoadingTimer = setTimeout(() => {
      if (searchLoading.value) {
        console.warn('[ChatStore] 超时兜底：强制关闭 searchLoading');
        searchLoading.value = false;
      }
      searchLoadingTimer = null;
    }, 20000);
  }

  async function fetchSessions() {
    sessionsLoading.value = true;
    const { error, data } = await fetchGetConversationSessions();
    if (!error && data) {
      sessions.value = data.map(item => ({
        ...item,
        keywords: item.keywords || [item.title || '', item.lastMessage || ''],
        searchText: `${item.title || ''} ${item.lastMessage || ''}`.toLowerCase().replace(/\s+/g, ' ').trim(),
        isPinned: Boolean(item.isPinned),
        pinnedAt: item.pinnedAt || ''
      }));
      if (!conversationId.value && data.length > 0) {
        conversationId.value = data[0].id;
      }
    }
    sessionsLoading.value = false;
    return { error, data };
  }

  async function fetchConversation(targetConversationId?: string) {
    conversationLoading.value = true;
    const finalConversationId = targetConversationId || conversationId.value || undefined;
    const { error, data } = await fetchGetConversationMessages(finalConversationId);
    if (!error && data) {
      list.value = data;
      if (finalConversationId) {
        conversationId.value = finalConversationId;
      }
      scrollToBottom.value?.();
    }
    conversationLoading.value = false;
    return { error, data };
  }

  async function createSession(autoSelect = true) {
    const { error, data } = await fetchCreateConversationSession();
    if (!error && data) {
      upsertSession(data);
      moveSessionToTop(data.id);
      if (autoSelect) {
        conversationId.value = data.id;
        list.value = [];
        searchResults.value = [];
      }
    }
    return { error, data };
  }

  async function deleteSession(targetConversationId: string) {
    if (!targetConversationId) return { error: null, data: null };

    deletingSessionIds.value = [...deletingSessionIds.value, targetConversationId];
    const { error, data } = await fetchDeleteConversationSession(targetConversationId);
    deletingSessionIds.value = deletingSessionIds.value.filter(id => id !== targetConversationId);

    if (!error && data) {
      sessions.value = sessions.value.filter(item => item.id !== targetConversationId);
      if (conversationId.value === targetConversationId) {
        conversationId.value = data.currentConversationId || '';
        searchResults.value = [];
        if (conversationId.value) {
          await fetchConversation(conversationId.value);
        } else {
          list.value = [];
        }
      }
    }

    return { error, data };
  }

  async function toggleSessionPin(targetConversationId: string, pinned: boolean) {
    if (!targetConversationId) return { error: null, data: null };

    pinningSessionIds.value = [...pinningSessionIds.value, targetConversationId];
    const { error, data } = await fetchUpdateConversationPin(targetConversationId, pinned);
    pinningSessionIds.value = pinningSessionIds.value.filter(id => id !== targetConversationId);

    if (!error && data) {
      const target = sessions.value.find(item => item.id === targetConversationId);
      if (target) {
        target.isPinned = data.isPinned;
        target.pinnedAt = data.pinnedAt;
      }
    }

    return { error, data };
  }

  async function selectSession(targetConversationId: string) {
    if (!targetConversationId || targetConversationId === conversationId.value) return;
    conversationId.value = targetConversationId;
    searchResults.value = [];
    await fetchConversation(targetConversationId);
  }

  async function ensureConversationReady() {
    if (conversationId.value) {
      return conversationId.value;
    }
    const { error, data } = await createSession(true);
    if (error || !data) {
      return '';
    }
    return data.id;
  }

  function sendChatMessage(message: string, apiKeyConfigId?: number | null) {
    if (!conversationId.value) return;
    const payload: Record<string, unknown> = {
      type: 'chat',
      message,
      conversationId: conversationId.value
    };
    if (apiKeyConfigId != null) {
      payload.apiKeyConfigId = apiKeyConfigId;
    }
    wsSend(JSON.stringify(payload));
  }

  return {
    input,
    conversationId,
    list,
    sessions,
    wsStatus,
    wsData,
    wsSend,
    wsOpen,
    wsClose,
    sessionId,
    scrollToBottom,
    searchResults,
    searchLoading,
    conversationLoading,
    sessionsLoading,
    deletingSessionIds,
    pinningSessionIds,
    startSearchLoading,
    handleWsMessage,
    fetchSessions,
    fetchConversation,
    createSession,
    deleteSession,
    toggleSessionPin,
    selectSession,
    ensureConversationReady,
    sendChatMessage,
    syncSessionPreview
  };
});

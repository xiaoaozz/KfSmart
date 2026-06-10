<script setup lang="ts">
defineOptions({
  name: 'SessionList'
});

const chatStore = useChatStore();
const { sessions, sessionsLoading, conversationId, list, deletingSessionIds, pinningSessionIds } = storeToRefs(chatStore);
const searchKeyword = ref('');
const creating = ref(false);

function normalizeSearchText(text: string) {
  return text.toLowerCase().replace(/\s+/g, ' ').trim();
}

function buildSessionSearchIndex(session: Api.Chat.Session) {
  const title = session.title || '';
  const lastMessage = session.lastMessage || '';
  const keywords = [title, lastMessage, ...(session.keywords || [])]
    .join(' ')
    .toLowerCase();
  return normalizeSearchText(keywords);
}

const filteredSessions = computed(() => {
  const sortedSessions = sessions.value
    .map((session, index) => ({ session, index }))
    .sort((a, b) => {
      if (a.session.isPinned !== b.session.isPinned) {
        return a.session.isPinned ? -1 : 1;
      }

      if (a.session.isPinned && b.session.isPinned) {
        const bPinnedAt = b.session.pinnedAt || '';
        const aPinnedAt = a.session.pinnedAt || '';
        if (bPinnedAt !== aPinnedAt) {
          return bPinnedAt.localeCompare(aPinnedAt);
        }
      }

      return a.index - b.index;
    })
    .map(item => item.session);

  const keyword = normalizeSearchText(searchKeyword.value);
  if (!keyword) return sortedSessions;

  return sortedSessions.filter(session => {
    const searchText = session.searchText || buildSessionSearchIndex(session);
    return searchText.includes(keyword);
  });
});

async function fetchSessions() {
  await chatStore.fetchSessions();
}

async function selectSession(id: string) {
  await chatStore.selectSession(id);
}

async function createNewSession() {
  const activeSession = sessions.value.find(session => session.id === conversationId.value);
  const hasActiveEmptySession = Boolean(
    conversationId.value && (activeSession?.messageCount ?? list.value.length) === 0 && list.value.length === 0
  );
  if (hasActiveEmptySession) {
    window.$message?.warning('当前新会话暂无消息，请先发送消息后再创建新会话');
    return;
  }

  creating.value = true;
  const { error, data } = await chatStore.createSession(true);
  creating.value = false;

  if (error || !data) return;

  window.$message?.success('已创建新会话');
}

async function deleteSession(id: string) {
  const deleting = deletingSessionIds.value.includes(id);
  if (deleting) return;

  window.$dialog?.warning({
    title: '删除会话',
    content: '删除后将清空该会话的历史消息，确认继续吗？',
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      const { error } = await chatStore.deleteSession(id);
      if (!error) {
        window.$message?.success('会话已删除');
      }
    }
  });
}

async function togglePin(session: Api.Chat.Session) {
  if (pinningSessionIds.value.includes(session.id)) return;

  const nextPinned = !session.isPinned;
  const { error } = await chatStore.toggleSessionPin(session.id, nextPinned);
  if (!error) {
    window.$message?.success(nextPinned ? '已置顶会话' : '已取消置顶');
  }
}

function formatTime(timeStr: string) {
  if (!timeStr) return '';
  try {
    const d = new Date(timeStr.replace('T', ' '));
    const now = new Date();
    const today = now.toDateString();
    if (d.toDateString() === today) {
      return d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' });
    }
    const yesterday = new Date(now);
    yesterday.setDate(now.getDate() - 1);
    if (d.toDateString() === yesterday.toDateString()) return '昨天';
    return `${d.getMonth() + 1}-${String(d.getDate()).padStart(2, '0')}`;
  } catch {
    return '';
  }
}

function renderHighlightedText(text: string) {
  if (!searchKeyword.value) return text || '暂无消息';
  const keyword = searchKeyword.value.trim();
  if (!keyword) return text || '暂无消息';

  const escapedKeyword = keyword.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  return (text || '暂无消息').replace(new RegExp(`(${escapedKeyword})`, 'ig'), '<mark>$1</mark>');
}
</script>

<template>
  <div class="session-list flex flex-col h-full">
    <div class="flex-shrink-0 p-4 border-b border-gray-200 dark:border-gray-700">
      <div class="flex items-center justify-between mb-4">
        <h2 class="text-lg font-bold text-gray-900 dark:text-white">会话列表</h2>
        <NButton circle type="primary" size="small" :loading="creating" @click="createNewSession">
          <template #icon>
            <icon-carbon:add class="text-lg" />
          </template>
        </NButton>
      </div>

      <NInput
        v-model:value="searchKeyword"
        placeholder="搜索标题、最近消息、关键词"
        size="medium"
        class="search-input"
        clearable
      >
        <template #prefix>
          <icon-carbon:search class="text-gray-400" />
        </template>
      </NInput>
    </div>

    <div class="flex-1 overflow-y-auto min-h-0 px-2 py-2">
      <NSpin :show="sessionsLoading">
        <div
          v-if="!sessionsLoading && filteredSessions.length === 0"
          class="flex flex-col items-center justify-center py-12 text-gray-400 dark:text-gray-500"
        >
          <icon-carbon:chat-off class="text-4xl mb-3" />
          <p class="text-sm">{{ searchKeyword ? '未找到相关会话' : '暂无会话记录' }}</p>
          <p class="text-xs mt-1">{{ searchKeyword ? '可以尝试使用标题或最近消息关键词搜索' : '点击右上角可创建新的对话会话' }}</p>
        </div>

        <div
          v-for="session in filteredSessions"
          :key="session.id"
          :class="[
            'session-item p-3 mb-2 rounded-xl cursor-pointer transition-all',
            conversationId === session.id
              ? 'bg-blue-50 dark:bg-blue-900/20 border border-blue-200 dark:border-blue-800 shadow-sm'
              : 'hover:bg-gray-50 dark:hover:bg-gray-700 border border-transparent'
          ]"
          @click="selectSession(session.id)"
        >
          <div class="flex items-start gap-3">
            <div :class="[
              'w-9 h-9 rounded-lg flex items-center justify-center flex-shrink-0 mt-0.5',
              conversationId === session.id
                ? 'bg-blue-100 dark:bg-blue-800/40'
                : 'bg-gray-100 dark:bg-gray-700'
            ]">
              <icon-carbon:chat :class="[
                'text-base',
                conversationId === session.id ? 'text-blue-600' : 'text-gray-500 dark:text-gray-400'
              ]" />
            </div>

            <div class="flex-1 min-w-0">
              <div class="flex items-center justify-between gap-2 mb-0.5">
                <div class="flex items-center gap-2 min-w-0 flex-1">
                  <h3
                    :class="[
                      'text-sm font-medium truncate flex-1',
                      conversationId === session.id
                        ? 'text-blue-600 dark:text-blue-400'
                        : 'text-gray-900 dark:text-white'
                    ]"
                    v-html="renderHighlightedText(session.title || '新会话')"
                  />
                  <NTag v-if="session.isPinned" size="tiny" type="warning" round>置顶</NTag>
                  <NTag v-else size="tiny" type="success" round>最近</NTag>
                </div>
                <span class="text-xs text-gray-400 dark:text-gray-500 flex-shrink-0">
                  {{ formatTime(session.time) }}
                </span>
              </div>

              <p
                class="text-xs text-gray-500 dark:text-gray-400 truncate"
                v-html="renderHighlightedText(session.lastMessage || '暂无消息')"
              />

              <div class="flex items-center justify-between gap-2 mt-1">
                <span class="text-xs text-gray-400 dark:text-gray-500">
                  {{ session.messageCount }} 条消息
                </span>

                <div class="flex items-center gap-1">
                  <NButton
                    text
                    size="tiny"
                    :loading="pinningSessionIds.includes(session.id)"
                    @click.stop="togglePin(session)"
                  >
                    <template #icon>
                      <icon-carbon:bookmark class="text-sm" />
                    </template>
                    {{ session.isPinned ? '取消置顶' : '置顶' }}
                  </NButton>

                  <NButton
                    text
                    size="tiny"
                    :loading="deletingSessionIds.includes(session.id)"
                    class="text-red-500"
                    @click.stop="deleteSession(session.id)"
                  >
                    <template #icon>
                      <icon-carbon:trash-can class="text-sm" />
                    </template>
                    删除
                  </NButton>
                </div>
              </div>
            </div>
          </div>
        </div>
      </NSpin>
    </div>

    <div class="flex-shrink-0 p-4 border-t border-gray-200 dark:border-gray-700">
      <div class="flex items-center justify-between">
        <span class="text-xs text-gray-500 dark:text-gray-400">共 {{ sessions.length }} 个会话</span>
        <NButton text size="tiny" @click="fetchSessions">
          <template #icon>
            <icon-carbon:reset class="text-gray-400" />
          </template>
          <span class="text-xs text-gray-400">刷新</span>
        </NButton>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.session-list {
  .search-input {
    :deep(.n-input) {
      border-radius: 12px;
      border: 1.5px solid #e5e7eb;

      &:hover {
        border-color: #3b82f6;
      }

      &:focus,
      &:focus-within {
        border-color: #3b82f6;
        box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.1);
      }
    }
  }

  .session-item {
    &:active {
      transform: scale(0.98);
    }

    :deep(mark) {
      background: rgba(250, 204, 21, 0.35);
      color: inherit;
      border-radius: 4px;
      padding: 0 2px;
    }
  }
}

::-webkit-scrollbar {
  width: 6px;
}

::-webkit-scrollbar-track {
  background: transparent;
}

::-webkit-scrollbar-thumb {
  background: #d1d5db;
  border-radius: 3px;

  &:hover {
    background: #9ca3af;
  }
}
</style>

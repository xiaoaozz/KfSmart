<script setup lang="ts">
defineOptions({
  name: 'SessionList'
});

const sessions = ref<Api.Chat.Session[]>([]);
const loading = ref(false);
const searchKeyword = ref('');

const filteredSessions = computed(() => {
  if (!searchKeyword.value) return sessions.value;
  return sessions.value.filter(s =>
    s.title.includes(searchKeyword.value) || s.lastMessage.includes(searchKeyword.value)
  );
});

const activeSessionId = ref('');

async function fetchSessions() {
  loading.value = true;
  const { error, data } = await request<Api.Chat.Session[]>({
    url: 'users/conversation/sessions'
  });
  if (!error && data) {
    sessions.value = data;
    if (data.length > 0 && !activeSessionId.value) {
      activeSessionId.value = data[0].id;
    }
  }
  loading.value = false;
}

function selectSession(id: string) {
  activeSessionId.value = id;
}

function createNewSession() {
  window.$message?.info('暂不支持创建新会话，当前每位用户默认使用同一会话');
}

/** 格式化时间：今天显示时间，其他显示日期 */
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

onMounted(() => {
  fetchSessions();
});
</script>

<template>
  <div class="session-list flex flex-col h-full">
    <!-- 头部 -->
    <div class="flex-shrink-0 p-4 border-b border-gray-200 dark:border-gray-700">
      <div class="flex items-center justify-between mb-4">
        <h2 class="text-lg font-bold text-gray-900 dark:text-white">会话列表</h2>
        <NButton circle type="primary" size="small" @click="createNewSession">
          <template #icon>
            <icon-carbon:add class="text-lg" />
          </template>
        </NButton>
      </div>

      <!-- 搜索框 -->
      <NInput
        v-model:value="searchKeyword"
        placeholder="搜索会话"
        size="medium"
        class="search-input"
        clearable
      >
        <template #prefix>
          <icon-carbon:search class="text-gray-400" />
        </template>
      </NInput>
    </div>

    <!-- 会话列表 -->
    <div class="flex-1 overflow-y-auto min-h-0 px-2 py-2">
      <NSpin :show="loading">
        <!-- 空状态 -->
        <div v-if="!loading && filteredSessions.length === 0" class="flex flex-col items-center justify-center py-12 text-gray-400 dark:text-gray-500">
          <icon-carbon:chat-off class="text-4xl mb-3" />
          <p class="text-sm">{{ searchKeyword ? '未找到相关会话' : '暂无会话记录' }}</p>
          <p class="text-xs mt-1">{{ searchKeyword ? '' : '开始对话后将在此显示' }}</p>
        </div>

        <!-- 会话项 -->
        <div
          v-for="session in filteredSessions"
          :key="session.id"
          :class="[
            'session-item p-3 mb-2 rounded-xl cursor-pointer transition-all',
            activeSessionId === session.id
              ? 'bg-blue-50 dark:bg-blue-900/20 border border-blue-200 dark:border-blue-800'
              : 'hover:bg-gray-50 dark:hover:bg-gray-700 border border-transparent'
          ]"
          @click="selectSession(session.id)"
        >
          <div class="flex items-start gap-3">
            <!-- 图标 -->
            <div :class="[
              'w-9 h-9 rounded-lg flex items-center justify-center flex-shrink-0 mt-0.5',
              activeSessionId === session.id
                ? 'bg-blue-100 dark:bg-blue-800/40'
                : 'bg-gray-100 dark:bg-gray-700'
            ]">
              <icon-carbon:chat :class="[
                'text-base',
                activeSessionId === session.id ? 'text-blue-600' : 'text-gray-500 dark:text-gray-400'
              ]" />
            </div>

            <!-- 内容 -->
            <div class="flex-1 min-w-0">
              <div class="flex items-center justify-between mb-0.5">
                <h3 :class="[
                  'text-sm font-medium truncate flex-1 mr-2',
                  activeSessionId === session.id
                    ? 'text-blue-600 dark:text-blue-400'
                    : 'text-gray-900 dark:text-white'
                ]">
                  {{ session.title || '新会话' }}
                </h3>
                <span class="text-xs text-gray-400 dark:text-gray-500 flex-shrink-0">
                  {{ formatTime(session.time) }}
                </span>
              </div>
              <p class="text-xs text-gray-500 dark:text-gray-400 truncate">
                {{ session.lastMessage || '暂无消息' }}
              </p>
              <div class="flex items-center gap-1 mt-1">
                <span class="text-xs text-gray-400 dark:text-gray-500">
                  {{ session.messageCount }} 条消息
                </span>
              </div>
            </div>
          </div>
        </div>
      </NSpin>
    </div>

    <!-- 底部信息 -->
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

      &:focus, &:focus-within {
        border-color: #3b82f6;
        box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.1);
      }
    }
  }

  .session-item {
    &:active {
      transform: scale(0.98);
    }
  }
}

// 滚动条样式
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

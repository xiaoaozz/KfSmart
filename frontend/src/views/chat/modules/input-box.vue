<script setup lang="ts">
const chatStore = useChatStore();
const { input, list, wsStatus, wsData, conversationId } = storeToRefs(chatStore);

const latestMessage = computed(() => {
  return list.value[list.value.length - 1] ?? {};
});

const isSending = computed(() => {
  return latestMessage.value?.role === 'assistant' && ['loading', 'pending'].includes(latestMessage.value?.status || '');
});

const sendable = computed(
  () => (!input.value.message && !isSending.value) || ['CLOSED', 'CONNECTING'].includes(wsStatus.value)
);

watch(wsData, val => {
  if (!val) return;
  let data: any = {};
  try {
    data = JSON.parse(val);
  } catch {
    return;
  }
  const assistant = list.value[list.value.length - 1];

  if (data.type === 'completion') {
    if (data.status === 'finished' && assistant?.status !== 'error') {
      assistant.status = 'finished';
      if (assistant?.content) {
        chatStore.syncSessionPreview(assistant.content, 'assistant');
      }
    } else if (data.status === 'stopped' && assistant) {
      assistant.status = 'stopped';
      if (typeof data.message === 'string' && !assistant.content) {
        assistant.content = data.message;
      }
    } else if (data.status === 'failed' && assistant) {
      assistant.status = 'error';
    }
    return;
  }

  if (data.type === 'stop') {
    if (assistant) {
      assistant.status = 'stopped';
      if (typeof data.partialContent === 'string') {
        assistant.content = data.partialContent;
      }
      if (!assistant.content && typeof data.message === 'string') {
        assistant.content = data.message;
      }
    }
    return;
  }

  if (data.error) {
    assistant && (assistant.status = 'error');
  } else if (data.chunk) {
    assistant && (assistant.status = 'loading');
    assistant && (assistant.content += data.chunk);
  }
});

const handleSend = async () => {
  if (isSending.value) {
    const { error, data } = await request<Api.Chat.Token>({ url: 'chat/websocket-token', baseURL: 'proxy-api' });
    if (error) return;

    chatStore.wsSend(JSON.stringify({ type: 'stop', _internal_cmd_token: data.cmdToken }));

    assistant.status = 'stopped';
    if (!latestMessage.value.content) list.value.pop();
    return;
  }

  const message = input.value.message;
  if (!message) return;

  const readyConversationId = await chatStore.ensureConversationReady();
  if (!readyConversationId) return;

  list.value.push({
    content: message,
    role: 'user',
    timestamp: new Date().toISOString()
  });
  chatStore.syncSessionPreview(message, 'user');
  chatStore.startSearchLoading();
  chatStore.sendChatMessage(message);
  list.value.push({
    content: '',
    role: 'assistant',
    status: 'pending'
  });
  input.value.message = '';
};

const inputRef = ref();
const insertNewline = () => {
  const textarea = inputRef.value;
  const start = textarea.selectionStart;
  const end = textarea.selectionEnd;

  input.value.message = `${input.value.message.substring(0, start)}\n${input.value.message.substring(end)}`;

  nextTick(() => {
    textarea.selectionStart = start + 1;
    textarea.selectionEnd = start + 1;
    textarea.focus();
  });
};

const handShortcut = (e: KeyboardEvent) => {
  if (e.key === 'Enter') {
    e.preventDefault();

    if (!e.shiftKey && !e.ctrlKey) {
      handleSend();
    } else insertNewline();
  }
};

import { fetchGetKnowledgeBases } from '@/service/api/knowledge-base';

const selectedKnowledgeBase = ref('');
const knowledgeBaseOptions = ref<{ label: string; value: string }[]>([]);

async function loadKnowledgeBaseOptions() {
  const { error, data } = await fetchGetKnowledgeBases();
  if (!error && data) {
    knowledgeBaseOptions.value = data.map(kb => ({
      label: `${kb.name}（${kb.fileCount}）`,
      value: kb.kbId
    }));
    if (knowledgeBaseOptions.value.length > 0 && !selectedKnowledgeBase.value) {
      selectedKnowledgeBase.value = knowledgeBaseOptions.value[0].value;
    }
  }
}

onMounted(() => {
  loadKnowledgeBaseOptions();
});
</script>

<template>
  <div class="input-box-container flex-shrink-0 border-t border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800">
    <div class="px-6 py-3 border-b border-gray-100 dark:border-gray-700">
      <div class="flex items-center justify-between">
        <div class="flex items-center gap-3">
          <NButton text class="p-2 hover:bg-gray-100 dark:hover:bg-gray-700 rounded-lg">
            <template #icon>
              <icon-carbon:attachment class="text-gray-500 text-lg" />
            </template>
          </NButton>

          <NButton text class="p-2 hover:bg-gray-100 dark:hover:bg-gray-700 rounded-lg">
            <template #icon>
              <icon-carbon:cloud-upload class="text-gray-500 text-lg" />
            </template>
          </NButton>

          <div class="flex items-center gap-2 px-3 py-1.5 bg-gray-50 dark:bg-gray-700 rounded-lg">
            <icon-carbon:data-base class="text-blue-500 text-sm" />
            <NSelect
              v-model:value="selectedKnowledgeBase"
              :options="knowledgeBaseOptions"
              size="small"
              class="knowledge-base-select w-180px"
              :bordered="false"
            />
            <icon-carbon:chevron-down class="text-gray-400 text-xs" />
          </div>
        </div>

        <div class="flex items-center gap-2 text-xs text-gray-500 dark:text-gray-400">
          <span>模型：</span>
          <NButton text class="flex items-center gap-1 hover:text-blue-600">
            <span class="font-medium">KnowFlow-Chat 3.5</span>
            <icon-carbon:chevron-down class="text-xs" />
          </NButton>
        </div>
      </div>
    </div>

    <div class="px-6 py-4">
      <div class="mb-3 flex items-center justify-between text-xs text-gray-500 dark:text-gray-400">
        <span>当前会话：{{ conversationId || '未创建，会在首次发送时自动创建' }}</span>
      </div>
      <div class="input-wrapper relative">
        <textarea
          ref="inputRef"
          v-model.trim="input.message"
          placeholder="输入问题，基于知识库进行问答..."
          class="input-textarea w-full min-h-80px max-h-200px resize-none bg-transparent text-gray-900 dark:text-white outline-none text-base"
          @keydown="handShortcut"
        />
      </div>
    </div>

    <div class="px-6 pb-4 flex items-center justify-between">
      <div class="flex items-center gap-2 text-xs text-gray-500 dark:text-gray-400">
        <span>连接状态：</span>
        <icon-eos-icons:loading v-if="wsStatus === 'CONNECTING'" class="text-yellow-500" />
        <div v-else-if="wsStatus === 'OPEN'" class="flex items-center gap-1 text-green-600">
          <icon-carbon:checkmark-filled />
          <span>已连接</span>
        </div>
        <div v-else class="flex items-center gap-1 text-red-500">
          <icon-carbon:close-filled />
          <span>未连接</span>
        </div>
      </div>

      <div class="flex items-center gap-2">
        <span class="text-xs text-gray-400 dark:text-gray-500 mr-2">
          Enter 发送 / Shift+Enter 换行
        </span>

        <NButton
          :disabled="sendable"
          type="primary"
          size="large"
          class="send-button h-40px px-8"
          @click="handleSend"
        >
          <template #icon>
            <icon-material-symbols:stop-rounded v-if="isSending" class="text-xl" />
            <icon-carbon:send-alt v-else class="text-xl" />
          </template>
          {{ isSending ? '停止' : '发送' }}
        </NButton>
      </div>
    </div>

    <div class="px-6 pb-3 text-center">
      <p class="text-xs text-gray-400 dark:text-gray-500">
        内容由 AI 生成，请结合参考资料核实准确性
      </p>
    </div>
  </div>
</template>

<style scoped lang="scss">
.input-box-container {
  .knowledge-base-select {
    :deep(.n-base-selection) {
      background: transparent !important;
      border: none !important;
      padding: 0 !important;

      .n-base-selection__border,
      .n-base-selection__state-border {
        display: none !important;
      }
    }
  }

  .input-textarea {
    font-family: inherit;
    line-height: 1.6;

    &::placeholder {
      color: #9ca3af;
    }

    &::-webkit-scrollbar {
      width: 6px;
    }

    &::-webkit-scrollbar-track {
      background: transparent;
    }

    &::-webkit-scrollbar-thumb {
      background: #d1d5db;
      border-radius: 3px;

      &:hover {
        background: #9ca3af;
      }
    }
  }

  .send-button {
    border-radius: 10px;
    font-weight: 600;
    transition: all 0.3s ease;

    &:hover:not(:disabled) {
      transform: translateY(-1px);
      box-shadow: 0 4px 12px rgba(59, 130, 246, 0.3);
    }

    &:active:not(:disabled) {
      transform: translateY(0);
    }
  }
}

:deep(.dark) {
  .input-textarea {
    &::placeholder {
      color: #6b7280;
    }

    &::-webkit-scrollbar-thumb {
      background: #4b5563;

      &:hover {
        background: #6b7280;
      }
    }
  }
}
</style>

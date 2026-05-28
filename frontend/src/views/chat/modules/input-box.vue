<script setup lang="ts">
const chatStore = useChatStore();
const { input, list, wsStatus, wsData } = storeToRefs(chatStore);

const latestMessage = computed(() => {
  return list.value[list.value.length - 1] ?? {};
});

const isSending = computed(() => {
  return (
    latestMessage.value?.role === 'assistant' && ['loading', 'pending'].includes(latestMessage.value?.status || '')
  );
});

const sendable = computed(
  () => (!input.value.message && !isSending) || ['CLOSED', 'CONNECTING'].includes(wsStatus.value)
);

watch(wsData, val => {
  const data = JSON.parse(val);
  const assistant = list.value[list.value.length - 1];

  if (data.type === 'completion' && data.status === 'finished' && assistant.status !== 'error')
    assistant.status = 'finished';
  if (data.error) assistant.status = 'error';
  else if (data.chunk) {
    assistant.status = 'loading';
    assistant.content += data.chunk;
  }
});

const handleSend = async () => {
  //  判断是否正在发送, 如果发送中，则停止ai继续响应
  if (isSending.value) {
    const { error, data } = await request<Api.Chat.Token>({ url: 'chat/websocket-token', baseURL: 'proxy-api' });
    if (error) return;

    chatStore.wsSend(JSON.stringify({ type: 'stop', _internal_cmd_token: data.cmdToken }));

    list.value[list.value.length - 1].status = 'finished';
    if (!latestMessage.value.content) list.value.pop();
    return;
  }

  list.value.push({
    content: input.value.message,
    role: 'user'
  });
  chatStore.wsSend(input.value.message);
  list.value.push({
    content: '',
    role: 'assistant',
    status: 'pending'
  });
  input.value.message = '';
};

const inputRef = ref();
// 手动插入换行符（确保所有浏览器兼容）
const insertNewline = () => {
  const textarea = inputRef.value;
  const start = textarea.selectionStart;
  const end = textarea.selectionEnd;

  // 在光标位置插入换行符
  input.value.message = `${input.value.message.substring(0, start)}\n${input.value.message.substring(end)}`;

  // 更新光标位置（在插入的换行符之后）
  nextTick(() => {
    textarea.selectionStart = start + 1;
    textarea.selectionEnd = start + 1;
    textarea.focus(); // 确保保持焦点
  });
};

// ctrl + enter 换行
// enter 发送
const handShortcut = (e: KeyboardEvent) => {
  if (e.key === 'Enter') {
    e.preventDefault();

    if (!e.shiftKey && !e.ctrlKey) {
      handleSend();
    } else insertNewline();
  }
};

// 知识库选择器
const selectedKnowledgeBase = ref('企业知识库（12）');
const knowledgeBaseOptions = [
  { label: '企业知识库（12）', value: '企业知识库（12）' },
  { label: '产品知识库', value: '产品知识库' },
  { label: '技术文档库', value: '技术文档库' }
];
</script>

<template>
  <div class="input-box-container border-t border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800">
    <!-- 顶部工具栏 -->
    <div class="px-6 py-3 border-b border-gray-100 dark:border-gray-700">
      <div class="flex items-center justify-between">
        <div class="flex items-center gap-3">
          <!-- 附件按钮 -->
          <NButton text class="p-2 hover:bg-gray-100 dark:hover:bg-gray-700 rounded-lg">
            <template #icon>
              <icon-carbon:attachment class="text-gray-500 text-lg" />
            </template>
          </NButton>

          <!-- 上传文件按钮 -->
          <NButton text class="p-2 hover:bg-gray-100 dark:hover:bg-gray-700 rounded-lg">
            <template #icon>
              <icon-carbon:cloud-upload class="text-gray-500 text-lg" />
            </template>
          </NButton>

          <!-- 知识库选择 -->
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

        <!-- 模型选择 -->
        <div class="flex items-center gap-2 text-xs text-gray-500 dark:text-gray-400">
          <span>模型：</span>
          <NButton text class="flex items-center gap-1 hover:text-blue-600">
            <span class="font-medium">KnowFlow-Chat 3.5</span>
            <icon-carbon:chevron-down class="text-xs" />
          </NButton>
        </div>
      </div>
    </div>

    <!-- 输入区域 -->
    <div class="px-6 py-4">
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

    <!-- 底部操作栏 -->
    <div class="px-6 pb-4 flex items-center justify-between">
      <div class="flex items-center gap-2 text-xs text-gray-500 dark:text-gray-400">
        <span>连接状态：</span>
        <icon-eos-icons:loading v-if="wsStatus === 'CONNECTING'" class="text-yellow-500" />
        <div v-else-if="wsStatus === 'OPEN'" class="flex items-center gap-1 text-green-600">
          <icon-fluent:plug-connected-checkmark-20-filled />
          <span>已连接</span>
        </div>
        <div v-else class="flex items-center gap-1 text-red-500">
          <icon-tabler:plug-connected-x />
          <span>未连接</span>
        </div>
      </div>

      <div class="flex items-center gap-2">
        <!-- 发送快捷键提示 -->
        <span class="text-xs text-gray-400 dark:text-gray-500 mr-2">
          Enter 发送 / Shift+Enter 换行
        </span>
        
        <!-- 发送按钮 -->
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

    <!-- 底部提示 -->
    <div class="px-6 pb-3 text-center">
      <p class="text-xs text-gray-400 dark:text-gray-500">
        内容由 AI 生成，请合参考并核实准确性
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

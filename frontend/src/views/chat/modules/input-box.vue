<script setup lang="ts">
import { ref, computed, watch, nextTick } from 'vue';
import AnimatedButton from '@/components/modern/AnimatedButton.vue';

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

const sendable = computed(() => {
  return !input.value.message || ['CLOSED', 'CONNECTING'].includes(wsStatus.value);
});

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
  // 判断是否正在发送, 如果发送中，则停止AI继续响应
  if (isSending.value) {
    const { error, data } = await request<Api.Chat.Token>({ url: 'chat/websocket-token', baseURL: 'proxy-api' });
    if (error) return;

    chatStore.wsSend(JSON.stringify({ type: 'stop', _internal_cmd_token: data.cmdToken }));

    list.value[list.value.length - 1].status = 'finished';
    if (!latestMessage.value.content) list.value.pop();
    return;
  }

  if (!input.value.message.trim()) return;

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
    textarea.focus();
  });
};

// Ctrl/Shift + Enter 换行, Enter 发送
const handleShortcut = (e: KeyboardEvent) => {
  if (e.key === 'Enter') {
    e.preventDefault();

    if (!e.shiftKey && !e.ctrlKey) {
      handleSend();
    } else {
      insertNewline();
    }
  }
};

// 建议问题列表
const suggestedQuestions = ref([
  '介绍一下你自己',
  '你能帮我做什么？',
  '如何上传文档？',
  '怎么查看聊天历史？'
]);

function handleSuggestedQuestion(question: string) {
  input.value.message = question;
  handleSend();
}

// WebSocket连接状态文本和颜色
const wsStatusInfo = computed(() => {
  const statusMap = {
    CONNECTING: { text: '连接中...', color: 'text-yellow-500', icon: 'i-eos-icons:loading' },
    OPEN: { text: '已连接', color: 'text-green-500', icon: 'i-carbon:checkmark-filled' },
    CLOSED: { text: '已断开', color: 'text-red-500', icon: 'i-carbon:close-filled' },
    CLOSING: { text: '断开中...', color: 'text-orange-500', icon: 'i-eos-icons:loading' }
  };

  return statusMap[wsStatus.value] || statusMap.CLOSED;
});
</script>

<template>
  <div class="modern-input-box-wrapper">
    <!-- 建议问题 (仅当输入框为空时显示) -->
    <div v-if="!input.message && suggestedQuestions.length" class="suggested-questions mb-4 animate-slide-up">
      <div class="flex items-center gap-2 mb-3">
        <div class="i-carbon:idea text-lg text-primary-500" />
        <span class="text-sm text-gray-600 dark:text-gray-400 font-500">试试问我:</span>
      </div>
      <div class="flex flex-wrap gap-2">
        <button
          v-for="(question, index) in suggestedQuestions"
          :key="index"
          class="suggested-question-btn"
          @click="handleSuggestedQuestion(question)"
        >
          <span>{{ question }}</span>
          <div class="i-carbon:arrow-right text-sm" />
        </button>
      </div>
    </div>

    <!-- 输入框卡片 -->
    <div class="input-card">
      <!-- 文本输入区域 -->
      <div class="input-wrapper">
        <textarea
          ref="inputRef"
          v-model.trim="input.message"
          placeholder="💭 给派聪明发送消息... (Shift/Ctrl+Enter 换行，Enter 发送)"
          class="message-input scrollbar-modern"
          rows="1"
          @keydown="handleShortcut"
          @input="
            e => {
              const target = e.target as HTMLTextAreaElement;
              target.style.height = 'auto';
              target.style.height = Math.min(target.scrollHeight, 200) + 'px';
            }
          "
        />

        <!-- 附件上传按钮 (暂时隐藏) -->
        <!-- <button class="action-btn" title="上传附件">
          <div class="i-carbon:attachment text-xl" />
        </button> -->
      </div>

      <!-- 底部工具栏 -->
      <div class="toolbar">
        <!-- 左侧: 连接状态 -->
        <div class="flex items-center gap-2">
          <div :class="[wsStatusInfo.icon, wsStatusInfo.color, 'text-lg']" />
          <span class="text-xs" :class="wsStatusInfo.color">{{ wsStatusInfo.text }}</span>
        </div>

        <!-- 右侧: 发送按钮 -->
        <div class="flex items-center gap-2">
          <!-- 字数统计 -->
          <span v-if="input.message" class="text-xs text-gray-400">
            {{ input.message.length }} 字
          </span>

          <!-- 发送/停止按钮 -->
          <AnimatedButton
            :variant="isSending ? 'error' : 'primary'"
            size="sm"
            :disabled="sendable && !isSending"
            :loading="false"
            :ripple="true"
            @click="handleSend"
          >
            <template #icon>
              <div v-if="isSending" class="i-carbon:stop-filled text-lg" />
              <div v-else class="i-carbon:send-alt text-lg" />
            </template>
            {{ isSending ? '停止' : '发送' }}
          </AnimatedButton>
        </div>
      </div>
    </div>

    <!-- 快捷键提示 -->
    <div class="text-center mt-2 text-xs text-gray-400 dark:text-gray-600">
      <span>Shift/Ctrl+Enter 换行</span>
      <span class="mx-2">·</span>
      <span>Enter 发送</span>
    </div>
  </div>
</template>

<style scoped lang="scss">
.modern-input-box-wrapper {
  position: relative;
  width: 100%;
  max-width: 900px;
  margin: 0 auto;
  padding: 0 16px 16px;
}

/* 建议问题按钮 */
.suggested-questions {
  .suggested-question-btn {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    padding: 8px 16px;
    background: white;
    border: 1px solid rgba(102, 126, 234, 0.2);
    border-radius: 20px;
    color: #667eea;
    font-size: 14px;
    font-weight: 500;
    transition: all 0.2s ease;
    cursor: pointer;
    box-shadow: 0 2px 8px rgba(102, 126, 234, 0.1);

    &:hover {
      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
      color: white;
      border-color: transparent;
      transform: translateY(-2px);
      box-shadow: 0 4px 12px rgba(102, 126, 234, 0.3);

      .i-carbon-arrow-right {
        transform: translateX(4px);
      }
    }

    &:active {
      transform: translateY(0);
    }
  }
}

/* 输入框卡片 */
.input-card {
  background: white;
  border: 2px solid rgba(102, 126, 234, 0.15);
  border-radius: 20px;
  box-shadow:
    0 4px 20px rgba(102, 126, 234, 0.08),
    0 0 0 1px rgba(102, 126, 234, 0.05);
  transition: all 0.3s ease;
  overflow: hidden;

  &:focus-within {
    border-color: rgba(102, 126, 234, 0.5);
    box-shadow:
      0 8px 30px rgba(102, 126, 234, 0.15),
      0 0 0 3px rgba(102, 126, 234, 0.1);
    transform: translateY(-2px);
  }
}

/* 输入区域包装器 */
.input-wrapper {
  display: flex;
  align-items: flex-end;
  gap: 12px;
  padding: 16px 20px 12px;
  position: relative;
}

/* 消息输入框 */
.message-input {
  flex: 1;
  min-height: 24px;
  max-height: 200px;
  padding: 0;
  border: none;
  background: transparent;
  color: #333;
  font-size: 15px;
  line-height: 1.6;
  resize: none;
  outline: none;
  font-family: inherit;

  &::placeholder {
    color: rgba(102, 126, 234, 0.4);
  }

  &::-webkit-scrollbar {
    width: 6px;
  }

  &::-webkit-scrollbar-thumb {
    background: rgba(102, 126, 234, 0.2);
    border-radius: 3px;

    &:hover {
      background: rgba(102, 126, 234, 0.3);
    }
  }
}

/* 操作按钮 */
.action-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 8px;
  background: rgba(102, 126, 234, 0.08);
  color: #667eea;
  border: none;
  cursor: pointer;
  transition: all 0.2s ease;

  &:hover {
    background: rgba(102, 126, 234, 0.15);
    transform: scale(1.05);
  }

  &:active {
    transform: scale(0.95);
  }
}

/* 工具栏 */
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 20px;
  border-top: 1px solid rgba(0, 0, 0, 0.06);
  background: rgba(102, 126, 234, 0.02);
}

/* 暗色模式适配 */
.dark {
  .input-card {
    background: #27272a;
    border-color: rgba(139, 158, 255, 0.2);

    &:focus-within {
      border-color: rgba(139, 158, 255, 0.5);
      box-shadow:
        0 8px 30px rgba(139, 158, 255, 0.2),
        0 0 0 3px rgba(139, 158, 255, 0.15);
    }
  }

  .message-input {
    color: #f1f1f1;

    &::placeholder {
      color: rgba(139, 158, 255, 0.4);
    }
  }

  .toolbar {
    border-top-color: rgba(255, 255, 255, 0.06);
    background: rgba(139, 158, 255, 0.03);
  }

  .suggested-question-btn {
    background: #27272a;
    border-color: rgba(139, 158, 255, 0.2);
    color: #8b9eff;

    &:hover {
      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
      color: white;
    }
  }
}
</style>

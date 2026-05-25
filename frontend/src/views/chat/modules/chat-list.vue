<script setup lang="ts">
import { ref, watch, computed, watchEffect, onMounted } from 'vue';
import { NScrollbar, NForm, NFormItem, NDatePicker, NSpin } from 'naive-ui';
import { VueMarkdownItProvider } from 'vue-markdown-shiki';
import ChatMessage from './chat-message.vue';

defineOptions({
  name: 'ChatList'
});

const chatStore = useChatStore();
const { list, sessionId } = storeToRefs(chatStore);

const loading = ref(false);
const scrollbarRef = ref<InstanceType<typeof NScrollbar>>();

watch(() => [...list.value], scrollToBottom);

function scrollToBottom() {
  setTimeout(() => {
    scrollbarRef.value?.scrollBy({
      top: 999999999999999,
      behavior: 'smooth'
    });
  }, 100);
}

const range = ref<[number, number]>([dayjs().subtract(7, 'day').valueOf(), dayjs().add(1, 'day').valueOf()]);

const params = computed(() => {
  return {
    start_date: dayjs(range.value[0]).format('YYYY-MM-DD'),
    end_date: dayjs(range.value[1]).format('YYYY-MM-DD')
  };
});

watchEffect(() => {
  getList();
});

async function getList() {
  loading.value = true;
  const { error, data } = await request<Api.Chat.Message[]>({
    url: 'users/conversation',
    params: params.value
  });
  if (!error) {
    list.value = data;
  }
  loading.value = false;
}

onMounted(() => {
  chatStore.scrollToBottom = scrollToBottom;
});
</script>

<template>
  <Suspense>
    <div class="chat-list-container h-0 flex-auto flex flex-col">
      <!-- 顶部工具栏 -->
      <Teleport defer to="#header-extra">
        <div class="chat-toolbar px-6">
          <NForm :model="params" label-placement="left" :show-feedback="false" inline>
            <NFormItem label="时间范围">
              <NDatePicker v-model:value="range" type="daterange" class="date-picker-modern" />
            </NFormItem>
          </NForm>
        </div>
      </Teleport>

      <!-- 消息列表滚动区域 -->
      <NScrollbar ref="scrollbarRef" class="flex-1 chat-scrollbar">
        <div class="chat-messages-wrapper">
          <NSpin :show="loading">
            <!-- 空状态 -->
            <div v-if="!loading && list.length === 0" class="empty-state">
              <div class="empty-icon">
                <div class="i-carbon:chat text-6xl text-primary-300" />
              </div>
              <h3 class="text-xl font-600 text-gray-600 dark:text-gray-400 mb-2">暂无聊天记录</h3>
              <p class="text-sm text-gray-400 dark:text-gray-500">开始与派聪明对话吧！</p>
            </div>

            <!-- 消息列表 -->
            <div v-else class="messages-list">
              <VueMarkdownItProvider>
                <ChatMessage v-for="(item, index) in list" :key="index" :msg="item" :session-id="sessionId" />
              </VueMarkdownItProvider>
            </div>

            <!-- 底部占位 (确保最后一条消息有足够空间) -->
            <div class="h-4" />
          </NSpin>
        </div>
      </NScrollbar>
    </div>
  </Suspense>
</template>

<style scoped lang="scss">
.chat-list-container {
  position: relative;
  background: linear-gradient(to bottom, #f7fafc 0%, #ffffff 100%);

  .dark & {
    background: linear-gradient(to bottom, #18181b 0%, #27272a 100%);
  }
}

/* 工具栏样式 */
.chat-toolbar {
  :deep(.n-form) {
    .n-form-item {
      margin-bottom: 0;
    }

    .n-form-item-label {
      font-size: 14px;
      font-weight: 500;
      color: #667eea;
    }
  }

  :deep(.date-picker-modern) {
    .n-input {
      border-radius: 8px;
      border-color: rgba(102, 126, 234, 0.2);
      transition: all 0.2s ease;

      &:hover {
        border-color: rgba(102, 126, 234, 0.4);
      }

      &:focus-within {
        border-color: #667eea;
        box-shadow: 0 0 0 3px rgba(102, 126, 234, 0.1);
      }
    }
  }
}

/* 滚动条样式 */
.chat-scrollbar {
  :deep(.n-scrollbar-rail) {
    right: 4px;
  }

  :deep(.n-scrollbar-rail__scrollbar) {
    background: rgba(102, 126, 234, 0.3);
    border-radius: 4px;
    width: 6px;
    transition: background 0.2s ease;

    &:hover {
      background: rgba(102, 126, 234, 0.5);
      width: 8px;
    }
  }
}

/* 消息列表容器 */
.chat-messages-wrapper {
  max-width: 1000px;
  margin: 0 auto;
  padding: 24px 16px;
  min-height: 100%;
}

/* 消息列表 */
.messages-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* 空状态 */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 400px;
  padding: 40px;
  text-align: center;

  .empty-icon {
    width: 120px;
    height: 120px;
    display: flex;
    align-items: center;
    justify-content: center;
    background: linear-gradient(135deg, rgba(102, 126, 234, 0.1) 0%, rgba(79, 172, 254, 0.1) 100%);
    border-radius: 50%;
    margin-bottom: 24px;
    animation: float 3s ease-in-out infinite;
  }
}

/* 加载状态样式 */
:deep(.n-spin-container) {
  min-height: 400px;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* 浮动动画 */
@keyframes float {
  0%,
  100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-10px);
  }
}

/* 响应式适配 */
@media (max-width: 768px) {
  .chat-messages-wrapper {
    padding: 16px 12px;
  }

  .messages-list {
    gap: 12px;
  }
}
</style>

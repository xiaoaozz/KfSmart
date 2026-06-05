<script setup lang="ts">
import { NScrollbar } from 'naive-ui';
import { VueMarkdownItProvider } from 'vue-markdown-shiki';
import ChatMessage from './chat-message.vue';

defineOptions({
  name: 'ChatList'
});

const chatStore = useChatStore();
const { list, sessionId, conversationId, conversationLoading } = storeToRefs(chatStore);
const scrollbarRef = ref<InstanceType<typeof NScrollbar>>();

watch(() => [...list.value], scrollToBottom);
watch(
  () => conversationId.value,
  () => {
    scrollToBottom();
  }
);

function scrollToBottom() {
  setTimeout(() => {
    scrollbarRef.value?.scrollBy({
      top: 999999999999999,
      behavior: 'auto'
    });
  }, 100);
}

onMounted(async () => {
  chatStore.scrollToBottom = scrollToBottom;

  await chatStore.fetchSessions();

  if (conversationId.value) {
    await chatStore.fetchConversation(conversationId.value);
  }
});
</script>

<template>
  <div class="chat-list-container flex-1 flex flex-col bg-white dark:bg-gray-800 min-h-0 overflow-hidden">
    <div class="flex-shrink-0 px-6 py-4 border-b border-gray-200 dark:border-gray-700">
      <div class="flex items-center justify-between">
        <div class="flex items-center gap-3">
          <div class="w-10 h-10 rounded-xl bg-gradient-to-br from-blue-400 to-blue-600 flex items-center justify-center">
            <icon-carbon:chat class="text-white text-xl" />
          </div>
          <div>
            <h2 class="text-lg font-bold text-gray-900 dark:text-white">企业知识问答助手</h2>
            <p class="text-xs text-gray-500 dark:text-gray-400">
              基于 RAG 的智能检索生成，为您提供准确、可信的企业知识问答服务
            </p>
          </div>
        </div>
        <div class="flex items-center gap-2">
          <NTag size="small" type="info" round>
            {{ conversationId ? '当前会话已连接' : '未选择会话' }}
          </NTag>
        </div>
      </div>
    </div>

    <Suspense>
      <NScrollbar ref="scrollbarRef" class="flex-1 h-0">
        <div class="px-6 py-4">
          <NSpin :show="conversationLoading">
            <div v-if="!conversationLoading && list.length === 0" class="py-16 text-center text-gray-400 dark:text-gray-500">
              <icon-carbon:chat-off class="text-4xl mb-3" />
              <p class="text-sm">当前会话还没有消息</p>
              <p class="text-xs mt-1">发送第一条消息后将开始记录该轮对话</p>
            </div>
            <VueMarkdownItProvider v-else>
              <ChatMessage v-for="(item, index) in list" :key="index" :msg="item" :session-id="sessionId" />
            </VueMarkdownItProvider>
          </NSpin>
        </div>
      </NScrollbar>
    </Suspense>
  </div>
</template>

<style scoped lang="scss">
.chat-list-container {
  :deep(.n-scrollbar) {
    flex: 1;
    min-height: 0;
  }

  :deep(.n-scrollbar-content) {
    display: flex;
    flex-direction: column;
  }
}

:deep(.n-scrollbar-rail) {
  right: 4px !important;
}

:deep(.n-scrollbar-rail__scrollbar) {
  width: 6px !important;
  border-radius: 3px;
}
</style>

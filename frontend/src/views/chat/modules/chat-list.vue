<script setup lang="ts">
import { NScrollbar } from 'naive-ui';
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
      behavior: 'auto'
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
  <div class="chat-list-container flex-1 flex flex-col bg-white dark:bg-gray-800">
    <!-- 顶部信息栏 -->
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
          <NButton circle size="small" tertiary>
            <template #icon>
              <icon-carbon:bookmark class="text-lg" />
            </template>
          </NButton>
          <NButton circle size="small" tertiary>
            <template #icon>
              <icon-carbon:overflow-menu-horizontal class="text-lg" />
            </template>
          </NButton>
        </div>
      </div>
    </div>

    <!-- 消息列表 -->
    <Suspense>
      <NScrollbar ref="scrollbarRef" class="flex-1">
        <div class="px-6 py-4">
          <NSpin :show="loading">
            <VueMarkdownItProvider>
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
  :deep(.n-scrollbar-content) {
    display: flex;
    flex-direction: column;
  }
}

// 滚动条样式
:deep(.n-scrollbar-rail) {
  right: 4px !important;
}

:deep(.n-scrollbar-rail__scrollbar) {
  width: 6px !important;
  border-radius: 3px;
}
</style>

<script setup lang="ts">
import ChunkDetailModal from './chunk-detail-modal.vue';

defineOptions({
  name: 'SearchResults'
});

const chatStore = useChatStore();
const { searchResults, searchLoading } = storeToRefs(chatStore);

/** 当前点击的卡片信息 */
const selectedItem = ref<Api.Chat.SearchResultItem | null>(null);
const detailVisible = ref(false);

function openDetail(item: Api.Chat.SearchResultItem) {
  // 先把 visible 设为 false，确保每次打开都触发 watch(visible) 或 onMounted 重新请求
  detailVisible.value = false;
  selectedItem.value = item;
  nextTick(() => {
    detailVisible.value = true;
  });
}

/** 检索得分转为百分比，最高 100 */
function scoreToPercent(score: number): number {
  const clamped = Math.min(Math.max(score, 0), 1);
  return Math.round(clamped * 100);
}

/** 得分对应的颜色 */
function scoreColor(score: number): string {
  const pct = scoreToPercent(score);
  if (pct >= 80) return '#22c55e';
  if (pct >= 60) return '#3b82f6';
  if (pct >= 40) return '#f59e0b';
  return '#ef4444';
}
</script>

<template>
  <div class="search-results flex flex-col h-full">
    <!-- 头部 -->
    <div class="flex-shrink-0 p-6 border-b border-gray-200 dark:border-gray-700">
      <div class="flex items-center justify-between mb-1">
        <h2 class="text-lg font-bold text-gray-900 dark:text-white">检索结果</h2>
        <span v-if="searchResults.length > 0" class="text-xs text-gray-400 dark:text-gray-500">
          最匹配片段
        </span>
      </div>
      <p class="text-xs text-gray-500 dark:text-gray-400">展示与本次提问最匹配的知识库片段</p>
    </div>

    <!-- 内容区 -->
    <div class="flex-1 overflow-y-auto p-6 space-y-4">

      <!-- 加载中 -->
      <div v-if="searchLoading" class="flex flex-col items-center justify-center py-16 gap-3">
        <icon-eos-icons:three-dots-loading class="text-4xl text-blue-500" />
        <span class="text-sm text-gray-500 dark:text-gray-400">检索知识库中…</span>
      </div>

      <!-- 空状态 -->
      <div v-else-if="searchResults.length === 0" class="flex flex-col items-center justify-center py-16 gap-3 text-center">
        <icon-carbon:search class="text-4xl text-gray-300 dark:text-gray-600" />
        <p class="text-sm text-gray-400 dark:text-gray-500">发送消息后，知识库命中的相关文档将在此展示</p>
      </div>

      <!-- 检索结果列表 -->
      <template v-else>
        <div
          v-for="item in searchResults"
          :key="item.referenceNumber"
          class="result-card rounded-xl border border-gray-100 dark:border-gray-700 bg-white dark:bg-gray-800/50 p-4 space-y-3 hover:shadow-md transition-shadow cursor-pointer"
          @click="openDetail(item)"
        >
          <!-- 标题行 -->
          <div class="flex items-start justify-between gap-2">
            <div class="flex items-center gap-2 min-w-0">
              <span class="flex-shrink-0 w-5 h-5 rounded-full bg-blue-100 dark:bg-blue-900/40 text-blue-600 dark:text-blue-400 text-xs font-bold flex items-center justify-center">
                {{ item.referenceNumber }}
              </span>
              <icon-carbon:document class="flex-shrink-0 text-blue-400 dark:text-blue-500 text-base" />
              <span class="text-sm font-medium text-gray-800 dark:text-gray-200 truncate" :title="item.fileName">
                {{ item.fileName }}
              </span>
            </div>
            <!-- 相关性分数 -->
            <span class="flex-shrink-0 text-xs font-semibold px-2 py-0.5 rounded-full" :style="{ color: scoreColor(item.score), background: `${scoreColor(item.score)}18` }">
              {{ scoreToPercent(item.score) }}%
            </span>
          </div>

          <!-- 进度条（相关性可视化） -->
          <NProgress
            type="line"
            :percentage="scoreToPercent(item.score)"
            :show-indicator="false"
            :color="scoreColor(item.score)"
            :height="4"
            :border-radius="2"
          />

          <!-- 文档片段（预览，3行截断，点击可查看完整内容） -->
          <div class="text-xs text-gray-600 dark:text-gray-400 leading-relaxed line-clamp-3">
            {{ item.snippet }}
          </div>

          <!-- 元数据 -->
          <div class="flex items-center gap-3 text-xs text-gray-400 dark:text-gray-500">
            <span class="flex items-center gap-1">
              <icon-carbon:cube class="text-xs" />
              Chunk #{{ item.chunkId }}
            </span>
            <span class="flex items-center gap-1 font-mono truncate" :title="item.fileMd5">
              <icon-carbon:fingerprint-recognition class="text-xs" />
              {{ item.fileMd5?.substring(0, 8) }}…
            </span>
          </div>
        </div>
      </template>
    </div>

    <!-- 片段详情弹窗 -->
    <ChunkDetailModal
      v-if="selectedItem"
      v-model:visible="detailVisible"
      :file-md5="selectedItem.fileMd5"
      :chunk-id="selectedItem.chunkId"
      :file-name="selectedItem.fileName"
      :reference-number="selectedItem.referenceNumber"
    />
  </div>
</template>

<style scoped lang="scss">
.search-results {
  .result-card {
    animation: slideIn 0.25s ease-out;
  }
}

@keyframes slideIn {
  from {
    opacity: 0;
    transform: translateY(8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
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

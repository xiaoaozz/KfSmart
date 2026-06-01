<script setup lang="ts">
import { ref } from 'vue';

defineOptions({
  name: 'SearchResults'
});

interface SearchResult {
  id: string;
  title: string;
  time: string;
  status: 'success' | 'processing';
  chunks?: number;
  topK?: number;
  totalCount?: number;
}

const results = ref<SearchResult[]>([
  {
    id: '1',
    title: '含中文档数',
    time: '耗时 186 ms',
    status: 'success',
    chunks: 23,
    topK: 50
  },
  {
    id: '2',
    title: '向量召回',
    time: '耗时 50 篇 (Top-K=50)',
    status: 'processing',
    totalCount: 50
  },
  {
    id: '3',
    title: '重排序',
    time: '耗时 142 ms',
    status: 'success'
  },
  {
    id: '4',
    title: '生成结果',
    time: '耗时 1.12 s',
    status: 'success'
  }
]);

const queries = ref([
  {
    id: '1',
    question: '如果入职不满3年假，有年假吗？',
    answer: '员工入职满3个月以上不满一年的，可享受5天年假'
  }
]);
</script>

<template>
  <div class="search-results flex flex-col h-full">
    <!-- 头部 -->
    <div class="flex-shrink-0 p-6 border-b border-gray-200 dark:border-gray-700">
      <div class="flex items-center justify-between mb-1">
        <h2 class="text-lg font-bold text-gray-900 dark:text-white">检索结果</h2>
        <NButton text circle size="small">
          <template #icon>
            <icon-carbon:close class="text-lg text-gray-500" />
          </template>
        </NButton>
      </div>
    </div>

    <!-- 内容区 -->
    <div class="flex-1 overflow-y-auto p-6 space-y-6">
      <!-- 含中文档数统计 -->
      <div class="result-section">
        <h3 class="text-sm font-medium text-gray-700 dark:text-gray-300 mb-3">含中文档数</h3>
        <div
          v-for="result in results.filter(r => r.title === '含中文档数')"
          :key="result.id"
          class="bg-blue-50 dark:bg-blue-900/20 rounded-xl p-4"
        >
          <div class="flex items-center justify-between mb-2">
            <div class="flex items-center gap-2">
              <icon-carbon:document class="text-blue-500" />
              <span class="text-2xl font-bold text-blue-600 dark:text-blue-400">{{ result.chunks }} 篇</span>
            </div>
            <span
              v-if="result.status === 'success'"
              class="flex items-center gap-1 text-xs text-green-600 dark:text-green-400"
            >
              <icon-carbon:checkmark-filled />
              <span>成功</span>
            </span>
          </div>
          <div class="text-xs text-gray-600 dark:text-gray-400">
            {{ result.time }}
          </div>
        </div>
      </div>

      <!-- 向量召回 -->
      <div class="result-section">
        <h3 class="text-sm font-medium text-gray-700 dark:text-gray-300 mb-3">向量召回</h3>
        <div
          v-for="result in results.filter(r => r.title === '向量召回')"
          :key="result.id"
          class="bg-purple-50 dark:bg-purple-900/20 rounded-xl p-4"
        >
          <div class="flex items-center justify-between mb-2">
            <span class="text-sm font-medium text-gray-900 dark:text-white">{{ result.time }}</span>
            <span
              v-if="result.status === 'processing'"
              class="flex items-center gap-1 text-xs text-yellow-600 dark:text-yellow-400"
            >
              <icon-carbon:in-progress />
              <span>处理中</span>
            </span>
          </div>
          <div class="mt-2">
            <NProgress type="line" :percentage="100" :show-indicator="false" color="#8b5cf6" />
          </div>
        </div>
      </div>

      <!-- 重排序 -->
      <div class="result-section">
        <h3 class="text-sm font-medium text-gray-700 dark:text-gray-300 mb-3">重排序</h3>
        <div
          v-for="result in results.filter(r => r.title === '重排序')"
          :key="result.id"
          class="bg-cyan-50 dark:bg-cyan-900/20 rounded-xl p-4"
        >
          <div class="flex items-center justify-between">
            <span class="text-sm font-medium text-gray-900 dark:text-white">{{ result.time }}</span>
            <span
              v-if="result.status === 'success'"
              class="flex items-center gap-1 text-xs text-green-600 dark:text-green-400"
            >
              <icon-carbon:checkmark-filled />
              <span>成功</span>
            </span>
          </div>
        </div>
      </div>

      <!-- 生成结果 -->
      <div class="result-section">
        <h3 class="text-sm font-medium text-gray-700 dark:text-gray-300 mb-3">生成结果</h3>
        <div
          v-for="result in results.filter(r => r.title === '生成结果')"
          :key="result.id"
          class="bg-green-50 dark:bg-green-900/20 rounded-xl p-4"
        >
          <div class="flex items-center justify-between mb-3">
            <span class="text-sm font-medium text-gray-900 dark:text-white">{{ result.time }}</span>
            <span
              v-if="result.status === 'success'"
              class="flex items-center gap-1 text-xs text-green-600 dark:text-green-400"
            >
              <icon-carbon:checkmark-filled />
              <span>成功</span>
            </span>
          </div>
          <div class="text-xs text-gray-600 dark:text-gray-400">
            模型：KnowFlow-Chat 3.5<br>
            生成 Tokens：512
          </div>
        </div>
      </div>

      <!-- 查询改写 -->
      <div class="result-section">
        <h3 class="text-sm font-medium text-gray-700 dark:text-gray-300 mb-3">查询改写</h3>
        <div
          v-for="query in queries"
          :key="query.id"
          class="bg-gray-50 dark:bg-gray-800 rounded-xl p-4 space-y-3"
        >
          <div class="text-xs text-gray-500 dark:text-gray-400">
            原问题：{{ query.question }}
          </div>
          <div class="text-xs text-gray-600 dark:text-gray-300">
            改写：{{ query.answer }}
          </div>
        </div>
      </div>

      <!-- 查看检索详情按钮 -->
      <div class="pt-4">
        <NButton text block class="text-blue-600 hover:bg-blue-50 dark:hover:bg-blue-900/20 rounded-xl py-3">
          <template #icon>
            <icon-carbon:arrow-right class="text-lg" />
          </template>
          查看检索详情
        </NButton>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.search-results {
  .result-section {
    animation: slideIn 0.3s ease-out;
  }
}

@keyframes slideIn {
  from {
    opacity: 0;
    transform: translateY(10px);
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

:deep(.dark) {
  ::-webkit-scrollbar-thumb {
    background: #4b5563;

    &:hover {
      background: #6b7280;
    }
  }
}
</style>

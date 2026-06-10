<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { fetchGetKnowledgeBaseStats } from '@/service/api/knowledge-base';

defineOptions({
  name: 'KnowledgeStats'
});

interface KnowledgeBaseItem {
  name: string;
  kbId: string;
  docCount: number;
  totalSize: number;
  updateTime: string;
  rawUpdatedAt: string;
}

const knowledgeBases = ref<KnowledgeBaseItem[]>([]);
const overview = ref<Api.KnowledgeBase.KnowledgeBaseStats | null>(null);
const loading = ref(false);
const sortMode = ref<'docs' | 'size' | 'updated'>('docs');

const totalDocs = computed(
  () => overview.value?.documentCount ?? knowledgeBases.value.reduce((sum, kb) => sum + kb.docCount, 0)
);
const totalSize = computed(() =>
  formatFileSize(overview.value?.totalSize ?? knowledgeBases.value.reduce((sum, kb) => sum + kb.totalSize, 0))
);
const knowledgeBaseCount = computed(() => overview.value?.knowledgeBaseCount || 0);
const chunkCount = computed(() => overview.value?.chunkCount || 0);
const maxDocs = computed(() => Math.max(...knowledgeBases.value.map(kb => kb.docCount), 1));
const maxSize = computed(() => Math.max(...knowledgeBases.value.map(kb => kb.totalSize), 1));

const chartColors = ['#2563eb', '#0891b2', '#16a34a', '#d97706', '#7c3aed', '#db2777', '#4f46e5', '#0d9488'];

const sortedKnowledgeBases = computed(() => {
  const list = [...knowledgeBases.value];
  if (sortMode.value === 'updated') {
    return list.sort((a, b) => getTimeValue(b.rawUpdatedAt) - getTimeValue(a.rawUpdatedAt));
  }
  if (sortMode.value === 'size') {
    return list.sort((a, b) => b.totalSize - a.totalSize);
  }
  return list.sort((a, b) => b.docCount - a.docCount);
});

const latestKnowledgeBases = computed(() =>
  [...knowledgeBases.value].sort((a, b) => getTimeValue(b.rawUpdatedAt) - getTimeValue(a.rawUpdatedAt)).slice(0, 4)
);

function getTimeValue(dateStr?: string): number {
  if (!dateStr) return 0;
  const value = new Date(dateStr).getTime();
  return Number.isNaN(value) ? 0 : value;
}

function formatFileSize(bytes: number): string {
  if (!bytes || bytes === 0) return '0 B';
  const units = ['B', 'KB', 'MB', 'GB', 'TB'];
  const k = 1024;
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return `${Number.parseFloat((bytes / k ** i).toFixed(1))} ${units[i]}`;
}

function getRankPercent(kb: KnowledgeBaseItem): number {
  if (sortMode.value === 'size') {
    return Math.max(6, Math.round((kb.totalSize / maxSize.value) * 100));
  }
  return Math.max(6, Math.round((kb.docCount / maxDocs.value) * 100));
}

function getRankValue(kb: KnowledgeBaseItem): string {
  if (sortMode.value === 'size') return formatFileSize(kb.totalSize);
  if (sortMode.value === 'updated') return kb.updateTime;
  return `${kb.docCount} 篇`;
}

function formatTime(dateStr: string): string {
  if (!dateStr) return '--';
  try {
    const d = new Date(dateStr);
    const now = new Date();
    const diffMs = now.getTime() - d.getTime();
    const diffHrs = Math.floor(diffMs / (1000 * 60 * 60));
    if (diffHrs < 1) return '刚刚';
    if (diffHrs < 24) return `${diffHrs}小时前`;
    const diffDays = Math.floor(diffHrs / 24);
    if (diffDays < 30) return `${diffDays}天前`;
    return `${d.getMonth() + 1}-${d.getDate()}`;
  } catch {
    return dateStr;
  }
}

/** 从后端独立的知识库统计API获取数据 */
async function fetchKnowledgeStats() {
  loading.value = true;
  try {
    const { error, data } = await fetchGetKnowledgeBaseStats();
    if (!error && data) {
      overview.value = data;
      knowledgeBases.value = data.knowledgeBases.map((kb: Api.KnowledgeBase.KnowledgeBaseInfo) => ({
        name: kb.name,
        kbId: kb.kbId,
        docCount: kb.fileCount,
        totalSize: kb.totalSize,
        updateTime: formatTime(kb.updatedAt),
        rawUpdatedAt: kb.updatedAt
      }));
    } else {
      overview.value = null;
      knowledgeBases.value = [];
    }
  } catch (e) {
    console.error('[KnowledgeStats] 获取知识库统计失败:', e);
  } finally {
    loading.value = false;
  }
}

onMounted(() => {
  fetchKnowledgeStats();
});
</script>

<template>
  <div class="knowledge-stats h-full">
    <NCard class="h-full">
      <template #header>
        <div class="flex items-center justify-between gap-3">
          <div>
            <h2 class="text-lg text-gray-900 font-semibold dark:text-white">知识库统计</h2>
            <p class="mt-1 text-xs text-gray-500 dark:text-gray-400">容量、文档数与更新动态</p>
          </div>
          <NButton text @click="fetchKnowledgeStats">
            <template #icon>
              <icon-carbon:renew class="text-lg" />
            </template>
          </NButton>
        </div>
      </template>

      <NSpin :show="loading">
        <div
          v-if="knowledgeBases.length === 0"
          class="min-h-[360px] flex flex-col items-center justify-center text-gray-400"
        >
          <icon-carbon:document-blank class="mb-3 text-4xl" />
          <p class="text-sm">暂无知识库数据</p>
          <p class="mt-1 text-xs">创建知识库后将在此显示统计</p>
        </div>

        <div v-else class="min-h-[360px] flex flex-col gap-4">
          <div class="grid grid-cols-2 gap-3 lg:grid-cols-4">
            <div class="summary-card">
              <div class="text-xs text-gray-500 dark:text-gray-400">知识库</div>
              <div class="mt-1 text-lg text-gray-900 font-bold dark:text-white">{{ knowledgeBaseCount }}</div>
            </div>
            <div class="summary-card">
              <div class="text-xs text-gray-500 dark:text-gray-400">文档</div>
              <div class="mt-1 text-lg text-gray-900 font-bold dark:text-white">{{ totalDocs }}</div>
            </div>
            <div class="summary-card">
              <div class="text-xs text-gray-500 dark:text-gray-400">分块</div>
              <div class="mt-1 text-lg text-gray-900 font-bold dark:text-white">{{ chunkCount }}</div>
            </div>
            <div class="summary-card">
              <div class="text-xs text-gray-500 dark:text-gray-400">容量</div>
              <div class="mt-1 text-lg text-gray-900 font-bold dark:text-white">{{ totalSize }}</div>
            </div>
          </div>

          <div class="flex flex-wrap items-center justify-between gap-3">
            <div>
              <div class="text-sm text-gray-900 font-semibold dark:text-white">知识库排行</div>
              <div class="text-xs text-gray-500 dark:text-gray-400">切换维度查看资源占用与活跃度</div>
            </div>
            <NRadioGroup v-model:value="sortMode" size="small">
              <NRadioButton value="docs">文档</NRadioButton>
              <NRadioButton value="size">容量</NRadioButton>
              <NRadioButton value="updated">更新</NRadioButton>
            </NRadioGroup>
          </div>

          <div class="knowledge-list max-h-[260px] min-h-0 overflow-y-auto pr-1 space-y-2">
            <div
              v-for="(kb, index) in sortedKnowledgeBases"
              :key="kb.kbId"
              class="border border-gray-100 rounded-lg bg-gray-50 p-3 transition-colors dark:border-gray-700 dark:bg-gray-800 hover:bg-gray-100 dark:hover:bg-gray-700"
            >
              <div class="flex items-center justify-between gap-4">
                <div class="min-w-0 flex flex-1 items-center gap-3">
                  <div
                    class="h-8 w-8 flex flex-shrink-0 items-center justify-center rounded-lg bg-white text-xs text-gray-700 font-semibold dark:bg-gray-900 dark:text-gray-200"
                  >
                    {{ index + 1 }}
                  </div>
                  <div class="min-w-0 flex-1">
                    <div class="truncate text-sm text-gray-900 font-medium dark:text-white">{{ kb.name }}</div>
                    <div class="mt-1 flex flex-wrap items-center gap-2 text-xs text-gray-500 dark:text-gray-400">
                      <span>{{ kb.docCount }} 篇</span>
                      <span>·</span>
                      <span>{{ formatFileSize(kb.totalSize) }}</span>
                      <span>·</span>
                      <span>更新于 {{ kb.updateTime }}</span>
                    </div>
                  </div>
                </div>
                <div class="w-24 flex-shrink-0 text-right text-sm text-gray-900 font-semibold dark:text-white">
                  {{ getRankValue(kb) }}
                </div>
              </div>
              <div class="mt-3 h-2 overflow-hidden rounded-full bg-gray-200 dark:bg-gray-700">
                <div
                  class="h-full rounded-full transition-all"
                  :style="{
                    width: `${getRankPercent(kb)}%`,
                    backgroundColor: chartColors[index % chartColors.length]
                  }"
                />
              </div>
            </div>
          </div>

          <div class="border-t border-gray-100 pt-3 dark:border-gray-700">
            <div class="mb-2 text-sm text-gray-900 font-semibold dark:text-white">最近更新</div>
            <div class="grid grid-cols-1 gap-2 sm:grid-cols-2">
              <div
                v-for="kb in latestKnowledgeBases"
                :key="`latest-${kb.kbId}`"
                class="flex items-center justify-between rounded-lg bg-gray-50 px-3 py-2 text-sm dark:bg-gray-800"
              >
                <span class="min-w-0 truncate text-gray-800 dark:text-gray-100">{{ kb.name }}</span>
                <span class="ml-3 flex-shrink-0 text-xs text-gray-500 dark:text-gray-400">{{ kb.updateTime }}</span>
              </div>
            </div>
          </div>
        </div>
      </NSpin>
    </NCard>
  </div>
</template>

<style scoped lang="scss">
.knowledge-stats {
  :deep(.n-card) {
    height: 100%;
  }

  :deep(.n-card__content) {
    height: calc(100% - 73px);
  }

  .summary-card {
    border-radius: 10px;
    border: 1px solid rgb(243 244 246);
    background: rgb(249 250 251);
    padding: 12px;
  }

  :global(.dark) .summary-card {
    border-color: rgb(55 65 81);
    background: rgb(31 41 55);
  }
}
</style>

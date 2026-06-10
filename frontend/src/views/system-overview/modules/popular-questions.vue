<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { fetchGetSystemStats } from '@/service/api/system';

defineOptions({
  name: 'PopularQuestions'
});

const loading = ref(false);
const systemStats = ref<Api.System.Stats | null>(null);

const popularQuestions = computed(() => systemStats.value?.popularQuestions || []);
const totalCount = computed(() => popularQuestions.value.reduce((sum, item) => sum + Number(item.count || 0), 0));
const topQuestion = computed(() => popularQuestions.value[0]?.question || '--');
const displayQuestions = computed(() => popularQuestions.value.slice(0, 5));

function getRankClass(rank: number) {
  if (rank === 1) return 'rank-first';
  if (rank === 2) return 'rank-second';
  if (rank === 3) return 'rank-third';
  return 'rank-normal';
}

async function fetchPopularQuestions() {
  loading.value = true;
  try {
    const { error, data } = await fetchGetSystemStats();
    if (!error && data) {
      systemStats.value = data;
    }
  } catch (e) {
    console.error('[PopularQuestions] 获取热门问题失败:', e);
  } finally {
    loading.value = false;
  }
}

onMounted(() => {
  fetchPopularQuestions();
});
</script>

<template>
  <div class="popular-questions h-full">
    <NCard class="h-full">
      <template #header>
        <div class="flex items-center justify-between">
          <div>
            <h2 class="text-lg text-gray-900 font-semibold dark:text-white">热门问题</h2>
            <p class="mt-1 text-xs text-gray-500 dark:text-gray-400">按今日提问频次排序</p>
          </div>
          <NButton text @click="fetchPopularQuestions">
            <template #icon>
              <icon-carbon:renew class="text-lg" />
            </template>
            刷新
          </NButton>
        </div>
      </template>

      <NSpin :show="loading">
        <div class="flex flex-col gap-4">
          <div class="grid grid-cols-2 gap-3">
            <div class="summary-card">
              <div class="text-xs text-gray-500 dark:text-gray-400">Top5 提问</div>
              <div class="mt-1 text-lg text-gray-900 font-bold dark:text-white">{{ totalCount }}</div>
            </div>
            <div class="summary-card min-w-0">
              <div class="text-xs text-gray-500 dark:text-gray-400">最高频问题</div>
              <div class="mt-1 truncate text-sm text-gray-900 font-semibold dark:text-white">{{ topQuestion }}</div>
            </div>
          </div>

          <div
            v-if="popularQuestions.length === 0"
            class="min-h-0 flex flex-col flex-1 items-center justify-center text-gray-400"
          >
            <icon-carbon:chat-off class="mb-3 text-4xl" />
            <p class="text-sm">暂无热门问题数据</p>
          </div>

          <div v-else class="question-list space-y-2">
            <div
              v-for="item in displayQuestions"
              :key="item.rank"
              class="question-item flex items-center gap-3 rounded-lg bg-gray-50 px-3 py-2.5 transition-colors dark:bg-gray-800 hover:bg-gray-100 dark:hover:bg-gray-700"
            >
              <div
                class="rank h-7 w-7 flex flex-shrink-0 items-center justify-center rounded-lg text-sm font-semibold"
                :class="[getRankClass(item.rank)]"
              >
                {{ item.rank }}
              </div>
              <div class="min-w-0 flex flex-1 items-center gap-3">
                <div class="truncate text-sm text-gray-900 font-medium dark:text-white">
                  {{ item.question }}
                </div>
                <div class="ml-auto flex flex-shrink-0 items-center gap-1 text-xs text-gray-500 dark:text-gray-400">
                  <icon-carbon:chat class="text-sm" />
                  {{ item.count }} 次提问
                </div>
              </div>
            </div>
          </div>
        </div>
      </NSpin>
    </NCard>
  </div>
</template>

<style scoped lang="scss">
.popular-questions {
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

  .rank-first {
    background: rgba(245, 158, 11, 0.14);
    color: #d97706;
  }

  .rank-second {
    background: rgba(100, 116, 139, 0.14);
    color: #475569;
  }

  .rank-third {
    background: rgba(249, 115, 22, 0.14);
    color: #ea580c;
  }

  .rank-normal {
    background: rgba(37, 99, 235, 0.1);
    color: #2563eb;
  }

  :global(.dark) .summary-card {
    border-color: rgb(55 65 81);
    background: rgb(31 41 55);
  }
}
</style>

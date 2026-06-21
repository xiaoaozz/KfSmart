<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { DEFAULT_PAGE_SIZE } from '@/constants/common';
import { fetchGetRecentActivities } from '@/service/api/system';
import ListPagination from '@/components/common/list-pagination.vue';

defineOptions({
  name: 'RecentActivities'
});

type ActivityType = 'all' | 'knowledge' | 'document' | 'user';

const activities = ref<Api.System.RecentActivity[]>([]);
const loading = ref(false);
const activeType = ref<ActivityType>('all');
const currentPage = ref(1);
const pageSize = ref(DEFAULT_PAGE_SIZE);
const activityStats = ref<Api.System.RecentActivityStats>({
  todayActivities: 0,
  weekActivities: 0,
  knowledgeUpdates: 0,
  documentUpdates: 0
});

const typeOptions = [
  { label: '全部', value: 'all' },
  { label: '知识库', value: 'knowledge' },
  { label: '文档', value: 'document' },
  { label: '用户', value: 'user' }
];

const filteredActivities = computed(() =>
  activeType.value === 'all' ? activities.value : activities.value.filter(item => item.type === activeType.value)
);

const visibleActivities = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value;
  return filteredActivities.value.slice(start, start + pageSize.value);
});

function formatTime(dateStr: string): string {
  if (!dateStr) return '--';
  try {
    const d = new Date(dateStr);
    const now = new Date();
    const diffMs = now.getTime() - d.getTime();
    const diffMin = Math.floor(diffMs / (1000 * 60));
    if (diffMin < 1) return '刚刚';
    if (diffMin < 60) return `${diffMin}分钟前`;
    const diffHrs = Math.floor(diffMin / 60);
    if (diffHrs < 24) return `${diffHrs}小时前`;
    const diffDays = Math.floor(diffHrs / 24);
    if (diffDays < 30) return `${diffDays}天前`;
    return `${d.getMonth() + 1}-${d.getDate()}`;
  } catch {
    return dateStr;
  }
}

async function fetchActivities() {
  loading.value = true;
  try {
    const { error, data } = await fetchGetRecentActivities();
    if (!error && data) {
      activities.value = data.activities || [];
      activityStats.value = data.stats || activityStats.value;
    }
  } catch {
  } finally {
    loading.value = false;
  }
}

onMounted(() => {
  fetchActivities();
});

watch(activeType, () => {
  currentPage.value = 1;
});

watch([filteredActivities, pageSize], ([list]) => {
  const maxPage = Math.max(1, Math.ceil(list.length / pageSize.value));
  if (currentPage.value > maxPage) {
    currentPage.value = maxPage;
  }
});

function handlePageSizeChange(size: number) {
  pageSize.value = size;
  currentPage.value = 1;
}

const getColorClasses = (color: string) => {
  const colorMap: Record<string, { icon: string; bg: string }> = {
    blue: { icon: 'text-blue-500', bg: 'bg-blue-50 dark:bg-blue-900/20' },
    green: { icon: 'text-green-500', bg: 'bg-green-50 dark:bg-green-900/20' },
    purple: { icon: 'text-purple-500', bg: 'bg-purple-50 dark:bg-purple-900/20' },
    cyan: { icon: 'text-cyan-500', bg: 'bg-cyan-50 dark:bg-cyan-900/20' },
    orange: { icon: 'text-orange-500', bg: 'bg-orange-50 dark:bg-orange-900/20' },
    pink: { icon: 'text-pink-500', bg: 'bg-pink-50 dark:bg-pink-900/20' }
  };
  return colorMap[color] || colorMap.blue;
};
</script>

<template>
  <div class="recent-activities">
    <NCard>
      <template #header>
        <div class="flex flex-wrap items-center justify-between gap-3">
          <div>
            <h2 class="text-lg text-gray-900 font-semibold dark:text-white">最近活动</h2>
            <p class="mt-1 text-xs text-gray-500 dark:text-gray-400">知识库、文档与用户活动实时追踪</p>
          </div>
          <div class="flex items-center gap-3">
            <NRadioGroup v-model:value="activeType" size="small">
              <NRadioButton v-for="option in typeOptions" :key="option.value" :value="option.value">
                {{ option.label }}
              </NRadioButton>
            </NRadioGroup>
            <NButton text @click="fetchActivities">
              <template #icon>
                <icon-carbon:renew class="text-lg" />
              </template>
              刷新
            </NButton>
          </div>
        </div>
      </template>

      <NSpin :show="loading">
        <div class="activity-panel h-[410px] flex flex-col">
          <div
            v-if="visibleActivities.length === 0"
            class="min-h-0 flex flex-col flex-1 items-center justify-center text-gray-400"
          >
            <icon-carbon:document-blank class="mb-3 text-4xl" />
            <p class="text-sm">暂无活动记录</p>
            <p class="mt-1 text-xs">创建知识库或上传文档后将在此显示</p>
          </div>

          <div v-else class="activities-list min-h-0 flex-1 overflow-hidden space-y-1">
            <div
              v-for="activity in visibleActivities"
              :key="activity.id"
              class="activity-item flex cursor-pointer items-start gap-4 rounded-lg p-3 transition-colors hover:bg-gray-50 dark:hover:bg-gray-800"
            >
              <div
                class="h-10 w-10 flex flex-shrink-0 items-center justify-center rounded-lg"
                :class="[getColorClasses(activity.color).bg]"
              >
                <icon-carbon:document-add
                  v-if="activity.icon === 'document-add'"
                  class="text-lg"
                  :class="[getColorClasses(activity.color).icon]"
                />
                <icon-carbon:document-tasks
                  v-else-if="activity.icon === 'document-tasks'"
                  class="text-lg"
                  :class="[getColorClasses(activity.color).icon]"
                />
                <icon-carbon:user-follow
                  v-else-if="activity.icon === 'user-follow'"
                  class="text-lg"
                  :class="[getColorClasses(activity.color).icon]"
                />
                <icon-carbon:data-base
                  v-else-if="activity.icon === 'data-base'"
                  class="text-lg"
                  :class="[getColorClasses(activity.color).icon]"
                />
                <icon-carbon:settings-adjust
                  v-else-if="activity.icon === 'settings-adjust'"
                  class="text-lg"
                  :class="[getColorClasses(activity.color).icon]"
                />
              </div>

              <div class="min-w-0 flex-1">
                <div class="mb-1 flex items-start justify-between gap-2">
                  <div class="min-w-0 flex items-center gap-2">
                    <h4 class="truncate text-sm text-gray-900 font-medium dark:text-white">
                      {{ activity.title }}
                    </h4>
                    <NTag size="small" :bordered="false">
                      {{ activity.type === 'knowledge' ? '知识库' : activity.type === 'document' ? '文档' : '用户' }}
                    </NTag>
                  </div>
                  <span class="flex-shrink-0 text-xs text-gray-500 dark:text-gray-400">
                    {{ formatTime(activity.occurredAt) }}
                  </span>
                </div>
                <p class="line-clamp-1 text-sm text-gray-600 dark:text-gray-400">
                  {{ activity.description }}
                </p>
              </div>
            </div>
          </div>

          <ListPagination
            v-model:page="currentPage"
            :item-count="filteredActivities.length"
            :page-size="pageSize"
            :disabled="filteredActivities.length === 0"
            size="small"
            class="mt-3 px-0 pt-3"
            @update:page-size="handlePageSizeChange"
          />
        </div>
      </NSpin>

      <div class="mt-4 border-t border-gray-200 pt-4 dark:border-gray-700">
        <div class="grid grid-cols-2 gap-4 md:grid-cols-4">
          <div class="text-center">
            <div class="text-2xl text-gray-900 font-bold dark:text-white">{{ activityStats.todayActivities }}</div>
            <div class="mt-1 text-xs text-gray-500 dark:text-gray-400">今日活动</div>
          </div>
          <div class="text-center">
            <div class="text-2xl text-gray-900 font-bold dark:text-white">{{ activityStats.weekActivities }}</div>
            <div class="mt-1 text-xs text-gray-500 dark:text-gray-400">本周活动</div>
          </div>
          <div class="text-center">
            <div class="text-2xl text-gray-900 font-bold dark:text-white">{{ activityStats.knowledgeUpdates }}</div>
            <div class="mt-1 text-xs text-gray-500 dark:text-gray-400">知识库更新</div>
          </div>
          <div class="text-center">
            <div class="text-2xl text-gray-900 font-bold dark:text-white">{{ activityStats.documentUpdates }}</div>
            <div class="mt-1 text-xs text-gray-500 dark:text-gray-400">文档更新</div>
          </div>
        </div>
      </div>
    </NCard>
  </div>
</template>

<style scoped lang="scss">
.recent-activities {
  .activity-item {
    transition: all 0.2s ease;

    &:hover {
      transform: translateX(4px);
    }
  }
}
</style>

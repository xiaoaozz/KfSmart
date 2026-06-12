<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { DEFAULT_PAGE_SIZE, PAGINATION_PAGE_SIZE_OPTIONS } from '@/constants/common';
import { fetchGetKnowledgeBases } from '@/service/api/knowledge-base';
import { request } from '@/service/request';

defineOptions({
  name: 'RecentActivities'
});

type ActivityType = 'all' | 'knowledge' | 'document' | 'user';

interface Activity {
  id: string;
  type: Exclude<ActivityType, 'all'>;
  icon: string;
  title: string;
  description: string;
  time: string;
  timestamp: number;
  color: string;
}

const activities = ref<Activity[]>([]);
const loading = ref(false);
const activeType = ref<ActivityType>('all');
const currentPage = ref(1);
const pageSize = ref(DEFAULT_PAGE_SIZE);
const pageSizeOptions = PAGINATION_PAGE_SIZE_OPTIONS;
const activityStats = ref({
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

function getTimeValue(dateStr?: string): number {
  if (!dateStr) return 0;
  const value = new Date(dateStr).getTime();
  return Number.isNaN(value) ? 0 : value;
}

function isSameTime(left?: string, right?: string): boolean {
  const leftTime = getTimeValue(left);
  const rightTime = getTimeValue(right);
  if (!leftTime || !rightTime) return false;
  return Math.abs(leftTime - rightTime) < 60 * 1000;
}

function isSameDay(timestamp: number, date: Date): boolean {
  if (!timestamp) return false;
  return new Date(timestamp).toDateString() === date.toDateString();
}

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

function pushActivity(items: Activity[], activity: Activity) {
  if (!activity.timestamp) return;
  items.push(activity);
}

/** 聚合知识库创建/更新、文档上传/更新和用户加入记录 */
async function fetchActivities() {
  loading.value = true;
  try {
    const [filesRes, kbRes] = await Promise.all([
      request<Api.Common.PaginatingQueryRecord<any>>({ url: '/documents/uploads', params: { sort: 'updatedAt', size: 100 } }),
      fetchGetKnowledgeBases({ size: 100 })
    ]);

    const items: Activity[] = [];
    const files = !filesRes.error && filesRes.data ? filesRes.data.records || filesRes.data.content || filesRes.data.data || [] : [];
    const knowledgeBases = !kbRes.error && kbRes.data ? kbRes.data.records || kbRes.data.content || kbRes.data.data || [] : [];

    knowledgeBases.forEach((kb: Api.KnowledgeBase.KnowledgeBaseInfo) => {
      pushActivity(items, {
        id: `kb-create-${kb.kbId}`,
        type: 'knowledge',
        icon: 'data-base',
        title: '创建知识库',
        description: `知识库「${kb.name || '未命名'}」已创建`,
        time: formatTime(kb.createdAt),
        timestamp: getTimeValue(kb.createdAt),
        color: 'green'
      });

      if (!isSameTime(kb.createdAt, kb.updatedAt)) {
        pushActivity(items, {
          id: `kb-update-${kb.kbId}`,
          type: 'knowledge',
          icon: 'settings-adjust',
          title: '更新知识库',
          description: `知识库「${kb.name || '未命名'}」信息已更新`,
          time: formatTime(kb.updatedAt),
          timestamp: getTimeValue(kb.updatedAt),
          color: 'cyan'
        });
      }
    });

    files.forEach((file: any) => {
      const targetName = file.kbName || file.orgTagName || file.kbId || '未归类';
      pushActivity(items, {
        id: `doc-create-${file.fileMd5 || file.fileName}`,
        type: 'document',
        icon: 'document-add',
        title: '上传文档',
        description: `文件「${file.fileName || '未知'}」上传到「${targetName}」`,
        time: formatTime(file.createdAt),
        timestamp: getTimeValue(file.createdAt),
        color: 'blue'
      });

      if (file.mergedAt && !isSameTime(file.createdAt, file.mergedAt)) {
        pushActivity(items, {
          id: `doc-update-${file.fileMd5 || file.fileName}`,
          type: 'document',
          icon: 'document-tasks',
          title: '更新文档',
          description: `文件「${file.fileName || '未知'}」已完成处理并更新索引`,
          time: formatTime(file.mergedAt),
          timestamp: getTimeValue(file.mergedAt),
          color: 'purple'
        });
      }
    });

    try {
      const { error: userErr, data: users } = await request<any[]>({ url: '/admin/users' });
      if (!userErr && users && users.length > 0) {
        users.slice(-5).forEach((user: any) => {
          pushActivity(items, {
            id: `user-${user.id || user.username}`,
            type: 'user',
            icon: 'user-follow',
            title: '用户加入',
            description: `用户「${user.username || '未知'}」已加入系统`,
            time: formatTime(user.createdAt),
            timestamp: getTimeValue(user.createdAt),
            color: 'orange'
          });
        });
      }
    } catch {
      // 非管理员可能无法访问用户列表，不影响知识库和文档活动。
    }

    activities.value = items.sort((a, b) => b.timestamp - a.timestamp).slice(0, 40);

    const now = new Date();
    const weekAgo = now.getTime() - 7 * 24 * 60 * 60 * 1000;
    activityStats.value.todayActivities = activities.value.filter(item => isSameDay(item.timestamp, now)).length;
    activityStats.value.weekActivities = activities.value.filter(item => item.timestamp >= weekAgo).length;
    activityStats.value.knowledgeUpdates = activities.value.filter(
      item => item.type === 'knowledge' && item.title.includes('更新')
    ).length;
    activityStats.value.documentUpdates = activities.value.filter(
      item => item.type === 'document' && item.title.includes('更新')
    ).length;
  } catch (e) {
    console.error('[RecentActivities] 获取活动数据失败:', e);
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
                    {{ activity.time }}
                  </span>
                </div>
                <p class="line-clamp-1 text-sm text-gray-600 dark:text-gray-400">
                  {{ activity.description }}
                </p>
              </div>
            </div>
          </div>

          <div class="mt-3 h-9 flex justify-end border-t border-gray-100 pt-3 dark:border-gray-700">
            <NPagination
              v-model:page="currentPage"
              :item-count="filteredActivities.length"
              :page-size="pageSize"
              :page-sizes="pageSizeOptions"
              :disabled="filteredActivities.length === 0"
              size="small"
              show-size-picker
              @update:page-size="handlePageSizeChange"
            />
          </div>
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

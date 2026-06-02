<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { request } from '@/service/request';
import { fetchGetSystemStats } from '@/service/api/system';

defineOptions({
  name: 'RecentActivities'
});

interface Activity {
  id: number;
  type: string;
  icon: string;
  title: string;
  description: string;
  time: string;
  color: string;
}

const activities = ref<Activity[]>([]);
const loading = ref(false);
const activityStats = ref({
  todayActivities: 0,
  weekActivities: 0,
  activeUsers: 0,
  systemEvents: 0
});

const activityColors = ['blue', 'green', 'purple', 'cyan', 'orange', 'pink'];

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

/** 从后端获取最新文件上传记录作为活动 */
async function fetchActivities() {
  loading.value = true;
  try {
    // 获取文件上传列表作为活动记录
    const { error: filesErr, data: files } = await request<any[]>({ url: '/documents/uploads' });
    
    const items: Activity[] = [];
    let colorIndex = 0;
    
    if (!filesErr && files && files.length > 0) {
      // 取最近 6 条上传作为活动
      const sortedFiles = [...files].sort((a, b) => {
        const tA = a.createdAt ? new Date(a.createdAt).getTime() : 0;
        const tB = b.createdAt ? new Date(b.createdAt).getTime() : 0;
        return tB - tA;
      });

      sortedFiles.slice(0, 6).forEach((file, idx) => {
        const tagName = file.orgTagName || '未知组织';
        items.push({
          id: 200 + idx,
          type: 'document',
          icon: 'document-add',
          title: '上传文档',
          description: `文件「${file.fileName || '未知'}」上传到「${tagName}」`,
          time: formatTime(file.createdAt),
          color: activityColors[colorIndex % activityColors.length]
        });
        colorIndex++;
      });
    }

    // 如果文件不足 6 条，补充用户活动
    if (items.length < 6) {
      try {
        const { error: userErr, data: users } = await request<any[]>({ url: '/admin/users' });
        if (!userErr && users && users.length > 0) {
          const recentUsers = users.slice(-3);
          recentUsers.forEach((user: any, idx: number) => {
            items.push({
              id: 300 + idx,
              type: 'user',
              icon: 'user-follow',
              title: '用户注册',
              description: `用户「${user.username || '未知'}」已加入系统`,
              time: formatTime(user.createdAt),
              color: activityColors[colorIndex % activityColors.length]
            });
            colorIndex++;
          });
        }
      } catch (e) {
        // 非管理员可能无法访问
      }
    }

    activities.value = items.sort((a, b) => a.id - b.id).slice(0, 6);
    
    // 计算活动统计
    const now = new Date();
    const weekAgo = new Date(now.getTime() - 7 * 24 * 60 * 60 * 1000);
    
    if (files && files.length > 0) {
      activityStats.value.todayActivities = files.filter((f: any) => {
        if (!f.createdAt) return false;
        return new Date(f.createdAt).toDateString() === now.toDateString();
      }).length;
      
      activityStats.value.weekActivities = files.filter((f: any) => {
        if (!f.createdAt) return false;
        return new Date(f.createdAt) >= weekAgo;
      }).length;
    }

    // 获取系统统计数据
    try {
      const { error: sErr, data: sData } = await fetchGetSystemStats();
      if (!sErr && sData) {
        activityStats.value.activeUsers = sData.totalUsers || 0;
        activityStats.value.systemEvents = sData.totalFiles || 0;
      }
    } catch { /* ignore */ }
    
  } catch (e) {
    console.error('[RecentActivities] 获取活动数据失败:', e);
  } finally {
    loading.value = false;
  }
}

onMounted(() => {
  fetchActivities();
});

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
        <div class="flex items-center justify-between">
          <div>
            <h2 class="text-lg font-semibold text-gray-900 dark:text-white">最近活动</h2>
            <p class="text-xs text-gray-500 dark:text-gray-400 mt-1">文档上传与用户活动实时追踪</p>
          </div>
          <NButton text @click="fetchActivities">
            <template #icon>
              <icon-carbon:renew class="text-lg" />
            </template>
            刷新
          </NButton>
        </div>
      </template>

      <NSpin :show="loading">
        <div v-if="activities.length === 0" class="flex flex-col items-center justify-center py-12 text-gray-400">
          <icon-carbon:document-blank class="text-4xl mb-3" />
          <p class="text-sm">暂无活动记录</p>
          <p class="text-xs mt-1">上传文档后将在此显示</p>
        </div>

        <div v-else class="activities-list space-y-1">
          <div
            v-for="activity in activities"
            :key="activity.id"
            class="activity-item flex items-start gap-4 p-3 rounded-lg hover:bg-gray-50 dark:hover:bg-gray-800 transition-colors cursor-pointer"
          >
            <div :class="[
              'w-10 h-10 rounded-lg flex items-center justify-center flex-shrink-0',
              getColorClasses(activity.color).bg
            ]">
              <icon-carbon:chat v-if="activity.icon === 'chat'" :class="['text-lg', getColorClasses(activity.color).icon]" />
              <icon-carbon:document-add v-else-if="activity.icon === 'document-add'" :class="['text-lg', getColorClasses(activity.color).icon]" />
              <icon-carbon:user-follow v-else-if="activity.icon === 'user-follow'" :class="['text-lg', getColorClasses(activity.color).icon]" />
              <icon-carbon:data-base v-else-if="activity.icon === 'data-base'" :class="['text-lg', getColorClasses(activity.color).icon]" />
              <icon-carbon:settings-adjust v-else-if="activity.icon === 'settings-adjust'" :class="['text-lg', getColorClasses(activity.color).icon]" />
              <icon-carbon:thumbs-up v-else-if="activity.icon === 'thumbs-up'" :class="['text-lg', getColorClasses(activity.color).icon]" />
            </div>

            <div class="flex-1 min-w-0">
              <div class="flex items-start justify-between gap-2 mb-1">
                <h4 class="text-sm font-medium text-gray-900 dark:text-white">
                  {{ activity.title }}
                </h4>
                <span class="text-xs text-gray-500 dark:text-gray-400 flex-shrink-0">
                  {{ activity.time }}
                </span>
              </div>
              <p class="text-sm text-gray-600 dark:text-gray-400 line-clamp-1">
                {{ activity.description }}
              </p>
            </div>
          </div>
        </div>
      </NSpin>

      <!-- 活动统计 -->
      <div class="mt-4 pt-4 border-t border-gray-200 dark:border-gray-700">
        <div class="grid grid-cols-4 gap-4">
          <div class="text-center">
            <div class="text-2xl font-bold text-gray-900 dark:text-white">{{ activityStats.todayActivities }}</div>
            <div class="text-xs text-gray-500 dark:text-gray-400 mt-1">今日上传</div>
          </div>
          <div class="text-center">
            <div class="text-2xl font-bold text-gray-900 dark:text-white">{{ activityStats.weekActivities }}</div>
            <div class="text-xs text-gray-500 dark:text-gray-400 mt-1">本周上传</div>
          </div>
          <div class="text-center">
            <div class="text-2xl font-bold text-gray-900 dark:text-white">{{ activityStats.activeUsers }}</div>
            <div class="text-xs text-gray-500 dark:text-gray-400 mt-1">总用户数</div>
          </div>
          <div class="text-center">
            <div class="text-2xl font-bold text-gray-900 dark:text-white">{{ activityStats.systemEvents }}</div>
            <div class="text-xs text-gray-500 dark:text-gray-400 mt-1">总文件数</div>
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
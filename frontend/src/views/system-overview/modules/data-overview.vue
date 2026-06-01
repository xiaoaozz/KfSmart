<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { fetchGetSystemStats } from '@/service/api/system';

defineOptions({
  name: 'DataOverview'
});

const loading = ref(false);

interface StatItem {
  icon: string;
  label: string;
  value: string;
  change: string;
  trend: 'up' | 'down';
  changeLabel: string;
  color: string;
  tooltip: string;
}

const stats = ref<StatItem[]>([
  {
    icon: 'data-base',
    label: '组织标签数',
    value: '--',
    change: '--',
    trend: 'up',
    changeLabel: '总计',
    color: 'blue',
    tooltip: '当前系统中的组织标签总数量'
  },
  {
    icon: 'document',
    label: '文档总数',
    value: '--',
    change: '--',
    trend: 'up',
    changeLabel: '总计',
    color: 'green',
    tooltip: '系统中已上传的文档总数量'
  },
  {
    icon: 'user-multiple',
    label: '用户总数',
    value: '--',
    change: '--',
    trend: 'up',
    changeLabel: '总计',
    color: 'purple',
    tooltip: '系统中注册的用户总数'
  },
  {
    icon: 'chat',
    label: '会话总数',
    value: '--',
    change: '--',
    trend: 'up',
    changeLabel: '总计',
    color: 'orange',
    tooltip: '系统中产生的会话总数'
  },
  {
    icon: 'chat-bot',
    label: '今日上传',
    value: '--',
    change: '--',
    trend: 'up',
    changeLabel: '今日新增',
    color: 'cyan',
    tooltip: '今日新增上传的文档数量'
  },
  {
    icon: 'time',
    label: '今日会话',
    value: '--',
    change: '--',
    trend: 'up',
    changeLabel: '今日新增',
    color: 'pink',
    tooltip: '今日新增的会话数量'
  },
  {
    icon: 'thumbs-up',
    label: '文档完成率',
    value: '--%',
    change: '--',
    trend: 'up',
    changeLabel: '完成/总计',
    color: 'emerald',
    tooltip: '已完成处理文档占总文档的比例'
  },
  {
    icon: 'checkmark-filled',
    label: '系统状态',
    value: '正常',
    change: '运行中',
    trend: 'up',
    changeLabel: '',
    color: 'indigo',
    tooltip: '当前系统运行状态'
  }
]);

/** 格式化数字为带千分位分隔的字符串 */
function formatNumber(num: number): string {
  if (num >= 10000) {
    return (num / 10000).toFixed(1) + 'w';
  }
  return num.toLocaleString();
}

/** 从后端获取统计数据并更新 */
async function fetchStats() {
  loading.value = true;
  try {
    const { error, data } = await fetchGetSystemStats();
    if (!error && data) {
      // 更新各统计卡片
      stats.value[0].value = formatNumber(data.totalOrgTags || 0);
      stats.value[0].change = String(data.totalOrgTags || 0);

      stats.value[1].value = formatNumber(data.totalDocuments || 0);
      stats.value[1].change = String(data.totalDocuments || 0);

      stats.value[2].value = formatNumber(data.totalUsers || 0);
      stats.value[2].change = String(data.totalUsers || 0);

      stats.value[3].value = formatNumber(data.totalConversations || 0);
      stats.value[3].change = String(data.totalConversations || 0);

      stats.value[4].value = formatNumber(data.todayUploads || 0);
      stats.value[4].change = `+${data.todayUploads || 0}`;

      stats.value[5].value = formatNumber(data.todayConversations || 0);
      stats.value[5].change = `+${data.todayConversations || 0}`;

      // 文档完成率（如果有总数则计算比例）
      if (data.totalFiles && data.totalFiles > 0) {
        stats.value[6].value = Math.round((data.totalDocuments / data.totalFiles) * 100) + '%';
        stats.value[6].change = `${data.totalDocuments}/${data.totalFiles}`;
      }
    }
  } catch (e) {
    console.error('[DataOverview] 获取系统统计失败:', e);
  } finally {
    loading.value = false;
  }
}

onMounted(() => {
  fetchStats();
});

const getColorClasses = (color: string) => {
  const colorMap: Record<string, { icon: string; bg: string; text: string }> = {
    blue: {
      icon: 'text-blue-500',
      bg: 'bg-blue-50 dark:bg-blue-900/20',
      text: 'text-blue-600 dark:text-blue-400'
    },
    green: {
      icon: 'text-green-500',
      bg: 'bg-green-50 dark:bg-green-900/20',
      text: 'text-green-600 dark:text-green-400'
    },
    purple: {
      icon: 'text-purple-500',
      bg: 'bg-purple-50 dark:bg-purple-900/20',
      text: 'text-purple-600 dark:text-purple-400'
    },
    orange: {
      icon: 'text-orange-500',
      bg: 'bg-orange-50 dark:bg-orange-900/20',
      text: 'text-orange-600 dark:text-orange-400'
    },
    cyan: {
      icon: 'text-cyan-500',
      bg: 'bg-cyan-50 dark:bg-cyan-900/20',
      text: 'text-cyan-600 dark:text-cyan-400'
    },
    pink: {
      icon: 'text-pink-500',
      bg: 'bg-pink-50 dark:bg-pink-900/20',
      text: 'text-pink-600 dark:text-pink-400'
    },
    emerald: {
      icon: 'text-emerald-500',
      bg: 'bg-emerald-50 dark:bg-emerald-900/20',
      text: 'text-emerald-600 dark:text-emerald-400'
    },
    indigo: {
      icon: 'text-indigo-500',
      bg: 'bg-indigo-50 dark:bg-indigo-900/20',
      text: 'text-indigo-600 dark:text-indigo-400'
    }
  };
  return colorMap[color] || colorMap.blue;
};
</script>

<template>
  <div class="data-overview">
    <NCard>
      <template #header>
        <div class="flex items-center justify-between">
          <div>
            <h2 class="text-lg font-semibold text-gray-900 dark:text-white">核心数据概览</h2>
            <p class="text-xs text-gray-500 dark:text-gray-400 mt-1">系统关键指标实时监控（数据来自数据库）</p>
          </div>
          <NButton text @click="fetchStats">
            <template #icon>
              <icon-carbon:renew class="text-lg" />
            </template>
            刷新
          </NButton>
        </div>
      </template>

      <NSpin :show="loading">
        <div class="grid grid-cols-4 gap-4">
          <div
            v-for="stat in stats"
            :key="stat.label"
            class="stat-card bg-white dark:bg-gray-800 rounded-xl p-4 border border-gray-100 dark:border-gray-700 hover:shadow-lg transition-all cursor-pointer"
          >
            <NTooltip placement="top">
              <template #trigger>
                <div class="flex flex-col h-full">
                  <div class="flex items-start justify-between mb-3">
                    <div :class="[
                      'w-11 h-11 rounded-lg flex items-center justify-center flex-shrink-0',
                      getColorClasses(stat.color).bg
                    ]">
                      <icon-carbon:data-base v-if="stat.icon === 'data-base'" :class="['text-xl', getColorClasses(stat.color).icon]" />
                      <icon-carbon:document v-else-if="stat.icon === 'document'" :class="['text-xl', getColorClasses(stat.color).icon]" />
                      <icon-carbon:user-multiple v-else-if="stat.icon === 'user-multiple'" :class="['text-xl', getColorClasses(stat.color).icon]" />
                      <icon-carbon:chat v-else-if="stat.icon === 'chat'" :class="['text-xl', getColorClasses(stat.color).icon]" />
                      <icon-carbon:chat-bot v-else-if="stat.icon === 'chat-bot'" :class="['text-xl', getColorClasses(stat.color).icon]" />
                      <icon-carbon:time v-else-if="stat.icon === 'time'" :class="['text-xl', getColorClasses(stat.color).icon]" />
                      <icon-carbon:thumbs-up v-else-if="stat.icon === 'thumbs-up'" :class="['text-xl', getColorClasses(stat.color).icon]" />
                      <icon-carbon:checkmark-filled v-else-if="stat.icon === 'checkmark-filled'" :class="['text-xl', getColorClasses(stat.color).icon]" />
                    </div>
                  </div>
                  
                  <div class="flex-1">
                    <div class="text-xs text-gray-500 dark:text-gray-400 mb-2">{{ stat.label }}</div>
                    <div class="text-2xl font-bold text-gray-900 dark:text-white mb-2">{{ stat.value }}</div>
                  </div>

                  <div class="flex items-center gap-1 text-xs pt-2 border-t border-gray-100 dark:border-gray-700">
                    <span :class="stat.trend === 'up' ? 'text-green-600 dark:text-green-400' : 'text-cyan-600 dark:text-cyan-400'">
                      {{ stat.changeLabel ? stat.changeLabel + ' ' : '' }}{{ stat.change }}
                    </span>
                    <icon-carbon:arrow-up 
                      v-if="stat.trend === 'up'"
                      class="text-xs text-green-600 dark:text-green-400"
                    />
                    <icon-carbon:arrow-down 
                      v-else
                      class="text-xs text-cyan-600 dark:text-cyan-400"
                    />
                  </div>
                </div>
              </template>
              {{ stat.tooltip }}
            </NTooltip>
          </div>
        </div>
      </NSpin>
    </NCard>
  </div>
</template>

<style scoped lang="scss">
.data-overview {
  .stat-card {
    transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
    
    &:hover {
      transform: translateY(-4px);
      border-color: rgb(var(--primary-color));
    }
  }
}
</style>
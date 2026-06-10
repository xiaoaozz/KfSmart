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
    icon: 'chat',
    label: '今日问答数',
    value: '--',
    change: '--',
    trend: 'up',
    changeLabel: '实时累计',
    color: 'blue',
    tooltip: '今日已完成的用户问答次数'
  },
  {
    icon: 'search',
    label: '知识库命中率',
    value: '--%',
    change: '--',
    trend: 'up',
    changeLabel: '今日检索',
    color: 'emerald',
    tooltip: '今日问答中成功检索到知识库上下文的比例'
  },
  {
    icon: 'time',
    label: '平均响应时间',
    value: '--',
    change: '--',
    trend: 'down',
    changeLabel: '今日平均',
    color: 'cyan',
    tooltip: '今日 AI 问答从请求到完成的平均耗时'
  },
  {
    icon: 'document',
    label: '文档总数',
    value: '--',
    change: '--',
    trend: 'up',
    changeLabel: '总计',
    color: 'indigo',
    tooltip: '系统中已上传并纳入知识库统计的文档总数'
  }
]);

/** 格式化数字为带千分位分隔的字符串 */
function formatNumber(num: number): string {
  if (num >= 10000) {
    return (num / 10000).toFixed(1) + 'w';
  }
  return num.toLocaleString();
}

function formatDuration(ms: number): string {
  if (!ms) return '0ms';
  if (ms >= 1000) {
    return `${(ms / 1000).toFixed(1)}s`;
  }
  return `${ms}ms`;
}

/** 从后端获取统计数据并更新 */
async function fetchStats() {
  loading.value = true;
  try {
    const { error, data } = await fetchGetSystemStats();
    if (!error && data) {
      // 更新各统计卡片
      stats.value[0].value = formatNumber(data.todayQuestions || data.todayConversations || 0);
      stats.value[0].change = `+${data.todayQuestions || data.todayConversations || 0}`;

      stats.value[1].value = `${data.knowledgeHitRate || 0}%`;
      stats.value[1].change = data.knowledgeHitRate ? '有命中数据' : '暂无命中';

      stats.value[2].value = formatDuration(data.averageResponseTimeMs || 0);
      stats.value[2].change = data.averageResponseTimeMs ? `${data.averageResponseTimeMs}ms` : '暂无耗时';

      stats.value[3].value = formatNumber(data.totalDocuments || 0);
      stats.value[3].change = String(data.totalDocuments || 0);
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
        <div class="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
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
                      <icon-carbon:search v-else-if="stat.icon === 'search'" :class="['text-xl', getColorClasses(stat.color).icon]" />
                      <icon-carbon:time v-else-if="stat.icon === 'time'" :class="['text-xl', getColorClasses(stat.color).icon]" />
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

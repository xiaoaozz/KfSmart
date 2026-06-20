<script setup lang="ts">
import { computed } from 'vue';
import type { EChartsOption } from 'echarts';
import { useSystemOverviewShared } from '../composables/use-overview-shared';

defineOptions({
  name: 'PerformanceMetrics'
});

const { status: systemStatus, statusLoading: loading, loadStatus } = useSystemOverviewShared();

// 性能指标数据
interface PerformanceItem {
  metric: string;
  value: string;
  target: string;
  status: 'good' | 'normal' | 'warning';
  icon: string;
  color: string;
  description: string;
}

const performanceData = computed<PerformanceItem[]>(() => [
  {
    metric: 'CPU 使用率',
    value: systemStatus.value?.cpu_usage || '--',
    target: '< 80%',
    status: parseFloat(systemStatus.value?.cpu_usage || '0') > 80 ? 'warning' : 'good',
    icon: 'meter',
    color: 'blue',
    description: '服务器 CPU 当前使用率'
  },
  {
    metric: '内存使用率',
    value: systemStatus.value?.memory_usage || '--',
    target: '< 85%',
    status: parseFloat(systemStatus.value?.memory_usage || '0') > 85 ? 'warning' : 'good',
    icon: 'data-base',
    color: 'green',
    description: '服务器内存当前使用率'
  },
  {
    metric: '磁盘使用率',
    value: systemStatus.value?.disk_usage || '--',
    target: '< 90%',
    status: parseFloat(systemStatus.value?.disk_usage || '0') > 90 ? 'warning' : 'good',
    icon: 'document',
    color: 'orange',
    description: '服务器磁盘当前使用率'
  },
  {
    metric: '活跃用户数',
    value: systemStatus.value?.active_users?.toString() || '--',
    target: '> 0',
    status: (systemStatus.value?.active_users || 0) > 0 ? 'good' : 'normal',
    icon: 'user-multiple',
    color: 'purple',
    description: '当前活跃用户数量'
  }
]);

// 资源占用图
const chartOptions = computed<EChartsOption>(() => {
  const cpu = parseFloat(systemStatus.value?.cpu_usage || '0');
  const mem = parseFloat(systemStatus.value?.memory_usage || '0');
  const disk = parseFloat(systemStatus.value?.disk_usage || '0');

  return {
    tooltip: {
      trigger: 'axis',
      formatter: (params: any) => {
        return params.map((p: any) => `${p.seriesName}: ${p.value}%`).join('<br/>');
      }
    },
    radar: {
      center: ['50%', '55%'],
      radius: '65%',
      indicator: [
        { name: 'CPU 使用率', max: 100 },
        { name: '内存使用率', max: 100 },
        { name: '磁盘使用率', max: 100 },
        { name: '系统负载', max: 100 },
        { name: '响应速度', max: 100 }
      ]
    },
    series: [
      {
        name: '系统资源使用',
        type: 'radar',
        data: [
          {
            value: [cpu, mem, disk, (cpu + mem + disk) / 3, 100 - (cpu + mem + disk) / 3],
            name: '当前状态',
            areaStyle: {
              color: {
                type: 'radial',
                x: 0.5, y: 0.5, r: 0.5,
                colorStops: [
                  { offset: 0, color: 'rgba(102, 126, 234, 0.3)' },
                  { offset: 0.5, color: 'rgba(6, 182, 212, 0.2)' },
                  { offset: 1, color: 'rgba(16, 185, 129, 0.1)' }
                ]
              }
            },
            lineStyle: {
              color: '#667eea',
              width: 2
            },
            itemStyle: {
              color: '#667eea'
            }
          }
        ]
      }
    ]
  };
});

const getStatusColor = (status: 'good' | 'normal' | 'warning') => {
  const map = {
    good: 'bg-green-50 dark:bg-green-900/20 text-green-600 dark:text-green-400',
    normal: 'bg-blue-50 dark:bg-blue-900/20 text-blue-600 dark:text-blue-400',
    warning: 'bg-orange-50 dark:bg-orange-900/20 text-orange-600 dark:text-orange-400'
  };
  return map[status];
};

const getColorClasses = (color: string) => {
  const colorMap: Record<string, { icon: string; bg: string }> = {
    blue: { icon: 'text-blue-500', bg: 'bg-blue-50 dark:bg-blue-900/20' },
    green: { icon: 'text-green-500', bg: 'bg-green-50 dark:bg-green-900/20' },
    orange: { icon: 'text-orange-500', bg: 'bg-orange-50 dark:bg-orange-900/20' },
    purple: { icon: 'text-purple-500', bg: 'bg-purple-50 dark:bg-purple-900/20' }
  };
  return colorMap[color] || colorMap.blue;
};

</script>

<template>
  <div class="performance-metrics h-full">
    <NCard class="h-full">
      <template #header>
        <div class="flex items-center justify-between">
          <div>
            <h2 class="text-lg font-semibold text-gray-900 dark:text-white">系统性能</h2>
            <p class="text-xs text-gray-500 dark:text-gray-400 mt-1">
              服务器资源使用情况实时监控
              <span class="text-blue-500" v-if="systemStatus">（数据来自系统 API）</span>
            </p>
          </div>
          <NButton text @click="loadStatus">
            <template #icon>
              <icon-carbon:renew class="text-lg" />
            </template>
            刷新
          </NButton>
        </div>
      </template>

      <NSpin :show="loading">
        <div class="flex h-[560px] flex-col">
          <!-- 指标卡片 -->
          <div class="grid grid-cols-2 gap-4">
            <div
              v-for="item in performanceData"
              :key="item.metric"
              class="metric-card rounded-xl p-4 border border-gray-100 dark:border-gray-700 bg-white dark:bg-gray-800 transition-all hover:shadow-md"
            >
              <div class="flex items-start justify-between mb-3">
                <div :class="['w-10 h-10 rounded-lg flex items-center justify-center flex-shrink-0', getColorClasses(item.color).bg]">
                  <icon-carbon:meter v-if="item.icon === 'meter'" :class="['text-xl', getColorClasses(item.color).icon]" />
                  <icon-carbon:data-base v-else-if="item.icon === 'data-base'" :class="['text-xl', getColorClasses(item.color).icon]" />
                  <icon-carbon:document v-else-if="item.icon === 'document'" :class="['text-xl', getColorClasses(item.color).icon]" />
                  <icon-carbon:user-multiple v-else-if="item.icon === 'user-multiple'" :class="['text-xl', getColorClasses(item.color).icon]" />
                </div>
                <div :class="['inline-flex px-2 py-1 rounded-full text-xs', getStatusColor(item.status)]">
                  {{ item.status === 'good' ? '正常' : item.status === 'normal' ? '一般' : '警告' }}
                </div>
              </div>
              <div class="text-xs text-gray-500 dark:text-gray-400 mb-2">{{ item.metric }}</div>
              <div class="text-2xl font-bold text-gray-900 dark:text-white mb-2">{{ item.value }}</div>
              <div class="text-xs text-gray-400 dark:text-gray-500 line-clamp-1">
                目标：{{ item.target }} | {{ item.description }}
              </div>
            </div>
          </div>

          <!-- 资源占用雷达图 -->
          <div class="chart-container mt-4 min-h-0 flex-1">
            <VChart :option="chartOptions" autoresize />
          </div>
        </div>
      </NSpin>
    </NCard>
  </div>
</template>

<style scoped lang="scss">
.performance-metrics {
  .chart-container {
    width: 100%;
  }

  :deep(.n-card) {
    height: 100%;
  }

  :deep(.n-card__content) {
    height: calc(100% - 73px);
  }

  .metric-card {
    transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
    
    &:hover {
      transform: translateY(-2px);
    }
  }
}
</style>

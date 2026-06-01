<script setup lang="ts">
import { ref, computed } from 'vue';
import type { EChartsOption } from 'echarts';

defineOptions({
  name: 'PerformanceMetrics'
});

const performanceData = ref([
  {
    metric: '平均响应时间',
    value: '1.42s',
    target: '< 2s',
    status: 'good',
    trend: 'down',
    change: '-0.21s',
    icon: 'time'
  },
  {
    metric: 'API成功率',
    value: '99.8%',
    target: '> 99%',
    status: 'good',
    trend: 'up',
    change: '+0.2%',
    icon: 'checkmark-outline'
  },
  {
    metric: '并发处理能力',
    value: '2,847',
    target: '> 2000',
    status: 'good',
    trend: 'up',
    change: '+423',
    icon: 'cloud-upload'
  },
  {
    metric: '系统可用性',
    value: '99.95%',
    target: '> 99.9%',
    status: 'good',
    trend: 'stable',
    change: '0%',
    icon: 'cloud-monitoring'
  }
]);

const responseTimeChartOptions = computed<EChartsOption>(() => ({
  tooltip: {
    trigger: 'axis',
    axisPointer: {
      type: 'shadow'
    }
  },
  grid: {
    left: '3%',
    right: '4%',
    bottom: '3%',
    top: '8%',
    containLabel: true
  },
  xAxis: {
    type: 'category',
    data: ['0-1s', '1-2s', '2-3s', '3-5s', '5s+'],
    axisLine: {
      lineStyle: {
        color: '#ddd'
      }
    },
    axisLabel: {
      color: '#666',
      fontSize: 11
    }
  },
  yAxis: {
    type: 'value',
    axisLine: {
      lineStyle: {
        color: '#ddd'
      }
    },
    axisLabel: {
      color: '#666',
      fontSize: 11
    },
    splitLine: {
      lineStyle: {
        color: '#f0f0f0'
      }
    }
  },
  series: [
    {
      name: '响应分布',
      type: 'bar',
      barWidth: '50%',
      itemStyle: {
        borderRadius: [6, 6, 0, 0],
        color: {
          type: 'linear',
          x: 0,
          y: 0,
          x2: 0,
          y2: 1,
          colorStops: [
            { offset: 0, color: '#667eea' },
            { offset: 1, color: '#764ba2' }
          ]
        }
      },
      data: [1823, 734, 245, 89, 23]
    }
  ]
}));

const getStatusColor = (status: string) => {
  return status === 'good' ? 'text-green-500' : status === 'warning' ? 'text-orange-500' : 'text-red-500';
};

const getStatusBg = (status: string) => {
  return status === 'good' 
    ? 'bg-green-50 dark:bg-green-900/20' 
    : status === 'warning' 
    ? 'bg-orange-50 dark:bg-orange-900/20' 
    : 'bg-red-50 dark:bg-red-900/20';
};
</script>

<template>
  <div class="performance-metrics">
    <NCard>
      <template #header>
        <div>
          <h2 class="text-lg font-semibold text-gray-900 dark:text-white">性能指标</h2>
          <p class="text-xs text-gray-500 dark:text-gray-400 mt-1">系统性能实时监控</p>
        </div>
      </template>

      <div class="flex flex-col gap-4">
        <!-- 性能指标卡片 -->
        <div class="grid grid-cols-2 gap-3">
          <div
            v-for="metric in performanceData"
            :key="metric.metric"
            class="p-3 rounded-lg border border-gray-200 dark:border-gray-700 hover:shadow-md transition-all"
          >
            <div class="flex items-start justify-between mb-2">
              <div :class="['w-8 h-8 rounded-lg flex items-center justify-center', getStatusBg(metric.status)]">
                <!-- 使用静态图标组件 -->
                <icon-carbon:time v-if="metric.icon === 'time'" :class="['text-lg', getStatusColor(metric.status)]" />
                <icon-carbon:checkmark-outline v-else-if="metric.icon === 'checkmark-outline'" :class="['text-lg', getStatusColor(metric.status)]" />
                <icon-carbon:cloud-upload v-else-if="metric.icon === 'cloud-upload'" :class="['text-lg', getStatusColor(metric.status)]" />
                <icon-carbon:cloud-monitoring v-else-if="metric.icon === 'cloud-monitoring'" :class="['text-lg', getStatusColor(metric.status)]" />
              </div>
              <div v-if="metric.trend !== 'stable'" class="flex items-center gap-1 text-xs">
                <icon-carbon:arrow-up 
                  v-if="metric.trend === 'up'"
                  :class="[
                    'text-xs',
                    'text-green-500'
                  ]"
                />
                <icon-carbon:arrow-down 
                  v-else
                  :class="[
                    'text-xs',
                    metric.metric === '平均响应时间' ? 'text-green-500' : 'text-red-500'
                  ]"
                />
                <span :class="[
                  metric.trend === 'down' && metric.metric === '平均响应时间' 
                    ? 'text-green-600 dark:text-green-400' 
                    : metric.trend === 'up' 
                    ? 'text-green-600 dark:text-green-400' 
                    : 'text-red-600 dark:text-red-400'
                ]">
                  {{ metric.change }}
                </span>
              </div>
            </div>
            <div class="text-xs text-gray-500 dark:text-gray-400 mb-1">{{ metric.metric }}</div>
            <div class="flex items-end justify-between">
              <div class="text-xl font-bold text-gray-900 dark:text-white">{{ metric.value }}</div>
              <div class="text-xs text-gray-400">目标: {{ metric.target }}</div>
            </div>
          </div>
        </div>

        <!-- 响应时间分布图 -->
        <div class="mt-2">
          <div class="text-sm font-medium text-gray-700 dark:text-gray-300 mb-3">响应时间分布</div>
          <div class="chart-container">
            <VChart :option="responseTimeChartOptions" autoresize />
          </div>
        </div>

        <!-- 系统状态摘要 -->
        <div class="flex items-center gap-2 p-3 rounded-lg bg-green-50 dark:bg-green-900/20 border border-green-200 dark:border-green-800">
          <div class="w-8 h-8 rounded-full bg-green-500 flex items-center justify-center flex-shrink-0">
            <icon-carbon:checkmark class="text-lg text-white" />
          </div>
          <div class="flex-1">
            <div class="text-sm font-medium text-green-900 dark:text-green-100">系统运行正常</div>
            <div class="text-xs text-green-700 dark:text-green-300">所有性能指标均在正常范围内</div>
          </div>
        </div>
      </div>
    </NCard>
  </div>
</template>

<style scoped lang="scss">
.performance-metrics {
  .chart-container {
    height: 200px;
    width: 100%;
  }
}
</style>

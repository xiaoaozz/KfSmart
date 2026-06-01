<script setup lang="ts">
import { ref, computed } from 'vue';
import type { EChartsOption } from 'echarts';

defineOptions({
  name: 'UsageTrends'
});

const timeRange = ref<'day' | 'week' | 'month'>('week');

const chartOptions = computed<EChartsOption>(() => {
  const dates = timeRange.value === 'day' 
    ? ['00:00', '04:00', '08:00', '12:00', '16:00', '20:00', '23:59']
    : timeRange.value === 'week'
    ? ['周一', '周二', '周三', '周四', '周五', '周六', '周日']
    : ['第1周', '第2周', '第3周', '第4周'];

  const conversationData = timeRange.value === 'day'
    ? [45, 32, 78, 156, 245, 198, 167]
    : timeRange.value === 'week'
    ? [285, 312, 398, 421, 387, 156, 134]
    : [1245, 1389, 1524, 1687];

  const responseData = timeRange.value === 'day'
    ? [289, 198, 534, 987, 1543, 1289, 1067]
    : timeRange.value === 'week'
    ? [1823, 2145, 2567, 2834, 2456, 987, 856]
    : [8456, 9234, 9876, 10234];

  const userCountData = timeRange.value === 'day'
    ? [23, 18, 42, 68, 85, 72, 58]
    : timeRange.value === 'week'
    ? [78, 82, 86, 89, 84, 45, 38]
    : [245, 267, 289, 312];

  return {
    tooltip: {
      trigger: 'axis',
      axisPointer: {
        type: 'cross',
        label: {
          backgroundColor: '#6a7985'
        }
      }
    },
    legend: {
      data: ['会话数', 'AI回答数', '活跃用户数'],
      textStyle: {
        color: '#666'
      }
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '3%',
      top: '12%',
      containLabel: true
    },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: dates,
      axisLine: {
        lineStyle: {
          color: '#ddd'
        }
      },
      axisLabel: {
        color: '#666'
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
        color: '#666'
      },
      splitLine: {
        lineStyle: {
          color: '#f0f0f0'
        }
      }
    },
    series: [
      {
        name: '会话数',
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 6,
        lineStyle: {
          width: 3,
          color: {
            type: 'linear',
            x: 0,
            y: 0,
            x2: 1,
            y2: 0,
            colorStops: [
              { offset: 0, color: '#667eea' },
              { offset: 1, color: '#764ba2' }
            ]
          }
        },
        areaStyle: {
          color: {
            type: 'linear',
            x: 0,
            y: 0,
            x2: 0,
            y2: 1,
            colorStops: [
              { offset: 0, color: 'rgba(102, 126, 234, 0.3)' },
              { offset: 1, color: 'rgba(102, 126, 234, 0.05)' }
            ]
          }
        },
        data: conversationData
      },
      {
        name: 'AI回答数',
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 6,
        lineStyle: {
          width: 3,
          color: {
            type: 'linear',
            x: 0,
            y: 0,
            x2: 1,
            y2: 0,
            colorStops: [
              { offset: 0, color: '#06b6d4' },
              { offset: 1, color: '#0891b2' }
            ]
          }
        },
        areaStyle: {
          color: {
            type: 'linear',
            x: 0,
            y: 0,
            x2: 0,
            y2: 1,
            colorStops: [
              { offset: 0, color: 'rgba(6, 182, 212, 0.3)' },
              { offset: 1, color: 'rgba(6, 182, 212, 0.05)' }
            ]
          }
        },
        data: responseData
      },
      {
        name: '活跃用户数',
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 6,
        lineStyle: {
          width: 3,
          color: {
            type: 'linear',
            x: 0,
            y: 0,
            x2: 1,
            y2: 0,
            colorStops: [
              { offset: 0, color: '#10b981' },
              { offset: 1, color: '#059669' }
            ]
          }
        },
        areaStyle: {
          color: {
            type: 'linear',
            x: 0,
            y: 0,
            x2: 0,
            y2: 1,
            colorStops: [
              { offset: 0, color: 'rgba(16, 185, 129, 0.3)' },
              { offset: 1, color: 'rgba(16, 185, 129, 0.05)' }
            ]
          }
        },
        data: userCountData
      }
    ]
  };
});

const handleTimeRangeChange = (range: 'day' | 'week' | 'month') => {
  timeRange.value = range;
};
</script>

<template>
  <div class="usage-trends">
    <NCard>
      <template #header>
        <div class="flex items-center justify-between">
          <div>
            <h2 class="text-lg font-semibold text-gray-900 dark:text-white">使用趋势</h2>
            <p class="text-xs text-gray-500 dark:text-gray-400 mt-1">系统使用情况趋势分析</p>
          </div>
          <div class="flex gap-2">
            <NButton
              :type="timeRange === 'day' ? 'primary' : 'default'"
              size="small"
              @click="handleTimeRangeChange('day')"
            >
              今日
            </NButton>
            <NButton
              :type="timeRange === 'week' ? 'primary' : 'default'"
              size="small"
              @click="handleTimeRangeChange('week')"
            >
              本周
            </NButton>
            <NButton
              :type="timeRange === 'month' ? 'primary' : 'default'"
              size="small"
              @click="handleTimeRangeChange('month')"
            >
              本月
            </NButton>
          </div>
        </div>
      </template>

      <div class="chart-container">
        <VChart :option="chartOptions" autoresize />
      </div>
    </NCard>
  </div>
</template>

<style scoped lang="scss">
.usage-trends {
  .chart-container {
    height: 400px;
    width: 100%;
  }
}
</style>

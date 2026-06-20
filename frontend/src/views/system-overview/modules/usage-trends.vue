<script setup lang="ts">
import { computed, nextTick, watch } from 'vue';
import { type ECOption, useEcharts } from '@/hooks/common/echarts';
import { useSystemOverviewShared } from '../composables/use-overview-shared';

defineOptions({
  name: 'UsageTrends'
});

const { stats: systemStats, statsLoading: loading, loadStats } = useSystemOverviewShared();

interface TrendItem {
  date: string;
  label: string;
  questions: number;
}

function buildFallbackTrends(): TrendItem[] {
  const today = new Date();
  const todayQuestions = systemStats.value?.todayQuestions || systemStats.value?.todayConversations || 0;
  return Array.from({ length: 7 }, (_, index) => {
    const date = new Date(today);
    date.setDate(today.getDate() - (6 - index));
    const isToday = index === 6;
    return {
      date: date.toISOString().slice(0, 10),
      label: `${date.getMonth() + 1}/${date.getDate()}`,
      questions: isToday ? todayQuestions : 0
    };
  });
}

const chartTrends = computed<TrendItem[]>(() => {
  const source = systemStats.value?.usageTrends || [];
  if (source.length > 0) {
    return source.map(item => ({
      date: item.date,
      label: item.label,
      questions: Number(item.questions || 0)
    }));
  }
  return buildFallbackTrends();
});

function buildChartOptions(): ECOption {
  return {
    tooltip: {
      trigger: 'axis',
      axisPointer: {
        type: 'line',
        lineStyle: { color: '#94a3b8' }
      },
      formatter: (params: any) => {
        const item = params?.[0];
        return item ? `${item.axisValue}<br/>问答数：${item.value}` : '';
      }
    },
    grid: {
      left: 16,
      right: 20,
      top: 28,
      bottom: 18,
      containLabel: true
    },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: chartTrends.value.map(item => item.label),
      axisTick: { show: false },
      axisLine: { lineStyle: { color: '#e5e7eb' } },
      axisLabel: { color: '#64748b' }
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      min: 0,
      max: (value: { max: number }) => (value.max <= 0 ? 5 : Math.ceil(value.max * 1.2)),
      axisLabel: { color: '#64748b' },
      splitLine: { lineStyle: { color: '#eef2f7' } }
    },
    series: [
      {
        name: '问答数',
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 7,
        lineStyle: {
          width: 3,
          color: '#2563eb'
        },
        itemStyle: {
          color: '#2563eb',
          borderColor: '#fff',
          borderWidth: 2
        },
        areaStyle: {
          color: {
            type: 'linear',
            x: 0,
            y: 0,
            x2: 0,
            y2: 1,
            colorStops: [
              { offset: 0, color: 'rgba(37, 99, 235, 0.22)' },
              { offset: 1, color: 'rgba(37, 99, 235, 0.02)' }
            ]
          }
        },
        data: chartTrends.value.map(item => item.questions)
      }
    ]
  };
}

const { domRef, updateOptions } = useEcharts(buildChartOptions, {
  onRender: chart => {
    // 图表渲染完成后，用最新数据（含已加载的 API 数据）更新一次
    // 避免 API 数据比图表渲染更早到达时更新被跳过的问题
    chart.hideLoading();
    chart.setOption(buildChartOptions(), true);
  }
});

async function refreshChart() {
  await nextTick();
  await updateOptions(() => buildChartOptions());
}

const totalQuestions = computed(() => chartTrends.value.reduce((sum, item) => sum + item.questions, 0));
const todayQuestions = computed(() => chartTrends.value.at(-1)?.questions || 0);
const peakQuestions = computed(() => Math.max(...chartTrends.value.map(item => item.questions), 0));
const activeDays = computed(() => chartTrends.value.filter(item => item.questions > 0).length);

const summaryItems = computed(() => [
  {
    label: '7日问答',
    value: totalQuestions.value,
    icon: 'chart-line'
  },
  {
    label: '今日问答',
    value: todayQuestions.value,
    icon: 'chat'
  },
  {
    label: '峰值',
    value: peakQuestions.value,
    icon: 'growth'
  },
  {
    label: '活跃天数',
    value: activeDays.value,
    icon: 'calendar'
  }
]);

function getSummaryIcon(icon: string) {
  const map: Record<string, string> = {
    'chart-line': 'i-carbon-chart-line',
    chat: 'i-carbon-chat',
    growth: 'i-carbon-growth',
    calendar: 'i-carbon-calendar'
  };
  return map[icon] || map['chart-line'];
}

watch(chartTrends, () => {
  refreshChart();
}, { deep: true, immediate: true });
</script>

<template>
  <div class="usage-trends">
    <NCard class="h-full">
      <template #header>
        <div class="flex items-center justify-between">
          <div>
            <h2 class="text-lg text-gray-900 font-semibold dark:text-white">使用趋势</h2>
            <p class="mt-1 text-xs text-gray-500 dark:text-gray-400">近 7 天问答量趋势</p>
          </div>
          <NButton text @click="loadStats">
            <template #icon>
              <icon-carbon:renew class="text-lg" />
            </template>
            刷新
          </NButton>
        </div>
      </template>

      <NSpin :show="loading">
        <div class="h-[360px] flex flex-col gap-4">
          <div class="grid grid-cols-4 gap-3">
            <div v-for="item in summaryItems" :key="item.label" class="summary-card">
              <div class="flex items-center justify-between">
                <div class="text-xs text-gray-500 dark:text-gray-400">{{ item.label }}</div>
                <div class="text-base text-blue-500" :class="[getSummaryIcon(item.icon)]" />
              </div>
              <div class="mt-1 text-lg text-gray-900 font-bold dark:text-white">{{ item.value }}</div>
            </div>
          </div>

          <div ref="domRef" class="chart-container min-h-0 flex-1"></div>
          <div v-if="chartTrends.length === 0" class="empty-chart text-gray-400">
            <icon-carbon:chart-line-data class="mb-3 text-4xl" />
            <p class="text-sm">暂无趋势数据</p>
          </div>
        </div>
      </NSpin>
    </NCard>
  </div>
</template>

<style scoped lang="scss">
.usage-trends {
  height: 100%;

  .chart-container {
    width: 100%;
  }

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

  .empty-chart {
    display: none;
  }

  :global(.dark) .summary-card {
    border-color: rgb(55 65 81);
    background: rgb(31 41 55);
  }
}
</style>

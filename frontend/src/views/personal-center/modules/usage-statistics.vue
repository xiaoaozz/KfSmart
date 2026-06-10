<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue';
import { fetchGetUsageStats } from '@/service/api/auth';
import { type ECOption, useEcharts } from '@/hooks/common/echarts';

defineOptions({ name: 'UsageStatistics' });

type RangeValue = 7 | 30;

const selectedRange = ref<RangeValue>(7);
const loading = ref(false);
const stats = ref<Api.User.UsageStats | null>(null);

interface TrendItem {
  date: string;
  label: string;
  questions: number;
}

function buildFallbackTrends(): TrendItem[] {
  const today = new Date();
  return Array.from({ length: selectedRange.value }, (_, index) => {
    const date = new Date(today);
    date.setDate(today.getDate() - (selectedRange.value - 1 - index));
    return {
      date: date.toISOString().slice(0, 10),
      label: `${date.getMonth() + 1}/${date.getDate()}`,
      questions: 0
    };
  });
}

const chartTrends = computed<TrendItem[]>(() => {
  const source = stats.value?.usageTrends || [];
  if (source.length > 0) {
    return source.map(item => ({
      date: item.date,
      label: item.label,
      questions: Number(item.questions || 0)
    }));
  }
  return buildFallbackTrends();
});

function formatNumber(value: number) {
  return Number(value || 0).toLocaleString();
}

function formatSize(bytes: number) {
  if (!bytes) return '0 B';
  const units = ['B', 'KB', 'MB', 'GB'];
  let value = bytes;
  let index = 0;
  while (value >= 1024 && index < units.length - 1) {
    value /= 1024;
    index += 1;
  }
  return `${value.toFixed(value >= 10 || index === 0 ? 0 : 1)} ${units[index]}`;
}

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
        return item ? `${item.axisValue}<br/>对话次数：${item.value}` : '';
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
      axisLabel: {
        color: '#64748b',
        interval: selectedRange.value === 30 ? 4 : 0
      }
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
        name: '对话次数',
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

const rangeConversationTotal = computed(() =>
  chartTrends.value.reduce((sum, item) => sum + item.questions, 0)
);
const peakConversations = computed(() => Math.max(...chartTrends.value.map(item => item.questions), 0));
const activeDays = computed(() => chartTrends.value.filter(item => item.questions > 0).length);

const overview = computed(() => [
  {
    label: '总对话次数',
    value: formatNumber(stats.value?.totalConversations || 0),
    unit: '次',
    icon: 'carbon:chat',
    color: 'text-blue-500',
    bg: 'bg-blue-50 dark:bg-blue-900/20',
    change: `今日 ${formatNumber(stats.value?.todayConversations || 0)} 次`
  },
  {
    label: '上传文档数',
    value: formatNumber(stats.value?.totalDocuments || 0),
    unit: '份',
    icon: 'carbon:upload',
    color: 'text-green-500',
    bg: 'bg-green-50 dark:bg-green-900/20',
    change: `今日 ${formatNumber(stats.value?.todayUploads || 0)} 份`
  },
  {
    label: '知识库数量',
    value: formatNumber(stats.value?.knowledgeBaseCount || 0),
    unit: '个',
    icon: 'carbon:folder',
    color: 'text-violet-500',
    bg: 'bg-violet-50 dark:bg-violet-900/20',
    change: '我创建的知识库'
  },
  {
    label: '本周活跃天',
    value: formatNumber(stats.value?.weekActiveDays || 0),
    unit: '天',
    icon: 'carbon:calendar',
    color: 'text-orange-500',
    bg: 'bg-orange-50 dark:bg-orange-900/20',
    change: '近 7 天有对话'
  },
  {
    label: `${selectedRange.value}日对话`,
    value: formatNumber(rangeConversationTotal.value),
    unit: '次',
    icon: 'carbon:chart-line',
    color: 'text-cyan-500',
    bg: 'bg-cyan-50 dark:bg-cyan-900/20',
    change: `峰值 ${formatNumber(peakConversations.value)} 次`
  },
  {
    label: '存储占用',
    value: formatSize(stats.value?.totalStorage || 0),
    unit: '',
    icon: 'carbon:data-base',
    color: 'text-pink-500',
    bg: 'bg-pink-50 dark:bg-pink-900/20',
    change: '已完成上传'
  }
]);

const topKnowledge = computed(() => {
  const list = stats.value?.topKnowledgeBases || [];
  const maxCount = Math.max(...list.map(item => item.count), 1);
  return list.map((item, index) => ({
    ...item,
    percent: Math.round((item.count / maxCount) * 100),
    color: ['#2563EB', '#16A34A', '#F59E0B', '#DC2626', '#9333EA'][index] || '#64748B'
  }));
});

const featureUsage = computed(() => stats.value?.featureUsage || []);
const featureCountTotal = computed(() => featureUsage.value.reduce((sum, item) => sum + Number(item.count || 0), 0));

async function refreshChart() {
  await nextTick();
  await updateOptions(() => buildChartOptions());
}

async function fetchUsageStats() {
  loading.value = true;
  try {
    const { error, data } = await fetchGetUsageStats(selectedRange.value);
    if (!error && data) {
      stats.value = data;
    }
  } catch {
    window.$message?.error('获取个人使用统计失败');
  } finally {
    loading.value = false;
  }
}

watch(stats, async () => {
  await refreshChart();
});

watch(selectedRange, () => {
  fetchUsageStats();
});

onMounted(() => {
  fetchUsageStats();
});
</script>

<template>
  <div class="usage-statistics space-y-6">
    <NSpin :show="loading">
      <div class="space-y-6">
        <div class="grid grid-cols-1 gap-3 lg:grid-cols-3 sm:grid-cols-2">
          <div
            v-for="item in overview"
            :key="item.label"
            class="flex items-center gap-3 border border-gray-200 rounded-lg bg-white p-3 dark:border-gray-700 dark:bg-gray-800"
          >
            <div class="h-10 w-10 flex flex-shrink-0 items-center justify-center rounded-lg" :class="item.bg">
              <component :is="item.icon" class="text-lg" :class="item.color" />
            </div>
            <div class="min-w-0">
              <p class="text-xs text-gray-400">{{ item.label }}</p>
              <div class="flex items-baseline gap-1">
                <span class="text-lg text-gray-800 font-bold dark:text-gray-100">{{ item.value }}</span>
                <span v-if="item.unit" class="text-xs text-gray-400">{{ item.unit }}</span>
              </div>
              <p class="mt-0.5 text-xs text-gray-500 dark:text-gray-400">{{ item.change }}</p>
            </div>
          </div>
        </div>

        <div class="border border-gray-200 rounded-lg bg-white p-4 dark:border-gray-700 dark:bg-gray-800">
          <div class="mb-4 flex items-center justify-between">
            <div>
              <h3 class="text-sm text-gray-800 font-semibold dark:text-gray-100">使用趋势</h3>
              <p class="mt-0.5 text-xs text-gray-400">每日个人对话次数</p>
            </div>
            <NRadioGroup v-model:value="selectedRange" size="small">
              <NRadioButton :value="7">近7天</NRadioButton>
              <NRadioButton :value="30">近30天</NRadioButton>
            </NRadioGroup>
          </div>

          <div class="grid grid-cols-3 mb-4 gap-3">
            <div class="summary-card">
              <div class="flex items-center justify-between">
                <div class="text-xs text-gray-500 dark:text-gray-400">{{ selectedRange }}日对话</div>
                <div class="text-base text-blue-500 i-carbon-chart-line" />
              </div>
              <div class="mt-1 text-lg text-gray-900 font-bold dark:text-white">
                {{ formatNumber(rangeConversationTotal) }}
              </div>
            </div>
            <div class="summary-card">
              <div class="flex items-center justify-between">
                <div class="text-xs text-gray-500 dark:text-gray-400">峰值</div>
                <div class="text-base text-blue-500 i-carbon-growth" />
              </div>
              <div class="mt-1 text-lg text-gray-900 font-bold dark:text-white">
                {{ formatNumber(peakConversations) }}
              </div>
            </div>
            <div class="summary-card">
              <div class="flex items-center justify-between">
                <div class="text-xs text-gray-500 dark:text-gray-400">活跃天数</div>
                <div class="text-base text-blue-500 i-carbon-calendar" />
              </div>
              <div class="mt-1 text-lg text-gray-900 font-bold dark:text-white">{{ activeDays }}</div>
            </div>
          </div>

          <div ref="domRef" class="h-72 w-full"></div>
        </div>

        <div class="grid grid-cols-1 gap-4 lg:grid-cols-2">
          <div class="border border-gray-200 rounded-lg bg-white p-4 dark:border-gray-700 dark:bg-gray-800">
            <h3 class="mb-3 text-sm text-gray-800 font-semibold dark:text-gray-100">知识库文档 Top5</h3>
            <div v-if="topKnowledge.length > 0" class="space-y-3">
              <div v-for="(kb, idx) in topKnowledge" :key="kb.kbId">
                <div class="mb-1 flex items-center justify-between gap-3">
                  <div class="min-w-0 flex items-center gap-2">
                    <span class="w-3 text-xs text-gray-400">{{ idx + 1 }}</span>
                    <span class="truncate text-xs text-gray-700 dark:text-gray-300">{{ kb.name }}</span>
                  </div>
                  <span class="flex-shrink-0 text-xs text-gray-500">{{ kb.count }} 份</span>
                </div>
                <div class="h-1.5 overflow-hidden rounded-full bg-gray-100 dark:bg-gray-700">
                  <div
                    class="h-full rounded-full transition-all duration-700"
                    :style="{ width: `${kb.percent}%`, background: kb.color }"
                  />
                </div>
              </div>
            </div>
            <div v-else class="flex flex-col items-center justify-center py-10 text-gray-400">
              <icon-carbon:folder class="mb-2 text-3xl" />
              <p class="text-xs">暂无知识库数据</p>
            </div>
          </div>

          <div class="border border-gray-200 rounded-lg bg-white p-4 dark:border-gray-700 dark:bg-gray-800">
            <h3 class="mb-3 text-sm text-gray-800 font-semibold dark:text-gray-100">功能使用分布</h3>
            <div class="flex items-center gap-5">
              <div class="relative h-24 w-24 flex-shrink-0">
                <svg viewBox="0 0 36 36" class="h-full w-full -rotate-90">
                  <circle
                    cx="18"
                    cy="18"
                    r="15.9"
                    fill="none"
                    stroke="#F3F4F6"
                    stroke-width="3.8"
                    class="dark:stroke-gray-700"
                  />
                  <circle
                    v-for="(feat, fi) in featureUsage"
                    :key="feat.label"
                    cx="18"
                    cy="18"
                    r="15.9"
                    fill="none"
                    :stroke="feat.color"
                    stroke-width="3.8"
                    :stroke-dasharray="`${featureCountTotal > 0 ? (feat.count / featureCountTotal) * 100 : 0} ${featureCountTotal > 0 ? 100 - (feat.count / featureCountTotal) * 100 : 100}`"
                    :stroke-dashoffset="
                      -featureUsage
                        .slice(0, fi)
                        .reduce(
                          (sum, item) => sum + (featureCountTotal > 0 ? (item.count / featureCountTotal) * 100 : 0),
                          0
                        )
                    "
                  />
                </svg>
                <div class="absolute inset-0 flex items-center justify-center">
                  <span class="text-xs text-gray-600 font-bold dark:text-gray-300">
                    {{ formatNumber(featureCountTotal) }}
                  </span>
                </div>
              </div>
              <div class="min-w-0 flex-1 space-y-2">
                <div v-for="feat in featureUsage" :key="feat.label" class="flex items-center justify-between gap-3">
                  <div class="min-w-0 flex items-center gap-1.5">
                    <span class="h-2 w-2 flex-shrink-0 rounded-full" :style="{ background: feat.color }" />
                    <span class="truncate text-xs text-gray-600 dark:text-gray-400">{{ feat.label }}</span>
                  </div>
                  <span class="flex-shrink-0 text-xs text-gray-700 font-medium dark:text-gray-300">
                    {{ feat.value }}%
                  </span>
                </div>
                <div v-if="featureUsage.length === 0" class="py-8 text-center text-xs text-gray-400">
                  暂无功能使用数据
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </NSpin>
  </div>
</template>

<style scoped lang="scss">
.summary-card {
  border: 1px solid rgb(243 244 246);
  border-radius: 8px;
  background: rgb(249 250 251);
  padding: 12px;
}

:global(.dark) .summary-card {
  border-color: rgb(55 65 81);
  background: rgb(31 41 55);
}
</style>

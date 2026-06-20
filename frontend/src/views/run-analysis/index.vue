<script setup lang="ts">
import { fetchRunAnalysis } from '@/service/api/resource';
import { type ECOption, useEcharts } from '@/hooks/common/echarts';

defineOptions({
  name: 'RunAnalysis'
});

const data = ref<Api.AgentCenter.RunAnalysis | null>(null);
const loading = ref(false);

const numberFormatter = new Intl.NumberFormat('zh-CN');
const compactFormatter = new Intl.NumberFormat('zh-CN', {
  notation: 'compact',
  maximumFractionDigits: 1
});

type MetricTone = 'blue' | 'emerald' | 'amber' | 'rose' | 'violet';

interface MetricCard {
  label: string;
  value: string;
  caption: string;
  icon: string;
  tone: MetricTone;
}

const toneClassMap: Record<MetricTone, { icon: string; text: string; bar: string }> = {
  blue: {
    icon: 'bg-blue-50 text-blue-600 dark:bg-blue-500/10 dark:text-blue-300',
    text: 'text-blue-600 dark:text-blue-300',
    bar: 'bg-blue-500'
  },
  emerald: {
    icon: 'bg-emerald-50 text-emerald-600 dark:bg-emerald-500/10 dark:text-emerald-300',
    text: 'text-emerald-600 dark:text-emerald-300',
    bar: 'bg-emerald-500'
  },
  amber: {
    icon: 'bg-amber-50 text-amber-600 dark:bg-amber-500/10 dark:text-amber-300',
    text: 'text-amber-600 dark:text-amber-300',
    bar: 'bg-amber-500'
  },
  rose: {
    icon: 'bg-rose-50 text-rose-600 dark:bg-rose-500/10 dark:text-rose-300',
    text: 'text-rose-600 dark:text-rose-300',
    bar: 'bg-rose-500'
  },
  violet: {
    icon: 'bg-violet-50 text-violet-600 dark:bg-violet-500/10 dark:text-violet-300',
    text: 'text-violet-600 dark:text-violet-300',
    bar: 'bg-violet-500'
  }
};

const emptyData: Api.AgentCenter.RunAnalysis = {
  agentCount: 0,
  runCount: 0,
  successCount: 0,
  successRate: 0,
  failureCount: 0,
  failureRate: 0,
  avgDurationMs: 0,
  hotAgents: [],
  dailyTrends: [],
  cost: {
    tokenUsage: 0,
    modelCost: 0,
    toolCost: 0
  }
};

const analysis = computed(() => data.value ?? emptyData);
const avgDurationSeconds = computed(() => analysis.value.avgDurationMs / 1000);
const totalCost = computed(() => analysis.value.cost.modelCost + analysis.value.cost.toolCost);
const tokenPerRun = computed(() => (analysis.value.runCount ? analysis.value.cost.tokenUsage / analysis.value.runCount : 0));
const costPerRun = computed(() => (analysis.value.runCount ? totalCost.value / analysis.value.runCount : 0));
const modelCostRatio = computed(() => (totalCost.value ? (analysis.value.cost.modelCost / totalCost.value) * 100 : 0));
const toolCostRatio = computed(() => Math.max(0, 100 - modelCostRatio.value));
const hotAgentMax = computed(() => Math.max(...analysis.value.hotAgents.map(item => item.callCount), 0));
const trendData = computed(() => {
  const source = analysis.value.dailyTrends || [];
  if (source.length > 0) return source;

  const today = new Date();
  return Array.from({ length: 7 }, (_, index) => {
    const date = new Date(today);
    date.setDate(today.getDate() - (6 - index));
    return {
      date: date.toISOString().slice(0, 10),
      label: `${date.getMonth() + 1}/${date.getDate()}`,
      calls: 0,
      success: 0,
      failed: 0
    };
  });
});
const resultPieData = computed(() => [
  { name: '成功调用', value: analysis.value.successCount || Math.round(analysis.value.runCount * (analysis.value.successRate / 100)) },
  { name: '失败调用', value: analysis.value.failureCount || Math.round(analysis.value.runCount * (analysis.value.failureRate / 100)) }
]);
const hotAgentChartData = computed(() => analysis.value.hotAgents.slice(0, 6).reverse());
const healthScore = computed(() => {
  const durationPenalty = Math.min(Math.max(avgDurationSeconds.value - 5, 0) * 2, 20);
  return Math.max(0, Math.round(analysis.value.successRate - durationPenalty));
});
const isEmpty = computed(() => !loading.value && !analysis.value.runCount && !analysis.value.agentCount);

const metrics = computed<MetricCard[]>(() => [
  {
    label: 'Agent 覆盖',
    value: formatNumber(analysis.value.agentCount),
    caption: '已纳入运行统计的 Agent 数量',
    icon: 'carbon:bot',
    tone: 'blue'
  },
  {
    label: '总调用量',
    value: formatNumber(analysis.value.runCount),
    caption: `单 Agent 平均 ${formatNumber(averagePerAgent.value)} 次`,
    icon: 'carbon:flow-stream',
    tone: 'violet'
  },
  {
    label: '成功率',
    value: `${formatPercent(analysis.value.successRate)}%`,
    caption: `失败率 ${formatPercent(analysis.value.failureRate)}%`,
    icon: 'carbon:checkmark-outline',
    tone: 'emerald'
  },
  {
    label: '平均耗时',
    value: `${avgDurationSeconds.value.toFixed(1)}s`,
    caption: durationLevel.value,
    icon: 'carbon:time',
    tone: 'amber'
  },
  {
    label: '总成本',
    value: formatCurrency(totalCost.value),
    caption: `单次 ${formatCurrency(costPerRun.value)}`,
    icon: 'carbon:currency-dollar',
    tone: 'rose'
  }
]);

const averagePerAgent = computed(() => {
  return analysis.value.agentCount ? Math.round(analysis.value.runCount / analysis.value.agentCount) : 0;
});

const durationLevel = computed(() => {
  if (avgDurationSeconds.value <= 3) return '响应速度优秀';
  if (avgDurationSeconds.value <= 8) return '响应速度稳定';
  return '建议排查慢调用';
});

const efficiencyItems = computed(() => [
  {
    label: '健康分',
    value: healthScore.value,
    suffix: '/100',
    width: healthScore.value,
    tone: 'emerald' as MetricTone
  },
  {
    label: '成功调用占比',
    value: formatPercent(analysis.value.successRate),
    suffix: '%',
    width: analysis.value.successRate,
    tone: 'blue' as MetricTone
  },
  {
    label: '失败调用占比',
    value: formatPercent(analysis.value.failureRate),
    suffix: '%',
    width: analysis.value.failureRate,
    tone: 'rose' as MetricTone
  }
]);

function buildTrendChartOptions(): ECOption {
  return {
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'line', lineStyle: { color: '#94a3b8' } }
    },
    legend: {
      top: 0,
      right: 8,
      itemWidth: 10,
      itemHeight: 10,
      textStyle: { color: '#64748b' }
    },
    grid: {
      left: 8,
      right: 16,
      top: 34,
      bottom: 8,
      containLabel: true
    },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: trendData.value.map(item => item.label),
      axisTick: { show: false },
      axisLine: { lineStyle: { color: '#e5e7eb' } },
      axisLabel: { color: '#64748b' }
    },
    yAxis: {
      type: 'value',
      min: 0,
      minInterval: 1,
      axisLabel: { color: '#64748b' },
      splitLine: { lineStyle: { color: '#eef2f7' } }
    },
    series: [
      {
        name: '调用量',
        type: 'line',
        smooth: true,
        symbolSize: 7,
        lineStyle: { width: 3, color: '#2563eb' },
        itemStyle: { color: '#2563eb' },
        areaStyle: {
          color: {
            type: 'linear',
            x: 0,
            y: 0,
            x2: 0,
            y2: 1,
            colorStops: [
              { offset: 0, color: 'rgba(37, 99, 235, 0.2)' },
              { offset: 1, color: 'rgba(37, 99, 235, 0.02)' }
            ]
          }
        },
        data: trendData.value.map(item => item.calls)
      },
      {
        name: '成功',
        type: 'line',
        smooth: true,
        symbolSize: 6,
        lineStyle: { width: 2, color: '#10b981' },
        itemStyle: { color: '#10b981' },
        data: trendData.value.map(item => item.success)
      },
      {
        name: '失败',
        type: 'line',
        smooth: true,
        symbolSize: 6,
        lineStyle: { width: 2, color: '#f43f5e' },
        itemStyle: { color: '#f43f5e' },
        data: trendData.value.map(item => item.failed)
      }
    ]
  };
}

function buildResultPieOptions(): ECOption {
  return {
    tooltip: {
      trigger: 'item',
      formatter: '{b}<br/>{c} 次 ({d}%)'
    },
    legend: {
      bottom: 0,
      left: 'center',
      itemWidth: 10,
      itemHeight: 10,
      textStyle: { color: '#64748b' }
    },
    series: [
      {
        name: '调用结果',
        type: 'pie',
        radius: ['52%', '72%'],
        center: ['50%', '44%'],
        avoidLabelOverlap: true,
        label: {
          formatter: '{d}%',
          color: '#64748b'
        },
        itemStyle: {
          borderColor: '#fff',
          borderWidth: 2
        },
        color: ['#10b981', '#f43f5e'],
        data: resultPieData.value
      }
    ]
  };
}

function buildHotAgentBarOptions(): ECOption {
  return {
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' }
    },
    grid: {
      left: 8,
      right: 20,
      top: 10,
      bottom: 8,
      containLabel: true
    },
    xAxis: {
      type: 'value',
      minInterval: 1,
      axisLabel: { color: '#64748b' },
      splitLine: { lineStyle: { color: '#eef2f7' } }
    },
    yAxis: {
      type: 'category',
      data: hotAgentChartData.value.map(item => item.name),
      axisTick: { show: false },
      axisLine: { show: false },
      axisLabel: {
        color: '#64748b',
        width: 96,
        overflow: 'truncate'
      }
    },
    series: [
      {
        name: '调用次数',
        type: 'bar',
        barWidth: 12,
        itemStyle: {
          color: '#2563eb',
          borderRadius: [0, 6, 6, 0]
        },
        data: hotAgentChartData.value.map(item => item.callCount)
      }
    ]
  };
}

const { domRef: trendChartRef, updateOptions: updateTrendChart } = useEcharts(buildTrendChartOptions, {
  onRender: chart => {
    chart.hideLoading();
    chart.setOption(buildTrendChartOptions(), true);
  }
});
const { domRef: resultPieRef, updateOptions: updateResultPie } = useEcharts(buildResultPieOptions, {
  onRender: chart => {
    chart.hideLoading();
    chart.setOption(buildResultPieOptions(), true);
  }
});
const { domRef: hotAgentBarRef, updateOptions: updateHotAgentBar } = useEcharts(buildHotAgentBarOptions, {
  onRender: chart => {
    chart.hideLoading();
    chart.setOption(buildHotAgentBarOptions(), true);
  }
});

async function loadData() {
  loading.value = true;
  try {
    const res = await fetchRunAnalysis();
    if (!res.error && res.data) data.value = res.data;
    await refreshCharts();
  } finally {
    loading.value = false;
  }
}

async function refreshCharts() {
  await nextTick();
  await Promise.all([
    updateTrendChart(() => buildTrendChartOptions()),
    updateResultPie(() => buildResultPieOptions()),
    updateHotAgentBar(() => buildHotAgentBarOptions())
  ]);
}

function formatNumber(value: number) {
  return numberFormatter.format(Math.round(value || 0));
}

function formatCompact(value: number) {
  return compactFormatter.format(value || 0);
}

function formatPercent(value: number) {
  return Number(value || 0).toFixed(1).replace(/\.0$/, '');
}

function formatCurrency(value: number) {
  return `$${Number(value || 0).toFixed(2)}`;
}

function getAgentShare(callCount: number) {
  if (!hotAgentMax.value) return 0;
  return Math.max(8, Math.round((callCount / hotAgentMax.value) * 100));
}

onMounted(loadData);
</script>

<template>
  <div class="run-analysis-page h-full overflow-y-auto bg-gray-50 dark:bg-gray-900">
    <div class="space-y-5 p-4 sm:p-6">
      <header class="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 class="text-2xl font-bold text-gray-900 dark:text-white">运行分析</h1>
          <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
            追踪 Agent 调用健康、效率与成本表现，辅助定位运行瓶颈。
          </p>
        </div>
        <NButton :loading="loading" secondary class="w-fit" @click="loadData">
          <template #icon>
            <icon-carbon:renew />
          </template>
          刷新
        </NButton>
      </header>

      <NSpin :show="loading">
        <div class="space-y-5">
          <section class="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-5">
            <article
              v-for="metric in metrics"
              :key="metric.label"
              class="metric-card rounded-lg border border-gray-100 bg-white p-4 shadow-sm transition-colors duration-200 hover:border-blue-200 dark:border-gray-700 dark:bg-gray-800 dark:hover:border-blue-500/50"
            >
              <div class="mb-4 flex items-center justify-between gap-3">
                <span class="text-sm font-medium text-gray-500 dark:text-gray-400">{{ metric.label }}</span>
                <span class="flex h-9 w-9 items-center justify-center rounded-lg" :class="toneClassMap[metric.tone].icon">
                  <SvgIcon :icon="metric.icon" class="text-lg" />
                </span>
              </div>
              <div class="text-2xl font-bold leading-none text-gray-950 dark:text-white">{{ metric.value }}</div>
              <div class="mt-3 text-xs text-gray-500 dark:text-gray-400">{{ metric.caption }}</div>
            </article>
          </section>

          <section class="grid grid-cols-1 gap-5 xl:grid-cols-[minmax(0,1.35fr)_minmax(340px,0.65fr)]">
            <article class="rounded-lg border border-gray-100 bg-white p-5 shadow-sm dark:border-gray-700 dark:bg-gray-800">
              <div class="mb-4 flex items-center justify-between gap-3">
                <div>
                  <h2 class="text-base font-semibold text-gray-900 dark:text-white">调用趋势</h2>
                  <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">近 7 天调用量、成功量与失败量分布。</p>
                </div>
                <icon-carbon:chart-line-data class="text-xl text-gray-400" />
              </div>
              <div ref="trendChartRef" class="h-[300px] w-full"></div>
            </article>

            <article class="rounded-lg border border-gray-100 bg-white p-5 shadow-sm dark:border-gray-700 dark:bg-gray-800">
              <div class="mb-4 flex items-center justify-between gap-3">
                <div>
                  <h2 class="text-base font-semibold text-gray-900 dark:text-white">调用结果占比</h2>
                  <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">成功调用与失败调用的结构。</p>
                </div>
                <icon-carbon:chart-pie class="text-xl text-gray-400" />
              </div>
              <div ref="resultPieRef" class="h-[300px] w-full"></div>
            </article>
          </section>

          <section class="grid grid-cols-1 gap-5 xl:grid-cols-[minmax(0,1.3fr)_minmax(360px,0.7fr)]">
            <article class="rounded-lg border border-gray-100 bg-white p-5 shadow-sm dark:border-gray-700 dark:bg-gray-800">
              <div class="mb-5 flex items-center justify-between gap-4">
                <div>
                  <h2 class="text-base font-semibold text-gray-900 dark:text-white">运行健康</h2>
                  <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">成功率、失败率与平均耗时组合评估。</p>
                </div>
                <div class="text-right">
                  <div class="text-3xl font-bold text-emerald-600 dark:text-emerald-300">{{ healthScore }}</div>
                  <div class="text-xs text-gray-500 dark:text-gray-400">健康分</div>
                </div>
              </div>

              <div class="space-y-4">
                <div v-for="item in efficiencyItems" :key="item.label">
                  <div class="mb-2 flex items-center justify-between text-sm">
                    <span class="font-medium text-gray-700 dark:text-gray-200">{{ item.label }}</span>
                    <span :class="toneClassMap[item.tone].text">{{ item.value }}{{ item.suffix }}</span>
                  </div>
                  <div class="h-2 rounded-full bg-gray-100 dark:bg-gray-700">
                    <div
                      class="h-full rounded-full transition-all duration-300"
                      :class="toneClassMap[item.tone].bar"
                      :style="{ width: `${Math.min(item.width, 100)}%` }"
                    ></div>
                  </div>
                </div>
              </div>
            </article>

            <article class="rounded-lg border border-gray-100 bg-white p-5 shadow-sm dark:border-gray-700 dark:bg-gray-800">
              <div class="mb-5">
                <h2 class="text-base font-semibold text-gray-900 dark:text-white">成本效率</h2>
                <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">Token、模型费用与工具费用的通用成本口径。</p>
              </div>

              <div class="grid grid-cols-2 gap-4 border-b border-gray-100 pb-4 dark:border-gray-700">
                <div>
                  <div class="text-xs text-gray-500 dark:text-gray-400">Token 消耗</div>
                  <div class="mt-1 text-xl font-semibold text-gray-900 dark:text-white">{{ formatCompact(analysis.cost.tokenUsage) }}</div>
                </div>
                <div>
                  <div class="text-xs text-gray-500 dark:text-gray-400">单次 Token</div>
                  <div class="mt-1 text-xl font-semibold text-gray-900 dark:text-white">{{ formatNumber(tokenPerRun) }}</div>
                </div>
              </div>

              <div class="mt-4 space-y-3 text-sm">
                <div>
                  <div class="mb-2 flex justify-between">
                    <span class="text-gray-600 dark:text-gray-300">模型费用</span>
                    <span class="font-medium text-gray-900 dark:text-white">{{ formatCurrency(analysis.cost.modelCost) }}</span>
                  </div>
                  <div class="h-2 rounded-full bg-gray-100 dark:bg-gray-700">
                    <div class="h-full rounded-full bg-blue-500" :style="{ width: `${modelCostRatio}%` }"></div>
                  </div>
                </div>
                <div>
                  <div class="mb-2 flex justify-between">
                    <span class="text-gray-600 dark:text-gray-300">工具调用费用</span>
                    <span class="font-medium text-gray-900 dark:text-white">{{ formatCurrency(analysis.cost.toolCost) }}</span>
                  </div>
                  <div class="h-2 rounded-full bg-gray-100 dark:bg-gray-700">
                    <div class="h-full rounded-full bg-amber-500" :style="{ width: `${toolCostRatio}%` }"></div>
                  </div>
                </div>
              </div>
            </article>
          </section>

          <section class="grid grid-cols-1 gap-5 xl:grid-cols-[minmax(360px,0.8fr)_minmax(0,1.2fr)]">
            <article class="rounded-lg border border-gray-100 bg-white p-5 shadow-sm dark:border-gray-700 dark:bg-gray-800">
              <div class="mb-5 flex items-center justify-between">
                <div>
                  <h2 class="text-base font-semibold text-gray-900 dark:text-white">热门 Agent</h2>
                  <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">按调用次数排序，识别高频能力。</p>
                </div>
                <icon-carbon:chart-line-data class="text-xl text-gray-400" />
              </div>

              <div v-if="analysis.hotAgents.length" class="space-y-4">
                <div v-for="(agent, index) in analysis.hotAgents" :key="agent.name" class="space-y-2">
                  <div class="flex items-center justify-between gap-3 text-sm">
                    <div class="min-w-0 flex items-center gap-3">
                      <span class="flex h-6 w-6 shrink-0 items-center justify-center rounded bg-gray-100 text-xs font-semibold text-gray-500 dark:bg-gray-700 dark:text-gray-300">
                        {{ index + 1 }}
                      </span>
                      <span class="truncate font-medium text-gray-800 dark:text-gray-100">{{ agent.name }}</span>
                    </div>
                    <span class="font-semibold text-gray-900 dark:text-white">{{ formatNumber(agent.callCount) }}</span>
                  </div>
                  <div class="h-2 rounded-full bg-gray-100 dark:bg-gray-700">
                    <div class="h-full rounded-full bg-blue-500" :style="{ width: `${getAgentShare(agent.callCount)}%` }"></div>
                  </div>
                </div>
              </div>

              <div v-else class="flex min-h-48 flex-col items-center justify-center text-gray-400">
                <icon-carbon:chart-line-data class="mb-3 text-4xl" />
                <span class="text-sm">暂无热门 Agent 数据</span>
              </div>
            </article>

            <article class="rounded-lg border border-gray-100 bg-white p-5 shadow-sm dark:border-gray-700 dark:bg-gray-800">
              <div class="mb-5 flex items-center justify-between">
                <div>
                  <h2 class="text-base font-semibold text-gray-900 dark:text-white">热门 Agent 分布</h2>
                  <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">Top Agent 调用量横向对比。</p>
                </div>
                <icon-carbon:chart-bar class="text-xl text-gray-400" />
              </div>
              <div ref="hotAgentBarRef" class="h-[300px] w-full"></div>
            </article>
          </section>

          <section>
            <article class="rounded-lg border border-gray-100 bg-white p-5 shadow-sm dark:border-gray-700 dark:bg-gray-800">
              <div class="mb-5">
                <h2 class="text-base font-semibold text-gray-900 dark:text-white">通用指标模块</h2>
                <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">当前接口可稳定支持的运行分析指标口径。</p>
              </div>

              <div class="grid grid-cols-1 gap-3 md:grid-cols-2">
                <div class="metric-module">
                  <div class="font-medium text-gray-900 dark:text-white">规模指标</div>
                  <p>Agent 覆盖数、总调用量、单 Agent 平均调用量。</p>
                </div>
                <div class="metric-module">
                  <div class="font-medium text-gray-900 dark:text-white">质量指标</div>
                  <p>成功率、失败率、健康分，适合做告警阈值。</p>
                </div>
                <div class="metric-module">
                  <div class="font-medium text-gray-900 dark:text-white">效率指标</div>
                  <p>平均耗时、响应速度分层，辅助排查慢调用。</p>
                </div>
                <div class="metric-module">
                  <div class="font-medium text-gray-900 dark:text-white">成本指标</div>
                  <p>Token 消耗、模型费用、工具费用、单次成本。</p>
                </div>
              </div>
            </article>
          </section>

          <div
            v-if="isEmpty"
            class="rounded-lg border border-dashed border-gray-200 bg-white p-10 text-center text-gray-500 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-400"
          >
            <icon-carbon:analytics class="mx-auto mb-3 text-4xl" />
            <div class="text-sm">暂无运行数据，产生 Agent 调用后会自动形成分析指标。</div>
          </div>
        </div>
      </NSpin>
    </div>
  </div>
</template>

<style scoped lang="scss">
.run-analysis-page {
  .metric-card,
  .metric-module {
    min-height: 128px;
  }

  .metric-module {
    border: 1px solid rgb(243 244 246);
    border-radius: 8px;
    padding: 14px;
    background: rgb(249 250 251);

    p {
      margin-top: 8px;
      color: rgb(107 114 128);
      font-size: 13px;
      line-height: 1.6;
    }
  }
}

.dark {
  .run-analysis-page {
    .metric-module {
      border-color: rgb(55 65 81);
      background: rgb(17 24 39);

      p {
        color: rgb(156 163 175);
      }
    }
  }
}
</style>

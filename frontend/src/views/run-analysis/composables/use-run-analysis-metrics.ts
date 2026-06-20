import { type Ref, computed } from 'vue';

type MetricTone = 'blue' | 'emerald' | 'amber' | 'rose' | 'violet';

export interface MetricCard {
  label: string;
  value: string;
  caption: string;
  icon: string;
  tone: MetricTone;
}

interface MetricFormatters {
  formatNumber: (value: number) => string;
  formatPercent: (value: number) => string;
  formatCurrency: (value: number) => string;
}

export function createEmptyRunAnalysis(): Api.AgentCenter.RunAnalysis {
  return {
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
}

export function useRunAnalysisMetrics(
  source: Ref<Api.AgentCenter.RunAnalysis | null>,
  { formatNumber, formatPercent, formatCurrency }: MetricFormatters
) {
  const emptyData = createEmptyRunAnalysis();
  const analysis = computed(() => source.value ?? emptyData);
  const avgDurationSeconds = computed(() => analysis.value.avgDurationMs / 1000);
  const totalCost = computed(() => analysis.value.cost.modelCost + analysis.value.cost.toolCost);
  const tokenPerRun = computed(() => (analysis.value.runCount ? analysis.value.cost.tokenUsage / analysis.value.runCount : 0));
  const costPerRun = computed(() => (analysis.value.runCount ? totalCost.value / analysis.value.runCount : 0));
  const modelCostRatio = computed(() => (totalCost.value ? (analysis.value.cost.modelCost / totalCost.value) * 100 : 0));
  const toolCostRatio = computed(() => Math.max(0, 100 - modelCostRatio.value));
  const hotAgentMax = computed(() => Math.max(...analysis.value.hotAgents.map(item => item.callCount), 0));
  const averagePerAgent = computed(() => (analysis.value.agentCount ? Math.round(analysis.value.runCount / analysis.value.agentCount) : 0));
  const durationLevel = computed(() => {
    if (avgDurationSeconds.value <= 3) return '响应速度优秀';
    if (avgDurationSeconds.value <= 8) return '响应速度稳定';
    return '建议排查慢调用';
  });
  const trendData = computed(() => {
    const sourceData = analysis.value.dailyTrends || [];
    if (sourceData.length > 0) return sourceData;

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
  const isEmpty = computed(() => !analysis.value.runCount && !analysis.value.agentCount);
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

  return {
    analysis,
    avgDurationSeconds,
    totalCost,
    tokenPerRun,
    costPerRun,
    modelCostRatio,
    toolCostRatio,
    hotAgentMax,
    averagePerAgent,
    durationLevel,
    trendData,
    resultPieData,
    hotAgentChartData,
    healthScore,
    isEmpty,
    metrics,
    efficiencyItems
  };
}

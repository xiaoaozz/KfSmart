<script setup lang="ts">
import { fetchRunAnalysis } from '@/service/api/agent-center';

const data = ref<Api.AgentCenter.RunAnalysis | null>(null);

async function loadData() {
  const res = await fetchRunAnalysis();
  if (!res.error && res.data) data.value = res.data;
}

onMounted(loadData);
</script>

<template>
  <div class="h-full overflow-y-auto bg-gray-50 px-8 py-6 dark:bg-gray-900">
    <h1 class="mb-6 text-2xl font-bold text-gray-900 dark:text-white">运行分析</h1>
    <div class="mb-6 grid grid-cols-5 gap-5">
      <div class="rounded-xl border border-gray-100 bg-white px-5 py-4 shadow-sm dark:border-gray-700 dark:bg-gray-800">
        <div class="text-2xl font-bold">{{ data?.agentCount || 0 }}</div>
        <div class="text-sm text-gray-500">Agent数量</div>
      </div>
      <div class="rounded-xl border border-gray-100 bg-white px-5 py-4 shadow-sm dark:border-gray-700 dark:bg-gray-800">
        <div class="text-2xl font-bold">{{ data?.runCount || 0 }}</div>
        <div class="text-sm text-gray-500">调用次数</div>
      </div>
      <div class="rounded-xl border border-gray-100 bg-white px-5 py-4 shadow-sm dark:border-gray-700 dark:bg-gray-800">
        <div class="text-2xl font-bold">{{ data?.successRate || 0 }}%</div>
        <div class="text-sm text-gray-500">成功率</div>
      </div>
      <div class="rounded-xl border border-gray-100 bg-white px-5 py-4 shadow-sm dark:border-gray-700 dark:bg-gray-800">
        <div class="text-2xl font-bold">{{ data?.failureRate || 0 }}%</div>
        <div class="text-sm text-gray-500">失败率</div>
      </div>
      <div class="rounded-xl border border-gray-100 bg-white px-5 py-4 shadow-sm dark:border-gray-700 dark:bg-gray-800">
        <div class="text-2xl font-bold">{{ ((data?.avgDurationMs || 0) / 1000).toFixed(1) }}s</div>
        <div class="text-sm text-gray-500">平均耗时</div>
      </div>
    </div>
    <div class="grid grid-cols-2 gap-5">
      <div class="rounded-xl border border-gray-100 bg-white p-5 shadow-sm dark:border-gray-700 dark:bg-gray-800">
        <h2 class="mb-4 text-base font-semibold">热门Agent</h2>
        <div v-for="agent in data?.hotAgents || []" :key="agent.name" class="mb-3 flex justify-between rounded-lg bg-gray-50 px-3 py-2 dark:bg-gray-900">
          <span>{{ agent.name }}</span>
          <span>{{ agent.callCount }}</span>
        </div>
      </div>
      <div class="rounded-xl border border-gray-100 bg-white p-5 shadow-sm dark:border-gray-700 dark:bg-gray-800">
        <h2 class="mb-4 text-base font-semibold">成本分析</h2>
        <div class="space-y-3 text-sm">
          <div class="flex justify-between"><span>Token消耗</span><span>{{ data?.cost.tokenUsage || 0 }}</span></div>
          <div class="flex justify-between"><span>模型费用</span><span>${{ (data?.cost.modelCost || 0).toFixed(2) }}</span></div>
          <div class="flex justify-between"><span>工具调用费用</span><span>${{ (data?.cost.toolCost || 0).toFixed(2) }}</span></div>
        </div>
      </div>
    </div>
  </div>
</template>

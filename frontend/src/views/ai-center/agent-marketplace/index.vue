<script setup lang="ts">
import { NButton, NTag } from 'naive-ui';
import { fetchAgentMarketplace, fetchCopyAgentWorkflow } from '@/service/api/agent-center';

const items = ref<Api.AgentCenter.MarketplaceItem[]>([]);
const installingId = ref('');
const categories = ['知识问答', '客服', 'HR', '财务', '法务', '研发'];

async function loadData() {
  const { error, data } = await fetchAgentMarketplace();
  if (!error && data) items.value = data;
}

async function installAgent(workflowId: string) {
  installingId.value = workflowId;
  try {
    const { error } = await fetchCopyAgentWorkflow(workflowId);
    if (!error) {
      window.$message?.success('已安装到工作流列表');
      await loadData();
    }
  } finally {
    installingId.value = '';
  }
}

onMounted(loadData);
</script>

<template>
  <div class="h-full overflow-y-auto bg-gray-50 px-8 py-6 dark:bg-gray-900">
    <div class="mb-6 flex items-center justify-between">
      <h1 class="text-2xl font-bold text-gray-900 dark:text-white">Agent广场</h1>
      <div class="flex gap-2">
        <NButton>发布</NButton>
        <NButton>导入</NButton>
        <NButton>导出</NButton>
      </div>
    </div>
    <div class="mb-5 flex gap-2">
      <NTag v-for="category in categories" :key="category" type="info" size="small">{{ category }}</NTag>
    </div>
    <div class="grid grid-cols-3 gap-5">
      <div v-for="item in items" :key="item.workflowId" class="rounded-xl border border-gray-100 bg-white p-5 shadow-sm dark:border-gray-700 dark:bg-gray-800">
        <div class="mb-2 flex items-center justify-between">
          <h2 class="text-base font-semibold text-gray-900 dark:text-white">{{ item.name }}</h2>
          <NTag size="small" type="success">{{ item.category }}</NTag>
        </div>
        <p class="mb-4 h-10 text-sm text-gray-500">{{ item.description }}</p>
        <div class="mb-4 text-xs text-gray-400">安装量 {{ item.installCount }}</div>
        <div class="flex gap-2">
          <NButton size="small" type="primary" :loading="installingId === item.workflowId" @click="installAgent(item.workflowId)">安装</NButton>
          <NButton size="small" :loading="installingId === item.workflowId" @click="installAgent(item.workflowId)">复制</NButton>
        </div>
      </div>
    </div>
  </div>
</template>

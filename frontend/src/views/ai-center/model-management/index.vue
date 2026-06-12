<script setup lang="tsx">
import { NButton, NDataTable, NTag } from 'naive-ui';
import type { DataTableColumns } from 'naive-ui';
import { fetchAgentModels } from '@/service/api/agent-center';

const loading = ref(false);
const models = ref<Api.AgentCenter.ModelConfig[]>([]);

const columns: DataTableColumns<Api.AgentCenter.ModelConfig> = [
  { key: 'name', title: '配置名称', width: 150 },
  { key: 'modelName', title: '模型', width: 160, ellipsis: { tooltip: true } },
  { key: 'provider', title: '供应商', width: 110 },
  { key: 'authType', title: '认证方式', width: 110 },
  { key: 'apiUrl', title: 'API地址', ellipsis: { tooltip: true } },
  {
    key: 'status',
    title: '状态',
    width: 100,
    render: row => <NTag type={row.active ? 'success' : 'default'} size="small">{row.active ? '激活中' : '可用'}</NTag>
  },
  {
    key: 'params',
    title: '生成参数',
    width: 160,
    render: row => `T ${row.temperature ?? '-'} / ${row.maxTokens ?? '-'} / P ${row.topP ?? '-'}`
  },
  {
    key: 'updatedAt',
    title: '更新时间',
    width: 150,
    render: row => row.updatedAt ? dayjs(row.updatedAt).format('YYYY-MM-DD HH:mm') : '-'
  }
];

async function loadData() {
  loading.value = true;
  try {
    const { error, data } = await fetchAgentModels();
    if (!error && data) {
      models.value = data;
    }
  } finally {
    loading.value = false;
  }
}

onMounted(loadData);
</script>

<template>
  <div class="h-full overflow-y-auto bg-gray-50 px-8 py-6 dark:bg-gray-900">
    <div class="mb-6 flex items-center justify-between">
      <div>
        <h1 class="text-2xl font-bold text-gray-900 dark:text-white">模型管理</h1>
        <p class="mt-1 text-sm text-gray-500">模型配置来自 API Key 管理，工作流 LLM 节点会使用当前激活模型</p>
      </div>
      <NButton @click="loadData">刷新</NButton>
    </div>
    <div class="rounded-xl border border-gray-100 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-800">
      <NDataTable :columns="columns" :data="models" :loading="loading" :pagination="{ pageSize: 10 }" size="small" striped />
    </div>
  </div>
</template>

<script setup lang="tsx">
import { NButton, NDataTable, NInput, NModal, NPagination, NSelect, NTag } from 'naive-ui';
import { fetchDeleteMcpTool, fetchMcpTools, fetchSaveMcpTool } from '@/service/api/agent-center';

const visible = ref(false);
const form = ref<Partial<Api.AgentCenter.McpTool>>({ name: '', type: 'MCP', status: '在线', endpoint: '', authType: 'API Key', apiKey: '', description: '' });

const { columns, data, getData, loading, mobilePagination } = useTable({
  apiFn: fetchMcpTools,
  columns: () => [
    { key: 'name', title: '名称', width: 140 },
    { key: 'type', title: '类型', width: 90 },
    { key: 'status', title: '状态', width: 90, render: row => <NTag type={row.status === '在线' ? 'success' : 'warning'} size="small">{row.status}</NTag> },
    { key: 'endpoint', title: 'Endpoint', ellipsis: { tooltip: true } },
    { key: 'authType', title: '认证方式', width: 120 },
    { key: 'callCount', title: '调用量', width: 90, align: 'center' },
    {
      key: 'operate',
      title: '操作',
      width: 120,
      render: row => (
        <div class="flex gap-2">
          <NButton text size="small" type="primary" onClick={() => editTool(row)}>编辑</NButton>
          <NButton text size="small" type="error" onClick={() => deleteTool(row.toolId)}>删除</NButton>
        </div>
      )
    }
  ]
});

function openCreate() {
  form.value = { name: '', type: 'MCP', status: '在线', endpoint: '', authType: 'API Key', apiKey: '', description: '' };
  visible.value = true;
}

function editTool(row: NaiveUI.TableDataWithIndex<Api.AgentCenter.McpTool>) {
  form.value = { ...row, apiKey: '' };
  visible.value = true;
}

async function saveTool() {
  const { error } = await fetchSaveMcpTool(form.value);
  if (!error) {
    visible.value = false;
    window.$message?.success('保存成功');
    await getData();
  }
}

function deleteTool(toolId: string) {
  window.$dialog?.warning({
    title: '删除MCP工具',
    content: '确认删除该工具配置吗？',
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      const { error } = await fetchDeleteMcpTool(toolId);
      if (!error) {
        window.$message?.success('删除成功');
        await getData();
      }
    }
  });
}
</script>

<template>
  <div class="h-full overflow-y-auto bg-gray-50 px-8 py-6 dark:bg-gray-900">
    <div class="mb-6 flex items-center justify-between">
      <h1 class="text-2xl font-bold text-gray-900 dark:text-white">MCP工具</h1>
      <NButton type="primary" @click="openCreate"><template #icon><icon-carbon:add /></template>新增工具</NButton>
    </div>
    <div class="overflow-hidden rounded-xl border border-gray-100 bg-white shadow-sm dark:border-gray-700 dark:bg-gray-800">
      <NDataTable :columns="columns" :data="data" :loading="loading" :pagination="false" size="small" striped />
      <div class="flex justify-end border-t border-gray-100 px-4 py-3 dark:border-gray-700">
        <NPagination v-bind="mobilePagination" />
      </div>
    </div>
    <NModal v-model:show="visible" preset="dialog" title="工具配置" positive-text="保存" negative-text="取消" @positive-click="saveTool">
      <div class="space-y-3">
        <NInput v-model:value="form.name" placeholder="名称" />
        <NInput v-model:value="form.endpoint" placeholder="Endpoint" />
        <NSelect v-model:value="form.authType" :options="['API Key', 'Bearer Token', '无认证'].map(v => ({ label: v, value: v }))" />
        <NInput v-model:value="form.apiKey" placeholder="API Key（留空则不修改）" />
        <NInput v-model:value="form.description" type="textarea" placeholder="描述" />
      </div>
    </NModal>
  </div>
</template>

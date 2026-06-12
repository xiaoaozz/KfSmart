<script setup lang="tsx">
import { NButton, NDataTable, NInput, NModal, NSelect, NTag } from 'naive-ui';
import type { DataTableColumns } from 'naive-ui';
import { fetchDeletePromptTemplate, fetchPromptTemplates, fetchSavePromptTemplate } from '@/service/api/agent-center';

const loading = ref(false);
const keyword = ref('');
const prompts = ref<Api.AgentCenter.PromptTemplate[]>([]);
const visible = ref(false);
const form = ref<Partial<Api.AgentCenter.PromptTemplate>>({ name: '', category: '知识问答', version: 'v1.0', content: '', variables: 'query,documents', status: '启用' });
const categories = ['知识问答', '客服助手', 'HR助手', '销售助手', '法务助手'];

const columns: DataTableColumns<Api.AgentCenter.PromptTemplate> = [
  { key: 'name', title: '名称', width: 180 },
  { key: 'category', title: '分类', width: 120 },
  { key: 'version', title: '版本', width: 90 },
  { key: 'variables', title: '变量', ellipsis: { tooltip: true } },
  { key: 'status', title: '状态', width: 90, render: row => <NTag type="success" size="small">{row.status}</NTag> },
  { key: 'updatedAt', title: '更新时间', width: 150, render: row => row.updatedAt ? dayjs(row.updatedAt).format('YYYY-MM-DD HH:mm') : '-' },
  {
    key: 'operate',
    title: '操作',
    width: 120,
    render: row => (
      <div class="flex gap-2">
        <NButton text size="small" type="primary" onClick={() => editPrompt(row)}>编辑</NButton>
        <NButton text size="small" type="error" onClick={() => deletePrompt(row.templateId)}>删除</NButton>
      </div>
    )
  }
];

function openCreate() {
  form.value = { name: '', category: '知识问答', version: 'v1.0', content: '', variables: 'query,documents', status: '启用' };
  visible.value = true;
}

function editPrompt(row: Api.AgentCenter.PromptTemplate) {
  form.value = { ...row };
  visible.value = true;
}

async function loadData() {
  loading.value = true;
  try {
    const { error, data } = await fetchPromptTemplates({ keyword: keyword.value || undefined, page: 1, size: 100 });
    if (!error && data) prompts.value = data.records || data.content || data.data || [];
  } finally {
    loading.value = false;
  }
}

async function savePrompt() {
  const { error } = await fetchSavePromptTemplate(form.value);
  if (!error) {
    visible.value = false;
    window.$message?.success('保存成功');
    await loadData();
  }
}

function deletePrompt(templateId: string) {
  window.$dialog?.warning({
    title: '删除Prompt',
    content: '确认删除该 Prompt 模板吗？',
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      const { error } = await fetchDeletePromptTemplate(templateId);
      if (!error) {
        window.$message?.success('删除成功');
        await loadData();
      }
    }
  });
}

onMounted(loadData);
</script>

<template>
  <div class="h-full overflow-y-auto bg-gray-50 px-8 py-6 dark:bg-gray-900">
    <div class="mb-6 flex items-center justify-between">
      <h1 class="text-2xl font-bold text-gray-900 dark:text-white">Prompt管理</h1>
      <NButton type="primary" @click="openCreate"><template #icon><icon-carbon:add /></template>新建模板</NButton>
    </div>
    <div class="mb-4 flex gap-2">
      <NInput v-model:value="keyword" clearable placeholder="搜索模板" class="max-w-280px" @keyup.enter="loadData" />
      <NButton @click="loadData">搜索</NButton>
    </div>
    <div class="rounded-xl border border-gray-100 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-800">
      <div class="mb-4 flex gap-2">
        <NTag v-for="item in categories" :key="item" type="info" size="small">{{ item }}</NTag>
      </div>
      <NDataTable :columns="columns" :data="prompts" :loading="loading" :pagination="{ pageSize: 10 }" size="small" striped />
    </div>
    <NModal v-model:show="visible" preset="dialog" title="Prompt模板" positive-text="保存" negative-text="取消" @positive-click="savePrompt">
      <div class="space-y-3">
        <NInput v-model:value="form.name" placeholder="名称" />
        <NSelect v-model:value="form.category" :options="categories.map(v => ({ label: v, value: v }))" />
        <NInput v-model:value="form.version" placeholder="版本" />
        <NInput v-model:value="form.variables" placeholder="变量" />
        <NInput v-model:value="form.content" type="textarea" placeholder="内容" />
      </div>
    </NModal>
  </div>
</template>

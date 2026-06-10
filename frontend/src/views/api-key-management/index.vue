<script setup lang="tsx">
import { NButton, NPopconfirm, NTag, NTooltip } from 'naive-ui';
import ApiKeyOperateDialog from './modules/api-key-operate-dialog.vue';

interface ApiKeyItem {
  id: number;
  name: string;
  provider: string;
  apiUrl: string;
  apiKey: string;
  modelName: string;
  authType: string;
  active: boolean;
  temperature: number;
  maxTokens: number;
  topP: number;
  remark: string;
  createdAt: string;
  updatedAt: string;
}

const loading = ref(false);
const data = ref<ApiKeyItem[]>([]);
const dialogVisible = ref(false);
const operateType = ref<'add' | 'edit'>('add');
const editingData = ref<ApiKeyItem | null>(null);
const activatingId = ref<number | null>(null);
const pageSizeOptions = [10, 50, 100];
const currentPage = ref(1);
const pageSize = ref(10);
const pagedData = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value;
  return data.value.slice(start, start + pageSize.value);
});

/** 提供商标签颜色映射 */
const providerTagType: Record<string, 'primary' | 'success' | 'warning' | 'info' | 'error' | 'default'> = {
  deepseek: 'primary',
  openai: 'success',
  qwen: 'info',
  zhipu: 'warning',
  ernie: 'warning',
  anthropic: 'error',
  other: 'default'
};

/** 提供商名称映射 */
const providerLabel: Record<string, string> = {
  deepseek: 'DeepSeek',
  openai: 'OpenAI',
  qwen: '通义千问',
  zhipu: '智谱 AI',
  ernie: '文心一言',
  anthropic: 'Anthropic',
  other: '其他'
};

/** 认证方式标签 */
const authTypeLabel: Record<string, string> = {
  bearer: 'Bearer',
  openai: 'OpenAI 兼容',
  anthropic: 'Anthropic'
};
const authTypeTagType: Record<string, 'primary' | 'success' | 'info' | 'default'> = {
  bearer: 'default',
  openai: 'success',
  anthropic: 'info'
};

async function getData() {
  loading.value = true;
  const { data: res, error } = await request<ApiKeyItem[]>({
    url: '/admin/api-keys'
  });
  if (!error && res) {
    data.value = res;
    const maxPage = Math.max(1, Math.ceil(res.length / pageSize.value));
    if (currentPage.value > maxPage) {
      currentPage.value = maxPage;
    }
  }
  loading.value = false;
}

async function handleActivate(id: number) {
  activatingId.value = id;
  const { error } = await request({ url: `/admin/api-keys/${id}/activate`, method: 'POST' });
  if (!error) {
    window.$message?.success('激活成功，模型配置已切换');
    await getData();
  }
  activatingId.value = null;
}

async function handleDelete(id: number) {
  const { error } = await request({ url: `/admin/api-keys/${id}`, method: 'DELETE' });
  if (!error) {
    window.$message?.success('删除成功');
    await getData();
  }
}

function handleAdd() {
  operateType.value = 'add';
  editingData.value = null;
  dialogVisible.value = true;
}

function handleEdit(row: ApiKeyItem) {
  operateType.value = 'edit';
  editingData.value = { ...row };
  dialogVisible.value = true;
}

function handlePageSizeChange(size: number) {
  pageSize.value = size;
  currentPage.value = 1;
}

const columns = computed(() => [
  {
    key: 'active',
    title: '状态',
    width: 80,
    align: 'center' as const,
    render: (row: ApiKeyItem) =>
      row.active ? (
        <NTag type="success" round>
          ✓ 激活中
        </NTag>
      ) : (
        <NTag type="default" round>
          未激活
        </NTag>
      )
  },
  {
    key: 'name',
    title: '配置名称',
    width: 150,
    ellipsis: { tooltip: true }
  },
  {
    key: 'provider',
    title: '提供商',
    width: 100,
    align: 'center' as const,
    render: (row: ApiKeyItem) => (
      <NTag type={providerTagType[row.provider] || 'default'} size="small">
        {providerLabel[row.provider] || row.provider}
      </NTag>
    )
  },
  {
    key: 'modelName',
    title: '模型名称',
    width: 160,
    ellipsis: { tooltip: true }
  },
  {
    key: 'authType',
    title: '认证方式',
    width: 110,
    align: 'center' as const,
    render: (row: ApiKeyItem) => (
      <NTag type={authTypeTagType[row.authType] || 'default'} size="small">
        {authTypeLabel[row.authType] || row.authType || 'Bearer'}
      </NTag>
    )
  },
  {
    key: 'apiUrl',
    title: 'API 地址',
    minWidth: 180,
    ellipsis: { tooltip: true }
  },
  {
    key: 'apiKey',
    title: 'API Key',
    width: 180,
    ellipsis: { tooltip: true },
    render: (row: ApiKeyItem) => {
      const masked = row.apiKey ? `${row.apiKey.slice(0, 6)}${'*'.repeat(8)}${row.apiKey.slice(-4)}` : '-';
      return (
        <NTooltip>
          {{
            trigger: () => <span class="font-mono text-xs text-gray-500">{masked}</span>,
            default: () => <span class="font-mono text-xs">{row.apiKey}</span>
          }}
        </NTooltip>
      );
    }
  },
  {
    key: 'params',
    title: '生成参数',
    width: 90,
    align: 'center' as const,
    render: (row: ApiKeyItem) => (
      <NTooltip>
        {{
          trigger: () => (
            <span class="cursor-pointer text-xs text-gray-500 underline-dotted underline">
              查看参数
            </span>
          ),
          default: () => (
            <div class="text-xs">
              <div>温度: {row.temperature}</div>
              <div>Max Tokens: {row.maxTokens}</div>
              <div>Top-P: {row.topP}</div>
            </div>
          )
        }}
      </NTooltip>
    )
  },
  {
    key: 'remark',
    title: '备注',
    minWidth: 100,
    ellipsis: { tooltip: true }
  },
  {
    key: 'updatedAt',
    title: '更新时间',
    width: 150,
    render: (row: ApiKeyItem) => dayjs(row.updatedAt).format('YYYY-MM-DD HH:mm')
  },
  {
    key: 'operate',
    title: '操作',
    width: 200,
    fixed: 'right' as const,
    render: (row: ApiKeyItem) => (
      <div class="flex gap-2 flex-wrap">
        {!row.active && (
          <NButton
            type="success"
            ghost
            size="small"
            loading={activatingId.value === row.id}
            onClick={() => handleActivate(row.id)}
          >
            激活
          </NButton>
        )}
        <NButton type="primary" ghost size="small" onClick={() => handleEdit(row)}>
          编辑
        </NButton>
        <NPopconfirm
          onPositiveClick={() => handleDelete(row.id)}
          positiveText="确认删除"
          negativeText="取消"
        >
          {{
            default: () => (row.active ? '当前配置正在激活中，无法删除！' : '确认删除该 API Key 配置吗？'),
            trigger: () => (
              <NButton type="error" ghost size="small" disabled={row.active}>
                删除
              </NButton>
            )
          }}
        </NPopconfirm>
      </div>
    )
  }
]);

onMounted(() => {
  getData();
});
</script>

<template>
  <div class="api-key-management-page h-full flex flex-col bg-gray-50 dark:bg-gray-900 overflow-y-auto">
    <div class="px-8 py-6 flex-1 min-h-0">
      <!-- 标题 -->
      <h1 class="text-2xl font-bold text-gray-900 dark:text-white mb-6">API Key 配置列表</h1>

      <!-- 说明 Alert -->
      <NAlert type="info" :show-icon="true" class="mb-4">
        <template #header>API Key 管理说明</template>
        管理平台问答模型的 API Key 配置。每次只能有一个配置处于
        <NTag type="success" size="small" round>激活中</NTag>
        状态，激活的配置将被聊天助手用于生成回答。修改激活配置后立即生效。
      </NAlert>

      <!-- 工具栏 -->
      <div class="flex items-center justify-end gap-3 mb-4">
        <NButton :loading="loading" @click="getData">
          <template #icon>
            <icon-carbon:renew />
          </template>
          刷新
        </NButton>
        <NButton type="primary" @click="handleAdd">
          <template #icon>
            <icon-carbon:add />
          </template>
          新增配置
        </NButton>
      </div>

      <!-- 表格 -->
      <div class="bg-white dark:bg-gray-800 rounded-xl shadow-sm border border-gray-100 dark:border-gray-700 overflow-hidden">
        <NDataTable
          :columns="columns"
          :data="pagedData"
          size="small"
          :scroll-x="1460"
          :loading="loading"
          :pagination="false"
          :row-key="(row: ApiKeyItem) => row.id"
          :row-class-name="(row: ApiKeyItem) => (row.active ? 'bg-green-50 dark:bg-green-900/20' : '')"
        />
        <div class="flex justify-end px-4 py-3 border-t border-gray-100 dark:border-gray-700">
          <NPagination
            v-model:page="currentPage"
            :page-count="Math.max(1, Math.ceil(data.length / pageSize))"
            :page-size="pageSize"
            :page-sizes="pageSizeOptions"
            show-size-picker
            @update:page-size="handlePageSizeChange"
          />
        </div>
      </div>
    </div>

    <ApiKeyOperateDialog
      v-model:visible="dialogVisible"
      :operate-type="operateType"
      :row-data="editingData"
      @submitted="getData"
    />
  </div>
</template>

<style scoped></style>

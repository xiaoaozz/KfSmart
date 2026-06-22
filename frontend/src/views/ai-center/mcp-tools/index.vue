<script setup lang="tsx">
import {
  NButton,
  NDescriptions,
  NDescriptionsItem,
  NDrawer,
  NDrawerContent,
  NForm,
  NFormItem,
  NInput,
  NSelect,
  NSpace,
  NSwitch,
  NTag,
  NTooltip
} from 'naive-ui';
import type { FormInst, FormRules } from 'naive-ui';
import {
  fetchDeleteMcpTool,
  fetchMcpTools,
  fetchSaveMcpTool,
  fetchTestMcpTool
} from '@/service/api/resource';
import FavoriteButton from '@/components/common/favorite-button.vue';
import ListPagination from '@/components/common/list-pagination.vue';

type McpToolForm = Partial<Api.AgentCenter.McpTool> & {
  enabled?: boolean;
};

const defaultSchema = JSON.stringify(
  {
    type: 'object',
    properties: {
      query: {
        type: 'string',
        description: '请求参数或查询内容'
      }
    },
    required: ['query']
  },
  null,
  2
);

const defaultArguments = JSON.stringify({ query: 'ping' }, null, 2);

const visible = ref(false);
const keyword = ref('');
const statusFilter = ref<string | null>(null);
const saving = ref(false);
const testing = ref(false);
const testArguments = ref(defaultArguments);
const testResult = ref('');
const formRef = ref<FormInst | null>(null);
const form = ref<McpToolForm>(createForm());

const requestModeOptions = [
  { label: 'MCP JSON-RPC', value: 'MCP_JSON_RPC' },
  { label: 'HTTP 兼容', value: 'HTTP_COMPAT' }
];

const authOptions = [
  { label: '无认证', value: '无认证' },
  { label: 'API Key', value: 'API Key' },
  { label: 'Bearer Token', value: 'Bearer Token' }
];

const statusOptions = [
  { label: '在线', value: '在线' },
  { label: '离线', value: '离线' }
];

const rules: FormRules = {
  name: { required: true, message: '请输入工具名称', trigger: ['blur', 'input'] },
  toolName: { required: true, message: '请输入 MCP 工具名', trigger: ['blur', 'input'] },
  endpoint: { required: true, message: '请输入 Endpoint', trigger: ['blur', 'input'] },
  inputSchema: {
    trigger: ['blur'],
    validator: (_rule, value: string) => {
      if (!value) return true;
      try {
        const parsed = JSON.parse(value);
        return parsed?.type === 'object';
      } catch {
        return new Error('请输入合法的 JSON Schema');
      }
    }
  }
};

const { data, getData, getDataByPage, loading, pagination, mobilePagination, updateSearchParams } = useTable({
  apiFn: fetchMcpTools,
  apiParams: { page: 1, size: 10 },
  columns: () => [],
  showTotal: true
});

const filteredData = computed(() => {
  if (!statusFilter.value) return data.value;
  return data.value.filter(item => item.status === statusFilter.value);
});

function createForm(): McpToolForm {
  return {
    name: '',
    type: 'MCP',
    status: '在线',
    enabled: true,
    toolName: '',
    requestMode: 'MCP_JSON_RPC',
    protocolVersion: '2024-11-05',
    endpoint: '',
    authType: '无认证',
    authHeaderName: 'X-API-Key',
    apiKey: '',
    description: '',
    inputSchema: defaultSchema
  };
}

function normalizeToolName(value: string) {
  return value
    .trim()
    .toLowerCase()
    .replace(/[^\w\u4e00-\u9fa5-]/g, '_')
    .replace(/_+/g, '_')
    .replace(/^_|_$/g, '');
}

function openCreate() {
  form.value = createForm();
  testArguments.value = defaultArguments;
  testResult.value = '';
  visible.value = true;
}

function openEdit(row: Api.AgentCenter.McpTool) {
  form.value = {
    ...row,
    enabled: row.status === '在线',
    apiKey: '',
    inputSchema: row.inputSchema || defaultSchema,
    authHeaderName: row.authHeaderName || 'X-API-Key',
    protocolVersion: row.protocolVersion || '2024-11-05',
    requestMode: row.requestMode || 'MCP_JSON_RPC'
  };
  testArguments.value = defaultArguments;
  testResult.value = '';
  visible.value = true;
}

function handleNameBlur() {
  if (!form.value.toolName && form.value.name) {
    form.value.toolName = normalizeToolName(form.value.name);
  }
}

async function searchTools() {
  updateSearchParams({
    page: 1,
    keyword: keyword.value || undefined
  });
  await getDataByPage(1);
}

async function handlePageChange(page: number) {
  updateSearchParams({
    page,
    size: pagination.pageSize!
  });
  await getDataByPage(page);
}

async function handlePageSizeChange(size: number) {
  pagination.pageSize = size;
  updateSearchParams({
    page: 1,
    size
  });
  await getDataByPage(1);
}

async function saveTool() {
  await formRef.value?.validate();
  saving.value = true;
  const payload = {
    ...form.value,
    status: form.value.enabled ? '在线' : '离线'
  };
  const { error } = await fetchSaveMcpTool(payload);
  saving.value = false;
  if (!error) {
    visible.value = false;
    window.$message?.success('保存成功');
    await getData();
  }
}

function parseJsonInput(value: string) {
  if (!value.trim()) return {};
  return JSON.parse(value);
}

async function quickTest(row: Api.AgentCenter.McpTool) {
  const { error, data: result } = await fetchTestMcpTool(row.toolId, { query: 'ping' });
  if (!error && result) {
    window.$message?.[result.success ? 'success' : 'error'](result.message);
    await getData();
  }
}

async function testCurrentTool() {
  if (!form.value.toolId) {
    window.$message?.warning('请先保存工具配置后再测试');
    return;
  }
  let args: Record<string, any>;
  try {
    args = parseJsonInput(testArguments.value);
  } catch {
    window.$message?.error('测试参数不是合法 JSON');
    return;
  }
  testing.value = true;
  const { error, data: result } = await fetchTestMcpTool(form.value.toolId, args);
  testing.value = false;
  if (!error && result) {
    testResult.value = JSON.stringify(result.result ?? result.message, null, 2);
    window.$message?.[result.success ? 'success' : 'error'](result.message);
    await getData();
  }
}

function deleteTool(toolId: string) {
  window.$dialog?.warning({
    title: '删除MCP工具',
    content: '删除后 Agent 和工作流中已绑定的工具将无法继续调用，确认删除吗？',
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
  <div class="h-full flex flex-col bg-[#f5f7fa] dark:bg-[#101014]">
    <div class="border-b border-gray-100 bg-white px-5 py-3 dark:border-gray-700 dark:bg-[#18181c]">
      <div class="flex flex-wrap items-center justify-between gap-3">
        <NSpace :size="8">
          <NInput
            v-model:value="keyword"
            clearable
            placeholder="搜索名称、类型或工具名"
            class="w-260px"
            size="small"
            @keyup.enter="searchTools"
          >
            <template #prefix><icon-carbon:search /></template>
          </NInput>
          <NSelect
            v-model:value="statusFilter"
            clearable
            placeholder="全部状态"
            :options="statusOptions"
            class="w-130px"
            size="small"
          />
          <NButton size="small" @click="searchTools">
            <template #icon><icon-carbon:search /></template>
            查询
          </NButton>
        </NSpace>
        <NButton size="small" type="primary" @click="openCreate">
          <template #icon><icon-carbon:add /></template>
          新增工具
        </NButton>
      </div>
    </div>

    <div class="min-h-0 flex-1 overflow-y-auto p-4">
      <div v-if="loading" class="grid grid-cols-1 gap-3 xl:grid-cols-2 2xl:grid-cols-3">
        <div v-for="item in 6" :key="item" class="h-220px animate-pulse rounded-lg border border-gray-100 bg-gray-50 dark:border-gray-700 dark:bg-gray-800/80">
          <div class="h-full p-4">
            <div class="mb-4 h-5 w-2/5 rounded bg-gray-200 dark:bg-gray-700"></div>
            <div class="mb-3 h-4 w-4/5 rounded bg-gray-100 dark:bg-gray-700"></div>
            <div class="mb-6 h-4 w-3/5 rounded bg-gray-100 dark:bg-gray-700"></div>
            <div class="grid grid-cols-3 gap-2">
              <div class="h-12 rounded bg-gray-100 dark:bg-gray-700"></div>
              <div class="h-12 rounded bg-gray-100 dark:bg-gray-700"></div>
              <div class="h-12 rounded bg-gray-100 dark:bg-gray-700"></div>
            </div>
          </div>
        </div>
      </div>

      <div v-else-if="filteredData.length" class="grid grid-cols-1 gap-3 xl:grid-cols-2 2xl:grid-cols-3">
        <article
          v-for="item in filteredData"
          :key="item.toolId"
          class="flex min-h-220px flex-col rounded-lg border border-gray-100 bg-white p-4 shadow-sm transition-colors duration-200 hover:border-primary-200 hover:bg-primary-50/30 dark:border-gray-700 dark:bg-gray-800 dark:hover:border-primary-600/60 dark:hover:bg-gray-800/80"
        >
          <div class="mb-3 flex items-start justify-between gap-3">
            <div class="min-w-0">
              <div class="flex items-center gap-2">
                <div class="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-primary-50 text-primary-600 dark:bg-primary-950 dark:text-primary-300">
                  <icon-carbon:tool-kit class="text-lg" />
                </div>
                <div class="min-w-0">
                  <h2 class="truncate text-base font-semibold text-gray-900 dark:text-white">{{ item.name }}</h2>
                  <div class="mt-0.5 truncate font-mono text-xs text-gray-500 dark:text-gray-400">{{ item.toolName || item.toolId }}</div>
                </div>
              </div>
            </div>
            <div class="flex shrink-0 items-center gap-1">
              <FavoriteButton
                type="mcp_tool"
                :target-id="item.toolId"
                :title="item.name"
                :description="item.description"
                :meta="item.toolName || item.requestMode || ''"
              />
              <NTag :type="item.status === '在线' ? 'success' : 'warning'" size="small" :bordered="false">
                {{ item.status }}
              </NTag>
            </div>
          </div>

          <p class="mb-3 line-clamp-2 min-h-38px text-sm text-gray-600 dark:text-gray-300">
            {{ item.description || '暂无描述' }}
          </p>

          <div class="mb-3 rounded-md bg-gray-50 px-3 py-2 dark:bg-gray-900/60">
            <div class="mb-1 flex items-center gap-1 text-xs text-gray-500 dark:text-gray-400">
              <icon-carbon:link />
              Endpoint
            </div>
            <div class="truncate font-mono text-xs text-gray-700 dark:text-gray-200" :title="item.endpoint">
              {{ item.endpoint || '未配置' }}
            </div>
          </div>

          <div class="mb-4 grid grid-cols-3 gap-2">
            <div class="rounded-md border border-gray-100 px-3 py-2 dark:border-gray-700">
              <div class="text-xs text-gray-500 dark:text-gray-400">模式</div>
              <div class="mt-1 truncate text-sm font-medium text-gray-900 dark:text-white">
                {{ item.requestMode === 'MCP_JSON_RPC' ? 'MCP' : 'HTTP' }}
              </div>
            </div>
            <div class="rounded-md border border-gray-100 px-3 py-2 dark:border-gray-700">
              <div class="text-xs text-gray-500 dark:text-gray-400">认证</div>
              <div class="mt-1 truncate text-sm font-medium text-gray-900 dark:text-white">{{ item.authType || '无认证' }}</div>
            </div>
            <div class="rounded-md border border-gray-100 px-3 py-2 dark:border-gray-700">
              <div class="text-xs text-gray-500 dark:text-gray-400">调用</div>
              <div class="mt-1 text-sm font-medium text-gray-900 dark:text-white">{{ item.callCount || 0 }}</div>
            </div>
          </div>

          <div class="mt-auto flex items-center justify-between border-t border-gray-100 pt-3 dark:border-gray-700">
            <div class="min-w-0">
              <NTooltip v-if="item.lastTestStatus">
                <template #trigger>
                  <NTag size="small" :type="item.lastTestStatus === '成功' ? 'success' : 'error'" :bordered="false">
                    {{ item.lastTestStatus }}
                  </NTag>
                </template>
                {{ item.lastTestMessage || '暂无详情' }}
              </NTooltip>
              <span v-else class="text-xs text-gray-400">未测试</span>
            </div>
            <div class="flex shrink-0 items-center gap-2">
              <NButton text size="small" type="primary" @click="openEdit(item)">
                <template #icon><icon-carbon:edit /></template>
                编辑
              </NButton>
              <NButton text size="small" @click="quickTest(item)">
                <template #icon><icon-carbon:play /></template>
                测试
              </NButton>
              <NButton text size="small" type="error" @click="deleteTool(item.toolId)">
                <template #icon><icon-carbon:trash-can /></template>
                删除
              </NButton>
            </div>
          </div>
        </article>
      </div>

      <div v-else class="flex min-h-360px flex-col items-center justify-center text-center">
        <div class="mb-3 flex h-12 w-12 items-center justify-center rounded-lg bg-gray-100 text-gray-400 dark:bg-gray-800 dark:text-gray-500">
          <icon-carbon:search class="text-xl" />
        </div>
        <div class="text-sm font-medium text-gray-800 dark:text-gray-100">暂无 MCP 工具</div>
        <div class="mt-1 text-xs text-gray-500 dark:text-gray-400">调整筛选条件，或新增一个工具配置</div>
      </div>
    </div>

    <ListPagination
      :page="mobilePagination.page"
      :page-size="mobilePagination.pageSize"
      :item-count="mobilePagination.itemCount"
      :disabled="mobilePagination.disabled"
      :page-slot="mobilePagination.pageSlot"
      class="dark:bg-[#18181c]"
      @update:page="handlePageChange"
      @update:page-size="handlePageSizeChange"
    />

    <NDrawer v-model:show="visible" :width="720" placement="right">
      <NDrawerContent :title="form.toolId ? '编辑 MCP 工具' : '新增 MCP 工具'" closable>
        <NForm ref="formRef" :model="form" :rules="rules" label-placement="top" size="small">
          <div class="grid grid-cols-1 gap-x-4 md:grid-cols-2">
            <NFormItem label="工具名称" path="name">
              <NInput v-model:value="form.name" placeholder="如：企业微信通知" @blur="handleNameBlur" />
            </NFormItem>
            <NFormItem label="MCP 工具名" path="toolName">
              <NInput v-model:value="form.toolName" placeholder="tools/call params.name" />
            </NFormItem>
            <NFormItem label="请求模式" path="requestMode">
              <NSelect v-model:value="form.requestMode" :options="requestModeOptions" />
            </NFormItem>
            <NFormItem label="状态">
              <div class="flex h-8 items-center gap-2">
                <NSwitch v-model:value="form.enabled" />
                <span class="text-sm text-gray-600 dark:text-gray-300">{{ form.enabled ? '在线' : '离线' }}</span>
              </div>
            </NFormItem>
            <NFormItem label="Endpoint" path="endpoint" class="md:col-span-2">
              <NInput v-model:value="form.endpoint" placeholder="https://example.com/mcp" />
            </NFormItem>
            <NFormItem label="协议版本" path="protocolVersion">
              <NInput v-model:value="form.protocolVersion" placeholder="2024-11-05" />
            </NFormItem>
            <NFormItem label="认证方式" path="authType">
              <NSelect v-model:value="form.authType" :options="authOptions" />
            </NFormItem>
            <NFormItem v-if="form.authType === 'API Key'" label="Header 名称" path="authHeaderName">
              <NInput v-model:value="form.authHeaderName" placeholder="X-API-Key" />
            </NFormItem>
            <NFormItem v-if="form.authType !== '无认证'" label="密钥" path="apiKey">
              <NInput v-model:value="form.apiKey" type="password" show-password-on="click" placeholder="留空则不修改" />
            </NFormItem>
            <NFormItem label="描述" path="description" class="md:col-span-2">
              <NInput v-model:value="form.description" type="textarea" :autosize="{ minRows: 2, maxRows: 4 }" placeholder="说明工具能力、适用场景和返回内容" />
            </NFormItem>
            <NFormItem label="输入 JSON Schema" path="inputSchema" class="md:col-span-2">
              <NInput v-model:value="form.inputSchema" type="textarea" :autosize="{ minRows: 8, maxRows: 14 }" />
            </NFormItem>
          </div>
        </NForm>

        <div class="mt-2 rounded-lg border border-gray-100 p-4 dark:border-gray-700">
          <div class="mb-3 flex items-center justify-between">
            <div class="text-sm font-semibold text-gray-800 dark:text-gray-100">连接测试</div>
            <NButton size="small" :loading="testing" :disabled="!form.toolId" @click="testCurrentTool">
              <template #icon><icon-carbon:play /></template>
              测试
            </NButton>
          </div>
          <NInput v-model:value="testArguments" type="textarea" :autosize="{ minRows: 4, maxRows: 8 }" />
          <NInput v-if="testResult" v-model:value="testResult" class="mt-3" type="textarea" readonly :autosize="{ minRows: 4, maxRows: 8 }" />
          <NDescriptions v-if="form.lastTestStatus" class="mt-3" size="small" :column="1" bordered>
            <NDescriptionsItem label="最近状态">{{ form.lastTestStatus }}</NDescriptionsItem>
            <NDescriptionsItem label="最近结果">{{ form.lastTestMessage }}</NDescriptionsItem>
          </NDescriptions>
        </div>

        <template #footer>
          <div class="flex justify-end gap-2">
            <NButton @click="visible = false">取消</NButton>
            <NButton type="primary" :loading="saving" @click="saveTool">保存</NButton>
          </div>
        </template>
      </NDrawerContent>
    </NDrawer>
  </div>
</template>

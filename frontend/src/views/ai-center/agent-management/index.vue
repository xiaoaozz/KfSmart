<script setup lang="tsx">
import {
  NButton,
  NDataTable,
  NEmpty,
  NInput,
  NInputNumber,
  NModal,
  NPagination,
  NSelect,
  NSpace,
  NTag,
  NDivider,
  NTooltip
} from 'naive-ui';
import {
  fetchAgentWorkflowStats,
  fetchAgentWorkflows,
  fetchCopyAgentWorkflow,
  fetchDebugAgentWorkflow,
  fetchDeleteAgentWorkflow,
  fetchMcpTools,
  fetchPromptTemplates,
  fetchPublishAgentWorkflow,
  fetchSaveAgentWorkflow,
  fetchAgentModels
} from '@/service/api/agent-center';
import { fetchGetKnowledgeBases } from '@/service/api/knowledge-base';

type SelectOption = {
  label: string;
  value: string;
  disabled?: boolean;
};

// ─── 统计数据 ───
const stats = ref<Api.AgentCenter.WorkflowStats>({
  agentCount: 0,
  runCount: 0,
  successRate: 100,
  avgDurationMs: 0
});

// ─── 选项数据 ───
const knowledgeBaseOptions = ref<SelectOption[]>([]);
const promptOptions = ref<SelectOption[]>([]);
const mcpToolOptions = ref<SelectOption[]>([]);
const modelOptions = ref<SelectOption[]>([]);

// ─── 页面状态 ───
const activeTab = ref('list');
const keyword = ref('');
const saving = ref(false);
const debugLoading = ref(false);
const createVisible = ref(false);
const createForm = ref({ name: '', type: '知识问答', description: '' });

// ─── Agent 详情 ───
const selectedAgentId = ref('');
const selectedAgent = ref<Api.AgentCenter.Workflow | null>(null);

const agentDetail = reactive({
  workflowId: '',
  name: '',
  description: '',
  type: '',
  status: '',
  ownerName: '',
  permissionScope: '组织内',
  tags: '',
  knowledgeBases: '',
  promptRefs: '',
  mcpTools: '',
  models: '',
  systemPrompt: '',
  temperature: 0.7,
  topP: 0.8,
  maxTokens: 4000,
  selectedModel: '',
  memoryTypes: [] as string[],
  avatarEmoji: '🤖'
});

// ─── 调试 ───
const testQuery = ref('');
const debugResult = ref<Api.AgentCenter.DebugResult | null>(null);

// ─── Agent 类型选项 ───
const agentTypeOptions = [
  '知识问答', '合同审查', '数据分析', '客户服务', '代码助手', '文本生成'
].map(v => ({ label: v, value: v }));

const memoryOptions = [
  { label: '会话记忆', value: '会话记忆' },
  { label: '长期记忆', value: '长期记忆' },
  { label: '用户画像', value: '用户画像' }
];

const permissionOptions = [
  { label: '公开', value: '公开' },
  { label: '组织内', value: '组织内' },
  { label: '指定部门', value: '指定部门' }
];

const avatarOptions = ['🤖', '👩‍💼', '⚖️', '📊', '💬', '🔬', '📝', '🛡️'];

// ─── 列表 ───
const { columns, data: agents, getData: getAgentData, getDataByPage, loading, mobilePagination, updateSearchParams } = useTable({
  apiFn: fetchAgentWorkflows,
  immediate: false,
  columns: () => [
    {
      key: 'name',
      title: 'Agent名称',
      width: 200,
      render: row => (
        <div class="flex items-center gap-2">
          <div class="w-7 h-7 rounded-lg bg-indigo-50 dark:bg-indigo-900/30 flex items-center justify-center flex-shrink-0 text-base">
            {agentDetail.avatarEmoji}
          </div>
          <NTooltip placement="top" trigger="hover" style="max-width: 300px">
            {{
              trigger: () => (
                <div style="overflow: hidden; white-space: nowrap; text-overflow: ellipsis; max-width: 130px; font-weight: 500;">
                  {row.name}
                </div>
              ),
              default: () => row.name
            }}
          </NTooltip>
        </div>
      )
    },
    {
      key: 'type',
      title: '类型',
      width: 110,
      render: row => <span class="text-gray-600 dark:text-gray-400">{row.type || '-'}</span>
    },
    {
      key: 'status',
      title: '状态',
      width: 100,
      align: 'center',
      titleAlign: 'center',
      render: row => (
        <NTag
          type={row.status === '运行中' ? 'success' : 'default'}
          size="small"
          round
        >
          {row.status === '运行中' ? '运行中' : row.status || '已停止'}
        </NTag>
      )
    },
    {
      key: 'callCount',
      title: '调用量',
      width: 90,
      align: 'center',
      titleAlign: 'center',
      render: row => <span class="text-gray-700 dark:text-gray-300">{row.callCount?.toLocaleString() ?? '-'}</span>
    },
    {
      key: 'successRate',
      title: '成功率',
      width: 90,
      align: 'center',
      titleAlign: 'center',
      render: row => (
        <span class={row.status === '运行中' ? 'text-emerald-600 font-medium' : 'text-gray-400'}>
          {row.status === '运行中' ? `${row.successRate ?? 90}%` : '-'}
        </span>
      )
    },
    {
      key: 'avgDuration',
      title: '平均时间',
      width: 90,
      align: 'center',
      titleAlign: 'center',
      render: row => (
        <span class="text-gray-600 dark:text-gray-400">
          {row.status === '运行中' ? `${((row.avgDurationMs ?? 1500) / 1000).toFixed(1)}s` : '-'}
        </span>
      )
    },
    {
      key: 'updatedAt',
      title: '更新时间',
      width: 150,
      align: 'center',
      titleAlign: 'center',
      render: row => (
        <span class="text-gray-500 text-xs">
          {row.updatedAt ? dayjs(row.updatedAt).format('YYYY-MM-DD HH:mm') : '-'}
        </span>
      )
    },
    {
      key: 'operate',
      title: '操作',
      width: 180,
      align: 'center',
      titleAlign: 'center',
      render: row => (
        <div class="flex items-center justify-center gap-2">
          <NButton text size="small" type="primary" onClick={() => editAgent(row)}>
            编辑
          </NButton>
          <NDivider vertical style="margin: 0" />
          <NButton text size="small" onClick={() => copyAgent(row.workflowId)}>
            复制
          </NButton>
          <NDivider vertical style="margin: 0" />
          <NButton text size="small" type="success" onClick={() => publishAgent(row.workflowId)}>
            发布
          </NButton>
          <NDivider vertical style="margin: 0" />
          <NButton text size="small" type="error" onClick={() => deleteAgent(row.workflowId)}>
            删除
          </NButton>
        </div>
      )
    }
  ]
});

// ─── 数据加载 ───
async function loadData() {
  const [statsRes, kbRes, promptRes, toolRes, modelRes] = await Promise.all([
    fetchAgentWorkflowStats(),
    fetchGetKnowledgeBases({ page: 1, size: 100 }),
    fetchPromptTemplates({ page: 1, size: 100 }),
    fetchMcpTools({ page: 1, size: 100 }),
    fetchAgentModels(),
    getAgentData()
  ]);

  if (!statsRes.error && statsRes.data) stats.value = statsRes.data;

  if (!kbRes.error && kbRes.data) {
    knowledgeBaseOptions.value = getPageRecords<Api.KnowledgeBase.KnowledgeBaseInfo>(kbRes.data).map(item => ({
      label: item.name,
      value: item.name
    }));
  }
  if (!promptRes.error && promptRes.data) {
    promptOptions.value = getPageRecords<Api.AgentCenter.PromptTemplate>(promptRes.data).map(item => ({
      label: `${item.name} ${item.version || ''}`.trim(),
      value: item.name
    }));
  }
  if (!toolRes.error && toolRes.data) {
    mcpToolOptions.value = getPageRecords<Api.AgentCenter.McpTool>(toolRes.data).map(item => ({
      label: item.name,
      value: item.name
    }));
  }
  if (!modelRes.error && modelRes.data) {
    modelOptions.value = modelRes.data.map(item => ({
      label: item.modelName || item.name,
      value: item.modelName || item.name
    }));
  }
}

function getPageRecords<T>(data: Api.Common.PaginatingQueryRecord<T> | { records?: T[]; content?: T[]; data?: T[] }): T[] {
  return data.records || data.content || data.data || [];
}

async function searchAgents() {
  updateSearchParams({ keyword: keyword.value || undefined });
  await getDataByPage();
}

// ─── CRUD 操作 ───
function openCreate() {
  createForm.value = { name: '', type: '知识问答', description: '' };
  createVisible.value = true;
}

async function createAgent() {
  const { name, type, description } = createForm.value;
  if (!name.trim()) {
    window.$message?.warning('请输入 Agent 名称');
    return;
  }
  const { error, data } = await fetchSaveAgentWorkflow({
    name,
    type,
    description,
    status: '草稿'
  });
  if (!error && data) {
    createVisible.value = false;
    window.$message?.success('创建成功');
    await loadData();
    editAgent(data);
  }
}

function editAgent(row: Api.AgentCenter.Workflow) {
  selectedAgentId.value = row.workflowId;
  selectedAgent.value = { ...row };
  applyAgent(row);
  activeTab.value = 'detail';
}

function applyAgent(row: Api.AgentCenter.Workflow) {
  agentDetail.workflowId = row.workflowId;
  agentDetail.name = row.name;
  agentDetail.description = row.description;
  agentDetail.type = row.type;
  agentDetail.status = row.status;
  agentDetail.ownerName = row.ownerName || 'admin';
  agentDetail.permissionScope = row.permissionScope || '组织内';
  agentDetail.tags = row.tags || '';
  agentDetail.knowledgeBases = row.knowledgeBases || '';
  agentDetail.promptRefs = row.promptRefs || '';
  agentDetail.mcpTools = row.mcpTools || '';
  agentDetail.models = row.models || '';
  agentDetail.selectedModel = (row.models || '').split(',')[0] || '';
  agentDetail.temperature = 0.7;
  agentDetail.topP = 0.8;
  agentDetail.maxTokens = 4000;
  agentDetail.systemPrompt = '';
  agentDetail.memoryTypes = ['会话记忆'];
}

async function saveAgent() {
  if (!agentDetail.workflowId) return;
  saving.value = true;
  try {
    const { error, data } = await fetchSaveAgentWorkflow({
      workflowId: agentDetail.workflowId,
      name: agentDetail.name,
      description: agentDetail.description,
      type: agentDetail.type,
      status: agentDetail.status,
      ownerName: agentDetail.ownerName,
      permissionScope: agentDetail.permissionScope,
      tags: agentDetail.tags,
      knowledgeBases: agentDetail.knowledgeBases,
      promptRefs: agentDetail.promptRefs,
      mcpTools: agentDetail.mcpTools,
      models: agentDetail.models
    });
    if (!error && data) {
      window.$message?.success('保存成功');
      await loadData();
    }
  } finally {
    saving.value = false;
  }
}

async function copyAgent(workflowId: string) {
  const { error } = await fetchCopyAgentWorkflow(workflowId);
  if (!error) {
    window.$message?.success('复制成功');
    await loadData();
  }
}

async function publishAgent(workflowId: string) {
  const { error } = await fetchPublishAgentWorkflow(workflowId);
  if (!error) {
    window.$message?.success('发布成功');
    await loadData();
  }
}

async function deleteAgent(workflowId: string) {
  window.$dialog?.warning({
    title: '删除 Agent',
    content: '确认删除该 Agent 吗？',
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      const { error } = await fetchDeleteAgentWorkflow(workflowId);
      if (!error) {
        window.$message?.success('删除成功');
        if (selectedAgentId.value === workflowId) {
          selectedAgentId.value = '';
          selectedAgent.value = null;
          activeTab.value = 'list';
        }
        await loadData();
      }
    }
  });
}

// ─── 调试 ───
async function runDebug() {
  if (!agentDetail.workflowId) {
    window.$message?.warning('请先选择一个 Agent');
    return;
  }
  debugLoading.value = true;
  try {
    const { error, data } = await fetchDebugAgentWorkflow(agentDetail.workflowId, {
      query: testQuery.value
    });
    if (!error && data) {
      debugResult.value = data;
    }
  } finally {
    debugLoading.value = false;
  }
}

// ─── 多选辅助 ───
function splitComma(val: string) {
  return val ? val.split(',').filter(Boolean) : [];
}

function joinComma(arr: string[]) {
  return arr.join(',');
}

onMounted(() => {
  loadData();
});
</script>

<template>
  <div class="h-full overflow-y-auto bg-[#f5f6fa] dark:bg-gray-900">
    <div class="px-8 py-6">
      <!-- 页面标题 -->
      <div class="mb-5 flex items-center justify-between">
        <h1 class="text-xl font-semibold text-gray-900 dark:text-white">Agent 管理</h1>
      </div>

      <!-- 统计卡片（仅列表页显示） -->
      <div v-if="activeTab === 'list'" class="mb-5 grid grid-cols-4 gap-4">
        <div class="rounded-xl border border-gray-100 bg-white px-6 py-5 shadow-sm dark:border-gray-700 dark:bg-gray-800">
          <div class="mb-1 text-3xl font-bold text-gray-900 dark:text-white">{{ stats.agentCount }}</div>
          <div class="text-sm text-gray-500">Agent 数量</div>
        </div>
        <div class="rounded-xl border border-gray-100 bg-white px-6 py-5 shadow-sm dark:border-gray-700 dark:bg-gray-800">
          <div class="mb-1 text-3xl font-bold text-gray-900 dark:text-white">{{ stats.runCount.toLocaleString() }}</div>
          <div class="text-sm text-gray-500">累计调用次数</div>
        </div>
        <div class="rounded-xl border border-gray-100 bg-white px-6 py-5 shadow-sm dark:border-gray-700 dark:bg-gray-800">
          <div class="mb-1 text-3xl font-bold text-gray-900 dark:text-white">{{ stats.successRate }}%</div>
          <div class="text-sm text-gray-500">任务成功率</div>
        </div>
        <div class="rounded-xl border border-gray-100 bg-white px-6 py-5 shadow-sm dark:border-gray-700 dark:bg-gray-800">
          <div class="mb-1 text-3xl font-bold text-gray-900 dark:text-white">{{ (stats.avgDurationMs / 1000).toFixed(2) }}s</div>
          <div class="text-sm text-gray-500">平均响应耗时</div>
        </div>
      </div>

      <!-- 主内容卡片 -->
      <div class="rounded-xl border border-gray-100 bg-white shadow-sm dark:border-gray-700 dark:bg-gray-800">

        <!-- ── Agent 列表 ── -->
        <div v-if="activeTab === 'list'" class="px-5 py-4">
          <!-- 搜索栏 + 操作按钮 -->
          <div class="mb-4 flex items-center justify-between gap-3">
              <NInput
                v-model:value="keyword"
                clearable
                placeholder="搜索 Agent 名称或描述"
                class="max-w-300px"
                @keyup.enter="searchAgents"
              >
                <template #prefix>
                  <icon-carbon:search class="text-gray-400" />
                </template>
              </NInput>
              <NSpace :size="10">
                <NButton secondary @click="loadData">
                  <template #icon><icon-carbon:upload /></template>
                  导入
                </NButton>
                <NButton secondary>
                  <template #icon><icon-carbon:download /></template>
                  导出
                </NButton>
                <NButton type="primary" @click="openCreate">
                  <template #icon><icon-carbon:add /></template>
                  创建 Agent
                </NButton>
              </NSpace>
            </div>

          <!-- 数据表格 -->
          <div class="rounded-xl border border-gray-100 dark:border-gray-700 overflow-hidden">
            <NDataTable
              :columns="columns"
              :data="agents"
              :loading="loading"
              :row-key="row => row.workflowId"
              :pagination="false"
              :scroll-x="1010"
              size="small"
              striped
              class="agent-table"
            />
            <div class="flex justify-end border-t border-gray-100 px-4 py-3 dark:border-gray-700">
              <NPagination v-bind="mobilePagination" />
            </div>
          </div>
        </div>

        <!-- ── Agent 详情 ── -->
        <div v-else-if="activeTab === 'detail'" class="px-5 py-4">
          <div v-if="selectedAgentId" class="pb-6">
              <!-- 详情顶栏 -->
              <div class="mb-6 flex items-center justify-between">
                <div class="flex items-center gap-4">
                  <div class="flex h-14 w-14 items-center justify-center rounded-2xl bg-indigo-50 text-3xl shadow-sm dark:bg-indigo-900/30">
                    {{ agentDetail.avatarEmoji }}
                  </div>
                  <div>
                    <div class="text-lg font-semibold text-gray-900 dark:text-white">{{ agentDetail.name }}</div>
                    <div class="text-sm text-gray-500">{{ agentDetail.description || '暂无描述' }}</div>
                  </div>
                </div>
                <NSpace>
                  <NButton @click="activeTab = 'list'">返回列表</NButton>
                  <NButton :loading="saving" type="primary" @click="saveAgent">保存</NButton>
                </NSpace>
              </div>

              <div class="grid grid-cols-3 gap-6">
                <!-- 左列：基础信息 + Prompt -->
                <div class="col-span-2 space-y-5">
                  <!-- 基础信息 -->
                  <div class="rounded-lg border border-gray-100 p-5 dark:border-gray-700">
                    <div class="mb-4 text-sm font-semibold text-gray-700 dark:text-gray-300">基础信息</div>
                    <div class="space-y-3">
                      <div class="grid grid-cols-2 gap-3">
                        <div>
                          <div class="mb-1 text-xs text-gray-500">Agent名称</div>
                          <NInput v-model:value="agentDetail.name" placeholder="请输入Agent名称" />
                        </div>
                        <div>
                          <div class="mb-1 text-xs text-gray-500">Agent类型</div>
                          <NSelect v-model:value="agentDetail.type" :options="agentTypeOptions" placeholder="选择类型" />
                        </div>
                      </div>
                      <div>
                        <div class="mb-1 text-xs text-gray-500">描述</div>
                        <NInput v-model:value="agentDetail.description" type="textarea" :rows="2" placeholder="描述该Agent的职责" />
                      </div>
                      <div>
                        <div class="mb-1 text-xs text-gray-500">头像</div>
                        <div class="flex flex-wrap gap-2">
                          <button
                            v-for="emoji in avatarOptions"
                            :key="emoji"
                            class="flex h-9 w-9 items-center justify-center rounded-lg border text-xl transition"
                            :class="agentDetail.avatarEmoji === emoji
                              ? 'border-indigo-400 bg-indigo-50 dark:border-indigo-500 dark:bg-indigo-900/30'
                              : 'border-gray-200 bg-white hover:border-indigo-300 dark:border-gray-600 dark:bg-gray-800'"
                            @click="agentDetail.avatarEmoji = emoji"
                          >{{ emoji }}</button>
                        </div>
                      </div>
                    </div>
                  </div>

                  <!-- System Prompt -->
                  <div class="rounded-lg border border-gray-100 p-5 dark:border-gray-700">
                    <div class="mb-4 text-sm font-semibold text-gray-700 dark:text-gray-300">System Prompt</div>
                    <NInput
                      v-model:value="agentDetail.systemPrompt"
                      type="textarea"
                      :rows="6"
                      placeholder="你是一名专业的 AI 助手，负责..."
                    />
                    <div class="mt-2 text-xs text-gray-400">支持使用 {{变量}} 引用上下文变量</div>
                  </div>

                  <!-- 模型配置 -->
                  <div class="rounded-lg border border-gray-100 p-5 dark:border-gray-700">
                    <div class="mb-4 text-sm font-semibold text-gray-700 dark:text-gray-300">模型配置</div>
                    <div class="space-y-3">
                      <div>
                        <div class="mb-1 text-xs text-gray-500">模型</div>
                        <NSelect
                          v-model:value="agentDetail.selectedModel"
                          :options="modelOptions.length ? modelOptions : [{ label: '暂无可用模型', value: '', disabled: true }]"
                          placeholder="选择模型"
                        />
                      </div>
                      <div class="grid grid-cols-3 gap-3">
                        <div>
                          <div class="mb-1 text-xs text-gray-500">Temperature</div>
                          <NInputNumber v-model:value="agentDetail.temperature" :min="0" :max="2" :step="0.1" :precision="1" class="w-full" />
                        </div>
                        <div>
                          <div class="mb-1 text-xs text-gray-500">Top P</div>
                          <NInputNumber v-model:value="agentDetail.topP" :min="0" :max="1" :step="0.05" :precision="2" class="w-full" />
                        </div>
                        <div>
                          <div class="mb-1 text-xs text-gray-500">Max Token</div>
                          <NInputNumber v-model:value="agentDetail.maxTokens" :min="1" :max="32768" class="w-full" />
                        </div>
                      </div>
                    </div>
                  </div>
                </div>

                <!-- 右列：知识库/工具/Memory/权限 -->
                <div class="space-y-5">
                  <!-- 知识库绑定 -->
                  <div class="rounded-lg border border-gray-100 p-5 dark:border-gray-700">
                    <div class="mb-4 text-sm font-semibold text-gray-700 dark:text-gray-300">知识库绑定</div>
                    <NSelect
                      :value="splitComma(agentDetail.knowledgeBases)"
                      multiple
                      :options="knowledgeBaseOptions.length ? knowledgeBaseOptions : [{ label: '暂无知识库', value: '', disabled: true }]"
                      placeholder="选择知识库"
                      @update:value="(v: string[]) => (agentDetail.knowledgeBases = joinComma(v))"
                    />
                  </div>

                  <!-- 工具绑定 -->
                  <div class="rounded-lg border border-gray-100 p-5 dark:border-gray-700">
                    <div class="mb-4 text-sm font-semibold text-gray-700 dark:text-gray-300">工具绑定</div>
                    <NSelect
                      :value="splitComma(agentDetail.mcpTools)"
                      multiple
                      :options="mcpToolOptions.length ? mcpToolOptions : [{ label: '暂无MCP工具', value: '', disabled: true }]"
                      placeholder="选择MCP工具"
                      @update:value="(v: string[]) => (agentDetail.mcpTools = joinComma(v))"
                    />
                  </div>

                  <!-- Memory -->
                  <div class="rounded-lg border border-gray-100 p-5 dark:border-gray-700">
                    <div class="mb-4 text-sm font-semibold text-gray-700 dark:text-gray-300">Memory 配置</div>
                    <NSelect
                      v-model:value="agentDetail.memoryTypes"
                      multiple
                      :options="memoryOptions"
                      placeholder="选择记忆类型"
                    />
                    <div class="mt-2 space-y-1">
                      <div
                        v-for="m in agentDetail.memoryTypes"
                        :key="m"
                        class="flex items-center gap-2 text-xs text-gray-500"
                      >
                        <div class="h-1.5 w-1.5 rounded-full bg-indigo-400"></div>
                        {{ m }}
                      </div>
                    </div>
                  </div>

                  <!-- 权限管理 -->
                  <div class="rounded-lg border border-gray-100 p-5 dark:border-gray-700">
                    <div class="mb-4 text-sm font-semibold text-gray-700 dark:text-gray-300">权限管理</div>
                    <NSelect
                      v-model:value="agentDetail.permissionScope"
                      :options="permissionOptions"
                      placeholder="选择权限范围"
                    />
                    <div class="mt-2 text-xs text-gray-400">
                      <template v-if="agentDetail.permissionScope === '公开'">所有用户均可访问</template>
                      <template v-else-if="agentDetail.permissionScope === '组织内'">仅组织成员可访问</template>
                      <template v-else>仅指定部门成员可访问</template>
                    </div>
                  </div>
                </div>
              </div>
          </div>
          <NEmpty v-else description="请先从列表中选择一个 Agent" class="py-16" />
        </div>

        <!-- ── Agent 调试 ── -->
        <div v-else-if="activeTab === 'debug'" class="px-5 py-4">
          <div v-if="selectedAgentId" class="pb-6">
              <div class="grid grid-cols-2 gap-6">
                <!-- 测试对话 -->
                <div class="space-y-4">
                  <div class="text-sm font-semibold text-gray-700 dark:text-gray-300">测试对话</div>
                  <div class="min-h-48 rounded-lg border border-gray-100 bg-gray-50 p-4 dark:border-gray-700 dark:bg-gray-900">
                    <div v-if="debugResult?.output?.answer" class="mb-3 rounded-lg bg-indigo-50 p-3 text-sm text-indigo-800 dark:bg-indigo-900/30 dark:text-indigo-200">
                      <div class="mb-1 text-xs font-medium text-indigo-500">Agent 回复</div>
                      {{ debugResult.output.answer }}
                    </div>
                    <NEmpty v-else description="发送消息开始调试" class="py-8" />
                  </div>
                  <div class="flex gap-2">
                    <NInput
                      v-model:value="testQuery"
                      placeholder="输入测试问题..."
                      clearable
                      @keyup.enter="runDebug"
                    />
                    <NButton type="primary" :loading="debugLoading" @click="runDebug">发送</NButton>
                  </div>
                </div>

                <!-- 调试信息 -->
                <div class="space-y-4">
                  <div class="text-sm font-semibold text-gray-700 dark:text-gray-300">调试信息</div>

                  <!-- Trace 链路 -->
                  <div class="rounded-lg border border-gray-100 p-4 dark:border-gray-700">
                    <div class="mb-2 text-xs font-medium text-gray-500">调用链路</div>
                    <div v-if="debugResult?.trace?.length" class="space-y-1">
                      <div
                        v-for="(step, idx) in debugResult.trace"
                        :key="idx"
                        class="flex items-center justify-between rounded-md px-3 py-2 text-xs"
                        :class="step.status === 'success' ? 'bg-emerald-50 dark:bg-emerald-900/20' : 'bg-red-50 dark:bg-red-900/20'"
                      >
                        <span class="font-medium">{{ step.name }}</span>
                        <span class="text-gray-500">{{ step.durationMs }}ms</span>
                      </div>
                    </div>
                    <div v-else class="py-4 text-center text-xs text-gray-400">暂无调用链路</div>
                  </div>

                  <!-- Token 统计 -->
                  <div class="rounded-lg border border-gray-100 p-4 dark:border-gray-700">
                    <div class="mb-2 text-xs font-medium text-gray-500">Token消耗</div>
                    <div v-if="debugResult?.tokens" class="grid grid-cols-2 gap-2">
                      <div class="text-xs text-gray-600 dark:text-gray-400">
                        <div class="text-lg font-bold text-gray-900 dark:text-white">{{ debugResult.tokens.promptTokens }}</div>
                        Prompt Token
                      </div>
                      <div class="text-xs text-gray-600 dark:text-gray-400">
                        <div class="text-lg font-bold text-gray-900 dark:text-white">{{ debugResult.tokens.completionTokens }}</div>
                        Completion Token
                      </div>
                      <div class="text-xs text-gray-600 dark:text-gray-400">
                        <div class="text-lg font-bold text-gray-900 dark:text-white">{{ debugResult.tokens.totalTokens }}</div>
                        总 Token
                      </div>
                      <div class="text-xs text-gray-600 dark:text-gray-400">
                        <div class="text-lg font-bold text-indigo-600 dark:text-indigo-400">${{ debugResult.tokens.cost.toFixed(4) }}</div>
                        Token 费用
                      </div>
                    </div>
                    <div v-else class="py-4 text-center text-xs text-gray-400">暂无 Token 数据</div>
                  </div>
                </div>
              </div>
          </div>
          <NEmpty v-else description="请先从列表中选择一个 Agent" class="py-16" />
        </div>

      </div>
    </div>

    <!-- 创建 Agent 弹窗 -->
    <NModal v-model:show="createVisible" preset="card" title="创建 Agent" class="max-w-480px">
      <div class="space-y-4">
        <div>
          <div class="mb-1 text-xs text-gray-500">Agent 名称 <span class="text-red-500">*</span></div>
          <NInput v-model:value="createForm.name" placeholder="例如：HR助手" @keyup.enter="createAgent" />
        </div>
        <div>
          <div class="mb-1 text-xs text-gray-500">Agent 类型</div>
          <NSelect v-model:value="createForm.type" :options="agentTypeOptions" placeholder="选择类型" />
        </div>
        <div>
          <div class="mb-1 text-xs text-gray-500">描述</div>
          <NInput v-model:value="createForm.description" type="textarea" :rows="2" placeholder="描述该 Agent 的职责（可选）" />
        </div>
      </div>
      <template #footer>
        <div class="flex justify-end gap-3">
          <NButton @click="createVisible = false">取消</NButton>
          <NButton type="primary" @click="createAgent">创建</NButton>
        </div>
      </template>
    </NModal>
  </div>
</template>

<style scoped lang="scss">
/* 首列左侧 padding 与知识库列表对齐 */
:deep(.agent-table) {
  th:first-child,
  td:first-child {
    padding-left: 40px !important;
  }
}
</style>

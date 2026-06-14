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
const activeModelName = ref(''); // 激活中的模型名称，作为默认选中值

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
type DebugMessage = {
  role: 'user' | 'agent';
  content: string;
  tokens?: Api.AgentCenter.DebugResult['tokens'];
  trace?: Api.AgentCenter.DebugResult['trace'];
  durationMs?: number;
  success?: boolean;
  errorMessage?: string;
};

const testQuery = ref('');
const debugMessages = ref<DebugMessage[]>([]);
const debugScrollRef = ref<HTMLElement | null>(null);

function clearDebugHistory() {
  debugMessages.value = [];
}

function newDebugSession() {
  debugMessages.value = [];
  testQuery.value = '';
  window.$message?.success('已开启新对话');
}

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
        <span class={row.callCount > 0 ? 'text-emerald-600 font-medium' : 'text-gray-400'}>
          {row.callCount > 0 ? `${row.successRate ?? 100}%` : '-'}
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
          {row.callCount > 0 ? `${((row.avgDurationMs ?? 0) / 1000).toFixed(1)}s` : '-'}
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
    // 记录激活中的模型，若当前未选择则自动选为默认值
    const activeItem = modelRes.data.find(item => item.active);
    activeModelName.value = activeItem?.modelName || activeItem?.name || '';
    if (!agentDetail.selectedModel) {
      agentDetail.selectedModel = activeModelName.value;
    }
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
  // 切换 agent 时重置调试历史
  debugMessages.value = [];
  testQuery.value = '';
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
  // 优先用保存的模型，否则回落到激活中的模型（activeModelName 在 loadData 时已设置）
  agentDetail.selectedModel = (row.models || '').split(',')[0] || activeModelName.value;
  agentDetail.temperature = row.temperature ?? 0.7;
  agentDetail.topP = row.topP ?? 0.8;
  agentDetail.maxTokens = row.maxTokens ?? 4000;
  agentDetail.systemPrompt = row.systemPrompt || '';
  agentDetail.memoryTypes = row.memoryTypes ? row.memoryTypes.split(',').filter(Boolean) : ['会话记忆'];
  agentDetail.avatarEmoji = row.avatarEmoji || '🤖';
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
      models: agentDetail.selectedModel || agentDetail.models,
      systemPrompt: agentDetail.systemPrompt,
      avatarEmoji: agentDetail.avatarEmoji,
      temperature: agentDetail.temperature,
      topP: agentDetail.topP,
      maxTokens: agentDetail.maxTokens,
      memoryTypes: agentDetail.memoryTypes.join(',')
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
  if (!testQuery.value.trim()) {
    window.$message?.warning('请输入测试问题');
    return;
  }
  const userQuery = testQuery.value.trim();
  testQuery.value = '';

  // 立即添加用户消息到列表
  debugMessages.value.push({ role: 'user', content: userQuery });
  debugLoading.value = true;

  // 构建发送给后端的历史消息（openai 格式）
  const history = debugMessages.value
    .filter(m => m.role === 'user' || m.role === 'agent')
    .slice(0, -1) // 去掉刚刚加入的当前用户消息
    .map(m => ({ role: m.role === 'user' ? 'user' : 'assistant', content: m.content }));

  try {
    const { error, data } = await fetchDebugAgentWorkflow(agentDetail.workflowId, {
      query: userQuery,
      history,
      systemPrompt: agentDetail.systemPrompt || undefined
    });
    if (!error && data) {
      debugMessages.value.push({
        role: 'agent',
        content: data.output?.answer || '工作流执行完成',
        tokens: data.tokens,
        trace: data.trace,
        durationMs: data.durationMs,
        success: data.success,
        errorMessage: data.errorMessage
      });
    } else {
      debugMessages.value.push({
        role: 'agent',
        content: '调试请求失败，请稍后重试',
        success: false
      });
    }
  } finally {
    debugLoading.value = false;
    // 滚动到底部
    nextTick(() => {
      if (debugScrollRef.value) {
        debugScrollRef.value.scrollTop = debugScrollRef.value.scrollHeight;
      }
    });
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
  <div class="h-full flex flex-col bg-[#f5f6fa] dark:bg-gray-900" :class="activeTab === 'detail' ? 'overflow-hidden' : 'overflow-y-auto'">
    <div :class="activeTab === 'detail' ? 'px-4 py-3 flex flex-col flex-1 overflow-hidden' : 'px-8 py-4'">
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
      <div class="rounded-xl border border-gray-100 bg-white shadow-sm dark:border-gray-700 dark:bg-gray-800" :class="activeTab === 'detail' ? 'flex flex-col flex-1 overflow-hidden' : ''">

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

        <!-- ── Agent 详情（左中右三栏布局） ── -->
        <div v-else-if="activeTab === 'detail'" class="flex flex-col h-full">
          <div v-if="selectedAgentId" class="flex flex-col flex-1 overflow-hidden">

            <!-- 顶部操作栏 -->
            <div class="flex items-center justify-between border-b border-gray-100 px-5 py-3 dark:border-gray-700">
              <div class="flex items-center gap-3">
                <div class="flex h-9 w-9 items-center justify-center rounded-xl bg-indigo-50 text-xl dark:bg-indigo-900/30">
                  {{ agentDetail.avatarEmoji }}
                </div>
                <div>
                  <div class="text-sm font-semibold text-gray-900 dark:text-white">{{ agentDetail.name }}</div>
                  <div class="text-xs text-gray-400">{{ agentDetail.type || '未分类' }}</div>
                </div>
              </div>
              <NSpace>
                <NButton size="small" @click="activeTab = 'list'">
                  <template #icon><icon-carbon:arrow-left /></template>
                  返回列表
                </NButton>
                <NButton size="small" :loading="saving" type="primary" @click="saveAgent">
                  <template #icon><icon-carbon:save /></template>
                  保存
                </NButton>
              </NSpace>
            </div>

            <!-- 三栏内容区 -->
            <div class="flex flex-1 overflow-hidden divide-x divide-gray-100 dark:divide-gray-700">

              <!-- ── 左栏：基础信息 ── -->
              <div class="flex-1 overflow-y-auto px-4 py-4 space-y-4 min-w-0 basis-1/3">
                <div class="text-xs font-semibold uppercase tracking-wider text-gray-400">基础信息</div>

                <!-- Agent 名称（只读） -->
                <div>
                  <div class="mb-1 text-xs text-gray-500">Agent 名称</div>
                  <div class="flex items-center gap-2 rounded-lg border border-gray-100 bg-gray-50 px-3 py-2 dark:border-gray-700 dark:bg-gray-800">
                    <span class="text-sm font-medium text-gray-700 dark:text-gray-200 select-none">{{ agentDetail.name }}</span>
                    <span class="ml-auto rounded bg-gray-200 px-1.5 py-0.5 text-[10px] text-gray-500 dark:bg-gray-700 dark:text-gray-400">只读</span>
                  </div>
                </div>

                <!-- Agent 类型 -->
                <div>
                  <div class="mb-1 text-xs text-gray-500">Agent 类型</div>
                  <NSelect v-model:value="agentDetail.type" :options="agentTypeOptions" placeholder="选择类型" size="small" />
                </div>

                <!-- 描述 -->
                <div>
                  <div class="mb-1 text-xs text-gray-500">描述</div>
                  <NInput v-model:value="agentDetail.description" type="textarea" :rows="3" placeholder="描述该 Agent 的职责" size="small" />
                </div>

                <!-- 头像选择 -->
                <div>
                  <div class="mb-2 text-xs text-gray-500">头像</div>
                  <div class="grid grid-cols-4 gap-1.5">
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

                <!-- System Prompt -->
                <div>
                  <div class="mb-1 text-xs text-gray-500">System Prompt</div>
                  <NInput
                    v-model:value="agentDetail.systemPrompt"
                    type="textarea"
                    :rows="8"
                    placeholder="你是一名专业的 AI 助手，负责..."
                    size="small"
                  />
                  <div class="mt-1 text-[10px] text-gray-400">支持使用 &#123;&#123;变量&#125;&#125; 引用上下文变量</div>
                </div>
              </div>

              <!-- ── 中栏：模型与能力配置 ── -->
              <div class="flex-1 overflow-y-auto px-4 py-4 space-y-4 min-w-0 basis-1/3">
                <div class="text-xs font-semibold uppercase tracking-wider text-gray-400">模型与能力</div>

                <!-- 模型选择 -->
                <div class="rounded-lg border border-gray-100 p-4 dark:border-gray-700">
                  <div class="mb-3 flex items-center gap-2 text-sm font-semibold text-gray-700 dark:text-gray-300">
                    <icon-carbon:machine-learning class="text-indigo-500" />
                    模型选择
                  </div>
                  <NSelect
                    v-model:value="agentDetail.selectedModel"
                    :options="modelOptions.length ? modelOptions : [{ label: '暂无可用模型', value: '', disabled: true }]"
                    placeholder="选择模型"
                    size="small"
                  />
                </div>

                <!-- 模型参数 -->
                <div class="rounded-lg border border-gray-100 p-4 dark:border-gray-700">
                  <div class="mb-3 flex items-center gap-2 text-sm font-semibold text-gray-700 dark:text-gray-300">
                    <icon-carbon:settings class="text-indigo-500" />
                    模型参数
                  </div>
                  <div class="space-y-3">
                    <div>
                      <div class="mb-1 flex items-center justify-between text-xs text-gray-500">
                        <span>Temperature</span>
                        <span class="font-medium text-gray-700 dark:text-gray-300">{{ agentDetail.temperature }}</span>
                      </div>
                      <NInputNumber v-model:value="agentDetail.temperature" :min="0" :max="2" :step="0.1" :precision="1" class="w-full" size="small" />
                    </div>
                    <div>
                      <div class="mb-1 flex items-center justify-between text-xs text-gray-500">
                        <span>Top P</span>
                        <span class="font-medium text-gray-700 dark:text-gray-300">{{ agentDetail.topP }}</span>
                      </div>
                      <NInputNumber v-model:value="agentDetail.topP" :min="0" :max="1" :step="0.05" :precision="2" class="w-full" size="small" />
                    </div>
                    <div>
                      <div class="mb-1 flex items-center justify-between text-xs text-gray-500">
                        <span>Max Tokens</span>
                        <span class="font-medium text-gray-700 dark:text-gray-300">{{ agentDetail.maxTokens }}</span>
                      </div>
                      <NInputNumber v-model:value="agentDetail.maxTokens" :min="1" :max="32768" class="w-full" size="small" />
                    </div>
                  </div>
                </div>

                <!-- 知识库绑定 -->
                <div class="rounded-lg border border-gray-100 p-4 dark:border-gray-700">
                  <div class="mb-3 flex items-center gap-2 text-sm font-semibold text-gray-700 dark:text-gray-300">
                    <icon-carbon:data-base class="text-indigo-500" />
                    知识库绑定
                  </div>
                  <NSelect
                    :value="splitComma(agentDetail.knowledgeBases)"
                    multiple
                    :options="knowledgeBaseOptions.length ? knowledgeBaseOptions : [{ label: '暂无知识库', value: '', disabled: true }]"
                    placeholder="选择知识库"
                    size="small"
                    @update:value="(v: string[]) => (agentDetail.knowledgeBases = joinComma(v))"
                  />
                </div>

                <!-- 工具绑定 -->
                <div class="rounded-lg border border-gray-100 p-4 dark:border-gray-700">
                  <div class="mb-3 flex items-center gap-2 text-sm font-semibold text-gray-700 dark:text-gray-300">
                    <icon-carbon:tool-kit class="text-indigo-500" />
                    工具绑定
                  </div>
                  <NSelect
                    :value="splitComma(agentDetail.mcpTools)"
                    multiple
                    :options="mcpToolOptions.length ? mcpToolOptions : [{ label: '暂无MCP工具', value: '', disabled: true }]"
                    placeholder="选择 MCP 工具"
                    size="small"
                    @update:value="(v: string[]) => (agentDetail.mcpTools = joinComma(v))"
                  />
                </div>

                <!-- Memory 配置 -->
                <div class="rounded-lg border border-gray-100 p-4 dark:border-gray-700">
                  <div class="mb-3 flex items-center gap-2 text-sm font-semibold text-gray-700 dark:text-gray-300">
                    <icon-carbon:cognitive class="text-indigo-500" />
                    Memory 配置
                  </div>
                  <NSelect
                    v-model:value="agentDetail.memoryTypes"
                    multiple
                    :options="memoryOptions"
                    placeholder="选择记忆类型"
                    size="small"
                  />
                  <div v-if="agentDetail.memoryTypes.length" class="mt-2 flex flex-wrap gap-1">
                    <span
                      v-for="m in agentDetail.memoryTypes"
                      :key="m"
                      class="inline-flex items-center gap-1 rounded-full bg-indigo-50 px-2 py-0.5 text-[11px] text-indigo-600 dark:bg-indigo-900/30 dark:text-indigo-300"
                    >
                      <span class="h-1.5 w-1.5 rounded-full bg-indigo-400"></span>
                      {{ m }}
                    </span>
                  </div>
                </div>

                <!-- 权限管理 -->
                <div class="rounded-lg border border-gray-100 p-4 dark:border-gray-700">
                  <div class="mb-3 flex items-center gap-2 text-sm font-semibold text-gray-700 dark:text-gray-300">
                    <icon-carbon:locked class="text-indigo-500" />
                    权限管理
                  </div>
                  <NSelect
                    v-model:value="agentDetail.permissionScope"
                    :options="permissionOptions"
                    placeholder="选择权限范围"
                    size="small"
                  />
                  <div class="mt-2 text-xs text-gray-400">
                    <template v-if="agentDetail.permissionScope === '公开'">所有用户均可访问</template>
                    <template v-else-if="agentDetail.permissionScope === '组织内'">仅组织成员可访问</template>
                    <template v-else>仅指定部门成员可访问</template>
                  </div>
                </div>
              </div>

              <!-- ── 右栏：即时调试 ── -->
              <div class="flex-1 flex flex-col bg-gray-50 dark:bg-gray-900 min-w-0 basis-1/3">
                <!-- 调试标题栏 -->
                <div class="flex items-center gap-2 border-b border-gray-100 px-4 py-2.5 dark:border-gray-700">
                  <icon-carbon:debug class="text-indigo-500 flex-shrink-0" />
                  <span class="text-sm font-semibold text-gray-700 dark:text-gray-300">即时调试</span>
                  <span class="ml-1 rounded-full bg-indigo-100 px-2 py-0.5 text-[10px] font-medium text-indigo-600 dark:bg-indigo-900/40 dark:text-indigo-300 truncate max-w-[100px]">
                    {{ agentDetail.name }}
                  </span>
                  <div class="ml-auto flex items-center gap-1">
                    <NButton
                      size="tiny"
                      secondary
                      :disabled="debugMessages.length === 0"
                      @click="clearDebugHistory"
                    >
                      <template #icon><icon-carbon:trash-can /></template>
                      清除
                    </NButton>
                    <NButton
                      size="tiny"
                      type="primary"
                      secondary
                      @click="newDebugSession"
                    >
                      <template #icon><icon-carbon:add /></template>
                      新对话
                    </NButton>
                  </div>
                </div>

                <!-- 对话消息区 -->
                <div ref="debugScrollRef" class="flex-1 overflow-y-auto px-4 py-4 space-y-4 min-h-0">
                  <!-- 空状态 -->
                  <div v-if="debugMessages.length === 0" class="flex h-full items-center justify-center">
                    <div class="text-center">
                      <div class="mb-2 text-3xl">💬</div>
                      <div class="text-xs text-gray-400">发送消息开始调试</div>
                    </div>
                  </div>

                  <!-- 消息列表 -->
                  <template v-for="(msg, idx) in debugMessages" :key="idx">
                    <!-- 用户消息气泡 -->
                    <div v-if="msg.role === 'user'" class="flex justify-end">
                      <div class="max-w-[80%] rounded-2xl rounded-tr-sm bg-indigo-500 px-3 py-2 text-sm text-white shadow-sm whitespace-pre-wrap">
                        {{ msg.content }}
                      </div>
                    </div>

                    <!-- Agent 回复气泡 -->
                    <div v-else class="space-y-2">
                      <div class="flex items-start gap-2">
                        <div class="flex h-7 w-7 flex-shrink-0 items-center justify-center rounded-full bg-indigo-100 text-sm dark:bg-indigo-900/30">
                          {{ agentDetail.avatarEmoji }}
                        </div>
                        <div
                          class="max-w-[80%] rounded-2xl rounded-tl-sm px-3 py-2 text-sm shadow-sm whitespace-pre-wrap"
                          :class="msg.success === false
                            ? 'bg-red-50 text-red-600 dark:bg-red-900/20 dark:text-red-400'
                            : 'bg-white text-gray-700 dark:bg-gray-800 dark:text-gray-200'"
                        >
                          <template v-if="msg.success === false && msg.errorMessage">
                            <span class="font-medium">执行失败：</span>{{ msg.errorMessage }}
                          </template>
                          <template v-else>{{ msg.content }}</template>
                        </div>
                      </div>

                      <!-- Token 摘要（折叠式） -->
                      <div v-if="msg.tokens" class="ml-9">
                        <details class="group">
                          <summary class="cursor-pointer text-[10px] text-gray-400 hover:text-gray-600 select-none list-none flex items-center gap-1">
                            <icon-carbon:chevron-right class="text-gray-400 transition-transform group-open:rotate-90" />
                            Token: {{ msg.tokens.totalTokens }} · {{ msg.durationMs }}ms
                          </summary>
                          <div class="mt-1.5 rounded-lg border border-gray-100 bg-white p-2.5 dark:border-gray-700 dark:bg-gray-800">
                            <div class="grid grid-cols-4 gap-1 text-center">
                              <div>
                                <div class="text-sm font-bold text-gray-800 dark:text-gray-100">{{ msg.tokens.promptTokens }}</div>
                                <div class="text-[10px] text-gray-400">Prompt</div>
                              </div>
                              <div>
                                <div class="text-sm font-bold text-gray-800 dark:text-gray-100">{{ msg.tokens.completionTokens }}</div>
                                <div class="text-[10px] text-gray-400">Output</div>
                              </div>
                              <div>
                                <div class="text-sm font-bold text-gray-800 dark:text-gray-100">{{ msg.tokens.totalTokens }}</div>
                                <div class="text-[10px] text-gray-400">Total</div>
                              </div>
                              <div>
                                <div class="text-sm font-bold text-indigo-600 dark:text-indigo-400">${{ msg.tokens.cost.toFixed(4) }}</div>
                                <div class="text-[10px] text-gray-400">Cost</div>
                              </div>
                            </div>
                          </div>
                        </details>
                      </div>

                      <!-- 调用链路（折叠式） -->
                      <div v-if="msg.trace?.length" class="ml-9">
                        <details class="group">
                          <summary class="cursor-pointer text-[10px] text-gray-400 hover:text-gray-600 select-none list-none flex items-center gap-1">
                            <icon-carbon:chevron-right class="text-gray-400 transition-transform group-open:rotate-90" />
                            调用链路 {{ msg.trace.length }} 步
                          </summary>
                          <div class="mt-1.5 rounded-lg border border-gray-100 bg-white p-2 dark:border-gray-700 dark:bg-gray-800 space-y-1">
                            <div
                              v-for="(step, si) in msg.trace"
                              :key="si"
                              class="flex items-center justify-between rounded px-2 py-1 text-xs"
                              :class="step.status === 'success' ? 'bg-emerald-50 dark:bg-emerald-900/20' : 'bg-red-50 dark:bg-red-900/20'"
                            >
                              <span class="font-medium">{{ step.name }}</span>
                              <span class="text-gray-500">{{ step.durationMs }}ms</span>
                            </div>
                          </div>
                        </details>
                      </div>
                    </div>
                  </template>

                  <!-- 正在加载 -->
                  <div v-if="debugLoading" class="flex items-start gap-2">
                    <div class="flex h-7 w-7 flex-shrink-0 items-center justify-center rounded-full bg-indigo-100 text-sm dark:bg-indigo-900/30">
                      {{ agentDetail.avatarEmoji }}
                    </div>
                    <div class="rounded-2xl rounded-tl-sm bg-white px-4 py-2.5 shadow-sm dark:bg-gray-800">
                      <div class="flex items-center gap-1">
                        <span class="inline-block h-1.5 w-1.5 rounded-full bg-indigo-400 animate-bounce" style="animation-delay: 0ms" />
                        <span class="inline-block h-1.5 w-1.5 rounded-full bg-indigo-400 animate-bounce" style="animation-delay: 150ms" />
                        <span class="inline-block h-1.5 w-1.5 rounded-full bg-indigo-400 animate-bounce" style="animation-delay: 300ms" />
                      </div>
                    </div>
                  </div>
                </div>

                <!-- 输入区 -->
                <div class="border-t border-gray-100 bg-white px-3 py-3 dark:border-gray-700 dark:bg-gray-800">
                  <div class="flex items-end gap-2">
                    <NInput
                      v-model:value="testQuery"
                      type="textarea"
                      :rows="2"
                      placeholder="输入测试问题..."
                      :autosize="{ minRows: 2, maxRows: 4 }"
                      size="small"
                      @keyup.enter.exact="runDebug"
                    />
                    <NButton type="primary" :loading="debugLoading" :disabled="debugLoading" size="small" class="flex-shrink-0" @click="runDebug">
                      <template #icon><icon-carbon:send /></template>
                    </NButton>
                  </div>
                  <div class="mt-1 text-[10px] text-gray-400">Enter 发送 · Shift+Enter 换行</div>
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

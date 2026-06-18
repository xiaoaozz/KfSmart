<script setup lang="ts">
import {
  NButton,
  NDivider,
  NEmpty,
  NInput,
  NInputNumber,
  NModal,
  NScrollbar,
  NSelect,
  NSpin,
  NTag,
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

type PageView = 'list' | 'detail';

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
const activeModelName = ref('');

// ─── 页面状态 ───
const activeView = ref<PageView>('list');
const keyword = ref('');
const saving = ref(false);
const debugLoading = ref(false);
const createVisible = ref(false);
const createForm = ref({ name: '', type: '知识问答', description: '' });
const loading = ref(false);

// ─── 列表数据 ───
const agentList = ref<Api.AgentCenter.Workflow[]>([]);
const activeCategory = ref('全部');
const categoryMode = ref<'type' | 'status'>('type');
const agentTypes = ref<string[]>([]);

const agentStatuses = computed(() => [...new Set(agentList.value.map(a => a.status).filter(Boolean))]);

const categoryCounts = computed(() => {
  const counts: Record<string, number> = { '全部': agentList.value.length };
  agentList.value.forEach(item => {
    const key = categoryMode.value === 'type' ? item.type : item.status;
    if (key) counts[key] = (counts[key] || 0) + 1;
  });
  return counts;
});

const categoryList = computed(() =>
  categoryMode.value === 'type' ? agentTypes.value : agentStatuses.value
);

const filteredAgents = computed(() => {
  let list = agentList.value;
  if (activeCategory.value !== '全部') {
    list = categoryMode.value === 'type'
      ? list.filter(a => a.type === activeCategory.value)
      : list.filter(a => a.status === activeCategory.value);
  }
  if (keyword.value) {
    const kw = keyword.value.toLowerCase();
    list = list.filter(a => a.name?.toLowerCase().includes(kw) || a.description?.toLowerCase().includes(kw));
  }
  return list;
});

function switchCategoryMode(mode: 'type' | 'status') {
  categoryMode.value = mode;
  activeCategory.value = '全部';
}

// ─── Agent 详情 ───
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

// ─── 数据加载 ───
function getPageRecords<T>(data: any): T[] {
  return data?.records || data?.content || data?.data || [];
}

async function loadData() {
  loading.value = true;
  const [statsRes, kbRes, promptRes, toolRes, modelRes, agentRes] = await Promise.all([
    fetchAgentWorkflowStats(),
    fetchGetKnowledgeBases({ page: 1, size: 100 }),
    fetchPromptTemplates({ page: 1, size: 100 }),
    fetchMcpTools({ page: 1, size: 100 }),
    fetchAgentModels(),
    fetchAgentWorkflows({ page: 1, size: 100, keyword: keyword.value || undefined })
  ]);
  loading.value = false;

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
    const activeItem = modelRes.data.find(item => item.active);
    activeModelName.value = activeItem?.modelName || activeItem?.name || '';
    if (!agentDetail.selectedModel) {
      agentDetail.selectedModel = activeModelName.value;
    }
  }
  if (!agentRes.error && agentRes.data) {
    agentList.value = getPageRecords<Api.AgentCenter.Workflow>(agentRes.data);
    const types = [...new Set(agentList.value.map(a => a.type).filter(Boolean))];
    agentTypes.value = types;
  }
}

async function searchAgents() {
  await loadData();
}

function handleCategoryClick(cat: string) {
  activeCategory.value = cat;
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
  selectedAgent.value = { ...row };
  applyAgent(row);
  activeView.value = 'detail';
  debugMessages.value = [];
  testQuery.value = '';
}

function selectAgent(item: Api.AgentCenter.Workflow) {
  selectedAgent.value = item;
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
    if (activeView.value === 'detail') {
      activeView.value = 'list';
    }
  }
}

async function deleteAgent(workflowId: string) {
  window.$dialog?.warning({
    title: '删除 Agent',
    content: '确认删除该 Agent 吗？此操作不可恢复。',
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      const { error } = await fetchDeleteAgentWorkflow(workflowId);
      if (!error) {
        window.$message?.success('删除成功');
        if (selectedAgent.value?.workflowId === workflowId) {
          selectedAgent.value = null;
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

  debugMessages.value.push({ role: 'user', content: userQuery });
  debugLoading.value = true;

  const history = debugMessages.value
    .filter(m => m.role === 'user' || m.role === 'agent')
    .slice(0, -1)
    .map(m => ({ role: m.role === 'user' ? 'user' : 'assistant', content: m.content }));

  try {
    const { error, data } = await fetchDebugAgentWorkflow(agentDetail.workflowId, {
      query: userQuery,
      history,
      systemPrompt: agentDetail.systemPrompt ?? '',
      mcpTools: agentDetail.mcpTools ?? '',
      models: agentDetail.selectedModel || agentDetail.models || '',
      temperature: agentDetail.temperature,
      topP: agentDetail.topP,
      maxTokens: agentDetail.maxTokens,
      knowledgeBases: agentDetail.knowledgeBases ?? '',
      memoryTypes: agentDetail.memoryTypes.length ? agentDetail.memoryTypes.join(',') : ''
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

function formatTime(time: string | undefined | null): string {
  if (!time) return '-';
  return dayjs(time).format('MM-DD HH:mm');
}

onMounted(() => {
  loadData();
});
</script>

<template>
  <div class="h-full flex flex-col bg-[#f5f7fa] dark:bg-[#101014]">
    <!-- ==================== 列表视图 ==================== -->
    <template v-if="activeView === 'list'">
      <div class="h-full flex">
        <!-- 左侧分类导航 -->
        <div class="w-180px flex-shrink-0 border-r border-gray-200 dark:border-gray-700 bg-white dark:bg-[#18181c] flex flex-col">
          <div class="px-4 pt-4 pb-2">
            <h2 class="text-sm font-semibold text-gray-800 dark:text-gray-100 mb-3">Agent 分类</h2>
            <div class="flex rounded-lg bg-gray-100 dark:bg-gray-800 p-0.5">
              <button
                class="flex-1 rounded-md py-1 text-xs font-medium transition-all"
                :class="categoryMode === 'type'
                  ? 'bg-white dark:bg-[#1e1e22] text-gray-800 dark:text-gray-100 shadow-sm'
                  : 'text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-300'"
                @click="switchCategoryMode('type')"
              >按类型</button>
              <button
                class="flex-1 rounded-md py-1 text-xs font-medium transition-all"
                :class="categoryMode === 'status'
                  ? 'bg-white dark:bg-[#1e1e22] text-gray-800 dark:text-gray-100 shadow-sm'
                  : 'text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-300'"
                @click="switchCategoryMode('status')"
              >按状态</button>
            </div>
          </div>
          <NScrollbar class="flex-1">
            <div class="px-2 space-y-0.5 pt-1">
              <div
                class="cursor-pointer rounded-lg px-3 py-2 text-sm transition-all"
                :class="activeCategory === '全部'
                  ? 'bg-primary-50 text-primary-600 font-medium dark:bg-primary-900/20 dark:text-primary-400'
                  : 'text-gray-600 hover:bg-gray-50 dark:text-gray-400 dark:hover:bg-gray-800'"
                @click="handleCategoryClick('全部')"
              >
                <div class="flex items-center justify-between">
                  <div class="flex items-center gap-2">
                    <icon-carbon:catalog class="text-base" />
                    <span>全部</span>
                  </div>
                  <span class="text-xs opacity-60">{{ categoryCounts['全部'] ?? 0 }}</span>
                </div>
              </div>
              <div
                v-for="cat in categoryList"
                :key="cat"
                class="cursor-pointer rounded-lg px-3 py-2 text-sm transition-all"
                :class="activeCategory === cat
                  ? 'bg-primary-50 text-primary-600 font-medium dark:bg-primary-900/20 dark:text-primary-400'
                  : 'text-gray-600 hover:bg-gray-50 dark:text-gray-400 dark:hover:bg-gray-800'"
                @click="handleCategoryClick(cat)"
              >
                <div class="flex items-center justify-between">
                  <div class="flex items-center gap-2">
                    <icon-carbon:tag class="text-base" />
                    <span>{{ cat }}</span>
                  </div>
                  <span class="text-xs opacity-60">{{ categoryCounts[cat] ?? 0 }}</span>
                </div>
              </div>
            </div>
          </NScrollbar>
        </div>

        <!-- 中间卡片列表 -->
        <div class="flex-1 flex flex-col min-w-0">
          <div class="flex items-center justify-between px-5 py-3 border-b border-gray-100 dark:border-gray-700 bg-white dark:bg-[#18181c]">
            <div class="flex items-center gap-2">
              <NInput
                v-model:value="keyword"
                placeholder="搜索 Agent 名称或描述..."
                clearable
                class="w-200px"
                size="small"
                @keyup.enter="searchAgents"
              >
                <template #prefix>
                  <icon-carbon:search class="text-gray-400" />
                </template>
              </NInput>
              <NButton size="small" @click="searchAgents">搜索</NButton>
            </div>
            <NButton size="small" type="primary" @click="openCreate">
              <template #icon><icon-carbon:add /></template>
              创建 Agent
            </NButton>
          </div>

          <NScrollbar class="flex-1">
            <div class="p-4">
              <NSpin :show="loading">
                <div v-if="filteredAgents.length === 0 && !loading" class="py-20">
                  <NEmpty description="暂无 Agent">
                    <template #extra>
                      <NButton size="small" type="primary" @click="openCreate">创建 Agent</NButton>
                    </template>
                  </NEmpty>
                </div>
                <div v-else class="grid grid-cols-1 xl:grid-cols-2 gap-3">
                  <div
                    v-for="item in filteredAgents"
                    :key="item.workflowId"
                    class="cursor-pointer rounded-xl border bg-white p-4 transition-all hover:shadow-md dark:bg-[#1e1e22] dark:border-gray-700"
                    :class="selectedAgent?.workflowId === item.workflowId
                      ? 'border-primary-400 shadow-sm ring-1 ring-primary-200 dark:border-primary-500 dark:ring-primary-800'
                      : 'border-gray-200 hover:border-primary-300 dark:border-gray-700 dark:hover:border-primary-600'"
                    @click="selectAgent(item)"
                  >
                    <div class="flex items-start justify-between mb-2">
                      <div class="flex items-center gap-2 flex-1 min-w-0">
                        <div class="w-8 h-8 rounded-lg bg-primary-50 dark:bg-primary-900/30 flex items-center justify-center flex-shrink-0 text-lg">
                          {{ item.avatarEmoji || '🤖' }}
                        </div>
                        <div class="min-w-0">
                          <h3 class="text-sm font-semibold text-gray-900 dark:text-gray-100 truncate">{{ item.name }}</h3>
                          <p v-if="item.description" class="mt-0.5 text-xs text-gray-500 dark:text-gray-400 line-clamp-1">{{ item.description }}</p>
                        </div>
                      </div>
                      <NTag
                        :type="item.status === '运行中' ? 'success' : 'default'"
                        size="small"
                        :bordered="false"
                        class="ml-2 flex-shrink-0"
                      >
                        {{ item.status || '草稿' }}
                      </NTag>
                    </div>

                    <div class="flex items-center justify-between text-xs text-gray-400 dark:text-gray-500 mt-2">
                      <div class="flex items-center gap-3">
                        <span class="flex items-center gap-1">
                          <icon-carbon:tag class="text-sm" />
                          {{ item.type || '未分类' }}
                        </span>
                        <span v-if="item.callCount !== undefined" class="flex items-center gap-1">
                          <icon-carbon:chart-line class="text-sm" />
                          {{ item.callCount?.toLocaleString() ?? 0 }} 次
                        </span>
                      </div>
                      <span>{{ formatTime(item.updatedAt) }}</span>
                    </div>
                  </div>
                </div>
              </NSpin>
            </div>
          </NScrollbar>
        </div>

        <!-- 右侧详情面板 -->
        <div class="w-[380px] flex-shrink-0 flex flex-col bg-white dark:bg-[#18181c] border-l border-gray-200 dark:border-gray-700">
          <template v-if="selectedAgent">
            <div class="px-5 py-3 border-b border-gray-100 dark:border-gray-700">
              <h2 class="text-sm font-semibold text-gray-800 dark:text-gray-100">Agent 详情</h2>
            </div>

            <NScrollbar class="flex-1">
              <div class="p-5 space-y-4">
                <!-- 头像 + 名称 -->
                <div class="flex items-center gap-3">
                  <div class="w-12 h-12 rounded-xl bg-primary-50 dark:bg-primary-900/30 flex items-center justify-center text-2xl flex-shrink-0">
                    {{ selectedAgent.avatarEmoji || '🤖' }}
                  </div>
                  <div>
                    <h3 class="text-base font-semibold text-gray-900 dark:text-gray-50">{{ selectedAgent.name }}</h3>
                    <p v-if="selectedAgent.description" class="text-sm text-gray-500 dark:text-gray-400 mt-0.5">
                      {{ selectedAgent.description }}
                    </p>
                  </div>
                </div>

                <!-- 状态 & 类型 -->
                <div class="flex items-center gap-2">
                  <NTag :type="selectedAgent.status === '运行中' ? 'success' : 'default'" :bordered="false" size="small">
                    {{ selectedAgent.status || '草稿' }}
                  </NTag>
                  <NTag type="info" :bordered="false" size="small">{{ selectedAgent.type || '未分类' }}</NTag>
                </div>

                <NDivider class="!my-2" />

                <!-- 统计信息 -->
                <div class="space-y-2">
                  <div class="flex items-center justify-between text-sm">
                    <span class="text-xs text-gray-500 dark:text-gray-400">调用次数</span>
                    <span class="text-gray-700 dark:text-gray-300">{{ selectedAgent.callCount?.toLocaleString() ?? '-' }}</span>
                  </div>
                  <div class="flex items-center justify-between text-sm">
                    <span class="text-xs text-gray-500 dark:text-gray-400">成功率</span>
                    <span :class="(selectedAgent.callCount ?? 0) > 0 ? 'text-emerald-600 font-medium' : 'text-gray-400'">
                      {{ (selectedAgent.callCount ?? 0) > 0 ? `${selectedAgent.successRate ?? 100}%` : '-' }}
                    </span>
                  </div>
                </div>

                <!-- 模型选择 -->
                <template v-if="selectedAgent.models">
                  <NDivider class="!my-2" />
                  <div>
                    <div class="mb-2 flex items-center gap-1.5 text-xs font-medium text-gray-500 dark:text-gray-400">
                      <icon-carbon:machine-learning class="text-primary-500" />
                      模型
                    </div>
                    <div class="flex flex-wrap gap-1.5">
                      <NTag v-for="m in selectedAgent.models.split(',').filter(Boolean)" :key="m" size="small" :bordered="false" type="info">
                        {{ m }}
                      </NTag>
                    </div>
                    <div class="mt-2 grid grid-cols-3 gap-2 text-xs">
                      <div v-if="selectedAgent.temperature !== undefined" class="rounded-lg bg-gray-50 dark:bg-[#1e1e22] px-2 py-1.5 text-center">
                        <div class="font-medium text-gray-700 dark:text-gray-300">{{ selectedAgent.temperature }}</div>
                        <div class="text-gray-400">Temp</div>
                      </div>
                      <div v-if="selectedAgent.topP !== undefined" class="rounded-lg bg-gray-50 dark:bg-[#1e1e22] px-2 py-1.5 text-center">
                        <div class="font-medium text-gray-700 dark:text-gray-300">{{ selectedAgent.topP }}</div>
                        <div class="text-gray-400">Top P</div>
                      </div>
                      <div v-if="selectedAgent.maxTokens !== undefined" class="rounded-lg bg-gray-50 dark:bg-[#1e1e22] px-2 py-1.5 text-center">
                        <div class="font-medium text-gray-700 dark:text-gray-300">{{ selectedAgent.maxTokens }}</div>
                        <div class="text-gray-400">Tokens</div>
                      </div>
                    </div>
                  </div>
                </template>

                <!-- 知识库 -->
                <template v-if="selectedAgent.knowledgeBases">
                  <NDivider class="!my-2" />
                  <div>
                    <div class="mb-2 flex items-center gap-1.5 text-xs font-medium text-gray-500 dark:text-gray-400">
                      <icon-carbon:data-base class="text-primary-500" />
                      知识库
                    </div>
                    <div class="flex flex-wrap gap-1.5">
                      <NTag v-for="kb in selectedAgent.knowledgeBases.split(',').filter(Boolean)" :key="kb" size="small" :bordered="false" type="warning">
                        {{ kb }}
                      </NTag>
                    </div>
                  </div>
                </template>

                <!-- 工具绑定 -->
                <template v-if="selectedAgent.mcpTools">
                  <NDivider class="!my-2" />
                  <div>
                    <div class="mb-2 flex items-center gap-1.5 text-xs font-medium text-gray-500 dark:text-gray-400">
                      <icon-carbon:tool-kit class="text-primary-500" />
                      工具
                    </div>
                    <div class="flex flex-wrap gap-1.5">
                      <NTag v-for="tool in selectedAgent.mcpTools.split(',').filter(Boolean)" :key="tool" size="small" :bordered="false">
                        {{ tool }}
                      </NTag>
                    </div>
                  </div>
                </template>

                <!-- Memory -->
                <template v-if="selectedAgent.memoryTypes">
                  <NDivider class="!my-2" />
                  <div>
                    <div class="mb-2 flex items-center gap-1.5 text-xs font-medium text-gray-500 dark:text-gray-400">
                      <icon-carbon:cognitive class="text-primary-500" />
                      Memory
                    </div>
                    <div class="flex flex-wrap gap-1.5">
                      <span
                        v-for="m in selectedAgent.memoryTypes.split(',').filter(Boolean)"
                        :key="m"
                        class="inline-flex items-center gap-1 rounded-full bg-primary-50 px-2 py-0.5 text-[11px] text-primary-600 dark:bg-primary-900/30 dark:text-primary-300"
                      >
                        <span class="h-1.5 w-1.5 rounded-full bg-primary-400"></span>
                        {{ m }}
                      </span>
                    </div>
                  </div>
                </template>

                <!-- 权限管理 -->
                <template v-if="selectedAgent.permissionScope">
                  <NDivider class="!my-2" />
                  <div>
                    <div class="mb-2 flex items-center gap-1.5 text-xs font-medium text-gray-500 dark:text-gray-400">
                      <icon-carbon:locked class="text-primary-500" />
                      权限
                    </div>
                    <NTag size="small" :bordered="false">{{ selectedAgent.permissionScope }}</NTag>
                  </div>
                </template>

                <NDivider class="!my-2" />

                <!-- 时间信息 -->
                <div class="space-y-1.5 text-xs text-gray-400 dark:text-gray-500">
                  <div class="flex items-center gap-2">
                    <icon-carbon:time class="text-sm" />
                    <span>更新：{{ formatTime(selectedAgent.updatedAt) }}</span>
                  </div>
                  <div class="flex items-center gap-2">
                    <icon-carbon:identification class="text-sm" />
                    <span>ID：{{ selectedAgent.workflowId }}</span>
                  </div>
                </div>

                <!-- 操作按钮 -->
                <div class="flex flex-wrap gap-2 pt-1">
                  <NButton size="small" type="primary" @click="editAgent(selectedAgent)">
                    <template #icon><icon-carbon:edit /></template>
                    编辑
                  </NButton>
                  <NButton size="small" secondary @click="copyAgent(selectedAgent.workflowId)">
                    <template #icon><icon-carbon:copy /></template>
                    复制
                  </NButton>
                  <NButton size="small" secondary type="error" @click="deleteAgent(selectedAgent.workflowId)">
                    <template #icon><icon-carbon:trash-can /></template>
                    删除
                  </NButton>
                </div>
              </div>
            </NScrollbar>
          </template>

          <template v-else>
            <div class="flex flex-1 items-center justify-center">
              <NEmpty description="选择一个 Agent 查看详情">
                <template #icon>
                  <icon-carbon:bot class="text-4xl text-gray-300 dark:text-gray-600" />
                </template>
              </NEmpty>
            </div>
          </template>
        </div>
      </div>
    </template>

    <!-- ==================== 详情/编辑视图 ==================== -->
    <template v-else-if="activeView === 'detail'">
      <div class="h-full flex flex-col">
        <!-- 顶部操作栏 -->
        <div class="flex items-center justify-between px-6 py-3 border-b border-gray-200 dark:border-gray-700 bg-white dark:bg-[#18181c]">
          <div class="flex items-center gap-3">
            <NButton text @click="activeView = 'list'">
              <template #icon><icon-carbon:arrow-left class="text-lg" /></template>
              返回列表
            </NButton>
            <NDivider vertical />
            <div class="flex items-center gap-2">
              <div class="flex h-8 w-8 items-center justify-center rounded-lg bg-primary-50 dark:bg-primary-900/30 text-lg">
                {{ agentDetail.avatarEmoji }}
              </div>
              <span class="text-base font-semibold text-gray-800 dark:text-gray-100">{{ agentDetail.name }}</span>
              <span class="text-xs text-gray-400">{{ agentDetail.type || '未分类' }}</span>
            </div>
          </div>
          <div class="flex items-center gap-2">
            <NButton size="small" :loading="saving" type="primary" @click="saveAgent">
              <template #icon><icon-carbon:save /></template>
              保存
            </NButton>
            <NButton size="small" type="success" @click="publishAgent(agentDetail.workflowId)">
              <template #icon><icon-carbon:launch /></template>
              发布
            </NButton>
          </div>
        </div>

        <!-- 三栏内容区 -->
        <div class="flex flex-1 overflow-hidden divide-x divide-gray-100 dark:divide-gray-700">

          <!-- ── 左栏：基础信息 ── -->
          <div class="flex-1 overflow-y-auto px-4 py-4 space-y-4 min-w-0 basis-1/3 bg-white dark:bg-[#18181c]">
            <div class="text-xs font-semibold uppercase tracking-wider text-gray-400">基础信息</div>

            <div>
              <div class="mb-1 text-xs text-gray-500 dark:text-gray-400">Agent 名称</div>
              <div class="flex items-center gap-2 rounded-lg border border-gray-200 bg-gray-50 px-3 py-2 dark:border-gray-700 dark:bg-[#1e1e22]">
                <span class="text-sm font-medium text-gray-700 dark:text-gray-200 select-none">{{ agentDetail.name }}</span>
                <span class="ml-auto rounded bg-gray-200 px-1.5 py-0.5 text-[10px] text-gray-500 dark:bg-gray-700 dark:text-gray-400">只读</span>
              </div>
            </div>

            <div>
              <div class="mb-1 text-xs text-gray-500 dark:text-gray-400">Agent 类型</div>
              <NSelect v-model:value="agentDetail.type" :options="agentTypeOptions" placeholder="选择类型" size="small" />
            </div>

            <div>
              <div class="mb-1 text-xs text-gray-500 dark:text-gray-400">描述</div>
              <NInput v-model:value="agentDetail.description" type="textarea" :rows="3" placeholder="描述该 Agent 的职责" size="small" />
            </div>

            <div>
              <div class="mb-2 text-xs text-gray-500 dark:text-gray-400">头像</div>
              <div class="grid grid-cols-4 gap-1.5">
                <button
                  v-for="emoji in avatarOptions"
                  :key="emoji"
                  class="flex h-9 w-9 items-center justify-center rounded-lg border text-xl transition"
                  :class="agentDetail.avatarEmoji === emoji
                    ? 'border-primary-400 bg-primary-50 dark:border-primary-500 dark:bg-primary-900/30'
                    : 'border-gray-200 bg-white hover:border-primary-300 dark:border-gray-600 dark:bg-[#1e1e22]'"
                  @click="agentDetail.avatarEmoji = emoji"
                >{{ emoji }}</button>
              </div>
            </div>

            <div>
              <div class="mb-1 text-xs text-gray-500 dark:text-gray-400">System Prompt</div>
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
          <div class="flex-1 overflow-y-auto px-4 py-4 space-y-4 min-w-0 basis-1/3 bg-white dark:bg-[#18181c]">
            <div class="text-xs font-semibold uppercase tracking-wider text-gray-400">模型与能力</div>

            <div class="rounded-lg border border-gray-100 p-4 dark:border-gray-700">
              <div class="mb-3 flex items-center gap-2 text-sm font-semibold text-gray-700 dark:text-gray-300">
                <icon-carbon:machine-learning class="text-primary-500" />
                模型选择
              </div>
              <NSelect
                v-model:value="agentDetail.selectedModel"
                :options="modelOptions.length ? modelOptions : [{ label: '暂无可用模型', value: '', disabled: true }]"
                placeholder="选择模型"
                size="small"
              />
            </div>

            <div class="rounded-lg border border-gray-100 p-4 dark:border-gray-700">
              <div class="mb-3 flex items-center gap-2 text-sm font-semibold text-gray-700 dark:text-gray-300">
                <icon-carbon:settings class="text-primary-500" />
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

            <div class="rounded-lg border border-gray-100 p-4 dark:border-gray-700">
              <div class="mb-3 flex items-center gap-2 text-sm font-semibold text-gray-700 dark:text-gray-300">
                <icon-carbon:data-base class="text-primary-500" />
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

            <div class="rounded-lg border border-gray-100 p-4 dark:border-gray-700">
              <div class="mb-3 flex items-center gap-2 text-sm font-semibold text-gray-700 dark:text-gray-300">
                <icon-carbon:tool-kit class="text-primary-500" />
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

            <div class="rounded-lg border border-gray-100 p-4 dark:border-gray-700">
              <div class="mb-3 flex items-center gap-2 text-sm font-semibold text-gray-700 dark:text-gray-300">
                <icon-carbon:cognitive class="text-primary-500" />
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
                  class="inline-flex items-center gap-1 rounded-full bg-primary-50 px-2 py-0.5 text-[11px] text-primary-600 dark:bg-primary-900/30 dark:text-primary-300"
                >
                  <span class="h-1.5 w-1.5 rounded-full bg-primary-400"></span>
                  {{ m }}
                </span>
              </div>
            </div>

            <div class="rounded-lg border border-gray-100 p-4 dark:border-gray-700">
              <div class="mb-3 flex items-center gap-2 text-sm font-semibold text-gray-700 dark:text-gray-300">
                <icon-carbon:locked class="text-primary-500" />
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
          <div class="flex-1 flex flex-col bg-[#f5f7fa] dark:bg-[#101014] min-w-0 basis-1/3">
            <div class="flex items-center gap-2 border-b border-gray-100 px-4 py-2.5 dark:border-gray-700 bg-white dark:bg-[#18181c]">
              <icon-carbon:debug class="text-primary-500 flex-shrink-0" />
              <span class="text-sm font-semibold text-gray-700 dark:text-gray-300">即时调试</span>
              <span class="ml-1 rounded-full bg-primary-100 px-2 py-0.5 text-[10px] font-medium text-primary-600 dark:bg-primary-900/40 dark:text-primary-300 truncate max-w-[100px]">
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

            <div ref="debugScrollRef" class="flex-1 overflow-y-auto px-4 py-4 space-y-4 min-h-0">
              <div v-if="debugMessages.length === 0" class="flex h-full items-center justify-center">
                <div class="text-center">
                  <div class="mb-2 text-3xl">💬</div>
                  <div class="text-xs text-gray-400">发送消息开始调试</div>
                </div>
              </div>

              <template v-for="(msg, idx) in debugMessages" :key="idx">
                <div v-if="msg.role === 'user'" class="flex justify-end">
                  <div class="max-w-[80%] rounded-2xl rounded-tr-sm bg-primary-500 px-3 py-2 text-sm text-white shadow-sm whitespace-pre-wrap">
                    {{ msg.content }}
                  </div>
                </div>

                <div v-else class="space-y-2">
                  <div class="flex items-start gap-2">
                    <div class="flex h-7 w-7 flex-shrink-0 items-center justify-center rounded-full bg-primary-100 text-sm dark:bg-primary-900/30">
                      {{ agentDetail.avatarEmoji }}
                    </div>
                    <div
                      class="max-w-[80%] rounded-2xl rounded-tl-sm px-3 py-2 text-sm shadow-sm whitespace-pre-wrap"
                      :class="msg.success === false
                        ? 'bg-red-50 text-red-600 dark:bg-red-900/20 dark:text-red-400'
                        : 'bg-white text-gray-700 dark:bg-[#1e1e22] dark:text-gray-200'"
                    >
                      <template v-if="msg.success === false && msg.errorMessage">
                        <span class="font-medium">执行失败：</span>{{ msg.errorMessage }}
                      </template>
                      <template v-else>{{ msg.content }}</template>
                    </div>
                  </div>

                  <div v-if="msg.tokens" class="ml-9">
                    <details class="group">
                      <summary class="cursor-pointer text-[10px] text-gray-400 hover:text-gray-600 select-none list-none flex items-center gap-1">
                        <icon-carbon:chevron-right class="text-gray-400 transition-transform group-open:rotate-90" />
                        Token: {{ msg.tokens.totalTokens }} · {{ msg.durationMs }}ms
                      </summary>
                      <div class="mt-1.5 rounded-lg border border-gray-100 bg-white p-2.5 dark:border-gray-700 dark:bg-[#1e1e22]">
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
                            <div class="text-sm font-bold text-primary-600 dark:text-primary-400">${{ msg.tokens.cost.toFixed(4) }}</div>
                            <div class="text-[10px] text-gray-400">Cost</div>
                          </div>
                        </div>
                      </div>
                    </details>
                  </div>

                  <div v-if="msg.trace?.length" class="ml-9">
                    <details class="group">
                      <summary class="cursor-pointer text-[10px] text-gray-400 hover:text-gray-600 select-none list-none flex items-center gap-1">
                        <icon-carbon:chevron-right class="text-gray-400 transition-transform group-open:rotate-90" />
                        调用链路 {{ msg.trace.length }} 步
                      </summary>
                      <div class="mt-1.5 rounded-lg border border-gray-100 bg-white p-2 dark:border-gray-700 dark:bg-[#1e1e22] space-y-1">
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

              <div v-if="debugLoading" class="flex items-start gap-2">
                <div class="flex h-7 w-7 flex-shrink-0 items-center justify-center rounded-full bg-primary-100 text-sm dark:bg-primary-900/30">
                  {{ agentDetail.avatarEmoji }}
                </div>
                <div class="rounded-2xl rounded-tl-sm bg-white px-4 py-2.5 shadow-sm dark:bg-[#1e1e22]">
                  <div class="flex items-center gap-1">
                    <span class="inline-block h-1.5 w-1.5 rounded-full bg-primary-400 animate-bounce" style="animation-delay: 0ms" />
                    <span class="inline-block h-1.5 w-1.5 rounded-full bg-primary-400 animate-bounce" style="animation-delay: 150ms" />
                    <span class="inline-block h-1.5 w-1.5 rounded-full bg-primary-400 animate-bounce" style="animation-delay: 300ms" />
                  </div>
                </div>
              </div>
            </div>

            <div class="border-t border-gray-100 bg-white px-3 py-3 dark:border-gray-700 dark:bg-[#18181c]">
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
    </template>

    <!-- 创建 Agent 弹窗 -->
    <NModal v-model:show="createVisible" preset="card" title="创建 Agent" class="max-w-480px">
      <div class="space-y-4">
        <div>
          <div class="mb-1.5 text-xs font-medium text-gray-500 dark:text-gray-400">Agent 名称 <span class="text-red-500">*</span></div>
          <NInput v-model:value="createForm.name" placeholder="例如：HR助手" @keyup.enter="createAgent" />
        </div>
        <div>
          <div class="mb-1.5 text-xs font-medium text-gray-500 dark:text-gray-400">Agent 类型</div>
          <NSelect v-model:value="createForm.type" :options="agentTypeOptions" placeholder="选择类型" />
        </div>
        <div>
          <div class="mb-1.5 text-xs font-medium text-gray-500 dark:text-gray-400">描述</div>
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

<style scoped>
.line-clamp-1 {
  display: -webkit-box;
  -webkit-line-clamp: 1;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
</style>

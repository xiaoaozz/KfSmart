<script setup lang="ts">
import { useRouter } from 'vue-router';
import {
  NButton,
  NDivider,
  NEmpty,
  NInput,
  NModal,
  NScrollbar,
  NSelect,
  NSpin,
  NTag
} from 'naive-ui';
import {
  fetchWorkflowStats as fetchAgentWorkflowStats,
  fetchWorkflows as fetchAgentWorkflows,
  fetchCopyWorkflow as fetchCopyAgentWorkflow,
  fetchDebugWorkflow as fetchDebugAgentWorkflow,
  fetchDeleteWorkflow as fetchDeleteAgentWorkflow,
  fetchPublishWorkflow as fetchPublishAgentWorkflow,
  fetchSaveWorkflow as fetchSaveAgentWorkflow
} from '@/service/api/workflow';
import {
  fetchAgentModels,
  fetchMcpTools,
  fetchPromptTemplates
} from '@/service/api/resource';
import { fetchSkills } from '@/service/api/skills';
import { fetchGetKnowledgeBases } from '@/service/api/knowledge-base';
import FavoriteButton from '@/components/common/favorite-button.vue';
import { defaultNodeConfig, defaultNodes, defaultEdges } from './constants/nodeDefinitions';
import { useDesignerState } from './composables/useDesignerState';
import { useCanvasViewport } from './composables/useCanvasViewport';
import WorkflowDesigner from './components/WorkflowDesigner.vue';
import VersionHistoryDrawer from './components/VersionHistoryDrawer.vue';
import ExecutionMonitor from './components/ExecutionMonitor.vue';
import type { WorkflowNode } from './types/workflow';

type SelectOption = { label: string; value: string; disabled?: boolean };
type PageView = 'list' | 'designer';
const router = useRouter();

// ─── 统计数据 ───
const stats = ref<Api.AgentCenter.WorkflowStats>({ agentCount: 0, runCount: 0, successRate: 100, avgDurationMs: 0 });

// ─── 选项数据 ───
const knowledgeBaseOptions = ref<SelectOption[]>([]);
const promptOptions = ref<SelectOption[]>([]);
const mcpToolOptions = ref<SelectOption[]>([]);
const skillOptions = ref<SelectOption[]>([]);
const modelOptions = ref<SelectOption[]>([]);

// ─── 页面状态 ───
const activeView = ref<PageView>('list');
const keyword = ref('');
const saving = ref(false);
const debugLoading = ref(false);
const testQuery = ref('');
const createVisible = ref(false);
const createForm = ref({ name: '', type: '工作流', description: '' });
const loading = ref(false);
const versionDrawerVisible = ref(false);
const executionMonitorVisible = ref(false);
const currentExecutionId = ref('');
const debugResult = ref<Api.AgentCenter.DebugResult | null>(null);

// ─── 列表数据 ───
const workflowList = ref<Api.AgentCenter.Workflow[]>([]);
const selectedWorkflow = ref<Api.AgentCenter.Workflow | null>(null);
const activeCategory = ref('全部');
const categoryMode = ref<'type' | 'status'>('type');

const workflowTypes = computed(() => [...new Set(workflowList.value.map(w => w.type).filter(Boolean))]);
const workflowStatuses = computed(() => [...new Set(workflowList.value.map(w => w.status).filter(Boolean))]);

const categoryCounts = computed(() => {
  const counts: Record<string, number> = { '全部': workflowList.value.length };
  workflowList.value.forEach(item => {
    const key = categoryMode.value === 'type' ? item.type : item.status;
    if (key) counts[key] = (counts[key] || 0) + 1;
  });
  return counts;
});

const categoryList = computed(() => categoryMode.value === 'type' ? workflowTypes.value : workflowStatuses.value);

const filteredWorkflows = computed(() => {
  let list = workflowList.value;
  if (activeCategory.value !== '全部') {
    list = categoryMode.value === 'type'
      ? list.filter(w => w.type === activeCategory.value)
      : list.filter(w => w.status === activeCategory.value);
  }
  if (keyword.value) {
    const kw = keyword.value.toLowerCase();
    list = list.filter(w => w.name?.toLowerCase().includes(kw) || w.description?.toLowerCase().includes(kw));
  }
  return list;
});

function switchCategoryMode(mode: 'type' | 'status') {
  categoryMode.value = mode;
  activeCategory.value = '全部';
}

function handleCategoryClick(cat: string) {
  activeCategory.value = cat;
}

// ─── 设计器状态 ───
const { designer, hasSelectedWorkflow, selectedNode, captureDesignerSnapshot, revertDesignerChanges, resetDesigner } = useDesignerState();
const { snap, screenToWorld } = useCanvasViewport();
const dragging = ref<{ nodeId: string; offsetX: number; offsetY: number } | null>(null);

// ─── 数据加载 ───
function getPageRecords<T>(data: any): T[] {
  return data?.records || data?.content || data?.data || [];
}

async function loadData() {
  loading.value = true;
  const [statsRes, kbRes, promptRes, toolRes, modelRes, skillRes, wfRes] = await Promise.all([
    fetchAgentWorkflowStats(),
    fetchGetKnowledgeBases({ page: 1, size: 100 }),
    fetchPromptTemplates({ page: 1, size: 100 }),
    fetchMcpTools({ page: 1, size: 100 }),
    fetchAgentModels(),
    fetchSkills({ page: 1, size: 100 }),
    fetchAgentWorkflows({ page: 1, size: 100, keyword: keyword.value || undefined })
  ]);
  loading.value = false;

  if (!statsRes.error && statsRes.data) stats.value = statsRes.data;
  if (!kbRes.error && kbRes.data) {
    knowledgeBaseOptions.value = getPageRecords(kbRes.data).map((item: any) => ({ label: item.name, value: item.name }));
  }
  if (!promptRes.error && promptRes.data) {
    promptOptions.value = getPageRecords(promptRes.data).map((item: any) => ({ label: `${item.name} ${item.version || ''}`.trim(), value: item.name }));
  }
  if (!toolRes.error && toolRes.data) {
    mcpToolOptions.value = getPageRecords(toolRes.data).map((item: any) => ({ label: item.name, value: item.toolId || item.name }));
  }
  if (!skillRes.error && skillRes.data) {
    skillOptions.value = getPageRecords(skillRes.data).map((item: any) => ({ label: item.name, value: item.skillId, disabled: item.status === '已停用' }));
  }
  if (!modelRes.error && modelRes.data) {
    modelOptions.value = modelRes.data.map((item: any) => ({ label: item.modelName || item.name, value: item.modelName || item.name }));
  }
  if (!wfRes.error && wfRes.data) {
    workflowList.value = getPageRecords(wfRes.data);
  }
}

async function searchWorkflows() {
  await loadData();
}

// ─── CRUD ───
function openCreate() {
  createForm.value = { name: '', type: '工作流', description: '' };
  createVisible.value = true;
}

async function createWorkflow() {
  if (!createForm.value.name.trim()) {
    window.$message?.warning('请输入工作流名称');
    return;
  }
  const { error, data } = await fetchSaveAgentWorkflow({
    name: createForm.value.name,
    type: createForm.value.type,
    description: createForm.value.description,
    status: '草稿',
    nodesJson: JSON.stringify(defaultNodes()),
    edgesJson: JSON.stringify(defaultEdges())
  });
  if (!error && data) {
    createVisible.value = false;
    window.$message?.success('创建成功');
    await loadData();
    editWorkflow(data);
  }
}

function selectWorkflow(item: Api.AgentCenter.Workflow) {
  selectedWorkflow.value = item;
}

function editWorkflow(row: Api.AgentCenter.Workflow) {
  applyWorkflow(row);
  activeView.value = 'designer';
}

function applyWorkflow(row: Api.AgentCenter.Workflow) {
  selectedWorkflow.value = { ...row };
  designer.workflowId = row.workflowId;
  designer.name = row.name;
  designer.description = row.description;
  designer.type = row.type;
  designer.status = row.status;
  designer.ownerName = row.ownerName || 'admin';
  designer.permissionScope = row.permissionScope || '组织内';
  designer.tags = row.tags || '';
  designer.knowledgeBases = row.knowledgeBases || '';
  designer.promptRefs = row.promptRefs || '';
  designer.mcpTools = row.mcpTools || '';
  designer.skillRefs = row.skillRefs || '';
  designer.models = row.models || '';
  let parsedNodes: WorkflowNode[] = [];
  try { parsedNodes = row.nodesJson ? JSON.parse(row.nodesJson) : defaultNodes(); } catch { parsedNodes = defaultNodes(); }
  designer.nodes = parsedNodes.map(n => ({ ...n, config: n.config ?? defaultNodeConfig(n.type) }));
  let parsedEdges: any[] = [];
  try { parsedEdges = row.edgesJson ? JSON.parse(row.edgesJson) : defaultEdges(); } catch { parsedEdges = defaultEdges(); }
  designer.edges = parsedEdges;
  designer.selectedNodeId = designer.nodes[0]?.id || '';
  captureDesignerSnapshot();
}

async function saveDesigner() {
  if (!designer.workflowId) { window.$message?.warning('请先选择工作流'); return; }
  saving.value = true;
  try {
    syncMcpToolsFromNodes();
    const { error, data } = await fetchSaveAgentWorkflow({
      workflowId: designer.workflowId,
      name: designer.name,
      description: designer.description,
      type: designer.type,
      status: designer.status,
      ownerName: designer.ownerName,
      permissionScope: designer.permissionScope,
      tags: designer.tags,
      knowledgeBases: designer.knowledgeBases,
      promptRefs: designer.promptRefs,
      mcpTools: designer.mcpTools,
      skillRefs: designer.skillRefs,
      models: designer.models,
      nodesJson: JSON.stringify(designer.nodes),
      edgesJson: JSON.stringify(designer.edges)
    });
    if (!error && data) {
      window.$message?.success('保存成功');
      await loadData();
      applyWorkflow(data);
      captureDesignerSnapshot();
    }
  } finally { saving.value = false; }
}

async function copyWorkflow(workflowId: string) {
  const { error } = await fetchCopyAgentWorkflow(workflowId);
  if (!error) { window.$message?.success('复制成功'); await loadData(); }
}

async function publishWorkflow(workflowId: string) {
  const { error } = await fetchPublishAgentWorkflow(workflowId);
  if (!error) {
    window.$message?.success('发布成功');
    await loadData();
    if (activeView.value === 'designer') activeView.value = 'list';
  }
}

function goToRuntime(workflow?: Partial<Api.AgentCenter.Workflow> | null) {
  const runtimeWorkflowId = workflow?.workflowId || '';
  if (!runtimeWorkflowId) {
    window.$message?.warning('请先选择一个工作流');
    return;
  }
  router.push({
    path: '/ai-center/runtime-center',
    query: {
      targetType: 'workflow',
      targetId: runtimeWorkflowId
    }
  });
}

async function deleteWorkflow(workflowId: string) {
  window.$dialog?.warning({
    title: '删除工作流',
    content: '确认删除该工作流吗？此操作不可恢复。',
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      const { error } = await fetchDeleteAgentWorkflow(workflowId);
      if (!error) {
        window.$message?.success('删除成功');
        if (selectedWorkflow.value?.workflowId === workflowId) selectedWorkflow.value = null;
        await loadData();
      }
    }
  });
}

// ─── 设计器操作 ───
function addNode(type: string) {
  if (!designer.workflowId) { window.$message?.warning('请先选择或创建工作流'); return; }
  const index = designer.nodes.length + 1;
  const id = `${type}_${Date.now()}`;
  designer.nodes.push({ id, type, name: type, x: Math.min(3064, 120 + index * 48), y: Math.min(2332, 120 + index * 72), description: '', config: defaultNodeConfig(type) });
  const previous = designer.nodes[designer.nodes.length - 2];
  if (previous) designer.edges.push({ source: previous.id, target: id });
  designer.selectedNodeId = id;
}

function deleteSelectedNode() {
  if (!designer.selectedNodeId) return;
  const node = designer.nodes.find((n: any) => n.id === designer.selectedNodeId);
  if (!node || node.type.includes('开始')) { window.$message?.warning('开始节点不能删除'); return; }
  designer.nodes = designer.nodes.filter((n: any) => n.id !== designer.selectedNodeId);
  designer.edges = designer.edges.filter((e: any) => e.source !== designer.selectedNodeId && e.target !== designer.selectedNodeId);
  designer.selectedNodeId = designer.nodes[0]?.id || '';
}

function addEdge(edge: { source: string; target: string }) {
  const exists = designer.edges.some((e: any) => e.source === edge.source && e.target === edge.target);
  if (!exists && edge.source !== edge.target) {
    designer.edges.push(edge);
  }
}

function deleteEdge(index: number) {
  designer.edges.splice(index, 1);
}

function startNodeDrag(event: MouseEvent, node: WorkflowNode) {
  if (event.button !== 0) return;
  event.preventDefault();
  event.stopPropagation();
  const canvasEl = document.querySelector('.workflow-canvas-empty') as HTMLElement;
  const rect = canvasEl?.getBoundingClientRect();
  if (!rect) return;
  const point = screenToWorld(event.clientX, event.clientY, rect);
  dragging.value = { nodeId: node.id, offsetX: point.x - node.x, offsetY: point.y - node.y };
  const onMove = (e: MouseEvent) => {
    const n = designer.nodes.find((item: any) => item.id === dragging.value?.nodeId);
    if (!n) return;
    const p = screenToWorld(e.clientX, e.clientY, rect);
    n.x = snap(Math.min(3064, Math.max(0, p.x - dragging.value.offsetX)));
    n.y = snap(Math.min(2332, Math.max(0, p.y - dragging.value.offsetY)));
  };
  const onUp = () => {
    dragging.value = null;
    window.removeEventListener('mousemove', onMove);
    window.removeEventListener('mouseup', onUp);
  };
  window.addEventListener('mousemove', onMove);
  window.addEventListener('mouseup', onUp);
}

function autoLayout() {
  const canvasWidth = document.querySelector('.workflow-canvas-empty')?.clientWidth || 900;
  const nodeWidth = 136, gapX = 72, gapY = 92, paddingX = 64, paddingY = 96;
  const columnCount = Math.max(1, Math.floor((canvasWidth - paddingX * 2 + gapX) / (nodeWidth + gapX)));
  designer.nodes.forEach((node: any, index: number) => {
    const col = index % columnCount;
    const row = Math.floor(index / columnCount);
    node.x = paddingX + col * (nodeWidth + gapX);
    node.y = paddingY + row * (68 + gapY);
  });
}

async function runDebug() {
  if (!designer.workflowId) { window.$message?.warning('请先选择或保存工作流'); return; }
  debugLoading.value = true;
  try {
    const { error, data } = await fetchDebugAgentWorkflow(designer.workflowId, { query: testQuery.value });
    if (!error && data) {
      debugResult.value = data;
      if (data.executionId) currentExecutionId.value = data.executionId;
      executionMonitorVisible.value = true;
    }
  } finally { debugLoading.value = false; }
}

function formatTime(time: string | undefined | null): string {
  if (!time) return '-';
  return dayjs(time).format('MM-DD HH:mm');
}

function getNodeCount(wf: Api.AgentCenter.Workflow): number {
  try { return wf.nodesJson ? JSON.parse(wf.nodesJson).length : 0; } catch { return 0; }
}

function getEdgeCount(wf: Api.AgentCenter.Workflow): number {
  try { return wf.edgesJson ? JSON.parse(wf.edgesJson).length : 0; } catch { return 0; }
}

function splitComma(val: string | undefined) {
  return val ? val.split(',').map(item => item.trim()).filter(Boolean) : [];
}

function joinComma(values: string[]) {
  return values.filter(Boolean).join(',');
}

function getOptionLabel(options: SelectOption[], value: string) {
  return options.find(item => item.value === value)?.label || value;
}

function syncMcpToolsFromNodes() {
  const existing = splitComma(designer.mcpTools);
  const nodeTools = designer.nodes
    .filter((node: any) => node.type === 'MCP工具')
    .map((node: any) => String(node.config?.toolId || '').trim())
    .filter(Boolean);
  designer.mcpTools = joinComma([...new Set([...existing, ...nodeTools])]);
}

onMounted(() => { loadData(); });
</script>

<template>
  <div class="h-full flex flex-col bg-[#f5f7fa] dark:bg-[#101014]">
    <!-- ==================== 列表视图 ==================== -->
    <template v-if="activeView === 'list'">
      <div class="h-full flex">
        <!-- 左侧分类导航 -->
        <div class="w-180px flex-shrink-0 border-r border-gray-200 dark:border-gray-700 bg-white dark:bg-[#18181c] flex flex-col">
          <div class="px-4 pt-4 pb-2">
            <h2 class="text-sm font-semibold text-gray-800 dark:text-gray-100 mb-3">工作流分类</h2>
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
                placeholder="搜索工作流名称或描述..."
                clearable
                class="w-200px"
                size="small"
                @keyup.enter="searchWorkflows"
              >
                <template #prefix>
                  <icon-carbon:search class="text-gray-400" />
                </template>
              </NInput>
              <NButton size="small" @click="searchWorkflows">搜索</NButton>
            </div>
            <NButton size="small" type="primary" @click="openCreate">
              <template #icon><icon-carbon:add /></template>
              创建工作流
            </NButton>
          </div>

          <NScrollbar class="flex-1">
            <div class="p-4">
              <NSpin :show="loading">
                <div v-if="filteredWorkflows.length === 0 && !loading" class="py-20">
                  <NEmpty description="暂无工作流">
                    <template #extra>
                      <NButton size="small" type="primary" @click="openCreate">创建工作流</NButton>
                    </template>
                  </NEmpty>
                </div>
                <div v-else class="grid grid-cols-1 xl:grid-cols-2 gap-3">
                  <div
                    v-for="item in filteredWorkflows"
                    :key="item.workflowId"
                    class="cursor-pointer rounded-xl border bg-white p-4 transition-all hover:shadow-md dark:bg-[#1e1e22]"
                    :class="selectedWorkflow?.workflowId === item.workflowId
                      ? 'border-primary-400 shadow-sm ring-1 ring-primary-200 dark:border-primary-500 dark:ring-primary-800'
                      : 'border-gray-200 hover:border-primary-300 dark:border-gray-700 dark:hover:border-primary-600'"
                    @click="selectWorkflow(item)"
                  >
                    <div class="flex items-start justify-between mb-2">
                      <div class="flex items-center gap-2 flex-1 min-w-0">
                        <div class="w-8 h-8 rounded-lg bg-primary-50 dark:bg-primary-900/30 flex items-center justify-center flex-shrink-0 text-lg">
                          <icon-carbon:flow class="text-base text-primary-500" />
                        </div>
                        <div class="min-w-0">
                          <h3 class="text-sm font-semibold text-gray-900 dark:text-gray-100 truncate">{{ item.name }}</h3>
                          <p v-if="item.description" class="mt-0.5 text-xs text-gray-500 dark:text-gray-400 line-clamp-1">{{ item.description }}</p>
                        </div>
                      </div>
                      <div class="ml-2 flex flex-shrink-0 items-center gap-1">
                        <FavoriteButton
                          type="workflow"
                          :target-id="item.workflowId"
                          :title="item.name"
                          :description="item.description"
                          :meta="item.type || item.status || ''"
                        />
                        <NTag
                          :type="item.status === '运行中' ? 'success' : 'default'"
                          size="small"
                          :bordered="false"
                        >
                          {{ item.status || '草稿' }}
                        </NTag>
                      </div>
                    </div>

                    <div class="flex items-center justify-between text-xs text-gray-400 dark:text-gray-500 mt-2">
                      <div class="flex items-center gap-3">
                        <span class="flex items-center gap-1">
                          <icon-carbon:tag class="text-sm" />
                          {{ item.type || '未分类' }}
                        </span>
                        <span class="flex items-center gap-1">
                          <icon-carbon:flow class="text-sm" />
                          {{ getNodeCount(item) }} 节点
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
          <template v-if="selectedWorkflow">
            <div class="px-5 py-3 border-b border-gray-100 dark:border-gray-700">
              <h2 class="text-sm font-semibold text-gray-800 dark:text-gray-100">工作流详情</h2>
            </div>

            <NScrollbar class="flex-1">
              <div class="p-5 space-y-4">
                <!-- 头像 + 名称 -->
                <div class="flex items-center gap-3">
                  <div class="w-12 h-12 rounded-xl bg-primary-50 dark:bg-primary-900/30 flex items-center justify-center flex-shrink-0">
                    <icon-carbon:flow class="text-2xl text-primary-500" />
                  </div>
                  <div>
                    <h3 class="text-base font-semibold text-gray-900 dark:text-gray-50">{{ selectedWorkflow.name }}</h3>
                    <p v-if="selectedWorkflow.description" class="text-sm text-gray-500 dark:text-gray-400 mt-0.5">
                      {{ selectedWorkflow.description }}
                    </p>
                  </div>
                </div>

                <!-- 状态 & 类型 -->
                <div class="flex items-center gap-2">
                  <NTag :type="selectedWorkflow.status === '运行中' ? 'success' : 'default'" :bordered="false" size="small">
                    {{ selectedWorkflow.status || '草稿' }}
                  </NTag>
                  <NTag type="info" :bordered="false" size="small">{{ selectedWorkflow.type || '未分类' }}</NTag>
                </div>

                <NDivider class="!my-2" />

                <!-- 统计信息 -->
                <div class="space-y-2">
                  <div class="flex items-center justify-between text-sm">
                    <span class="text-xs text-gray-500 dark:text-gray-400">调用次数</span>
                    <span class="text-gray-700 dark:text-gray-300">{{ selectedWorkflow.callCount?.toLocaleString() ?? '-' }}</span>
                  </div>
                  <div class="flex items-center justify-between text-sm">
                    <span class="text-xs text-gray-500 dark:text-gray-400">成功率</span>
                    <span :class="(selectedWorkflow.callCount ?? 0) > 0 ? 'text-emerald-600 font-medium' : 'text-gray-400'">
                      {{ (selectedWorkflow.callCount ?? 0) > 0 ? `${selectedWorkflow.successRate ?? 100}%` : '-' }}
                    </span>
                  </div>
                  <div class="flex items-center justify-between text-sm">
                    <span class="text-xs text-gray-500 dark:text-gray-400">平均耗时</span>
                    <span class="text-gray-700 dark:text-gray-300">{{ ((selectedWorkflow.avgDurationMs ?? 0) / 1000).toFixed(1) }}s</span>
                  </div>
                </div>

                <!-- 节点统计 -->
                <NDivider class="!my-2" />
                <div>
                  <div class="mb-2 flex items-center gap-1.5 text-xs font-medium text-gray-500 dark:text-gray-400">
                    <icon-carbon:flow class="text-primary-500" />
                    流程结构
                  </div>
                  <div class="grid grid-cols-2 gap-2">
                    <div class="rounded-lg bg-gray-50 dark:bg-[#1e1e22] px-3 py-2 text-center">
                      <div class="text-lg font-bold text-gray-700 dark:text-gray-300">{{ getNodeCount(selectedWorkflow) }}</div>
                      <div class="text-xs text-gray-400">节点数</div>
                    </div>
                    <div class="rounded-lg bg-gray-50 dark:bg-[#1e1e22] px-3 py-2 text-center">
                      <div class="text-lg font-bold text-gray-700 dark:text-gray-300">{{ getEdgeCount(selectedWorkflow) }}</div>
                      <div class="text-xs text-gray-400">连线数</div>
                    </div>
                  </div>
                </div>

                <!-- 模型参数 -->
                <template v-if="selectedWorkflow.models">
                  <NDivider class="!my-2" />
                  <div>
                    <div class="mb-2 flex items-center gap-1.5 text-xs font-medium text-gray-500 dark:text-gray-400">
                      <icon-carbon:machine-learning class="text-primary-500" />
                      模型
                    </div>
                    <div class="flex flex-wrap gap-1.5">
                      <NTag v-for="m in splitComma(selectedWorkflow.models)" :key="m" size="small" :bordered="false" type="info">
                        {{ m }}
                      </NTag>
                    </div>
                    <div class="mt-2 grid grid-cols-3 gap-2 text-xs">
                      <div v-if="selectedWorkflow.temperature !== undefined" class="rounded-lg bg-gray-50 dark:bg-[#1e1e22] px-2 py-1.5 text-center">
                        <div class="font-medium text-gray-700 dark:text-gray-300">{{ selectedWorkflow.temperature }}</div>
                        <div class="text-gray-400">Temp</div>
                      </div>
                      <div v-if="selectedWorkflow.topP !== undefined" class="rounded-lg bg-gray-50 dark:bg-[#1e1e22] px-2 py-1.5 text-center">
                        <div class="font-medium text-gray-700 dark:text-gray-300">{{ selectedWorkflow.topP }}</div>
                        <div class="text-gray-400">Top P</div>
                      </div>
                      <div v-if="selectedWorkflow.maxTokens !== undefined" class="rounded-lg bg-gray-50 dark:bg-[#1e1e22] px-2 py-1.5 text-center">
                        <div class="font-medium text-gray-700 dark:text-gray-300">{{ selectedWorkflow.maxTokens }}</div>
                        <div class="text-gray-400">Tokens</div>
                      </div>
                    </div>
                  </div>
                </template>

                <!-- 知识库 -->
                <template v-if="selectedWorkflow.knowledgeBases">
                  <NDivider class="!my-2" />
                  <div>
                    <div class="mb-2 flex items-center gap-1.5 text-xs font-medium text-gray-500 dark:text-gray-400">
                      <icon-carbon:data-base class="text-primary-500" />
                      知识库
                    </div>
                    <div class="flex flex-wrap gap-1.5">
                      <NTag v-for="kb in splitComma(selectedWorkflow.knowledgeBases)" :key="kb" size="small" :bordered="false" type="warning">
                        {{ kb }}
                      </NTag>
                    </div>
                  </div>
                </template>

                <!-- 工具绑定 -->
                <template v-if="selectedWorkflow.mcpTools">
                  <NDivider class="!my-2" />
                  <div>
                    <div class="mb-2 flex items-center gap-1.5 text-xs font-medium text-gray-500 dark:text-gray-400">
                      <icon-carbon:tool-kit class="text-primary-500" />
                      工具
                    </div>
                    <div class="flex flex-wrap gap-1.5">
                      <NTag v-for="tool in splitComma(selectedWorkflow.mcpTools)" :key="tool" size="small" :bordered="false">
                        {{ tool }}
                      </NTag>
                    </div>
                  </div>
                </template>

                <template v-if="selectedWorkflow.skillRefs">
                  <NDivider class="!my-2" />
                  <div>
                    <div class="mb-2 flex items-center gap-1.5 text-xs font-medium text-gray-500 dark:text-gray-400">
                      <icon-carbon:skill-level-advanced class="text-primary-500" />
                      技能
                    </div>
                    <div class="flex flex-wrap gap-1.5">
                      <NTag v-for="skill in splitComma(selectedWorkflow.skillRefs)" :key="skill" size="small" :bordered="false" type="warning">
                        {{ getOptionLabel(skillOptions, skill) }}
                      </NTag>
                    </div>
                  </div>
                </template>

                <NDivider class="!my-2" />

                <!-- 时间信息 -->
                <div class="space-y-1.5 text-xs text-gray-400 dark:text-gray-500">
                  <div class="flex items-center gap-2">
                    <icon-carbon:time class="text-sm" />
                    <span>更新：{{ formatTime(selectedWorkflow.updatedAt) }}</span>
                  </div>
                  <div class="flex items-center gap-2">
                    <icon-carbon:identification class="text-sm" />
                    <span>ID：{{ selectedWorkflow.workflowId }}</span>
                  </div>
                </div>

                <!-- 操作按钮 -->
                <div class="flex flex-wrap gap-2 pt-1">
                  <FavoriteButton
                    type="workflow"
                    :target-id="selectedWorkflow.workflowId"
                    :title="selectedWorkflow.name"
                    :description="selectedWorkflow.description"
                    :meta="selectedWorkflow.type || selectedWorkflow.status || ''"
                    size="small"
                    :text="false"
                    show-label
                  />
                  <NButton size="small" type="primary" @click="editWorkflow(selectedWorkflow)">
                    <template #icon><icon-carbon:edit /></template>
                    编辑
                  </NButton>
                  <NButton size="small" secondary type="success" @click="goToRuntime(selectedWorkflow)">
                    <template #icon><icon-carbon:play-filled /></template>
                    去运行
                  </NButton>
                  <NButton size="small" secondary @click="copyWorkflow(selectedWorkflow.workflowId)">
                    <template #icon><icon-carbon:copy /></template>
                    复制
                  </NButton>
                  <NButton size="small" secondary type="success" @click="publishWorkflow(selectedWorkflow.workflowId)">
                    <template #icon><icon-carbon:launch /></template>
                    发布
                  </NButton>
                  <NButton size="small" secondary type="error" @click="deleteWorkflow(selectedWorkflow.workflowId)">
                    <template #icon><icon-carbon:trash-can /></template>
                    删除
                  </NButton>
                </div>
              </div>
            </NScrollbar>
          </template>

          <template v-else>
            <div class="flex flex-1 items-center justify-center">
              <NEmpty description="选择一个工作流查看详情">
                <template #icon>
                  <icon-carbon:flow class="text-4xl text-gray-300 dark:text-gray-600" />
                </template>
              </NEmpty>
            </div>
          </template>
        </div>
      </div>
    </template>

    <!-- ==================== 设计器视图 ==================== -->
    <template v-else-if="activeView === 'designer'">
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
              <div class="flex h-8 w-8 items-center justify-center rounded-lg bg-primary-50 dark:bg-primary-900/30">
                <icon-carbon:flow class="text-lg text-primary-500" />
              </div>
              <span class="text-base font-semibold text-gray-800 dark:text-gray-100">{{ designer.name }}</span>
              <span class="text-xs text-gray-400">{{ designer.type || '未分类' }}</span>
            </div>
          </div>
          <div class="flex items-center gap-2">
            <NButton size="small" @click="versionDrawerVisible = true">
              <template #icon><icon-carbon:time /></template>
              版本历史
            </NButton>
            <NButton size="small" :loading="saving" type="primary" @click="saveDesigner">
              <template #icon><icon-carbon:save /></template>
              保存
            </NButton>
            <NButton size="small" type="success" @click="publishWorkflow(designer.workflowId)">
              <template #icon><icon-carbon:launch /></template>
              发布
            </NButton>
            <NButton size="small" secondary type="success" @click="goToRuntime({ workflowId: designer.workflowId })">
              <template #icon><icon-carbon:play-filled /></template>
              运行界面
            </NButton>
          </div>
        </div>

        <!-- 设计器组件 -->
        <WorkflowDesigner
          :designer="designer"
          :has-selected-workflow="hasSelectedWorkflow"
          :selected-node="selectedNode"
          :model-options="modelOptions"
          :knowledge-base-options="knowledgeBaseOptions"
          :prompt-options="promptOptions"
          :mcp-tool-options="mcpToolOptions"
          :skill-options="skillOptions"
          :saving="saving"
          :debug-loading="debugLoading"
          :test-query="testQuery"
          :debug-result="debugResult"
          @save="saveDesigner"
          @run-debug="runDebug"
          @update:test-query="testQuery = $event"
          @add-node="addNode"
          @select-node="designer.selectedNodeId = $event"
          @delete-node="deleteSelectedNode"
          @auto-layout="autoLayout"
          @start-node-drag="startNodeDrag"
          @add-edge="addEdge"
          @delete-edge="deleteEdge"
        />
      </div>
    </template>

    <!-- 创建弹窗 -->
    <NModal v-model:show="createVisible" preset="card" title="创建工作流" class="max-w-480px">
      <div class="space-y-4">
        <div>
          <div class="mb-1.5 text-xs font-medium text-gray-500 dark:text-gray-400">工作流名称 <span class="text-red-500">*</span></div>
          <NInput v-model:value="createForm.name" placeholder="例如：智能客服工作流" @keyup.enter="createWorkflow" />
        </div>
        <div>
          <div class="mb-1.5 text-xs font-medium text-gray-500 dark:text-gray-400">工作流类型</div>
          <NSelect v-model:value="createForm.type" :options="['知识问答', '多Agent', '工作流'].map(v => ({ label: v, value: v }))" placeholder="选择类型" />
        </div>
        <div>
          <div class="mb-1.5 text-xs font-medium text-gray-500 dark:text-gray-400">描述</div>
          <NInput v-model:value="createForm.description" type="textarea" :rows="2" placeholder="描述该工作流的用途（可选）" />
        </div>
      </div>
      <template #footer>
        <div class="flex justify-end gap-3">
          <NButton @click="createVisible = false">取消</NButton>
          <NButton type="primary" @click="createWorkflow">创建</NButton>
        </div>
      </template>
    </NModal>

    <!-- 版本历史抽屉 -->
    <VersionHistoryDrawer
      v-model:visible="versionDrawerVisible"
      :workflow-id="designer.workflowId"
      @rollback="loadData"
    />

    <!-- 执行监控弹窗 -->
    <ExecutionMonitor
      v-model:visible="executionMonitorVisible"
      :workflow-id="designer.workflowId"
      :execution-id="currentExecutionId"
    />
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

<script setup lang="tsx">
import { NButton, NDataTable, NEmpty, NInput, NInputNumber, NModal, NPagination, NSelect, NSpace, NTabPane, NTabs, NTag } from 'naive-ui';
import {
  fetchAgentModels,
  fetchAgentWorkflowStats,
  fetchAgentWorkflows,
  fetchCopyAgentWorkflow,
  fetchDebugAgentWorkflow,
  fetchDeleteAgentWorkflow,
  fetchMcpTools,
  fetchPromptTemplates,
  fetchPublishAgentWorkflow,
  fetchSaveAgentWorkflow
} from '@/service/api/agent-center';
import { fetchGetKnowledgeBases } from '@/service/api/knowledge-base';

type WorkflowNode = {
  id: string;
  type: string;
  name: string;
  x: number;
  y: number;
  description?: string;
  config?: Record<string, any>;
};

type WorkflowEdge = {
  source: string;
  target: string;
};

type SelectOption = {
  label: string;
  value: string;
  disabled?: boolean;
};

const stats = ref<Api.AgentCenter.WorkflowStats>({
  agentCount: 0,
  runCount: 0,
  successRate: 100,
  avgDurationMs: 0
});
const selectedWorkflow = ref<Api.AgentCenter.Workflow | null>(null);
const knowledgeBaseOptions = ref<SelectOption[]>([]);
const promptOptions = ref<SelectOption[]>([]);
const mcpToolOptions = ref<SelectOption[]>([]);
const modelOptions = ref<SelectOption[]>([]);
const keyword = ref('');
const activeTab = ref('list');
const pendingTab = ref('');
const designerSnapshot = ref('');
const selectedWorkflowId = ref('');
const saving = ref(false);
const debugLoading = ref(false);
const testQuery = ref('');
const debugResult = ref<Api.AgentCenter.DebugResult | null>(null);
const createVisible = ref(false);
const createForm = ref({ name: '', type: '工作流', description: '' });
const canvasRef = ref<HTMLElement | null>(null);
const dragging = ref<{ nodeId: string; offsetX: number; offsetY: number } | null>(null);
const panning = ref<{ startX: number; startY: number; panX: number; panY: number } | null>(null);
const viewport = reactive({
  x: 20,
  y: 20,
  scale: 1
});

const designer = reactive({
  workflowId: '',
  name: '',
  description: '',
  type: '',
  status: '',
  ownerName: '',
  permissionScope: '',
  tags: '',
  knowledgeBases: '',
  promptRefs: '',
  mcpTools: '',
  models: '',
  selectedNodeId: '',
  nodes: [] as WorkflowNode[],
  edges: [] as WorkflowEdge[]
});

const nodeGroups = [
  { title: '基础节点', nodes: ['开始', '结束', '变量', '条件判断', '循环', '延迟'] },
  { title: 'AI节点', nodes: ['LLM', '知识库检索', 'Prompt模板', 'Agent调用'] },
  { title: '工具节点', nodes: ['MCP工具', 'HTTP请求', 'SQL查询', 'Python执行', '代码执行'] },
  { title: '企业节点', nodes: ['审批', '消息通知', '邮件发送', 'Webhook', '飞书通知', '企业微信通知'] }
];

const hasSelectedWorkflow = computed(() => Boolean(selectedWorkflowId.value && designer.workflowId));
const selectedNode = computed(() => designer.nodes.find(item => item.id === designer.selectedNodeId) || designer.nodes[0]);
const viewportStyle = computed(() => ({
  transform: `translate(${viewport.x}px, ${viewport.y}px) scale(${viewport.scale})`,
  transformOrigin: '0 0'
}));
const designerDirty = computed(() => designerSnapshot.value !== getDesignerSnapshot());

const { columns, data: workflows, getData: getWorkflowData, getDataByPage, loading, mobilePagination, updateSearchParams } = useTable({
  apiFn: fetchAgentWorkflows,
  immediate: false,
  columns: () => [
    {
      key: 'name',
      title: '名称',
      width: 180,
      render: row => (
        <div class="flex flex-col">
          <span class="font-medium text-gray-900 dark:text-white">{row.name}</span>
          <span class="text-xs text-gray-500">{row.description || '-'}</span>
        </div>
      )
    },
    { key: 'type', title: '类型', width: 110 },
    {
      key: 'status',
      title: '状态',
      width: 90,
      render: row => <NTag type={row.status === '运行中' ? 'success' : 'warning'} size="small">{row.status}</NTag>
    },
    { key: 'callCount', title: '调用量', width: 90, align: 'center' },
    {
      key: 'updatedAt',
      title: '更新时间',
      width: 150,
      render: row => row.updatedAt ? dayjs(row.updatedAt).format('YYYY-MM-DD HH:mm') : '-'
    },
    {
      key: 'operate',
      title: '操作',
      width: 240,
      render: row => (
        <div class="flex items-center gap-2">
          <NButton text size="small" type="primary" onClick={() => editWorkflow(row)}>编辑</NButton>
          <NButton text size="small" onClick={() => copyWorkflow(row.workflowId)}>复制</NButton>
          <NButton text size="small" type="success" onClick={() => publishWorkflow(row.workflowId)}>发布</NButton>
          <NButton text size="small" type="error" onClick={() => deleteWorkflow(row.workflowId)}>删除</NButton>
        </div>
      )
    }
  ]
});

async function loadData() {
  const [statsRes, kbRes, promptRes, toolRes, modelRes] = await Promise.all([
    fetchAgentWorkflowStats(),
    fetchGetKnowledgeBases({ page: 1, size: 100 }),
    fetchPromptTemplates({ page: 1, size: 100 }),
    fetchMcpTools({ page: 1, size: 100 }),
    fetchAgentModels(),
    getWorkflowData()
  ]);

  if (!selectedWorkflowId.value) {
    const nextSelected = workflows.value[0];
    if (nextSelected) {
      selectedWorkflowId.value = nextSelected.workflowId;
      applyWorkflow(nextSelected);
    } else {
      resetDesigner();
    }
  } else if (selectedWorkflow.value) {
    const updatedSelected = workflows.value.find(item => item.workflowId === selectedWorkflowId.value);
    if (updatedSelected) applyWorkflow(updatedSelected);
  }

  if (!statsRes.error && statsRes.data) {
    stats.value = statsRes.data;
  }
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

async function searchWorkflows() {
  updateSearchParams({ keyword: keyword.value || undefined });
  await getDataByPage();
}

function getPageRecords<T>(data: Api.Common.PaginatingQueryRecord<T> | { records?: T[]; content?: T[]; data?: T[] }): T[] {
  return data.records || data.content || data.data || [];
}

function emptyOptions(label: string) {
  return [{ label, value: '', disabled: true }];
}

function openCreate() {
  createForm.value = { name: '', type: '工作流', description: '' };
  createVisible.value = true;
}

async function createWorkflow() {
  const { name, type, description } = createForm.value;
  if (!name.trim()) {
    window.$message?.warning('请输入工作流名称');
    return;
  }
  const { error, data } = await fetchSaveAgentWorkflow({
    name,
    type,
    description,
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

function editWorkflow(row: Api.AgentCenter.Workflow) {
  selectedWorkflowId.value = row.workflowId;
  applyWorkflow(row);
  switchTab('designer');
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
  designer.models = row.models || '';
  designer.nodes = safeJson<WorkflowNode[]>(row.nodesJson, defaultNodes()).map(n => ({
    ...n,
    config: n.config ?? defaultNodeConfig(n.type)
  }));
  designer.edges = safeJson(row.edgesJson, defaultEdges());
  designer.selectedNodeId = designer.nodes[0]?.id || '';
  captureDesignerSnapshot();
}

function resetDesigner() {
  selectedWorkflowId.value = '';
  selectedWorkflow.value = null;
  designer.workflowId = '';
  designer.name = '';
  designer.description = '';
  designer.type = '';
  designer.status = '';
  designer.ownerName = '';
  designer.permissionScope = '';
  designer.tags = '';
  designer.knowledgeBases = '';
  designer.promptRefs = '';
  designer.mcpTools = '';
  designer.models = '';
  designer.nodes = [];
  designer.edges = [];
  designer.selectedNodeId = '';
  debugResult.value = null;
  captureDesignerSnapshot();
}

async function saveDesigner() {
  if (!designer.workflowId) {
    window.$message?.warning('请先选择一个后端工作流，或新建后再编辑');
    return;
  }
  saving.value = true;
  try {
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
  } finally {
    saving.value = false;
  }
}

async function saveDesignerSilently() {
  if (!designer.workflowId) return false;
  saving.value = true;
  try {
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
      models: designer.models,
      nodesJson: JSON.stringify(designer.nodes),
      edgesJson: JSON.stringify(designer.edges)
    });
    if (!error && data) {
      await loadData();
      applyWorkflow(data);
      captureDesignerSnapshot();
      return true;
    }
    return false;
  } finally {
    saving.value = false;
  }
}

function switchTab(tabName: string) {
  if (tabName === activeTab.value) return;
  if (activeTab.value === 'designer' && designerDirty.value) {
    pendingTab.value = tabName;
    window.$dialog?.warning({
      title: '未保存的编辑',
      content: '当前工作流设计器有未保存的修改。保存后切换，还是放弃本次修改？',
      positiveText: '保存并切换',
      negativeText: '放弃修改',
      onPositiveClick: async () => {
        const success = await saveDesignerSilently();
        if (success) {
          activeTab.value = pendingTab.value;
          pendingTab.value = '';
          return undefined;
        }
        return false;
      },
      onNegativeClick: () => {
        revertDesignerChanges();
        activeTab.value = pendingTab.value;
        pendingTab.value = '';
      }
    });
    return;
  }
  activeTab.value = tabName;
}

function getDesignerSnapshot() {
  return JSON.stringify({
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
    models: designer.models,
    nodes: designer.nodes,
    edges: designer.edges
  });
}

function captureDesignerSnapshot() {
  designerSnapshot.value = getDesignerSnapshot();
}

function revertDesignerChanges() {
  if (!designerSnapshot.value) return;
  const snapshot = JSON.parse(designerSnapshot.value);
  designer.workflowId = snapshot.workflowId;
  designer.name = snapshot.name;
  designer.description = snapshot.description;
  designer.type = snapshot.type;
  designer.status = snapshot.status;
  designer.ownerName = snapshot.ownerName;
  designer.permissionScope = snapshot.permissionScope;
  designer.tags = snapshot.tags;
  designer.knowledgeBases = snapshot.knowledgeBases;
  designer.promptRefs = snapshot.promptRefs;
  designer.mcpTools = snapshot.mcpTools;
  designer.models = snapshot.models;
  designer.nodes = snapshot.nodes;
  designer.edges = snapshot.edges;
  designer.selectedNodeId = designer.nodes[0]?.id || '';
}

async function copyWorkflow(workflowId: string) {
  const { error } = await fetchCopyAgentWorkflow(workflowId);
  if (!error) {
    window.$message?.success('复制成功');
    await loadData();
  }
}

async function publishWorkflow(workflowId: string) {
  const { error } = await fetchPublishAgentWorkflow(workflowId);
  if (!error) {
    window.$message?.success('发布成功');
    await loadData();
  }
}

async function deleteWorkflow(workflowId: string) {
  window.$dialog?.warning({
    title: '删除工作流',
    content: '确认删除该 Agent 工作流吗？',
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      const { error } = await fetchDeleteAgentWorkflow(workflowId);
      if (!error) {
        window.$message?.success('删除成功');
        selectedWorkflowId.value = '';
        await loadData();
      }
    }
  });
}

function defaultNodeConfig(type: string): Record<string, any> {
  switch (type) {
    case '开始':
      return { triggerType: '手动触发', inputSchema: '{"query": "string"}', timeout: 300 };
    case '结束':
      return { outputMode: '直接输出', outputTemplate: '{{llm.output}}' };
    case '变量':
      return { varName: '', varType: 'string', varValue: '', scope: '全局' };
    case '条件判断':
      return { conditionExpr: '{{input.type}} == "A"', trueLabel: '是', falseLabel: '否', elseEnabled: false };
    case '循环':
      return { loopType: '列表循环', iterateVar: '{{input.items}}', loopVar: 'item', maxIterations: 100 };
    case '延迟':
      return { delayMs: 1000, delayType: '固定延迟', unit: 'ms' };
    case 'LLM':
      return { model: '', temperature: 0.7, maxTokens: 2048, topP: 1.0, systemPrompt: '', stream: true, timeout: 60 };
    case '知识库检索':
      return { knowledgeBase: '', searchMode: '混合检索', topK: 5, scoreThreshold: 0.5, rerankEnabled: false };
    case 'Prompt模板':
      return { templateId: '', templateContent: '', inputVars: '{"query": "{{input.query}}"}' };
    case 'Agent调用':
      return { agentId: '', inputMapping: '{"query": "{{input.query}}"}', outputMapping: '{"result": "{{agent.output}}"}', timeout: 120 };
    case 'MCP工具':
      return { toolId: '', inputMapping: '{"query": "{{input.query}}"}', outputField: 'result', retryCount: 1, timeout: 30 };
    case 'HTTP请求':
      return { url: '', method: 'GET', headers: '{"Content-Type": "application/json"}', body: '', authType: 'none', timeout: 30, retryCount: 1 };
    case 'SQL查询':
      return { datasource: '', sql: 'SELECT * FROM table WHERE id = {{input.id}}', resultType: '列表', maxRows: 100 };
    case 'Python执行':
      return { code: '# 可使用 input 变量\nresult = input.get("query", "")', requirements: '', timeout: 30, inputVars: 'input', outputVar: 'result' };
    case '代码执行':
      return { language: 'JavaScript', code: '// 可使用 input 变量\nconst result = input.query;\nreturn { result };', timeout: 30 };
    case '审批':
      return { approvers: '', approvalType: '任一审批', formFields: '{"reason": "string"}', timeout: 86400, autoApprove: false };
    case '消息通知':
      return { channel: '系统通知', recipients: '', title: '', content: '{{input.message}}', priority: '普通' };
    case '邮件发送':
      return { to: '', cc: '', subject: '', body: '', bodyType: 'html', attachments: '' };
    case 'Webhook':
      return { url: '', method: 'POST', headers: '{"Content-Type": "application/json"}', payload: '{{input}}', secret: '', retryCount: 2 };
    case '飞书通知':
      return { webhookUrl: '', msgType: 'text', title: '', content: '{{input.message}}', atAll: false, atUsers: '' };
    case '企业微信通知':
      return { webhookUrl: '', msgType: 'text', content: '{{input.message}}', atUsers: '', atAll: false };
    default:
      return {};
  }
}

function addNode(type: string) {
  if (!designer.workflowId) {
    window.$message?.warning('请先选择一个后端工作流，或新建后再编辑');
    return;
  }
  const index = designer.nodes.length + 1;
  const id = `${type}_${Date.now()}`;
  designer.nodes.push({ id, type, name: type, x: 120 + index * 48, y: 120 + index * 72, description: '', config: defaultNodeConfig(type) });
  const previous = designer.nodes[designer.nodes.length - 2];
  if (previous) {
    designer.edges.push({ source: previous.id, target: id });
  }
  designer.selectedNodeId = id;
}

function startNodeDrag(event: MouseEvent, node: WorkflowNode) {
  if (event.button !== 0) return;
  event.preventDefault();
  event.stopPropagation();
  const rect = canvasRef.value?.getBoundingClientRect();
  if (!rect) return;
  const point = screenToWorld(event.clientX, event.clientY, rect);
  designer.selectedNodeId = node.id;
  dragging.value = {
    nodeId: node.id,
    offsetX: point.x - node.x,
    offsetY: point.y - node.y
  };
  window.addEventListener('mousemove', moveNode);
  window.addEventListener('mouseup', stopNodeDrag);
}

function moveNode(event: MouseEvent) {
  if (!dragging.value || !canvasRef.value) return;
  const node = designer.nodes.find(item => item.id === dragging.value?.nodeId);
  if (!node) return;
  const rect = canvasRef.value.getBoundingClientRect();
  const point = screenToWorld(event.clientX, event.clientY, rect);
  node.x = snap(Math.max(0, point.x - dragging.value.offsetX));
  node.y = snap(Math.max(0, point.y - dragging.value.offsetY));
}

function stopNodeDrag() {
  dragging.value = null;
  window.removeEventListener('mousemove', moveNode);
  window.removeEventListener('mouseup', stopNodeDrag);
}

function startCanvasPan(event: MouseEvent) {
  if (event.button !== 0 && event.button !== 1) return;
  const target = event.target as HTMLElement;
  if (!target.closest('.workflow-canvas-empty')) return;
  event.preventDefault();
  designer.selectedNodeId = '';
  panning.value = {
    startX: event.clientX,
    startY: event.clientY,
    panX: viewport.x,
    panY: viewport.y
  };
  window.addEventListener('mousemove', moveCanvas);
  window.addEventListener('mouseup', stopCanvasPan);
}

function moveCanvas(event: MouseEvent) {
  if (!panning.value) return;
  viewport.x = panning.value.panX + event.clientX - panning.value.startX;
  viewport.y = panning.value.panY + event.clientY - panning.value.startY;
}

function stopCanvasPan() {
  panning.value = null;
  window.removeEventListener('mousemove', moveCanvas);
  window.removeEventListener('mouseup', stopCanvasPan);
}

function handleCanvasWheel(event: WheelEvent) {
  event.preventDefault();
  const rect = canvasRef.value?.getBoundingClientRect();
  if (!rect) return;
  const factor = event.deltaY > 0 ? 0.9 : 1.1;
  zoomCanvas(factor, { clientX: event.clientX, clientY: event.clientY, rect });
}

function zoomCanvas(factor: number, pivot?: { clientX?: number; clientY?: number; rect?: DOMRect }) {
  const canvasRect = pivot?.rect || canvasRef.value?.getBoundingClientRect();
  if (!canvasRect) return;
  const oldScale = viewport.scale;
  const nextScale = Math.min(2, Math.max(0.4, Number((oldScale * factor).toFixed(2))));
  if (nextScale === oldScale) return;
  const pivotX = (pivot?.clientX ?? canvasRect.left + canvasRect.width / 2) - canvasRect.left;
  const pivotY = (pivot?.clientY ?? canvasRect.top + canvasRect.height / 2) - canvasRect.top;
  const worldX = (pivotX - viewport.x) / oldScale;
  const worldY = (pivotY - viewport.y) / oldScale;
  viewport.scale = nextScale;
  viewport.x = pivotX - worldX * nextScale;
  viewport.y = pivotY - worldY * nextScale;
}

function resetViewport() {
  viewport.x = 20;
  viewport.y = 20;
  viewport.scale = 1;
}

function deleteSelectedNode() {
  if (!designer.workflowId) return;
  if (!designer.selectedNodeId) return;
  const node = designer.nodes.find(item => item.id === designer.selectedNodeId);
  if (!node || node.type.includes('开始')) {
    window.$message?.warning('开始节点不能删除');
    return;
  }
  designer.nodes = designer.nodes.filter(item => item.id !== designer.selectedNodeId);
  designer.edges = designer.edges.filter(item => item.source !== designer.selectedNodeId && item.target !== designer.selectedNodeId);
  designer.selectedNodeId = designer.nodes[0]?.id || '';
}

function autoLayout() {
  if (!designer.workflowId) return;
  const canvasWidth = canvasRef.value?.clientWidth || 900;
  const nodeWidth = 136;
  const nodeHeight = 68;
  const gapX = 72;
  const gapY = 92;
  const paddingX = 64;
  const paddingY = 96;
  const usableWidth = Math.max(nodeWidth, canvasWidth - paddingX * 2);
  const columnCount = Math.max(1, Math.floor((usableWidth + gapX) / (nodeWidth + gapX)));

  designer.nodes.forEach((node, index) => {
    const col = index % columnCount;
    const row = Math.floor(index / columnCount);
    node.x = paddingX + col * (nodeWidth + gapX);
    node.y = paddingY + row * (nodeHeight + gapY);
  });
  resetViewport();
}

function getNode(nodeId: string) {
  return designer.nodes.find(item => item.id === nodeId);
}

function edgePath(edge: WorkflowEdge) {
  const source = getNode(edge.source);
  const target = getNode(edge.target);
  if (!source || !target) return '';

  const startX = source.x + 136;
  const startY = source.y + 34;
  const endX = target.x;
  const endY = target.y + 34;
  const distance = Math.max(80, Math.abs(endX - startX));
  const controlOffset = Math.min(180, distance * 0.55);

  if (endX >= startX) {
    return `M ${startX} ${startY} C ${startX + controlOffset} ${startY}, ${endX - controlOffset} ${endY}, ${endX} ${endY}`;
  }

  const loopOffset = Math.max(90, Math.abs(endY - startY) + 60);
  return `M ${startX} ${startY} C ${startX + loopOffset} ${startY}, ${endX - loopOffset} ${endY}, ${endX} ${endY}`;
}

function snap(value: number) {
  return Math.round(value / 10) * 10;
}

function screenToWorld(clientX: number, clientY: number, rect: DOMRect) {
  return {
    x: (clientX - rect.left - viewport.x) / viewport.scale,
    y: (clientY - rect.top - viewport.y) / viewport.scale
  };
}

function handleKeydown(event: KeyboardEvent) {
  if (activeTab.value !== 'designer') return;
  const target = event.target as HTMLElement | null;
  const tagName = target?.tagName?.toLowerCase();
  const isEditing = target?.isContentEditable || tagName === 'input' || tagName === 'textarea';
  if (isEditing) return;

  if (event.key === 'Delete' || event.key === 'Backspace') {
    event.preventDefault();
    deleteSelectedNode();
  }
  if (event.key === 'Escape') {
    designer.selectedNodeId = '';
  }
  if ((event.metaKey || event.ctrlKey) && event.key === '=') {
    event.preventDefault();
    zoomCanvas(1.1);
  }
  if ((event.metaKey || event.ctrlKey) && event.key === '-') {
    event.preventDefault();
    zoomCanvas(0.9);
  }
  if ((event.metaKey || event.ctrlKey) && event.key === '0') {
    event.preventDefault();
    resetViewport();
  }
}

async function runDebug() {
  if (!selectedWorkflowId.value && !designer.workflowId) {
    window.$message?.warning('请先选择或保存一个工作流');
    return;
  }
  debugLoading.value = true;
  try {
    const { error, data } = await fetchDebugAgentWorkflow(selectedWorkflowId.value || designer.workflowId, {
      query: testQuery.value
    });
    if (!error && data) {
      debugResult.value = data;
      switchTab('debug');
    }
  } finally {
    debugLoading.value = false;
  }
}

function nodeClass(type: string) {
  if (type.includes('LLM') || type.includes('知识库') || type.includes('Agent') || type.includes('Prompt')) return 'border-blue-200 bg-blue-50 text-blue-700';
  if (type.includes('MCP') || type.includes('HTTP') || type.includes('SQL') || type.includes('代码') || type.includes('Python')) return 'border-emerald-200 bg-emerald-50 text-emerald-700';
  if (type.includes('审批') || type.includes('通知') || type.includes('邮件') || type.includes('Webhook')) return 'border-amber-200 bg-amber-50 text-amber-700';
  return 'border-gray-200 bg-white text-gray-700';
}

function safeJson<T>(value: string, fallback: T): T {
  try {
    return value ? JSON.parse(value) : fallback;
  } catch {
    return fallback;
  }
}

function defaultNodes(): WorkflowNode[] {
  return [
    { id: 'start', type: '开始', name: '开始', x: 80, y: 140, config: defaultNodeConfig('开始') },
    { id: 'kb', type: '知识库检索', name: '知识库检索', x: 280, y: 140, config: defaultNodeConfig('知识库检索') },
    { id: 'mcp', type: 'MCP工具', name: 'MCP工具', x: 480, y: 140, config: defaultNodeConfig('MCP工具') },
    { id: 'llm', type: 'LLM', name: 'LLM生成', x: 680, y: 140, config: defaultNodeConfig('LLM') },
    { id: 'end', type: '结束', name: '输出结果', x: 880, y: 140, config: defaultNodeConfig('结束') }
  ];
}

function defaultEdges(): WorkflowEdge[] {
  return [
    { source: 'start', target: 'kb' },
    { source: 'kb', target: 'mcp' },
    { source: 'mcp', target: 'llm' },
    { source: 'llm', target: 'end' }
  ];
}

onMounted(() => {
  loadData();
  window.addEventListener('keydown', handleKeydown);
});
onBeforeUnmount(() => {
  stopNodeDrag();
  stopCanvasPan();
  window.removeEventListener('keydown', handleKeydown);
});
</script>

<template>
  <div class="h-full bg-gray-50 dark:bg-gray-900" :class="activeTab === 'designer' ? 'overflow-hidden' : 'overflow-y-auto'">
    <div class="h-full px-8 py-6 flex flex-col">
      <div class="mb-4 flex shrink-0 items-center justify-between">
        <div>
          <h1 class="mb-1 text-2xl font-bold text-gray-900 dark:text-white">Agent工作流</h1>
          <p class="text-sm text-gray-500">编排知识库、LLM、MCP工具和企业流程节点</p>
        </div>
        <NSpace>
          <NButton @click="loadData">
            <template #icon><icon-carbon:renew /></template>
            刷新
          </NButton>
          <NButton type="primary" @click="openCreate">
            <template #icon><icon-carbon:add /></template>
            新建工作流
          </NButton>
        </NSpace>
      </div>

      <div v-if="activeTab === 'list'" class="mb-6 grid shrink-0 grid-cols-4 gap-5">
        <div class="rounded-xl border border-gray-100 bg-white px-6 py-5 shadow-sm dark:border-gray-700 dark:bg-gray-800">
          <div class="mb-1 text-3xl font-bold text-gray-900 dark:text-white">{{ stats.agentCount }}</div>
          <div class="text-sm text-gray-500">Agent数量</div>
        </div>
        <div class="rounded-xl border border-gray-100 bg-white px-6 py-5 shadow-sm dark:border-gray-700 dark:bg-gray-800">
          <div class="mb-1 text-3xl font-bold text-gray-900 dark:text-white">{{ stats.runCount.toLocaleString() }}</div>
          <div class="text-sm text-gray-500">运行次数</div>
        </div>
        <div class="rounded-xl border border-gray-100 bg-white px-6 py-5 shadow-sm dark:border-gray-700 dark:bg-gray-800">
          <div class="mb-1 text-3xl font-bold text-gray-900 dark:text-white">{{ stats.successRate }}%</div>
          <div class="text-sm text-gray-500">成功率</div>
        </div>
        <div class="rounded-xl border border-gray-100 bg-white px-6 py-5 shadow-sm dark:border-gray-700 dark:bg-gray-800">
          <div class="mb-1 text-3xl font-bold text-gray-900 dark:text-white">{{ (stats.avgDurationMs / 1000).toFixed(1) }}s</div>
          <div class="text-sm text-gray-500">平均耗时</div>
        </div>
      </div>

      <div class="min-h-0 flex-1 rounded-xl border border-gray-100 bg-white shadow-sm dark:border-gray-700 dark:bg-gray-800">
        <NTabs :value="activeTab" type="line" animated class="h-full px-5 pt-3" @update:value="switchTab">
          <NTabPane name="list" tab="工作流列表">
            <div class="mb-4 flex items-center justify-between">
              <NInput v-model:value="keyword" clearable placeholder="搜索工作流名称或类型" class="max-w-280px" @keyup.enter="searchWorkflows">
                <template #prefix><icon-carbon:search class="text-gray-400" /></template>
              </NInput>
              <NSpace>
                <NButton>导入</NButton>
                <NButton>导出</NButton>
              </NSpace>
            </div>
            <NDataTable :columns="columns" :data="workflows" :loading="loading" :row-key="row => row.workflowId" :pagination="false" size="small" striped />
            <div class="flex justify-end border-t border-gray-100 px-4 py-3 dark:border-gray-700">
              <NPagination v-bind="mobilePagination" />
            </div>
          </NTabPane>

          <NTabPane name="designer" tab="工作流设计器">
            <div class="designer-shell grid h-[calc(100vh-190px)] min-h-560px grid-cols-[240px_minmax(520px,1fr)_300px] overflow-hidden rounded-lg border border-gray-100 dark:border-gray-700">
              <aside class="overflow-y-auto border-r border-gray-100 bg-gray-50 p-4 dark:border-gray-700 dark:bg-gray-900">
                <div class="mb-3 flex items-center justify-between">
                  <div class="text-sm font-semibold text-gray-900 dark:text-white">节点库</div>
                  <NButton size="tiny" @click="autoLayout">布局</NButton>
                </div>
                <div v-for="group in nodeGroups" :key="group.title" class="mb-5">
                  <div class="mb-2 text-xs text-gray-500">{{ group.title }}</div>
                  <div class="grid gap-2">
                    <button
                      v-for="node in group.nodes"
                      :key="node"
                      class="h-9 rounded-lg border border-gray-200 bg-white px-3 text-left text-sm text-gray-700 transition hover:border-blue-300 hover:text-blue-600 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-200"
                      @click="addNode(node)"
                    >
                      {{ node }}
                    </button>
                  </div>
                </div>
              </aside>

              <main class="flex min-w-0 flex-col bg-white dark:bg-gray-950">
                <div class="flex h-14 shrink-0 items-center justify-between border-b border-gray-100 px-4 dark:border-gray-700">
                  <div class="flex items-center gap-3">
                    <NInput v-model:value="designer.name" class="max-w-220px" :disabled="!hasSelectedWorkflow" placeholder="请选择工作流" />
                    <NTag v-if="designer.type" type="info" size="small">{{ designer.type }}</NTag>
                    <NTag v-if="designer.status" :type="designer.status === '运行中' ? 'success' : 'warning'" size="small">{{ designer.status }}</NTag>
                  </div>
                  <NSpace>
                    <NButton :disabled="!hasSelectedWorkflow" @click="autoLayout">自动布局</NButton>
                    <NButton :disabled="!hasSelectedWorkflow" @click="zoomCanvas(0.9)">缩小</NButton>
                    <NTag size="small">{{ Math.round(viewport.scale * 100) }}%</NTag>
                    <NButton :disabled="!hasSelectedWorkflow" @click="zoomCanvas(1.1)">放大</NButton>
                    <NButton :disabled="!hasSelectedWorkflow" @click="resetViewport">重置视图</NButton>
                    <NButton :disabled="!hasSelectedWorkflow" :loading="saving" type="primary" @click="saveDesigner">保存</NButton>
                    <NButton :disabled="!hasSelectedWorkflow" :loading="debugLoading" @click="runDebug">调试运行</NButton>
                  </NSpace>
                </div>
                <div
                  ref="canvasRef"
                  class="workflow-canvas-empty relative min-h-0 flex-1 cursor-grab overflow-hidden bg-[#f8fafc] bg-[radial-gradient(circle,#d7dde6_1px,transparent_1px)] bg-[length:20px_20px] active:cursor-grabbing dark:bg-gray-950"
                  tabindex="0"
                  @mousedown="startCanvasPan"
                  @wheel="handleCanvasWheel"
                >
                  <div v-if="hasSelectedWorkflow" class="absolute left-0 top-0 h-2400px w-3200px" :style="viewportStyle">
                    <svg class="absolute inset-0 h-full w-full pointer-events-none">
                      <defs>
                        <marker id="workflow-arrow" markerHeight="8" markerWidth="8" orient="auto" refX="8" refY="4">
                          <path d="M0,0 L8,4 L0,8 Z" fill="#94a3b8" />
                        </marker>
                      </defs>
                      <path
                        v-for="edge in designer.edges"
                        :key="`${edge.source}-${edge.target}`"
                        :d="edgePath(edge)"
                        fill="none"
                        stroke="#94a3b8"
                        stroke-width="2"
                        stroke-linecap="round"
                        marker-end="url(#workflow-arrow)"
                      />
                    </svg>
                    <button
                      v-for="node in designer.nodes"
                      :key="node.id"
                      class="absolute h-68px w-136px cursor-move rounded-lg border px-3 text-left shadow-sm transition select-none hover:shadow-md"
                      :class="[nodeClass(node.type), designer.selectedNodeId === node.id ? 'ring-2 ring-blue-400' : '']"
                      :style="{ left: `${node.x}px`, top: `${node.y}px` }"
                      @click.stop="designer.selectedNodeId = node.id"
                      @mousedown="startNodeDrag($event, node)"
                    >
                      <div class="text-sm font-semibold">{{ node.name }}</div>
                      <div class="text-xs opacity-70">{{ node.type }}</div>
                    </button>
                  </div>
                  <div v-if="!designer.nodes.length" class="absolute inset-0 flex items-center justify-center text-sm text-gray-400">
                    <NEmpty :description="hasSelectedWorkflow ? '从左侧节点库添加节点' : '暂无后端工作流数据'">
                      <template #extra>
                        <NButton size="small" type="primary" @click="openCreate">新建工作流</NButton>
                      </template>
                    </NEmpty>
                  </div>
                  <div class="pointer-events-none absolute bottom-3 left-3 rounded border border-gray-200 bg-white/90 px-3 py-2 text-xs text-gray-500 shadow-sm dark:border-gray-700 dark:bg-gray-900/90">
                    拖动画布平移 · 滚轮缩放 · Delete 删除节点
                  </div>
                </div>
              </main>

              <aside class="overflow-y-auto border-l border-gray-100 bg-white p-4 dark:border-gray-700 dark:bg-gray-800">
                <div class="mb-4 flex items-center justify-between">
                  <div class="text-sm font-semibold text-gray-900 dark:text-white">属性配置</div>
                  <NButton size="tiny" type="error" ghost :disabled="!hasSelectedWorkflow || !selectedNode" @click="deleteSelectedNode">删除</NButton>
                </div>
                <div v-if="hasSelectedWorkflow && selectedNode" class="space-y-3">
                  <!-- 通用属性 -->
                  <div class="border-b border-gray-100 pb-3 dark:border-gray-700">
                    <div class="mb-2 text-xs font-medium text-gray-500 uppercase tracking-wide">基本信息</div>
                    <NInput v-model:value="selectedNode.name" class="mb-2" placeholder="节点名称" />
                    <NInput :value="selectedNode.id" disabled class="mb-2" placeholder="节点ID" />
                    <NInput v-model:value="selectedNode.description" type="textarea" :rows="2" placeholder="节点描述（可选）" />
                  </div>

                  <!-- 坐标 -->
                  <div class="border-b border-gray-100 pb-3 dark:border-gray-700">
                    <div class="mb-2 text-xs font-medium text-gray-500 uppercase tracking-wide">位置</div>
                    <div class="grid grid-cols-2 gap-2">
                      <NInputNumber v-model:value="selectedNode.x" :min="0" class="w-full" placeholder="X" />
                      <NInputNumber v-model:value="selectedNode.y" :min="0" class="w-full" placeholder="Y" />
                    </div>
                  </div>

                  <!-- 开始节点 -->
                  <template v-if="selectedNode.type === '开始'">
                    <div class="mb-2 text-xs font-medium text-gray-500 uppercase tracking-wide">触发配置</div>
                    <NSelect
                      v-model:value="selectedNode.config!.triggerType"
                      :options="['手动触发', '定时触发', 'API触发', 'Webhook触发'].map(v => ({ label: v, value: v }))"
                      placeholder="触发方式"
                    />
                    <NInput v-model:value="selectedNode.config!.inputSchema" type="textarea" :rows="3" placeholder="输入参数 Schema（JSON）" />
                    <div class="flex items-center gap-2">
                      <span class="text-sm text-gray-600 dark:text-gray-400 shrink-0">超时(秒)</span>
                      <NInputNumber v-model:value="selectedNode.config!.timeout" :min="1" :max="3600" class="flex-1" />
                    </div>
                  </template>

                  <!-- 结束节点 -->
                  <template v-if="selectedNode.type === '结束'">
                    <div class="mb-2 text-xs font-medium text-gray-500 uppercase tracking-wide">输出配置</div>
                    <NSelect
                      v-model:value="selectedNode.config!.outputMode"
                      :options="['直接输出', '模板渲染', '变量映射'].map(v => ({ label: v, value: v }))"
                      placeholder="输出模式"
                    />
                    <NInput v-model:value="selectedNode.config!.outputTemplate" type="textarea" :rows="3" placeholder="输出模板，支持 {{变量}} 语法" />
                  </template>

                  <!-- 变量节点 -->
                  <template v-if="selectedNode.type === '变量'">
                    <div class="mb-2 text-xs font-medium text-gray-500 uppercase tracking-wide">变量配置</div>
                    <NInput v-model:value="selectedNode.config!.varName" placeholder="变量名称（如：userInput）" />
                    <NSelect
                      v-model:value="selectedNode.config!.varType"
                      :options="['string', 'number', 'boolean', 'array', 'object'].map(v => ({ label: v, value: v }))"
                      placeholder="变量类型"
                    />
                    <NInput v-model:value="selectedNode.config!.varValue" type="textarea" :rows="2" placeholder="默认值（可使用 {{上游变量}} 引用）" />
                    <NSelect
                      v-model:value="selectedNode.config!.scope"
                      :options="['全局', '流程内', '当前节点'].map(v => ({ label: v, value: v }))"
                      placeholder="作用域"
                    />
                  </template>

                  <!-- 条件判断节点 -->
                  <template v-if="selectedNode.type === '条件判断'">
                    <div class="mb-2 text-xs font-medium text-gray-500 uppercase tracking-wide">条件配置</div>
                    <NInput v-model:value="selectedNode.config!.conditionExpr" type="textarea" :rows="2" placeholder="条件表达式，如：{{input.score}} >= 60" />
                    <div class="grid grid-cols-2 gap-2">
                      <NInput v-model:value="selectedNode.config!.trueLabel" placeholder="是（True）分支名" />
                      <NInput v-model:value="selectedNode.config!.falseLabel" placeholder="否（False）分支名" />
                    </div>
                  </template>

                  <!-- 循环节点 -->
                  <template v-if="selectedNode.type === '循环'">
                    <div class="mb-2 text-xs font-medium text-gray-500 uppercase tracking-wide">循环配置</div>
                    <NSelect
                      v-model:value="selectedNode.config!.loopType"
                      :options="['列表循环', '计数循环', '条件循环'].map(v => ({ label: v, value: v }))"
                      placeholder="循环类型"
                    />
                    <NInput v-model:value="selectedNode.config!.iterateVar" placeholder="迭代变量（如：{{input.items}}）" />
                    <NInput v-model:value="selectedNode.config!.loopVar" placeholder="循环项变量名（如：item）" />
                    <div class="flex items-center gap-2">
                      <span class="text-sm text-gray-600 dark:text-gray-400 shrink-0">最大迭代次数</span>
                      <NInputNumber v-model:value="selectedNode.config!.maxIterations" :min="1" :max="10000" class="flex-1" />
                    </div>
                  </template>

                  <!-- 延迟节点 -->
                  <template v-if="selectedNode.type === '延迟'">
                    <div class="mb-2 text-xs font-medium text-gray-500 uppercase tracking-wide">延迟配置</div>
                    <NSelect
                      v-model:value="selectedNode.config!.delayType"
                      :options="['固定延迟', '随机延迟'].map(v => ({ label: v, value: v }))"
                      placeholder="延迟类型"
                    />
                    <div class="flex items-center gap-2">
                      <NInputNumber v-model:value="selectedNode.config!.delayMs" :min="0" :max="300000" class="flex-1" placeholder="延迟时长" />
                      <NSelect
                        v-model:value="selectedNode.config!.unit"
                        class="w-20"
                        :options="['ms', 's', 'min'].map(v => ({ label: v, value: v }))"
                      />
                    </div>
                  </template>

                  <!-- LLM 节点 -->
                  <template v-if="selectedNode.type === 'LLM'">
                    <div class="mb-2 text-xs font-medium text-gray-500 uppercase tracking-wide">模型配置</div>
                    <NSelect
                      v-model:value="selectedNode.config!.model"
                      clearable
                      :options="modelOptions.length ? modelOptions : emptyOptions('暂无可用模型')"
                      placeholder="选择模型"
                    />
                    <div class="flex items-center gap-2">
                      <span class="text-sm text-gray-600 dark:text-gray-400 shrink-0 w-24">Temperature</span>
                      <NInputNumber v-model:value="selectedNode.config!.temperature" :min="0" :max="2" :step="0.1" :precision="1" class="flex-1" />
                    </div>
                    <div class="flex items-center gap-2">
                      <span class="text-sm text-gray-600 dark:text-gray-400 shrink-0 w-24">最大Token数</span>
                      <NInputNumber v-model:value="selectedNode.config!.maxTokens" :min="1" :max="32768" class="flex-1" />
                    </div>
                    <div class="flex items-center gap-2">
                      <span class="text-sm text-gray-600 dark:text-gray-400 shrink-0 w-24">Top P</span>
                      <NInputNumber v-model:value="selectedNode.config!.topP" :min="0" :max="1" :step="0.05" :precision="2" class="flex-1" />
                    </div>
                    <NInput v-model:value="selectedNode.config!.systemPrompt" type="textarea" :rows="4" placeholder="系统提示词（System Prompt）" />
                    <div class="flex items-center gap-2">
                      <span class="text-sm text-gray-600 dark:text-gray-400 shrink-0 w-24">超时(秒)</span>
                      <NInputNumber v-model:value="selectedNode.config!.timeout" :min="1" :max="600" class="flex-1" />
                    </div>
                  </template>

                  <!-- 知识库检索节点 -->
                  <template v-if="selectedNode.type === '知识库检索'">
                    <div class="mb-2 text-xs font-medium text-gray-500 uppercase tracking-wide">检索配置</div>
                    <NSelect
                      v-model:value="selectedNode.config!.knowledgeBase"
                      clearable
                      filterable
                      :options="knowledgeBaseOptions.length ? knowledgeBaseOptions : emptyOptions('暂无可访问知识库')"
                      placeholder="选择知识库"
                    />
                    <NSelect
                      v-model:value="selectedNode.config!.searchMode"
                      :options="['向量检索', '关键词检索', '混合检索'].map(v => ({ label: v, value: v }))"
                      placeholder="检索模式"
                    />
                    <div class="flex items-center gap-2">
                      <span class="text-sm text-gray-600 dark:text-gray-400 shrink-0 w-24">返回条数 (TopK)</span>
                      <NInputNumber v-model:value="selectedNode.config!.topK" :min="1" :max="20" class="flex-1" />
                    </div>
                    <div class="flex items-center gap-2">
                      <span class="text-sm text-gray-600 dark:text-gray-400 shrink-0 w-24">相似度阈值</span>
                      <NInputNumber v-model:value="selectedNode.config!.scoreThreshold" :min="0" :max="1" :step="0.05" :precision="2" class="flex-1" />
                    </div>
                  </template>

                  <!-- Prompt模板节点 -->
                  <template v-if="selectedNode.type === 'Prompt模板'">
                    <div class="mb-2 text-xs font-medium text-gray-500 uppercase tracking-wide">Prompt 配置</div>
                    <NSelect
                      v-model:value="selectedNode.config!.templateId"
                      clearable
                      filterable
                      :options="promptOptions.length ? promptOptions : emptyOptions('暂无Prompt模板')"
                      placeholder="选择已有模板（可选）"
                    />
                    <NInput v-model:value="selectedNode.config!.templateContent" type="textarea" :rows="5" placeholder="Prompt 内容，支持 {{变量}} 语法" />
                    <NInput v-model:value="selectedNode.config!.inputVars" type="textarea" :rows="2" placeholder="输入变量映射（JSON）" />
                  </template>

                  <!-- Agent调用节点 -->
                  <template v-if="selectedNode.type === 'Agent调用'">
                    <div class="mb-2 text-xs font-medium text-gray-500 uppercase tracking-wide">Agent 配置</div>
                    <NInput v-model:value="selectedNode.config!.agentId" placeholder="Agent ID 或名称" />
                    <NInput v-model:value="selectedNode.config!.inputMapping" type="textarea" :rows="2" placeholder="输入变量映射（JSON）" />
                    <NInput v-model:value="selectedNode.config!.outputMapping" type="textarea" :rows="2" placeholder="输出变量映射（JSON）" />
                    <div class="flex items-center gap-2">
                      <span class="text-sm text-gray-600 dark:text-gray-400 shrink-0 w-16">超时(秒)</span>
                      <NInputNumber v-model:value="selectedNode.config!.timeout" :min="1" :max="600" class="flex-1" />
                    </div>
                  </template>

                  <!-- MCP工具节点 -->
                  <template v-if="selectedNode.type === 'MCP工具'">
                    <div class="mb-2 text-xs font-medium text-gray-500 uppercase tracking-wide">工具配置</div>
                    <NSelect
                      v-model:value="selectedNode.config!.toolId"
                      clearable
                      filterable
                      :options="mcpToolOptions.length ? mcpToolOptions : emptyOptions('暂无MCP工具')"
                      placeholder="选择 MCP 工具"
                    />
                    <NInput v-model:value="selectedNode.config!.inputMapping" type="textarea" :rows="2" placeholder="输入参数映射（JSON）" />
                    <NInput v-model:value="selectedNode.config!.outputField" placeholder="输出字段名（如：result）" />
                    <div class="grid grid-cols-2 gap-2">
                      <div class="flex items-center gap-1">
                        <span class="text-xs text-gray-500 shrink-0">重试次数</span>
                        <NInputNumber v-model:value="selectedNode.config!.retryCount" :min="0" :max="5" class="flex-1" />
                      </div>
                      <div class="flex items-center gap-1">
                        <span class="text-xs text-gray-500 shrink-0">超时(秒)</span>
                        <NInputNumber v-model:value="selectedNode.config!.timeout" :min="1" :max="300" class="flex-1" />
                      </div>
                    </div>
                  </template>

                  <!-- HTTP请求节点 -->
                  <template v-if="selectedNode.type === 'HTTP请求'">
                    <div class="mb-2 text-xs font-medium text-gray-500 uppercase tracking-wide">HTTP 配置</div>
                    <div class="flex gap-2">
                      <NSelect
                        v-model:value="selectedNode.config!.method"
                        class="w-28 shrink-0"
                        :options="['GET', 'POST', 'PUT', 'PATCH', 'DELETE'].map(v => ({ label: v, value: v }))"
                      />
                      <NInput v-model:value="selectedNode.config!.url" class="flex-1" placeholder="请求 URL" />
                    </div>
                    <NInput v-model:value="selectedNode.config!.headers" type="textarea" :rows="2" placeholder="请求头（JSON）" />
                    <NInput v-model:value="selectedNode.config!.body" type="textarea" :rows="3" placeholder="请求体（JSON/文本，支持 {{变量}}）" />
                    <NSelect
                      v-model:value="selectedNode.config!.authType"
                      :options="['none', 'Bearer Token', 'Basic Auth', 'API Key'].map(v => ({ label: v, value: v }))"
                      placeholder="认证方式"
                    />
                    <div class="grid grid-cols-2 gap-2">
                      <div class="flex items-center gap-1">
                        <span class="text-xs text-gray-500 shrink-0">超时(秒)</span>
                        <NInputNumber v-model:value="selectedNode.config!.timeout" :min="1" :max="300" class="flex-1" />
                      </div>
                      <div class="flex items-center gap-1">
                        <span class="text-xs text-gray-500 shrink-0">重试次数</span>
                        <NInputNumber v-model:value="selectedNode.config!.retryCount" :min="0" :max="5" class="flex-1" />
                      </div>
                    </div>
                  </template>

                  <!-- SQL查询节点 -->
                  <template v-if="selectedNode.type === 'SQL查询'">
                    <div class="mb-2 text-xs font-medium text-gray-500 uppercase tracking-wide">SQL 配置</div>
                    <NInput v-model:value="selectedNode.config!.datasource" placeholder="数据源名称/连接标识" />
                    <NInput v-model:value="selectedNode.config!.sql" type="textarea" :rows="5" placeholder="SQL 语句（支持 {{变量}} 参数）" />
                    <NSelect
                      v-model:value="selectedNode.config!.resultType"
                      :options="['列表', '单行', '单值', '影响行数'].map(v => ({ label: v, value: v }))"
                      placeholder="结果类型"
                    />
                    <div class="flex items-center gap-2">
                      <span class="text-sm text-gray-600 dark:text-gray-400 shrink-0 w-16">最大行数</span>
                      <NInputNumber v-model:value="selectedNode.config!.maxRows" :min="1" :max="10000" class="flex-1" />
                    </div>
                  </template>

                  <!-- Python执行节点 -->
                  <template v-if="selectedNode.type === 'Python执行'">
                    <div class="mb-2 text-xs font-medium text-gray-500 uppercase tracking-wide">Python 配置</div>
                    <NInput v-model:value="selectedNode.config!.code" type="textarea" :rows="7" placeholder="Python 代码（可使用 input 变量）" style="font-family: monospace; font-size: 12px;" />
                    <NInput v-model:value="selectedNode.config!.requirements" placeholder="依赖包（逗号分隔，如：requests,pandas）" />
                    <div class="flex items-center gap-2">
                      <span class="text-sm text-gray-600 dark:text-gray-400 shrink-0 w-16">超时(秒)</span>
                      <NInputNumber v-model:value="selectedNode.config!.timeout" :min="1" :max="300" class="flex-1" />
                    </div>
                  </template>

                  <!-- 代码执行节点 -->
                  <template v-if="selectedNode.type === '代码执行'">
                    <div class="mb-2 text-xs font-medium text-gray-500 uppercase tracking-wide">代码配置</div>
                    <NSelect
                      v-model:value="selectedNode.config!.language"
                      :options="['JavaScript', 'TypeScript', 'Python', 'Shell'].map(v => ({ label: v, value: v }))"
                      placeholder="执行语言"
                    />
                    <NInput v-model:value="selectedNode.config!.code" type="textarea" :rows="7" placeholder="代码内容" style="font-family: monospace; font-size: 12px;" />
                    <div class="flex items-center gap-2">
                      <span class="text-sm text-gray-600 dark:text-gray-400 shrink-0 w-16">超时(秒)</span>
                      <NInputNumber v-model:value="selectedNode.config!.timeout" :min="1" :max="300" class="flex-1" />
                    </div>
                  </template>

                  <!-- 审批节点 -->
                  <template v-if="selectedNode.type === '审批'">
                    <div class="mb-2 text-xs font-medium text-gray-500 uppercase tracking-wide">审批配置</div>
                    <NInput v-model:value="selectedNode.config!.approvers" placeholder="审批人（用户名/角色，逗号分隔）" />
                    <NSelect
                      v-model:value="selectedNode.config!.approvalType"
                      :options="['任一审批', '全部审批', '顺序审批'].map(v => ({ label: v, value: v }))"
                      placeholder="审批类型"
                    />
                    <NInput v-model:value="selectedNode.config!.formFields" type="textarea" :rows="3" placeholder="审批表单字段（JSON Schema）" />
                    <div class="flex items-center gap-2">
                      <span class="text-sm text-gray-600 dark:text-gray-400 shrink-0 w-24">超时(秒)</span>
                      <NInputNumber v-model:value="selectedNode.config!.timeout" :min="60" :max="2592000" class="flex-1" placeholder="默认86400(1天)" />
                    </div>
                  </template>

                  <!-- 消息通知节点 -->
                  <template v-if="selectedNode.type === '消息通知'">
                    <div class="mb-2 text-xs font-medium text-gray-500 uppercase tracking-wide">通知配置</div>
                    <NSelect
                      v-model:value="selectedNode.config!.channel"
                      :options="['系统通知', '站内信', 'SMS短信'].map(v => ({ label: v, value: v }))"
                      placeholder="通知渠道"
                    />
                    <NInput v-model:value="selectedNode.config!.recipients" placeholder="接收人（用户名/角色，逗号分隔）" />
                    <NInput v-model:value="selectedNode.config!.title" placeholder="消息标题" />
                    <NInput v-model:value="selectedNode.config!.content" type="textarea" :rows="3" placeholder="消息内容（支持 {{变量}}）" />
                    <NSelect
                      v-model:value="selectedNode.config!.priority"
                      :options="['普通', '紧急', '非常紧急'].map(v => ({ label: v, value: v }))"
                      placeholder="优先级"
                    />
                  </template>

                  <!-- 邮件发送节点 -->
                  <template v-if="selectedNode.type === '邮件发送'">
                    <div class="mb-2 text-xs font-medium text-gray-500 uppercase tracking-wide">邮件配置</div>
                    <NInput v-model:value="selectedNode.config!.to" placeholder="收件人（邮箱，逗号分隔）" />
                    <NInput v-model:value="selectedNode.config!.cc" placeholder="抄送（邮箱，可选）" />
                    <NInput v-model:value="selectedNode.config!.subject" placeholder="邮件主题（支持 {{变量}}）" />
                    <NSelect
                      v-model:value="selectedNode.config!.bodyType"
                      :options="['html', 'text'].map(v => ({ label: v === 'html' ? 'HTML' : '纯文本', value: v }))"
                      placeholder="正文格式"
                    />
                    <NInput v-model:value="selectedNode.config!.body" type="textarea" :rows="5" placeholder="邮件正文（支持 {{变量}}）" />
                  </template>

                  <!-- Webhook节点 -->
                  <template v-if="selectedNode.type === 'Webhook'">
                    <div class="mb-2 text-xs font-medium text-gray-500 uppercase tracking-wide">Webhook 配置</div>
                    <div class="flex gap-2">
                      <NSelect
                        v-model:value="selectedNode.config!.method"
                        class="w-24 shrink-0"
                        :options="['POST', 'GET', 'PUT'].map(v => ({ label: v, value: v }))"
                      />
                      <NInput v-model:value="selectedNode.config!.url" class="flex-1" placeholder="Webhook URL" />
                    </div>
                    <NInput v-model:value="selectedNode.config!.headers" type="textarea" :rows="2" placeholder="请求头（JSON）" />
                    <NInput v-model:value="selectedNode.config!.payload" type="textarea" :rows="3" placeholder="请求体（JSON，支持 {{变量}}）" />
                    <NInput v-model:value="selectedNode.config!.secret" placeholder="签名密钥（可选，用于验签）" />
                    <div class="flex items-center gap-2">
                      <span class="text-sm text-gray-600 dark:text-gray-400 shrink-0 w-16">重试次数</span>
                      <NInputNumber v-model:value="selectedNode.config!.retryCount" :min="0" :max="5" class="flex-1" />
                    </div>
                  </template>

                  <!-- 飞书通知节点 -->
                  <template v-if="selectedNode.type === '飞书通知'">
                    <div class="mb-2 text-xs font-medium text-gray-500 uppercase tracking-wide">飞书配置</div>
                    <NInput v-model:value="selectedNode.config!.webhookUrl" placeholder="飞书自定义机器人 Webhook URL" />
                    <NSelect
                      v-model:value="selectedNode.config!.msgType"
                      :options="['text', 'post', 'interactive'].map(v => ({ label: v === 'text' ? '文本' : v === 'post' ? '富文本' : '卡片消息', value: v }))"
                      placeholder="消息类型"
                    />
                    <NInput v-if="selectedNode.config!.msgType !== 'text'" v-model:value="selectedNode.config!.title" placeholder="消息标题" />
                    <NInput v-model:value="selectedNode.config!.content" type="textarea" :rows="3" placeholder="消息内容（支持 {{变量}}）" />
                    <NInput v-model:value="selectedNode.config!.atUsers" placeholder="@指定成员（user_id，逗号分隔）" />
                  </template>

                  <!-- 企业微信通知节点 -->
                  <template v-if="selectedNode.type === '企业微信通知'">
                    <div class="mb-2 text-xs font-medium text-gray-500 uppercase tracking-wide">企业微信配置</div>
                    <NInput v-model:value="selectedNode.config!.webhookUrl" placeholder="企业微信群机器人 Webhook URL" />
                    <NSelect
                      v-model:value="selectedNode.config!.msgType"
                      :options="['text', 'markdown', 'news'].map(v => ({ label: v === 'text' ? '文本' : v === 'markdown' ? 'Markdown' : '图文', value: v }))"
                      placeholder="消息类型"
                    />
                    <NInput v-model:value="selectedNode.config!.content" type="textarea" :rows="3" placeholder="消息内容（支持 {{变量}}）" />
                    <NInput v-model:value="selectedNode.config!.atUsers" placeholder="@成员（企业微信账号，逗号分隔）" />
                  </template>
                </div>
                <div v-else class="rounded-lg border border-dashed border-gray-200 py-10 text-center text-sm text-gray-400">
                  {{ hasSelectedWorkflow ? '请选择一个节点' : '暂无可配置节点' }}
                </div>
              </aside>
            </div>
          </NTabPane>

          <NTabPane name="debug" tab="调试运行">
            <div class="grid grid-cols-[320px_1fr_320px] gap-5">
              <div class="rounded-lg border border-gray-100 bg-gray-50 p-4 dark:border-gray-700 dark:bg-gray-900">
                <div class="mb-3 text-sm font-semibold">输入区</div>
                <NInput v-model:value="testQuery" type="textarea" placeholder="请输入测试问题" />
                <NButton block type="primary" class="mt-3" :disabled="!hasSelectedWorkflow" :loading="debugLoading" @click="runDebug">执行</NButton>
              </div>
              <div class="rounded-lg border border-gray-100 p-4 dark:border-gray-700">
                <div class="mb-3 text-sm font-semibold">执行轨迹</div>
                <div v-if="debugResult" class="space-y-3">
                  <div v-for="item in debugResult.trace" :key="item.name" class="flex items-center justify-between rounded-lg bg-gray-50 px-3 py-2 dark:bg-gray-900">
                    <span>{{ item.name }}</span>
                    <NTag type="success" size="small">{{ (item.durationMs / 1000).toFixed(1) }}s</NTag>
                  </div>
                  <div class="rounded-lg bg-blue-50 p-3 text-sm text-blue-700">{{ debugResult.output.answer }}</div>
                </div>
                <div v-else class="py-16 text-center text-gray-400">暂无调试结果</div>
              </div>
              <div class="space-y-5">
                <div class="rounded-lg border border-gray-100 p-4 dark:border-gray-700">
                  <div class="mb-3 text-sm font-semibold">变量监控</div>
                  <pre class="whitespace-pre-wrap rounded bg-gray-50 p-3 text-xs dark:bg-gray-900">{{ JSON.stringify(debugResult?.variables || { query: testQuery }, null, 2) }}</pre>
                </div>
                <div class="rounded-lg border border-gray-100 p-4 dark:border-gray-700">
                  <div class="mb-3 text-sm font-semibold">Token统计</div>
                  <div class="space-y-2 text-sm">
                    <div class="flex justify-between"><span>Prompt Token</span><span>{{ debugResult?.tokens.promptTokens || 0 }}</span></div>
                    <div class="flex justify-between"><span>Completion Token</span><span>{{ debugResult?.tokens.completionTokens || 0 }}</span></div>
                    <div class="flex justify-between"><span>总Token</span><span>{{ debugResult?.tokens.totalTokens || 0 }}</span></div>
                    <div class="flex justify-between"><span>费用</span><span>${{ debugResult?.tokens.cost || 0 }}</span></div>
                  </div>
                </div>
              </div>
            </div>
          </NTabPane>

          <NTabPane name="detail" tab="Agent详情">
            <div class="grid grid-cols-3 gap-5">
              <div class="rounded-lg border border-gray-100 p-5 dark:border-gray-700">
                <div class="mb-4 text-sm font-semibold">基础信息</div>
                <div v-if="hasSelectedWorkflow" class="space-y-2 text-sm text-gray-600">
                  <div>名称：{{ selectedWorkflow?.name }}</div>
                  <div>描述：{{ selectedWorkflow?.description || '-' }}</div>
                  <div>负责人：{{ selectedWorkflow?.ownerName || '-' }}</div>
                  <div>标签：{{ selectedWorkflow?.tags || '-' }}</div>
                </div>
                <div v-else class="py-8 text-center text-sm text-gray-400">
                  暂无后端工作流数据
                </div>
              </div>
              <div class="rounded-lg border border-gray-100 p-5 dark:border-gray-700">
                <div class="mb-4 text-sm font-semibold">权限配置</div>
                <NSelect
                  v-model:value="designer.permissionScope"
                  :disabled="!hasSelectedWorkflow"
                  :options="['公开', '组织内', '指定角色', '指定用户'].map(v => ({ label: v, value: v }))"
                  placeholder="暂无权限配置"
                />
              </div>
              <div class="rounded-lg border border-gray-100 p-5 dark:border-gray-700">
                <div class="mb-4 text-sm font-semibold">关联资源</div>
                <div v-if="hasSelectedWorkflow" class="space-y-2 text-sm text-gray-600">
                  <div>知识库：{{ designer.knowledgeBases || '-' }}</div>
                  <div>Prompt：{{ designer.promptRefs || '-' }}</div>
                  <div>MCP工具：{{ designer.mcpTools || '-' }}</div>
                  <div>模型：{{ designer.models || '-' }}</div>
                </div>
                <div v-else class="py-8 text-center text-sm text-gray-400">
                  暂无关联资源
                </div>
              </div>
            </div>
          </NTabPane>
        </NTabs>
      </div>
    </div>

    <NModal v-model:show="createVisible" preset="dialog" title="新建工作流" positive-text="创建" negative-text="取消" @positive-click="createWorkflow">
      <div class="space-y-3">
        <NInput v-model:value="createForm.name" placeholder="名称" />
        <NSelect v-model:value="createForm.type" :options="['知识问答', '多Agent', '工作流'].map(v => ({ label: v, value: v }))" />
        <NInput v-model:value="createForm.description" type="textarea" placeholder="描述" />
      </div>
    </NModal>
  </div>
</template>

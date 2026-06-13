<script setup lang="tsx">
import { NButton, NDataTable, NInput, NSelect, NSpace, NTabPane, NTabs, NTag, NEmpty, NPagination } from 'naive-ui';

type LogLevel = 'request' | 'response' | 'error';

type DebugLog = {
  id: string;
  traceId: string;
  level: LogLevel;
  agentName: string;
  action: string;
  durationMs: number;
  status: 'success' | 'error';
  timestamp: string;
  prompt?: string;
  retrievalResult?: string;
  toolCalls?: string;
  errorMsg?: string;
};

type TraceStep = {
  name: string;
  type: 'user' | 'agent' | 'knowledge' | 'tool' | 'model';
  durationMs: number;
  status: 'success' | 'error';
  detail?: string;
};

// ─── 状态 ───
const activeTab = ref('logs');
const logLevel = ref<LogLevel | ''>('');
const keyword = ref('');
const selectedLog = ref<DebugLog | null>(null);
const traceVisible = ref(false);

// ─── 模拟日志数据 ───
const mockLogs: DebugLog[] = [
  {
    id: '1',
    traceId: 'trace-001',
    level: 'request',
    agentName: 'HR助手',
    action: '员工请假制度查询',
    durationMs: 1234,
    status: 'success',
    timestamp: new Date(Date.now() - 3 * 60000).toISOString(),
    prompt: '你是一名专业HR顾问，请回答以下问题：请假制度是什么？',
    retrievalResult: '命中文档：员工手册 > 请假规定（score: 0.92）',
    toolCalls: '无工具调用'
  },
  {
    id: '2',
    traceId: 'trace-002',
    level: 'error',
    agentName: '法务助手',
    action: '合同审查',
    durationMs: 5600,
    status: 'error',
    timestamp: new Date(Date.now() - 8 * 60000).toISOString(),
    errorMsg: 'LLM 超时，响应时长超过 5s 阈值',
    prompt: '请审查以下合同条款...'
  },
  {
    id: '3',
    traceId: 'trace-003',
    level: 'response',
    agentName: '销售助手',
    action: '数据分析报告生成',
    durationMs: 2100,
    status: 'success',
    timestamp: new Date(Date.now() - 12 * 60000).toISOString(),
    toolCalls: 'CRM查询 → 数据整合 → 报告生成'
  }
];

const logs = ref<DebugLog[]>(mockLogs);

// ─── 过滤日志 ───
const filteredLogs = computed(() => {
  return logs.value.filter(log => {
    const matchLevel = !logLevel.value || log.level === logLevel.value;
    const matchKeyword = !keyword.value || log.agentName.includes(keyword.value) || log.action.includes(keyword.value);
    return matchLevel && matchKeyword;
  });
});

// ─── 调用链模拟数据 ───
const traceSteps = computed<TraceStep[]>(() => {
  if (!selectedLog.value) return [];
  return [
    { name: '用户请求', type: 'user', durationMs: 0, status: 'success', detail: selectedLog.value.action },
    { name: `Agent: ${selectedLog.value.agentName}`, type: 'agent', durationMs: 45, status: 'success', detail: '接收请求，准备调用链' },
    { name: '知识库检索', type: 'knowledge', durationMs: 312, status: 'success', detail: selectedLog.value.retrievalResult || '无知识库调用' },
    { name: 'MCP工具调用', type: 'tool', durationMs: 180, status: selectedLog.value.status, detail: selectedLog.value.toolCalls || '无工具调用' },
    { name: 'LLM 推理', type: 'model', durationMs: selectedLog.value.durationMs - 537, status: selectedLog.value.status, detail: selectedLog.value.errorMsg || '推理完成' }
  ];
});

// ─── 表格列 ───
const logColumns = [
  {
    key: 'level',
    title: '类型',
    width: 90,
    render: (row: DebugLog) => {
      const map: Record<LogLevel, { type: 'info' | 'success' | 'error'; label: string }> = {
        request: { type: 'info', label: '请求' },
        response: { type: 'success', label: '响应' },
        error: { type: 'error', label: '错误' }
      };
      const cfg = map[row.level];
      return <NTag type={cfg.type} size="small">{cfg.label}</NTag>;
    }
  },
  {
    key: 'agentName',
    title: 'Agent / 工作流',
    width: 150
  },
  {
    key: 'action',
    title: '操作',
    ellipsis: true
  },
  {
    key: 'status',
    title: '状态',
    width: 80,
    render: (row: DebugLog) => (
      <NTag type={row.status === 'success' ? 'success' : 'error'} size="small">
        {row.status === 'success' ? '成功' : '失败'}
      </NTag>
    )
  },
  {
    key: 'durationMs',
    title: '耗时',
    width: 90,
    render: (row: DebugLog) => <span>{row.durationMs}ms</span>
  },
  {
    key: 'timestamp',
    title: '时间',
    width: 150,
    render: (row: DebugLog) => dayjs(row.timestamp).format('HH:mm:ss')
  },
  {
    key: 'operate',
    title: '操作',
    width: 100,
    render: (row: DebugLog) => (
      <NButton text size="small" type="primary" onClick={() => openTrace(row)}>
        查看Trace
      </NButton>
    )
  }
];

function openTrace(log: DebugLog) {
  selectedLog.value = log;
  traceVisible.value = true;
}

function getTypeIcon(type: TraceStep['type']) {
  const map = {
    user: '👤',
    agent: '🤖',
    knowledge: '📚',
    tool: '🔧',
    model: '⚡'
  };
  return map[type];
}

function getTypeBg(type: TraceStep['type']) {
  const map = {
    user: 'bg-gray-50 border-gray-200 dark:bg-gray-800 dark:border-gray-700',
    agent: 'bg-blue-50 border-blue-200 dark:bg-blue-900/20 dark:border-blue-700',
    knowledge: 'bg-amber-50 border-amber-200 dark:bg-amber-900/20 dark:border-amber-700',
    tool: 'bg-emerald-50 border-emerald-200 dark:bg-emerald-900/20 dark:border-emerald-700',
    model: 'bg-purple-50 border-purple-200 dark:bg-purple-900/20 dark:border-purple-700'
  };
  return map[type];
}

function refreshLogs() {
  // 实际场景中这里调用 API
  window.$message?.success('已刷新');
}
</script>

<template>
  <div class="h-full overflow-y-auto bg-gray-50 dark:bg-gray-900">
    <div class="px-8 py-6">
      <!-- 页面标题 -->
      <div class="mb-6 flex items-center justify-between">
        <div>
          <h1 class="mb-1 text-2xl font-bold text-gray-900 dark:text-white">调试中心</h1>
          <p class="text-sm text-gray-500">统一排查 Agent 和 Workflow 问题，查看请求日志、调用链和 Trace 详情</p>
        </div>
        <NButton @click="refreshLogs">
          <template #icon><icon-carbon:renew /></template>
          刷新
        </NButton>
      </div>

      <!-- 统计卡片 -->
      <div class="mb-6 grid grid-cols-4 gap-5">
        <div class="rounded-xl border border-gray-100 bg-white px-6 py-5 shadow-sm dark:border-gray-700 dark:bg-gray-800">
          <div class="mb-1 text-3xl font-bold text-gray-900 dark:text-white">{{ logs.length }}</div>
          <div class="text-sm text-gray-500">总日志数</div>
        </div>
        <div class="rounded-xl border border-gray-100 bg-white px-6 py-5 shadow-sm dark:border-gray-700 dark:bg-gray-800">
          <div class="mb-1 text-3xl font-bold text-emerald-600 dark:text-emerald-400">
            {{ logs.filter(l => l.status === 'success').length }}
          </div>
          <div class="text-sm text-gray-500">成功请求</div>
        </div>
        <div class="rounded-xl border border-gray-100 bg-white px-6 py-5 shadow-sm dark:border-gray-700 dark:bg-gray-800">
          <div class="mb-1 text-3xl font-bold text-red-500 dark:text-red-400">
            {{ logs.filter(l => l.status === 'error').length }}
          </div>
          <div class="text-sm text-gray-500">错误请求</div>
        </div>
        <div class="rounded-xl border border-gray-100 bg-white px-6 py-5 shadow-sm dark:border-gray-700 dark:bg-gray-800">
          <div class="mb-1 text-3xl font-bold text-gray-900 dark:text-white">
            {{ logs.length ? Math.round(logs.reduce((s, l) => s + l.durationMs, 0) / logs.length) : 0 }}ms
          </div>
          <div class="text-sm text-gray-500">平均耗时</div>
        </div>
      </div>

      <!-- 主内容卡片 -->
      <div class="rounded-xl border border-gray-100 bg-white shadow-sm dark:border-gray-700 dark:bg-gray-800">
        <NTabs v-model:value="activeTab" type="line" animated class="px-5 pt-3">

          <!-- ── 调试日志 Tab ── -->
          <NTabPane name="logs" tab="调试日志">
            <!-- 过滤栏 -->
            <div class="mb-4 flex items-center gap-3">
              <NInput
                v-model:value="keyword"
                clearable
                placeholder="搜索 Agent 名称或操作"
                class="max-w-260px"
              >
                <template #prefix><icon-carbon:search class="text-gray-400" /></template>
              </NInput>
              <NSelect
                v-model:value="logLevel"
                clearable
                placeholder="日志类型"
                class="w-32"
                :options="[
                  { label: '请求日志', value: 'request' },
                  { label: '响应日志', value: 'response' },
                  { label: '错误日志', value: 'error' }
                ]"
              />
              <NSpace class="ml-auto">
                <NButton size="small">导出日志</NButton>
                <NButton size="small" type="error" ghost>清空日志</NButton>
              </NSpace>
            </div>

            <!-- 日志表格 -->
            <NDataTable
              :columns="logColumns"
              :data="filteredLogs"
              :row-key="row => row.id"
              size="small"
              striped
              :pagination="false"
            />
            <div class="flex justify-end border-t border-gray-100 px-4 py-3 dark:border-gray-700">
              <span class="text-xs text-gray-400">共 {{ filteredLogs.length }} 条记录</span>
            </div>
          </NTabPane>

          <!-- ── 调用链 Tab ── -->
          <NTabPane name="trace" tab="调用链">
            <div class="py-4">
              <div v-if="logs.length === 0" class="py-16">
                <NEmpty description="暂无调用记录，请先从调试日志中选择一条记录查看" />
              </div>
              <div v-else class="space-y-4">
                <div
                  v-for="log in logs.slice(0, 5)"
                  :key="log.id"
                  class="cursor-pointer rounded-lg border border-gray-100 p-4 transition hover:border-blue-200 dark:border-gray-700 dark:hover:border-blue-600"
                  :class="selectedLog?.id === log.id ? 'border-blue-300 bg-blue-50 dark:border-blue-600 dark:bg-blue-900/10' : 'bg-white dark:bg-gray-800'"
                  @click="selectedLog = log"
                >
                  <div class="mb-3 flex items-center justify-between">
                    <div class="flex items-center gap-3">
                      <NTag :type="log.status === 'success' ? 'success' : 'error'" size="small">
                        {{ log.status === 'success' ? '成功' : '失败' }}
                      </NTag>
                      <span class="font-medium text-gray-900 dark:text-white">{{ log.agentName }}</span>
                      <span class="text-sm text-gray-500">{{ log.action }}</span>
                    </div>
                    <span class="text-xs text-gray-400">{{ dayjs(log.timestamp).format('HH:mm:ss') }}</span>
                  </div>

                  <!-- 调用链路 -->
                  <div v-if="selectedLog?.id === log.id" class="mt-3 flex flex-wrap items-center gap-2">
                    <div
                      v-for="(step, idx) in traceSteps"
                      :key="idx"
                      class="flex items-center gap-1"
                    >
                      <div
                        class="flex items-center gap-1.5 rounded-lg border px-3 py-1.5 text-xs"
                        :class="getTypeBg(step.type)"
                      >
                        <span>{{ getTypeIcon(step.type) }}</span>
                        <span class="font-medium">{{ step.name }}</span>
                        <span class="text-gray-500">{{ step.durationMs }}ms</span>
                      </div>
                      <icon-carbon:arrow-right v-if="idx < traceSteps.length - 1" class="text-gray-300" />
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </NTabPane>

          <!-- ── Trace 详情 Tab ── -->
          <NTabPane name="trace-detail" tab="Trace详情">
            <div v-if="selectedLog" class="pb-6">
              <div class="mb-4 flex items-center gap-3">
                <NTag :type="selectedLog.status === 'success' ? 'success' : 'error'">
                  {{ selectedLog.status === 'success' ? '执行成功' : '执行失败' }}
                </NTag>
                <span class="font-semibold text-gray-900 dark:text-white">{{ selectedLog.agentName }}</span>
                <span class="text-gray-500">{{ selectedLog.action }}</span>
                <span class="ml-auto text-sm text-gray-400">Trace ID: {{ selectedLog.traceId }}</span>
              </div>

              <div class="grid grid-cols-2 gap-6">
                <!-- 调用步骤时间线 -->
                <div class="space-y-3">
                  <div class="text-sm font-semibold text-gray-700 dark:text-gray-300">调用链路</div>
                  <div class="space-y-2">
                    <div
                      v-for="(step, idx) in traceSteps"
                      :key="idx"
                      class="flex gap-3"
                    >
                      <div class="flex flex-col items-center">
                        <div
                          class="flex h-8 w-8 shrink-0 items-center justify-center rounded-full border text-sm"
                          :class="step.status === 'success'
                            ? 'border-emerald-300 bg-emerald-50 dark:border-emerald-600 dark:bg-emerald-900/20'
                            : 'border-red-300 bg-red-50 dark:border-red-600 dark:bg-red-900/20'"
                        >
                          {{ getTypeIcon(step.type) }}
                        </div>
                        <div v-if="idx < traceSteps.length - 1" class="mt-1 h-6 w-0.5 bg-gray-200 dark:bg-gray-700"></div>
                      </div>
                      <div class="flex-1 pb-2">
                        <div class="flex items-center justify-between">
                          <span class="text-sm font-medium text-gray-900 dark:text-white">{{ step.name }}</span>
                          <span class="text-xs text-gray-400">{{ step.durationMs }}ms</span>
                        </div>
                        <div class="text-xs text-gray-500">{{ step.detail }}</div>
                      </div>
                    </div>
                  </div>
                </div>

                <!-- 右侧详情 -->
                <div class="space-y-4">
                  <!-- 耗时统计 -->
                  <div class="rounded-lg border border-gray-100 p-4 dark:border-gray-700">
                    <div class="mb-2 text-xs font-medium text-gray-500">耗时统计</div>
                    <div class="space-y-2 text-sm">
                      <div class="flex justify-between">
                        <span class="text-gray-600 dark:text-gray-400">总耗时</span>
                        <span class="font-medium text-gray-900 dark:text-white">{{ selectedLog.durationMs }}ms</span>
                      </div>
                      <div
                        v-for="step in traceSteps.slice(1)"
                        :key="step.name"
                        class="flex justify-between text-xs"
                      >
                        <span class="text-gray-500">{{ step.name }}</span>
                        <span class="text-gray-600 dark:text-gray-400">{{ step.durationMs }}ms</span>
                      </div>
                    </div>
                  </div>

                  <!-- Prompt 内容 -->
                  <div v-if="selectedLog.prompt" class="rounded-lg border border-gray-100 p-4 dark:border-gray-700">
                    <div class="mb-2 text-xs font-medium text-gray-500">Prompt</div>
                    <pre class="whitespace-pre-wrap rounded bg-gray-50 p-3 text-xs dark:bg-gray-900">{{ selectedLog.prompt }}</pre>
                  </div>

                  <!-- 检索结果 -->
                  <div v-if="selectedLog.retrievalResult" class="rounded-lg border border-gray-100 p-4 dark:border-gray-700">
                    <div class="mb-2 text-xs font-medium text-gray-500">知识库检索结果</div>
                    <div class="rounded bg-amber-50 p-3 text-xs text-amber-700 dark:bg-amber-900/20 dark:text-amber-300">
                      {{ selectedLog.retrievalResult }}
                    </div>
                  </div>

                  <!-- 工具调用 -->
                  <div v-if="selectedLog.toolCalls" class="rounded-lg border border-gray-100 p-4 dark:border-gray-700">
                    <div class="mb-2 text-xs font-medium text-gray-500">工具调用</div>
                    <div class="rounded bg-emerald-50 p-3 text-xs text-emerald-700 dark:bg-emerald-900/20 dark:text-emerald-300">
                      {{ selectedLog.toolCalls }}
                    </div>
                  </div>

                  <!-- 错误信息 -->
                  <div v-if="selectedLog.errorMsg" class="rounded-lg border border-red-200 p-4 dark:border-red-800">
                    <div class="mb-2 text-xs font-medium text-red-500">错误详情</div>
                    <div class="rounded bg-red-50 p-3 text-xs text-red-700 dark:bg-red-900/20 dark:text-red-300">
                      {{ selectedLog.errorMsg }}
                    </div>
                  </div>
                </div>
              </div>
            </div>
            <div v-else class="py-16">
              <NEmpty description="请先从调试日志中点击「查看Trace」选择一条记录" />
            </div>
          </NTabPane>
        </NTabs>
      </div>
    </div>
  </div>
</template>

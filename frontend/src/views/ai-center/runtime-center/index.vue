<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { storeToRefs } from 'pinia';
import { NButton, NEmpty, NInput, NScrollbar, NSpin, NTag, NTooltip } from 'naive-ui';
import { VueMarkdownIt, VueMarkdownItProvider } from 'vue-markdown-shiki';
import { DEFAULT_PAGE_SIZE } from '@/constants/common';
import {
  fetchAgentExecutionDetail,
  fetchCreateConversationSession,
  fetchDeleteConversationSession,
  fetchGetConversationMessages,
  fetchGetConversationSessions,
  fetchRuntimeCatalog,
  fetchRuntimeExecute,
  fetchUpdateConversationPin,
  fetchWorkflowExecutionDetail
} from '@/service/api';
import { useAuthStore } from '@/store/modules/auth';
import { getAvatarText, useUserAvatar } from '@/utils/avatar';
import ListPagination from '@/components/common/list-pagination.vue';

defineOptions({
  name: 'RuntimeCenter'
});

type RuntimeType = 'agent' | 'workflow';
type AppFilter = 'all' | RuntimeType;

type TraceItem = {
  name?: string;
  nodeName?: string;
  nodeType?: string;
  status?: string;
  durationMs?: number;
  errorMessage?: string;
  description?: string;
  output?: unknown;
};

const route = useRoute();
const router = useRouter();
const authStore = useAuthStore();
const { userInfo } = storeToRefs(authStore);
const { avatarText } = useUserAvatar(userInfo);

const catalogLoading = ref(false);
const sessionsLoading = ref(false);
const messagesLoading = ref(false);
const executionDetailLoading = ref(false);
const running = ref(false);
const creatingSession = ref(false);
const deletingSessionIds = ref<string[]>([]);
const pinningSessionIds = ref<string[]>([]);

const appFilter = ref<AppFilter>('all');
const keyword = ref('');
const selectedAppId = ref('');
const conversationId = ref('');
const inputMessage = ref('');
const currentPage = ref(1);
const pageSize = ref(DEFAULT_PAGE_SIZE);

const catalog = ref<Api.Runtime.Catalog>({
  agents: [],
  workflows: [],
  agentCount: 0,
  workflowCount: 0
});
const sessions = ref<Api.Chat.Session[]>([]);
const messages = ref<Api.Chat.Message[]>([]);
const lastExecution = ref<Api.Runtime.ExecuteResult['execution'] | null>(null);
const executionDetail = ref<Record<string, any> | null>(null);

const allApps = computed<Api.Runtime.CatalogItem[]>(() => [...catalog.value.agents, ...catalog.value.workflows]);

const filteredApps = computed(() => {
  const normalizedKeyword = keyword.value.trim().toLowerCase();
  return allApps.value.filter(item => {
    const matchType = appFilter.value === 'all' || item.type === appFilter.value;
    const matchKeyword =
      !normalizedKeyword ||
      [item.name, item.description, item.tags, item.ownerName, item.models]
        .join(' ')
        .toLowerCase()
        .includes(normalizedKeyword);
    return matchType && matchKeyword;
  });
});

const appPageCount = computed(() => Math.max(1, Math.ceil(filteredApps.value.length / pageSize.value)));

const pagedApps = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value;
  return filteredApps.value.slice(start, start + pageSize.value);
});

const activeApp = computed(() => allApps.value.find(item => item.id === selectedAppId.value) || null);
const runtimeType = computed<RuntimeType>(() => activeApp.value?.type || 'agent');
const isExecutionView = computed(() => Boolean(activeApp.value));
const assistantAvatarText = computed(() => getAvatarText(activeApp.value?.name || getAppTypeLabel(runtimeType.value)));

const runtimeStats = computed(() => {
  const items = allApps.value;
  const runCount = items.reduce((sum, item) => sum + (Number(item.callCount) || 0), 0);
  const successRate = items.length
    ? Math.round(items.reduce((sum, item) => sum + (Number(item.successRate) || 0), 0) / items.length)
    : 0;
  return {
    appCount: items.length,
    agentCount: catalog.value.agentCount,
    workflowCount: catalog.value.workflowCount,
    runCount,
    successRate
  };
});

const groupedSessions = computed(() => {
  const groups = [
    { label: '今天', items: [] as Api.Chat.Session[] },
    { label: '昨天', items: [] as Api.Chat.Session[] },
    { label: '更早', items: [] as Api.Chat.Session[] }
  ];
  const today = new Date();
  const yesterday = new Date();
  yesterday.setDate(today.getDate() - 1);

  sessions.value.forEach(session => {
    const date = new Date(session.time || session.updatedAt || session.createdAt || '');
    if (Number.isNaN(date.getTime())) {
      groups[2].items.push(session);
      return;
    }
    if (date.toDateString() === today.toDateString()) groups[0].items.push(session);
    else if (date.toDateString() === yesterday.toDateString()) groups[1].items.push(session);
    else groups[2].items.push(session);
  });

  return groups.filter(group => group.items.length > 0);
});

const traceItems = computed<TraceItem[]>(() => {
  if (Array.isArray(lastExecution.value?.trace)) return lastExecution.value.trace;
  const parsed = parseJson(executionDetail.value?.traceJson);
  return Array.isArray(parsed) ? parsed : [];
});

const tokenUsage = computed(() => {
  const tokens = lastExecution.value?.tokens || {};
  return {
    promptTokens: numberValue(tokens.promptTokens ?? executionDetail.value?.promptTokens),
    completionTokens: numberValue(tokens.completionTokens ?? executionDetail.value?.completionTokens),
    totalTokens: numberValue(tokens.totalTokens ?? executionDetail.value?.totalTokens),
    cost: numberValue(tokens.cost ?? executionDetail.value?.cost)
  };
});

const executionStatus = computed(() => {
  if (running.value) return { label: '运行中', type: 'info' as const };
  if (!lastExecution.value && !executionDetail.value) return { label: '待运行', type: 'default' as const };
  if (lastExecution.value?.success === false || executionDetail.value?.status === 'failed') {
    return { label: '失败', type: 'error' as const };
  }
  return { label: '完成', type: 'success' as const };
});

const isSendDisabled = computed(() => !activeApp.value || !inputMessage.value.trim() || running.value);

function numberValue(value: unknown) {
  const num = Number(value);
  return Number.isFinite(num) ? num : 0;
}

function parseJson(value: unknown) {
  if (!value || typeof value !== 'string') return null;
  try {
    return JSON.parse(value);
  } catch {
    return null;
  }
}

function formatTime(value?: string | null) {
  if (!value) return '-';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '-';
  const now = new Date();
  if (date.toDateString() === now.toDateString()) {
    return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' });
  }
  return `${date.getMonth() + 1}-${String(date.getDate()).padStart(2, '0')} ${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`;
}

function formatDuration(value?: number | null) {
  const duration = numberValue(value);
  if (duration <= 0) return '-';
  if (duration < 1000) return `${duration} ms`;
  return `${(duration / 1000).toFixed(1)} s`;
}

function getTraceName(item: TraceItem) {
  return item.nodeName || item.name || item.nodeType || '执行步骤';
}

function getTraceStatusType(status?: string) {
  const normalized = (status || '').toLowerCase();
  if (['success', 'completed', 'finished'].includes(normalized)) return 'success';
  if (['failed', 'error'].includes(normalized)) return 'error';
  if (['running', 'pending'].includes(normalized)) return 'info';
  return 'default';
}

function getAppTypeLabel(type?: RuntimeType) {
  return type === 'workflow' ? 'Workflow 应用' : 'Agent 应用';
}

function getAppIdentity(item?: Api.Runtime.CatalogItem | null) {
  if (!item) return '-';
  return `${getAppTypeLabel(item.type)} / ${item.ownerName || 'system'}`;
}

function renderResultContent(value: unknown) {
  if (!value) return '暂无运行结果';
  if (typeof value === 'string') return value;
  try {
    return JSON.stringify(value, null, 2);
  } catch {
    return String(value);
  }
}

function handleCopy(content: unknown) {
  const text = renderResultContent(content);
  navigator.clipboard.writeText(text);
  window.$message?.success('已复制');
}

async function loadCatalog() {
  catalogLoading.value = true;
  const { error, data } = await fetchRuntimeCatalog();
  catalogLoading.value = false;
  if (error || !data) return;

  catalog.value = data;
  let queryType: AppFilter = 'all';
  if (route.query.targetType === 'workflow') queryType = 'workflow';
  if (route.query.targetType === 'agent') queryType = 'agent';
  const queryTargetId = typeof route.query.targetId === 'string' ? route.query.targetId : '';
  const queryApp = queryTargetId
    ? allApps.value.find(item => item.id === queryTargetId && (queryType === 'all' || item.type === queryType))
    : null;

  selectedAppId.value = queryApp?.id || '';
  appFilter.value = queryType;

  if (queryApp) {
    await loadSessions();
  } else {
    await backToCatalog(Boolean(queryTargetId));
  }
}

async function syncRoute() {
  await router.replace({
    path: '/ai-center/runtime-center',
    query: activeApp.value
      ? {
          targetType: activeApp.value.type,
          targetId: activeApp.value.id
        }
      : undefined
  });
}

async function selectApp(item: Api.Runtime.CatalogItem) {
  selectedAppId.value = item.id;
  conversationId.value = '';
  messages.value = [];
  lastExecution.value = null;
  executionDetail.value = null;
  await syncRoute();
  await loadSessions();
}

async function backToCatalog(shouldReplace = true) {
  selectedAppId.value = '';
  conversationId.value = '';
  messages.value = [];
  lastExecution.value = null;
  executionDetail.value = null;
  sessions.value = [];
  if (shouldReplace) {
    await router.replace({
      path: '/ai-center/runtime-center'
    });
  }
}

async function loadSessions() {
  if (!activeApp.value) {
    sessions.value = [];
    conversationId.value = '';
    messages.value = [];
    return;
  }

  sessionsLoading.value = true;
  const { error, data } = await fetchGetConversationSessions({
    sessionType: 'runtime',
    targetType: activeApp.value.type,
    targetId: activeApp.value.id
  });
  sessionsLoading.value = false;
  if (error || !data) return;

  sessions.value = data;
  if (!sessions.value.find(item => item.id === conversationId.value)) {
    conversationId.value = sessions.value[0]?.id || '';
  }

  if (conversationId.value) await loadMessages(conversationId.value);
  else messages.value = [];
}

async function loadMessages(targetConversationId: string) {
  if (!targetConversationId) {
    messages.value = [];
    return;
  }
  messagesLoading.value = true;
  const { error, data } = await fetchGetConversationMessages(targetConversationId);
  messagesLoading.value = false;
  if (error || !data) return;
  conversationId.value = targetConversationId;
  messages.value = data;
  await nextTick();
  scrollResultPaneToBottom();
}

async function createSession() {
  if (!activeApp.value) {
    window.$message?.warning('请先选择运行应用');
    return;
  }

  creatingSession.value = true;
  const { error, data } = await fetchCreateConversationSession({
    sessionType: 'runtime',
    targetType: activeApp.value.type,
    targetId: activeApp.value.id,
    targetName: activeApp.value.name,
    targetDescription: activeApp.value.description
  });
  creatingSession.value = false;

  if (error || !data) return;
  await loadSessions();
  await loadMessages(data.id);
}

async function removeSession(sessionId: string) {
  deletingSessionIds.value = [...deletingSessionIds.value, sessionId];
  const { error } = await fetchDeleteConversationSession(sessionId);
  deletingSessionIds.value = deletingSessionIds.value.filter(item => item !== sessionId);
  if (error) return;
  await loadSessions();
}

async function togglePin(session: Api.Chat.Session) {
  pinningSessionIds.value = [...pinningSessionIds.value, session.id];
  const { error } = await fetchUpdateConversationPin(session.id, !session.isPinned);
  pinningSessionIds.value = pinningSessionIds.value.filter(item => item !== session.id);
  if (error) return;
  await loadSessions();
}

async function fetchExecutionDetail(executionId?: string) {
  if (!executionId || !activeApp.value) {
    executionDetail.value = null;
    return;
  }

  executionDetailLoading.value = true;
  const request =
    activeApp.value.type === 'workflow'
      ? fetchWorkflowExecutionDetail(activeApp.value.id, executionId)
      : fetchAgentExecutionDetail(activeApp.value.id, executionId);
  const { error, data } = await request;
  executionDetailLoading.value = false;
  if (!error && data) executionDetail.value = data as Record<string, any>;
}

async function handleSend() {
  const message = inputMessage.value.trim();
  if (!message || !activeApp.value || running.value) return;

  let targetConversationId = conversationId.value;
  if (!targetConversationId) {
    const { error, data } = await fetchCreateConversationSession({
      sessionType: 'runtime',
      targetType: activeApp.value.type,
      targetId: activeApp.value.id,
      targetName: activeApp.value.name,
      targetDescription: activeApp.value.description
    });
    if (error || !data) return;
    targetConversationId = data.id;
    conversationId.value = targetConversationId;
  }

  messages.value.push({
    role: 'user',
    content: message,
    timestamp: new Date().toISOString()
  });
  messages.value.push({
    role: 'assistant',
    content: '',
    status: 'pending'
  });
  inputMessage.value = '';
  running.value = true;
  lastExecution.value = null;
  executionDetail.value = null;
  await nextTick();
  scrollResultPaneToBottom();

  const { error, data } = await fetchRuntimeExecute({
    conversationId: targetConversationId,
    targetType: activeApp.value.type,
    targetId: activeApp.value.id,
    message
  });
  running.value = false;

  if (error || !data) {
    await loadSessions();
    await loadMessages(targetConversationId);
    return;
  }

  lastExecution.value = data.execution;
  await fetchExecutionDetail(data.execution.executionId);
  await loadSessions();
  await loadMessages(data.conversationId);
}

function rerunFromSession(session: Api.Chat.Session) {
  const text = session.lastRole === 'user' ? session.lastMessage : '';
  if (text) inputMessage.value = text;
  loadMessages(session.id);
}

function scrollResultPaneToBottom() {
  const element = document.querySelector('.runtime-result-scroll .n-scrollbar-container');
  element?.scrollTo({ top: element.scrollHeight, behavior: 'smooth' });
}

watch(appFilter, () => {
  currentPage.value = 1;
  if (appFilter.value !== 'all' && activeApp.value?.type !== appFilter.value) {
    backToCatalog();
  }
});

watch(keyword, () => {
  currentPage.value = 1;
});

watch([filteredApps, pageSize], () => {
  if (currentPage.value > appPageCount.value) {
    currentPage.value = appPageCount.value;
  }
});

onMounted(async () => {
  await loadCatalog();
});
</script>

<template>
  <div class="runtime-center h-full overflow-hidden bg-slate-50 text-slate-900">
    <div class="h-full p-5 md:p-6">
      <div
        class="runtime-surface h-full min-h-0 overflow-hidden border border-slate-200 rounded-2xl bg-white shadow-sm"
      >
        <template v-if="!isExecutionView">
          <header class="border-b border-slate-200 bg-white px-5 py-4">
            <div class="flex flex-wrap items-center justify-between gap-4">
              <div>
                <div class="text-lg text-slate-950 font-semibold">运行中心</div>
                <div class="mt-1 text-sm text-slate-500">已发布应用的统一运行入口</div>
              </div>

              <div class="grid grid-cols-2 gap-3 text-xs text-slate-500 md:grid-cols-4">
                <div class="runtime-stat">
                  <div class="runtime-stat-value">{{ runtimeStats.appCount }}</div>
                  <div>应用</div>
                </div>
                <div class="runtime-stat">
                  <div class="runtime-stat-value">{{ runtimeStats.runCount }}</div>
                  <div>累计运行</div>
                </div>
                <div class="runtime-stat">
                  <div class="runtime-stat-value">{{ runtimeStats.successRate }}%</div>
                  <div>平均成功率</div>
                </div>
                <div class="runtime-stat">
                  <div class="runtime-stat-value">{{ runtimeStats.agentCount }}/{{ runtimeStats.workflowCount }}</div>
                  <div>Agent/Workflow</div>
                </div>
              </div>
            </div>

            <div class="mt-4 flex flex-wrap items-center gap-3">
              <NInput v-model:value="keyword" class="max-w-[360px]" placeholder="搜索应用名称、说明、标签" clearable>
                <template #prefix>
                  <icon-carbon:search />
                </template>
              </NInput>

              <div class="runtime-segment">
                <button :class="{ active: appFilter === 'all' }" @click="appFilter = 'all'">全部</button>
                <button :class="{ active: appFilter === 'agent' }" @click="appFilter = 'agent'">Agent 应用</button>
                <button :class="{ active: appFilter === 'workflow' }" @click="appFilter = 'workflow'">
                  Workflow 应用
                </button>
              </div>
            </div>
          </header>

          <NScrollbar class="h-[calc(100%-198px)]">
            <div class="runtime-app-grid p-4">
              <NSpin :show="catalogLoading">
                <div v-if="filteredApps.length === 0" class="py-16">
                  <NEmpty description="暂无已发布应用" />
                </div>

                <div v-else class="grid grid-cols-1 gap-3 2xl:grid-cols-4 lg:grid-cols-3 md:grid-cols-2">
                  <button
                    v-for="item in pagedApps"
                    :key="`${item.type}-${item.id}`"
                    class="runtime-app-card"
                    @click="selectApp(item)"
                  >
                    <div class="flex items-start justify-between gap-3">
                      <div class="min-w-0">
                        <div class="truncate text-base text-slate-950 font-semibold">
                          <span v-if="item.avatarEmoji" class="mr-1">{{ item.avatarEmoji }}</span>
                          {{ item.name }}
                        </div>
                        <div class="mt-1 truncate text-xs text-slate-500">{{ getAppIdentity(item) }}</div>
                      </div>
                      <NTag size="small" :type="item.type === 'workflow' ? 'info' : 'success'" :bordered="false">
                        {{ item.type === 'workflow' ? 'Workflow' : 'Agent' }}
                      </NTag>
                    </div>
                    <div class="line-clamp-2 mt-2 min-h-9 text-left text-xs text-slate-500 leading-5">
                      {{ item.description || '暂无描述' }}
                    </div>
                    <div
                      class="grid grid-cols-3 mt-3 gap-2 border-t border-slate-100 pt-2 text-left text-xs text-slate-500"
                    >
                      <div>
                        <strong>{{ item.callCount || 0 }}</strong>
                        <span>累计运行</span>
                      </div>
                      <div>
                        <strong>{{ item.successRate || 100 }}%</strong>
                        <span>成功率</span>
                      </div>
                      <div>
                        <strong>{{ formatTime(item.publishedAt) }}</strong>
                        <span>发布时间</span>
                      </div>
                    </div>
                  </button>
                </div>
              </NSpin>
            </div>
          </NScrollbar>

          <ListPagination
            v-model:page="currentPage"
            v-model:page-size="pageSize"
            :item-count="filteredApps.length"
            class="runtime-pagination border-slate-200 px-5"
          />
        </template>

        <template v-else>
          <header class="runtime-execution-header border-b border-slate-200 bg-white px-5 py-4">
            <div class="min-w-0 flex items-center justify-between gap-4">
              <div class="min-w-0 flex items-center gap-3">
                <NButton secondary @click="backToCatalog()">
                  <template #icon>
                    <icon-carbon:arrow-left />
                  </template>
                  应用列表
                </NButton>
                <div class="min-w-0">
                  <div class="mb-1 flex items-center gap-2">
                    <NTag size="small" :bordered="false" :type="runtimeType === 'workflow' ? 'info' : 'success'">
                      {{ getAppTypeLabel(runtimeType) }}
                    </NTag>
                    <span class="text-xs text-slate-400">Runtime App</span>
                  </div>
                  <div class="truncate text-lg text-slate-950 font-semibold">{{ activeApp?.name }}</div>
                </div>
              </div>

              <div class="hidden shrink-0 items-center gap-3 text-xs text-slate-500 md:flex">
                <span>{{ activeApp?.callCount || 0 }} 次运行</span>
                <span>{{ activeApp?.successRate || 100 }}% 成功率</span>
                <span>{{ formatTime(activeApp?.publishedAt) }} 发布</span>
              </div>
            </div>
          </header>

          <main
            class="runtime-execution-main grid grid-cols-1 min-h-0 gap-4 p-4 xl:grid-cols-[300px_minmax(0,1fr)_340px]"
          >
            <section class="runtime-panel">
              <div class="runtime-panel-header">
                <div>
                  <div class="text-sm text-slate-950 font-semibold">任务历史</div>
                  <div class="mt-1 text-xs text-slate-500">{{ activeApp?.name || '请选择应用' }}</div>
                </div>
                <NTooltip>
                  <template #trigger>
                    <NButton circle secondary type="primary" :loading="creatingSession" @click="createSession">
                      <template #icon>
                        <icon-carbon:add />
                      </template>
                    </NButton>
                  </template>
                  新建任务
                </NTooltip>
              </div>

              <NScrollbar class="runtime-scroll">
                <div class="p-3">
                  <NSpin :show="sessionsLoading">
                    <div v-if="sessions.length === 0" class="py-12">
                      <NEmpty description="暂无运行历史" />
                    </div>

                    <div v-for="group in groupedSessions" :key="group.label" class="mb-4">
                      <div class="mb-2 px-1 text-xs text-slate-400 font-medium">{{ group.label }}</div>
                      <div class="space-y-2">
                        <div
                          v-for="session in group.items"
                          :key="session.id"
                          class="runtime-history-card"
                          :class="{ active: conversationId === session.id }"
                          role="button"
                          tabindex="0"
                          @click="loadMessages(session.id)"
                          @keydown.enter.prevent="loadMessages(session.id)"
                        >
                          <div class="flex items-start gap-3">
                            <div
                              class="runtime-history-avatar"
                              :class="{ active: conversationId === session.id, pinned: session.isPinned }"
                            >
                              <icon-carbon:chat class="runtime-history-avatar-icon" />
                              <span v-if="session.isPinned" class="runtime-history-avatar-dot" />
                            </div>

                            <div class="min-w-0 flex-1 text-left">
                              <div class="mb-1 flex items-center justify-between gap-2">
                                <div class="min-w-0 flex flex-1 items-center gap-2">
                                  <div
                                    class="truncate text-sm font-medium"
                                    :class="conversationId === session.id ? 'text-blue-600' : 'text-slate-900'"
                                  >
                                    {{ session.title || '新任务' }}
                                  </div>
                                  <NTag v-if="session.isPinned" size="tiny" type="warning" round>置顶</NTag>
                                  <NTag v-else size="tiny" type="success" round>最近</NTag>
                                </div>
                                <span class="shrink-0 text-xs text-slate-400">{{ formatTime(session.time) }}</span>
                              </div>

                              <div class="truncate text-xs text-slate-500">
                                {{ session.lastMessage || '暂无运行内容' }}
                              </div>

                              <div class="mt-2 flex items-center justify-between gap-2">
                                <span class="text-xs text-slate-400">{{ session.messageCount }} 条消息</span>
                                <div class="flex items-center gap-1">
                                  <NButton text size="tiny" @click.stop="rerunFromSession(session)">
                                    <template #icon>
                                      <icon-carbon:redo class="text-sm" />
                                    </template>
                                    重跑
                                  </NButton>
                                  <NButton
                                    text
                                    size="tiny"
                                    :loading="pinningSessionIds.includes(session.id)"
                                    @click.stop="togglePin(session)"
                                  >
                                    <template #icon>
                                      <icon-carbon:bookmark class="text-sm" />
                                    </template>
                                    {{ session.isPinned ? '取消置顶' : '置顶' }}
                                  </NButton>
                                  <NButton
                                    text
                                    size="tiny"
                                    class="runtime-danger-action"
                                    :loading="deletingSessionIds.includes(session.id)"
                                    @click.stop="removeSession(session.id)"
                                  >
                                    <template #icon>
                                      <icon-carbon:trash-can class="text-sm" />
                                    </template>
                                    删除
                                  </NButton>
                                </div>
                              </div>
                            </div>
                          </div>
                        </div>
                      </div>
                    </div>
                  </NSpin>
                </div>
              </NScrollbar>
            </section>

            <section class="runtime-panel min-w-0">
              <div class="runtime-main-header">
                <div class="min-w-0">
                  <div class="truncate text-base text-slate-950 font-semibold">运行工作区</div>
                  <div class="line-clamp-1 mt-1 text-sm text-slate-500">
                    {{ activeApp?.description || '输入需求后开始运行。' }}
                  </div>
                </div>
                <NTag size="small" :type="executionStatus.type" :bordered="false">
                  {{ executionStatus.label }}
                </NTag>
              </div>

              <NScrollbar class="runtime-result-scroll flex-1">
                <div class="mx-auto max-w-4xl flex flex-col px-6 py-5">
                  <NSpin :show="messagesLoading">
                    <div v-if="messages.length === 0" class="py-14">
                      <NEmpty description="当前任务还没有运行结果" />
                    </div>

                    <VueMarkdownItProvider v-else>
                      <div
                        v-for="(message, index) in messages"
                        :key="`${message.role}-${index}`"
                        class="runtime-message"
                        :class="message.role === 'user' ? 'user' : 'assistant'"
                      >
                        <div v-if="message.role === 'user'" class="runtime-message-row user">
                          <div class="user runtime-message-body">
                            <div class="user runtime-message-title">
                              <span>{{ formatTime(message.timestamp) }}</span>
                              <span>运行输入</span>
                            </div>
                            <div class="user runtime-bubble">
                              <div class="runtime-content whitespace-pre-wrap break-words">
                                {{ renderResultContent(message.content) }}
                              </div>
                            </div>
                          </div>
                          <Avatar
                            :src="userInfo.avatar || ''"
                            :text="avatarText"
                            :version="userInfo.avatarVersion"
                            :size="38"
                            class="runtime-user-avatar"
                          />
                        </div>

                        <div v-else class="runtime-message-row assistant">
                          <div
                            class="runtime-dialog-avatar runtime-dialog-avatar-assistant"
                            :class="runtimeType"
                            :aria-label="`${activeApp?.name || '运行应用'}头像`"
                          >
                            <span class="runtime-dialog-avatar-text">{{ assistantAvatarText }}</span>
                            <span class="runtime-dialog-avatar-badge runtime-dialog-avatar-badge--assistant">
                              <icon-carbon:play />
                            </span>
                          </div>
                          <div class="runtime-message-body assistant">
                            <div class="runtime-message-title assistant">
                              <span>运行结果</span>
                              <span>{{ formatTime(message.timestamp) }}</span>
                            </div>
                            <div v-if="message.status === 'pending'" class="runtime-bubble assistant pending">
                              <icon-eos-icons:three-dots-loading class="text-xl text-blue-500" />
                            </div>
                            <div v-else-if="message.status === 'error'" class="runtime-bubble assistant error">
                              <div class="flex items-start gap-2 text-sm text-rose-600">
                                <icon-carbon:warning class="mt-0.5 shrink-0 text-base" />
                                <div class="min-w-0 flex-1">
                                  <div class="mb-0.5 font-medium">运行失败</div>
                                  <div class="break-words">{{ message.errorMessage || '运行失败' }}</div>
                                </div>
                              </div>
                            </div>
                            <div v-else class="runtime-bubble assistant">
                              <div class="runtime-content runtime-markdown">
                                <VueMarkdownIt :content="renderResultContent(message.content)" />
                              </div>
                            </div>
                            <div v-if="message.status !== 'pending'" class="runtime-message-actions">
                              <NButton
                                text
                                size="tiny"
                                class="runtime-copy-action"
                                @click="
                                  handleCopy(
                                    message.status === 'error'
                                      ? message.errorMessage || message.content
                                      : message.content
                                  )
                                "
                              >
                                <template #icon>
                                  <icon-mynaui:copy />
                                </template>
                                <span>复制</span>
                              </NButton>
                            </div>
                          </div>
                        </div>
                      </div>
                    </VueMarkdownItProvider>
                  </NSpin>
                </div>
              </NScrollbar>

              <div class="border-t border-slate-200 bg-white px-5 py-4">
                <div class="runtime-input-box">
                  <textarea
                    v-model="inputMessage"
                    class="runtime-textarea min-h-[72px] w-full resize-none"
                    placeholder="输入运行需求，例如：帮我分析这个销售报表"
                    @keydown.enter.exact.prevent="handleSend"
                  />

                  <div class="mt-2 flex flex-wrap items-center justify-between gap-3">
                    <div class="text-xs text-slate-500">当前任务：{{ conversationId || '首次运行时自动创建' }}</div>

                    <div class="flex items-center gap-3">
                      <span class="text-xs text-slate-400">Enter 运行</span>
                      <NButton type="primary" :disabled="isSendDisabled" :loading="running" @click="handleSend">
                        <template #icon>
                          <icon-carbon:play />
                        </template>
                        {{ running ? '运行中' : '开始运行' }}
                      </NButton>
                    </div>
                  </div>
                </div>
              </div>
            </section>

            <aside class="runtime-panel">
              <div class="runtime-panel-header">
                <div>
                  <div class="text-sm text-slate-950 font-semibold">执行详情</div>
                  <div class="mt-1 text-xs text-slate-500">Trace、Token 与运行状态</div>
                </div>
                <NTag size="small" :type="executionStatus.type" :bordered="false">
                  {{ executionStatus.label }}
                </NTag>
              </div>

              <NScrollbar class="runtime-scroll">
                <div class="p-4 space-y-4">
                  <div class="runtime-detail-grid">
                    <div>
                      <span>执行 ID</span>
                      <strong>{{ lastExecution?.executionId || executionDetail?.executionId || '-' }}</strong>
                    </div>
                    <div>
                      <span>耗时</span>
                      <strong>{{ formatDuration(lastExecution?.durationMs || executionDetail?.durationMs) }}</strong>
                    </div>
                    <div>
                      <span>Tokens</span>
                      <strong>{{ tokenUsage.totalTokens }}</strong>
                    </div>
                    <div>
                      <span>成本</span>
                      <strong>{{ tokenUsage.cost }}</strong>
                    </div>
                  </div>

                  <div class="runtime-detail-section">
                    <div class="runtime-section-title">应用信息</div>
                    <div class="text-xs text-slate-600 space-y-2">
                      <div class="flex justify-between gap-3">
                        <span>类型</span>
                        <strong>{{ getAppTypeLabel(activeApp?.type) }}</strong>
                      </div>
                      <div class="flex justify-between gap-3">
                        <span>负责人</span>
                        <strong>{{ activeApp?.ownerName || '-' }}</strong>
                      </div>
                      <div class="flex justify-between gap-3">
                        <span>模型</span>
                        <strong class="truncate">{{ activeApp?.models || '-' }}</strong>
                      </div>
                      <div class="flex justify-between gap-3">
                        <span>状态</span>
                        <strong>{{ activeApp?.status || '-' }}</strong>
                      </div>
                    </div>
                  </div>

                  <div class="runtime-detail-section">
                    <div class="runtime-section-title">执行轨迹</div>
                    <NSpin :show="executionDetailLoading">
                      <div v-if="traceItems.length === 0" class="py-6">
                        <NEmpty description="暂无执行轨迹" />
                      </div>
                      <div v-else class="space-y-3">
                        <div v-for="(item, index) in traceItems" :key="index" class="runtime-trace-item">
                          <div class="runtime-trace-line">
                            <span class="runtime-trace-dot" :class="getTraceStatusType(item.status)" />
                            <div class="min-w-0 flex-1">
                              <div class="flex items-center justify-between gap-3">
                                <div class="truncate text-sm text-slate-900 font-medium">{{ getTraceName(item) }}</div>
                                <NTag size="small" :type="getTraceStatusType(item.status)">
                                  {{ item.status || 'unknown' }}
                                </NTag>
                              </div>
                              <div class="mt-1 text-xs text-slate-500">
                                {{ item.nodeType || item.description || '执行步骤' }} ·
                                {{ formatDuration(item.durationMs) }}
                              </div>
                              <div
                                v-if="item.errorMessage"
                                class="mt-2 rounded-md bg-rose-50 px-2 py-1 text-xs text-rose-600"
                              >
                                {{ item.errorMessage }}
                              </div>
                            </div>
                          </div>
                        </div>
                      </div>
                    </NSpin>
                  </div>

                  <div class="runtime-detail-section">
                    <div class="runtime-section-title">Token 明细</div>
                    <div class="grid grid-cols-3 gap-2 text-center text-xs">
                      <div class="runtime-token-box">
                        <strong>{{ tokenUsage.promptTokens }}</strong>
                        <span>Prompt</span>
                      </div>
                      <div class="runtime-token-box">
                        <strong>{{ tokenUsage.completionTokens }}</strong>
                        <span>Completion</span>
                      </div>
                      <div class="runtime-token-box">
                        <strong>{{ tokenUsage.totalTokens }}</strong>
                        <span>Total</span>
                      </div>
                    </div>
                  </div>

                  <div v-if="lastExecution?.errorMessage || executionDetail?.errorMessage" class="runtime-error">
                    {{ lastExecution?.errorMessage || executionDetail?.errorMessage }}
                  </div>
                </div>
              </NScrollbar>
            </aside>
          </main>
        </template>
      </div>
    </div>
  </div>
</template>

<style scoped>
.runtime-center :deep(.n-input) {
  --n-color: rgba(248, 250, 252, 0.96);
  --n-color-focus: #fff;
  --n-border: 1px solid rgba(203, 213, 225, 0.9);
  --n-border-focus: 1px solid rgba(15, 23, 42, 0.28);
  --n-box-shadow-focus: 0 0 0 3px rgba(148, 163, 184, 0.16);
}

.runtime-stat {
  min-width: 86px;
  border: 1px solid rgba(226, 232, 240, 0.9);
  border-radius: 8px;
  background: #f8fafc;
  padding: 8px 10px;
}

.runtime-stat-value {
  margin-bottom: 2px;
  color: #0f172a;
  font-size: 15px;
  font-weight: 700;
}

.runtime-segment {
  display: inline-flex;
  gap: 4px;
  border: 1px solid rgba(226, 232, 240, 0.95);
  border-radius: 8px;
  background: #f8fafc;
  padding: 4px;
}

.runtime-segment button {
  border-radius: 6px;
  padding: 6px 12px;
  color: #64748b;
  font-size: 12px;
  transition: all 0.18s ease;
}

.runtime-segment button.active {
  background: #0f172a;
  color: #fff;
  box-shadow: 0 6px 14px rgba(15, 23, 42, 0.16);
}

.runtime-app-card {
  width: 220px;
  flex: 0 0 220px;
  border: 1px solid rgba(226, 232, 240, 0.95);
  border-radius: 8px;
  background: #fff;
  padding: 10px 12px;
  transition: all 0.18s ease;
}

.runtime-app-card:hover,
.runtime-app-card.active {
  border-color: rgba(15, 23, 42, 0.28);
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.08);
  transform: translateY(-1px);
}

.runtime-app-card.active {
  background: linear-gradient(180deg, #fff, #f8fafc);
}

.runtime-app-grid .runtime-app-card {
  width: 100%;
  min-height: 142px;
  flex: initial;
}

.runtime-app-grid .runtime-app-card strong {
  display: block;
  overflow: hidden;
  color: #0f172a;
  font-size: 13px;
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.runtime-app-grid .runtime-app-card span {
  display: block;
  margin-top: 2px;
}

.runtime-app-grid :deep(.n-spin-container) {
  min-height: 100%;
}

.runtime-pagination {
  display: flex;
  justify-content: flex-end;
}

.runtime-execution-main {
  height: calc(100% - 73px);
}

.runtime-panel {
  display: flex;
  min-height: 0;
  flex-direction: column;
  overflow: hidden;
  border: 1px solid rgba(226, 232, 240, 0.95);
  border-radius: 8px;
  background: #fff;
}

.runtime-panel-header,
.runtime-main-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  border-bottom: 1px solid rgba(226, 232, 240, 0.9);
  padding: 14px 16px;
}

.runtime-scroll {
  flex: 1;
  min-height: 0;
}

.runtime-history-card {
  width: 100%;
  cursor: pointer;
  border: 1px solid transparent;
  border-radius: 12px;
  background: #fff;
  padding: 12px;
  transition: all 0.18s ease;
}

.runtime-history-card:hover,
.runtime-history-card.active {
  border-color: rgba(147, 197, 253, 0.9);
  background: #eff6ff;
  box-shadow: 0 8px 18px rgba(15, 23, 42, 0.06);
}

.runtime-history-avatar {
  position: relative;
  display: flex;
  height: 36px;
  width: 36px;
  flex: 0 0 36px;
  align-items: center;
  justify-content: center;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  background: #f8fafc;
  color: #475569;
  font-size: 16px;
  transition:
    border-color 0.18s ease,
    background-color 0.18s ease,
    color 0.18s ease;
}

.runtime-history-avatar.active {
  border-color: #93c5fd;
  background: #eff6ff;
  color: #2563eb;
}

.runtime-history-avatar.pinned {
  border-color: #fde68a;
  background: #fffbeb;
  color: #b45309;
}

.runtime-history-avatar.active.pinned {
  border-color: #93c5fd;
  background: #eff6ff;
  color: #2563eb;
}

.runtime-history-avatar-icon {
  font-size: 17px;
}

.runtime-history-avatar-dot {
  position: absolute;
  top: -3px;
  right: -3px;
  height: 9px;
  width: 9px;
  border: 2px solid #fff;
  border-radius: 999px;
  background: #f59e0b;
}

.runtime-danger-action {
  color: #e11d48;
}

.runtime-meta {
  min-width: 100px;
  border: 1px solid rgba(226, 232, 240, 0.9);
  border-radius: 8px;
  background: #f8fafc;
  padding: 8px 10px;
}

.runtime-meta strong {
  margin-top: 2px;
  display: block;
  color: #0f172a;
  font-weight: 600;
}

.runtime-message {
  margin-bottom: 16px;
  animation: runtime-slide-in 0.2s ease-out;
}

.runtime-message-row {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.runtime-message-row.user {
  justify-content: flex-end;
}

.runtime-message-row.assistant {
  justify-content: flex-start;
}

.runtime-message-body {
  min-width: 0;
}

.runtime-message-body.user {
  max-width: min(70%, 720px);
}

.runtime-message-body.assistant {
  max-width: min(82%, 860px);
}

.runtime-message-title {
  margin-bottom: 6px;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 4px;
  color: #64748b;
  font-size: 12px;
}

.runtime-message-title.user {
  justify-content: flex-end;
}

.runtime-message-title.assistant {
  justify-content: flex-start;
}

.runtime-user-avatar {
  margin-top: 24px;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
}

.runtime-dialog-avatar {
  position: relative;
  margin-top: 22px;
  display: inline-flex;
  height: 38px;
  width: 38px;
  flex: 0 0 38px;
  align-items: center;
  justify-content: center;
  overflow: visible;
  border: 1px solid #e2e8f0;
  border-radius: 999px;
  background: #fff;
  color: #0f172a;
  font-size: 14px;
  font-weight: 700;
  line-height: 1;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
}

.runtime-dialog-avatar::before {
  position: absolute;
  inset: 3px;
  border-radius: inherit;
  content: '';
}

.runtime-dialog-avatar-assistant {
  border-color: #bfdbfe;
  background: #eff6ff;
  color: #1d4ed8;
}

.runtime-dialog-avatar-assistant.workflow {
  border-color: #bbf7d0;
  background: #f0fdf4;
  color: #15803d;
}

.runtime-dialog-avatar-text {
  position: relative;
  z-index: 1;
}

.runtime-dialog-avatar-badge {
  position: absolute;
  right: -3px;
  bottom: -3px;
  z-index: 2;
  display: inline-flex;
  height: 16px;
  width: 16px;
  align-items: center;
  justify-content: center;
  border: 2px solid #fff;
  border-radius: 999px;
  font-size: 10px;
}

.runtime-dialog-avatar-badge--assistant {
  background: #2563eb;
  color: #fff;
}

.runtime-dialog-avatar-assistant.workflow .runtime-dialog-avatar-badge--assistant {
  background: #16a34a;
}

.runtime-bubble {
  word-break: break-word;
  box-shadow: 0 2px 5px rgba(15, 23, 42, 0.06);
}

.runtime-bubble.user {
  border-radius: 16px 4px 16px 16px;
  background: linear-gradient(135deg, #3b82f6, #2563eb);
  padding: 10px 16px;
  color: #fff;
  box-shadow: 0 3px 8px rgba(37, 99, 235, 0.16);
}

.runtime-bubble.assistant {
  border: 1px solid rgba(226, 232, 240, 0.95);
  border-radius: 4px 16px 16px 16px;
  background: #f8fafc;
  padding: 12px 16px;
  color: #1e293b;
}

.runtime-bubble.pending {
  display: inline-flex;
  min-width: 58px;
  align-items: center;
  justify-content: center;
  padding: 12px 16px;
}

.runtime-bubble.error {
  border-color: rgba(254, 205, 211, 0.95);
  background: #fff1f2;
}

.runtime-pre {
  white-space: pre-wrap;
  word-break: break-word;
  font-family: inherit;
  font-size: 14px;
  line-height: 1.65;
}

.runtime-content {
  font-size: 14px;
  line-height: 1.7;
}

.runtime-message-actions {
  margin-top: 6px;
  margin-left: 4px;
  display: flex;
  align-items: center;
  gap: 4px;
}

.runtime-copy-action {
  color: #64748b;
  opacity: 0.72;
  transition:
    color 0.18s ease,
    opacity 0.18s ease;
}

.runtime-copy-action:hover {
  color: #334155;
  opacity: 1;
}

.runtime-markdown :deep(.prose) {
  max-width: none;
  color: inherit;
  font-size: 14px;
}

.runtime-markdown :deep(.prose p) {
  margin: 0.75em 0;
  line-height: 1.7;
}

.runtime-markdown :deep(.prose p:first-child) {
  margin-top: 0;
}

.runtime-markdown :deep(.prose p:last-child) {
  margin-bottom: 0;
}

.runtime-markdown :deep(.prose code) {
  border-radius: 4px;
  background: rgba(15, 23, 42, 0.08);
  padding: 0.15em 0.4em;
  font-family: 'SF Mono', Monaco, 'Cascadia Code', monospace;
  font-size: 0.86em;
}

.runtime-markdown :deep(.prose pre) {
  overflow-x: auto;
  border-radius: 8px;
  background: rgba(15, 23, 42, 0.06);
  padding: 12px;
  margin: 0.75em 0;
  border: 1px solid rgba(15, 23, 42, 0.08);
}

.runtime-markdown :deep(.prose pre code) {
  border-radius: 0;
  background: transparent;
  padding: 0;
  font-size: 0.86em;
}

.runtime-markdown :deep(.prose ul),
.runtime-markdown :deep(.prose ol) {
  margin: 0.75em 0;
  padding-left: 1.5em;
}

.runtime-markdown :deep(.prose li) {
  margin: 0.375em 0;
  line-height: 1.6;
}

.runtime-markdown :deep(.prose blockquote) {
  margin: 0.75em 0;
  border-left: 3px solid #3b82f6;
  padding-left: 1em;
  color: #64748b;
  font-style: italic;
}

.runtime-markdown :deep(.prose h1),
.runtime-markdown :deep(.prose h2),
.runtime-markdown :deep(.prose h3),
.runtime-markdown :deep(.prose h4),
.runtime-markdown :deep(.prose h5),
.runtime-markdown :deep(.prose h6) {
  margin: 1em 0 0.5em;
  font-weight: 600;
  line-height: 1.3;
}

.runtime-markdown :deep(.prose h1:first-child),
.runtime-markdown :deep(.prose h2:first-child),
.runtime-markdown :deep(.prose h3:first-child),
.runtime-markdown :deep(.prose h4:first-child),
.runtime-markdown :deep(.prose h5:first-child),
.runtime-markdown :deep(.prose h6:first-child) {
  margin-top: 0;
}

.runtime-markdown :deep(.prose h1) {
  font-size: 1.5em;
}

.runtime-markdown :deep(.prose h2) {
  font-size: 1.3em;
}

.runtime-markdown :deep(.prose h3) {
  font-size: 1.15em;
}

.runtime-markdown :deep(.prose h4) {
  font-size: 1em;
}

.runtime-markdown :deep(.prose a) {
  color: #3b82f6;
  text-decoration: underline;
  text-decoration-thickness: 1px;
  text-underline-offset: 2px;
}

.runtime-markdown :deep(.prose a:hover) {
  color: #2563eb;
}

.runtime-markdown :deep(.prose table) {
  width: 100%;
  margin: 1em 0;
  border-collapse: collapse;
  font-size: 0.875em;
}

.runtime-markdown :deep(.prose th),
.runtime-markdown :deep(.prose td) {
  border: 1px solid #e5e7eb;
  padding: 0.5em 0.75em;
  text-align: left;
}

.runtime-markdown :deep(.prose th) {
  background: rgba(15, 23, 42, 0.03);
  font-weight: 600;
}

.runtime-markdown :deep(.prose tr:nth-child(even)) {
  background: rgba(15, 23, 42, 0.02);
}

.runtime-markdown :deep(.prose hr) {
  margin: 1.5em 0;
  border: none;
  border-top: 1px solid #e5e7eb;
}

.runtime-input-box {
  border: 1px solid rgba(203, 213, 225, 0.95);
  border-radius: 12px;
  background: #f8fafc;
  padding: 12px;
  transition:
    border-color 0.18s ease,
    box-shadow 0.18s ease,
    background-color 0.18s ease;
}

.runtime-input-box:focus-within {
  border-color: rgba(59, 130, 246, 0.58);
  background: #fff;
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.12);
}

.runtime-textarea {
  border: none;
  outline: none;
  background: transparent;
  color: #0f172a;
  font-size: 14px;
  line-height: 1.6;
}

.runtime-detail-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.runtime-detail-grid > div,
.runtime-detail-section {
  border: 1px solid rgba(226, 232, 240, 0.9);
  border-radius: 8px;
  background: #f8fafc;
  padding: 12px;
}

.runtime-detail-grid span,
.runtime-token-box span {
  display: block;
  color: #64748b;
  font-size: 12px;
}

.runtime-detail-grid strong,
.runtime-token-box strong {
  margin-top: 4px;
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #0f172a;
  font-size: 13px;
  font-weight: 700;
}

.runtime-section-title {
  margin-bottom: 10px;
  color: #0f172a;
  font-size: 13px;
  font-weight: 700;
}

.runtime-trace-line {
  display: flex;
  gap: 10px;
}

.runtime-trace-dot {
  margin-top: 5px;
  height: 9px;
  width: 9px;
  flex: 0 0 9px;
  border-radius: 999px;
  background: #94a3b8;
}

.runtime-trace-dot.success {
  background: #22c55e;
}

.runtime-trace-dot.error {
  background: #ef4444;
}

.runtime-trace-dot.info {
  background: #3b82f6;
}

.runtime-token-box {
  border: 1px solid rgba(226, 232, 240, 0.9);
  border-radius: 8px;
  background: #fff;
  padding: 10px 6px;
}

.runtime-error {
  border: 1px solid rgba(244, 63, 94, 0.22);
  border-radius: 8px;
  background: #fff1f2;
  padding: 12px;
  color: #be123c;
  font-size: 12px;
  line-height: 1.6;
}

@keyframes runtime-slide-in {
  from {
    transform: translateY(4px);
    opacity: 0;
  }

  to {
    transform: translateY(0);
    opacity: 1;
  }
}

@media (max-width: 768px) {
  .runtime-message-body.user,
  .runtime-message-body.assistant {
    max-width: calc(100% - 46px);
  }
}
</style>

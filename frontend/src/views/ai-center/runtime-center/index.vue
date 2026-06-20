<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import {
  NButton,
  NEmpty,
  NInput,
  NScrollbar,
  NSpin,
  NTag
} from 'naive-ui';
import {
  fetchCreateConversationSession,
  fetchDeleteConversationSession,
  fetchGetConversationMessages,
  fetchGetConversationSessions,
  fetchRuntimeCatalog,
  fetchRuntimeExecute,
  fetchUpdateConversationPin
} from '@/service/api';

defineOptions({
  name: 'RuntimeCenter'
});

const route = useRoute();
const router = useRouter();

const catalogLoading = ref(false);
const sessionsLoading = ref(false);
const messagesLoading = ref(false);
const running = ref(false);
const creatingSession = ref(false);
const deletingSessionIds = ref<string[]>([]);
const pinningSessionIds = ref<string[]>([]);

const runtimeType = ref<'agent' | 'workflow'>('agent');
const catalog = ref<Api.Runtime.Catalog>({
  agents: [],
  workflows: [],
  agentCount: 0,
  workflowCount: 0
});
const sessions = ref<Api.Chat.Session[]>([]);
const messages = ref<Api.Chat.Message[]>([]);
const keyword = ref('');
const inputMessage = ref('');
const conversationId = ref('');
const lastExecution = ref<Api.Runtime.ExecuteResult['execution'] | null>(null);

const targetOptions = computed(() => runtimeType.value === 'agent' ? catalog.value.agents : catalog.value.workflows);

const filteredTargets = computed(() => {
  const normalizedKeyword = keyword.value.trim().toLowerCase();
  if (!normalizedKeyword) return targetOptions.value;
  return targetOptions.value.filter(item =>
    [item.name, item.description, item.tags, item.ownerName]
      .join(' ')
      .toLowerCase()
      .includes(normalizedKeyword)
  );
});

const selectedTargetId = ref('');

const activeTarget = computed(() =>
  targetOptions.value.find(item => item.id === selectedTargetId.value) || null
);

const canSend = computed(() => !activeTarget.value || !inputMessage.value.trim() || running.value);

function formatTime(value?: string | null) {
  if (!value) return '-';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '-';
  const now = new Date();
  const sameDay = date.toDateString() === now.toDateString();
  if (sameDay) {
    return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' });
  }
  return `${date.getMonth() + 1}-${String(date.getDate()).padStart(2, '0')} ${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`;
}

function formatSessionTime(value?: string) {
  if (!value) return '';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '';
  const now = new Date();
  if (date.toDateString() === now.toDateString()) {
    return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' });
  }
  return `${date.getMonth() + 1}-${String(date.getDate()).padStart(2, '0')}`;
}

function renderWorkflowContent(content: string) {
  if (!content) return '';
  return content;
}

async function loadCatalog() {
  catalogLoading.value = true;
  const { error, data } = await fetchRuntimeCatalog();
  catalogLoading.value = false;
  if (error || !data) return;

  catalog.value = data;

  const queryType = route.query.targetType === 'workflow' ? 'workflow' : 'agent';
  const queryTargetId = typeof route.query.targetId === 'string' ? route.query.targetId : '';
  runtimeType.value = queryType;

  const currentOptions = queryType === 'agent' ? data.agents : data.workflows;
  selectedTargetId.value = currentOptions.find(item => item.id === queryTargetId)?.id || currentOptions[0]?.id || '';

  if (!selectedTargetId.value && queryType === 'agent' && data.workflows.length > 0) {
    runtimeType.value = 'workflow';
    selectedTargetId.value = data.workflows[0].id;
  }

  await syncRoute();
  await loadSessions();
}

async function syncRoute() {
  await router.replace({
    path: '/ai-center/runtime-center',
    query: activeTarget.value
      ? {
          targetType: runtimeType.value,
          targetId: activeTarget.value.id
        }
      : undefined
  });
}

async function selectTarget(type: 'agent' | 'workflow', id: string) {
  runtimeType.value = type;
  selectedTargetId.value = id;
  conversationId.value = '';
  messages.value = [];
  lastExecution.value = null;
  await syncRoute();
  await loadSessions();
}

async function loadSessions() {
  if (!activeTarget.value) {
    sessions.value = [];
    conversationId.value = '';
    messages.value = [];
    return;
  }

  sessionsLoading.value = true;
  const { error, data } = await fetchGetConversationSessions({
    sessionType: 'runtime',
    targetType: runtimeType.value,
    targetId: activeTarget.value.id
  });
  sessionsLoading.value = false;
  if (error || !data) return;

  sessions.value = data;

  if (!sessions.value.find(item => item.id === conversationId.value)) {
    conversationId.value = sessions.value[0]?.id || '';
  }

  if (conversationId.value) {
    await loadMessages(conversationId.value);
  } else {
    messages.value = [];
  }
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
  scrollMessagePaneToBottom();
}

async function createSession() {
  if (!activeTarget.value) {
    window.$message?.warning('请先选择运行对象');
    return;
  }

  creatingSession.value = true;
  const { error, data } = await fetchCreateConversationSession({
    sessionType: 'runtime',
    targetType: runtimeType.value,
    targetId: activeTarget.value.id,
    targetName: activeTarget.value.name,
    targetDescription: activeTarget.value.description
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

async function handleSend() {
  const message = inputMessage.value.trim();
  if (!message || !activeTarget.value || running.value) return;

  let targetConversationId = conversationId.value;
  if (!targetConversationId) {
    const { error, data } = await fetchCreateConversationSession({
      sessionType: 'runtime',
      targetType: runtimeType.value,
      targetId: activeTarget.value.id,
      targetName: activeTarget.value.name,
      targetDescription: activeTarget.value.description
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
  await nextTick();
  scrollMessagePaneToBottom();

  const { error, data } = await fetchRuntimeExecute({
    conversationId: targetConversationId,
    targetType: runtimeType.value,
    targetId: activeTarget.value.id,
    message
  });
  running.value = false;

  if (error || !data) {
    await loadSessions();
    await loadMessages(targetConversationId);
    return;
  }

  lastExecution.value = data.execution;
  await loadSessions();
  await loadMessages(data.conversationId);
}

function scrollMessagePaneToBottom() {
  const element = document.querySelector('.runtime-message-scroll .n-scrollbar-container');
  element?.scrollTo({ top: element.scrollHeight, behavior: 'smooth' });
}

watch(runtimeType, async value => {
  const options = value === 'agent' ? catalog.value.agents : catalog.value.workflows;
  selectedTargetId.value = options[0]?.id || '';
  conversationId.value = '';
  messages.value = [];
  lastExecution.value = null;
  await syncRoute();
  await loadSessions();
});

onMounted(async () => {
  await loadCatalog();
});
</script>

<template>
  <div class="runtime-center h-full bg-[#06111f] text-white">
    <div class="h-full flex flex-col">
      <div class="flex-shrink-0 border-b border-white/10 bg-[linear-gradient(135deg,rgba(15,23,42,0.96),rgba(8,47,73,0.94))] px-6 py-5">
        <div class="flex flex-wrap items-center justify-between gap-4">
          <div>
            <div class="mb-2 flex items-center gap-2 text-xs uppercase tracking-[0.28em] text-emerald-300/80">
              <span>Runtime Center</span>
              <span class="h-1 w-1 rounded-full bg-emerald-400" />
              <span>Published Assets</span>
            </div>
            <h1 class="text-2xl font-semibold text-slate-50">已发布 Agent / Workflow 运行台</h1>
            <p class="mt-1 text-sm text-slate-300">
              选择已发布对象后直接运行，多会话按用户隔离，并保留历史记录。
            </p>
          </div>

          <div class="flex items-center gap-2 rounded-2xl border border-white/10 bg-white/5 p-1">
            <button
              class="rounded-xl px-4 py-2 text-sm transition"
              :class="runtimeType === 'agent' ? 'bg-emerald-400 text-slate-950' : 'text-slate-300 hover:bg-white/5'"
              @click="selectTarget('agent', catalog.agents[0]?.id || '')"
            >
              Agent
            </button>
            <button
              class="rounded-xl px-4 py-2 text-sm transition"
              :class="runtimeType === 'workflow' ? 'bg-emerald-400 text-slate-950' : 'text-slate-300 hover:bg-white/5'"
              @click="selectTarget('workflow', catalog.workflows[0]?.id || '')"
            >
              Workflow
            </button>
          </div>
        </div>
      </div>

      <div class="min-h-0 flex-1">
        <div class="grid h-full min-h-0 grid-cols-1 xl:grid-cols-[320px_320px_minmax(0,1fr)]">
          <div class="border-r border-white/10 bg-[#081527]">
            <div class="border-b border-white/10 px-4 py-4">
              <div class="mb-3 flex items-center justify-between">
                <div>
                  <div class="text-sm font-medium text-slate-100">
                    {{ runtimeType === 'agent' ? '已发布 Agent' : '已发布 Workflow' }}
                  </div>
                  <div class="text-xs text-slate-400">
                    {{ runtimeType === 'agent' ? catalog.agentCount : catalog.workflowCount }} 个可运行对象
                  </div>
                </div>
                <NTag size="small" round type="success">
                  {{ runtimeType === 'agent' ? '智能体' : '工作流' }}
                </NTag>
              </div>

              <NInput v-model:value="keyword" placeholder="搜索名称、描述、标签" clearable>
                <template #prefix>
                  <icon-carbon:search />
                </template>
              </NInput>
            </div>

            <NScrollbar class="h-[calc(100%-96px)]">
              <div class="space-y-3 p-3">
                <NSpin :show="catalogLoading">
                  <div v-if="filteredTargets.length === 0" class="py-12">
                    <NEmpty description="暂无可运行对象" />
                  </div>

                  <button
                    v-for="item in filteredTargets"
                    :key="item.id"
                    class="w-full rounded-2xl border p-4 text-left transition-all"
                    :class="selectedTargetId === item.id
                      ? 'border-emerald-400 bg-emerald-400/12 shadow-[0_0_0_1px_rgba(52,211,153,0.24)]'
                      : 'border-white/8 bg-white/[0.03] hover:border-white/20 hover:bg-white/[0.06]'"
                    @click="selectTarget(runtimeType, item.id)"
                  >
                    <div class="mb-2 flex items-start justify-between gap-3">
                      <div class="min-w-0">
                        <div class="truncate text-sm font-semibold text-slate-100">
                          <span v-if="item.avatarEmoji" class="mr-1">{{ item.avatarEmoji }}</span>{{ item.name }}
                        </div>
                        <div class="mt-1 line-clamp-2 text-xs leading-5 text-slate-400">
                          {{ item.description || '暂无描述' }}
                        </div>
                      </div>
                      <NTag size="small" :type="item.status === '运行中' ? 'success' : 'default'">
                        {{ item.status }}
                      </NTag>
                    </div>

                    <div class="flex flex-wrap gap-2 text-xs text-slate-400">
                      <span>{{ item.ownerName || 'system' }}</span>
                      <span>·</span>
                      <span>{{ item.callCount || 0 }} 次调用</span>
                      <span>·</span>
                      <span>{{ item.successRate || 100 }}% 成功率</span>
                    </div>
                  </button>
                </NSpin>
              </div>
            </NScrollbar>
          </div>

          <div class="border-r border-white/10 bg-[#0b1a2e]">
            <div class="border-b border-white/10 px-4 py-4">
              <div class="mb-3 flex items-center justify-between">
                <div>
                  <div class="text-sm font-medium text-slate-100">运行会话</div>
                  <div class="text-xs text-slate-400">与当前用户绑定，可创建多个独立会话</div>
                </div>
                <NButton type="primary" secondary :loading="creatingSession" @click="createSession">
                  <template #icon>
                    <icon-carbon:add />
                  </template>
                  新会话
                </NButton>
              </div>
            </div>

            <NScrollbar class="h-[calc(100%-96px)]">
              <div class="space-y-2 p-3">
                <NSpin :show="sessionsLoading">
                  <div v-if="sessions.length === 0" class="py-12">
                    <NEmpty description="当前对象暂无会话" />
                  </div>

                  <button
                    v-for="session in sessions"
                    :key="session.id"
                    class="w-full rounded-2xl border p-3 text-left transition-all"
                    :class="conversationId === session.id
                      ? 'border-sky-400 bg-sky-400/10'
                      : 'border-white/8 bg-white/[0.03] hover:border-white/20 hover:bg-white/[0.05]'"
                    @click="loadMessages(session.id)"
                  >
                    <div class="mb-2 flex items-start justify-between gap-2">
                      <div class="min-w-0">
                        <div class="truncate text-sm font-medium text-slate-100">{{ session.title || '新会话' }}</div>
                        <div class="mt-1 truncate text-xs text-slate-400">{{ session.lastMessage || '暂无消息' }}</div>
                      </div>
                      <div class="text-[11px] text-slate-500">{{ formatSessionTime(session.time) }}</div>
                    </div>

                    <div class="flex items-center justify-between gap-2 text-xs text-slate-400">
                      <div class="flex items-center gap-2">
                        <NTag size="small" :type="session.isPinned ? 'warning' : 'default'">
                          {{ session.isPinned ? '置顶' : '普通' }}
                        </NTag>
                        <span>{{ session.messageCount }} 条消息</span>
                      </div>

                      <div class="flex items-center gap-2">
                        <button class="text-slate-400 hover:text-slate-200" @click.stop="togglePin(session)">
                          {{ pinningSessionIds.includes(session.id) ? '处理中' : (session.isPinned ? '取消置顶' : '置顶') }}
                        </button>
                        <button class="text-rose-300 hover:text-rose-200" @click.stop="removeSession(session.id)">
                          {{ deletingSessionIds.includes(session.id) ? '删除中' : '删除' }}
                        </button>
                      </div>
                    </div>
                  </button>
                </NSpin>
              </div>
            </NScrollbar>
          </div>

          <div class="min-w-0 bg-[radial-gradient(circle_at_top,rgba(34,197,94,0.12),transparent_28%),linear-gradient(180deg,#091423,#040b14)]">
            <div class="border-b border-white/10 px-5 py-4">
              <div class="flex flex-wrap items-start justify-between gap-4">
                <div class="min-w-0">
                  <div class="mb-2 flex items-center gap-2">
                    <NTag size="small" type="success">{{ runtimeType === 'agent' ? 'Agent' : 'Workflow' }}</NTag>
                    <span class="text-xs uppercase tracking-[0.24em] text-emerald-300/80">Live Run</span>
                  </div>
                  <div class="truncate text-xl font-semibold text-slate-50">
                    {{ activeTarget?.name || '请选择运行对象' }}
                  </div>
                  <div class="mt-1 max-w-2xl text-sm text-slate-400">
                    {{ activeTarget?.description || '从左侧选择已发布的 Agent 或 Workflow，随后在这里直接运行。' }}
                  </div>
                </div>

                <div class="grid grid-cols-2 gap-3 rounded-2xl border border-white/10 bg-white/[0.04] p-3 text-xs text-slate-300">
                  <div>
                    <div class="text-slate-500">发布时间</div>
                    <div class="mt-1">{{ formatTime(activeTarget?.publishedAt) }}</div>
                  </div>
                  <div>
                    <div class="text-slate-500">最近更新</div>
                    <div class="mt-1">{{ formatTime(activeTarget?.updatedAt) }}</div>
                  </div>
                  <div>
                    <div class="text-slate-500">累计运行</div>
                    <div class="mt-1">{{ activeTarget?.callCount || 0 }}</div>
                  </div>
                  <div>
                    <div class="text-slate-500">成功率</div>
                    <div class="mt-1">{{ activeTarget?.successRate || 100 }}%</div>
                  </div>
                </div>
              </div>
            </div>

            <div class="flex h-[calc(100%-109px)] flex-col">
              <NScrollbar class="runtime-message-scroll flex-1">
                <div class="mx-auto flex max-w-4xl flex-col gap-4 px-5 py-6">
                  <NSpin :show="messagesLoading">
                    <div v-if="messages.length === 0" class="py-18">
                      <NEmpty description="当前会话还没有运行记录" />
                    </div>

                    <div
                      v-for="(message, index) in messages"
                      :key="`${message.role}-${index}`"
                      class="flex"
                      :class="message.role === 'user' ? 'justify-end' : 'justify-start'"
                    >
                      <div
                        class="max-w-[85%] rounded-3xl px-4 py-3 shadow-sm"
                        :class="message.role === 'user'
                          ? 'bg-emerald-400 text-slate-950'
                          : 'border border-white/10 bg-white/[0.04] text-slate-100'"
                      >
                        <div class="mb-2 flex items-center gap-2 text-[11px] opacity-70">
                          <span>{{ message.role === 'user' ? '你' : (runtimeType === 'agent' ? 'Agent' : 'Workflow') }}</span>
                          <span>{{ formatTime(message.timestamp) }}</span>
                        </div>
                        <pre
                          class="whitespace-pre-wrap break-words text-sm leading-6 font-sans"
                        >{{ renderWorkflowContent(message.content || (message.status === 'pending' ? '运行中...' : '')) }}</pre>
                        <div v-if="message.status === 'error'" class="mt-2 text-xs text-rose-300">
                          {{ message.errorMessage || '运行失败' }}
                        </div>
                      </div>
                    </div>
                  </NSpin>
                </div>
              </NScrollbar>

              <div class="border-t border-white/10 bg-[#07111d]/96 px-5 py-4">
                <div v-if="lastExecution" class="mb-3 flex flex-wrap gap-2 text-xs text-slate-400">
                  <NTag size="small" type="info">执行ID {{ lastExecution.executionId || '-' }}</NTag>
                  <NTag size="small" :type="lastExecution.success === false ? 'error' : 'success'">
                    {{ lastExecution.success === false ? '失败' : '完成' }}
                  </NTag>
                  <NTag size="small" type="default">耗时 {{ lastExecution.durationMs || 0 }} ms</NTag>
                  <NTag size="small" type="warning">Tokens {{ lastExecution.tokens?.totalTokens || 0 }}</NTag>
                </div>

                <div class="rounded-3xl border border-white/10 bg-white/[0.03] p-3">
                  <textarea
                    v-model="inputMessage"
                    class="min-h-[88px] w-full resize-none border-none bg-transparent text-sm leading-6 text-slate-100 outline-none"
                    :placeholder="activeTarget ? `向${runtimeType === 'agent' ? ' Agent' : ' Workflow'} 输入运行内容...` : '请先从左侧选择运行对象'"
                    @keydown.enter.exact.prevent="handleSend"
                  />

                  <div class="mt-3 flex flex-wrap items-center justify-between gap-3">
                    <div class="text-xs text-slate-500">
                      当前会话：{{ conversationId || '首次发送时自动创建' }}
                    </div>

                    <div class="flex items-center gap-3">
                      <span class="text-xs text-slate-500">Enter 发送</span>
                      <NButton type="primary" :disabled="canSend" :loading="running" @click="handleSend">
                        {{ running ? '运行中' : '发送运行请求' }}
                      </NButton>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.runtime-center :deep(.n-input) {
  --n-color: rgba(255, 255, 255, 0.04);
  --n-color-focus: rgba(255, 255, 255, 0.08);
  --n-border: 1px solid rgba(255, 255, 255, 0.08);
  --n-border-focus: 1px solid rgba(52, 211, 153, 0.5);
  --n-box-shadow-focus: 0 0 0 3px rgba(52, 211, 153, 0.12);
  --n-text-color: #f8fafc;
  --n-placeholder-color: rgba(148, 163, 184, 0.8);
}
</style>

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
  <div class="runtime-center h-full overflow-hidden bg-slate-50 text-slate-900">
    <div class="runtime-shell h-full p-5 md:p-6">
      <div class="runtime-surface h-full min-h-0 overflow-hidden rounded-[28px] border border-white/70 bg-white/92 shadow-[0_18px_50px_rgba(15,23,42,0.08)] backdrop-blur">
        <div class="runtime-hero border-b border-slate-200/80 px-6 py-5">
          <div class="flex justify-end">
            <div class="runtime-switcher">
              <button
                class="runtime-switcher-button"
                :class="runtimeType === 'agent' ? 'runtime-switcher-button-active' : ''"
                @click="selectTarget('agent', catalog.agents[0]?.id || '')"
              >
                <span class="runtime-switcher-kicker">A</span>
                Agent
              </button>
              <button
                class="runtime-switcher-button"
                :class="runtimeType === 'workflow' ? 'runtime-switcher-button-active' : ''"
                @click="selectTarget('workflow', catalog.workflows[0]?.id || '')"
              >
                <span class="runtime-switcher-kicker">W</span>
                Workflow
              </button>
            </div>
          </div>
        </div>

        <div class="min-h-0 h-[calc(100%-137px)]">
          <div class="grid h-full min-h-0 grid-cols-1 gap-4 p-4 xl:grid-cols-[300px_300px_minmax(0,1fr)]">
            <section class="runtime-panel min-h-0">
              <div class="runtime-panel-header">
                <div>
                  <div class="text-sm font-semibold text-slate-900">
                    {{ runtimeType === 'agent' ? '已发布 Agent' : '已发布 Workflow' }}
                  </div>
                  <div class="mt-1 text-xs text-slate-500">
                    {{ runtimeType === 'agent' ? catalog.agentCount : catalog.workflowCount }} 个可运行对象
                  </div>
                </div>
                <NTag size="small" round :bordered="false" type="success">
                  {{ runtimeType === 'agent' ? '智能体' : '工作流' }}
                </NTag>
              </div>

              <div class="px-4 pb-4">
                <NInput v-model:value="keyword" placeholder="搜索名称、描述、标签" clearable>
                  <template #prefix>
                    <icon-carbon:search />
                  </template>
                </NInput>
              </div>

              <NScrollbar class="runtime-scroll">
                <div class="space-y-3 p-3">
                  <NSpin :show="catalogLoading">
                    <div v-if="filteredTargets.length === 0" class="py-12">
                      <NEmpty description="暂无可运行对象" />
                    </div>

                    <button
                      v-for="item in filteredTargets"
                      :key="item.id"
                      class="runtime-card-button"
                      :class="selectedTargetId === item.id ? 'runtime-card-button-active runtime-card-button-emerald' : ''"
                      @click="selectTarget(runtimeType, item.id)"
                    >
                      <div class="mb-3 flex items-start justify-between gap-3">
                        <div class="min-w-0">
                          <div class="truncate text-sm font-semibold text-slate-900">
                            <span v-if="item.avatarEmoji" class="mr-1">{{ item.avatarEmoji }}</span>{{ item.name }}
                          </div>
                          <div class="mt-1 line-clamp-2 text-xs leading-5 text-slate-500">
                            {{ item.description || '暂无描述' }}
                          </div>
                        </div>
                        <NTag size="small" :type="item.status === '运行中' ? 'success' : 'default'">
                          {{ item.status }}
                        </NTag>
                      </div>

                      <div class="flex flex-wrap gap-2 text-xs text-slate-500">
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
            </section>

            <section class="runtime-panel min-h-0">
              <div class="runtime-panel-header">
                <div>
                  <div class="text-sm font-semibold text-slate-900">运行会话</div>
                  <div class="mt-1 text-xs text-slate-500">当前用户下的独立会话，支持置顶与历史复用</div>
                </div>
                <NButton type="primary" secondary :loading="creatingSession" @click="createSession">
                  <template #icon>
                    <icon-carbon:add />
                  </template>
                  新会话
                </NButton>
              </div>

              <NScrollbar class="runtime-scroll">
                <div class="space-y-2 p-3">
                  <NSpin :show="sessionsLoading">
                    <div v-if="sessions.length === 0" class="py-12">
                      <NEmpty description="当前对象暂无会话" />
                    </div>

                    <button
                      v-for="session in sessions"
                      :key="session.id"
                      class="runtime-card-button"
                      :class="conversationId === session.id ? 'runtime-card-button-active runtime-card-button-sky' : ''"
                      @click="loadMessages(session.id)"
                    >
                      <div class="mb-2 flex items-start justify-between gap-2">
                        <div class="min-w-0">
                          <div class="truncate text-sm font-medium text-slate-900">{{ session.title || '新会话' }}</div>
                          <div class="mt-1 truncate text-xs text-slate-500">{{ session.lastMessage || '暂无消息' }}</div>
                        </div>
                        <div class="text-[11px] text-slate-400">{{ formatSessionTime(session.time) }}</div>
                      </div>

                      <div class="flex items-center justify-between gap-2 text-xs text-slate-500">
                        <div class="flex items-center gap-2">
                          <NTag size="small" :type="session.isPinned ? 'warning' : 'default'">
                            {{ session.isPinned ? '置顶' : '普通' }}
                          </NTag>
                          <span>{{ session.messageCount }} 条消息</span>
                        </div>

                        <div class="flex items-center gap-2">
                          <button class="rounded-full px-2 py-1 text-slate-500 transition hover:bg-slate-100 hover:text-slate-900" @click.stop="togglePin(session)">
                            {{ pinningSessionIds.includes(session.id) ? '处理中' : (session.isPinned ? '取消置顶' : '置顶') }}
                          </button>
                          <button class="rounded-full px-2 py-1 text-rose-500 transition hover:bg-rose-50 hover:text-rose-600" @click.stop="removeSession(session.id)">
                            {{ deletingSessionIds.includes(session.id) ? '删除中' : '删除' }}
                          </button>
                        </div>
                      </div>
                    </button>
                  </NSpin>
                </div>
              </NScrollbar>
            </section>

            <section class="runtime-main min-w-0">
              <div class="runtime-main-header border-b border-slate-200 px-5 py-5">
                <div class="flex flex-wrap items-start justify-between gap-4">
                  <div class="min-w-0">
                    <div class="mb-2 flex items-center gap-2">
                      <NTag size="small" :bordered="false" type="success">{{ runtimeType === 'agent' ? 'Agent' : 'Workflow' }}</NTag>
                      <span class="text-xs font-medium uppercase tracking-[0.18em] text-slate-400">Live Run</span>
                    </div>
                    <div class="truncate text-xl font-semibold text-slate-900">
                      {{ activeTarget?.name || '请选择运行对象' }}
                    </div>
                    <div class="mt-2 max-w-2xl text-sm leading-6 text-slate-500">
                      {{ activeTarget?.description || '从左侧选择已发布的 Agent 或 Workflow，随后在这里直接运行。' }}
                    </div>
                  </div>

                  <div class="grid grid-cols-2 gap-3 rounded-2xl border border-slate-200 bg-slate-50/90 p-3 text-xs text-slate-600">
                    <div>
                      <div class="text-slate-400">发布时间</div>
                      <div class="mt-1 font-medium text-slate-900">{{ formatTime(activeTarget?.publishedAt) }}</div>
                    </div>
                    <div>
                      <div class="text-slate-400">最近更新</div>
                      <div class="mt-1 font-medium text-slate-900">{{ formatTime(activeTarget?.updatedAt) }}</div>
                    </div>
                    <div>
                      <div class="text-slate-400">累计运行</div>
                      <div class="mt-1 font-medium text-slate-900">{{ activeTarget?.callCount || 0 }}</div>
                    </div>
                    <div>
                      <div class="text-slate-400">成功率</div>
                      <div class="mt-1 font-medium text-slate-900">{{ activeTarget?.successRate || 100 }}%</div>
                    </div>
                  </div>
                </div>
              </div>

              <div class="flex h-[calc(100%-121px)] flex-col">
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
                          class="max-w-[85%] rounded-[24px] px-4 py-3 shadow-sm"
                          :class="message.role === 'user'
                            ? 'bg-slate-900 text-white'
                            : 'border border-slate-200 bg-white text-slate-800'"
                        >
                          <div class="mb-2 flex items-center gap-2 text-[11px] opacity-70">
                            <span>{{ message.role === 'user' ? '你' : (runtimeType === 'agent' ? 'Agent' : 'Workflow') }}</span>
                            <span>{{ formatTime(message.timestamp) }}</span>
                          </div>
                          <pre
                            class="whitespace-pre-wrap break-words text-sm leading-6 font-sans"
                          >{{ renderWorkflowContent(message.content || (message.status === 'pending' ? '运行中...' : '')) }}</pre>
                          <div v-if="message.status === 'error'" class="mt-2 text-xs text-rose-500">
                            {{ message.errorMessage || '运行失败' }}
                          </div>
                        </div>
                      </div>
                    </NSpin>
                  </div>
                </NScrollbar>

                <div class="border-t border-slate-200 bg-white/95 px-5 py-4">
                  <div v-if="lastExecution" class="mb-3 flex flex-wrap gap-2 text-xs text-slate-500">
                    <NTag size="small" type="info">执行ID {{ lastExecution.executionId || '-' }}</NTag>
                    <NTag size="small" :type="lastExecution.success === false ? 'error' : 'success'">
                      {{ lastExecution.success === false ? '失败' : '完成' }}
                    </NTag>
                    <NTag size="small" type="default">耗时 {{ lastExecution.durationMs || 0 }} ms</NTag>
                    <NTag size="small" type="warning">Tokens {{ lastExecution.tokens?.totalTokens || 0 }}</NTag>
                  </div>

                  <div class="rounded-[24px] border border-slate-200 bg-slate-50/90 p-3 shadow-[inset_0_1px_0_rgba(255,255,255,0.6)]">
                    <textarea
                      v-model="inputMessage"
                      class="runtime-textarea min-h-[88px] w-full resize-none"
                      :placeholder="activeTarget ? `向${runtimeType === 'agent' ? ' Agent' : ' Workflow'} 输入运行内容...` : '请先从左侧选择运行对象'"
                      @keydown.enter.exact.prevent="handleSend"
                    />

                    <div class="mt-3 flex flex-wrap items-center justify-between gap-3">
                      <div class="text-xs text-slate-500">
                        当前会话：{{ conversationId || '首次发送时自动创建' }}
                      </div>

                      <div class="flex items-center gap-3">
                        <span class="text-xs text-slate-400">Enter 发送</span>
                        <NButton type="primary" :disabled="canSend" :loading="running" @click="handleSend">
                          {{ running ? '运行中' : '发送运行请求' }}
                        </NButton>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </section>
          </div>
        </div>
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
  --n-text-color: #0f172a;
  --n-placeholder-color: rgba(100, 116, 139, 0.85);
}

.runtime-panel,
.runtime-main {
  display: flex;
  min-height: 0;
  flex-direction: column;
  overflow: hidden;
  border: 1px solid rgba(226, 232, 240, 0.9);
  border-radius: 24px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(248, 250, 252, 0.98));
  box-shadow: 0 12px 30px rgba(15, 23, 42, 0.05);
}

.runtime-hero {
  background:
    radial-gradient(circle at top left, rgba(16, 185, 129, 0.12), transparent 24%),
    radial-gradient(circle at top right, rgba(59, 130, 246, 0.1), transparent 28%),
    linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(248, 250, 252, 0.96));
}

.runtime-panel-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 16px 16px 12px;
}

.runtime-switcher {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: 1px solid rgba(226, 232, 240, 0.95);
  border-radius: 18px;
  background: rgba(248, 250, 252, 0.9);
  padding: 6px;
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.75),
    0 10px 24px rgba(15, 23, 42, 0.06);
}

.runtime-switcher-button {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  border: 1px solid transparent;
  border-radius: 14px;
  background: transparent;
  color: #64748b;
  padding: 9px 14px;
  font-size: 13px;
  font-weight: 600;
  transition:
    border-color 0.2s ease,
    background-color 0.2s ease,
    color 0.2s ease,
    box-shadow 0.2s ease;
}

.runtime-switcher-button:hover {
  color: #0f172a;
  background: rgba(255, 255, 255, 0.72);
}

.runtime-switcher-button-active {
  border-color: rgba(203, 213, 225, 0.95);
  background: #fff;
  color: #0f172a;
  box-shadow: 0 8px 18px rgba(15, 23, 42, 0.08);
}

.runtime-switcher-kicker {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border-radius: 999px;
  background: rgba(226, 232, 240, 0.9);
  color: #475569;
  font-size: 11px;
  font-weight: 700;
}

.runtime-switcher-button-active .runtime-switcher-kicker {
  background: #0f172a;
  color: #fff;
}

.runtime-scroll {
  height: calc(100% - 84px);
}

.runtime-card-button {
  width: 100%;
  border: 1px solid rgba(226, 232, 240, 0.95);
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.82);
  padding: 16px;
  text-align: left;
  transition:
    transform 0.2s ease,
    box-shadow 0.2s ease,
    border-color 0.2s ease,
    background-color 0.2s ease;
}

.runtime-card-button:hover {
  transform: translateY(-1px);
  border-color: rgba(148, 163, 184, 0.5);
  background: #fff;
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.08);
}

.runtime-card-button-active {
  background: #fff;
  box-shadow: 0 14px 28px rgba(15, 23, 42, 0.08);
}

.runtime-card-button-emerald {
  border-color: rgba(52, 211, 153, 0.45);
  box-shadow:
    0 0 0 1px rgba(16, 185, 129, 0.12),
    0 14px 30px rgba(16, 185, 129, 0.12);
}

.runtime-card-button-sky {
  border-color: rgba(56, 189, 248, 0.45);
  box-shadow:
    0 0 0 1px rgba(14, 165, 233, 0.1),
    0 14px 30px rgba(14, 165, 233, 0.1);
}

.runtime-textarea {
  border: none;
  background: transparent;
  color: #0f172a;
  font-size: 14px;
  line-height: 1.75;
  outline: none;
}

.runtime-textarea::placeholder {
  color: #94a3b8;
}
</style>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { fetchGetUsageStats } from '@/service/api/auth';
import { fetchGetConversationSessions } from '@/service/api/conversation';
import { fetchAgentStats, fetchAgents } from '@/service/api/agent';
import { fetchWorkflowStats, fetchWorkflows } from '@/service/api/workflow';
import { fetchSkillStats, fetchSkills } from '@/service/api/skills';
import { fetchGetKnowledgeBaseStats } from '@/service/api/knowledge-base';
import { useChatStore } from '@/store/modules/chat';
import { useAuthStore } from '@/store/modules/auth';

defineOptions({
  name: 'HomeDashboard'
});

type RecentTabKey = 'conversation' | 'agent' | 'workflow' | 'skill';

interface StatCardItem {
  key: string;
  label: string;
  value: string;
  change: string;
  color: string;
  tone: string;
  icon: string;
}

interface RecentFeedItem {
  id: string;
  name: string;
  desc: string;
  meta: string;
  time: string;
  icon: string;
  accent: string;
  route: string;
}

const router = useRouter();
const authStore = useAuthStore();
const chatStore = useChatStore();

const loading = ref(false);

const usageStats = ref<Api.User.UsageStats | null>(null);
const sessions = ref<Api.Chat.Session[]>([]);
const agentStats = ref<Api.AgentCenter.WorkflowStats | null>(null);
const workflowStats = ref<Api.AgentCenter.WorkflowStats | null>(null);
const skillStats = ref<Api.AgentCenter.SkillStats | null>(null);
const knowledgeBaseStats = ref<Api.KnowledgeBase.KnowledgeBaseStats | null>(null);
const recentAgents = ref<Api.AgentCenter.Workflow[]>([]);
const recentWorkflows = ref<Api.AgentCenter.Workflow[]>([]);
const recentSkills = ref<Api.AgentCenter.Skill[]>([]);

const chatInput = ref('');
const recentTab = ref<RecentTabKey>('conversation');

const username = computed(() => authStore.userInfo.username || 'admin');

const greeting = computed(() => {
  const hour = new Date().getHours();
  if (hour < 12) return '早上好';
  if (hour < 18) return '下午好';
  return '晚上好';
});

const quickActions = [
  {
    label: '创建智能体',
    desc: '配置专属 AI 助手',
    icon: 'agent',
    color: '#2563EB',
    tone: 'linear-gradient(135deg, #DBEAFE 0%, #EFF6FF 100%)',
    route: '/ai-center/agent-management'
  },
  {
    label: '创建工作流',
    desc: '编排自动化业务流程',
    icon: 'workflow',
    color: '#0F766E',
    tone: 'linear-gradient(135deg, #CCFBF1 0%, #F0FDFA 100%)',
    route: '/ai-center/workflow'
  },
  {
    label: '管理知识库',
    desc: '同步文档并构建检索能力',
    icon: 'knowledge',
    color: '#B45309',
    tone: 'linear-gradient(135deg, #FEF3C7 0%, #FFFBEB 100%)',
    route: '/ai-assistant/knowledge-base'
  },
  {
    label: '技能中心',
    desc: '沉淀可复用的技能模块',
    icon: 'skill',
    color: '#7C3AED',
    tone: 'linear-gradient(135deg, #EDE9FE 0%, #F5F3FF 100%)',
    route: '/ai-center/skills'
  }
];

const recentTabs = [
  { key: 'conversation', label: '最近会话' },
  { key: 'agent', label: '智能体' },
  { key: 'workflow', label: '工作流' },
  { key: 'skill', label: '技能' }
] as const;

function formatNumber(value?: number | null) {
  if (value === null || value === undefined) return '--';
  if (value >= 10000) return `${(value / 10000).toFixed(1)}w`;
  return value.toLocaleString();
}

function formatDuration(ms?: number | null) {
  if (!ms) return '0ms';
  if (ms >= 1000) return `${(ms / 1000).toFixed(1)}s`;
  return `${ms}ms`;
}

function formatStorage(size?: number | null) {
  if (!size) return '0 B';
  const units = ['B', 'KB', 'MB', 'GB', 'TB'];
  let next = size;
  let index = 0;
  while (next >= 1024 && index < units.length - 1) {
    next /= 1024;
    index += 1;
  }
  return `${next.toFixed(index === 0 ? 0 : 1)} ${units[index]}`;
}

function formatDateTime(value?: string) {
  if (!value) return '暂无记录';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  const month = `${date.getMonth() + 1}`.padStart(2, '0');
  const day = `${date.getDate()}`.padStart(2, '0');
  const hour = `${date.getHours()}`.padStart(2, '0');
  const minute = `${date.getMinutes()}`.padStart(2, '0');
  return `${month}-${day} ${hour}:${minute}`;
}

function calcSuccessRate(success: number, total: number) {
  if (!total) return '0% 成功率';
  return `${Math.round((success / total) * 100)}% 成功率`;
}

function navigateTo(path: string) {
  router.push(path);
}

function handleQuickQuestion(question: string) {
  chatInput.value = question;
}

function handleSend() {
  const message = chatInput.value.trim();
  if (!message) return;

  chatStore.input.message = message;
  chatInput.value = '';
  router.push('/ai-assistant/chat');
}

function safeDescription(text?: string | null, fallback = '暂无描述') {
  return text && text.trim() ? text.trim() : fallback;
}

const quickQuestions = computed(() => {
  const topKb = usageStats.value?.topKnowledgeBases?.[0]?.name;
  const topSession = sessions.value[0]?.title;

  const questions = [
    topKb ? `总结知识库「${topKb}」的核心内容` : '帮我梳理本周知识库更新重点',
    topSession ? `延续会话「${topSession}」继续分析` : '帮我分析本周对话使用情况',
    '生成一份适合管理层查看的工作汇报'
  ];

  return questions;
});

const heroTags = computed(() => {
  const stats = usageStats.value;
  return [
    {
      label: '今日对话',
      value: formatNumber(stats?.todayConversations)
    },
    {
      label: '我的知识库',
      value: formatNumber(stats?.knowledgeBaseCount)
    },
    {
      label: '文档存量',
      value: formatNumber(stats?.totalDocuments)
    },
    {
      label: '存储占用',
      value: formatStorage(stats?.totalStorage)
    }
  ];
});

const topKnowledgeBases = computed(() => usageStats.value?.topKnowledgeBases || []);
const featureUsage = computed(() => usageStats.value?.featureUsage || []);

const heroHighlights = computed(() => [
  {
    label: '活跃会话',
    value: formatNumber(sessions.value.length),
    desc: sessions.value[0]?.title || '暂无进行中的会话',
    icon: 'chat'
  },
  {
    label: '优先知识库',
    value: topKnowledgeBases.value[0]?.name || '未建立',
    desc: topKnowledgeBases.value[0] ? `${topKnowledgeBases.value[0].count} 份文档` : '先创建一个知识库',
    icon: 'knowledge'
  }
]);

const statCards = computed<StatCardItem[]>(() => [
  {
    key: 'agent',
    label: '智能体总数',
    value: formatNumber(agentStats.value?.agentCount),
    change: `${formatNumber(agentStats.value?.runCount)} 次累计运行`,
    color: '#2563EB',
    tone: 'linear-gradient(135deg, #DBEAFE 0%, #EFF6FF 100%)',
    icon: 'agent'
  },
  {
    key: 'workflow',
    label: '工作流总数',
    value: formatNumber(workflowStats.value?.workflowCount),
    change: `${formatNumber(workflowStats.value?.runCount)} 次累计执行`,
    color: '#0F766E',
    tone: 'linear-gradient(135deg, #CCFBF1 0%, #F0FDFA 100%)',
    icon: 'workflow'
  },
  {
    key: 'skill',
    label: '技能总数',
    value: formatNumber(skillStats.value?.total),
    change: `${formatNumber(skillStats.value?.published)} 个已发布`,
    color: '#7C3AED',
    tone: 'linear-gradient(135deg, #EDE9FE 0%, #F5F3FF 100%)',
    icon: 'skill'
  },
  {
    key: 'knowledge',
    label: '知识库数量',
    value: formatNumber(knowledgeBaseStats.value?.knowledgeBaseCount),
    change: `${formatNumber(knowledgeBaseStats.value?.documentCount)} 份文档`,
    color: '#B45309',
    tone: 'linear-gradient(135deg, #FEF3C7 0%, #FFFBEB 100%)',
    icon: 'knowledge'
  },
  {
    key: 'conversation',
    label: '今日问答数',
    value: formatNumber(usageStats.value?.todayConversations),
    change: `${formatNumber(usageStats.value?.totalConversations)} 次历史对话`,
    color: '#DC2626',
    tone: 'linear-gradient(135deg, #FEE2E2 0%, #FEF2F2 100%)',
    icon: 'chat'
  }
]);

const assistantMetrics = computed(() => [
  {
    label: '平均响应',
    value: formatDuration(agentStats.value?.avgDurationMs || workflowStats.value?.avgDurationMs || 0)
  },
  {
    label: '运行成功率',
    value: `${Math.max(agentStats.value?.successRate || 0, workflowStats.value?.successRate || 0)}%`
  },
  {
    label: '会话数量',
    value: formatNumber(sessions.value.length)
  }
]);

const recentFeeds = computed<Record<RecentTabKey, RecentFeedItem[]>>(() => ({
  conversation: sessions.value.slice(0, 5).map(item => ({
    id: item.id,
    name: item.title || '新会话',
    desc: item.lastMessage || '会话尚未产生消息',
    meta: `${item.messageCount || 0} 条消息`,
    time: formatDateTime(item.updatedAt || item.time),
    icon: 'chat',
    accent: '#2563EB',
    route: '/ai-assistant/chat'
  })),
  agent: recentAgents.value.slice(0, 5).map(item => ({
    id: item.agentId || item.workflowId || item.name,
    name: item.name,
    desc: safeDescription(item.description, '智能体描述待补充'),
    meta: `${formatNumber(item.callCount)} 次调用 · ${calcSuccessRate(item.successCount, item.callCount)}`,
    time: formatDateTime(item.updatedAt),
    icon: 'agent',
    accent: '#2563EB',
    route: '/ai-center/agent-management'
  })),
  workflow: recentWorkflows.value.slice(0, 5).map(item => ({
    id: item.workflowId,
    name: item.name,
    desc: safeDescription(item.description, '工作流描述待补充'),
    meta: `${formatNumber(item.callCount)} 次运行 · ${calcSuccessRate(item.successCount, item.callCount)}`,
    time: formatDateTime(item.updatedAt),
    icon: 'workflow',
    accent: '#0F766E',
    route: '/ai-center/workflow'
  })),
  skill: recentSkills.value.slice(0, 5).map(item => ({
    id: item.skillId,
    name: item.name,
    desc: safeDescription(item.description, '技能说明待补充'),
    meta: `${item.category || '未分类'} · ${formatNumber(item.callCount)} 次调用`,
    time: formatDateTime(item.updatedAt),
    icon: 'skill',
    accent: '#7C3AED',
    route: '/ai-center/skills'
  }))
}));

const currentRecentItems = computed(() => recentFeeds.value[recentTab.value] || []);

function getRecentTabRoute(tab: RecentTabKey) {
  const routeMap: Record<RecentTabKey, string> = {
    conversation: '/ai-assistant/chat',
    agent: '/ai-center/agent-management',
    workflow: '/ai-center/workflow',
    skill: '/ai-center/skills'
  };
  return routeMap[tab];
}

function extractData<T>(result: PromiseSettledResult<{ error: unknown; data: T | null | undefined }>) {
  if (result.status !== 'fulfilled' || result.value.error || !result.value.data) {
    return null;
  }

  return result.value.data;
}

async function loadHomeData() {
  loading.value = true;

  try {
    const [
      usageResult,
      sessionResult,
      agentStatsResult,
      workflowStatsResult,
      skillStatsResult,
      kbStatsResult,
      agentsResult,
      workflowsResult,
      skillsResult
    ] = await Promise.allSettled([
      fetchGetUsageStats(7),
      fetchGetConversationSessions(),
      fetchAgentStats(),
      fetchWorkflowStats(),
      fetchSkillStats(),
      fetchGetKnowledgeBaseStats(),
      fetchAgents({ size: 5 }),
      fetchWorkflows({ size: 5 }),
      fetchSkills({ size: 5 })
    ]);

    usageStats.value = extractData(usageResult);
    sessions.value = extractData(sessionResult) || [];
    agentStats.value = extractData(agentStatsResult);
    workflowStats.value = extractData(workflowStatsResult);
    skillStats.value = extractData(skillStatsResult);
    knowledgeBaseStats.value = extractData(kbStatsResult);

    recentAgents.value = extractData(agentsResult)?.records || [];
    recentWorkflows.value = extractData(workflowsResult)?.records || [];
    recentSkills.value = extractData(skillsResult)?.records || [];
  } finally {
    loading.value = false;
  }
}

onMounted(loadHomeData);
</script>

<template>
  <div class="home-page h-full overflow-auto">
    <NSpin :show="loading">
      <div class="home-shell mx-auto max-w-[1600px] min-w-0 w-full px-5 py-5">
        <section class="hero-panel relative overflow-hidden rounded-[28px] px-6 py-6 lg:px-8">
          <div class="hero-backdrop"></div>
          <div class="hero-grid relative z-1 grid items-stretch gap-5 xl:grid-cols-[minmax(0,1.28fr)_390px]">
            <div class="h-full min-w-0 flex flex-col">
              <div class="text-[11px] text-slate-500 font-600 tracking-[0.24em] uppercase">KnowFlow Workspace</div>

              <div class="grid mt-4 gap-4 lg:grid-cols-[minmax(0,1fr)_280px] lg:items-start">
                <div class="min-w-0">
                  <h1 class="text-3xl text-slate-900 font-700 leading-tight lg:text-4xl">
                    {{ greeting }}，{{ username }}
                  </h1>
                  <p class="mt-3 max-w-[680px] text-sm text-slate-600 leading-6 lg:text-[15px]">
                    从这里直接进入对话、接续最近工作、查看知识库状态和当前资产规模。上半区只保留高频动作和关键反馈，减少无效装饰和松散留白。
                  </p>
                </div>

                <div class="grid gap-3 lg:grid-cols-1 sm:grid-cols-2">
                  <div
                    v-for="item in heroHighlights"
                    :key="item.label"
                    class="hero-side-card border border-white/70 rounded-2xl bg-white/78 p-4 shadow-sm backdrop-blur"
                  >
                    <div class="flex items-start justify-between gap-3">
                      <div class="min-w-0">
                        <div class="text-[11px] text-slate-400 tracking-[0.2em] uppercase">{{ item.label }}</div>
                        <div class="mt-2 truncate text-base text-slate-900 font-700">{{ item.value }}</div>
                        <div class="mt-1 truncate text-xs text-slate-500">{{ item.desc }}</div>
                      </div>
                      <div class="hero-side-icon">
                        <icon-carbon:chat v-if="item.icon === 'chat'" class="text-lg" />
                        <icon-carbon:data-base v-else class="text-lg" />
                      </div>
                    </div>
                  </div>
                </div>
              </div>

              <div
                class="hero-input-panel mt-6 border border-white/70 rounded-[24px] bg-white/88 p-4 shadow-[0_16px_40px_rgba(15,23,42,0.08)] backdrop-blur"
              >
                <div class="grid gap-4 lg:grid-cols-[minmax(0,1fr)_220px] lg:items-center">
                  <div class="min-w-0">
                    <div class="mb-3 flex items-center gap-2 text-xs text-slate-500">
                      <icon-carbon:machine-learning-model class="text-base text-slate-700" />
                      智能助理入口
                    </div>
                    <div class="flex items-center gap-3 border border-slate-200 rounded-2xl bg-slate-50 px-3 py-3">
                      <input
                        v-model="chatInput"
                        type="text"
                        class="min-w-0 flex-1 border-none bg-transparent text-sm text-slate-800 outline-none placeholder:text-slate-400"
                        placeholder="直接输入问题，跳转到智能对话继续处理..."
                        @keyup.enter="handleSend"
                      />
                      <NButton type="primary" size="large" class="hero-send-btn px-5" @click="handleSend">
                        <template #icon>
                          <icon-carbon:send class="text-base" />
                        </template>
                        进入对话
                      </NButton>
                    </div>

                    <div class="mt-3 flex flex-wrap items-center gap-2">
                      <span class="text-xs text-slate-400">快捷提问</span>
                      <button
                        v-for="question in quickQuestions"
                        :key="question"
                        class="border border-slate-200 rounded-full bg-slate-50 px-3 py-1 text-xs text-slate-600 transition-all hover:border-blue-300 hover:bg-white hover:text-blue-600"
                        @click="handleQuickQuestion(question)"
                      >
                        {{ question }}
                      </button>
                    </div>
                  </div>

                  <div class="border border-slate-200 rounded-2xl bg-slate-50/90 p-4">
                    <div class="text-[11px] text-slate-400 tracking-[0.2em] uppercase">快速去向</div>
                    <div class="mt-3 space-y-2">
                      <button class="hero-jump-card" @click="navigateTo('/ai-assistant/chat')">
                        <span>继续对话</span>
                        <icon-carbon:arrow-right />
                      </button>
                      <button class="hero-jump-card" @click="navigateTo('/ai-assistant/knowledge-base')">
                        <span>查看知识库</span>
                        <icon-carbon:arrow-right />
                      </button>
                      <button class="hero-jump-card" @click="navigateTo('/ai-center/workflow')">
                        <span>进入工作流</span>
                        <icon-carbon:arrow-right />
                      </button>
                    </div>
                  </div>
                </div>
              </div>

              <div class="grid grid-cols-2 mt-5 gap-3 lg:grid-cols-4">
                <div
                  v-for="tag in heroTags"
                  :key="tag.label"
                  class="hero-stat-chip border border-white/65 rounded-2xl bg-white/70 px-4 py-3 shadow-sm backdrop-blur"
                >
                  <div class="text-xs text-slate-400 tracking-[0.18em] uppercase">{{ tag.label }}</div>
                  <div class="mt-2 text-xl text-slate-900 font-700">{{ tag.value }}</div>
                </div>
              </div>
            </div>

            <div
              class="assistant-panel relative h-full min-w-0 overflow-hidden border border-slate-200/70 rounded-[28px] bg-[#081225] p-5 text-slate-50 shadow-[0_24px_60px_rgba(15,23,42,0.28)]"
            >
              <div class="assistant-noise"></div>
              <div class="relative z-1 h-full flex flex-col">
                <div class="flex items-center justify-between">
                  <div>
                    <div class="text-[11px] text-cyan-300/80 tracking-[0.3em] uppercase">Assistant Console</div>
                    <div class="mt-1 text-lg font-700">首页智能助理</div>
                  </div>
                  <div
                    class="inline-flex items-center gap-2 border border-emerald-400/20 rounded-full bg-emerald-400/10 px-3 py-1 text-xs text-emerald-300"
                  >
                    <span class="assistant-ping h-2 w-2 rounded-full bg-emerald-400"></span>
                    ONLINE
                  </div>
                </div>

                <div class="assistant-core mt-4">
                  <div class="assistant-orb">
                    <div class="assistant-face">
                      <span class="eye"></span>
                      <span class="eye"></span>
                    </div>
                  </div>
                  <div class="assistant-scanline"></div>
                </div>

                <div class="grid grid-cols-3 mt-4 gap-3">
                  <div
                    v-for="metric in assistantMetrics"
                    :key="metric.label"
                    class="border border-white/10 rounded-2xl bg-white/6 px-3 py-3"
                  >
                    <div class="text-[11px] text-slate-400 tracking-[0.18em] uppercase">{{ metric.label }}</div>
                    <div class="mt-2 text-lg text-white font-700">{{ metric.value }}</div>
                  </div>
                </div>

                <div class="mt-4 border border-cyan-400/12 rounded-2xl bg-white/6 p-4">
                  <div class="flex items-center justify-between">
                    <div>
                      <div class="text-xs text-slate-400">工作流信号</div>
                      <div class="mt-1 text-sm text-white font-600">对话、知识库、自动化能力均已就绪</div>
                    </div>
                    <icon-carbon:chart-line-data class="text-2xl text-cyan-300" />
                  </div>
                  <div class="mt-4 space-y-3">
                    <div class="signal-row">
                      <span>会话活跃度</span>
                      <span>{{ formatNumber(usageStats?.weekActiveDays) }} / 7 天</span>
                    </div>
                    <div class="signal-bar">
                      <div class="signal-fill w-[78%]"></div>
                    </div>
                    <div class="signal-row">
                      <span>知识库文档</span>
                      <span>{{ formatNumber(knowledgeBaseStats?.documentCount) }}</span>
                    </div>
                    <div class="signal-bar">
                      <div class="signal-fill alt w-[66%]"></div>
                    </div>
                  </div>
                </div>

                <div class="mt-auto flex gap-2 pt-4">
                  <button class="assistant-action" @click="navigateTo('/ai-assistant/chat')">
                    <icon-carbon:chat class="text-sm" />
                    打开对话
                  </button>
                  <button class="assistant-action muted" @click="navigateTo('/ai-assistant/knowledge-base')">
                    <icon-carbon:data-base class="text-sm" />
                    查看知识库
                  </button>
                </div>
              </div>
            </div>
          </div>
        </section>

        <section class="grid mt-4 gap-3 md:grid-cols-2 xl:grid-cols-5">
          <div
            v-for="card in statCards"
            :key="card.key"
            class="stat-card border border-slate-200/70 rounded-2xl bg-white p-4 shadow-[0_10px_30px_rgba(15,23,42,0.05)] transition-all hover:shadow-[0_16px_40px_rgba(15,23,42,0.08)] hover:-translate-y-[2px]"
          >
            <div class="flex items-start justify-between gap-3">
              <div class="min-w-0">
                <div class="text-xs text-slate-400 tracking-[0.2em] uppercase">{{ card.label }}</div>
                <div class="mt-2 text-3xl text-slate-900 font-700">{{ card.value }}</div>
              </div>
              <div
                class="h-12 w-12 flex flex-shrink-0 items-center justify-center rounded-2xl"
                :style="{ background: card.tone, color: card.color }"
              >
                <icon-carbon:machine-learning-model v-if="card.icon === 'agent'" class="text-xl" />
                <icon-carbon:workflow-automation v-else-if="card.icon === 'workflow'" class="text-xl" />
                <icon-carbon:skill-level-advanced v-else-if="card.icon === 'skill'" class="text-xl" />
                <icon-carbon:data-base v-else-if="card.icon === 'knowledge'" class="text-xl" />
                <icon-carbon:chat v-else class="text-xl" />
              </div>
            </div>
            <div class="mt-4 text-sm text-slate-500">{{ card.change }}</div>
          </div>
        </section>

        <section
          class="mt-4 border border-slate-200/70 rounded-[24px] bg-white p-5 shadow-[0_10px_30px_rgba(15,23,42,0.05)]"
        >
          <div class="flex items-center justify-between gap-4">
            <div>
              <div class="text-sm text-slate-900 font-700">快速入口</div>
              <div class="mt-1 text-sm text-slate-500">保留常用任务，但每张卡片都与真实工作台能力对应</div>
            </div>
          </div>

          <div class="grid mt-4 gap-3 md:grid-cols-2 xl:grid-cols-4">
            <button
              v-for="action in quickActions"
              :key="action.label"
              class="quick-action-card group border border-slate-200/70 rounded-2xl bg-slate-50 px-4 py-4 text-left transition-all hover:border-slate-300 hover:bg-white hover:-translate-y-[2px]"
              @click="navigateTo(action.route)"
            >
              <div class="flex items-start gap-3">
                <div
                  class="h-11 w-11 flex flex-shrink-0 items-center justify-center rounded-2xl"
                  :style="{ background: action.tone, color: action.color }"
                >
                  <icon-carbon:machine-learning-model v-if="action.icon === 'agent'" class="text-xl" />
                  <icon-carbon:workflow-automation v-else-if="action.icon === 'workflow'" class="text-xl" />
                  <icon-carbon:data-base v-else-if="action.icon === 'knowledge'" class="text-xl" />
                  <icon-carbon:skill-level-advanced v-else class="text-xl" />
                </div>
                <div class="min-w-0 flex-1">
                  <div class="text-sm text-slate-900 font-700">{{ action.label }}</div>
                  <div class="mt-1 text-sm text-slate-500 leading-6">{{ action.desc }}</div>
                </div>
                <icon-carbon:arrow-up-right
                  class="mt-1 text-lg text-slate-300 transition-colors group-hover:text-slate-600"
                />
              </div>
            </button>
          </div>
        </section>

        <section class="grid mt-4 gap-4 xl:grid-cols-[minmax(0,1fr)_340px]">
          <div
            class="min-w-0 border border-slate-200/70 rounded-[24px] bg-white shadow-[0_10px_30px_rgba(15,23,42,0.05)]"
          >
            <div class="flex items-center justify-between px-5 pb-3 pt-5">
              <div>
                <div class="text-sm text-slate-900 font-700">最近工作台动态</div>
                <div class="mt-1 text-sm text-slate-500">全部来自后端真实记录，不再使用本地静态示例</div>
              </div>
              <button
                class="text-sm text-blue-600 transition-colors hover:text-blue-700"
                @click="navigateTo(getRecentTabRoute(recentTab))"
              >
                查看全部
              </button>
            </div>

            <div class="flex gap-2 border-b border-slate-100 px-5 pb-3">
              <button
                v-for="tab in recentTabs"
                :key="tab.key"
                class="rounded-full px-3 py-1.5 text-sm transition-all"
                :class="
                  recentTab === tab.key
                    ? 'bg-slate-900 text-white'
                    : 'bg-slate-100 text-slate-500 hover:bg-slate-200 hover:text-slate-700'
                "
                @click="recentTab = tab.key"
              >
                {{ tab.label }}
              </button>
            </div>

            <div v-if="currentRecentItems.length" class="divide-y divide-slate-100">
              <button
                v-for="item in currentRecentItems"
                :key="item.id"
                class="recent-item w-full flex items-start gap-3 px-5 py-4 text-left transition-colors hover:bg-slate-50"
                @click="navigateTo(item.route)"
              >
                <div
                  class="mt-1 h-10 w-10 flex flex-shrink-0 items-center justify-center rounded-2xl text-white"
                  :style="{ background: item.accent }"
                >
                  <icon-carbon:chat v-if="item.icon === 'chat'" class="text-lg" />
                  <icon-carbon:machine-learning-model v-else-if="item.icon === 'agent'" class="text-lg" />
                  <icon-carbon:workflow-automation v-else-if="item.icon === 'workflow'" class="text-lg" />
                  <icon-carbon:skill-level-advanced v-else class="text-lg" />
                </div>
                <div class="min-w-0 flex-1">
                  <div class="flex items-start justify-between gap-3">
                    <div class="truncate text-sm text-slate-900 font-700">{{ item.name }}</div>
                    <div class="flex-shrink-0 text-xs text-slate-400">{{ item.time }}</div>
                  </div>
                  <div class="mt-1 truncate text-sm text-slate-500">{{ item.desc }}</div>
                  <div class="mt-2 text-xs text-slate-400">{{ item.meta }}</div>
                </div>
              </button>
            </div>
            <div v-else class="px-5 py-10 text-center text-sm text-slate-400">暂无可展示的后端数据</div>
          </div>

          <div class="space-y-4">
            <div
              class="border border-slate-200/70 rounded-[24px] bg-white p-5 shadow-[0_10px_30px_rgba(15,23,42,0.05)]"
            >
              <div class="flex items-center justify-between">
                <div>
                  <div class="text-sm text-slate-900 font-700">知识库焦点</div>
                  <div class="mt-1 text-sm text-slate-500">按当前账户真实使用情况统计</div>
                </div>
                <icon-carbon:data-base class="text-xl text-amber-500" />
              </div>

              <div v-if="topKnowledgeBases.length" class="mt-4 space-y-3">
                <div v-for="item in topKnowledgeBases" :key="item.kbId" class="rounded-2xl bg-slate-50 p-3">
                  <div class="flex items-center justify-between gap-3">
                    <div class="min-w-0 truncate text-sm text-slate-800 font-600">{{ item.name }}</div>
                    <div class="text-xs text-slate-400">{{ item.count }} 份文档</div>
                  </div>
                  <div class="mt-3 h-2 overflow-hidden rounded-full bg-slate-200">
                    <div
                      class="h-full rounded-full from-amber-400 to-orange-500 bg-gradient-to-r"
                      :style="{ width: `${Math.min(100, item.count * 18 || 12)}%` }"
                    ></div>
                  </div>
                </div>
              </div>
              <div v-else class="mt-4 rounded-2xl bg-slate-50 px-4 py-6 text-sm text-slate-400">
                当前账户还没有知识库使用记录
              </div>
            </div>

            <div
              class="border border-slate-200/70 rounded-[24px] bg-white p-5 shadow-[0_10px_30px_rgba(15,23,42,0.05)]"
            >
              <div class="flex items-center justify-between">
                <div>
                  <div class="text-sm text-slate-900 font-700">功能占比</div>
                  <div class="mt-1 text-sm text-slate-500">来自个人使用统计接口</div>
                </div>
                <icon-carbon:chart-pie class="text-xl text-blue-500" />
              </div>

              <div v-if="featureUsage.length" class="mt-4 space-y-3">
                <div v-for="item in featureUsage" :key="item.label" class="space-y-2">
                  <div class="flex items-center justify-between text-sm">
                    <span class="text-slate-700">{{ item.label }}</span>
                    <span class="text-slate-400">{{ item.count }} 次</span>
                  </div>
                  <div class="h-2 overflow-hidden rounded-full bg-slate-200">
                    <div class="h-full rounded-full" :style="{ width: `${item.value}%`, background: item.color }"></div>
                  </div>
                </div>
              </div>
              <div v-else class="mt-4 rounded-2xl bg-slate-50 px-4 py-6 text-sm text-slate-400">暂无功能使用数据</div>
            </div>
          </div>
        </section>
      </div>
    </NSpin>
  </div>
</template>

<style scoped lang="scss">
.home-page {
  background:
    radial-gradient(circle at top left, rgba(59, 130, 246, 0.08), transparent 28%),
    linear-gradient(180deg, #f7f9fc 0%, #f3f5f9 100%);
}

.hero-panel {
  background:
    radial-gradient(circle at 0% 0%, rgba(14, 165, 233, 0.18), transparent 26%),
    radial-gradient(circle at 100% 0%, rgba(99, 102, 241, 0.18), transparent 28%),
    linear-gradient(135deg, #f8fbff 0%, #f4f7fb 44%, #edf3ff 100%);
  border: 1px solid rgba(226, 232, 240, 0.75);
  box-shadow: 0 28px 70px rgba(15, 23, 42, 0.08);
}

.hero-backdrop {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(148, 163, 184, 0.06) 1px, transparent 1px),
    linear-gradient(90deg, rgba(148, 163, 184, 0.06) 1px, transparent 1px);
  background-size: 26px 26px;
  mask-image: linear-gradient(180deg, rgba(255, 255, 255, 0.8), transparent);
  pointer-events: none;
}

.assistant-panel {
  background:
    radial-gradient(circle at 50% 0%, rgba(34, 211, 238, 0.14), transparent 28%),
    radial-gradient(circle at 100% 100%, rgba(59, 130, 246, 0.16), transparent 30%),
    linear-gradient(180deg, #081225 0%, #0b1730 55%, #0d1c38 100%);
}

.assistant-noise {
  position: absolute;
  inset: 0;
  opacity: 0.12;
  background-image:
    linear-gradient(transparent 0, rgba(255, 255, 255, 0.12) 50%, transparent 100%),
    linear-gradient(90deg, rgba(255, 255, 255, 0.04) 1px, transparent 1px);
  background-size:
    100% 7px,
    18px 18px;
  pointer-events: none;
}

.hero-side-card,
.hero-stat-chip {
  transition:
    transform 0.2s ease,
    box-shadow 0.2s ease,
    border-color 0.2s ease;
}

.hero-side-card:hover,
.hero-stat-chip:hover {
  transform: translateY(-2px);
  box-shadow: 0 16px 36px rgba(15, 23, 42, 0.08);
  border-color: rgba(148, 163, 184, 0.35);
}

.hero-side-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  flex-shrink: 0;
  border-radius: 14px;
  background: linear-gradient(135deg, #e2e8f0 0%, #f8fafc 100%);
  color: #0f172a;
}

.hero-jump-card {
  display: flex;
  width: 100%;
  align-items: center;
  justify-content: space-between;
  border-radius: 14px;
  border: 1px solid #e2e8f0;
  background: rgba(255, 255, 255, 0.75);
  padding: 10px 12px;
  font-size: 13px;
  color: #334155;
  transition:
    border-color 0.2s ease,
    background 0.2s ease,
    color 0.2s ease;
}

.hero-jump-card:hover {
  border-color: #93c5fd;
  background: #fff;
  color: #1d4ed8;
}

.assistant-core {
  position: relative;
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 188px;
  border-radius: 26px;
  overflow: hidden;
  border: 1px solid rgba(148, 163, 184, 0.12);
  background: linear-gradient(180deg, rgba(15, 23, 42, 0.8) 0%, rgba(15, 23, 42, 0.28) 100%);
}

.assistant-orb {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 136px;
  height: 136px;
  border-radius: 999px;
  background: radial-gradient(
    circle at 50% 35%,
    rgba(34, 211, 238, 0.95),
    rgba(29, 78, 216, 0.92) 48%,
    rgba(15, 23, 42, 0.6) 85%
  );
  box-shadow:
    0 0 0 18px rgba(14, 165, 233, 0.06),
    0 0 40px rgba(34, 211, 238, 0.26);
}

.assistant-orb::before,
.assistant-orb::after {
  content: '';
  position: absolute;
  inset: -14px;
  border-radius: inherit;
  border: 1px solid rgba(103, 232, 249, 0.22);
}

.assistant-orb::after {
  inset: -28px;
  border-color: rgba(103, 232, 249, 0.1);
}

.assistant-face {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 14px;
  width: 82px;
  height: 82px;
  border-radius: 28px;
  background: rgba(8, 18, 37, 0.65);
  box-shadow: inset 0 0 20px rgba(15, 23, 42, 0.65);
}

.eye {
  width: 13px;
  height: 13px;
  border-radius: 999px;
  background: #f8fafc;
  box-shadow: 0 0 14px rgba(255, 255, 255, 0.75);
}

.assistant-scanline {
  position: absolute;
  left: 18px;
  right: 18px;
  top: 50%;
  height: 1px;
  background: linear-gradient(90deg, transparent, rgba(103, 232, 249, 0.8), transparent);
  box-shadow: 0 0 18px rgba(103, 232, 249, 0.5);
}

.assistant-ping {
  animation: pulse-dot 1.8s ease-in-out infinite;
}

.signal-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 12px;
  color: #cbd5e1;
}

.signal-bar {
  overflow: hidden;
  height: 7px;
  border-radius: 999px;
  background: rgba(148, 163, 184, 0.14);
}

.signal-fill {
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, #06b6d4, #60a5fa);
}

.signal-fill.alt {
  background: linear-gradient(90deg, #34d399, #22c55e);
}

.assistant-action {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border-radius: 999px;
  border: 1px solid rgba(34, 211, 238, 0.18);
  background: rgba(8, 145, 178, 0.18);
  padding: 10px 14px;
  font-size: 12px;
  color: #ecfeff;
  transition: all 0.2s ease;
}

.assistant-action:hover {
  transform: translateY(-1px);
  background: rgba(8, 145, 178, 0.28);
}

.assistant-action.muted {
  border-color: rgba(148, 163, 184, 0.16);
  background: rgba(15, 23, 42, 0.28);
  color: #cbd5e1;
}

.hero-send-btn:deep(.n-button__content) {
  font-weight: 600;
}

@keyframes pulse-dot {
  0%,
  100% {
    transform: scale(1);
    opacity: 1;
  }
  50% {
    transform: scale(1.4);
    opacity: 0.65;
  }
}

@media (prefers-reduced-motion: reduce) {
  .assistant-ping,
  .stat-card,
  .quick-action-card,
  .recent-item,
  .assistant-action {
    animation: none !important;
    transition: none !important;
  }
}
</style>

<script setup lang="ts">
import { ref, computed } from 'vue';
import { useRouter } from 'vue-router';
import { useAuthStore } from '@/store/modules/auth';

defineOptions({
  name: 'Home'
});

const router = useRouter();
const authStore = useAuthStore();

const username = computed(() => authStore.userInfo.username || 'admin');

const greeting = computed(() => {
  const hour = new Date().getHours();
  if (hour < 12) return '早上好';
  if (hour < 18) return '下午好';
  return '晚上好';
});

const chatInput = ref('');

const quickQuestions = [
  '如何创建一个智能体？',
  '帮我分析本月销售数据',
  '生成一份项目报告'
];

function handleQuickQuestion(q: string) {
  chatInput.value = q;
}

function handleSend() {
  if (!chatInput.value.trim()) return;
  router.push('/chat');
}

const stats = [
  { label: '智能体', value: 156, change: '+12 本周新增', icon: 'agent', color: '#7C6FD4', bg: '#EDE9FF' },
  { label: '工作流', value: 89, change: '+8 本周新增', icon: 'workflow', color: '#3B82F6', bg: '#DBEAFE' },
  { label: 'MCP 工具', value: 34, change: '+5 本周新增', icon: 'mcp', color: '#10B981', bg: '#D1FAE5' },
  { label: '技能', value: 247, change: '+23 本周新增', icon: 'skill', color: '#F59E0B', bg: '#FEF3C7' },
  { label: '对话次数', value: '2,456', change: '+18% 较上周', icon: 'chat', color: '#8B5CF6', bg: '#EDE9FE' }
];

const quickActions = [
  { label: '创建智能体', desc: '创建一个专属的 AI 助手', icon: 'agent', color: '#7C6FD4', bg: '#EDE9FF', route: '/ai-center/agent-management' },
  { label: '创建工作流', desc: '设计自动化业务流程', icon: 'workflow', color: '#3B82F6', bg: '#DBEAFE', route: '/ai-center/workflow' },
  { label: '添加 MCP 工具', desc: '接入外部服务与能力', icon: 'mcp', color: '#10B981', bg: '#D1FAE5', route: '/ai-center/mcp-tools' },
  { label: '创建技能', desc: '扩展智能体的专业能力', icon: 'skill', color: '#F59E0B', bg: '#FEF3C7', route: '/ai-center/skills' }
];

const recentTab = ref('agent');
const recentTabs = [
  { key: 'agent', label: '智能体' },
  { key: 'workflow', label: '工作流' },
  { key: 'mcp', label: 'MCP 工具' },
  { key: 'skill', label: '技能' }
];

const recentItems: Record<string, { name: string; desc: string; uses: string; time: string; icon: string; bg: string }[]> = {
  agent: [
    { name: '合同审查助手', desc: '基于企业合同模板与法律知识，自动审查合同条款，识别风险点并给出修改建议。', uses: '1.2k', time: '今天 14:30', icon: 'contract', bg: '#DBEAFE' },
    { name: '财务报表分析师', desc: '分析企业财务报表，生成关键指标解读与趋势分析，辅助经营决策。', uses: '1.5k', time: '今天 10:15', icon: 'finance', bg: '#D1FAE5' },
    { name: '客户服务助手', desc: '7x24 小时客户问答，自动识别问题类型并提供解决方案。', uses: '856', time: '昨天 16:45', icon: 'service', bg: '#FEF3C7' },
    { name: '市场调研分析师', desc: '收集市场信息，分析行业趋势，生成调研报告与洞察建议。', uses: '789', time: '昨天 09:30', icon: 'market', bg: '#DBEAFE' }
  ],
  workflow: [
    { name: '数据处理流水线', desc: '自动化数据采集、清洗、分析全流程。', uses: '432', time: '今天 09:10', icon: 'data', bg: '#DBEAFE' },
    { name: '邮件自动回复', desc: '智能分类邮件并自动生成回复草稿。', uses: '321', time: '昨天 15:20', icon: 'email', bg: '#D1FAE5' }
  ],
  mcp: [
    { name: '数据库查询工具', desc: '连接企业数据库，执行自然语言查询。', uses: '654', time: '今天 11:30', icon: 'db', bg: '#EDE9FE' },
    { name: 'API 集成工具', desc: '快速接入第三方 API 服务。', uses: '234', time: '昨天 14:00', icon: 'api', bg: '#DBEAFE' }
  ],
  skill: [
    { name: '代码审查技能', desc: '自动审查代码质量并给出改进建议。', uses: '987', time: '今天 08:45', icon: 'code', bg: '#D1FAE5' },
    { name: '文档生成技能', desc: '根据代码或需求自动生成技术文档。', uses: '543', time: '昨天 17:30', icon: 'doc', bg: '#FEF3C7' }
  ]
};

const hotAgents = [
  { rank: 1, name: '合同审查助手', uses: '1.2k 次使用', rating: 4.8, color: '#EF4444' },
  { rank: 2, name: '财务报表分析师', uses: '1.5k 次使用', rating: 4.7, color: '#F59E0B' },
  { rank: 3, name: '招标文件分析师', uses: '856 次使用', rating: 4.6, color: '#10B981' },
  { rank: 4, name: 'IT 运维助手', uses: '945 次使用', rating: 4.7, color: '#6B7280' },
  { rank: 5, name: 'HR 助手', uses: '632 次使用', rating: 4.6, color: '#6B7280' }
];

const announcements = [
  { title: 'KnowFlow 2.1 版本发布', desc: '新增工作流条件分支功能，优化了智能体对话体验。', date: '2024-05-15', type: 'success' },
  { title: 'MCP 工具市场更新', desc: '新增 12 个实用工具，覆盖数据分析、文档处理等领域。', date: '2024-05-12', type: 'info' },
  { title: '系统维护通知', desc: '系统将于 5 月 20 日 02:00-04:00 进行维护升级。', date: '2024-05-10', type: 'warning' }
];

function navigateTo(path: string) {
  router.push(path);
}

function getAnnouncementDot(type: string) {
  if (type === 'success') return '#10B981';
  if (type === 'warning') return '#F59E0B';
  return '#3B82F6';
}
</script>

<template>
  <div class="home-page w-full bg-[#F5F6FA]">
    <div class="p-5 space-y-4" style="max-width: 1600px; margin: 0 auto; min-width: 0;">

      <!-- ── Hero Banner ── -->
      <div class="hero-banner relative overflow-hidden rounded-2xl px-8 py-5" style="min-height: 168px;">

        <!-- 背景光晕 -->
        <div class="absolute inset-0 pointer-events-none overflow-hidden">
          <div class="absolute right-64 top-6 w-56 h-56 rounded-full bg-indigo-200/25 blur-3xl"></div>
          <div class="absolute right-20 bottom-0 w-40 h-40 rounded-full bg-blue-200/30 blur-2xl"></div>
        </div>

        <!-- ── 右侧插图区 ── -->
        <div class="illustration-area absolute right-6 top-0 bottom-0 w-[320px] pointer-events-none flex items-center justify-end">

          <!-- 浮动卡片 1 — 工作流节点图 -->
          <div class="card-1 absolute right-40 top-5 w-48 rounded-2xl bg-white/80 shadow-lg p-3 backdrop-blur-sm border border-white/60">
            <div class="flex items-center gap-2 mb-2.5">
              <div class="w-2 h-2 rounded-full bg-red-300"></div>
              <div class="w-2 h-2 rounded-full bg-yellow-300"></div>
              <div class="w-2 h-2 rounded-full bg-green-300"></div>
            </div>
            <div class="flex items-center gap-1.5 mb-1.5">
              <div class="node-pill bg-indigo-100 text-indigo-500">开始</div>
              <div class="arrow-line"></div>
              <div class="node-pill bg-blue-500 text-white font-medium">处理</div>
              <div class="arrow-line"></div>
              <div class="node-pill bg-indigo-100 text-indigo-500">结束</div>
            </div>
            <div class="flex items-center gap-1.5">
              <div class="node-pill bg-purple-100 text-purple-500">输入</div>
              <div class="arrow-line"></div>
              <div class="node-diamond"></div>
              <div class="arrow-line"></div>
              <div class="node-pill bg-purple-100 text-purple-500">输出</div>
              <div class="arrow-line"></div>
              <div class="node-pill bg-indigo-100 text-indigo-400">完成</div>
            </div>
          </div>

          <!-- 浮动卡片 2 — Agent 对话卡片 -->
          <div class="card-2 absolute right-36 bottom-6 w-40 rounded-2xl bg-white/85 shadow-md p-2.5 backdrop-blur-sm border border-white/60">
            <div class="flex items-center gap-2 mb-2">
              <div class="w-5 h-5 rounded-full bg-indigo-500 flex items-center justify-center text-white text-xs font-bold">AI</div>
              <div class="text-xs text-gray-500 font-medium">智能助手</div>
            </div>
            <div class="space-y-1.5">
              <div class="h-1.5 bg-indigo-100 rounded-full w-full"></div>
              <div class="h-1.5 bg-indigo-100 rounded-full w-4/5"></div>
              <div class="h-1.5 bg-blue-100 rounded-full w-3/5"></div>
            </div>
          </div>

          <!-- 浮动卡片 3 — 顶部小卡片 -->
          <div class="card-3 absolute right-6 top-4 w-32 rounded-xl bg-white/75 shadow-sm p-2 backdrop-blur-sm border border-white/60">
            <div class="flex items-center gap-1.5 mb-1.5">
              <div class="w-4 h-4 rounded-md bg-indigo-500 flex items-center justify-center">
                <svg class="w-2.5 h-2.5 text-white" viewBox="0 0 24 24" fill="currentColor"><path d="M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41L9 16.17z"/></svg>
              </div>
              <span class="text-xs text-gray-600 font-medium">任务完成</span>
            </div>
            <div class="h-1.5 bg-indigo-200 rounded-full w-full overflow-hidden">
              <div class="h-full bg-indigo-500 rounded-full w-4/5"></div>
            </div>
          </div>

          <!-- 机器人主体 SVG -->
          <div class="robot-wrap relative z-10 mr-1 flex-shrink-0">
            <div class="absolute inset-0 rounded-full bg-indigo-400/15 blur-2xl scale-150 pointer-events-none"></div>
            <svg width="110" height="145" viewBox="0 0 130 170" fill="none" xmlns="http://www.w3.org/2000/svg">
              <ellipse cx="65" cy="162" rx="36" ry="7" fill="#C7D2FE" opacity="0.5"/>
              <rect x="40" y="134" width="18" height="22" rx="9" fill="url(#legGrad)"/>
              <rect x="72" y="134" width="18" height="22" rx="9" fill="url(#legGrad)"/>
              <rect x="36" y="150" width="26" height="12" rx="6" fill="#E0E7FF"/>
              <rect x="68" y="150" width="26" height="12" rx="6" fill="#E0E7FF"/>
              <rect x="26" y="82" width="78" height="60" rx="22" fill="url(#bodyGrad)"/>
              <circle cx="65" cy="110" r="14" fill="#EEF2FF" stroke="#C7D2FE" stroke-width="1.5"/>
              <circle cx="65" cy="110" r="8" fill="url(#coreGrad)"/>
              <circle cx="65" cy="110" r="4" fill="white" opacity="0.8"/>
              <circle cx="42" cy="100" r="4" fill="#A5B4FC"/>
              <circle cx="42" cy="100" r="2" fill="white" opacity="0.6"/>
              <circle cx="88" cy="100" r="4" fill="#A5B4FC"/>
              <circle cx="88" cy="100" r="2" fill="white" opacity="0.6"/>
              <rect x="3" y="90" width="20" height="44" rx="10" fill="url(#armGrad)" transform="rotate(-8 3 90)"/>
              <circle cx="10" cy="138" r="7" fill="#E0E7FF"/>
              <rect x="107" y="90" width="20" height="44" rx="10" fill="url(#armGrad)" transform="rotate(8 107 90)"/>
              <circle cx="120" cy="138" r="7" fill="#E0E7FF"/>
              <rect x="53" y="72" width="24" height="14" rx="6" fill="#C7D2FE"/>
              <rect x="18" y="20" width="94" height="58" rx="26" fill="url(#headGrad)"/>
              <ellipse cx="65" cy="30" rx="30" ry="10" fill="white" opacity="0.35"/>
              <rect x="62" y="6" width="6" height="16" rx="3" fill="#C7D2FE"/>
              <circle cx="65" cy="5" r="6" fill="url(#antennaGrad)"/>
              <circle cx="65" cy="5" r="3" fill="white" opacity="0.8"/>
              <circle cx="46" cy="50" r="14" fill="#4F46E5"/>
              <circle cx="46" cy="50" r="10" fill="#3730A3"/>
              <circle cx="46" cy="50" r="7" fill="url(#eyeGrad)"/>
              <circle cx="46" cy="50" r="3.5" fill="white" opacity="0.9"/>
              <circle cx="49" cy="47" r="1.8" fill="white" opacity="0.7"/>
              <circle cx="84" cy="50" r="14" fill="#4F46E5"/>
              <circle cx="84" cy="50" r="10" fill="#3730A3"/>
              <circle cx="84" cy="50" r="7" fill="url(#eyeGrad)"/>
              <circle cx="84" cy="50" r="3.5" fill="white" opacity="0.9"/>
              <circle cx="87" cy="47" r="1.8" fill="white" opacity="0.7"/>
              <path d="M52 66 Q65 74 78 66" stroke="#A5B4FC" stroke-width="2.5" stroke-linecap="round" fill="none"/>
              <defs>
                <linearGradient id="headGrad" x1="18" y1="20" x2="112" y2="78" gradientUnits="userSpaceOnUse">
                  <stop offset="0%" stop-color="#FFFFFF"/>
                  <stop offset="100%" stop-color="#EEF2FF"/>
                </linearGradient>
                <linearGradient id="bodyGrad" x1="26" y1="82" x2="104" y2="142" gradientUnits="userSpaceOnUse">
                  <stop offset="0%" stop-color="#FFFFFF"/>
                  <stop offset="100%" stop-color="#E0E7FF"/>
                </linearGradient>
                <linearGradient id="legGrad" x1="0" y1="0" x2="0" y2="1" gradientUnits="objectBoundingBox">
                  <stop offset="0%" stop-color="#E0E7FF"/>
                  <stop offset="100%" stop-color="#C7D2FE"/>
                </linearGradient>
                <linearGradient id="armGrad" x1="0" y1="0" x2="1" y2="1" gradientUnits="objectBoundingBox">
                  <stop offset="0%" stop-color="#FFFFFF"/>
                  <stop offset="100%" stop-color="#DDD6FE"/>
                </linearGradient>
                <radialGradient id="eyeGrad" cx="50%" cy="50%" r="50%">
                  <stop offset="0%" stop-color="#60A5FA"/>
                  <stop offset="100%" stop-color="#3B82F6"/>
                </radialGradient>
                <radialGradient id="coreGrad" cx="50%" cy="50%" r="50%">
                  <stop offset="0%" stop-color="#818CF8"/>
                  <stop offset="100%" stop-color="#4F46E5"/>
                </radialGradient>
                <linearGradient id="antennaGrad" x1="59" y1="0" x2="71" y2="10" gradientUnits="userSpaceOnUse">
                  <stop offset="0%" stop-color="#818CF8"/>
                  <stop offset="100%" stop-color="#4F46E5"/>
                </linearGradient>
              </defs>
            </svg>
          </div>

        </div>

        <!-- ── 左侧文字区 ── -->
        <div class="relative z-10" style="max-width: 520px;">
          <h1 class="text-2xl font-bold text-gray-800 mb-1">
            {{ greeting }}，{{ username }} 👋
          </h1>
          <p class="text-gray-500 mb-4 text-sm">欢迎使用 KnowFlow，探索智能协作的新方式</p>

          <!-- AI 输入框 -->
          <div class="flex items-center gap-3 bg-white rounded-xl shadow-sm px-4 py-2 border border-gray-100/80" style="max-width: 500px;">
            <input
              v-model="chatInput"
              type="text"
              placeholder="与您的 AI 助手对话，获取帮助或创建内容..."
              class="flex-1 outline-none text-sm text-gray-700 bg-transparent"
              style="min-width: 0;"
              @keyup.enter="handleSend"
            />
            <button
              class="flex items-center gap-1.5 bg-indigo-500 hover:bg-indigo-600 text-white text-sm px-3.5 py-1.5 rounded-lg transition-colors flex-shrink-0"
              @click="handleSend"
            >
              <svg class="w-3.5 h-3.5" viewBox="0 0 24 24" fill="currentColor">
                <path d="M2.01 21L23 12 2.01 3 2 10l15 2-15 2z" />
              </svg>
              发送
            </button>
          </div>

          <!-- 快速问题 -->
          <div class="flex items-center gap-2 mt-2.5 flex-wrap">
            <span class="text-xs text-gray-400">试试问我：</span>
            <button
              v-for="q in quickQuestions"
              :key="q"
              class="text-xs text-gray-500 hover:text-indigo-600 border border-gray-200 hover:border-indigo-300 rounded-full px-3 py-1 bg-white/70 hover:bg-white transition-all"
              @click="handleQuickQuestion(q)"
            >
              ◇ {{ q }}
            </button>
          </div>
        </div>
      </div>

      <!-- ── 统计卡片 + 快速创建 两行合一 ── -->
      <div class="grid gap-3" style="grid-template-columns: repeat(5, 1fr);">
        <div
          v-for="stat in stats"
          :key="stat.label"
          class="stat-card bg-white rounded-xl p-4 flex items-center gap-3 shadow-sm hover:shadow-md transition-shadow"
        >
          <div
            class="w-10 h-10 rounded-xl flex items-center justify-center flex-shrink-0"
            :style="{ background: stat.bg }"
          >
            <svg v-if="stat.icon === 'agent'" class="w-5 h-5" :style="{ color: stat.color }" viewBox="0 0 24 24" fill="currentColor">
              <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-2 14.5v-9l6 4.5-6 4.5z"/>
            </svg>
            <svg v-else-if="stat.icon === 'workflow'" class="w-5 h-5" :style="{ color: stat.color }" viewBox="0 0 24 24" fill="currentColor">
              <path d="M17 12h-5v5h5v-5zM16 1v2H8V1H6v2H5c-1.11 0-1.99.9-1.99 2L3 19c0 1.1.89 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2h-1V1h-2zm3 18H5V8h14v11z"/>
            </svg>
            <svg v-else-if="stat.icon === 'mcp'" class="w-5 h-5" :style="{ color: stat.color }" viewBox="0 0 24 24" fill="currentColor">
              <path d="M12 2l-5.5 9h11L12 2zm0 3.84L13.93 9h-3.87L12 5.84zM17.5 13c-2.49 0-4.5 2.01-4.5 4.5s2.01 4.5 4.5 4.5 4.5-2.01 4.5-4.5-2.01-4.5-4.5-4.5zm0 7a2.5 2.5 0 0 1 0-5 2.5 2.5 0 0 1 0 5zM3 21.5h8v-8H3v8zm2-6h4v4H5v-4z"/>
            </svg>
            <svg v-else-if="stat.icon === 'skill'" class="w-5 h-5" :style="{ color: stat.color }" viewBox="0 0 24 24" fill="currentColor">
              <path d="M12 1L3 5v6c0 5.55 3.84 10.74 9 12 5.16-1.26 9-6.45 9-12V5l-9-4zm0 10.99h7c-.53 4.12-3.28 7.79-7 8.94V12H5V6.3l7-3.11v8.8z"/>
            </svg>
            <svg v-else class="w-5 h-5" :style="{ color: stat.color }" viewBox="0 0 24 24" fill="currentColor">
              <path d="M20 2H4c-1.1 0-1.99.9-1.99 2L2 22l4-4h14c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zm-2 12H6v-2h12v2zm0-3H6V9h12v2zm0-3H6V6h12v2z"/>
            </svg>
          </div>
          <div class="min-w-0">
            <div class="text-xs text-gray-400 mb-0.5 truncate">{{ stat.label }}</div>
            <div class="text-xl font-bold text-gray-800 leading-tight">{{ stat.value }}</div>
            <div class="text-xs mt-0.5" style="color: #10B981;">{{ stat.change }}</div>
          </div>
        </div>
      </div>

      <!-- ── 快速创建 ── -->
      <div>
        <h2 class="text-sm font-semibold text-gray-800 mb-2.5">快速创建</h2>
        <div class="grid grid-cols-4 gap-3">
          <div
            v-for="action in quickActions"
            :key="action.label"
            class="quick-action-card bg-white rounded-xl p-4 flex items-center gap-3 shadow-sm hover:shadow-md cursor-pointer transition-all group"
            @click="navigateTo(action.route)"
          >
            <div
              class="w-10 h-10 rounded-xl flex items-center justify-center flex-shrink-0"
              :style="{ background: action.bg }"
            >
              <svg v-if="action.icon === 'agent'" class="w-5 h-5" :style="{ color: action.color }" viewBox="0 0 24 24" fill="currentColor">
                <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-2 14.5v-9l6 4.5-6 4.5z"/>
              </svg>
              <svg v-else-if="action.icon === 'workflow'" class="w-5 h-5" :style="{ color: action.color }" viewBox="0 0 24 24" fill="currentColor">
                <path d="M6.5 10h-2v5h2v-5zm4 0h-2v5h2v-5zm8.5 7H5v2h14v-2zm-4.5-7h-2v5h2v-5zM14 3L2 9v2h20V9L14 3z"/>
              </svg>
              <svg v-else-if="action.icon === 'mcp'" class="w-5 h-5" :style="{ color: action.color }" viewBox="0 0 24 24" fill="currentColor">
                <path d="M12 2l-5.5 9h11L12 2zm0 3.84L13.93 9h-3.87L12 5.84zM17.5 13c-2.49 0-4.5 2.01-4.5 4.5s2.01 4.5 4.5 4.5 4.5-2.01 4.5-4.5-2.01-4.5-4.5-4.5zm0 7a2.5 2.5 0 0 1 0-5 2.5 2.5 0 0 1 0 5zM3 21.5h8v-8H3v8zm2-6h4v4H5v-4z"/>
              </svg>
              <svg v-else class="w-5 h-5" :style="{ color: action.color }" viewBox="0 0 24 24" fill="currentColor">
                <path d="M12 1L3 5v6c0 5.55 3.84 10.74 9 12 5.16-1.26 9-6.45 9-12V5l-9-4zm0 10.99h7c-.53 4.12-3.28 7.79-7 8.94V12H5V6.3l7-3.11v8.8z"/>
              </svg>
            </div>
            <div class="flex-1 min-w-0">
              <div class="font-medium text-gray-800 text-sm">{{ action.label }}</div>
              <div class="text-xs text-gray-400 mt-0.5 truncate">{{ action.desc }}</div>
            </div>
            <div class="flex-shrink-0 w-6 h-6 rounded-lg border border-gray-100 flex items-center justify-center text-gray-300 group-hover:border-indigo-200 group-hover:text-indigo-400 transition-colors">
              <svg class="w-3.5 h-3.5" viewBox="0 0 24 24" fill="currentColor"><path d="M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z"/></svg>
            </div>
          </div>
        </div>
      </div>

      <!-- ── 底部两栏：最近使用 + 右侧（热门 + 公告） ── -->
      <div class="grid gap-4" style="grid-template-columns: 1fr 300px;">

        <!-- 最近使用 -->
        <div class="bg-white rounded-xl shadow-sm overflow-hidden">
          <div class="flex items-center justify-between px-5 pt-4 pb-2.5">
            <h2 class="text-sm font-semibold text-gray-800">最近使用</h2>
            <button class="text-xs text-indigo-500 hover:text-indigo-700">查看全部</button>
          </div>
          <div class="flex gap-0 px-5 border-b border-gray-100">
            <button
              v-for="tab in recentTabs"
              :key="tab.key"
              class="text-sm py-1.5 px-3 -mb-px border-b-2 transition-colors"
              :class="recentTab === tab.key
                ? 'border-indigo-500 text-indigo-600 font-medium'
                : 'border-transparent text-gray-400 hover:text-gray-600'"
              @click="recentTab = tab.key"
            >
              {{ tab.label }}
            </button>
          </div>
          <div class="divide-y divide-gray-50">
            <div
              v-for="item in recentItems[recentTab]"
              :key="item.name"
              class="flex items-start gap-3 px-5 py-3 hover:bg-gray-50 cursor-pointer transition-colors"
            >
              <div
                class="w-8 h-8 rounded-lg flex items-center justify-center flex-shrink-0 mt-0.5"
                :style="{ background: item.bg }"
              >
                <span class="text-sm">
                  <span v-if="item.icon === 'contract'">📋</span>
                  <span v-else-if="item.icon === 'finance'">📊</span>
                  <span v-else-if="item.icon === 'service'">💬</span>
                  <span v-else-if="item.icon === 'market'">📈</span>
                  <span v-else-if="item.icon === 'train'">🎓</span>
                  <span v-else-if="item.icon === 'data'">🗄️</span>
                  <span v-else-if="item.icon === 'email'">✉️</span>
                  <span v-else-if="item.icon === 'db'">🗃️</span>
                  <span v-else-if="item.icon === 'api'">🔌</span>
                  <span v-else-if="item.icon === 'code'">💻</span>
                  <span v-else>📄</span>
                </span>
              </div>
              <div class="flex-1 min-w-0">
                <div class="flex items-center justify-between mb-0.5">
                  <span class="text-sm font-medium text-gray-800">{{ item.name }}</span>
                  <span class="text-xs text-gray-400 flex-shrink-0 ml-2">使用 {{ item.uses }} 次</span>
                </div>
                <p class="text-xs text-gray-400 truncate">{{ item.desc }}</p>
              </div>
              <div class="text-xs text-gray-300 flex-shrink-0 ml-2">{{ item.time }}</div>
            </div>
          </div>
        </div>

        <!-- 右侧：热门智能体 + 系统公告 -->
        <div class="space-y-3">

          <!-- 热门智能体 -->
          <div class="bg-white rounded-xl shadow-sm overflow-hidden">
            <div class="flex items-center justify-between px-4 pt-4 pb-2.5">
              <h2 class="text-sm font-semibold text-gray-800">热门智能体</h2>
              <button class="text-xs text-indigo-500 hover:text-indigo-700">查看全部</button>
            </div>
            <div class="divide-y divide-gray-50 px-3">
              <div
                v-for="agent in hotAgents"
                :key="agent.rank"
                class="flex items-center gap-2.5 py-2.5 hover:bg-gray-50 rounded-lg px-1 cursor-pointer transition-colors"
              >
                <div
                  class="w-5 h-5 rounded-md flex items-center justify-center text-xs font-bold flex-shrink-0"
                  :style="agent.rank <= 3
                    ? { background: agent.color, color: 'white' }
                    : { background: '#F3F4F6', color: '#9CA3AF' }"
                >
                  {{ agent.rank }}
                </div>
                <div class="flex-1 min-w-0">
                  <div class="text-sm font-medium text-gray-800 truncate">{{ agent.name }}</div>
                  <div class="text-xs text-gray-400">{{ agent.uses }}</div>
                </div>
                <div class="flex items-center gap-0.5 text-xs text-gray-500 flex-shrink-0">
                  <span class="text-yellow-400">★</span>
                  {{ agent.rating }}
                </div>
              </div>
            </div>
          </div>

        </div>

      </div>

    </div>
  </div>
</template>

<style scoped lang="scss">
.home-page {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
}

/* Hero Banner 渐变背景 */
.hero-banner {
  background: linear-gradient(135deg, #EEF0FF 0%, #F0EFFF 55%, #E8F4FF 100%);
}

/* ── 浮动卡片动画 ── */
.card-1 {
  animation: float-slow 4s ease-in-out infinite;
}
.card-2 {
  animation: float-slow 4s ease-in-out infinite 1s;
}
.card-3 {
  animation: float-slow 4s ease-in-out infinite 0.5s;
}

@keyframes float-slow {
  0%, 100% { transform: translateY(0px); }
  50%       { transform: translateY(-6px); }
}

/* 机器人浮动 */
.robot-wrap {
  animation: float-robot 3.5s ease-in-out infinite;
}

@keyframes float-robot {
  0%, 100% { transform: translateY(0px); }
  50%       { transform: translateY(-10px); }
}

/* 节点样式 */
.node-pill {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 2px 6px;
  border-radius: 6px;
  font-size: 10px;
  white-space: nowrap;
  flex-shrink: 0;
}

.arrow-line {
  flex: 1;
  height: 1px;
  min-width: 6px;
  background: linear-gradient(90deg, #C7D2FE, #A5B4FC);
  position: relative;

  &::after {
    content: '';
    position: absolute;
    right: -1px;
    top: -3px;
    border: 3px solid transparent;
    border-left-color: #A5B4FC;
  }
}

.node-diamond {
  width: 12px;
  height: 12px;
  background: #818CF8;
  transform: rotate(45deg);
  flex-shrink: 0;
  border-radius: 2px;
}

/* 卡片边框 */
.stat-card,
.quick-action-card {
  border: 1px solid #F3F4F6;
}
</style>

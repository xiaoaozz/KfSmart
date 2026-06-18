<script setup lang="ts">
import { NButton, NTag, NInput } from 'naive-ui';
import { fetchCopyAgent as fetchCopyAgentWorkflow } from '@/service/api/agent';
import { fetchMarketplace as fetchAgentMarketplace } from '@/service/api/resource';

const items = ref<Api.AgentCenter.MarketplaceItem[]>([]);
const installingId = ref('');
const searchKeyword = ref('');
const activeTab = ref('推荐');

const tabs = ['推荐', '全部', '热门', '最新', '我创建的'];

const hotAgents = [
  { rank: 1, name: '合同审查助手', uses: '1.2k 次使用', rating: 4.8, color: '#ef4444' },
  { rank: 2, name: '财务报表分析师', uses: '1.5k 次使用', rating: 4.7, color: '#f97316' },
  { rank: 3, name: '招标文件分析师', uses: '856 次使用', rating: 4.6, color: '#22c55e' },
  { rank: 4, name: 'IT 运维助手', uses: '945 次使用', rating: 4.7, color: '#3b82f6' },
  { rank: 5, name: 'HR 助手', uses: '632 次使用', rating: 4.6, color: '#8b5cf6' },
];

const categories = [
  { icon: '⚖️', name: '法律合规', count: 12 },
  { icon: '💰', name: '财务管理', count: 8 },
  { icon: '👥', name: '人力资源', count: 9 },
  { icon: '📣', name: '市场营销', count: 7 },
  { icon: '💻', name: '信息技术', count: 11 },
  { icon: '🎧', name: '客户服务', count: 6 },
  { icon: '📚', name: '知识管理', count: 10 },
  { icon: '⚙️', name: '运营管理', count: 8 },
];

const myAgents = [
  { name: '项目管理助手', status: '已发布', uses: 156, date: '2024-05-10', color: '#3b82f6' },
  { name: '供应商评估助手', status: '审核中', uses: 0, date: '2024-05-08', color: '#3b82f6' },
];

const featuredAgents = [
  {
    id: '1',
    name: '合同审查助手',
    description: '基于企业合同模板与法律知识，自动审查合同条款，识别风险点并给出修改建议。',
    tags: ['法律', '合同审查', '风险识别'],
    uses: 1200,
    satisfaction: 98,
    color: '#6366f1',
    icon: '📋',
  },
  {
    id: '2',
    name: '招标文件分析师',
    description: '智能解析招标文件，提取关键信息，生成分析报告与投标建议。',
    tags: ['招投标', '文件解析', '信息提取'],
    uses: 856,
    satisfaction: 95,
    color: '#ec4899',
    icon: '📊',
  },
  {
    id: '3',
    name: '财务报表分析师',
    description: '分析企业财务报表，生成关键指标解读与趋势分析，辅助经营决策。',
    tags: ['财务', '报表分析', '经营分析'],
    uses: 1500,
    satisfaction: 97,
    color: '#10b981',
    icon: '📈',
  },
  {
    id: '4',
    name: 'HR 助手',
    description: '解答员工常见问题，提供人事政策、流程指引与表单生成等服务。',
    tags: ['人力资源', '问答', '流程指引'],
    uses: 632,
    satisfaction: 96,
    color: '#f59e0b',
    icon: '👤',
  },
  {
    id: '5',
    name: '市场调研分析师',
    description: '收集市场信息，分析行业趋势，生成调研报告与洞察建议。',
    tags: ['市场', '调研', '行业分析'],
    uses: 789,
    satisfaction: 94,
    color: '#3b82f6',
    icon: '🔍',
  },
  {
    id: '6',
    name: 'IT 运维助手',
    description: '解答 IT 相关问题，提供故障排查指引、操作手册与最佳实践。',
    tags: ['IT运维', '故障排查', '技术支持'],
    uses: 945,
    satisfaction: 97,
    color: '#8b5cf6',
    icon: '🔧',
  },
];

const latestAgents = [
  { id: '7', name: '知识库问答助手', description: '基于企业知识库，提供精准问答服务，支持多源知识检索。', category: '知识管理', uses: 324, rating: 4.8, date: '2024-05-15' },
  { id: '8', name: '客户服务助手', description: '7x24 小时客户问答，自动识别问题类型并提供解决方案。', category: '客户服务', uses: 567, rating: 4.7, date: '2024-05-14' },
  { id: '9', name: '培训课程生成器', description: '根据主题自动生成培训大纲、课件与练习题。', category: '培训学习', uses: 234, rating: 4.6, date: '2024-05-13' },
];

async function loadData() {
  const { error, data } = await fetchAgentMarketplace();
  if (!error && data) items.value = data;
}

async function installAgent(workflowId: string) {
  installingId.value = workflowId;
  try {
    const { error } = await fetchCopyAgentWorkflow(workflowId);
    if (!error) {
      window.$message?.success('已安装到工作流列表');
      await loadData();
    }
  } finally {
    installingId.value = '';
  }
}

onMounted(loadData);
</script>

<template>
  <div class="flex h-full overflow-hidden bg-[#f5f6fa]">
    <!-- Main Content -->
    <div class="flex-1 overflow-y-auto px-8 py-6">
      <!-- Header -->
      <div class="mb-6">
        <h1 class="mb-1 text-2xl font-bold text-gray-900">Agent 广场</h1>
        <p class="text-sm text-gray-500">探索、试用并部署适合您业务场景的智能体，助力团队效率提升</p>
      </div>

      <!-- Tabs -->
      <div class="mb-6 flex gap-6 border-b border-gray-200">
        <button
          v-for="tab in tabs"
          :key="tab"
          class="relative pb-3 text-sm font-medium transition-colors"
          :class="activeTab === tab ? 'text-blue-600' : 'text-gray-500 hover:text-gray-700'"
          @click="activeTab = tab"
        >
          {{ tab }}
          <span
            v-if="activeTab === tab"
            class="absolute bottom-0 left-0 right-0 h-0.5 rounded-full bg-blue-600"
          />
        </button>
      </div>

      <!-- Hero Banner -->
      <div class="relative mb-8 overflow-hidden rounded-2xl bg-gradient-to-r from-[#e8eeff] to-[#d4e0ff] p-8">
        <div class="max-w-md">
          <h2 class="mb-2 text-2xl font-bold text-gray-900">快速构建与部署专属智能体</h2>
          <p class="mb-6 text-sm text-gray-600">结合企业知识与大模型能力，打造贴合业务场景的智能助手</p>
          <div class="flex gap-3">
            <NButton type="primary" size="medium" class="!rounded-lg !px-6 !font-medium">
              创建智能体
            </NButton>
            <NButton size="medium" class="!rounded-lg !px-6 !font-medium">
              使用指南
            </NButton>
          </div>
        </div>
        <!-- Robot illustration placeholder -->
        <div class="absolute right-10 top-1/2 -translate-y-1/2 text-8xl opacity-80">🤖</div>
      </div>

      <!-- Featured Recommendations -->
      <div class="mb-8">
        <div class="mb-4 flex items-center justify-between">
          <h3 class="text-base font-semibold text-gray-900">精选推荐</h3>
          <button class="text-sm text-blue-600 hover:text-blue-700">查看全部</button>
        </div>
        <div class="grid grid-cols-3 gap-4">
          <div
            v-for="agent in featuredAgents"
            :key="agent.id"
            class="rounded-xl border border-gray-100 bg-white p-5 shadow-sm transition-shadow hover:shadow-md"
          >
            <div class="mb-3 flex items-center gap-3">
              <div
                class="flex h-10 w-10 items-center justify-center rounded-xl text-xl"
                :style="{ backgroundColor: agent.color + '20' }"
              >
                {{ agent.icon }}
              </div>
              <h4 class="font-semibold text-gray-900">{{ agent.name }}</h4>
            </div>
            <p class="mb-3 line-clamp-2 h-9 text-xs leading-relaxed text-gray-500">{{ agent.description }}</p>
            <div class="mb-3 flex flex-wrap gap-1.5">
              <span
                v-for="tag in agent.tags"
                :key="tag"
                class="rounded-md bg-blue-50 px-2 py-0.5 text-xs text-blue-600"
              >{{ tag }}</span>
            </div>
            <div class="flex items-center justify-between">
              <div class="flex items-center gap-3 text-xs text-gray-400">
                <span class="flex items-center gap-1">
                  <svg class="h-3.5 w-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4.318 6.318a4.5 4.5 0 000 6.364L12 20.364l7.682-7.682a4.5 4.5 0 00-6.364-6.364L12 7.636l-1.318-1.318a4.5 4.5 0 00-6.364 0z" />
                  </svg>
                  {{ agent.uses >= 1000 ? (agent.uses / 1000).toFixed(1) + 'k' : agent.uses }}
                </span>
                <span class="flex items-center gap-1">
                  <svg class="h-3.5 w-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M14 10h4.764a2 2 0 011.789 2.894l-3.5 7A2 2 0 0115.263 21h-4.017c-.163 0-.326-.02-.485-.06L7 20m7-10V5a2 2 0 00-2-2h-.095c-.5 0-.905.405-.905.905 0 .714-.211 1.412-.608 1.99L7 11v9m7-10h-2M7 20H5a2 2 0 01-2-2v-6a2 2 0 012-2h2.5" />
                  </svg>
                  {{ agent.satisfaction }}%
                </span>
              </div>
              <NButton size="tiny" type="primary" ghost>试用</NButton>
            </div>
          </div>
        </div>
      </div>

      <!-- Latest Releases -->
      <div>
        <div class="mb-4 flex items-center justify-between">
          <h3 class="text-base font-semibold text-gray-900">最新发布</h3>
        </div>
        <div class="overflow-hidden rounded-xl border border-gray-100 bg-white shadow-sm">
          <table class="w-full">
            <thead>
              <tr class="border-b border-gray-100 bg-gray-50">
                <th class="px-5 py-3 text-left text-xs font-medium text-gray-500">智能体</th>
                <th class="px-5 py-3 text-left text-xs font-medium text-gray-500">简介</th>
                <th class="px-5 py-3 text-left text-xs font-medium text-gray-500">分类</th>
                <th class="px-5 py-3 text-left text-xs font-medium text-gray-500">使用次数</th>
                <th class="px-5 py-3 text-left text-xs font-medium text-gray-500">评分</th>
                <th class="px-5 py-3 text-left text-xs font-medium text-gray-500">更新时间</th>
                <th class="px-5 py-3 text-left text-xs font-medium text-gray-500">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="(agent, index) in latestAgents"
                :key="agent.id"
                class="border-b border-gray-50 hover:bg-gray-50"
                :class="index === latestAgents.length - 1 ? '!border-b-0' : ''"
              >
                <td class="px-5 py-4">
                  <div class="flex items-center gap-2">
                    <div class="flex h-7 w-7 items-center justify-center rounded-lg bg-blue-100 text-sm">🤖</div>
                    <span class="text-sm font-medium text-gray-900">{{ agent.name }}</span>
                  </div>
                </td>
                <td class="max-w-xs px-5 py-4">
                  <span class="line-clamp-1 text-sm text-gray-500">{{ agent.description }}</span>
                </td>
                <td class="px-5 py-4">
                  <span class="rounded-md bg-blue-50 px-2 py-1 text-xs text-blue-600">{{ agent.category }}</span>
                </td>
                <td class="px-5 py-4 text-sm text-gray-700">{{ agent.uses }}</td>
                <td class="px-5 py-4">
                  <div class="flex items-center gap-1 text-sm">
                    <span class="text-yellow-400">★</span>
                    <span class="text-gray-700">{{ agent.rating }}</span>
                  </div>
                </td>
                <td class="px-5 py-4 text-sm text-gray-500">{{ agent.date }}</td>
                <td class="px-5 py-4">
                  <NButton size="tiny" type="primary" ghost>试用</NButton>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <!-- Right Sidebar -->
    <div class="w-72 flex-shrink-0 overflow-y-auto border-l border-gray-200 bg-white px-5 py-6">
      <!-- Hot Agents -->
      <div class="mb-6">
        <div class="mb-3 flex items-center justify-between">
          <h4 class="text-sm font-semibold text-gray-900">热门智能体</h4>
          <button class="text-xs text-blue-600 hover:text-blue-700">查看更多</button>
        </div>
        <div class="space-y-3">
          <div
            v-for="agent in hotAgents"
            :key="agent.rank"
            class="flex items-center gap-3"
          >
            <div
              class="flex h-5 w-5 flex-shrink-0 items-center justify-center rounded text-xs font-bold text-white"
              :style="{ backgroundColor: agent.rank <= 3 ? ['#ef4444','#f97316','#22c55e'][agent.rank-1] : '#94a3b8' }"
            >
              {{ agent.rank }}
            </div>
            <div class="flex-1 min-w-0">
              <p class="truncate text-sm font-medium text-gray-900">{{ agent.name }}</p>
              <p class="text-xs text-gray-400">{{ agent.uses }}</p>
            </div>
            <div class="flex items-center gap-0.5 text-xs">
              <span class="text-yellow-400">★</span>
              <span class="text-gray-600">{{ agent.rating }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- Divider -->
      <div class="mb-6 border-t border-gray-100" />

      <!-- Category Browse -->
      <div class="mb-6">
        <div class="mb-3 flex items-center justify-between">
          <h4 class="text-sm font-semibold text-gray-900">分类浏览</h4>
          <button class="text-xs text-blue-600 hover:text-blue-700">全部分类</button>
        </div>
        <div class="space-y-2">
          <div
            v-for="cat in categories"
            :key="cat.name"
            class="flex cursor-pointer items-center justify-between rounded-lg px-2 py-1.5 hover:bg-gray-50"
          >
            <div class="flex items-center gap-2">
              <span class="text-base">{{ cat.icon }}</span>
              <span class="text-sm text-gray-700">{{ cat.name }}</span>
            </div>
            <span class="text-xs text-gray-400">{{ cat.count }}</span>
          </div>
        </div>
      </div>

      <!-- Divider -->
      <div class="mb-6 border-t border-gray-100" />

      <!-- My Agents -->
      <div>
        <div class="mb-3 flex items-center justify-between">
          <h4 class="text-sm font-semibold text-gray-900">我的智能体</h4>
          <button class="text-xs text-blue-600 hover:text-blue-700">管理我的智能体</button>
        </div>
        <div class="mb-4 space-y-3">
          <div
            v-for="agent in myAgents"
            :key="agent.name"
            class="flex items-start gap-3 rounded-lg border border-gray-100 p-3"
          >
            <div class="flex h-8 w-8 flex-shrink-0 items-center justify-center rounded-lg bg-blue-100 text-sm">🤖</div>
            <div class="flex-1 min-w-0">
              <p class="truncate text-sm font-medium text-gray-900">{{ agent.name }}</p>
              <p
                class="text-xs"
                :class="agent.status === '已发布' ? 'text-green-500' : 'text-orange-500'"
              >{{ agent.status }}</p>
              <p class="text-xs text-gray-400">使用 {{ agent.uses }} 次</p>
            </div>
            <span class="flex-shrink-0 text-xs text-gray-400">{{ agent.date }}</span>
          </div>
        </div>
        <NButton type="primary" ghost block size="small">
          + 创建智能体
        </NButton>
      </div>
    </div>
  </div>
</template>

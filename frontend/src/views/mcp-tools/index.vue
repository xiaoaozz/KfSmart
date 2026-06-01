<script setup lang="ts">
import { ref, computed } from 'vue';

defineOptions({
  name: 'McpTools'
});

// 统计数据
const stats = ref([
  {
    icon: 'tool-kit',
    label: '已接入工具数',
    value: '18',
    change: '+2',
    trend: 'up',
    color: 'blue'
  },
  {
    icon: 'data-connected',
    label: '在线服务数',
    value: '14',
    change: '+1',
    trend: 'up',
    color: 'green'
  },
  {
    icon: 'chat',
    label: '今日调用次数',
    value: '1,248',
    change: '+356',
    trend: 'up',
    color: 'purple'
  },
  {
    icon: 'time',
    label: '平均响应时间',
    value: '842ms',
    change: '-38ms',
    trend: 'down',
    color: 'cyan'
  }
]);

// 工具分类标签
const categories = ref([
  { label: '全部', value: 'all', count: 18 },
  { label: '文档处理', value: 'document', count: 3 },
  { label: '代码仓库', value: 'code', count: 2 },
  { label: '数据库', value: 'database', count: 3 },
  { label: '工作系统', value: 'work', count: 2 },
  { label: '企业IM', value: 'im', count: 2 },
  { label: '办公协作', value: 'office', count: 2 }
]);

const activeCategory = ref('all');

// 工具列表
const tools = ref([
  {
    id: '1',
    name: 'GitLab 仓库助手',
    icon: 'logos:gitlab',
    description: '通过 MCP 协议连接 GitLab 仓库事务，搜索项目、MR、Issue 等资源。',
    category: 'code',
    status: '已连接',
    statusType: 'success',
    tags: ['代码仓库'],
    version: '1.2.3',
    lastUpdate: '2025-04-12 14:32'
  },
  {
    id: '2',
    name: 'MySQL 查询工具',
    icon: 'logos:mysql',
    description: '安全查询类 SQL 事务，直连数据库或数据库映射工具进行。',
    category: 'database',
    status: '已连接',
    statusType: 'success',
    tags: ['数据库'],
    version: '1.2.3',
    lastUpdate: '2025-05-23 10:25:31'
  },
  {
    id: '3',
    name: '飞书知识库',
    icon: 'carbon:document',
    description: '检索多维表格内容内容，支持查询和自动同步机制。',
    category: 'document',
    status: '已连接',
    statusType: 'success',
    tags: ['文档处理'],
    version: '1.2.3',
    lastUpdate: '2025-05-23 10:22:45'
  },
  {
    id: '4',
    name: 'Jira 工单助手',
    icon: 'logos:jira',
    description: '组建、查询与监控 Jira 工单，打通需求管理与事务流程。',
    category: 'work',
    status: '维护中',
    statusType: 'warning',
    tags: ['工作系统'],
    version: '0.9.2',
    lastUpdate: '2025-05-17'
  },
  {
    id: '5',
    name: '文档解析器',
    icon: 'carbon:document-tasks',
    description: '解析多维表格文件 (PDF/Word/Excel)，重塑文本与表格是自动摘取信息。',
    category: 'document',
    status: '已连接',
    statusType: 'success',
    tags: ['文档处理'],
    version: '2.1.0',
    lastUpdate: '2025-05-23 10:24:18'
  },
  {
    id: '6',
    name: 'Webhook 调用器',
    icon: 'carbon:webhook',
    description: '通过 Webhook 向外部服务或内部服务查询事项。',
    category: 'work',
    status: '失败',
    statusType: 'error',
    tags: ['集成服务'],
    version: '1.0.5',
    lastUpdate: '2025-05-23 10:20:09'
  }
]);

// 侧边栏工具详情
const selectedTool = ref<any>(null);
const showToolDetail = ref(false);

function selectTool(tool: any) {
  selectedTool.value = tool;
  showToolDetail.value = true;
}

function closeToolDetail() {
  showToolDetail.value = false;
  selectedTool.value = null;
}

// 筛选后的工具列表
const filteredTools = computed(() => {
  if (activeCategory.value === 'all') {
    return tools.value;
  }
  return tools.value.filter(tool => tool.category === activeCategory.value);
});

const getColorClasses = (color: string) => {
  const colorMap: Record<string, { icon: string; bg: string; text: string }> = {
    blue: { icon: 'text-blue-500', bg: 'bg-blue-50 dark:bg-blue-900/20', text: 'text-blue-600' },
    green: { icon: 'text-green-500', bg: 'bg-green-50 dark:bg-green-900/20', text: 'text-green-600' },
    purple: { icon: 'text-purple-500', bg: 'bg-purple-50 dark:bg-purple-900/20', text: 'text-purple-600' },
    cyan: { icon: 'text-cyan-500', bg: 'bg-cyan-50 dark:bg-cyan-900/20', text: 'text-cyan-600' }
  };
  return colorMap[color] || colorMap.blue;
};
</script>

<template>
  <div class="mcp-tools-page flex flex-col h-full bg-gray-50 dark:bg-gray-900">
    <!-- 顶部标题和操作 -->
    <div class="flex-shrink-0 bg-white dark:bg-gray-800 border-b border-gray-200 dark:border-gray-700 px-8 py-6">
      <div class="flex items-center justify-between mb-6">
        <div>
          <div class="flex items-center gap-3 mb-2">
            <h1 class="text-2xl font-bold text-gray-900 dark:text-white">MCP 工具中心</h1>
            <NTag type="info" size="small">企业版</NTag>
          </div>
          <p class="text-sm text-gray-600 dark:text-gray-400">
            连接外部工具与服务，通过 Model Context Protocol (MCP) 标准协议，打通通工作效率。
          </p>
        </div>
        <div class="flex items-center gap-3">
          <NButton type="primary" size="large">
            <template #icon>
              <icon-carbon:add class="text-lg" />
            </template>
            新建 MCP 工具
          </NButton>
          <NButton size="large" tertiary>
            <template #icon>
              <icon-carbon:data-vis-1 class="text-lg" />
            </template>
            MCP 协议文档
          </NButton>
        </div>
      </div>

      <!-- 统计卡片 -->
      <div class="grid grid-cols-4 gap-4">
        <div
          v-for="stat in stats"
          :key="stat.label"
          class="stat-card bg-white dark:bg-gray-800 rounded-xl p-4 border border-gray-100 dark:border-gray-700"
        >
          <div class="flex items-center gap-3 mb-3">
            <div :class="['w-10 h-10 rounded-lg flex items-center justify-center', getColorClasses(stat.color).bg]">
              <!-- 使用静态图标组件 -->
              <icon-carbon:tool-kit v-if="stat.icon === 'tool-kit'" :class="['text-lg', getColorClasses(stat.color).icon]" />
              <icon-carbon:data-connected v-else-if="stat.icon === 'data-connected'" :class="['text-lg', getColorClasses(stat.color).icon]" />
              <icon-carbon:chat v-else-if="stat.icon === 'chat'" :class="['text-lg', getColorClasses(stat.color).icon]" />
              <icon-carbon:time v-else-if="stat.icon === 'time'" :class="['text-lg', getColorClasses(stat.color).icon]" />
            </div>
            <div class="flex-1">
              <div class="text-xs text-gray-600 dark:text-gray-400">{{ stat.label }}</div>
              <div class="text-xl font-bold text-gray-900 dark:text-white">{{ stat.value }}</div>
            </div>
          </div>
          <div class="flex items-center gap-1 text-xs" :class="stat.trend === 'up' ? 'text-green-600' : 'text-cyan-600'">
            <icon-carbon:arrow-up v-if="stat.trend === 'up'" class="text-xs" />
            <icon-carbon:arrow-down v-else class="text-xs" />
            <span>较昨日 {{ stat.change }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 主体内容 -->
    <div class="flex-1 flex overflow-hidden">
      <div :class="['flex-1 p-6 overflow-y-auto transition-all', showToolDetail ? 'mr-400px' : '']">
        <!-- 分类标签 -->
        <div class="flex items-center gap-3 mb-6">
          <NButton
            v-for="cat in categories"
            :key="cat.value"
            :type="activeCategory === cat.value ? 'primary' : 'default'"
            :ghost="activeCategory !== cat.value"
            size="medium"
            @click="activeCategory = cat.value"
          >
            {{ cat.label }}
            <template v-if="activeCategory === cat.value" #icon>
              <span class="ml-1 text-xs">({{ cat.count }})</span>
            </template>
          </NButton>
        </div>

        <!-- 工具卡片网格 -->
        <div class="grid grid-cols-3 gap-6">
          <div
            v-for="tool in filteredTools"
            :key="tool.id"
            class="tool-card bg-white dark:bg-gray-800 rounded-2xl p-6 border border-gray-200 dark:border-gray-700 cursor-pointer hover:shadow-lg transition-all"
            @click="selectTool(tool)"
          >
            <!-- 工具头部 -->
            <div class="flex items-start justify-between mb-4">
              <div class="flex items-center gap-3">
                <div class="w-12 h-12 rounded-xl bg-gray-50 dark:bg-gray-700 flex items-center justify-center">
                  <!-- 处理不同类型的图标 -->
                  <icon-logos:gitlab v-if="tool.icon === 'logos:gitlab'" class="text-2xl" />
                  <icon-logos:mysql v-else-if="tool.icon === 'logos:mysql'" class="text-2xl" />
                  <icon-carbon:document v-else-if="tool.icon === 'carbon:document'" class="text-2xl" />
                  <icon-logos:jira v-else-if="tool.icon === 'logos:jira'" class="text-2xl" />
                  <icon-carbon:document-tasks v-else-if="tool.icon === 'carbon:document-tasks'" class="text-2xl" />
                  <icon-carbon:webhook v-else-if="tool.icon === 'carbon:webhook'" class="text-2xl" />
                </div>
                <div>
                  <h3 class="text-base font-bold text-gray-900 dark:text-white mb-1">{{ tool.name }}</h3>
                  <NTag :type="tool.statusType" size="small">{{ tool.status }}</NTag>
                </div>
              </div>
              <NButton text circle size="small">
                <template #icon>
                  <icon-carbon:overflow-menu-horizontal class="text-gray-500" />
                </template>
              </NButton>
            </div>

            <!-- 描述 -->
            <p class="text-sm text-gray-600 dark:text-gray-400 mb-4 line-clamp-2">
              {{ tool.description }}
            </p>

            <!-- 标签 -->
            <div class="flex items-center gap-2 mb-4">
              <NTag v-for="tag in tool.tags" :key="tag" size="small" :bordered="false">{{ tag }}</NTag>
            </div>

            <!-- 底部信息 -->
            <div class="flex items-center justify-between text-xs text-gray-500 dark:text-gray-400">
              <span>版本: {{ tool.version }}</span>
              <span>{{ tool.lastUpdate }}</span>
            </div>

            <!-- 操作按钮 -->
            <div class="flex items-center gap-2 mt-4 pt-4 border-t border-gray-100 dark:border-gray-700">
              <NButton size="small" type="primary" ghost class="flex-1">配置</NButton>
              <NButton size="small" class="flex-1">测试</NButton>
              <NButton text circle size="small" type="error">
                <template #icon>
                  <icon-carbon:close />
                </template>
              </NButton>
            </div>
          </div>
        </div>
      </div>

      <!-- 右侧工具详情面板 -->
      <Transition name="slide-left">
        <div
          v-if="showToolDetail && selectedTool"
          class="fixed right-0 top-0 bottom-0 w-400px bg-white dark:bg-gray-800 border-l border-gray-200 dark:border-gray-700 shadow-2xl overflow-y-auto z-50"
        >
          <div class="p-6">
            <!-- 头部 -->
            <div class="flex items-start justify-between mb-6">
              <div class="flex items-center gap-3">
                <div class="w-14 h-14 rounded-xl bg-gray-50 dark:bg-gray-700 flex items-center justify-center">
                  <component :is="`icon-${selectedTool.icon}`" class="text-3xl" />
                </div>
                <div>
                  <h2 class="text-lg font-bold text-gray-900 dark:text-white">{{ selectedTool.name }}</h2>
                  <NTag :type="selectedTool.statusType" size="small">{{ selectedTool.status }}</NTag>
                </div>
              </div>
              <NButton text circle @click="closeToolDetail">
                <template #icon>
                  <icon-carbon:close class="text-lg" />
                </template>
              </NButton>
            </div>

            <!-- 基础信息 -->
            <div class="mb-6">
              <h3 class="text-sm font-medium text-gray-700 dark:text-gray-300 mb-3">基础信息</h3>
              <div class="space-y-3">
                <div class="flex justify-between text-sm">
                  <span class="text-gray-600 dark:text-gray-400">工具ID</span>
                  <span class="font-mono text-gray-900 dark:text-white">{{ selectedTool.id }}</span>
                </div>
                <div class="flex justify-between text-sm">
                  <span class="text-gray-600 dark:text-gray-400">描述</span>
                  <span class="text-gray-900 dark:text-white">{{ selectedTool.description }}</span>
                </div>
                <div class="flex justify-between text-sm">
                  <span class="text-gray-600 dark:text-gray-400">类别</span>
                  <NTag size="small">{{ selectedTool.tags[0] }}</NTag>
                </div>
                <div class="flex justify-between text-sm">
                  <span class="text-gray-600 dark:text-gray-400">版本</span>
                  <span class="font-mono text-gray-900 dark:text-white">{{ selectedTool.version }}</span>
                </div>
                <div class="flex justify-between text-sm">
                  <span class="text-gray-600 dark:text-gray-400">创建时间</span>
                  <span class="text-gray-900 dark:text-white">{{ selectedTool.lastUpdate }}</span>
                </div>
                <div class="flex justify-between text-sm">
                  <span class="text-gray-600 dark:text-gray-400">创建人</span>
                  <span class="text-gray-900 dark:text-white">张明</span>
                </div>
              </div>
            </div>

            <!-- 连接配置 -->
            <div class="mb-6">
              <h3 class="text-sm font-medium text-gray-700 dark:text-gray-300 mb-3">连接配置</h3>
              <div class="bg-gray-50 dark:bg-gray-700/50 rounded-xl p-4">
                <div class="space-y-3 text-sm">
                  <div class="flex justify-between">
                    <span class="text-gray-600 dark:text-gray-400">Endpoint URL</span>
                    <span class="font-mono text-xs text-gray-900 dark:text-white">https://gitlab.acme.com/api/mcp</span>
                  </div>
                  <div class="flex justify-between">
                    <span class="text-gray-600 dark:text-gray-400">认证方式</span>
                    <span class="text-gray-900 dark:text-white">Personal Access Token</span>
                  </div>
                  <div class="flex justify-between">
                    <span class="text-gray-600 dark:text-gray-400">Access Token</span>
                    <span class="font-mono text-xs text-gray-900 dark:text-white">glpat-***********8f2a</span>
                  </div>
                  <div class="flex justify-between">
                    <span class="text-gray-600 dark:text-gray-400">过期时间</span>
                    <span class="text-gray-900 dark:text-white">30秒</span>
                  </div>
                </div>
              </div>
            </div>

            <!-- 权限与范围 -->
            <div class="mb-6">
              <h3 class="text-sm font-medium text-gray-700 dark:text-gray-300 mb-3">权限与范围</h3>
              <div class="space-y-2">
                <div class="flex items-center justify-between py-2">
                  <span class="text-sm text-gray-700 dark:text-gray-300">read_repository</span>
                  <icon-carbon:checkmark-filled class="text-green-500" />
                </div>
                <div class="flex items-center justify-between py-2">
                  <span class="text-sm text-gray-700 dark:text-gray-300">read_api</span>
                  <icon-carbon:checkmark-filled class="text-green-500" />
                </div>
                <div class="flex items-center justify-between py-2">
                  <span class="text-sm text-gray-700 dark:text-gray-300">read_user</span>
                  <icon-carbon:checkmark-filled class="text-green-500" />
                </div>
              </div>
            </div>

            <!-- 共享用户 -->
            <div class="mb-6">
              <h3 class="text-sm font-medium text-gray-700 dark:text-gray-300 mb-3">共享用户（3个）</h3>
              <div class="flex items-center gap-2">
                <NTag size="small">全局用户</NTag>
                <NButton text size="tiny" type="primary">
                  <template #icon>
                    <icon-carbon:add class="text-xs" />
                  </template>
                </NButton>
              </div>
            </div>

            <!-- IP 白名单 -->
            <div class="mb-6">
              <h3 class="text-sm font-medium text-gray-700 dark:text-gray-300 mb-3">IP 白名单</h3>
              <div class="bg-gray-50 dark:bg-gray-700/50 rounded-lg p-3 text-xs">
                <div class="space-y-1 font-mono text-gray-700 dark:text-gray-300">
                  <div>10.0.0.8, 172.16.0.0/12</div>
                  <div>+2</div>
                </div>
              </div>
            </div>

            <!-- 健康状态 -->
            <div class="mb-6">
              <h3 class="text-sm font-medium text-gray-700 dark:text-gray-300 mb-3">健康状态</h3>
              <div class="space-y-3">
                <div class="flex justify-between text-sm">
                  <span class="text-gray-600 dark:text-gray-400">状态</span>
                  <div class="flex items-center gap-1 text-green-600">
                    <icon-carbon:checkmark-filled />
                    <span>在线</span>
                  </div>
                </div>
                <div class="flex justify-between text-sm">
                  <span class="text-gray-600 dark:text-gray-400">最后健康检查</span>
                  <span class="text-gray-900 dark:text-white">2025-05-23 10:24:01</span>
                </div>
                <div class="flex justify-between text-sm">
                  <span class="text-gray-600 dark:text-gray-400">响应时间</span>
                  <span class="text-gray-900 dark:text-white">512ms</span>
                </div>
              </div>
            </div>

            <!-- 操作按钮 -->
            <div class="flex gap-3">
              <NButton type="default" block size="large">
                <template #icon>
                  <icon-carbon:pause />
                </template>
                禁用 Key
              </NButton>
              <NButton type="error" block size="large">
                <template #icon>
                  <icon-carbon:trash-can />
                </template>
                删除 Key
              </NButton>
            </div>
          </div>
        </div>
      </Transition>
    </div>
  </div>
</template>

<style scoped lang="scss">
.mcp-tools-page {
  .stat-card {
    transition: all 0.3s ease;
    
    &:hover {
      transform: translateY(-2px);
      box-shadow: 0 8px 20px rgba(0, 0, 0, 0.08);
    }
  }

  .tool-card {
    transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
    
    &:hover {
      transform: translateY(-4px);
      border-color: #3b82f6;
    }
  }
}

// 侧边栏滑入动画
.slide-left-enter-active,
.slide-left-leave-active {
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.slide-left-enter-from {
  transform: translateX(100%);
  opacity: 0;
}

.slide-left-leave-to {
  transform: translateX(100%);
  opacity: 0;
}

// 滚动条样式
::-webkit-scrollbar {
  width: 6px;
}

::-webkit-scrollbar-track {
  background: transparent;
}

::-webkit-scrollbar-thumb {
  background: #d1d5db;
  border-radius: 3px;

  &:hover {
    background: #9ca3af;
  }
}

:deep(.dark) {
  ::-webkit-scrollbar-thumb {
    background: #4b5563;

    &:hover {
      background: #6b7280;
    }
  }
}
</style>

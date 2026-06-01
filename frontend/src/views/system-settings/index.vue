<script setup lang="ts">
import { ref } from 'vue';

defineOptions({
  name: 'SystemSettings'
});

// 当前选中的导航结构
const selectedNavStructure = ref('default');

// 1. 侧边栏导航状态
const sidebarItems = ref([
  {
    id: 'chat',
    icon: 'carbon:chat',
    label: '对话',
    type: 'default',
    status: 'enabled',
    permissions: ['all_users']
  },
  {
    id: 'knowledge',
    icon: 'carbon:data-base',
    label: '知识库',
    type: 'default',
    status: 'enabled',
    permissions: ['all_users']
  },
  {
    id: 'mcp',
    icon: 'carbon:tool-kit',
    label: 'MCP工具',
    type: 'default',
    status: 'enabled',
    permissions: ['admin', 'power_user']
  },
  {
    id: 'api-key',
    icon: 'carbon:credentials',
    label: 'API Key',
    type: 'default',
    status: 'enabled',
    permissions: ['admin']
  },
  {
    id: 'token-stats',
    icon: 'carbon:chart-line',
    label: 'Token统计',
    type: 'default',
    status: 'enabled',
    permissions: ['admin', 'analyst']
  },
  {
    id: 'settings',
    icon: 'carbon:settings',
    label: '系统设置',
    type: 'default',
    status: 'enabled',
    permissions: ['admin']
  }
]);

// 2. 顶部导航预览
const topNavPreview = ref({
  showLogo: true,
  showSearch: true,
  showNotification: true,
  showHelp: true,
  showUser: true
});

// 3. 面包屑导航示例
const breadcrumbExamples = ref([
  {
    id: '1',
    path: ['对话'],
    description: '一级导航（主导航）'
  },
  {
    id: '2',
    path: ['知识库', '产品知识库'],
    description: '二级导航'
  },
  {
    id: '3',
    path: ['系统设置', '角色与权限', '编辑角色'],
    description: '三级导航'
  },
  {
    id: '4',
    path: ['MCP工具', '智能写作助手', '配置详情'],
    description: '深度嵌套导航'
  }
]);

// 4. 快捷入口与最近访问
const quickAccess = ref([
  { icon: 'carbon:document', label: '新建文档', badge: '', color: 'blue' },
  { icon: 'carbon:cloud-upload', label: '上传文档', badge: '', color: 'green' },
  { icon: 'carbon:chat', label: '创建对话', badge: '', color: 'purple' },
  { icon: 'carbon:settings', label: '系统设置', badge: '', color: 'gray' }
]);

const recentVisits = ref([
  { icon: 'carbon:chat', label: '销售知识问答', time: '10:24', type: 'chat' },
  { icon: 'carbon:document', label: '产品手册解析', time: '昨天', type: 'doc' },
  { icon: 'carbon:tool-kit', label: 'GitLab 仓库助手', time: '昨天', type: 'mcp' },
  { icon: 'carbon:settings', label: 'Token统计', time: '05-19', type: 'stats' }
]);

// 5. 权限展示
const rolePermissions = ref({
  admin: {
    name: '管理员',
    color: 'red',
    permissions: ['对话', '知识库', 'MCP工具', 'API Key', 'Token统计', '系统设置']
  },
  powerUser: {
    name: '普通用户',
    color: 'blue',
    permissions: ['对话', '知识库', 'MCP工具']
  },
  viewer: {
    name: '只读用户',
    color: 'green',
    permissions: ['对话', '知识库']
  }
});

// 6. 导航设计原则
const designPrinciples = ref([
  {
    icon: 'carbon:flow',
    title: '清晰一致',
    description: '全局导航结构保持统一，各模块间跳转逻辑清晰。'
  },
  {
    icon: 'carbon:rocket',
    title: '高效直达',
    description: '常用功能快捷可达，减少用户操作层级和跳转次数。'
  },
  {
    icon: 'carbon:security',
    title: '权限可控',
    description: '基于角色权限控制，按需显示可访问功能项，确保数据安全。'
  },
  {
    icon: 'carbon:document',
    title: '可扩展性',
    description: '模块化结构，提供简洁的扩展机制，随业务需求而增长。'
  }
]);
</script>

<template>
  <div class="system-settings-page flex flex-col h-full bg-gray-50 dark:bg-gray-900">
    <!-- 页面标题 -->
    <div class="flex-shrink-0 bg-white dark:bg-gray-800 border-b border-gray-200 dark:border-gray-700 px-8 py-6">
      <div class="flex items-center justify-between">
        <div>
          <h1 class="text-2xl font-bold text-gray-900 dark:text-white mb-2">导航结构总览</h1>
          <p class="text-sm text-gray-600 dark:text-gray-400">
            管理和预览 KnowFlow 的导航结构，确保用户体验一致高效。角色权限配置，功能模块可见性调整。
          </p>
        </div>
        <div class="flex items-center gap-3">
          <NButton size="large" tertiary>
            <template #icon>
              <icon-carbon:export class="text-lg" />
            </template>
            导出配置
          </NButton>
          <NButton type="primary" size="large">
            <template #icon>
              <icon-carbon:save class="text-lg" />
            </template>
            保存修改
          </NButton>
        </div>
      </div>
    </div>

    <!-- 主体内容 -->
    <div class="flex-1 overflow-y-auto">
      <div class="max-w-1400px mx-auto p-8 space-y-8">
        <!-- 1. 侧边栏导航状态 -->
        <div class="bg-white dark:bg-gray-800 rounded-2xl p-6 border border-gray-200 dark:border-gray-700">
          <div class="flex items-center justify-between mb-6">
            <div>
              <h2 class="text-xl font-bold text-gray-900 dark:text-white mb-1">1. 侧边栏导航状态</h2>
              <p class="text-sm text-gray-600 dark:text-gray-400">一级导航（主导航）</p>
            </div>
            <NButton circle size="small" tertiary>
              <template #icon>
                <icon-carbon:add class="text-lg" />
              </template>
            </NButton>
          </div>

          <div class="grid grid-cols-3 gap-4">
            <div
              v-for="item in sidebarItems"
              :key="item.id"
              class="nav-card bg-gray-50 dark:bg-gray-700/50 rounded-xl p-4 border-2 border-transparent hover:border-blue-500 transition-all cursor-pointer"
            >
              <div class="flex items-start justify-between mb-3">
                <div class="flex items-center gap-3">
                  <div class="w-10 h-10 rounded-lg bg-blue-100 dark:bg-blue-900/30 flex items-center justify-center">
                    <component :is="`icon-${item.icon}`" class="text-blue-600 text-lg" />
                  </div>
                  <div>
                    <h3 class="text-sm font-medium text-gray-900 dark:text-white">{{ item.label }}</h3>
                    <NTag :type="item.status === 'enabled' ? 'success' : 'warning'" size="small">
                      {{ item.status === 'enabled' ? '启用' : '禁用' }}
                    </NTag>
                  </div>
                </div>
                <NSwitch :value="item.status === 'enabled'" size="small" />
              </div>
              <div class="text-xs text-gray-500 dark:text-gray-400">
                权限：{{ item.permissions.join(', ') }}
              </div>
            </div>
          </div>
        </div>

        <!-- 2. 顶部导航栏预览 -->
        <div class="bg-white dark:bg-gray-800 rounded-2xl p-6 border border-gray-200 dark:border-gray-700">
          <h2 class="text-xl font-bold text-gray-900 dark:text-white mb-6">2. 顶部导航栏</h2>
          <div class="bg-gray-50 dark:bg-gray-700/50 rounded-xl p-6">
            <div class="flex items-center justify-between">
              <!-- Logo -->
              <div class="flex items-center gap-3">
                <div class="w-10 h-10 rounded-lg bg-blue-500 flex items-center justify-center">
                  <icon-carbon:cube class="text-white text-xl" />
                </div>
                <span class="text-lg font-bold text-gray-900 dark:text-white">PaiSmart</span>
                <NButton text size="tiny">
                  <template #icon>
                    <icon-carbon:enterprise class="text-blue-500" />
                  </template>
                  默认租户
                  <template #icon-suffix>
                    <icon-carbon:chevron-down class="text-gray-400 text-xs" />
                  </template>
                </NButton>
              </div>

              <!-- 中间搜索 -->
              <div class="flex-1 max-w-500px mx-12">
                <NInput placeholder="搜索知识库、文档、会话或问题" size="medium">
                  <template #prefix>
                    <icon-carbon:search class="text-gray-400" />
                  </template>
                </NInput>
              </div>

              <!-- 右侧操作 -->
              <div class="flex items-center gap-2">
                <NButton circle size="small" tertiary>
                  <template #icon>
                    <icon-carbon:notification />
                  </template>
                </NButton>
                <NButton circle size="small" tertiary>
                  <template #icon>
                    <icon-carbon:help />
                  </template>
                </NButton>
                <div class="flex items-center gap-2 ml-3 pl-3 border-l border-gray-300 dark:border-gray-600">
                  <NAvatar :size="32" round>张</NAvatar>
                  <span class="text-sm font-medium text-gray-900 dark:text-white">张明</span>
                  <icon-carbon:chevron-down class="text-gray-400 text-xs" />
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- 3. 面包屑导航示例 -->
        <div class="bg-white dark:bg-gray-800 rounded-2xl p-6 border border-gray-200 dark:border-gray-700">
          <h2 class="text-xl font-bold text-gray-900 dark:text-white mb-6">3. 面包屑导航示例</h2>
          <div class="space-y-4">
            <div
              v-for="example in breadcrumbExamples"
              :key="example.id"
              class="flex items-center justify-between p-4 bg-gray-50 dark:bg-gray-700/50 rounded-xl"
            >
              <div class="flex items-center gap-2">
                <component
                  v-for="(path, idx) in example.path"
                  :key="idx"
                  :is="idx < example.path.length - 1 ? 'NButton' : 'span'"
                  :text="idx < example.path.length - 1"
                  size="small"
                  :class="idx === example.path.length - 1 ? 'text-sm font-medium text-gray-900 dark:text-white' : ''"
                >
                  {{ path }}
                  <template v-if="idx < example.path.length - 1" #icon-suffix>
                    <icon-carbon:chevron-right class="text-gray-400 text-xs ml-1" />
                  </template>
                </component>
              </div>
              <span class="text-xs text-gray-500 dark:text-gray-400">{{ example.description }}</span>
            </div>
          </div>
        </div>

        <!-- 4. 快捷入口与最近访问 -->
        <div class="grid grid-cols-2 gap-6">
          <!-- 快捷入口 -->
          <div class="bg-white dark:bg-gray-800 rounded-2xl p-6 border border-gray-200 dark:border-gray-700">
            <h2 class="text-xl font-bold text-gray-900 dark:text-white mb-6">4. 快捷入口</h2>
            <div class="grid grid-cols-2 gap-3">
              <div
                v-for="item in quickAccess"
                :key="item.label"
                class="quick-access-card flex items-center gap-3 p-4 bg-gray-50 dark:bg-gray-700/50 rounded-xl cursor-pointer hover:shadow-md transition-all"
              >
                <div :class="[
                  'w-10 h-10 rounded-lg flex items-center justify-center',
                  `bg-${item.color}-100 dark:bg-${item.color}-900/30`
                ]">
                  <component :is="`icon-${item.icon}`" :class="`text-${item.color}-600 text-lg`" />
                </div>
                <span class="text-sm font-medium text-gray-900 dark:text-white">{{ item.label }}</span>
              </div>
            </div>
          </div>

          <!-- 最近访问 -->
          <div class="bg-white dark:bg-gray-800 rounded-2xl p-6 border border-gray-200 dark:border-gray-700">
            <div class="flex items-center justify-between mb-6">
              <h2 class="text-xl font-bold text-gray-900 dark:text-white">最近访问</h2>
              <NButton text size="small" type="primary">
                查看全部
                <template #icon-suffix>
                  <icon-carbon:arrow-right class="text-xs" />
                </template>
              </NButton>
            </div>
            <div class="space-y-2">
              <div
                v-for="visit in recentVisits"
                :key="visit.label"
                class="flex items-center justify-between p-3 bg-gray-50 dark:bg-gray-700/50 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-700 cursor-pointer transition-all"
              >
                <div class="flex items-center gap-3">
                  <component :is="`icon-${visit.icon}`" class="text-blue-500 text-lg" />
                  <span class="text-sm text-gray-900 dark:text-white">{{ visit.label }}</span>
                </div>
                <span class="text-xs text-gray-500 dark:text-gray-400">{{ visit.time }}</span>
              </div>
            </div>
          </div>
        </div>

        <!-- 5. 权限展示（按角色） -->
        <div class="bg-white dark:bg-gray-800 rounded-2xl p-6 border border-gray-200 dark:border-gray-700">
          <h2 class="text-xl font-bold text-gray-900 dark:text-white mb-6">5. 权限展示示例（按角色）</h2>
          <div class="space-y-6">
            <!-- 管理员权限 -->
            <div class="permission-section">
              <div class="flex items-center gap-2 mb-4">
                <icon-carbon:user-admin class="text-red-500 text-xl" />
                <h3 class="text-lg font-bold text-gray-900 dark:text-white">管理员权限</h3>
                <NTag type="error" size="small">【拥有全部权限】</NTag>
              </div>
              <div class="grid grid-cols-6 gap-3">
                <div
                  v-for="perm in rolePermissions.admin.permissions"
                  :key="perm"
                  class="flex flex-col items-center gap-2 p-3 bg-green-50 dark:bg-green-900/20 rounded-lg"
                >
                  <icon-carbon:checkmark-filled class="text-green-500 text-xl" />
                  <span class="text-xs font-medium text-gray-900 dark:text-white">{{ perm }}</span>
                </div>
              </div>
            </div>

            <!-- 普通用户权限 -->
            <div class="permission-section">
              <div class="flex items-center gap-2 mb-4">
                <icon-carbon:user class="text-blue-500 text-xl" />
                <h3 class="text-lg font-bold text-gray-900 dark:text-white">普通用户权限</h3>
                <NTag type="default" size="small">【受限权限】</NTag>
              </div>
              <div class="grid grid-cols-6 gap-3">
                <div
                  v-for="perm in sidebarItems"
                  :key="perm.id"
                  :class="[
                    'flex flex-col items-center gap-2 p-3 rounded-lg',
                    rolePermissions.powerUser.permissions.includes(perm.label)
                      ? 'bg-green-50 dark:bg-green-900/20'
                      : 'bg-gray-100 dark:bg-gray-700/50'
                  ]"
                >
                  <component
                    :is="rolePermissions.powerUser.permissions.includes(perm.label) ? 'icon-carbon:checkmark-filled' : 'icon-carbon:locked'"
                    :class="[
                      'text-xl',
                      rolePermissions.powerUser.permissions.includes(perm.label) ? 'text-green-500' : 'text-gray-400'
                    ]"
                  />
                  <span class="text-xs font-medium text-gray-900 dark:text-white">{{ perm.label }}</span>
                </div>
              </div>
            </div>

            <!-- 只读用户权限 -->
            <div class="permission-section">
              <div class="flex items-center gap-2 mb-4">
                <icon-carbon:view class="text-gray-500 text-xl" />
                <h3 class="text-lg font-bold text-gray-900 dark:text-white">只读用户权限</h3>
                <NTag type="warning" size="small">【仅查看权限】</NTag>
              </div>
              <div class="grid grid-cols-6 gap-3">
                <div
                  v-for="perm in sidebarItems"
                  :key="perm.id"
                  :class="[
                    'flex flex-col items-center gap-2 p-3 rounded-lg',
                    rolePermissions.viewer.permissions.includes(perm.label)
                      ? 'bg-green-50 dark:bg-green-900/20'
                      : 'bg-gray-100 dark:bg-gray-700/50'
                  ]"
                >
                  <component
                    :is="rolePermissions.viewer.permissions.includes(perm.label) ? 'icon-carbon:view' : 'icon-carbon:locked'"
                    :class="[
                      'text-xl',
                      rolePermissions.viewer.permissions.includes(perm.label) ? 'text-green-500' : 'text-gray-400'
                    ]"
                  />
                  <span class="text-xs font-medium text-gray-900 dark:text-white">{{ perm.label }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- 6. 导航设计原则 -->
        <div class="bg-white dark:bg-gray-800 rounded-2xl p-6 border border-gray-200 dark:border-gray-700">
          <h2 class="text-xl font-bold text-gray-900 dark:text-white mb-6">6. 导航设计原则</h2>
          <div class="grid grid-cols-2 gap-6">
            <div
              v-for="principle in designPrinciples"
              :key="principle.title"
              class="principle-card bg-gradient-to-br from-blue-50 to-cyan-50 dark:from-blue-900/20 dark:to-cyan-900/20 rounded-xl p-6 border border-blue-100 dark:border-blue-800"
            >
              <div class="flex items-start gap-4">
                <div class="w-12 h-12 rounded-xl bg-blue-500 flex items-center justify-center flex-shrink-0">
                  <component :is="`icon-${principle.icon}`" class="text-white text-2xl" />
                </div>
                <div>
                  <h3 class="text-lg font-bold text-gray-900 dark:text-white mb-2">{{ principle.title }}</h3>
                  <p class="text-sm text-gray-600 dark:text-gray-400">{{ principle.description }}</p>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- 底部说明 -->
        <div class="bg-blue-50 dark:bg-blue-900/20 rounded-2xl p-6 border border-blue-200 dark:border-blue-800">
          <div class="flex items-start gap-4">
            <icon-carbon:information class="text-blue-600 text-2xl flex-shrink-0 mt-0.5" />
            <div>
              <h3 class="text-base font-bold text-blue-900 dark:text-blue-100 mb-2">导航结构配置说明</h3>
              <p class="text-sm text-blue-700 dark:text-blue-300 leading-relaxed">
                导航结构需要变更时，请联系管理员。每次修改需详细测试所有用户角色的功能访问权限，确保系统可用性的同时，防止权限泄露。请审慎变更。
              </p>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.system-settings-page {
  .nav-card,
  .quick-access-card,
  .principle-card {
    transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
    
    &:hover {
      transform: translateY(-2px);
    }
  }

  .permission-section {
    padding: 1.5rem;
    background: rgba(249, 250, 251, 0.5);
    border-radius: 16px;
    
    :deep(.dark) & {
      background: rgba(31, 41, 55, 0.3);
    }
  }
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

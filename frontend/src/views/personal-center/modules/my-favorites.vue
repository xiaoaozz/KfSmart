<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { fetchDeleteFavorite, fetchGetFavorites, fetchUpdateFavoriteStarred } from '@/service/api';

defineOptions({ name: 'MyFavorites' });

type FavType = 'all' | Api.User.FavoriteType;

const activeTab = ref<FavType>('all');
const searchText = ref('');

const loading = ref(false);
const favorites = ref<Api.User.FavoriteItem[]>([]);

const filteredFavorites = computed(() =>
  favorites.value.filter(item => {
    const matchTab = activeTab.value === 'all' || item.type === activeTab.value;
    const matchSearch = !searchText.value || item.title.includes(searchText.value) || (item.desc || '').includes(searchText.value);
    return matchTab && matchSearch;
  })
);

async function loadFavorites() {
  loading.value = true;
  const { data, error } = await fetchGetFavorites();
  loading.value = false;
  if (!error && data) {
    favorites.value = data;
  }
}

async function removeFavorite(id: number) {
  const { error } = await fetchDeleteFavorite(id);
  if (error) return;
  const idx = favorites.value.findIndex(f => f.id === id);
  if (idx > -1) {
    favorites.value.splice(idx, 1);
    window.$message?.success('已从收藏中移除');
  }
}

async function toggleStar(item: Api.User.FavoriteItem) {
  const nextStarred = !item.starred;
  const { data, error } = await fetchUpdateFavoriteStarred(item.id, nextStarred);
  if (error) return;
  item.starred = data?.starred ?? nextStarred;
}

const typeConfig: Record<string, { label: string; icon: string; color: string; bg: string; tagType: 'success' | 'info' | 'warning' | 'default' }> = {
  chat: { label: '对话', icon: 'carbon:chat', color: 'text-purple-500', bg: 'bg-purple-50 dark:bg-purple-900/20', tagType: 'info' },
  agent: { label: 'Agent', icon: 'carbon:bot', color: 'text-indigo-500', bg: 'bg-indigo-50 dark:bg-indigo-900/20', tagType: 'info' },
  workflow: { label: '工作流', icon: 'carbon:flow', color: 'text-cyan-500', bg: 'bg-cyan-50 dark:bg-cyan-900/20', tagType: 'info' },
  document: { label: '文档', icon: 'carbon:document', color: 'text-blue-500', bg: 'bg-blue-50 dark:bg-blue-900/20', tagType: 'default' },
  knowledge: { label: '知识库', icon: 'carbon:folder', color: 'text-green-500', bg: 'bg-green-50 dark:bg-green-900/20', tagType: 'success' },
  prompt: { label: 'Prompt', icon: 'carbon:text-creation', color: 'text-amber-500', bg: 'bg-amber-50 dark:bg-amber-900/20', tagType: 'warning' },
  skill: { label: '技能', icon: 'carbon:skill-level', color: 'text-rose-500', bg: 'bg-rose-50 dark:bg-rose-900/20', tagType: 'default' },
  mcp_tool: { label: 'MCP 工具', icon: 'carbon:tool-kit', color: 'text-teal-500', bg: 'bg-teal-50 dark:bg-teal-900/20', tagType: 'default' },
  model: { label: '模型', icon: 'carbon:machine-learning-model', color: 'text-slate-500', bg: 'bg-slate-50 dark:bg-slate-900/20', tagType: 'default' }
};

const tabOptions = [
  { key: 'all' as FavType, label: '全部' },
  { key: 'chat' as FavType, label: '对话' },
  { key: 'agent' as FavType, label: 'Agent' },
  { key: 'workflow' as FavType, label: '工作流' },
  { key: 'knowledge' as FavType, label: '知识库' },
  { key: 'document' as FavType, label: '文档' },
];

function getTypeConfig(type: string) {
  return typeConfig[type] || {
    label: type,
    icon: 'carbon:bookmark',
    color: 'text-gray-500',
    bg: 'bg-gray-50 dark:bg-gray-900/20',
    tagType: 'default' as const
  };
}

function formatDate(value: string) {
  if (!value) return '--';
  return value.replace('T', ' ').substring(0, 16);
}

onMounted(loadFavorites);
</script>

<template>
  <div class="favorites-module space-y-4">
    <!-- 搜索 + tab筛选 -->
    <div class="flex items-center gap-3">
      <NInput
        v-model:value="searchText"
        placeholder="搜索收藏内容..."
        clearable
        size="small"
        class="flex-1"
      >
        <template #prefix><icon-carbon:search class="text-gray-400" /></template>
      </NInput>
      <NButtonGroup size="small">
        <NButton
          v-for="tab in tabOptions"
          :key="tab.key"
          :type="activeTab === tab.key ? 'primary' : 'default'"
          ghost
          @click="activeTab = tab.key"
        >
          {{ tab.label }}
        </NButton>
      </NButtonGroup>
    </div>

    <NSpin :show="loading">
    <!-- 收藏列表 -->
    <div v-if="filteredFavorites.length > 0" class="space-y-2">
      <div
        v-for="item in filteredFavorites"
        :key="item.id"
        class="group flex items-start gap-3 p-3 bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 hover:border-blue-300 dark:hover:border-blue-700 transition-colors"
      >
        <div class="w-9 h-9 rounded-lg flex items-center justify-center flex-shrink-0 mt-0.5" :class="getTypeConfig(item.type).bg">
          <component :is="getTypeConfig(item.type).icon" class="text-base" :class="getTypeConfig(item.type).color" />
        </div>
        <div class="flex-1 min-w-0">
          <div class="flex items-start justify-between gap-2">
            <div class="min-w-0">
              <div class="flex items-center gap-2 mb-0.5">
                <span class="text-sm font-medium text-gray-800 dark:text-gray-100 truncate">{{ item.title }}</span>
                <NTag size="tiny" :type="getTypeConfig(item.type).tagType">{{ getTypeConfig(item.type).label }}</NTag>
              </div>
              <p class="text-xs text-gray-400 line-clamp-2">{{ item.desc || '暂无描述' }}</p>
            </div>
            <div class="flex items-center gap-1 flex-shrink-0 opacity-0 group-hover:opacity-100 transition-opacity">
              <NButton
                text size="small"
                :type="item.starred ? 'warning' : 'default'"
                @click="toggleStar(item)"
              >
                <icon-carbon:star-filled v-if="item.starred" class="text-yellow-400" />
                <icon-carbon:star v-else />
              </NButton>
              <NButton text size="small" type="error" @click="removeFavorite(item.id)">
                <icon-carbon:trash-can />
              </NButton>
            </div>
          </div>
          <div class="flex items-center gap-2 mt-1.5 text-xs text-gray-400">
            <span>{{ formatDate(item.updatedAt || item.createdAt) }}</span>
            <span v-if="item.meta">· {{ item.meta }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 空状态 -->
    <div v-else class="flex flex-col items-center justify-center py-12 text-gray-400">
      <icon-carbon:bookmark class="text-4xl mb-2 opacity-50" />
      <p class="text-sm">暂无收藏内容</p>
      <p class="text-xs mt-1">在对话或文档页面点击收藏图标即可添加</p>
    </div>
    </NSpin>
  </div>
</template>

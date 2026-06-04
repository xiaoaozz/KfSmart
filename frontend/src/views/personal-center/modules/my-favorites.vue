<script setup lang="ts">
import { ref, computed } from 'vue';

defineOptions({ name: 'MyFavorites' });

type FavType = 'all' | 'chat' | 'document' | 'knowledge';

const activeTab = ref<FavType>('all');
const searchText = ref('');

interface FavoriteItem {
  id: number;
  type: 'chat' | 'document' | 'knowledge';
  title: string;
  desc: string;
  time: string;
  meta?: string;
  starred?: boolean;
}

const favorites = ref<FavoriteItem[]>([
  { id: 1, type: 'chat', title: '如何优化向量检索性能', desc: '关于 RAG 系统中向量检索优化的详细讨论，涵盖索引策略和相似度计算...', time: '2024-06-04', meta: '18条消息', starred: true },
  { id: 2, type: 'document', title: 'PaiSmart 技术架构文档.pdf', desc: '系统整体技术架构说明，包括前后端设计、数据库选型、向量存储方案...', time: '2024-06-03', meta: '2.4 MB', starred: true },
  { id: 3, type: 'knowledge', title: '大模型应用开发知识库', desc: '收录了 LLM 应用开发相关的最佳实践、提示词工程技巧和常见问题解答', time: '2024-06-02', meta: '56份文档', starred: false },
  { id: 4, type: 'chat', title: 'Spring Boot 微服务架构实践', desc: '与 AI 助手探讨微服务拆分策略、服务发现、配置中心等核心问题...', time: '2024-06-01', meta: '32条消息', starred: false },
  { id: 5, type: 'document', title: 'Milvus 向量数据库使用指南.md', desc: 'Milvus 集合管理、索引配置、查询优化的完整使用说明', time: '2024-05-30', meta: '680 KB', starred: false },
  { id: 6, type: 'knowledge', title: 'Java 后端开发规范库', desc: '团队内部 Java 开发规范，包含代码审查要点和常见反例分析', time: '2024-05-28', meta: '23份文档', starred: true },
]);

const filteredFavorites = computed(() =>
  favorites.value.filter(item => {
    const matchTab = activeTab.value === 'all' || item.type === activeTab.value;
    const matchSearch = !searchText.value || item.title.includes(searchText.value) || item.desc.includes(searchText.value);
    return matchTab && matchSearch;
  })
);

function removeFavorite(id: number) {
  const idx = favorites.value.findIndex(f => f.id === id);
  if (idx > -1) {
    favorites.value.splice(idx, 1);
    window.$message?.success('已从收藏中移除');
  }
}

function toggleStar(item: FavoriteItem) {
  item.starred = !item.starred;
}

const typeConfig: Record<string, { label: string; icon: string; color: string; bg: string; tagType: 'success' | 'info' | 'default' }> = {
  chat: { label: '对话', icon: 'carbon:chat', color: 'text-purple-500', bg: 'bg-purple-50 dark:bg-purple-900/20', tagType: 'info' },
  document: { label: '文档', icon: 'carbon:document', color: 'text-blue-500', bg: 'bg-blue-50 dark:bg-blue-900/20', tagType: 'default' },
  knowledge: { label: '知识库', icon: 'carbon:folder', color: 'text-green-500', bg: 'bg-green-50 dark:bg-green-900/20', tagType: 'success' },
};

const tabOptions = [
  { key: 'all' as FavType, label: '全部' },
  { key: 'chat' as FavType, label: '对话' },
  { key: 'document' as FavType, label: '文档' },
  { key: 'knowledge' as FavType, label: '知识库' },
];
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

    <!-- 收藏列表 -->
    <div v-if="filteredFavorites.length > 0" class="space-y-2">
      <div
        v-for="item in filteredFavorites"
        :key="item.id"
        class="group flex items-start gap-3 p-3 bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 hover:border-blue-300 dark:hover:border-blue-700 transition-colors"
      >
        <div class="w-9 h-9 rounded-lg flex items-center justify-center flex-shrink-0 mt-0.5" :class="typeConfig[item.type].bg">
          <component :is="typeConfig[item.type].icon" class="text-base" :class="typeConfig[item.type].color" />
        </div>
        <div class="flex-1 min-w-0">
          <div class="flex items-start justify-between gap-2">
            <div class="min-w-0">
              <div class="flex items-center gap-2 mb-0.5">
                <span class="text-sm font-medium text-gray-800 dark:text-gray-100 truncate">{{ item.title }}</span>
                <NTag size="tiny" :type="typeConfig[item.type].tagType">{{ typeConfig[item.type].label }}</NTag>
              </div>
              <p class="text-xs text-gray-400 line-clamp-2">{{ item.desc }}</p>
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
            <span>{{ item.time }}</span>
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
  </div>
</template>

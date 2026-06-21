<script setup lang="ts">
import { NButton, NEmpty, NInput, NSpin, NTag } from 'naive-ui';
import { fetchAgentModels } from '@/service/api/resource';
import FavoriteButton from '@/components/common/favorite-button.vue';
import ListPagination from '@/components/common/list-pagination.vue';
import { DEFAULT_PAGE_SIZE } from '@/constants/common';

const loading = ref(false);
const keyword = ref('');
const activeCategory = ref('全部');
const models = ref<Api.AgentCenter.ModelConfig[]>([]);
const currentPage = ref(1);
const pageSize = ref(DEFAULT_PAGE_SIZE);

const categories = computed(() => {
  const counter = new Map<string, number>();
  models.value.forEach(item => {
    const category = item.category || '未分类';
    counter.set(category, (counter.get(category) || 0) + 1);
  });

  return [
    { name: '全部', count: models.value.length },
    ...Array.from(counter.entries()).map(([name, count]) => ({ name, count }))
  ];
});

const filteredModels = computed(() => {
  const searchText = keyword.value.trim().toLowerCase();

  return models.value.filter(item => {
    const matchedCategory = activeCategory.value === '全部' || (item.category || '未分类') === activeCategory.value;
    const matchedKeyword =
      !searchText ||
      [item.name, item.modelName, item.providerLabel, item.description, ...(item.tags || [])]
        .filter(Boolean)
        .some(text => String(text).toLowerCase().includes(searchText));

    return matchedCategory && matchedKeyword;
  });
});

const paginatedModels = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value;
  return filteredModels.value.slice(start, start + pageSize.value);
});

const modelPageCount = computed(() => Math.max(1, Math.ceil(filteredModels.value.length / pageSize.value)));

function selectCategory(category: string) {
  activeCategory.value = category;
  currentPage.value = 1;
}

function handlePageSizeChange(size: number) {
  pageSize.value = size;
  currentPage.value = 1;
}

async function loadModels() {
  loading.value = true;
  const { data, error } = await fetchAgentModels();
  if (!error && data) {
    models.value = data;
    if (!categories.value.some(item => item.name === activeCategory.value)) {
      activeCategory.value = '全部';
    }
    if (currentPage.value > modelPageCount.value) {
      currentPage.value = modelPageCount.value;
    }
  }
  loading.value = false;
}

watch(keyword, () => {
  currentPage.value = 1;
});

function getProviderClass(provider: string) {
  const normalized = provider?.toLowerCase();
  return (
    {
      deepseek: 'from-cyan-500 to-blue-600',
      openai: 'from-emerald-500 to-teal-600',
      qwen: 'from-blue-500 to-indigo-600',
      zhipu: 'from-amber-500 to-orange-600',
      ernie: 'from-rose-500 to-pink-600',
      anthropic: 'from-violet-500 to-purple-600',
      ollama: 'from-slate-600 to-zinc-800'
    }[normalized] || 'from-gray-600 to-gray-800'
  );
}

onMounted(loadModels);
</script>

<template>
  <div class="h-full flex bg-[#f5f7fa] dark:bg-[#101014]">
    <aside class="w-180px flex flex-shrink-0 flex-col border-r border-gray-200 bg-white dark:border-gray-700 dark:bg-[#18181c]">
      <div class="px-4 py-5">
        <h2 class="text-sm font-semibold text-gray-800 dark:text-gray-100">模型分类</h2>
      </div>

      <div class="flex-1 overflow-y-auto px-2">
        <button
          v-for="category in categories"
          :key="category.name"
          class="mb-0.5 flex w-full items-center justify-between rounded-lg px-3 py-2 text-left text-sm transition-all"
          :class="
            activeCategory === category.name
              ? 'bg-primary-50 text-primary-600 font-medium dark:bg-primary-900/20 dark:text-primary-400'
              : 'text-gray-600 hover:bg-gray-50 dark:text-gray-400 dark:hover:bg-gray-800'
          "
          @click="selectCategory(category.name)"
        >
          <span class="flex min-w-0 items-center gap-2">
            <icon-carbon:catalog v-if="category.name === '全部'" class="shrink-0 text-base" />
            <icon-carbon:tag v-else class="shrink-0 text-base" />
            <span class="truncate">{{ category.name }}</span>
          </span>
          <span class="text-xs opacity-60">{{ category.count }}</span>
        </button>
      </div>
    </aside>

    <main class="min-w-0 flex flex-1 flex-col">
      <div class="border-b border-gray-100 bg-white px-5 py-3 dark:border-gray-700 dark:bg-[#18181c]">
        <div class="flex flex-wrap items-center justify-between gap-3">
          <div class="min-w-0">
            <h1 class="truncate text-sm font-semibold text-gray-800 dark:text-gray-100">模型广场</h1>
            <p class="mt-0.5 text-xs text-gray-500 dark:text-gray-400">当前分类：{{ activeCategory }}</p>
          </div>
        <div class="flex items-center gap-3">
          <NInput v-model:value="keyword" class="w-240px" clearable placeholder="搜索模型、标签或供应商" size="small">
            <template #prefix>
              <icon-carbon:search />
            </template>
          </NInput>
          <NButton size="small" :loading="loading" @click="loadModels">
            <template #icon>
              <icon-carbon:renew />
            </template>
            刷新
          </NButton>
        </div>
      </div>
      </div>

      <div class="min-h-0 flex-1 overflow-y-auto p-4">
        <NSpin :show="loading">
          <div v-if="filteredModels.length" class="grid grid-cols-1 gap-3 xl:grid-cols-2 2xl:grid-cols-3">
            <article
              v-for="model in paginatedModels"
              :key="model.id"
              class="rounded-lg border border-gray-200 bg-white p-5 shadow-sm transition-shadow hover:shadow-md dark:border-gray-800 dark:bg-gray-900"
            >
              <div class="mb-4 flex items-start gap-3">
                <div
                  class="flex h-12 w-12 shrink-0 items-center justify-center rounded-lg bg-gradient-to-br text-sm font-bold text-white"
                  :class="getProviderClass(model.provider)"
                >
                  {{ model.icon || 'LLM' }}
                </div>
                <div class="min-w-0 flex-1">
                  <div class="flex items-center gap-2">
                    <h3 class="truncate text-base font-semibold text-gray-950 dark:text-white">{{ model.modelName }}</h3>
                    <NTag v-if="model.active" type="success" size="small" round>激活中</NTag>
                  </div>
                  <p class="mt-1 truncate text-xs text-gray-500 dark:text-gray-400">{{ model.name }}</p>
                </div>
                <FavoriteButton
                  type="model"
                  :target-id="model.modelName || model.name || model.id"
                  :title="model.modelName || model.name"
                  :description="model.description"
                  :meta="model.providerLabel || model.provider || ''"
                />
              </div>

              <p class="mb-4 line-clamp-3 min-h-15 text-sm leading-5 text-gray-600 dark:text-gray-300">
                {{ model.description }}
              </p>

              <div class="mb-4 flex flex-wrap gap-2">
                <NTag v-for="tag in model.tags" :key="tag" size="small" round>{{ tag }}</NTag>
              </div>

              <div class="grid grid-cols-3 gap-2 border-t border-gray-100 pt-4 text-xs dark:border-gray-800">
                <div>
                  <p class="text-gray-400">温度</p>
                  <p class="mt-1 font-medium text-gray-800 dark:text-gray-100">{{ model.temperature ?? '-' }}</p>
                </div>
                <div>
                  <p class="text-gray-400">Tokens</p>
                  <p class="mt-1 font-medium text-gray-800 dark:text-gray-100">{{ model.maxTokens ?? '-' }}</p>
                </div>
                <div>
                  <p class="text-gray-400">Top-P</p>
                  <p class="mt-1 font-medium text-gray-800 dark:text-gray-100">{{ model.topP ?? '-' }}</p>
                </div>
              </div>
            </article>
          </div>

          <div v-else class="flex min-h-360px items-center justify-center">
            <NEmpty description="暂无匹配模型" />
          </div>
        </NSpin>
      </div>
      <ListPagination
        v-model:page="currentPage"
        v-model:page-size="pageSize"
        :page-count="modelPageCount"
        :item-count="filteredModels.length"
        :disabled="loading"
        class="dark:bg-[#18181c]"
        @update:page-size="handlePageSizeChange"
      />
    </main>
  </div>
</template>

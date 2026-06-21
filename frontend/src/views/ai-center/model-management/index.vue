<script setup lang="ts">
import { NButton, NEmpty, NInput, NSpin, NTag } from 'naive-ui';
import { fetchAgentModels } from '@/service/api/resource';
import FavoriteButton from '@/components/common/favorite-button.vue';

const loading = ref(false);
const keyword = ref('');
const activeCategory = ref('全部');
const models = ref<Api.AgentCenter.ModelConfig[]>([]);

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

async function loadModels() {
  loading.value = true;
  const { data, error } = await fetchAgentModels();
  if (!error && data) {
    models.value = data;
    if (!categories.value.some(item => item.name === activeCategory.value)) {
      activeCategory.value = '全部';
    }
  }
  loading.value = false;
}

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
  <div class="flex h-full overflow-hidden bg-[#f6f7fb] dark:bg-gray-950">
    <aside class="w-64 shrink-0 border-r border-gray-200 bg-white px-4 py-6 dark:border-gray-800 dark:bg-gray-900">
      <div class="mb-5 px-2">
        <h1 class="text-xl font-semibold text-gray-950 dark:text-white">模型广场</h1>
        <p class="mt-1 text-xs leading-5 text-gray-500 dark:text-gray-400">模型来源于 API Key 配置，添加配置后自动出现在广场。</p>
      </div>

      <div class="space-y-1">
        <button
          v-for="category in categories"
          :key="category.name"
          class="flex w-full items-center justify-between rounded-lg px-3 py-2 text-left text-sm transition-colors"
          :class="
            activeCategory === category.name
              ? 'bg-blue-50 text-blue-700 dark:bg-blue-950/50 dark:text-blue-200'
              : 'text-gray-600 hover:bg-gray-100 dark:text-gray-300 dark:hover:bg-gray-800'
          "
          @click="activeCategory = category.name"
        >
          <span class="font-medium">{{ category.name }}</span>
          <span class="text-xs opacity-70">{{ category.count }}</span>
        </button>
      </div>
    </aside>

    <main class="min-w-0 flex-1 overflow-y-auto px-8 py-6">
      <div class="mb-6 flex flex-wrap items-center justify-between gap-3">
        <div>
          <h2 class="text-2xl font-bold text-gray-950 dark:text-white">{{ activeCategory }}</h2>
          <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">查看已接入模型的能力分类、用途说明和运行状态。</p>
        </div>
        <div class="flex items-center gap-3">
          <NInput v-model:value="keyword" class="w-72" clearable placeholder="搜索模型、标签或供应商">
            <template #prefix>
              <icon-carbon:search />
            </template>
          </NInput>
          <NButton :loading="loading" @click="loadModels">
            <template #icon>
              <icon-carbon:renew />
            </template>
            刷新
          </NButton>
        </div>
      </div>

      <NSpin :show="loading">
        <div v-if="filteredModels.length" class="grid grid-cols-1 gap-4 xl:grid-cols-2 2xl:grid-cols-3">
          <article
            v-for="model in filteredModels"
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

        <div v-else class="flex min-h-80 items-center justify-center rounded-lg border border-dashed border-gray-200 bg-white dark:border-gray-800 dark:bg-gray-900">
          <NEmpty description="暂无匹配模型" />
        </div>
      </NSpin>
    </main>
  </div>
</template>

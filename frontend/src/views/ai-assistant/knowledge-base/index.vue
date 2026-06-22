<script setup lang="tsx">
import { NButton, NDivider, NEmpty, NInput, NModal, NScrollbar, NSpin, NTag } from 'naive-ui';
import debounce from 'lodash-es/debounce';
import { DEFAULT_PAGE_SIZE } from '@/constants/common';
import {
  fetchDeleteKnowledgeBase,
  fetchGetKnowledgeBaseStats,
  fetchGetKnowledgeBases
} from '@/service/api/knowledge-base';
import ListPagination from '@/components/common/list-pagination.vue';
import FavoriteButton from '@/components/common/favorite-button.vue';
import CreateKbDialog from './modules/create-kb-dialog.vue';

// --------- 统计数字 ---------
const stats = ref({
  knowledgeBaseCount: 0,
  documentCount: 0,
  totalSize: 0,
  chunkCount: 0
});

// --------- 知识库列表 ---------
const knowledgeBases = ref<Api.KnowledgeBase.KnowledgeBaseInfo[]>([]);
const loading = ref(false);
const searchKeyword = ref('');
const selectedKbId = ref('');

const selectedKnowledgeBase = computed(
  () => knowledgeBases.value.find(item => item.kbId === selectedKbId.value) || knowledgeBases.value[0] || null
);

const categoryMode = ref<'visibility' | 'status' | 'org'>('visibility');
const activeCategory = ref('全部');

const categoryList = computed(() => {
  const values = knowledgeBases.value.map(item => {
    if (categoryMode.value === 'visibility') return item.isPublic ? '公开' : '私有';
    if (categoryMode.value === 'status') return getStatusText(item.status);
    return item.orgTag || '未分组';
  });
  return [...new Set(values.filter(Boolean))];
});

const categoryCounts = computed(() => {
  const counts: Record<string, number> = { '全部': knowledgeBases.value.length };
  knowledgeBases.value.forEach(item => {
    const key =
      categoryMode.value === 'visibility'
        ? item.isPublic
          ? '公开'
          : '私有'
        : categoryMode.value === 'status'
          ? getStatusText(item.status)
          : item.orgTag || '未分组';
    counts[key] = (counts[key] || 0) + 1;
  });
  return counts;
});

const filteredKnowledgeBases = computed(() => {
  if (activeCategory.value === '全部') return knowledgeBases.value;
  return knowledgeBases.value.filter(item => {
    const key =
      categoryMode.value === 'visibility'
        ? item.isPublic
          ? '公开'
          : '私有'
        : categoryMode.value === 'status'
          ? getStatusText(item.status)
          : item.orgTag || '未分组';
    return key === activeCategory.value;
  });
});

function switchCategoryMode(mode: 'visibility' | 'status' | 'org') {
  categoryMode.value = mode;
  activeCategory.value = '全部';
}

function handleCategoryClick(category: string) {
  activeCategory.value = category;
  const first = filteredKnowledgeBases.value[0];
  if (first) selectedKbId.value = first.kbId;
}

// --------- 分页 ---------
const currentPage = ref(1);
const pageSize = ref(DEFAULT_PAGE_SIZE);
const totalCount = ref(0);

// --------- 新建/编辑知识库 ---------
const createKbVisible = ref(false);
const editKbVisible = ref(false);
const editingKb = ref<Api.KnowledgeBase.KnowledgeBaseInfo | null>(null);

function handleEdit(row: Api.KnowledgeBase.KnowledgeBaseInfo) {
  editingKb.value = row;
  editKbVisible.value = true;
}

function selectKnowledgeBase(row: Api.KnowledgeBase.KnowledgeBaseInfo) {
  selectedKbId.value = row.kbId;
}

// --------- 删除知识库弹窗 ---------
const deleteModalVisible = ref(false);
const deletingKb = ref<Api.KnowledgeBase.KnowledgeBaseInfo | null>(null);
const deleteCountdown = ref(0);
const deleteLoading = ref(false);
let countdownTimer: ReturnType<typeof setInterval> | null = null;

function handleDeleteClick(row: Api.KnowledgeBase.KnowledgeBaseInfo) {
  deletingKb.value = row;
  deleteCountdown.value = 3;
  deleteModalVisible.value = true;
  // 开始倒计时
  countdownTimer = setInterval(() => {
    deleteCountdown.value -= 1;
    if (deleteCountdown.value <= 0) {
      clearInterval(countdownTimer!);
      countdownTimer = null;
    }
  }, 1000);
}

function handleDeleteCancel() {
  deleteModalVisible.value = false;
  deletingKb.value = null;
  if (countdownTimer) {
    clearInterval(countdownTimer);
    countdownTimer = null;
  }
  deleteCountdown.value = 0;
}

async function handleDeleteConfirm() {
  if (!deletingKb.value || deleteCountdown.value > 0) return;
  deleteLoading.value = true;
  try {
    const { error } = await fetchDeleteKnowledgeBase(deletingKb.value.kbId);
    if (!error) {
      window.$message?.success('删除成功');
      deleteModalVisible.value = false;
      deletingKb.value = null;
      await loadKnowledgeBases();
    }
  } finally {
    deleteLoading.value = false;
  }
}

// --------- 搜索防抖 ---------
const debouncedSearch = debounce(() => {
  currentPage.value = 1;
  loadKnowledgeBases();
}, 300);

// --------- 加载数据 ---------
async function loadKnowledgeBases() {
  loading.value = true;
  try {
    const [listRes, statsRes] = await Promise.all([
      fetchGetKnowledgeBases({
        keyword: searchKeyword.value.trim() || undefined,
        page: currentPage.value,
        size: pageSize.value
      }),
      fetchGetKnowledgeBaseStats()
    ]);

    const { error, data } = listRes;
    if (!error && data) {
      const records = data.records || data.content || data.data || [];
      knowledgeBases.value = records;
      totalCount.value = data.totalElements ?? data.total ?? records.length;
      if (!records.some(item => item.kbId === selectedKbId.value)) {
        selectedKbId.value = records[0]?.kbId || '';
      }
    }

    if (!statsRes.error && statsRes.data) {
      stats.value.knowledgeBaseCount = statsRes.data.knowledgeBaseCount || 0;
      stats.value.documentCount = statsRes.data.documentCount || 0;
      stats.value.totalSize = statsRes.data.totalSize || 0;
      stats.value.chunkCount = statsRes.data.chunkCount || 0;
    }
  } finally {
    loading.value = false;
  }
}

function handlePageChange(page: number) {
  currentPage.value = page;
  loadKnowledgeBases();
}

function handlePageSizeChange(size: number) {
  pageSize.value = size;
  currentPage.value = 1;
  loadKnowledgeBases();
}

onMounted(loadKnowledgeBases);

// --------- 格式化存储大小 ---------
function formatSize(bytes: number) {
  if (!bytes || bytes === 0) return '0 B';
  const units = ['B', 'KB', 'MB', 'GB', 'TB'];
  let i = 0;
  let v = bytes;
  while (v >= 1024 && i < units.length - 1) {
    v /= 1024;
    i += 1;
  }
  return `${v.toFixed(1)} ${units[i]}`;
}

// --------- 图标映射 ---------
const iconMap: Record<string, { icon: string; bg: string; color: string }> = {
  folder: { icon: 'icon-carbon-folder', bg: 'bg-blue-50', color: 'text-blue-500' },
  enterprise: { icon: 'icon-carbon-enterprise', bg: 'bg-purple-50', color: 'text-purple-500' },
  product: { icon: 'icon-carbon-product', bg: 'bg-green-50', color: 'text-green-500' },
  code: { icon: 'icon-carbon-code', bg: 'bg-orange-50', color: 'text-orange-500' },
  'tool-kit': { icon: 'icon-carbon-tool-kit', bg: 'bg-rose-50', color: 'text-rose-500' },
  'chart-line': { icon: 'icon-carbon-chart-line', bg: 'bg-teal-50', color: 'text-teal-500' },
  catalog: { icon: 'icon-carbon-catalog', bg: 'bg-indigo-50', color: 'text-indigo-500' },
  bookmark: { icon: 'icon-carbon-bookmark', bg: 'bg-pink-50', color: 'text-pink-500' },
  'data-base': { icon: 'icon-carbon-data-base', bg: 'bg-blue-50', color: 'text-blue-500' }
};

function getIconConfig(icon: string) {
  return iconMap[icon] ?? iconMap['data-base'];
}

function getStatusType(status?: string) {
  return status === '正常' || status === 'active' ? 'success' : 'warning';
}

function getStatusText(status?: string) {
  return status === '正常' || status === 'active' ? '已启用' : status || '未启用';
}

function formatTime(time?: string) {
  return time ? dayjs(time).format('YYYY-MM-DD HH:mm') : '-';
}
</script>

<template>
  <div class="kb-overview-page h-full flex flex-col bg-[#f5f7fa] dark:bg-[#101014]">
    <div class="min-h-0 flex-1 overflow-hidden lg:flex">
      <div class="w-180px flex-shrink-0 border-r border-gray-200 bg-white dark:border-gray-700 dark:bg-[#18181c] flex flex-col">
        <div class="px-4 pb-2 pt-4">
          <h2 class="mb-3 text-sm text-gray-800 font-semibold dark:text-gray-100">知识库分类</h2>
          <div class="grid grid-cols-3 rounded-lg bg-gray-100 p-0.5 dark:bg-gray-800">
            <button
              class="rounded-md py-1 text-xs font-medium transition-all"
              :class="categoryMode === 'visibility' ? 'bg-white text-gray-800 shadow-sm dark:bg-[#1e1e22] dark:text-gray-100' : 'text-gray-500 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-300'"
              @click="switchCategoryMode('visibility')"
            >
              权限
            </button>
            <button
              class="rounded-md py-1 text-xs font-medium transition-all"
              :class="categoryMode === 'status' ? 'bg-white text-gray-800 shadow-sm dark:bg-[#1e1e22] dark:text-gray-100' : 'text-gray-500 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-300'"
              @click="switchCategoryMode('status')"
            >
              状态
            </button>
            <button
              class="rounded-md py-1 text-xs font-medium transition-all"
              :class="categoryMode === 'org' ? 'bg-white text-gray-800 shadow-sm dark:bg-[#1e1e22] dark:text-gray-100' : 'text-gray-500 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-300'"
              @click="switchCategoryMode('org')"
            >
              组织
            </button>
          </div>
        </div>
        <NScrollbar class="flex-1">
          <div class="space-y-0.5 px-2 pt-1">
            <div
              class="cursor-pointer rounded-lg px-3 py-2 text-sm transition-all"
              :class="activeCategory === '全部' ? 'bg-primary-50 text-primary-600 font-medium dark:bg-primary-900/20 dark:text-primary-400' : 'text-gray-600 hover:bg-gray-50 dark:text-gray-400 dark:hover:bg-gray-800'"
              @click="handleCategoryClick('全部')"
            >
              <div class="flex items-center justify-between">
                <div class="flex min-w-0 items-center gap-2">
                  <icon-carbon:catalog class="text-base" />
                  <span class="truncate">全部</span>
                </div>
                <span class="text-xs opacity-60">{{ categoryCounts['全部'] ?? 0 }}</span>
              </div>
            </div>
            <div
              v-for="cat in categoryList"
              :key="cat"
              class="cursor-pointer rounded-lg px-3 py-2 text-sm transition-all"
              :class="activeCategory === cat ? 'bg-primary-50 text-primary-600 font-medium dark:bg-primary-900/20 dark:text-primary-400' : 'text-gray-600 hover:bg-gray-50 dark:text-gray-400 dark:hover:bg-gray-800'"
              @click="handleCategoryClick(cat)"
            >
              <div class="flex items-center justify-between">
                <div class="flex min-w-0 items-center gap-2">
                  <icon-carbon:tag class="text-base" />
                  <span class="truncate">{{ cat }}</span>
                </div>
                <span class="text-xs opacity-60">{{ categoryCounts[cat] ?? 0 }}</span>
              </div>
            </div>
          </div>
        </NScrollbar>
      </div>

      <div class="min-w-0 flex flex-1 flex-col">
        <div class="border-b border-gray-100 bg-white px-5 py-3 dark:border-gray-700 dark:bg-[#18181c]">
          <div class="flex items-center justify-between gap-3">
            <div class="grid min-w-0 flex-1 grid-cols-4 gap-3">
              <div class="rounded-lg bg-gray-50 px-3 py-2 dark:bg-[#1e1e22]">
                <div class="text-lg text-gray-900 font-semibold dark:text-white">{{ stats.knowledgeBaseCount }}</div>
                <div class="text-xs text-gray-500 dark:text-gray-400">知识库</div>
              </div>
              <div class="rounded-lg bg-gray-50 px-3 py-2 dark:bg-[#1e1e22]">
                <div class="text-lg text-gray-900 font-semibold dark:text-white">{{ stats.documentCount.toLocaleString() }}</div>
                <div class="text-xs text-gray-500 dark:text-gray-400">文档</div>
              </div>
              <div class="rounded-lg bg-gray-50 px-3 py-2 dark:bg-[#1e1e22]">
                <div class="truncate text-lg text-gray-900 font-semibold dark:text-white">{{ formatSize(stats.totalSize) }}</div>
                <div class="text-xs text-gray-500 dark:text-gray-400">存储</div>
              </div>
              <div class="rounded-lg bg-gray-50 px-3 py-2 dark:bg-[#1e1e22]">
                <div class="text-lg text-gray-900 font-semibold dark:text-white">{{ stats.chunkCount.toLocaleString() }}</div>
                <div class="text-xs text-gray-500 dark:text-gray-400">分块</div>
              </div>
            </div>
            <NButton size="small" type="primary" @click="createKbVisible = true">
              <template #icon><icon-carbon:add /></template>
              新建知识库
            </NButton>
          </div>
        </div>

        <div class="flex items-center justify-between border-b border-gray-100 bg-white px-5 py-3 dark:border-gray-700 dark:bg-[#18181c]">
          <div class="flex items-center gap-2">
            <NInput
              v-model:value="searchKeyword"
              placeholder="搜索知识库名称"
              clearable
              class="w-220px"
              size="small"
              @input="debouncedSearch"
              @clear="debouncedSearch"
            >
              <template #prefix>
                <icon-carbon:search class="text-gray-400" />
              </template>
            </NInput>
            <span class="text-xs text-gray-400">当前 {{ filteredKnowledgeBases.length }} / {{ totalCount }}</span>
          </div>
        </div>

        <div class="min-h-0 flex flex-1 overflow-hidden">
          <div class="min-w-0 flex-1 border-r border-gray-100 dark:border-gray-700">
            <NScrollbar class="h-full">
          <NSpin :show="loading">
            <div v-if="filteredKnowledgeBases.length === 0 && !loading" class="py-20">
              <NEmpty description="暂无知识库">
                <template #extra>
                  <NButton size="small" type="primary" @click="createKbVisible = true">新建知识库</NButton>
                </template>
              </NEmpty>
            </div>
            <div v-else class="grid grid-cols-1 gap-3 p-4 xl:grid-cols-2">
              <div
                v-for="item in filteredKnowledgeBases"
                :key="item.kbId"
                class="cursor-pointer border rounded-xl bg-white p-4 transition-all dark:bg-[#1e1e22] hover:shadow-md"
                :class="
                  selectedKnowledgeBase?.kbId === item.kbId
                    ? 'border-primary-400 shadow-sm ring-1 ring-primary-200 dark:border-primary-500 dark:ring-primary-800'
                    : 'border-gray-200 hover:border-primary-300 dark:border-gray-700 dark:hover:border-primary-600'
                "
                @click="selectKnowledgeBase(item)"
              >
                <div class="mb-3 flex items-start justify-between gap-3">
                  <div class="min-w-0 flex items-center gap-3">
                    <div
                      class="h-10 w-10 flex flex-shrink-0 items-center justify-center rounded-xl"
                      :class="getIconConfig(item.icon || 'data-base').bg"
                    >
                      <span
                        class="text-xl"
                        :class="[
                          getIconConfig(item.icon || 'data-base').icon,
                          getIconConfig(item.icon || 'data-base').color
                        ]"
                      />
                    </div>
                    <div class="min-w-0">
                      <h3 class="truncate text-sm text-gray-900 font-semibold dark:text-gray-100">{{ item.name }}</h3>
                      <p class="line-clamp-1 mt-0.5 text-xs text-gray-500 dark:text-gray-400">
                        {{ item.description || '暂无描述' }}
                      </p>
                    </div>
                  </div>
                  <div class="flex flex-shrink-0 items-center gap-1">
                    <FavoriteButton
                      type="knowledge"
                      :target-id="item.kbId"
                      :title="item.name"
                      :description="item.description"
                      :meta="`${item.fileCount || 0} 个文档`"
                    />
                    <NTag :type="getStatusType(item.status)" size="small" :bordered="false">
                      {{ getStatusText(item.status) }}
                    </NTag>
                  </div>
                </div>

                <div class="grid grid-cols-3 gap-2 rounded-lg bg-gray-50 p-2 text-center dark:bg-[#18181c]">
                  <div>
                    <div class="text-sm text-gray-900 font-semibold dark:text-gray-100">{{ item.fileCount || 0 }}</div>
                    <div class="mt-0.5 text-[11px] text-gray-500 dark:text-gray-400">文档</div>
                  </div>
                  <div>
                    <div class="text-sm text-gray-900 font-semibold dark:text-gray-100">{{ item.chunkCount || 0 }}</div>
                    <div class="mt-0.5 text-[11px] text-gray-500 dark:text-gray-400">分块</div>
                  </div>
                  <div>
                    <div class="truncate text-sm text-gray-900 font-semibold dark:text-gray-100">
                      {{ formatSize(item.totalSize || 0) }}
                    </div>
                    <div class="mt-0.5 text-[11px] text-gray-500 dark:text-gray-400">容量</div>
                  </div>
                </div>

                <div class="mt-3 flex items-center justify-between text-xs text-gray-400 dark:text-gray-500">
                  <span class="min-w-0 flex items-center gap-1">
                    <icon-carbon:tag class="text-sm" />
                    <span class="truncate">{{ item.orgTag || (item.isPublic ? '公开' : '私有') }}</span>
                  </span>
                  <span>{{ formatTime(item.updatedAt) }}</span>
                </div>
              </div>
            </div>
          </NSpin>
            </NScrollbar>
        </div>

        <div class="w-380px flex-shrink-0 bg-white dark:bg-[#18181c]">
          <template v-if="selectedKnowledgeBase">
            <div class="border-b border-gray-100 px-5 py-3 dark:border-gray-700">
              <h2 class="text-sm text-gray-800 font-semibold dark:text-gray-100">知识库详情</h2>
            </div>
            <div class="p-5 space-y-4">
              <div class="flex items-start gap-3">
                <div
                  class="h-12 w-12 flex flex-shrink-0 items-center justify-center rounded-xl"
                  :class="getIconConfig(selectedKnowledgeBase.icon || 'data-base').bg"
                >
                  <span
                    class="text-2xl"
                    :class="[
                      getIconConfig(selectedKnowledgeBase.icon || 'data-base').icon,
                      getIconConfig(selectedKnowledgeBase.icon || 'data-base').color
                    ]"
                  />
                </div>
                <div class="min-w-0">
                  <h3 class="text-base text-gray-900 font-semibold dark:text-gray-50">
                    {{ selectedKnowledgeBase.name }}
                  </h3>
                  <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
                    {{ selectedKnowledgeBase.description || '暂无描述' }}
                  </p>
                </div>
              </div>

              <div class="flex flex-wrap items-center gap-2">
                <NTag :type="getStatusType(selectedKnowledgeBase.status)" :bordered="false" size="small">
                  {{ getStatusText(selectedKnowledgeBase.status) }}
                </NTag>
                <NTag :type="selectedKnowledgeBase.isPublic ? 'success' : 'default'" :bordered="false" size="small">
                  {{ selectedKnowledgeBase.isPublic ? '公开' : '私有' }}
                </NTag>
              </div>

              <NDivider class="!my-2" />

              <div class="grid grid-cols-3 gap-2">
                <div class="rounded-lg bg-gray-50 px-2 py-2 text-center dark:bg-[#1e1e22]">
                  <div class="text-base text-gray-900 font-semibold dark:text-gray-100">
                    {{ selectedKnowledgeBase.fileCount || 0 }}
                  </div>
                  <div class="mt-0.5 text-xs text-gray-500 dark:text-gray-400">文档数</div>
                </div>
                <div class="rounded-lg bg-gray-50 px-2 py-2 text-center dark:bg-[#1e1e22]">
                  <div class="text-base text-gray-900 font-semibold dark:text-gray-100">
                    {{ selectedKnowledgeBase.chunkCount || 0 }}
                  </div>
                  <div class="mt-0.5 text-xs text-gray-500 dark:text-gray-400">分块数</div>
                </div>
                <div class="rounded-lg bg-gray-50 px-2 py-2 text-center dark:bg-[#1e1e22]">
                  <div class="truncate text-base text-gray-900 font-semibold dark:text-gray-100">
                    {{ formatSize(selectedKnowledgeBase.totalSize || 0) }}
                  </div>
                  <div class="mt-0.5 text-xs text-gray-500 dark:text-gray-400">存储</div>
                </div>
              </div>

              <div class="text-sm space-y-2">
                <div class="flex items-center justify-between gap-4">
                  <span class="text-xs text-gray-500 dark:text-gray-400">组织标签</span>
                  <span class="truncate text-gray-700 dark:text-gray-300">
                    {{ selectedKnowledgeBase.orgTag || '-' }}
                  </span>
                </div>
                <div class="flex items-center justify-between gap-4">
                  <span class="text-xs text-gray-500 dark:text-gray-400">创建人</span>
                  <span class="truncate text-gray-700 dark:text-gray-300">
                    {{ selectedKnowledgeBase.createdBy || '-' }}
                  </span>
                </div>
                <div class="flex items-center justify-between gap-4">
                  <span class="text-xs text-gray-500 dark:text-gray-400">创建时间</span>
                  <span class="text-gray-700 dark:text-gray-300">
                    {{ formatTime(selectedKnowledgeBase.createdAt) }}
                  </span>
                </div>
                <div class="flex items-center justify-between gap-4">
                  <span class="text-xs text-gray-500 dark:text-gray-400">更新时间</span>
                  <span class="text-gray-700 dark:text-gray-300">
                    {{ formatTime(selectedKnowledgeBase.updatedAt) }}
                  </span>
                </div>
                <div class="flex items-center justify-between gap-4">
                  <span class="text-xs text-gray-500 dark:text-gray-400">ID</span>
                  <span class="truncate text-gray-700 dark:text-gray-300">{{ selectedKnowledgeBase.kbId }}</span>
                </div>
              </div>

              <div class="flex gap-2 pt-2">
                <FavoriteButton
                  type="knowledge"
                  :target-id="selectedKnowledgeBase.kbId"
                  :title="selectedKnowledgeBase.name"
                  :description="selectedKnowledgeBase.description"
                  :meta="`${selectedKnowledgeBase.fileCount || 0} 个文档`"
                  size="small"
                  :text="false"
                  show-label
                />
                <NButton size="small" type="primary" @click="handleEdit(selectedKnowledgeBase)">
                  <template #icon><icon-carbon:edit /></template>
                  编辑
                </NButton>
                <NButton size="small" secondary type="error" @click="handleDeleteClick(selectedKnowledgeBase)">
                  <template #icon><icon-carbon:trash-can /></template>
                  删除
                </NButton>
              </div>
            </div>
          </template>
          <div v-else class="py-20">
            <NEmpty description="选择一个知识库查看详情" />
          </div>
        </div>
        </div>

        <!-- 分页 -->
        <ListPagination
          v-model:page="currentPage"
          :page-count="Math.max(1, Math.ceil(totalCount / pageSize))"
          :page-size="pageSize"
          class="dark:bg-[#18181c]"
          @update:page="handlePageChange"
          @update:page-size="handlePageSizeChange"
        />
      </div>
    </div>

    <!-- 三点操作下拉菜单 -->
    <!-- 已移除，操作按钮直接内联在表格行中 -->

    <!-- 新建知识库对话框 -->
    <CreateKbDialog v-model:visible="createKbVisible" @submitted="loadKnowledgeBases" />

    <!-- 编辑知识库对话框 -->
    <CreateKbDialog v-model:visible="editKbVisible" :edit-data="editingKb" @submitted="loadKnowledgeBases" />

    <!-- 删除确认对话框 -->
    <NModal
      v-model:show="deleteModalVisible"
      preset="dialog"
      type="error"
      title="删除知识库"
      :mask-closable="false"
      :close-on-esc="false"
      @after-leave="handleDeleteCancel"
    >
      <template #default>
        <div class="py-2">
          <p class="mb-3 text-gray-700 dark:text-gray-300">
            确认删除知识库
            <span class="text-gray-900 font-semibold dark:text-white">「{{ deletingKb?.name }}」</span>
            吗？
          </p>
          <template v-if="deletingKb && deletingKb.fileCount > 0">
            <div
              class="mb-3 flex items-start gap-2 border border-red-200 rounded-lg bg-red-50 px-4 py-3 dark:border-red-800 dark:bg-red-900/20"
            >
              <icon-carbon:warning-alt class="mt-0.5 flex-shrink-0 text-lg text-red-500" />
              <p class="text-sm text-red-700 dark:text-red-400">
                该知识库下包含
                <span class="text-red-600 font-bold dark:text-red-300">{{ deletingKb.fileCount }}</span>
                个文档，删除知识库将同时删除所有文档及其索引数据，
                <span class="font-semibold">此操作不可撤销！</span>
              </p>
            </div>
          </template>
          <template v-else>
            <p class="mb-3 text-sm text-gray-500 dark:text-gray-400">该知识库下暂无文档，此操作不可撤销。</p>
          </template>
        </div>
      </template>
      <template #action>
        <div class="flex justify-end gap-2">
          <NButton @click="handleDeleteCancel">取消</NButton>
          <NButton
            type="error"
            :disabled="deleteCountdown > 0 || deleteLoading"
            :loading="deleteLoading"
            @click="handleDeleteConfirm"
          >
            {{ deleteCountdown > 0 ? `删除（${deleteCountdown}s）` : '确认删除' }}
          </NButton>
        </div>
      </template>
    </NModal>
  </div>
</template>

<style scoped lang="scss">
.kb-overview-page {
  .stat-card {
    transition: box-shadow 0.2s ease;
    &:hover {
      box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
    }
  }
}

.min-h-520px {
  min-height: 520px;
}
</style>

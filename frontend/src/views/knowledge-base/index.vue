<script setup lang="tsx">
import { NButton, NDataTable, NInput, NModal, NPagination, NTag, NTooltip } from 'naive-ui';

import type { DataTableColumns } from 'naive-ui';
import { fetchGetKnowledgeBases, fetchDeleteKnowledgeBase, fetchGetKnowledgeBaseStats } from '@/service/api/knowledge-base';
import CreateKbDialog from './modules/create-kb-dialog.vue';
import debounce from 'lodash-es/debounce';

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

// --------- 分页 ---------
const pageSizeOptions = [10, 50, 100];
const currentPage = ref(1);
const pageSize = ref(10);
const totalCount = ref(0);

// --------- 新建/编辑知识库 ---------
const createKbVisible = ref(false);
const editKbVisible = ref(false);
const editingKb = ref<Api.KnowledgeBase.KnowledgeBaseInfo | null>(null);

function handleEdit(row: Api.KnowledgeBase.KnowledgeBaseInfo) {
  editingKb.value = row;
  editKbVisible.value = true;
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
  while (v >= 1024 && i < units.length - 1) { v /= 1024; i++; }
  return `${v.toFixed(1)} ${units[i]}`;
}

// --------- 图标映射 ---------
const iconMap: Record<string, { icon: string; bg: string; color: string }> = {
  'folder':     { icon: 'icon-carbon-folder',       bg: 'bg-blue-50',   color: 'text-blue-500' },
  'enterprise': { icon: 'icon-carbon-enterprise',   bg: 'bg-purple-50', color: 'text-purple-500' },
  'product':    { icon: 'icon-carbon-product',      bg: 'bg-green-50',  color: 'text-green-500' },
  'code':       { icon: 'icon-carbon-code',         bg: 'bg-orange-50', color: 'text-orange-500' },
  'tool-kit':   { icon: 'icon-carbon-tool-kit',     bg: 'bg-rose-50',   color: 'text-rose-500' },
  'chart-line': { icon: 'icon-carbon-chart-line',   bg: 'bg-teal-50',   color: 'text-teal-500' },
  'catalog':    { icon: 'icon-carbon-catalog',      bg: 'bg-indigo-50', color: 'text-indigo-500' },
  'bookmark':   { icon: 'icon-carbon-bookmark',     bg: 'bg-pink-50',   color: 'text-pink-500' },
  'data-base':  { icon: 'icon-carbon-data-base',    bg: 'bg-blue-50',   color: 'text-blue-500' }
};

function getIconConfig(icon: string) {
  return iconMap[icon] ?? iconMap['data-base'];
}

// --------- 表格列 ---------
const columns = computed<DataTableColumns<Api.KnowledgeBase.KnowledgeBaseInfo>>(() => [
  {
    key: 'name',
    title: '知识库名称',
    width: 160,
    render: row => {
      const cfg = getIconConfig(row.icon || 'data-base');
      return (
      <div class="flex items-center gap-2">
        <div class={`w-7 h-7 rounded-lg ${cfg.bg} flex items-center justify-center flex-shrink-0`}>
          <span class={`${cfg.icon} ${cfg.color} text-base`} />
        </div>
        <NTooltip placement="top" trigger="hover" style="max-width: 360px">
          {{
            trigger: () => (
              <div style="overflow: hidden; white-space: nowrap; text-overflow: ellipsis; max-width: 100px;">
                { row.name }
              </div>
            ),
            default: () => row.name
          }}
        </NTooltip>
      </div>
    );
    }
  },
  {
    key: 'description',
    title: '描述',
    width: 200,
    ellipsis: {
      tooltip: true
    },
    render: row => (
      <NTooltip placement="top" trigger="hover" style="max-width: 360px">
        {{
          trigger: () => (
            <div style="overflow: hidden; white-space: nowrap; text-overflow: ellipsis; max-width: 180px;">
              { row.description || '-' }
            </div>
          ),
          default: () => row.description || '-'
        }}
      </NTooltip>
    )
  },
  {
    key: 'fileCount',
    title: '文档数',
    width: 80,
    align: 'center',
    titleAlign: 'center'
  },
  {
    key: 'chunkCount',
    title: '分块数',
    width: 80,
    align: 'center',
    titleAlign: 'center'
  },
  {
    key: 'updatedAt',
    title: '更新时间',
    width: 150,
    align: 'center',
    titleAlign: 'center',
    render: row => row.updatedAt ? dayjs(row.updatedAt).format('YYYY-MM-DD HH:mm') : '-'
  },
  {
    key: 'status',
    title: '状态',
    width: 80,
    align: 'center',
    titleAlign: 'center',
    render: row => (
      <NTag type={row.status === '正常' || row.status === 'active' ? 'success' : 'warning'} size="small">
        { row.status === '正常' || row.status === 'active' ? '已启用' : row.status }
      </NTag>
    )
  },
  {
    key: 'operate',
    title: '操作',
    width: 120,
    align: 'center',
    titleAlign: 'center',
    render: row => (
      <div class="flex items-center justify-center gap-2">
        <NButton
          text
          size="small"
          type="primary"
          onClick={() => handleEdit(row)}
        >
          编辑
        </NButton>
        <NButton
          text
          size="small"
          type="error"
          onClick={() => handleDeleteClick(row)}
        >
          删除
        </NButton>
      </div>
    )
  }
]);
</script>

<template>
  <div class="kb-overview-page h-full flex flex-col bg-gray-50 dark:bg-gray-900 overflow-y-auto">
    <div class="px-8 py-6 flex-1 min-h-0">
      <!-- 标题 -->
      <h1 class="text-2xl font-bold text-gray-900 dark:text-white mb-6">知识库</h1>

      <!-- 统计卡片 -->
      <div class="grid grid-cols-4 gap-5 mb-6">
        <div class="stat-card bg-white dark:bg-gray-800 rounded-xl px-6 py-5 shadow-sm border border-gray-100 dark:border-gray-700">
          <div class="text-3xl font-bold text-gray-900 dark:text-white mb-1">{{ stats.knowledgeBaseCount }}</div>
          <div class="text-sm text-gray-500 dark:text-gray-400">知识库总数</div>
        </div>
        <div class="stat-card bg-white dark:bg-gray-800 rounded-xl px-6 py-5 shadow-sm border border-gray-100 dark:border-gray-700">
          <div class="text-3xl font-bold text-gray-900 dark:text-white mb-1">{{ stats.documentCount.toLocaleString() }}</div>
          <div class="text-sm text-gray-500 dark:text-gray-400">文档总数</div>
        </div>
        <div class="stat-card bg-white dark:bg-gray-800 rounded-xl px-6 py-5 shadow-sm border border-gray-100 dark:border-gray-700">
          <div class="text-3xl font-bold text-gray-900 dark:text-white mb-1">{{ formatSize(stats.totalSize) }}</div>
          <div class="text-sm text-gray-500 dark:text-gray-400">存储使用</div>
        </div>
        <div class="stat-card bg-white dark:bg-gray-800 rounded-xl px-6 py-5 shadow-sm border border-gray-100 dark:border-gray-700">
          <div class="text-3xl font-bold text-gray-900 dark:text-white mb-1">{{ stats.chunkCount.toLocaleString() }}</div>
          <div class="text-sm text-gray-500 dark:text-gray-400">关键词数量</div>
        </div>
      </div>

      <!-- 搜索 + 新建按钮 -->
      <div class="flex items-center justify-between mb-4">
        <NInput
          v-model:value="searchKeyword"
          placeholder="搜索知识库名称"
          clearable
          class="max-w-260px"
          @input="debouncedSearch"
          @clear="debouncedSearch"
        >
          <template #prefix>
            <icon-carbon:search class="text-gray-400" />
          </template>
        </NInput>
        <NButton type="primary" @click="createKbVisible = true">
          <template #icon>
            <icon-carbon:add />
          </template>
          新建知识库
        </NButton>
      </div>

      <!-- 表格 -->
      <div class="bg-white dark:bg-gray-800 rounded-xl shadow-sm border border-gray-100 dark:border-gray-700 overflow-hidden">
        <NDataTable
          :columns="columns"
          :data="knowledgeBases"
          :loading="loading"
          :row-key="row => row.kbId"
          size="small"
          :pagination="false"
          :scroll-x="800"
          striped
          class="kb-table"
        />

        <!-- 分页 -->
        <div class="flex justify-end px-4 py-3 border-t border-gray-100 dark:border-gray-700">
          <NPagination
            v-model:page="currentPage"
            :page-count="Math.max(1, Math.ceil(totalCount / pageSize))"
            :page-size="pageSize"
            :page-sizes="pageSizeOptions"
            show-size-picker
            @update:page="handlePageChange"
            @update:page-size="handlePageSizeChange"
          />
        </div>
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
          <p class="text-gray-700 dark:text-gray-300 mb-3">
            确认删除知识库
            <span class="font-semibold text-gray-900 dark:text-white">「{{ deletingKb?.name }}」</span>
            吗？
          </p>
          <template v-if="deletingKb && deletingKb.fileCount > 0">
            <div class="flex items-start gap-2 bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded-lg px-4 py-3 mb-3">
              <icon-carbon:warning-alt class="text-red-500 text-lg flex-shrink-0 mt-0.5" />
              <p class="text-sm text-red-700 dark:text-red-400">
                该知识库下包含 <span class="font-bold text-red-600 dark:text-red-300">{{ deletingKb.fileCount }}</span> 个文档，删除知识库将同时删除所有文档及其索引数据，<span class="font-semibold">此操作不可撤销！</span>
              </p>
            </div>
          </template>
          <template v-else>
            <p class="text-sm text-gray-500 dark:text-gray-400 mb-3">该知识库下暂无文档，此操作不可撤销。</p>
          </template>
        </div>
      </template>
      <template #action>
        <div class="flex gap-2 justify-end">
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

/* 第一列与分页区域左侧对齐，保持 16px 左内边距 */
:deep(.kb-table) {
  th:first-child,
  td:first-child {
    padding-left: 16px !important;
  }
}
</style>

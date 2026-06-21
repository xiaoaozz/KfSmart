<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { fetchGetOperationRecords } from '@/service/api';
import { DEFAULT_PAGE_SIZE, PAGINATION_PAGE_SIZE_OPTIONS } from '@/constants/common';

defineOptions({ name: 'OperationRecord' });

const pageSizeOptions = PAGINATION_PAGE_SIZE_OPTIONS;
const currentPage = ref(1);
const pageSize = ref(DEFAULT_PAGE_SIZE);
const filterType = ref('all');
const searchText = ref('');
const loading = ref(false);

type OpType = 'login' | 'upload' | 'chat' | 'knowledge' | 'profile' | 'delete';

interface OperationRecord {
  id: string;
  type: OpType;
  action: string;
  detail: string;
  ip: string;
  device: string;
  time: string;
  status: 'success' | 'failed';
}

const allRecords = ref<OperationRecord[]>([]);

const typeOptions = [
  { label: '全部类型', value: 'all' },
  { label: '登录/登出', value: 'login' },
  { label: '文档操作', value: 'upload' },
  { label: '对话操作', value: 'chat' },
  { label: '知识库', value: 'knowledge' },
  { label: '个人设置', value: 'profile' },
  { label: '删除操作', value: 'delete' }
];

const typeLabelMap: Record<OpType, string> = {
  login: '登录',
  upload: '文档',
  chat: '对话',
  knowledge: '知识库',
  profile: '设置',
  delete: '删除'
};

const filteredRecords = computed(() =>
  allRecords.value.filter(record => {
    const matchType = filterType.value === 'all' || record.type === filterType.value;
    const keyword = searchText.value.trim();
    const matchSearch = !keyword || record.action.includes(keyword) || record.detail.includes(keyword);
    return matchType && matchSearch;
  })
);

const pageCount = computed(() => Math.max(1, Math.ceil(filteredRecords.value.length / pageSize.value)));

const pagedRecords = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value;
  return filteredRecords.value.slice(start, start + pageSize.value);
});

watch([filterType, searchText], () => {
  currentPage.value = 1;
});

watch([filteredRecords, pageSize], () => {
  if (currentPage.value > pageCount.value) {
    currentPage.value = pageCount.value;
  }
});

function handlePageChange(page: number) {
  currentPage.value = page;
}

function handlePageSizeChange(size: number) {
  pageSize.value = size;
  currentPage.value = 1;
}

const typeConfig: Record<OpType, { icon: string; color: string; bg: string }> = {
  login: { icon: 'carbon:login', color: 'text-blue-500', bg: 'bg-blue-50 dark:bg-blue-900/20' },
  upload: { icon: 'carbon:upload', color: 'text-green-500', bg: 'bg-green-50 dark:bg-green-900/20' },
  chat: { icon: 'carbon:chat', color: 'text-purple-500', bg: 'bg-purple-50 dark:bg-purple-900/20' },
  knowledge: { icon: 'carbon:folder', color: 'text-orange-500', bg: 'bg-orange-50 dark:bg-orange-900/20' },
  profile: { icon: 'carbon:user', color: 'text-cyan-500', bg: 'bg-cyan-50 dark:bg-cyan-900/20' },
  delete: { icon: 'carbon:trash-can', color: 'text-red-500', bg: 'bg-red-50 dark:bg-red-900/20' }
};

function formatTime(value: string) {
  if (!value) return '--';
  return value.replace('T', ' ').substring(0, 19);
}

async function loadRecords() {
  loading.value = true;
  const { data, error } = await fetchGetOperationRecords();
  loading.value = false;
  if (!error && data) {
    allRecords.value = data as OperationRecord[];
  }
}

onMounted(loadRecords);
</script>

<template>
  <div class="operation-record space-y-3">
    <!-- 筛选栏 -->
    <div class="flex flex-wrap items-center gap-3">
      <NInput
        v-model:value="searchText"
        placeholder="搜索操作记录..."
        clearable
        size="small"
        class="w-56"
      >
        <template #prefix><icon-carbon:search class="text-gray-400" /></template>
      </NInput>
      <NSelect
        v-model:value="filterType"
        :options="typeOptions"
        size="small"
        style="width: 130px"
      />
      <span class="ml-auto text-xs text-gray-400">共 {{ filteredRecords.length }} 条</span>
    </div>

    <!-- 记录列表 -->
    <NSpin :show="loading">
    <div class="bg-white dark:bg-gray-800 rounded-xl shadow-sm border border-gray-100 dark:border-gray-700 overflow-hidden">
      <div v-if="pagedRecords.length > 0" class="divide-y divide-gray-100 dark:divide-gray-700">
        <div
          v-for="record in pagedRecords"
          :key="record.id"
          class="flex items-start gap-3 px-4 py-2.5 hover:bg-gray-50 dark:hover:bg-gray-700/30 transition-colors"
        >
          <div class="w-9 h-9 rounded-xl flex items-center justify-center flex-shrink-0" :class="typeConfig[record.type].bg">
            <component :is="typeConfig[record.type].icon" class="text-sm" :class="typeConfig[record.type].color" />
          </div>

          <div class="flex-1 min-w-0">
            <div class="flex items-start justify-between gap-3">
              <div class="min-w-0 flex-1">
                <div class="flex flex-wrap items-center gap-2">
                  <span class="text-sm font-medium text-gray-800 dark:text-gray-100 leading-5">{{ record.action }}</span>
                  <NTag size="tiny" :bordered="false" class="text-11px">
                    {{ typeLabelMap[record.type] }}
                  </NTag>
                  <NTag :type="record.status === 'success' ? 'success' : 'error'" size="tiny" :bordered="false">
                    {{ record.status === 'success' ? '成功' : '失败' }}
                  </NTag>
                </div>
                <p class="mt-1 text-xs text-gray-500 dark:text-gray-400 leading-5 break-all">{{ record.detail }}</p>
              </div>

              <span class="text-xs text-gray-400 flex-shrink-0 leading-5 whitespace-nowrap">{{ formatTime(record.time) }}</span>
            </div>

            <div class="flex flex-wrap items-center gap-x-3 gap-y-1 mt-1.5 text-xs text-gray-400">
              <span class="inline-flex items-center gap-1 rounded-md bg-gray-50 dark:bg-gray-700/50 px-2 py-0.5">
                <icon-carbon:network-4 class="text-xs" />
                {{ record.ip }}
              </span>
              <span class="inline-flex items-center gap-1 rounded-md bg-gray-50 dark:bg-gray-700/50 px-2 py-0.5">
                <icon-carbon:screen class="text-xs" />
                {{ record.device }}
              </span>
            </div>
          </div>
        </div>
      </div>

      <!-- 空状态 -->
      <div v-else class="flex flex-col items-center justify-center py-12 text-gray-400">
        <icon-carbon:document-blank class="text-4xl mb-2 opacity-50" />
        <p class="text-sm">暂无操作记录</p>
      </div>

      <!-- 分页 -->
      <div v-if="filteredRecords.length > 0" class="flex items-center justify-end gap-3 px-4 py-3 border-t border-gray-100 dark:border-gray-700">
        <NPagination
          v-model:page="currentPage"
          :page-count="pageCount"
          :page-size="pageSize"
          :page-sizes="pageSizeOptions"
          show-size-picker
          @update:page="handlePageChange"
          @update:page-size="handlePageSizeChange"
        />
      </div>
    </div>
    </NSpin>
  </div>
</template>

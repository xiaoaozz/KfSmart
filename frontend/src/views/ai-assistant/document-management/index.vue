<script setup lang="tsx">
import { NButton, NDivider, NEmpty, NInput, NModal, NPagination, NPopconfirm, NScrollbar, NSelect, NSpin, NTag } from 'naive-ui';
import debounce from 'lodash-es/debounce';
import { fakePaginationRequest } from '@/service/request';
import { fetchGetKnowledgeBaseFilterOptions, fetchGetKnowledgeBases } from '@/service/api/knowledge-base';
import { fileSize, getFileExt } from '@/utils/common';
import { UploadStatus } from '@/enum';
import FilePreview from '@/components/custom/file-preview.vue';
import UploadDialog from '@/views/ai-assistant/knowledge-base/modules/upload-dialog.vue';

// --------- Tab ---------
const activeTab = ref<'all' | 'mine' | 'recent'>('all');
const tabOptions = [
  { label: '全部文档', value: 'all' },
  { label: '我创建的', value: 'mine' },
  { label: '最近更新', value: 'recent' }
];

// --------- 筛选状态 ---------
const searchKeyword = ref('');
const selectedFileType = ref('');
const selectedKbId = ref('');
const categoryMode = ref<'tab' | 'type' | 'kb'>('tab');
const activeCategory = ref('全部');

// --------- 筛选选项 ---------
const filterOptions = ref<Api.KnowledgeBase.KnowledgeBaseFilterOptions>({
  orgTags: [],
  creators: [],
  icons: [],
  publicOptions: [],
  timeRangeOptions: [],
  fileTypes: []
});
const knowledgeBases = ref<Api.KnowledgeBase.KnowledgeBaseInfo[]>([]);

async function loadFilterOptions() {
  const [filterRes, kbRes] = await Promise.all([
    fetchGetKnowledgeBaseFilterOptions(),
    fetchGetKnowledgeBases({ size: 100 })
  ]);
  if (!filterRes.error && filterRes.data) filterOptions.value = filterRes.data;
  if (!kbRes.error && kbRes.data)
    knowledgeBases.value = kbRes.data.records || kbRes.data.content || kbRes.data.data || [];
}

const kbSelectOptions = computed(() => [
  { label: '全部知识库', value: '' },
  ...knowledgeBases.value.map(kb => ({ label: kb.name, value: kb.kbId }))
]);

const fileTypeSelectOptions = computed(() => [
  { label: '全部类型', value: '' },
  ...filterOptions.value.fileTypes.map(t => ({ label: t, value: t }))
]);

const categoryList = computed(() => {
  if (categoryMode.value === 'tab') return tabOptions.map(item => item.label);
  if (categoryMode.value === 'type') return fileTypeSelectOptions.value.filter(item => item.value).map(item => item.label);
  return knowledgeBases.value.map(item => item.name);
});

const categoryCounts = computed(() => {
  const counts: Record<string, number> = { '全部': data.value.length };
  data.value.forEach(item => {
    const tabKey = activeTab.value === 'mine' ? '我创建的' : activeTab.value === 'recent' ? '最近更新' : '全部文档';
    const typeKey = getFileType(item.fileName);
    const kbKey = getKbName(item.kbId);
    const key = categoryMode.value === 'tab' ? tabKey : categoryMode.value === 'type' ? typeKey : kbKey;
    counts[key] = (counts[key] || 0) + 1;
  });
  return counts;
});

// --------- 文件预览 ---------
const previewVisible = ref(false);
const previewFileName = ref('');
const previewFileMd5 = ref('');
const previewModalStyle = {
  width: '760px',
  maxWidth: 'calc(100vw - 32px)'
};

function getPreviewFileIcon() {
  const ext = getFileExt(previewFileName.value);
  if (ext) {
    const supportedIcons = ['pdf', 'doc', 'docx', 'txt', 'md', 'jpg', 'jpeg', 'png', 'gif'];
    return supportedIcons.includes(ext.toLowerCase()) ? ext : 'dflt';
  }
  return 'dflt';
}

function handleFilePreview(fileName: string, fileMd5: string) {
  previewFileName.value = fileName;
  previewFileMd5.value = fileMd5;
  previewVisible.value = true;
}

async function handleDownloadPreview() {
  if (!previewFileName.value) return;
  try {
    const token = localStorage.getItem('token');
    if (previewFileMd5.value) {
      const { error: requestError, data } = await request<{
        fileName: string;
        downloadUrl: string;
        fileSize: number;
      }>({
        url: '/documents/download-by-md5',
        params: { fileMd5: previewFileMd5.value, token: token || undefined }
      });
      if (!requestError && data) {
        const link = document.createElement('a');
        link.href = data.downloadUrl;
        link.download = data.fileName;
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        window.$message?.success('开始下载文件');
      }
    } else {
      const { error: requestError, data } = await request<{
        fileName: string;
        downloadUrl: string;
        fileSize: number;
      }>({
        url: '/documents/download',
        params: { fileName: previewFileName.value, token: token || undefined }
      });
      if (!requestError && data) {
        const link = document.createElement('a');
        link.href = data.downloadUrl;
        link.download = data.fileName;
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        window.$message?.success('开始下载文件');
      }
    }
  } catch (err: any) {
    window.$message?.error(`下载失败：${err.message || '网络错误'}`);
  }
}

// --------- 数据加载 ---------
function apiFn(pageParams?: Api.Common.CommonSearchParams) {
  const params: Record<string, string | boolean | number> = {};
  if (pageParams?.page) params.page = pageParams.page;
  if (pageParams?.size) params.size = pageParams.size;
  if (selectedFileType.value) params.fileType = selectedFileType.value;
  if (selectedKbId.value) params.kbId = selectedKbId.value;
  if (searchKeyword.value) params.keyword = searchKeyword.value;
  if (activeTab.value === 'mine') params.mine = true;
  if (activeTab.value === 'recent') params.sort = 'updatedAt';
  return fakePaginationRequest<Api.KnowledgeBase.List>({ url: '/documents/uploads', params });
}

// 文件类型图标映射（carbon 图标 + 背景色 + 图标色，风格与知识库图标一致）
const fileIconMap: Record<string, { icon: string; bg: string; color: string }> = {
  pdf: { icon: 'icon-carbon-document-pdf', bg: 'bg-red-50', color: 'text-red-500' },
  doc: { icon: 'icon-carbon-document-word-processor', bg: 'bg-blue-50', color: 'text-blue-600' },
  docx: { icon: 'icon-carbon-document-word-processor', bg: 'bg-blue-50', color: 'text-blue-600' },
  xls: { icon: 'icon-carbon-data-table', bg: 'bg-green-50', color: 'text-green-600' },
  xlsx: { icon: 'icon-carbon-data-table', bg: 'bg-green-50', color: 'text-green-600' },
  ppt: { icon: 'icon-carbon-document', bg: 'bg-orange-50', color: 'text-orange-500' },
  pptx: { icon: 'icon-carbon-document', bg: 'bg-orange-50', color: 'text-orange-500' },
  txt: { icon: 'icon-carbon-document', bg: 'bg-gray-50', color: 'text-gray-500' },
  md: { icon: 'icon-carbon-document', bg: 'bg-slate-50', color: 'text-slate-600' },
  jpg: { icon: 'icon-carbon-image', bg: 'bg-purple-50', color: 'text-purple-500' },
  jpeg: { icon: 'icon-carbon-image', bg: 'bg-purple-50', color: 'text-purple-500' },
  png: { icon: 'icon-carbon-image', bg: 'bg-purple-50', color: 'text-purple-500' },
  gif: { icon: 'icon-carbon-image', bg: 'bg-purple-50', color: 'text-purple-500' },
  csv: { icon: 'icon-carbon-data-table', bg: 'bg-teal-50', color: 'text-teal-600' },
  json: { icon: 'icon-carbon-document', bg: 'bg-yellow-50', color: 'text-yellow-600' },
  zip: { icon: 'icon-carbon-zip', bg: 'bg-gray-50', color: 'text-gray-500' }
};

function getFileIconConfig(fileName: string) {
  const ext = getFileExt(fileName)?.toLowerCase();
  return ext
    ? (fileIconMap[ext] ?? { icon: 'icon-carbon-document', bg: 'bg-gray-50', color: 'text-gray-400' })
    : { icon: 'icon-carbon-document', bg: 'bg-gray-50', color: 'text-gray-400' };
}

const store = useKnowledgeBaseStore();
const { tasks } = storeToRefs(store);

const { data, getData, getDataByPage, loading, mobilePagination } = useTable({
  apiFn,
  immediate: false,
  columns: () => []
});

const selectedFileMd5 = ref('');

const selectedDocument = computed(
  () => data.value.find(item => item.fileMd5 === selectedFileMd5.value) || data.value[0] || null
);

function selectDocument(row: Api.KnowledgeBase.UploadTask) {
  selectedFileMd5.value = row.fileMd5;
}

function getKbName(kbId?: string | null) {
  return knowledgeBases.value.find(kb => kb.kbId === kbId)?.name || kbId || '-';
}

function getFileType(fileName: string) {
  return getFileExt(fileName)?.toUpperCase() || 'FILE';
}

function formatTime(time?: string) {
  return time ? dayjs(time).format('YYYY-MM-DD HH:mm') : '-';
}

function getUploadStatusText(status?: UploadStatus | string) {
  if (status === UploadStatus.Uploading) return '上传中';
  if (status === UploadStatus.Completed) return '已上传';
  if (status === UploadStatus.Pending) return '等待中';
  if (status === UploadStatus.Paused) return '已暂停';
  if (status === UploadStatus.Break) return '已中断';
  return status || '已上传';
}

function getUploadStatusType(status?: UploadStatus | string) {
  if (status === UploadStatus.Break) return 'error';
  if (status === UploadStatus.Uploading) return 'info';
  if (status === UploadStatus.Pending || status === UploadStatus.Paused) return 'warning';
  return 'success';
}

async function downloadDocument(row: Api.KnowledgeBase.UploadTask) {
  previewFileName.value = row.fileName;
  previewFileMd5.value = row.fileMd5;
  await handleDownloadPreview();
}

async function handleDelete(fileMd5: string) {
  const { error } = await request({ url: `/documents/${fileMd5}`, method: 'DELETE' });
  if (!error) {
    window.$message?.success('删除成功');
    await getData();
  }
}

// --------- 搜索防抖 ---------
const debouncedSearch = debounce(() => getDataByPage(), 300);

// --------- Tab 切换 ---------
function onTabChange(val: string) {
  activeTab.value = val as 'all' | 'mine' | 'recent';
  getDataByPage();
}

function switchCategoryMode(mode: 'tab' | 'type' | 'kb') {
  categoryMode.value = mode;
  activeCategory.value = '全部';
}

function handleCategoryClick(category: string) {
  activeCategory.value = category;
  if (categoryMode.value === 'tab') {
    const target = tabOptions.find(item => item.label === category);
    activeTab.value = (target?.value || 'all') as 'all' | 'mine' | 'recent';
  } else if (categoryMode.value === 'type') {
    selectedFileType.value = category === '全部' ? '' : category;
  } else {
    const target = knowledgeBases.value.find(item => item.name === category);
    selectedKbId.value = target?.kbId || '';
  }
  getDataByPage();
}

// --------- 上传 ---------
const uploadVisible = ref(false);
const activeKbForUpload = ref('');

function handleUpload() {
  activeKbForUpload.value = selectedKbId.value;
  uploadVisible.value = true;
}

// --------- 监听任务完成自动刷新 ---------
watch(
  () => tasks.value.filter(t => t.status === UploadStatus.Completed).length,
  (newCount, oldCount) => {
    if (newCount > oldCount) getData();
  }
);

watch(data, list => {
  if (!list.some(item => item.fileMd5 === selectedFileMd5.value)) {
    selectedFileMd5.value = list[0]?.fileMd5 || '';
  }
});

onMounted(async () => {
  await loadFilterOptions();
  await getData();
});
</script>

<template>
  <div class="doc-management-page h-full flex flex-col bg-[#f5f7fa] dark:bg-[#101014]">
    <div class="min-h-0 flex-1 overflow-hidden lg:flex">
      <div class="w-180px flex-shrink-0 border-r border-gray-200 bg-white dark:border-gray-700 dark:bg-[#18181c] flex flex-col">
        <div class="px-4 pb-2 pt-4">
          <h2 class="mb-3 text-sm text-gray-800 font-semibold dark:text-gray-100">文档分类</h2>
          <div class="grid grid-cols-3 rounded-lg bg-gray-100 p-0.5 dark:bg-gray-800">
            <button
              class="rounded-md py-1 text-xs font-medium transition-all"
              :class="categoryMode === 'tab' ? 'bg-white text-gray-800 shadow-sm dark:bg-[#1e1e22] dark:text-gray-100' : 'text-gray-500 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-300'"
              @click="switchCategoryMode('tab')"
            >
              范围
            </button>
            <button
              class="rounded-md py-1 text-xs font-medium transition-all"
              :class="categoryMode === 'type' ? 'bg-white text-gray-800 shadow-sm dark:bg-[#1e1e22] dark:text-gray-100' : 'text-gray-500 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-300'"
              @click="switchCategoryMode('type')"
            >
              类型
            </button>
            <button
              class="rounded-md py-1 text-xs font-medium transition-all"
              :class="categoryMode === 'kb' ? 'bg-white text-gray-800 shadow-sm dark:bg-[#1e1e22] dark:text-gray-100' : 'text-gray-500 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-300'"
              @click="switchCategoryMode('kb')"
            >
              知识库
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
        <div class="flex items-center justify-between border-b border-gray-100 bg-white px-5 py-3 dark:border-gray-700 dark:bg-[#18181c]">
          <div class="flex items-center gap-2">
            <NInput
              v-model:value="searchKeyword"
              placeholder="搜索文档名称或关键词"
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
            <NSelect
              v-model:value="selectedFileType"
              :options="fileTypeSelectOptions"
              placeholder="全部类型"
              class="w-130px"
              size="small"
              @update:value="getDataByPage"
            />
            <NSelect
              v-model:value="selectedKbId"
              :options="kbSelectOptions"
              placeholder="全部知识库"
              class="w-160px"
              size="small"
              @update:value="getDataByPage"
            />
          </div>
          <NButton size="small" type="primary" @click="handleUpload">
            <template #icon><icon-carbon:add /></template>
            上传文档
          </NButton>
        </div>

        <div class="min-h-0 flex flex-1 overflow-hidden">
          <div class="min-w-0 flex-1 border-r border-gray-100 dark:border-gray-700">
            <NScrollbar class="h-full">
          <NSpin :show="loading">
            <div v-if="data.length === 0 && !loading" class="py-20">
              <NEmpty description="暂无文档">
                <template #extra>
                  <NButton size="small" type="primary" @click="handleUpload">上传文档</NButton>
                </template>
              </NEmpty>
            </div>
            <div v-else class="grid grid-cols-1 gap-3 p-4 xl:grid-cols-2">
              <div
                v-for="item in data"
                :key="item.fileMd5"
                class="cursor-pointer border rounded-xl bg-white p-4 transition-all dark:bg-[#1e1e22] hover:shadow-md"
                :class="
                  selectedDocument?.fileMd5 === item.fileMd5
                    ? 'border-primary-400 shadow-sm ring-1 ring-primary-200 dark:border-primary-500 dark:ring-primary-800'
                    : 'border-gray-200 hover:border-primary-300 dark:border-gray-700 dark:hover:border-primary-600'
                "
                @click="selectDocument(item)"
              >
                <div class="mb-3 flex items-start justify-between gap-3">
                  <div class="min-w-0 flex items-center gap-3">
                    <div
                      class="h-10 w-10 flex flex-shrink-0 items-center justify-center rounded-xl"
                      :class="getFileIconConfig(item.fileName).bg"
                    >
                      <span
                        class="text-xl"
                        :class="[getFileIconConfig(item.fileName).icon, getFileIconConfig(item.fileName).color]"
                      />
                    </div>
                    <div class="min-w-0">
                      <h3
                        class="truncate text-sm text-gray-900 font-semibold transition-colors dark:text-gray-100 hover:text-primary-600"
                        @click.stop="handleFilePreview(item.fileName, item.fileMd5)"
                      >
                        {{ item.fileName }}
                      </h3>
                      <p class="mt-0.5 truncate text-xs text-gray-500 dark:text-gray-400">
                        {{ getKbName(item.kbId) }}
                      </p>
                    </div>
                  </div>
                  <NTag size="small" :bordered="false" class="flex-shrink-0">{{ getFileType(item.fileName) }}</NTag>
                </div>

                <div class="grid grid-cols-3 gap-2 rounded-lg bg-gray-50 p-2 text-center dark:bg-[#18181c]">
                  <div>
                    <div class="truncate text-sm text-gray-900 font-semibold dark:text-gray-100">
                      {{ fileSize(item.totalSize || 0) }}
                    </div>
                    <div class="mt-0.5 text-[11px] text-gray-500 dark:text-gray-400">大小</div>
                  </div>
                  <div>
                    <div class="text-sm text-gray-900 font-semibold dark:text-gray-100">
                      {{ item.progress ?? 100 }}%
                    </div>
                    <div class="mt-0.5 text-[11px] text-gray-500 dark:text-gray-400">进度</div>
                  </div>
                  <div>
                    <div class="truncate text-sm text-gray-900 font-semibold dark:text-gray-100">
                      {{ getUploadStatusText(item.status) }}
                    </div>
                    <div class="mt-0.5 text-[11px] text-gray-500 dark:text-gray-400">状态</div>
                  </div>
                </div>

                <div class="mt-3 flex items-center justify-between text-xs text-gray-400 dark:text-gray-500">
                  <span class="min-w-0 flex items-center gap-1">
                    <icon-carbon:time class="text-sm" />
                    <span>{{ formatTime(item.createdAt || item.mergedAt) }}</span>
                  </span>
                  <div class="flex items-center gap-2">
                    <NButton
                      text
                      size="tiny"
                      type="primary"
                      @click.stop="handleFilePreview(item.fileName, item.fileMd5)"
                    >
                      预览
                    </NButton>
                    <NButton text size="tiny" type="info" @click.stop="downloadDocument(item)">下载</NButton>
                  </div>
                </div>
              </div>
            </div>
          </NSpin>
            </NScrollbar>
        </div>

        <div class="w-380px flex-shrink-0 bg-white dark:bg-[#18181c]">
          <template v-if="selectedDocument">
            <div class="border-b border-gray-100 px-5 py-3 dark:border-gray-700">
              <h2 class="text-sm text-gray-800 font-semibold dark:text-gray-100">文档详情</h2>
            </div>
            <div class="p-5 space-y-4">
              <div class="flex items-start gap-3">
                <div
                  class="h-12 w-12 flex flex-shrink-0 items-center justify-center rounded-xl"
                  :class="getFileIconConfig(selectedDocument.fileName).bg"
                >
                  <span
                    class="text-2xl"
                    :class="[
                      getFileIconConfig(selectedDocument.fileName).icon,
                      getFileIconConfig(selectedDocument.fileName).color
                    ]"
                  />
                </div>
                <div class="min-w-0">
                  <h3 class="break-all text-base text-gray-900 font-semibold dark:text-gray-50">
                    {{ selectedDocument.fileName }}
                  </h3>
                  <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">{{ getKbName(selectedDocument.kbId) }}</p>
                </div>
              </div>

              <div class="flex flex-wrap items-center gap-2">
                <NTag type="info" :bordered="false" size="small">{{ getFileType(selectedDocument.fileName) }}</NTag>
                <NTag :type="getUploadStatusType(selectedDocument.status)" :bordered="false" size="small">
                  {{ getUploadStatusText(selectedDocument.status) }}
                </NTag>
              </div>

              <NDivider class="!my-2" />

              <div class="grid grid-cols-3 gap-2">
                <div class="rounded-lg bg-gray-50 px-2 py-2 text-center dark:bg-[#1e1e22]">
                  <div class="truncate text-base text-gray-900 font-semibold dark:text-gray-100">
                    {{ fileSize(selectedDocument.totalSize || 0) }}
                  </div>
                  <div class="mt-0.5 text-xs text-gray-500 dark:text-gray-400">文件大小</div>
                </div>
                <div class="rounded-lg bg-gray-50 px-2 py-2 text-center dark:bg-[#1e1e22]">
                  <div class="text-base text-gray-900 font-semibold dark:text-gray-100">
                    {{ selectedDocument.progress ?? 100 }}%
                  </div>
                  <div class="mt-0.5 text-xs text-gray-500 dark:text-gray-400">处理进度</div>
                </div>
                <div class="rounded-lg bg-gray-50 px-2 py-2 text-center dark:bg-[#1e1e22]">
                  <div class="truncate text-base text-gray-900 font-semibold dark:text-gray-100">
                    {{ selectedDocument.uploadedChunks?.length || 0 }}
                  </div>
                  <div class="mt-0.5 text-xs text-gray-500 dark:text-gray-400">分片</div>
                </div>
              </div>

              <div class="text-sm space-y-2">
                <div class="flex items-center justify-between gap-4">
                  <span class="text-xs text-gray-500 dark:text-gray-400">知识库</span>
                  <span class="truncate text-gray-700 dark:text-gray-300">{{ getKbName(selectedDocument.kbId) }}</span>
                </div>
                <div class="flex items-center justify-between gap-4">
                  <span class="text-xs text-gray-500 dark:text-gray-400">权限</span>
                  <span class="text-gray-700 dark:text-gray-300">
                    {{ selectedDocument.isPublic || selectedDocument.public ? '公开' : '私有' }}
                  </span>
                </div>
                <div class="flex items-center justify-between gap-4">
                  <span class="text-xs text-gray-500 dark:text-gray-400">组织标签</span>
                  <span class="truncate text-gray-700 dark:text-gray-300">
                    {{ selectedDocument.orgTagName || selectedDocument.orgTag || '-' }}
                  </span>
                </div>
                <div class="flex items-center justify-between gap-4">
                  <span class="text-xs text-gray-500 dark:text-gray-400">上传时间</span>
                  <span class="text-gray-700 dark:text-gray-300">{{ formatTime(selectedDocument.createdAt) }}</span>
                </div>
                <div class="flex items-center justify-between gap-4">
                  <span class="text-xs text-gray-500 dark:text-gray-400">完成时间</span>
                  <span class="text-gray-700 dark:text-gray-300">{{ formatTime(selectedDocument.mergedAt) }}</span>
                </div>
                <div class="flex items-center justify-between gap-4">
                  <span class="text-xs text-gray-500 dark:text-gray-400">MD5</span>
                  <span class="truncate text-gray-700 dark:text-gray-300">{{ selectedDocument.fileMd5 }}</span>
                </div>
              </div>

              <div class="flex flex-wrap gap-2 pt-2">
                <NButton
                  size="small"
                  type="primary"
                  @click="handleFilePreview(selectedDocument.fileName, selectedDocument.fileMd5)"
                >
                  <template #icon><icon-carbon:view /></template>
                  预览
                </NButton>
                <NButton size="small" secondary type="info" @click="downloadDocument(selectedDocument)">
                  <template #icon><icon-mdi-download /></template>
                  下载
                </NButton>
                <NPopconfirm @positive-click="handleDelete(selectedDocument.fileMd5)">
                  <template #trigger>
                    <NButton size="small" secondary type="error">
                      <template #icon><icon-carbon:trash-can /></template>
                      删除
                    </NButton>
                  </template>
                  确认删除该文档吗？此操作不可撤销。
                </NPopconfirm>
              </div>
            </div>
          </template>
          <div v-else class="py-20">
            <NEmpty description="选择一个文档查看详情" />
          </div>
        </div>
        </div>

        <!-- 分页 -->
        <div class="flex justify-end border-t border-gray-100 bg-white px-4 py-3 dark:border-gray-700 dark:bg-[#18181c]">
          <NPagination v-bind="mobilePagination" />
        </div>
      </div>
    </div>

    <!-- 上传文档对话框 -->
    <UploadDialog v-model:visible="uploadVisible" :active-kb-id="activeKbForUpload" @submitted="getData" />

    <!-- 文件预览弹窗 -->
    <NModal
      v-model:show="previewVisible"
      preset="card"
      class="doc-preview-modal"
      :style="previewModalStyle"
      :bordered="false"
    >
      <template #header>
        <div class="w-full flex items-center justify-between">
          <div class="flex items-center gap-2">
            <SvgIcon :local-icon="getPreviewFileIcon()" class="text-16" />
            <span class="max-w-590px truncate font-medium">{{ previewFileName }}</span>
          </div>
          <NButton size="small" @click="handleDownloadPreview">
            <template #icon>
              <icon-mdi-download />
            </template>
            下载
          </NButton>
        </div>
      </template>
      <div class="doc-preview-body">
        <FilePreview :file-name="previewFileName" :file-md5="previewFileMd5" :visible="previewVisible" />
      </div>
    </NModal>
  </div>
</template>

<style scoped lang="scss">
.doc-management-page {
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
}

.min-h-520px {
  min-height: 520px;
}

:deep(.doc-preview-modal) {
  border-radius: 10px;
}

:deep(.doc-preview-modal .n-card-header) {
  padding: 14px 18px;
}

:deep(.doc-preview-modal .n-card__content) {
  padding: 0;
}

.doc-preview-body {
  max-height: min(68vh, 640px);
  overflow: auto;
  padding: 12px 14px 14px;
}

:deep(.doc-preview-body img),
:deep(.doc-preview-body canvas),
:deep(.doc-preview-body iframe),
:deep(.doc-preview-body video) {
  max-width: 100%;
}

@media (max-width: 768px) {
  :deep(.doc-preview-modal) {
    max-width: calc(100vw - 24px);
  }

  .doc-preview-body {
    max-height: 74vh;
    padding: 12px;
  }
}
</style>

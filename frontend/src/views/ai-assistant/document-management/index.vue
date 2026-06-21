<script setup lang="tsx">
import { NButton, NDivider, NEmpty, NInput, NModal, NPagination, NPopconfirm, NSelect, NSpin, NTag } from 'naive-ui';
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

// --------- 文件预览 ---------
const previewVisible = ref(false);
const previewFileName = ref('');
const previewFileMd5 = ref('');

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
  doc: { icon: 'icon-carbon-document-word', bg: 'bg-blue-50', color: 'text-blue-600' },
  docx: { icon: 'icon-carbon-document-word', bg: 'bg-blue-50', color: 'text-blue-600' },
  xls: { icon: 'icon-carbon-document-spreadsheet', bg: 'bg-green-50', color: 'text-green-600' },
  xlsx: { icon: 'icon-carbon-document-spreadsheet', bg: 'bg-green-50', color: 'text-green-600' },
  ppt: { icon: 'icon-carbon-document-presentation', bg: 'bg-orange-50', color: 'text-orange-500' },
  pptx: { icon: 'icon-carbon-document-presentation', bg: 'bg-orange-50', color: 'text-orange-500' },
  txt: { icon: 'icon-carbon-document-text', bg: 'bg-gray-50', color: 'text-gray-500' },
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
  <div class="doc-management-page h-full flex flex-col overflow-y-auto bg-gray-50 dark:bg-gray-900">
    <div class="min-h-0 flex flex-col flex-1 px-8 py-6">
      <!-- 标题 -->
      <h1 class="mb-5 text-2xl text-gray-900 font-bold dark:text-white">文档管理</h1>

      <!-- Tab -->
      <div class="mb-5 flex items-center gap-0 border-b border-gray-200 dark:border-gray-700">
        <button
          v-for="tab in tabOptions"
          :key="tab.value"
          class="border-b-2 bg-transparent px-5 py-2.5 text-sm font-medium transition-colors -mb-px"
          :class="[
            activeTab === tab.value
              ? 'border-blue-500 text-blue-600 dark:text-blue-400'
              : 'border-transparent text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-300'
          ]"
          @click="onTabChange(tab.value)"
        >
          {{ tab.label }}
        </button>
      </div>

      <!-- 工具栏 -->
      <div class="mb-4 flex items-center gap-3">
        <!-- 搜索框 -->
        <NInput
          v-model:value="searchKeyword"
          placeholder="搜索文档名称或关键词"
          clearable
          class="max-w-220px"
          @input="debouncedSearch"
          @clear="debouncedSearch"
        >
          <template #prefix>
            <icon-carbon:search class="text-gray-400" />
          </template>
        </NInput>

        <!-- 文件类型 -->
        <NSelect
          v-model:value="selectedFileType"
          :options="fileTypeSelectOptions"
          placeholder="全部类型"
          class="w-130px"
          size="medium"
          @update:value="getDataByPage"
        />

        <!-- 所属知识库 -->
        <NSelect
          v-model:value="selectedKbId"
          :options="kbSelectOptions"
          placeholder="全部知识库"
          class="w-150px"
          size="medium"
          @update:value="getDataByPage"
        />

        <div class="flex-1" />

        <!-- 上传文档按钮 -->
        <NButton type="primary" @click="handleUpload">
          <template #icon>
            <icon-carbon:add />
          </template>
          上传文档
        </NButton>
      </div>

      <!-- 卡片列表 + 详情 -->
      <div
        class="min-h-520px overflow-hidden border border-gray-100 rounded-xl bg-white shadow-sm lg:flex dark:border-gray-700 dark:bg-gray-800"
      >
        <div class="min-w-0 flex-1 border-gray-100 lg:border-r dark:border-gray-700">
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
        </div>

        <div class="w-full flex-shrink-0 bg-white lg:w-380px dark:bg-[#18181c]">
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

        <!-- 分页 -->
        <div class="flex justify-end border-t border-gray-100 px-4 py-3 lg:hidden dark:border-gray-700">
          <NPagination v-bind="mobilePagination" />
        </div>
      </div>

      <div class="hidden justify-end px-4 py-3 lg:flex">
        <NPagination v-bind="mobilePagination" />
      </div>
    </div>

    <!-- 上传文档对话框 -->
    <UploadDialog v-model:visible="uploadVisible" :active-kb-id="activeKbForUpload" @submitted="getData" />

    <!-- 文件预览弹窗 -->
    <NModal v-model:show="previewVisible" preset="card" class="doc-preview-modal">
      <template #header>
        <div class="w-full flex items-center justify-between">
          <div class="flex items-center gap-2">
            <SvgIcon :local-icon="getPreviewFileIcon()" class="text-16" />
            <span class="font-medium">{{ previewFileName }}</span>
          </div>
          <NButton size="small" @click="handleDownloadPreview">
            <template #icon>
              <icon-mdi-download />
            </template>
            下载
          </NButton>
        </div>
      </template>
      <div>
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
  width: 80%;
  max-width: 1000px;
}
</style>

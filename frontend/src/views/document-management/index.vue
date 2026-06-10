<script setup lang="tsx">
import { NButton, NDataTable, NInput, NModal, NPagination, NPopconfirm, NSelect, NTag, NTooltip } from 'naive-ui';
import { fakePaginationRequest } from '@/service/request';
import { UploadStatus } from '@/enum';
import FilePreview from '@/components/custom/file-preview.vue';
import UploadDialog from '@/views/knowledge-base/modules/upload-dialog.vue';
import { fetchGetKnowledgeBases, fetchGetKnowledgeBaseFilterOptions } from '@/service/api/knowledge-base';
import { getFileExt } from '@/utils/common';
import debounce from 'lodash-es/debounce';

const appStore = useAppStore();

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
  if (!kbRes.error && kbRes.data) knowledgeBases.value = kbRes.data.records || kbRes.data.content || kbRes.data.data || [];
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

function closeFilePreview() {
  previewVisible.value = false;
  previewFileName.value = '';
  previewFileMd5.value = '';
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
    window.$message?.error('下载失败：' + (err.message || '网络错误'));
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
  pdf:  { icon: 'icon-carbon-document-pdf',          bg: 'bg-red-50',     color: 'text-red-500' },
  doc:  { icon: 'icon-carbon-document-word',         bg: 'bg-blue-50',    color: 'text-blue-600' },
  docx: { icon: 'icon-carbon-document-word',         bg: 'bg-blue-50',    color: 'text-blue-600' },
  xls:  { icon: 'icon-carbon-document-spreadsheet',  bg: 'bg-green-50',   color: 'text-green-600' },
  xlsx: { icon: 'icon-carbon-document-spreadsheet',  bg: 'bg-green-50',   color: 'text-green-600' },
  ppt:  { icon: 'icon-carbon-document-presentation', bg: 'bg-orange-50',  color: 'text-orange-500' },
  pptx: { icon: 'icon-carbon-document-presentation', bg: 'bg-orange-50',  color: 'text-orange-500' },
  txt:  { icon: 'icon-carbon-document-text',         bg: 'bg-gray-50',    color: 'text-gray-500' },
  md:   { icon: 'icon-carbon-document',              bg: 'bg-slate-50',   color: 'text-slate-600' },
  jpg:  { icon: 'icon-carbon-image',                 bg: 'bg-purple-50',  color: 'text-purple-500' },
  jpeg: { icon: 'icon-carbon-image',                 bg: 'bg-purple-50',  color: 'text-purple-500' },
  png:  { icon: 'icon-carbon-image',                 bg: 'bg-purple-50',  color: 'text-purple-500' },
  gif:  { icon: 'icon-carbon-image',                 bg: 'bg-purple-50',  color: 'text-purple-500' },
  csv:  { icon: 'icon-carbon-data-table',            bg: 'bg-teal-50',    color: 'text-teal-600' },
  json: { icon: 'icon-carbon-document',              bg: 'bg-yellow-50',  color: 'text-yellow-600' },
  zip:  { icon: 'icon-carbon-zip',                   bg: 'bg-gray-50',    color: 'text-gray-500' },
};

function renderIcon(fileName: string) {
  const ext = getFileExt(fileName)?.toLowerCase();
  const cfg = ext
    ? (fileIconMap[ext] ?? { icon: 'icon-carbon-document', bg: 'bg-gray-50', color: 'text-gray-400' })
    : { icon: 'icon-carbon-document', bg: 'bg-gray-50', color: 'text-gray-400' };
  return (
    <div class={`w-7 h-7 rounded-lg ${cfg.bg} flex items-center justify-center flex-shrink-0`}>
      <span class={`${cfg.icon} ${cfg.color} text-base`} />
    </div>
  );
}

const store = useKnowledgeBaseStore();
const { tasks } = storeToRefs(store);

const { columns, data, getData, getDataByPage, loading, mobilePagination } = useTable({
  apiFn,
  immediate: false,
  columns: () => [
    {
      key: 'fileName',
      title: '文档名称',
      width: 200,
      align: 'left',
      titleAlign: 'left',
      render: row => {
        const displayName = row.fileName.length > 20 ? row.fileName.slice(0, 20) + '…' : row.fileName;
        return (
          <div class="flex items-center gap-1.5">
            {renderIcon(row.fileName)}
            <NTooltip placement="top" trigger="hover" style="max-width: 420px">
              {{
                trigger: () => (
                  <span
                    class="cursor-pointer hover:text-primary transition-colors truncate"
                    style="max-width: 140px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; display: inline-block;"
                    onClick={() => handleFilePreview(row.fileName, row.fileMd5)}
                  >
                    {displayName}
                  </span>
                ),
                default: () => row.fileName
              }}
            </NTooltip>
          </div>
        );
      }
    },
    {
      key: 'kbId',
      title: '知识库',
      width: 140,
      align: 'left',
      titleAlign: 'left',
      render: row => {
        const kb = knowledgeBases.value.find(k => k.kbId === row.kbId);
        const name = kb?.name || row.kbId || '-';
        return (
          <NTooltip placement="top" trigger="hover" style="max-width: 300px">
            {{
              trigger: () => (
                <span style="overflow: hidden; white-space: nowrap; text-overflow: ellipsis; max-width: 120px; display: inline-block;">
                  {name}
                </span>
              ),
              default: () => name
            }}
          </NTooltip>
        );
      }
    },
    {
      key: 'status',
      title: '类型',
      width: 80,
      align: 'center',
      titleAlign: 'center',
      render: row => {
        const ext = getFileExt(row.fileName);
        return <NTag size="small">{ext?.toUpperCase() || '-'}</NTag>;
      }
    },
    {
      key: 'totalSize',
      title: '大小',
      width: 100,
      align: 'center',
      titleAlign: 'center',
      render: row => fileSize(row.totalSize)
    },
    {
      key: 'createdAt',
      title: '更新时间',
      width: 160,
      align: 'center',
      titleAlign: 'center',
      render: row => dayjs(row.createdAt).format('YYYY-MM-DD HH:mm')
    },
    {
      key: 'operate',
      title: '操作',
      width: 160,
      align: 'center',
      titleAlign: 'center',
      render: row => (
        <div class="flex items-center justify-center gap-2">
          <NButton
            text
            size="small"
            type="primary"
            onClick={() => handleFilePreview(row.fileName, row.fileMd5)}
          >
            预览
          </NButton>
          <NButton
            text
            size="small"
            type="info"
            onClick={async () => {
              previewFileName.value = row.fileName;
              previewFileMd5.value = row.fileMd5;
              await handleDownloadPreview();
            }}
          >
            下载
          </NButton>
          <NPopconfirm onPositiveClick={() => handleDelete(row.fileMd5)}>
            {{
              trigger: () => (
                <NButton text size="small" type="error">
                  删除
                </NButton>
              ),
              default: () => '确认删除该文档吗？此操作不可撤销。'
            }}
          </NPopconfirm>
        </div>
      )
    }
  ]
});

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

onMounted(async () => {
  await loadFilterOptions();
  await getData();
});
</script>

<template>
  <div class="doc-management-page h-full flex flex-col bg-gray-50 dark:bg-gray-900 overflow-y-auto">
    <div class="px-8 py-6 flex-1 min-h-0 flex flex-col">
      <!-- 标题 -->
      <h1 class="text-2xl font-bold text-gray-900 dark:text-white mb-5">文档管理</h1>

      <!-- Tab -->
      <div class="flex items-center gap-0 mb-5 border-b border-gray-200 dark:border-gray-700">
        <button
          v-for="tab in tabOptions"
          :key="tab.value"
          :class="[
            'px-5 py-2.5 text-sm font-medium border-b-2 transition-colors -mb-px bg-transparent',
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
      <div class="flex items-center gap-3 mb-4">
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

      <!-- 表格 -->
      <div class="bg-white dark:bg-gray-800 rounded-xl shadow-sm border border-gray-100 dark:border-gray-700 overflow-hidden">
        <NDataTable
          striped
          :columns="columns"
          :data="data"
          size="medium"
          :scroll-x="800"
          :loading="loading"
          :row-key="row => row.fileMd5"
          remote
          :pagination="false"
          class="doc-table"
        />

        <!-- 分页 -->
        <div class="flex justify-end px-4 py-3 border-t border-gray-100 dark:border-gray-700">
          <NPagination v-bind="mobilePagination" />
        </div>
      </div>
    </div>

    <!-- 上传文档对话框 -->
    <UploadDialog
      v-model:visible="uploadVisible"
      :active-kb-id="activeKbForUpload"
      @submitted="getData"
    />

    <!-- 文件预览弹窗 -->
    <NModal v-model:show="previewVisible" preset="card" style="width: 80%; max-width: 1000px;">
      <template #header>
        <div class="flex items-center justify-between w-full">
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
        <FilePreview
          :file-name="previewFileName"
          :file-md5="previewFileMd5"
          :visible="previewVisible"
        />
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
    &:hover { background: #9ca3af; }
  }
}

/* 表格行间距优化 */
:deep(.doc-table) {
  .n-data-table-th,
  .n-data-table-td {
    padding-top: 12px;
    padding-bottom: 12px;
  }

  // 首列左侧与页面边距对齐
  th:first-child,
  td:first-child {
    padding-left: 40px !important;
  }
}
</style>

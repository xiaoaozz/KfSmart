<script setup lang="tsx">
import { ref, computed, onMounted } from 'vue';
import type { UploadFileInfo } from 'naive-ui';
import { NButton, NModal, NSpin, NEmpty, NSwitch, NTag, NProgress, NUpload } from 'naive-ui';
import { uploadAccept } from '@/constants/common';
import { fakePaginationRequest } from '@/service/request';
import { UploadStatus } from '@/enum';
import FileCard from '@/components/modern/FileCard.vue';
import AnimatedButton from '@/components/modern/AnimatedButton.vue';
import FilePreview from '@/components/custom/file-preview.vue';
import UploadDialog from './modules/upload-dialog.vue';
import SearchDialog from './modules/search-dialog.vue';

const appStore = useAppStore();

// 视图模式: grid | list
const viewMode = ref<'grid' | 'list'>('grid');

// 文件预览相关状态
const previewVisible = ref(false);
const previewFileName = ref('');
const previewFileMd5 = ref('');

function apiFn() {
  return fakePaginationRequest<Api.KnowledgeBase.List>({ url: '/documents/uploads' });
}

// 处理文件预览
function handleFilePreview(fileName: string, fileMd5: string) {
  previewFileName.value = fileName;
  previewFileMd5.value = fileMd5;
  previewVisible.value = true;
}

// 关闭文件预览
function closeFilePreview() {
  previewVisible.value = false;
  previewFileName.value = '';
  previewFileMd5.value = '';
}

const { data, getData, loading } = useTable({
  apiFn,
  immediate: false
});

const store = useKnowledgeBaseStore();
const { tasks } = storeToRefs(store);

onMounted(async () => {
  await getList();
});

/** 异步获取列表函数 */
async function getList() {
  await getData();

  if (data.value.length === 0) {
    tasks.value = [];
    return;
  }

  data.value.forEach(item => {
    if (item.status === UploadStatus.Completed) {
      const index = tasks.value.findIndex(task => task.fileMd5 === item.fileMd5);
      if (index !== -1) {
        tasks.value[index].status = UploadStatus.Completed;
      } else {
        tasks.value.push(item);
      }
    } else if (!tasks.value.some(task => task.fileMd5 === item.fileMd5)) {
      item.status = UploadStatus.Break;
      tasks.value.push(item);
    }
  });
}

async function handleDelete(fileMd5: string) {
  const index = tasks.value.findIndex(task => task.fileMd5 === fileMd5);

  if (index !== -1) {
    tasks.value[index].requestIds?.forEach(requestId => {
      request.cancelRequest(requestId);
    });
  }

  if (tasks.value[index].uploadedChunks && tasks.value[index].uploadedChunks.length === 0) {
    tasks.value.splice(index, 1);
    return;
  }

  const { error } = await request({ url: `/documents/${fileMd5}`, method: 'DELETE' });
  if (!error) {
    tasks.value.splice(index, 1);
    window.$message?.success('删除成功');
    await getData();
  }
}

// 下载文件
async function handleDownload(fileMd5: string, fileName: string) {
  try {
    window.$message?.loading('正在获取下载链接...', { duration: 0 });
    
    const authStore = useAuthStore();
    const { error, data } = await request<Api.Document.DownloadResponse>({
      url: 'documents/download-by-md5',
      params: {
        fileMd5,
        token: authStore.token
      },
      baseURL: '/proxy-api'
    });

    window.$message?.destroyAll();

    if (!error && data?.downloadUrl) {
      window.open(data.downloadUrl, '_blank');
      window.$message?.success('下载链接已打开');
    } else {
      window.$message?.error('获取下载链接失败');
    }
  } catch (err) {
    window.$message?.destroyAll();
    window.$message?.error('下载失败');
  }
}

// 文件上传
const uploadVisible = ref(false);
function handleUpload() {
  uploadVisible.value = true;
}

// 检索知识库
const searchVisible = ref(false);
function handleSearch() {
  searchVisible.value = true;
}

// 获取文件状态映射
function getFileStatus(status: UploadStatus) {
  const statusMap = {
    [UploadStatus.Completed]: 'success' as const,
    [UploadStatus.Break]: 'error' as const,
    [UploadStatus.Pending]: 'uploading' as const,
    [UploadStatus.Uploading]: 'uploading' as const
  };
  return statusMap[status] || 'pending' as const;
}

// 格式化文件大小
function fileSize(bytes: number): string {
  if (bytes === 0) return '0 B';
  const k = 1024;
  const sizes = ['B', 'KB', 'MB', 'GB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return `${(bytes / Math.pow(k, i)).toFixed(2)} ${sizes[i]}`;
}

// 文件续传
function renderResumeUploadButton(row: Api.KnowledgeBase.UploadTask) {
  if (row.status === UploadStatus.Break) {
    if (row.file) {
      return (
        <AnimatedButton size="xs" variant="primary" onClick={() => resumeUpload(row)}>
          续传
        </AnimatedButton>
      );
    }
    return (
      <NUpload
        show-file-list={false}
        default-upload={false}
        accept={uploadAccept}
        onBeforeUpload={options => onBeforeUpload(options, row)}
        class="inline-block"
      >
        <AnimatedButton size="xs" variant="primary">
          续传
        </AnimatedButton>
      </NUpload>
    );
  }
  return null;
}

function resumeUpload(row: Api.KnowledgeBase.UploadTask) {
  row.status = UploadStatus.Pending;
  store.startUpload();
}

async function onBeforeUpload(
  options: { file: UploadFileInfo; fileList: UploadFileInfo[] },
  row: Api.KnowledgeBase.UploadTask
) {
  const md5 = await calculateMD5(options.file.file!);
  if (md5 !== row.fileMd5) {
    window.$message?.error('两次上传的文件不一致');
    return false;
  }
  loading.value = true;
  const { error, data: progress } = await request<Api.KnowledgeBase.Progress>({
    url: '/upload/status',
    params: { file_md5: row.fileMd5 }
  });
  if (!error) {
    row.file = options.file.file!;
    row.status = UploadStatus.Pending;
    row.progress = progress.progress;
    row.uploadedChunks = progress.uploaded;
    store.startUpload();
    loading.value = false;
    return true;
  }
  loading.value = false;
  return false;
}
</script>

<template>
  <div class="knowledge-base-modern h-full flex flex-col overflow-hidden">
    <!-- 顶部工具栏 -->
    <div class="toolbar">
      <div class="toolbar-left">
        <h2 class="page-title">
          <div class="i-carbon:document-multiple text-2xl" />
          <span>知识库</span>
          <span class="file-count">{{ tasks.length }} 个文件</span>
        </h2>
      </div>

      <div class="toolbar-right">
        <!-- 搜索按钮 -->
        <AnimatedButton variant="secondary" size="sm" @click="handleSearch">
          <template #icon>
            <div class="i-carbon:search" />
          </template>
          检索知识库
        </AnimatedButton>

        <!-- 视图切换 -->
        <div class="view-switcher">
          <button
            :class="['view-btn', { active: viewMode === 'grid' }]"
            title="网格视图"
            @click="viewMode = 'grid'"
          >
            <div class="i-carbon:grid text-lg" />
          </button>
          <button
            :class="['view-btn', { active: viewMode === 'list' }]"
            title="列表视图"
            @click="viewMode = 'list'"
          >
            <div class="i-carbon:list text-lg" />
          </button>
        </div>

        <!-- 刷新按钮 -->
        <button class="icon-btn" title="刷新" :disabled="loading" @click="getList">
          <div :class="['i-carbon:refresh', loading && 'animate-spin']" />
        </button>

        <!-- 上传按钮 -->
        <AnimatedButton variant="primary" size="sm" @click="handleUpload">
          <template #icon>
            <div class="i-carbon:cloud-upload" />
          </template>
          上传文件
        </AnimatedButton>
      </div>
    </div>

    <!-- 内容区域 -->
    <div class="content-area flex-1 overflow-auto scrollbar-modern">
      <NSpin :show="loading">
        <!-- 空状态 -->
        <div v-if="!loading && tasks.length === 0" class="empty-state">
          <div class="empty-icon">
            <div class="i-carbon:document-blank text-6xl text-primary-300" />
          </div>
          <h3 class="text-xl font-600 text-gray-600 dark:text-gray-400 mb-2">暂无文件</h3>
          <p class="text-sm text-gray-400 dark:text-gray-500 mb-6">上传您的第一个文档开始使用吧</p>
          <AnimatedButton variant="primary" @click="handleUpload">
            <template #icon>
              <div class="i-carbon:cloud-upload" />
            </template>
            立即上传
          </AnimatedButton>
        </div>

        <!-- 网格视图 -->
        <div v-else-if="viewMode === 'grid'" class="grid-view">
          <FileCard
            v-for="task in tasks"
            :key="task.fileMd5"
            :file-name="task.fileName"
            :file-size="fileSize(task.totalSize)"
            :file-md5="task.fileMd5"
            :progress="task.progress"
            :status="getFileStatus(task.status)"
            :upload-time="dayjs(task.createdAt).format('YYYY-MM-DD HH:mm')"
            :previewable="task.status === UploadStatus.Completed"
            :deletable="true"
            @preview="handleFilePreview(task.fileName, task.fileMd5)"
            @delete="handleDelete(task.fileMd5)"
            @download="handleDownload(task.fileMd5, task.fileName)"
          >
            <!-- 续传按钮插槽 -->
            <template v-if="task.status === UploadStatus.Break" #actions>
              <component :is="renderResumeUploadButton(task)" />
            </template>
          </FileCard>
        </div>

        <!-- 列表视图 -->
        <div v-else class="list-view">
          <div class="list-header">
            <div class="list-col col-name">文件名</div>
            <div class="list-col col-size">大小</div>
            <div class="list-col col-status">状态</div>
            <div class="list-col col-time">上传时间</div>
            <div class="list-col col-actions">操作</div>
          </div>

          <div class="list-body">
            <div v-for="task in tasks" :key="task.fileMd5" class="list-row">
              <div class="list-col col-name">
                <div class="file-info">
                  <div class="i-carbon:document text-2xl text-primary-500" />
                  <div class="file-details">
                    <div class="file-name" :title="task.fileName">{{ task.fileName }}</div>
                    <div v-if="task.fileMd5" class="file-md5">MD5: {{ task.fileMd5.substring(0, 12) }}...</div>
                  </div>
                </div>
              </div>
              
              <div class="list-col col-size">
                {{ fileSize(task.totalSize) }}
              </div>
              
              <div class="list-col col-status">
                <NTag v-if="task.status === UploadStatus.Completed" type="success" size="small">
                  已完成
                </NTag>
                <NTag v-else-if="task.status === UploadStatus.Break" type="error" size="small">
                  上传中断
                </NTag>
                <div v-else class="flex items-center gap-2">
                  <NProgress :percentage="task.progress" :show-indicator="false" style="width: 80px" />
                  <span class="text-xs text-gray-500">{{ task.progress }}%</span>
                </div>
              </div>
              
              <div class="list-col col-time">
                {{ dayjs(task.createdAt).format('YYYY-MM-DD HH:mm') }}
              </div>
              
              <div class="list-col col-actions">
                <div class="action-buttons">
                  <component
                    v-if="task.status === UploadStatus.Break"
                    :is="renderResumeUploadButton(task)"
                  />
                  <button
                    v-if="task.status === UploadStatus.Completed"
                    class="action-btn preview"
                    title="预览"
                    @click="handleFilePreview(task.fileName, task.fileMd5)"
                  >
                    <div class="i-carbon:view" />
                  </button>
                  <button
                    v-if="task.status === UploadStatus.Completed"
                    class="action-btn download"
                    title="下载"
                    @click="handleDownload(task.fileMd5, task.fileName)"
                  >
                    <div class="i-carbon:download" />
                  </button>
                  <button
                    class="action-btn delete"
                    title="删除"
                    @click="handleDelete(task.fileMd5)"
                  >
                    <div class="i-carbon:trash-can" />
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>
      </NSpin>
    </div>

    <!-- 上传对话框 -->
    <UploadDialog v-model:visible="uploadVisible" />
    
    <!-- 搜索对话框 -->
    <SearchDialog v-model:visible="searchVisible" />
    
    <!-- 文件预览弹窗 -->
    <NModal
      v-model:show="previewVisible"
      preset="card"
      title="文件预览"
      class="preview-modal"
      :style="{ width: '80%', maxWidth: '1200px' }"
    >
      <FilePreview
        :file-name="previewFileName"
        :file-md5="previewFileMd5"
        :visible="previewVisible"
        @close="closeFilePreview"
      />
    </NModal>
  </div>
</template>

<style scoped lang="scss">
.knowledge-base-modern {
  background: linear-gradient(to bottom, #f7fafc 0%, #ffffff 100%);

  .dark & {
    background: linear-gradient(to bottom, #18181b 0%, #27272a 100%);
  }
}

/* 工具栏 */
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 24px;
  background: white;
  border-bottom: 1px solid rgba(0, 0, 0, 0.06);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);

  .dark & {
    background: #27272a;
    border-bottom-color: rgba(255, 255, 255, 0.06);
  }
}

.toolbar-left {
  .page-title {
    display: flex;
    align-items: center;
    gap: 12px;
    font-size: 24px;
    font-weight: 700;
    color: #1f1f1f;
    margin: 0;

    .dark & {
      color: #f1f1f1;
    }

    .i-carbon-document-multiple {
      color: #667eea;
    }

    .file-count {
      font-size: 14px;
      font-weight: 500;
      color: #999;
      padding: 4px 12px;
      background: rgba(102, 126, 234, 0.1);
      border-radius: 12px;
    }
  }
}

.toolbar-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

/* 视图切换器 */
.view-switcher {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px;
  background: rgba(102, 126, 234, 0.08);
  border-radius: 8px;

  .view-btn {
    padding: 6px 10px;
    border: none;
    background: transparent;
    color: #667eea;
    border-radius: 6px;
    cursor: pointer;
    transition: all 0.2s ease;
    display: flex;
    align-items: center;
    justify-content: center;

    &:hover {
      background: rgba(102, 126, 234, 0.15);
    }

    &.active {
      background: #667eea;
      color: white;
      box-shadow: 0 2px 8px rgba(102, 126, 234, 0.3);
    }
  }
}

/* 图标按钮 */
.icon-btn {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid rgba(102, 126, 234, 0.2);
  background: transparent;
  color: #667eea;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s ease;

  &:hover:not(:disabled) {
    background: rgba(102, 126, 234, 0.1);
    border-color: rgba(102, 126, 234, 0.4);
  }

  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }
}

/* 内容区域 */
.content-area {
  padding: 24px;
}

/* 空状态 */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 400px;
  text-align: center;

  .empty-icon {
    width: 120px;
    height: 120px;
    display: flex;
    align-items: center;
    justify-content: center;
    background: linear-gradient(135deg, rgba(102, 126, 234, 0.1) 0%, rgba(79, 172, 254, 0.1) 100%);
    border-radius: 50%;
    margin-bottom: 24px;
    animation: float 3s ease-in-out infinite;
  }
}

/* 网格视图 */
.grid-view {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 20px;
  animation: fadeIn 0.3s ease;
}

/* 列表视图 */
.list-view {
  background: white;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
  animation: fadeIn 0.3s ease;

  .dark & {
    background: #27272a;
  }
}

.list-header {
  display: grid;
  grid-template-columns: 2fr 120px 150px 180px 180px;
  gap: 16px;
  padding: 16px 20px;
  background: rgba(102, 126, 234, 0.05);
  font-weight: 600;
  font-size: 14px;
  color: #667eea;
  border-bottom: 1px solid rgba(0, 0, 0, 0.06);

  .dark & {
    border-bottom-color: rgba(255, 255, 255, 0.06);
  }
}

.list-body {
  .list-row {
    display: grid;
    grid-template-columns: 2fr 120px 150px 180px 180px;
    gap: 16px;
    padding: 16px 20px;
    border-bottom: 1px solid rgba(0, 0, 0, 0.06);
    transition: all 0.2s ease;

    &:hover {
      background: rgba(102, 126, 234, 0.03);
    }

    &:last-child {
      border-bottom: none;
    }

    .dark & {
      border-bottom-color: rgba(255, 255, 255, 0.06);

      &:hover {
        background: rgba(102, 126, 234, 0.08);
      }
    }
  }
}

.list-col {
  display: flex;
  align-items: center;
  font-size: 14px;
  color: #333;

  .dark & {
    color: #f1f1f1;
  }

  &.col-name {
    min-width: 0;
  }

  &.col-actions {
    justify-content: flex-end;
  }
}

/* 文件信息 */
.file-info {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;

  .file-details {
    flex: 1;
    min-width: 0;
  }

  .file-name {
    font-weight: 500;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .file-md5 {
    font-size: 12px;
    color: #999;
    font-family: 'Monaco', 'Courier New', monospace;
  }
}

/* 操作按钮组 */
.action-buttons {
  display: flex;
  align-items: center;
  gap: 8px;
}

.action-btn {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s ease;
  font-size: 16px;

  &.preview {
    background: rgba(102, 126, 234, 0.1);
    color: #667eea;

    &:hover {
      background: rgba(102, 126, 234, 0.2);
    }
  }

  &.download {
    background: rgba(79, 172, 254, 0.1);
    color: #4facfe;

    &:hover {
      background: rgba(79, 172, 254, 0.2);
    }
  }

  &.delete {
    background: rgba(245, 34, 45, 0.1);
    color: #f5222d;

    &:hover {
      background: rgba(245, 34, 45, 0.2);
    }
  }
}

/* 预览弹窗 */
.preview-modal {
  :deep(.n-card__content) {
    padding: 0;
    max-height: 80vh;
    overflow: auto;
  }
}

/* 动画 */
@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes float {
  0%, 100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-10px);
  }
}

/* 响应式适配 */
@media (max-width: 1200px) {
  .grid-view {
    grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  }
}

@media (max-width: 768px) {
  .toolbar {
    flex-direction: column;
    gap: 16px;
    align-items: stretch;
  }

  .toolbar-left,
  .toolbar-right {
    width: 100%;
  }

  .toolbar-right {
    justify-content: space-between;
  }

  .grid-view {
    grid-template-columns: 1fr;
  }

  .list-view {
    overflow-x: auto;

    .list-header,
    .list-row {
      min-width: 800px;
    }
  }

  .content-area {
    padding: 16px;
  }
}
</style>

<style scoped lang="scss">
.file-list-container {
  transition: width 0.3s ease;
}

:deep() {
  .n-progress-icon.n-progress-icon--as-text {
    white-space: nowrap;
  }
}
</style>

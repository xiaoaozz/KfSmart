<script setup lang="tsx">
import type { UploadFileInfo } from 'naive-ui';
import { NButton, NEllipsis, NModal, NPopconfirm, NProgress, NTag, NTooltip, NUpload } from 'naive-ui';
import { uploadAccept } from '@/constants/common';
import { fakePaginationRequest } from '@/service/request';
import { UploadStatus } from '@/enum';
import SvgIcon from '@/components/custom/svg-icon.vue';
import FilePreview from '@/components/custom/file-preview.vue';
import UploadDialog from './modules/upload-dialog.vue';
import CreateKbDialog from './modules/create-kb-dialog.vue';
import SearchDialog from './modules/search-dialog.vue';
import { fetchGetKnowledgeBases, fetchRefreshKnowledgeBaseStats, fetchGetKnowledgeBaseFilterOptions } from '@/service/api/knowledge-base';

const appStore = useAppStore();

// 文件预览相关状态
const previewVisible = ref(false);
const previewFileName = ref('');
const previewFileMd5 = ref('');

// 筛选器状态
const selectedFileType = ref('全部');
const selectedOwner = ref('全部');
const selectedTimeRange = ref('全部时间');
const kbSearchKeyword = ref('');

// 筛选选项（从后端动态获取）
const filterOptions = ref<Api.KnowledgeBase.KnowledgeBaseFilterOptions>({
  orgTags: [],
  creators: [],
  icons: [],
  publicOptions: [],
  timeRangeOptions: [],
  fileTypes: []
});

// 知识库列表 - 从后端独立的知识库API获取
const knowledgeBases = ref<Api.KnowledgeBase.KnowledgeBaseInfo[]>([]);
const activeKnowledgeBase = ref('');

// 右侧面板统计数据
const panelStats = ref({
  knowledgeBaseCount: 0,
  documentCount: 0,
  chunkCount: 0
});

function apiFn() {
  const params: Record<string, string | boolean> = {};
  if (selectedFileType.value && selectedFileType.value !== '全部') params.fileType = selectedFileType.value;
  if (selectedOwner.value && selectedOwner.value !== '全部') params.orgTag = selectedOwner.value;
  if (selectedTimeRange.value && selectedTimeRange.value !== '全部时间') params.timeRange = selectedTimeRange.value;
  return fakePaginationRequest<Api.KnowledgeBase.List>({ url: '/documents/uploads', params });
}

function renderIcon(fileName: string) {
  const ext = getFileExt(fileName);
  if (ext) {
    if (uploadAccept.split(',').includes(`.${ext}`)) return <SvgIcon localIcon={ext} class="mx-4 text-12" />;
    return <SvgIcon localIcon="dflt" class="mx-4 text-12" />;
  }
  return null;
}

// 处理文件预览
function handleFilePreview(fileName: string, fileMd5: string) {
  console.log('[知识库] 点击预览按钮:', {
    fileName,
    fileMd5,
    '完整信息': { fileName, fileMd5 }
  });

  previewFileName.value = fileName;
  previewFileMd5.value = fileMd5;
  previewVisible.value = true;
}

// 关闭文件预览
function closeFilePreview() {
  console.log('[知识库] 关闭文件预览');
  previewVisible.value = false;
  previewFileName.value = '';
  previewFileMd5.value = '';
}

const { columns, columnChecks, data, getData, loading } = useTable({
  apiFn,
  immediate: false,
  columns: () => [
    {
      key: 'fileName',
      title: '文件名',
      minWidth: 300,
      render: row => (
        <div class="flex items-center">
          {renderIcon(row.fileName)}
          <NEllipsis lineClamp={2} tooltip>
            <span
              class="cursor-pointer hover:text-primary transition-colors"
              onClick={() => handleFilePreview(row.fileName, row.fileMd5)}
            >
              {row.fileName}
            </span>
          </NEllipsis>
        </div>
      )
    },
    {
      key: 'fileMd5',
      title: 'MD5',
      width: 120,
      render: row => (
        <NEllipsis tooltip>
          <span
            class="cursor-pointer hover:text-primary transition-colors font-mono text-3"
            onClick={() => {
              navigator.clipboard.writeText(row.fileMd5);
              window.$message?.success('MD5已复制');
            }}
            title="点击复制MD5"
          >
            {row.fileMd5.substring(0, 8)}...
          </span>
        </NEllipsis>
      )
    },
    {
      key: 'totalSize',
      title: '文件大小',
      width: 100,
      render: row => fileSize(row.totalSize)
    },
    {
      key: 'status',
      title: '上传状态',
      width: 100,
      render: row => renderStatus(row.status, row.progress)
    },
    {
      key: 'orgTagName',
      title: '组织标签',
      width: 150,
      ellipsis: { tooltip: true, lineClamp: 2 }
    },
    {
      key: 'isPublic',
      title: '是否公开',
      width: 100,
      render: row => (row.public || row.isPublic ? <NTag type="success">公开</NTag> : <NTag type="warning">私有</NTag>)
    },
    {
      key: 'createdAt',
      title: '上传时间',
      width: 100,
      render: row => dayjs(row.createdAt).format('YYYY-MM-DD')
    },
    {
      key: 'operate',
      title: '操作',
      width: 180,
      render: row => (
        <div class="flex gap-4">
          {renderResumeUploadButton(row)}
          <NButton
            type="primary"
            ghost
            size="small"
            onClick={() => handleFilePreview(row.fileName, row.fileMd5)}
          >
            预览
          </NButton>
          <NPopconfirm onPositiveClick={() => handleDelete(row.fileMd5)}>
            {{
              default: () => '确认删除当前文件吗？',
              trigger: () => (
                <NButton type="error" ghost size="small">
                  删除
                </NButton>
              )
            }}
          </NPopconfirm>
        </div>
      )
    }
  ]
});

const store = useKnowledgeBaseStore();
const { tasks } = storeToRefs(store);
onMounted(async () => {
  await getList();
  await refreshKnowledgeBaseStats();
  await loadFilterOptions();
});

/** 加载筛选选项 */
async function loadFilterOptions() {
  try {
    const { error, data } = await fetchGetKnowledgeBaseFilterOptions();
    if (!error && data) {
      filterOptions.value = data;
    }
  } catch (e) {
    console.error('[知识库] 加载筛选选项失败:', e);
  }
}

/** 从后端独立的知识库API获取知识库列表和统计（带筛选参数） */
async function refreshKnowledgeBaseStats() {
  try {
    const params: Record<string, string> = {};
    if (kbSearchKeyword.value) params.keyword = kbSearchKeyword.value;
    
    const { error, data } = await fetchGetKnowledgeBases(params);
    if (!error && data) {
      knowledgeBases.value = data;

      if (knowledgeBases.value.length > 0 && !activeKnowledgeBase.value) {
        activeKnowledgeBase.value = knowledgeBases.value[0].kbId;
      }

      // 更新右侧面板统计
      panelStats.value = {
        knowledgeBaseCount: knowledgeBases.value.length,
        documentCount: knowledgeBases.value.reduce((sum, kb) => sum + kb.fileCount, 0),
        chunkCount: knowledgeBases.value.reduce((sum, kb) => sum + kb.chunkCount, 0)
      };
    }
  } catch (e) {
    console.error('[知识库] 刷新统计失败:', e);
  }
}

/** 刷新知识库统计信息（调用刷新接口） */
async function handleRefreshStats() {
  try {
    const { error, data } = await fetchRefreshKnowledgeBaseStats();
    if (!error && data) {
      knowledgeBases.value = data.knowledgeBases || [];
      if (knowledgeBases.value.length > 0 && !activeKnowledgeBase.value) {
        activeKnowledgeBase.value = knowledgeBases.value[0].kbId;
      }
      panelStats.value = {
        knowledgeBaseCount: data.knowledgeBaseCount || 0,
        documentCount: data.documentCount || 0,
        chunkCount: data.chunkCount || 0
      };
      window.$message?.success('统计信息已刷新');
    }
  } catch (e) {
    console.error('[知识库] 刷新统计失败:', e);
  }
}

/** 异步获取列表函数 */
async function getList() {
  console.log('[知识库] 开始获取文件列表');

  await getData();

  console.log('[知识库] 获取到原始数据，数量:', data.value.length);
  data.value.forEach((item, index) => {
    console.log(`[知识库] 原始数据[${index}]:`, {
      fileName: item.fileName,
      fileMd5: item.fileMd5,
      status: item.status
    });
  });

  if (data.value.length === 0) {
    tasks.value = [];
    return;
  }

  data.value.forEach((item, dataIndex) => {
    if (item.status === UploadStatus.Completed) {
      const index = tasks.value.findIndex(task => task.fileMd5 === item.fileMd5);
      if (index !== -1) {
        tasks.value[index].status = UploadStatus.Completed;
        console.log(`[知识库] 更新现有任务[${index}]:`, {
          fileName: item.fileName,
          fileMd5: item.fileMd5
        });
      } else {
        tasks.value.push(item);
        console.log(`[知识库] 添加新任务[${tasks.value.length - 1}]:`, {
          fileName: item.fileName,
          fileMd5: item.fileMd5
        });
      }
    } else if (!tasks.value.some(task => task.fileMd5 === item.fileMd5)) {
      item.status = UploadStatus.Break;
      tasks.value.push(item);
      console.log(`[知识库] 添加中断任务[${tasks.value.length - 1}]:`, {
        fileName: item.fileName,
        fileMd5: item.fileMd5
      });
    }
  });

  console.log('[知识库] 任务列表处理完成，总数:', tasks.value.length);
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
    await refreshKnowledgeBaseStats();
  }
}

// #region 文件上传
const uploadVisible = ref(false);
function handleUpload() {
  uploadVisible.value = true;
}
// #endregion

// #region 新建知识库
const createKbVisible = ref(false);
function handleCreateKb() {
  createKbVisible.value = true;
}
// #endregion

// #region 检索知识库
const searchVisible = ref(false);
function handleSearch() {
  searchVisible.value = true;
}
// #endregion

// 渲染上传状态
function renderStatus(status: UploadStatus, percentage: number) {
  if (status === UploadStatus.Completed) return <NTag type="success">已完成</NTag>;
  else if (status === UploadStatus.Break) return <NTag type="error">上传中断</NTag>;
  return <NProgress percentage={percentage} processing />;
}

// #region 文件续传
function renderResumeUploadButton(row: Api.KnowledgeBase.UploadTask) {
  if (row.status === UploadStatus.Break) {
    if (row.file)
      return (
        <NButton type="primary" size="small" ghost onClick={() => resumeUpload(row)}>
          续传
        </NButton>
      );
    return (
      <NUpload
        show-file-list={false}
        default-upload={false}
        accept={uploadAccept}
        onBeforeUpload={options => onBeforeUpload(options, row)}
        class="w-fit"
      >
        <NButton type="primary" size="small" ghost>
          续传
        </NButton>
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
  <div class="knowledge-base-page flex flex-col h-full bg-gray-50 dark:bg-gray-900">
    <!-- 页面标题和操作栏 -->
    <div class="flex-shrink-0 bg-white dark:bg-gray-800 border-b border-gray-200 dark:border-gray-700 px-6 py-3">
      <div class="flex items-center justify-between">
        <h1 class="text-base font-bold text-gray-900 dark:text-white">知识库管理</h1>
        <div class="flex items-center gap-3">
          <NButton type="primary" size="medium" @click="handleCreateKb">
            <template #icon>
              <icon-carbon:add class="text-base" />
            </template>
            新建知识库
          </NButton>
          <NButton type="primary" size="medium" ghost @click="handleUpload">
            <template #icon>
              <icon-carbon:cloud-upload class="text-base" />
            </template>
            上传文档
          </NButton>
          <NButton size="medium" tertiary @click="handleUpload">
            <template #icon>
              <icon-carbon:download class="text-base" />
            </template>
            批量导入
          </NButton>
        </div>
      </div>
    </div>

    <!-- 主体内容 -->
    <div class="flex-1 flex overflow-hidden">
      <!-- 左侧：知识库列表 -->
      <div class="w-340px border-r border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 flex flex-col">
        <!-- 知识库列表标题 -->
        <div class="flex-shrink-0 px-6 py-4 border-b border-gray-200 dark:border-gray-700">
          <div class="flex items-center justify-between mb-3">
            <h2 class="text-sm font-bold text-gray-900 dark:text-white">知识库列表</h2>
          </div>
          <!-- 搜索框 -->
          <NInput v-model:value="kbSearchKeyword" placeholder="搜索知识库" size="small" @input="refreshKnowledgeBaseStats">
            <template #prefix>
              <icon-carbon:search class="text-gray-400" />
            </template>
          </NInput>
        </div>

        <!-- 知识库卡片列表 -->
        <div class="flex-1 overflow-y-auto p-3 space-y-2">
          <div
            v-for="kb in knowledgeBases"
            :key="kb.kbId"
            :class="[
              'knowledge-base-card p-4 rounded-xl cursor-pointer transition-all',
              activeKnowledgeBase === kb.kbId
                ? 'bg-blue-50 dark:bg-blue-900/20 border-2 border-blue-500'
                : 'bg-gray-50 dark:bg-gray-700/50 border-2 border-transparent hover:border-gray-300 dark:hover:border-gray-600'
            ]"
            @click="activeKnowledgeBase = kb.kbId"
          >
            <div class="flex items-start gap-3 mb-3">
              <div :class="[
                'w-10 h-10 rounded-lg flex items-center justify-center flex-shrink-0',
                activeKnowledgeBase === kb.kbId ? 'bg-blue-100 dark:bg-blue-800' : 'bg-gray-100 dark:bg-gray-600'
              ]">
                <icon-carbon:enterprise v-if="kb.icon === 'enterprise'" :class="['text-lg', activeKnowledgeBase === kb.kbId ? 'text-blue-600' : 'text-gray-600']" />
                <icon-carbon:product v-else-if="kb.icon === 'product'" :class="['text-lg', activeKnowledgeBase === kb.kbId ? 'text-blue-600' : 'text-gray-600']" />
                <icon-carbon:code v-else-if="kb.icon === 'code'" :class="['text-lg', activeKnowledgeBase === kb.kbId ? 'text-blue-600' : 'text-gray-600']" />
                <icon-carbon:tool-kit v-else-if="kb.icon === 'tool-kit'" :class="['text-lg', activeKnowledgeBase === kb.kbId ? 'text-blue-600' : 'text-gray-600']" />
                <icon-carbon:chart-line v-else-if="kb.icon === 'chart-line'" :class="['text-lg', activeKnowledgeBase === kb.kbId ? 'text-blue-600' : 'text-gray-600']" />
                <icon-carbon:folder v-else-if="kb.icon === 'folder'" :class="['text-lg', activeKnowledgeBase === kb.kbId ? 'text-blue-600' : 'text-gray-600']" />
                <icon-carbon:catalog v-else-if="kb.icon === 'catalog'" :class="['text-lg', activeKnowledgeBase === kb.kbId ? 'text-blue-600' : 'text-gray-600']" />
                <icon-carbon:bookmark v-else-if="kb.icon === 'bookmark'" :class="['text-lg', activeKnowledgeBase === kb.kbId ? 'text-blue-600' : 'text-gray-600']" />
                <icon-carbon:data-base v-else :class="['text-lg', activeKnowledgeBase === kb.kbId ? 'text-blue-600' : 'text-gray-600']" />
              </div>
              <div class="flex-1 min-w-0">
                <h3 :class="[
                  'text-sm font-medium mb-1 truncate',
                  activeKnowledgeBase === kb.kbId ? 'text-blue-600 dark:text-blue-400' : 'text-gray-900 dark:text-white'
                ]">
                  <NTooltip>
                    <template #trigger>
                      <span>{{ kb.name }}</span>
                    </template>
                    {{ kb.name }}
                  </NTooltip>
                </h3>
                <div class="flex items-center gap-2 text-xs text-gray-500 dark:text-gray-400">
                  <span>{{ kb.fileCount }} 文档</span>
                  <span>·</span>
                  <span>{{ kb.chunkCount.toLocaleString() }} Chunk</span>
                </div>
              </div>
            </div>
            <div class="flex items-center justify-between">
              <NTag :type="kb.status === '正常' ? 'success' : 'warning'" size="small">{{ kb.status }}</NTag>
              <NTag v-if="kb.isPublic" type="info" size="small">公开</NTag>
              <NTag v-else type="warning" size="small">私有</NTag>
            </div>
          </div>
        </div>

        <!-- 底部统计 -->
        <div class="flex-shrink-0 px-6 py-4 border-t border-gray-200 dark:border-gray-700">
          <div class="text-xs text-gray-500 dark:text-gray-400 space-y-1">
            <div class="flex justify-between">
              <span>知识库数量</span>
              <span class="font-medium">{{ panelStats.knowledgeBaseCount }} 个</span>
            </div>
            <div class="flex justify-between">
              <span>文档总数</span>
              <span class="font-medium">{{ panelStats.documentCount }} 个</span>
            </div>
            <div class="flex justify-between">
              <span>Chunk 数量</span>
              <span class="font-medium">{{ panelStats.chunkCount.toLocaleString() }} 个</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 右侧：文件列表和筛选器 -->
      <div class="flex-1 flex flex-col bg-white dark:bg-gray-800">
        <!-- 筛选器 -->
        <div class="flex-shrink-0 px-6 py-4 border-b border-gray-200 dark:border-gray-700">
          <div class="flex items-center gap-4">
            <!-- 文件类型筛选 -->
            <div class="flex items-center gap-2">
              <span class="text-sm text-gray-600 dark:text-gray-400">文件类型</span>
              <NSelect
                v-model:value="selectedFileType"
                :options="[
                  { label: '全部类型', value: '全部' },
                  ...filterOptions.fileTypes.map(t => ({ label: t, value: t }))
                ]"
                size="small"
                class="w-140px"
                @update:value="getList"
              />
            </div>

            <!-- 所属者筛选 -->
            <div class="flex items-center gap-2">
              <span class="text-sm text-gray-600 dark:text-gray-400">所属者</span>
              <NSelect
                v-model:value="selectedOwner"
                :options="[
                  { label: '全部', value: '全部' },
                  ...filterOptions.orgTags.map(t => ({ label: t, value: t }))
                ]"
                size="small"
                class="w-140px"
                @update:value="getList"
              />
            </div>

            <!-- 更新时间筛选 -->
            <div class="flex items-center gap-2">
              <span class="text-sm text-gray-600 dark:text-gray-400">更新时间</span>
              <NSelect
                v-model:value="selectedTimeRange"
                :options="[
                  { label: '全部时间', value: '全部时间' },
                  { label: '近7天', value: '近7天' },
                  { label: '近30天', value: '近30天' },
                  { label: '近90天', value: '近90天' }
                ]"
                size="small"
                class="w-140px"
                @update:value="getList"
              />
            </div>

            <div class="flex-1"></div>

            <!-- 刷新按钮 -->
            <NButton circle size="small" tertiary @click="handleRefreshStats">
              <template #icon>
                <icon-carbon:renew class="text-lg" />
              </template>
            </NButton>
          </div>
        </div>

        <!-- 文件列表 -->
        <div class="flex-1 overflow-hidden">
          <NDataTable
            striped
            :columns="columns"
            :data="tasks"
            size="small"
            :flex-height="true"
            :scroll-x="962"
            :loading="loading"
            remote
            :row-key="row => row.id"
            :pagination="false"
            class="h-full"
          />
        </div>
      </div>

      <!-- 右侧面板：知识库概览 -->
      <div class="w-280px border-l border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 flex flex-col">
        <div class="p-4">
          <h2 class="text-base font-bold text-gray-900 dark:text-white mb-4">知识库概览</h2>

          <!-- 统计卡片 -->
          <div class="space-y-3 mb-4">
            <div class="stat-card bg-blue-50 dark:bg-blue-900/20 rounded-xl p-3">
              <div class="flex items-center gap-3 mb-2">
                <div class="w-8 h-8 rounded-lg bg-blue-100 dark:bg-blue-800 flex items-center justify-center">
                  <icon-carbon:data-base class="text-blue-600 text-base" />
                </div>
                <div class="flex-1">
                  <div class="text-11px text-gray-600 dark:text-gray-400">知识库数量</div>
                  <div class="text-xl font-bold text-blue-600">{{ panelStats.knowledgeBaseCount }}</div>
                </div>
              </div>
              <div class="flex items-center gap-1 text-xs text-gray-500">
                <span>独立管理单元</span>
              </div>
            </div>

            <div class="stat-card bg-green-50 dark:bg-green-900/20 rounded-xl p-3">
              <div class="flex items-center gap-3 mb-2">
                <div class="w-8 h-8 rounded-lg bg-green-100 dark:bg-green-800 flex items-center justify-center">
                  <icon-carbon:document class="text-green-600 text-base" />
                </div>
                <div class="flex-1">
                  <div class="text-11px text-gray-600 dark:text-gray-400">文档总数</div>
                  <div class="text-xl font-bold text-green-600">{{ panelStats.documentCount }}</div>
                </div>
              </div>
              <div class="flex items-center gap-1 text-xs text-gray-500">
                <span>当前可访问文档</span>
              </div>
            </div>

            <div class="stat-card bg-purple-50 dark:bg-purple-900/20 rounded-xl p-3">
              <div class="flex items-center gap-3 mb-2">
                <div class="w-8 h-8 rounded-lg bg-purple-100 dark:bg-purple-800 flex items-center justify-center">
                  <icon-carbon:chart-cluster-bar class="text-purple-600 text-base" />
                </div>
                <div class="flex-1">
                  <div class="text-11px text-gray-600 dark:text-gray-400">Chunk 数量</div>
                  <div class="text-xl font-bold text-purple-600">{{ panelStats.chunkCount.toLocaleString() }}</div>
                </div>
              </div>
              <div class="flex items-center gap-1 text-xs text-gray-500">
                <span>向量化检索分块</span>
              </div>
            </div>
          </div>

          <!-- 向量索引实时状态 -->
          <div class="mb-4">
            <h3 class="text-xs font-medium text-gray-700 dark:text-gray-300 mb-2">向量索引实时状态</h3>
            <div class="bg-gray-50 dark:bg-gray-700/50 rounded-lg p-3 space-y-2">
              <div class="flex items-center justify-between text-xs">
                <span class="text-gray-600 dark:text-gray-400">索引健康度</span>
                <div class="flex items-center gap-2">
                  <icon-carbon:checkmark-filled class="text-green-500" />
                  <span class="font-medium text-green-600">{{ tasks.length > 0 ? '正常' : '无数据' }}</span>
                </div>
              </div>
              <div class="flex items-center justify-between text-xs">
                <span class="text-gray-600 dark:text-gray-400">已索引文档</span>
                <span class="font-medium text-gray-900 dark:text-white">{{ panelStats.documentCount }} 篇</span>
              </div>
              <div class="flex items-center justify-between text-xs">
                <span class="text-gray-600 dark:text-gray-400">Chunk 数量</span>
                <span class="font-medium text-gray-900 dark:text-white">{{ panelStats.chunkCount.toLocaleString() }} 个</span>
              </div>
            </div>
          </div>

          <!-- 最近入库任务 -->
          <div>
            <div class="flex items-center justify-between mb-2">
              <h3 class="text-xs font-medium text-gray-700 dark:text-gray-300">最近上传文件</h3>
              <NButton text size="tiny" type="primary" @click="handleRefreshStats">
                <template #icon>
                  <icon-carbon:renew class="text-xs" />
                </template>
              </NButton>
            </div>
            <div class="space-y-3" v-if="tasks.length > 0">
              <div
                v-for="task in tasks.slice(0, 3)"
                :key="task.fileMd5"
                class="bg-gray-50 dark:bg-gray-700/50 rounded-lg p-3"
              >
                <div class="flex items-start gap-2 mb-2">
                  <icon-carbon:document 
                    :class="[
                      'text-sm flex-shrink-0 mt-0.5',
                      task.status === UploadStatus.Completed ? 'text-green-500' : 'text-blue-500'
                    ]" 
                  />
                  <div class="flex-1 min-w-0">
                    <div class="text-xs font-medium text-gray-900 dark:text-white truncate mb-1">
                      {{ task.fileName }}
                    </div>
                    <div :class="[
                      'text-xs',
                      task.status === UploadStatus.Completed ? 'text-green-600' : 'text-blue-600'
                    ]">
                      {{ task.status === UploadStatus.Completed ? '已完成' : task.status === UploadStatus.Break ? '上传中断' : '处理中' }}
                    </div>
                  </div>
                </div>
                <NProgress
                  :percentage="task.status === UploadStatus.Completed ? 100 : (task.progress || 0)"
                  :show-indicator="false"
                  :color="task.status === UploadStatus.Completed ? '#10b981' : '#3b82f6'"
                />
              </div>
            </div>
            <div v-else class="text-center py-6 text-gray-400">
              <icon-carbon:document-blank class="text-3xl mb-2 mx-auto" />
              <p class="text-xs">暂无上传文件</p>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 对话框 -->
    <CreateKbDialog v-model:visible="createKbVisible" @submitted="refreshKnowledgeBaseStats" />
    <UploadDialog v-model:visible="uploadVisible" />
    <SearchDialog v-model:visible="searchVisible" />
    
    <!-- 文件预览弹窗 -->
    <NModal v-model:show="previewVisible" preset="card" title="文件预览" style="width: 80%; max-width: 1000px;">
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
.knowledge-base-page {
  .knowledge-base-card {
    transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
    
    &:hover {
      transform: translateY(-2px);
      box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
    }
    
    &:active {
      transform: translateY(0);
    }
  }

  .stat-card {
    transition: all 0.3s ease;
    
    &:hover {
      transform: translateY(-2px);
      box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
    }
  }
}

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

:deep(.dark) {
  ::-webkit-scrollbar-thumb {
    background: #4b5563;

    &:hover {
      background: #6b7280;
    }
  }
}

:deep() {
  .n-progress-icon.n-progress-icon--as-text {
    white-space: nowrap;
  }
}
</style>
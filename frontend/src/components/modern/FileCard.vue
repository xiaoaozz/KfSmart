<script setup lang="ts">
import { computed } from 'vue';

interface Props {
  /** 文件名 */
  fileName: string;
  /** 文件大小 */
  fileSize?: string;
  /** 文件类型 */
  fileType?: string;
  /** 上传进度 (0-100) */
  progress?: number;
  /** 状态 */
  status?: 'pending' | 'uploading' | 'success' | 'error';
  /** 上传时间 */
  uploadTime?: string;
  /** 是否可预览 */
  previewable?: boolean;
  /** 是否可删除 */
  deletable?: boolean;
  /** 文件MD5 */
  fileMd5?: string;
}

const props = withDefaults(defineProps<Props>(), {
  progress: 0,
  status: 'success',
  previewable: true,
  deletable: true
});

const emit = defineEmits<{
  preview: [];
  delete: [];
  download: [];
}>();

const fileIcon = computed(() => {
  const type = props.fileType?.toLowerCase() || getFileTypeFromName(props.fileName);
  
  const iconMap: Record<string, string> = {
    pdf: 'i-vscode-icons:file-type-pdf2',
    doc: 'i-vscode-icons:file-type-word',
    docx: 'i-vscode-icons:file-type-word',
    xls: 'i-vscode-icons:file-type-excel',
    xlsx: 'i-vscode-icons:file-type-excel',
    ppt: 'i-vscode-icons:file-type-powerpoint',
    pptx: 'i-vscode-icons:file-type-powerpoint',
    txt: 'i-vscode-icons:file-type-text',
    md: 'i-vscode-icons:file-type-markdown',
    zip: 'i-vscode-icons:file-type-zip',
    rar: 'i-vscode-icons:file-type-zip',
    jpg: 'i-vscode-icons:file-type-image',
    jpeg: 'i-vscode-icons:file-type-image',
    png: 'i-vscode-icons:file-type-image',
    gif: 'i-vscode-icons:file-type-image',
    svg: 'i-vscode-icons:file-type-svg',
    mp4: 'i-vscode-icons:file-type-video',
    mp3: 'i-vscode-icons:file-type-audio'
  };
  
  return iconMap[type] || 'i-carbon:document';
});

const statusColor = computed(() => {
  const colors = {
    pending: 'text-gray-400',
    uploading: 'text-blue-500',
    success: 'text-green-500',
    error: 'text-red-500'
  };
  
  return colors[props.status];
});

const statusText = computed(() => {
  const texts = {
    pending: '等待上传',
    uploading: '上传中...',
    success: '上传成功',
    error: '上传失败'
  };
  
  return texts[props.status];
});

function getFileTypeFromName(fileName: string): string {
  const ext = fileName.split('.').pop();
  return ext?.toLowerCase() || '';
}
</script>

<template>
  <div
    class="file-card group rd-lg p-4 bg-white dark:bg-dark-container shadow-md hover:shadow-lg transition-all hover:-translate-y-1 border border-gray-100 dark:border-gray-700"
  >
    <!-- 文件图标和基本信息 -->
    <div class="flex items-start gap-3">
      <!-- 文件图标 -->
      <div
        class="flex-shrink-0 w-12 h-12 rd-lg flex-cc text-3xl transition-transform group-hover:scale-110"
        :class="fileIcon"
      />
      
      <!-- 文件信息 -->
      <div class="flex-1 min-w-0">
        <h4 class="text-sm font-500 text-gray-800 dark:text-gray-100 truncate mb-1" :title="fileName">
          {{ fileName }}
        </h4>
        
        <div class="flex items-center gap-2 text-xs text-gray-500 dark:text-gray-400">
          <span v-if="fileSize">{{ fileSize }}</span>
          <span v-if="fileSize && uploadTime" class="text-gray-300 dark:text-gray-600">•</span>
          <span v-if="uploadTime">{{ uploadTime }}</span>
        </div>

        <!-- 状态标签 -->
        <div class="mt-2 flex items-center gap-2">
          <span class="text-xs font-500" :class="statusColor">
            {{ statusText }}
          </span>
          
          <!-- 上传进度 -->
          <span v-if="status === 'uploading'" class="text-xs text-gray-500">
            {{ progress }}%
          </span>
        </div>
      </div>

      <!-- 操作按钮(悬浮显示) -->
      <div class="flex-shrink-0 flex gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
        <button
          v-if="previewable && status === 'success'"
          class="w-8 h-8 rd-md flex-cc text-gray-600 dark:text-gray-300 hover:bg-primary-50 dark:hover:bg-primary-900/20 hover:text-primary-500 transition-colors"
          title="预览"
          @click="emit('preview')"
        >
          <div class="i-carbon:view text-lg" />
        </button>
        
        <button
          v-if="status === 'success'"
          class="w-8 h-8 rd-md flex-cc text-gray-600 dark:text-gray-300 hover:bg-blue-50 dark:hover:bg-blue-900/20 hover:text-blue-500 transition-colors"
          title="下载"
          @click="emit('download')"
        >
          <div class="i-carbon:download text-lg" />
        </button>
        
        <button
          v-if="deletable"
          class="w-8 h-8 rd-md flex-cc text-gray-600 dark:text-gray-300 hover:bg-red-50 dark:hover:bg-red-900/20 hover:text-red-500 transition-colors"
          title="删除"
          @click="emit('delete')"
        >
          <div class="i-carbon:trash-can text-lg" />
        </button>
      </div>
    </div>

    <!-- 进度条 -->
    <div v-if="status === 'uploading'" class="mt-3">
      <div class="h-1.5 bg-gray-200 dark:bg-gray-700 rd-full overflow-hidden">
        <div
          class="h-full bg-gradient-to-r from-primary-500 to-primary-600 transition-all duration-300"
          :style="{ width: `${progress}%` }"
        />
      </div>
    </div>

    <!-- 错误信息 -->
    <div v-if="status === 'error'" class="mt-2 text-xs text-red-500 flex items-center gap-1">
      <div class="i-carbon:warning" />
      <span>上传失败,请重试</span>
    </div>

    <!-- MD5信息(可选) -->
    <div v-if="fileMd5 && status === 'success'" class="mt-2 pt-2 border-t border-gray-100 dark:border-gray-700">
      <div class="text-xs text-gray-500 dark:text-gray-400 font-mono flex items-center gap-2">
        <span>MD5:</span>
        <span class="flex-1 truncate">{{ fileMd5.substring(0, 16) }}...</span>
        <button
          class="hover:text-primary-500 transition-colors"
          title="复制MD5"
          @click="navigator.clipboard.writeText(fileMd5)"
        >
          <div class="i-carbon:copy" />
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.file-card {
  position: relative;
}
</style>

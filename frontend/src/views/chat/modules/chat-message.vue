<script setup lang="ts">
import { nextTick, ref, computed } from 'vue';
import { VueMarkdownIt } from 'vue-markdown-shiki';
import { formatDate } from '@/utils/common';
import MessageBubble from '@/components/modern/MessageBubble.vue';

defineOptions({ name: 'ChatMessage' });

const props = defineProps<{
  msg: Api.Chat.Message;
  sessionId?: string;
}>();

const authStore = useAuthStore();

// 存储文件名和对应的事件处理
const sourceFiles = ref<Array<{ fileName: string; id: string; referenceNumber: number; fileMd5?: string }>>([]);

// 处理来源文件链接的函数
function processSourceLinks(text: string): string {
  sourceFiles.value = [];

  // 新格式：匹配 (来源#数字: 文件名 | MD5:xxx)
  const newSourcePattern = /([\(（])来源#(\d+):\s*([^|\n\r（）]+?)\s*\|\s*MD5:\s*([a-fA-F0-9]+)([\)）])/g;

  let processedText = text.replace(
    newSourcePattern,
    (_match, leftParen, sourceNum, fileName, fileMd5, rightParen) => {
      const linkClass = 'source-file-link';
      const trimmedFileName = fileName.trim();
      const trimmedMd5 = fileMd5.trim();
      const fileId = `source-file-${sourceFiles.value.length}`;
      const referenceNumber = parseInt(sourceNum, 10);

      sourceFiles.value.push({
        fileName: trimmedFileName,
        id: fileId,
        referenceNumber,
        fileMd5: trimmedMd5
      });

      const lp = leftParen === '(' ? '(' : '（';
      const rp = rightParen === ')' ? ')' : '）';

      return `${lp}来源#${sourceNum}: <span class="${linkClass}" data-file-id="${fileId}">${trimmedFileName} | MD5:${trimmedMd5.substring(0, 8)}...</span>${rp}`;
    }
  );

  // 旧格式：匹配 (来源#数字: 文件名)
  const oldSourcePattern = /([\(（])来源#(\d+):\s*([^\n\r（）]+?)([\)）])/g;

  processedText = processedText.replace(oldSourcePattern, (_match, leftParen, sourceNum, fileName, rightParen) => {
    const linkClass = 'source-file-link';
    const trimmedFileName = fileName.trim();
    const fileId = `source-file-${sourceFiles.value.length}`;
    const referenceNumber = parseInt(sourceNum, 10);

    sourceFiles.value.push({
      fileName: trimmedFileName,
      id: fileId,
      referenceNumber
    });

    const lp = leftParen || '';
    const rp = rightParen || '';

    return `${lp}来源#${sourceNum}: <span class="${linkClass}" data-file-id="${fileId}">${trimmedFileName}</span>${rp}`;
  });

  return processedText;
}

const content = computed(() => {
  const rawContent = props.msg.content ?? '';

  if (props.msg.role === 'assistant') {
    return processSourceLinks(rawContent);
  }

  return rawContent;
});

// 处理内容点击事件（事件委托）
function handleContentClick(event: MouseEvent) {
  const target = event.target as HTMLElement;

  if (target.classList.contains('source-file-link')) {
    const fileId = target.getAttribute('data-file-id');
    if (fileId) {
      const file = sourceFiles.value.find(f => f.id === fileId);
      if (file) {
        handleSourceFileClick({
          fileName: file.fileName,
          referenceNumber: file.referenceNumber,
          fileMd5: file.fileMd5
        });
      }
    }
  }
}

// 处理来源文件点击事件
async function handleSourceFileClick(fileInfo: { fileName: string; referenceNumber: number; fileMd5?: string }) {
  const { fileName, referenceNumber, fileMd5: extractedMd5 } = fileInfo;

  try {
    window.$message?.loading(`正在获取文件下载链接: ${fileName}`, {
      duration: 0,
      closable: false
    });

    let targetMd5 = null;

    if (extractedMd5) {
      targetMd5 = extractedMd5;
    } else if (props.sessionId) {
      try {
        const { error: md5Error, data: md5Data } = await request<Api.Document.ReferenceMd5Response>({
          url: 'documents/reference-md5',
          params: {
            sessionId: props.sessionId,
            referenceNumber: referenceNumber.toString()
          },
          baseURL: '/proxy-api'
        });

        if (!md5Error && md5Data?.fileMd5) {
          targetMd5 = md5Data.fileMd5;
        }
      } catch (md5Err) {
        console.warn('通过API查询MD5失败:', md5Err);
      }
    }

    if (targetMd5) {
      const { error: downloadError, data: downloadData } = await request<Api.Document.DownloadResponse>({
        url: 'documents/download-by-md5',
        params: {
          fileMd5: targetMd5,
          token: authStore.token
        },
        baseURL: '/proxy-api'
      });

      window.$message?.destroyAll();

      if (!downloadError && downloadData?.downloadUrl) {
        window.open(downloadData.downloadUrl, '_blank');
        window.$message?.success(`文件下载链接已打开: ${downloadData.fileName || fileName}`);
        return;
      }
    }

    const { error, data } = await request<Api.Document.DownloadResponse>({
      url: 'documents/download',
      params: {
        fileName,
        token: authStore.token
      },
      baseURL: '/proxy-api'
    });

    window.$message?.destroyAll();

    if (error) {
      window.$message?.error(`文件下载失败: ${error.response?.data?.message || '未知错误'}`);
      return;
    }

    if (data?.downloadUrl) {
      window.open(data.downloadUrl, '_blank');
      window.$message?.success(`文件下载链接已打开: ${data.fileName || fileName}`);
    } else {
      window.$message?.error('未能获取到下载链接');
    }
  } catch (err) {
    window.$message?.destroyAll();
    console.error('文件下载失败:', err);
    window.$message?.error(`文件下载失败: ${fileName}`);
  }
}

function handleCopy(text: string) {
  navigator.clipboard.writeText(text);
  window.$message?.success('已复制到剪贴板');
}

function handleRetry() {
  window.$message?.info('重试功能开发中...');
}

// 格式化时间戳
const timestamp = computed(() => {
  return props.msg.timestamp ? formatDate(props.msg.timestamp) : '';
});

// 用户头像URL
const userAvatarUrl = computed(() => {
  return authStore.userInfo.avatar || '';
});
</script>

<template>
  <div class="chat-message-wrapper group">
    <MessageBubble
      :role="msg.role"
      :content="content"
      :status="msg.status"
      :timestamp="timestamp"
      :show-avatar="true"
      :avatar-url="userAvatarUrl"
      :copyable="true"
      @copy="handleCopy"
      @retry="handleRetry"
    >
      <!-- 自定义内容渲染 (Markdown) -->
      <div
        v-if="msg.role === 'assistant' && msg.status !== 'loading' && msg.status !== 'error'"
        class="message-content"
        @click="handleContentClick"
      >
        <VueMarkdownIt :content="content" />
      </div>
      
      <!-- 用户消息直接显示文本 -->
      <div v-else-if="msg.role === 'user'" class="message-content whitespace-pre-wrap">
        {{ content }}
      </div>
    </MessageBubble>
  </div>
</template>

<style scoped lang="scss">
.chat-message-wrapper {
  position: relative;
  
  /* 文件链接样式 */
  :deep(.source-file-link) {
    color: #667eea;
    text-decoration: underline;
    cursor: pointer;
    transition: all 0.2s ease;
    font-weight: 500;
    padding: 2px 4px;
    border-radius: 4px;
    background-color: rgba(102, 126, 234, 0.1);

    &:hover {
      color: #5568d3;
      background-color: rgba(102, 126, 234, 0.2);
      text-decoration: none;
    }
  }

  /* Markdown 内容样式 */
  :deep(.message-content) {
    /* 代码块样式 */
    pre {
      background-color: rgba(0, 0, 0, 0.05);
      border-radius: 8px;
      padding: 12px;
      margin: 8px 0;
      overflow-x: auto;

      code {
        background-color: transparent;
        padding: 0;
        border-radius: 0;
      }
    }

    /* 行内代码样式 */
    code {
      background-color: rgba(0, 0, 0, 0.08);
      padding: 2px 6px;
      border-radius: 4px;
      font-family: 'Monaco', 'Menlo', 'Courier New', monospace;
      font-size: 0.9em;
    }

    /* 链接样式 */
    a {
      color: #667eea;
      text-decoration: none;
      transition: color 0.2s;

      &:hover {
        color: #5568d3;
        text-decoration: underline;
      }
    }

    /* 引用块样式 */
    blockquote {
      border-left: 4px solid #667eea;
      padding-left: 16px;
      margin: 12px 0;
      color: #666;
      font-style: italic;
    }

    /* 表格样式 */
    table {
      width: 100%;
      border-collapse: collapse;
      margin: 12px 0;

      th,
      td {
        border: 1px solid rgba(0, 0, 0, 0.1);
        padding: 8px 12px;
        text-align: left;
      }

      th {
        background-color: rgba(102, 126, 234, 0.1);
        font-weight: 600;
      }

      tr:hover {
        background-color: rgba(0, 0, 0, 0.02);
      }
    }

    /* 列表样式 */
    ul,
    ol {
      padding-left: 24px;
      margin: 8px 0;
    }

    li {
      margin: 4px 0;
    }

    /* 标题样式 */
    h1,
    h2,
    h3,
    h4,
    h5,
    h6 {
      margin: 16px 0 8px;
      font-weight: 600;
    }

    /* 段落间距 */
    p {
      margin: 8px 0;
      line-height: 1.6;
    }
  }

  /* 暗色模式适配 */
  .dark & {
    :deep(.source-file-link) {
      color: #8b9eff;
      background-color: rgba(102, 126, 234, 0.15);

      &:hover {
        color: #a8b5ff;
        background-color: rgba(102, 126, 234, 0.25);
      }
    }

    :deep(.message-content) {
      pre {
        background-color: rgba(255, 255, 255, 0.05);
      }

      code {
        background-color: rgba(255, 255, 255, 0.08);
      }

      blockquote {
        border-left-color: #8b9eff;
        color: #aaa;
      }

      table {
        th,
        td {
          border-color: rgba(255, 255, 255, 0.1);
        }

        th {
          background-color: rgba(102, 126, 234, 0.15);
        }

        tr:hover {
          background-color: rgba(255, 255, 255, 0.02);
        }
      }
    }
  }
}
</style>

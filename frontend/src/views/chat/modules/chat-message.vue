<script setup lang="ts">
// eslint-disable-next-line @typescript-eslint/no-unused-vars
import { nextTick } from 'vue';
import { VueMarkdownIt } from 'vue-markdown-shiki';
import { formatDate } from '@/utils/common';
defineOptions({ name: 'ChatMessage' });

const props = defineProps<{
  msg: Api.Chat.Message,
  sessionId?: string
}>();

const authStore = useAuthStore();

function handleCopy(content: string) {
  navigator.clipboard.writeText(content);
  window.$message?.success('已复制');
}

/**
 * 将原始错误信息转换为友好的提示文案
 */
function formatErrorMessage(raw?: string): string {
  if (!raw) return '服务器繁忙，请稍后再试';
  const msg = raw.toUpperCase();
  // 认证 / API Key 相关
  if (msg.includes('401') || msg.includes('UNAUTHORIZED') || msg.includes('API_KEY') || msg.includes('APIKEY') || msg.includes('INVALID_KEY')) {
    return '模型暂不可用，请检查 API Key 是否正确或已过期';
  }
  // 资源不存在 / 模型不存在
  if (msg.includes('404') || msg.includes('NOT_FOUND') || msg.includes('MODEL_NOT_FOUND')) {
    return '模型暂不可用，请确认模型名称是否正确或 API 地址是否有效';
  }
  // 限流
  if (msg.includes('429') || msg.includes('RATE_LIMIT') || msg.includes('TOO_MANY')) {
    return '请求过于频繁，请稍后再试';
  }
  // 服务端错误
  if (msg.includes('500') || msg.includes('502') || msg.includes('503') || msg.includes('INTERNAL_SERVER') || msg.includes('SERVICE_UNAVAILABLE')) {
    return '模型服务暂时不可用，请稍后重试';
  }
  // 超时
  if (msg.includes('TIMEOUT') || msg.includes('TIMED_OUT')) {
    return '请求超时，请检查网络连接后重试';
  }
  // 余额不足
  if (msg.includes('INSUFFICIENT') || msg.includes('QUOTA') || msg.includes('BALANCE')) {
    return '账户余额不足或配额已用尽，请检查 API Key 对应账户';
  }
  // 默认降级提示
  return '模型请求失败，请检查 API Key 配置或稍后重试';
}

const chatStore = useChatStore();

// 存储文件名和对应的事件处理
const sourceFiles = ref<Array<{fileName: string, id: string, referenceNumber: number, fileMd5?: string}>>([]);

// 处理来源文件链接的函数
function processSourceLinks(text: string): string {
  // 重置来源文件列表，避免重复
  sourceFiles.value = [];

  // 新格式：匹配 (来源#数字: 文件名 | MD5:xxx) 的正则表达式，兼容全角括号
  // 格式示例：(来源#1: test.txt | MD5:abc123) 或 (来源#1: test.txt|MD5:abc123)
  const newSourcePattern = /([\(（])来源#(\d+):\s*([^|\n\r（）]+?)\s*\|\s*MD5:\s*([a-fA-F0-9]+)([\)）])/g;

  // 先处理新格式（包含MD5）
  let processedText = text.replace(newSourcePattern, (_match, leftParen, sourceNum, fileName, fileMd5, rightParen) => {
    const linkClass = 'source-file-link';
    const trimmedFileName = fileName.trim();
    const trimmedMd5 = fileMd5.trim();
    const fileId = `source-file-${sourceFiles.value.length}`;
    const referenceNumber = parseInt(sourceNum, 10);

    // 存储文件信息（包含文件名和MD5）
    sourceFiles.value.push({
      fileName: trimmedFileName,
      id: fileId,
      referenceNumber,
      fileMd5: trimmedMd5
    });

    const lp = leftParen === '(' ? '(' : '（';
    const rp = rightParen === ')' ? ')' : '）';

    // 显示格式：来源#1: test.txt | MD5:abc...
    return `${lp}来源#${sourceNum}: <span class="${linkClass}" data-file-id="${fileId}">${trimmedFileName} | MD5:${trimmedMd5.substring(0, 8)}...</span>${rp}`;
  });

  // 旧格式：匹配 (来源#数字: 文件名) 的正则表达式，兼容全角括号和无括号格式
  // 用于向后兼容旧的引用格式
  const oldSourcePattern = /([\(（])来源#(\d+):\s*([^\n\r（）]+?)([\)）])/g;

  processedText = processedText.replace(oldSourcePattern, (_match, leftParen, sourceNum, fileName, rightParen) => {
    const linkClass = 'source-file-link';
    const trimmedFileName = fileName.trim();
    const fileId = `source-file-${sourceFiles.value.length}`;
    const referenceNumber = parseInt(sourceNum, 10);

    // 存储文件信息（旧格式，没有MD5）
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
  chatStore.scrollToBottom?.();
  const rawContent = props.msg.content ?? '';

  // 只对助手消息处理来源链接
  if (props.msg.role === 'assistant') {
    return processSourceLinks(rawContent);
  }

  return rawContent;
});

// 处理内容点击事件（事件委托）
function handleContentClick(event: MouseEvent) {
  const target = event.target as HTMLElement;

  // 检查点击的是否是文件链接
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
async function handleSourceFileClick(fileInfo: { fileName: string, referenceNumber: number, fileMd5?: string }) {
  const { fileName, referenceNumber, fileMd5: extractedMd5 } = fileInfo;
  console.log('点击了来源文件:', fileName, '引用编号:', referenceNumber, '提取的MD5:', extractedMd5, '会话ID:', props.sessionId);

  try {
    window.$message?.loading(`正在获取文件下载链接: ${fileName}`, {
      duration: 0,
      closable: false
    });

    let targetMd5 = null;

    // 方案1：优先使用从引用中直接提取的MD5
    if (extractedMd5) {
      console.log('使用从引用中提取的MD5:', extractedMd5);
      targetMd5 = extractedMd5;
    }
    // 方案2：如果没有提取到MD5，则通过后端API查询
    else if (props.sessionId) {
      try {
        console.log('步骤1: 通过API查询引用MD5', { sessionId: props.sessionId, referenceNumber });
        const { error: md5Error, data: md5Data } = await request<Api.Document.ReferenceMd5Response>({
          url: 'documents/reference-md5',
          params: {
            sessionId: props.sessionId,
            referenceNumber: referenceNumber.toString()
          },
          baseURL: '/proxy-api'
        });

        console.log('引用MD5查询结果:', { error: md5Error, data: md5Data });

        if (!md5Error && md5Data?.fileMd5) {
          targetMd5 = md5Data.fileMd5;
        }
      } catch (md5Err) {
        console.warn('通过API查询MD5失败:', md5Err);
      }
    }

    // 如果获取到了MD5，使用MD5精确下载
    if (targetMd5) {
      console.log('步骤2: 使用MD5下载文件', targetMd5);
      const { error: downloadError, data: downloadData } = await request<Api.Document.DownloadResponse>({
        url: 'documents/download-by-md5',
        params: {
          fileMd5: targetMd5,
          token: authStore.token
        },
        baseURL: '/proxy-api'
      });

      console.log('文件下载结果:', { error: downloadError, data: downloadData });

      window.$message?.destroyAll();

      if (!downloadError && downloadData?.downloadUrl) {
        window.open(downloadData.downloadUrl, '_blank');
        window.$message?.success(`文件下载链接已打开: ${downloadData.fileName || fileName}`);
        return;
      }
    }

    // 降级方案：使用文件名下载（向后兼容）
    console.log('降级方案: 使用文件名下载', fileName);
    const { error, data } = await request<Api.Document.DownloadResponse>({
      url: 'documents/download',
      params: {
        fileName: fileName,
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
</script>

<template>
  <div class="chat-message mb-4">
    <!-- 用户消息（右侧） -->
    <div v-if="msg.role === 'user'" class="flex justify-end">
      <div class="flex items-start gap-3 max-w-[70%]">
        <div class="flex-1 min-w-0 flex flex-col items-end">
          <div class="flex items-center gap-2 mb-1.5 px-1">
            <span class="text-xs text-gray-500 dark:text-gray-400">{{ formatDate(msg.timestamp) }}</span>
            <span class="text-sm font-medium text-gray-900 dark:text-white">{{ authStore.userInfo.username }}</span>
          </div>
          <div class="user-content bg-gradient-to-br from-blue-500 to-blue-600 text-white rounded-2xl rounded-tr-sm px-4 py-2.5 shadow-sm">
            <div class="text-sm leading-relaxed whitespace-pre-wrap break-words">{{ content }}</div>
          </div>
        </div>
        <NAvatar size="medium" round class="flex-shrink-0 bg-gradient-to-br from-green-400 to-green-600 mt-6">
          <icon-ph:user-circle class="text-lg text-white" />
        </NAvatar>
      </div>
    </div>

    <!-- 助手消息（左侧） -->
    <div v-else class="flex justify-start">
      <div class="flex items-start gap-3 max-w-[80%]">
        <NAvatar size="medium" round class="flex-shrink-0 bg-gradient-to-br from-blue-500 to-purple-600 mt-6">
          <SystemLogo class="text-base text-white" />
        </NAvatar>
        <div class="flex-1 min-w-0">
          <div class="flex items-center gap-2 mb-1.5 px-1">
            <span class="text-sm font-medium text-gray-900 dark:text-white">KnowFlow</span>
            <span class="text-xs text-gray-500 dark:text-gray-400">{{ formatDate(msg.timestamp) }}</span>
          </div>
          
          <!-- 加载状态 -->
          <div v-if="msg.status === 'pending'" class="assistant-content bg-gray-50 dark:bg-gray-800 rounded-2xl rounded-tl-sm px-4 py-3 shadow-sm border border-gray-100 dark:border-gray-700">
            <icon-eos-icons:three-dots-loading class="text-xl text-blue-500" />
          </div>
          
          <!-- 错误状态 -->
          <div v-else-if="msg.status === 'error'" class="assistant-content bg-red-50 dark:bg-red-900/20 rounded-2xl rounded-tl-sm px-4 py-3 shadow-sm border border-red-200 dark:border-red-800">
            <div class="text-sm text-red-600 dark:text-red-400 flex items-start gap-1.5">
              <icon-carbon:warning class="text-base flex-shrink-0 mt-0.5" />
              <div class="flex-1 min-w-0">
                <div class="font-medium mb-0.5">请求失败</div>
                <div class="text-red-500/80 break-words">{{ formatErrorMessage(msg.errorMessage) }}</div>
              </div>
            </div>
          </div>

          <!-- 正常内容 -->
          <div v-else class="assistant-content bg-gray-50 dark:bg-gray-800 rounded-2xl rounded-tl-sm px-4 py-3 shadow-sm border border-gray-100 dark:border-gray-700" @click="handleContentClick">
            <div class="prose prose-sm dark:prose-invert max-w-none">
              <VueMarkdownIt :content="content" />
            </div>
          </div>

          <!-- 操作按钮 -->
          <div class="flex items-center gap-1 mt-1.5 ml-1">
            <NButton text size="tiny" class="opacity-60 hover:opacity-100" @click="handleCopy(msg.content)">
              <template #icon>
                <icon-mynaui:copy class="text-gray-500 dark:text-gray-400" />
              </template>
              <span class="text-xs text-gray-500 dark:text-gray-400">复制</span>
            </NButton>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.chat-message {
  animation: slideIn 0.2s ease-out;

  .user-content {
    word-break: break-word;
    box-shadow: 0 2px 4px rgba(59, 130, 246, 0.12);
  }

  .assistant-content {
    word-break: break-word;

    :deep(.source-file-link) {
      color: #3b82f6;
      cursor: pointer;
      text-decoration: underline;
      text-decoration-thickness: 1px;
      text-underline-offset: 2px;
      transition: all 0.2s;
      font-weight: 500;

      &:hover {
        color: #2563eb;
        text-decoration-color: #2563eb;
      }

      &:active {
        color: #1d4ed8;
      }
    }

    // Markdown 样式优化
    :deep(.prose) {
      color: inherit;
      font-size: 0.875rem;
      
      p {
        margin: 0.75em 0;
        line-height: 1.7;

        &:first-child {
          margin-top: 0;
        }

        &:last-child {
          margin-bottom: 0;
        }
      }

      code {
        background: rgba(0, 0, 0, 0.06);
        padding: 0.15em 0.4em;
        border-radius: 4px;
        font-size: 0.85em;
        font-family: 'SF Mono', Monaco, 'Cascadia Code', monospace;
      }

      pre {
        background: rgba(0, 0, 0, 0.04);
        padding: 0.875em;
        border-radius: 8px;
        overflow-x: auto;
        margin: 0.75em 0;
        border: 1px solid rgba(0, 0, 0, 0.06);

        code {
          background: none;
          padding: 0;
          border-radius: 0;
          font-size: 0.8125em;
        }
      }

      ul, ol {
        margin: 0.75em 0;
        padding-left: 1.5em;

        li {
          margin: 0.375em 0;
          line-height: 1.6;
        }
      }

      blockquote {
        border-left: 3px solid #3b82f6;
        padding-left: 1em;
        margin: 0.75em 0;
        color: #6b7280;
        font-style: italic;
      }

      h1, h2, h3, h4, h5, h6 {
        margin: 1em 0 0.5em;
        font-weight: 600;
        line-height: 1.3;

        &:first-child {
          margin-top: 0;
        }
      }

      h1 { font-size: 1.5em; }
      h2 { font-size: 1.3em; }
      h3 { font-size: 1.15em; }
      h4 { font-size: 1em; }

      a {
        color: #3b82f6;
        text-decoration: underline;
        text-decoration-thickness: 1px;
        text-underline-offset: 2px;
        
        &:hover {
          color: #2563eb;
        }
      }

      table {
        width: 100%;
        border-collapse: collapse;
        margin: 1em 0;
        font-size: 0.875em;

        th, td {
          border: 1px solid #e5e7eb;
          padding: 0.5em 0.75em;
          text-align: left;
        }

        th {
          background: rgba(0, 0, 0, 0.02);
          font-weight: 600;
        }

        tr:nth-child(even) {
          background: rgba(0, 0, 0, 0.01);
        }
      }

      hr {
        border: none;
        border-top: 1px solid #e5e7eb;
        margin: 1.5em 0;
      }
    }
  }
}

@keyframes slideIn {
  from {
    opacity: 0;
    transform: translateY(8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

:deep(.dark) {
  .assistant-content {
    .prose {
      code {
        background: rgba(255, 255, 255, 0.08);
      }

      pre {
        background: rgba(255, 255, 255, 0.04);
        border-color: rgba(255, 255, 255, 0.08);
      }

      table {
        th, td {
          border-color: #374151;
        }

        th {
          background: rgba(255, 255, 255, 0.03);
        }

        tr:nth-child(even) {
          background: rgba(255, 255, 255, 0.02);
        }
      }

      blockquote {
        color: #9ca3af;
        border-left-color: #60a5fa;
      }

      hr {
        border-top-color: #374151;
      }
    }
  }
}
</style>

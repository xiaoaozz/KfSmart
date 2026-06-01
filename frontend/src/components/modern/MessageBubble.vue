<script setup lang="ts">
import { computed } from 'vue';

interface Props {
  /** 消息角色 */
  role: 'user' | 'assistant';
  /** 消息内容 */
  content: string;
  /** 消息状态 */
  status?: 'pending' | 'loading' | 'finished' | 'error';
  /** 时间戳 */
  timestamp?: string;
  /** 是否显示头像 */
  showAvatar?: boolean;
  /** 头像URL */
  avatarUrl?: string;
  /** 是否可复制 */
  copyable?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  status: 'finished',
  showAvatar: true,
  copyable: true
});

const emit = defineEmits<{
  copy: [content: string];
  retry: [];
}>();

const bubbleClasses = computed(() => {
  const baseClass = 'message-bubble p-4 max-w-3xl transition-all duration-250 animate-slide-up';
  
  if (props.role === 'user') {
    return [
      baseClass,
      'bubble-user',
      'ml-auto',
      'text-white'
    ];
  }
  
  return [
    baseClass,
    'bubble-assistant',
    'bg-white dark:bg-dark-container',
    'text-gray-800 dark:text-gray-100',
    'shadow-md'
  ];
});

const containerClasses = computed(() => {
  return [
    'flex gap-3 mb-4',
    {
      'flex-row-reverse': props.role === 'user',
      'flex-row': props.role === 'assistant'
    }
  ];
});

function handleCopy() {
  if (props.copyable) {
    emit('copy', props.content);
  }
}

function handleRetry() {
  emit('retry');
}
</script>

<template>
  <div :class="containerClasses">
    <!-- 头像 -->
    <div v-if="showAvatar" class="flex-shrink-0">
      <div
        v-if="role === 'user'"
        class="w-10 h-10 rd-full bg-gradient-to-br from-primary-500 to-primary-700 flex-cc text-white font-600"
      >
        <div v-if="avatarUrl" class="w-full h-full rd-full overflow-hidden">
          <img :src="avatarUrl" alt="User" class="w-full h-full object-cover" />
        </div>
        <div v-else class="i-carbon:user text-xl" />
      </div>
      
      <div
        v-else
        class="w-10 h-10 rd-full bg-gradient-to-br from-secondary-400 to-secondary-600 flex-cc text-white"
      >
        <div class="i-carbon:bot text-xl" />
      </div>
    </div>

    <!-- 消息内容区 -->
    <div class="flex-1 min-w-0">
      <!-- 消息气泡 -->
      <div :class="bubbleClasses">
        <!-- 加载中状态 -->
        <div v-if="status === 'loading'" class="flex items-center gap-2">
          <div class="flex gap-1">
            <span class="w-2 h-2 rd-full bg-current animate-bounce" style="animation-delay: 0ms" />
            <span class="w-2 h-2 rd-full bg-current animate-bounce" style="animation-delay: 150ms" />
            <span class="w-2 h-2 rd-full bg-current animate-bounce" style="animation-delay: 300ms" />
          </div>
          <span class="text-sm opacity-70">正在思考...</span>
        </div>

        <!-- 错误状态 -->
        <div v-else-if="status === 'error'" class="text-error">
          <div class="flex items-center gap-2 mb-2">
            <div class="i-carbon:warning text-xl" />
            <span class="font-500">消息发送失败</span>
          </div>
          <button
            class="text-sm underline hover:no-underline"
            @click="handleRetry"
          >
            点击重试
          </button>
        </div>

        <!-- 正常内容 -->
        <div v-else>
          <slot>
            <div class="prose dark:prose-invert max-w-none" v-html="content" />
          </slot>
        </div>

        <!-- 打字机光标 (仅在加载时显示) -->
        <span
          v-if="status === 'loading'"
          class="inline-block w-0.5 h-4 bg-current ml-1 animate-blink"
        />
      </div>

      <!-- 操作栏 -->
      <div
        v-if="status === 'finished' && copyable"
        class="flex items-center gap-2 mt-2 px-2 opacity-0 group-hover:opacity-100 transition-opacity"
      >
        <button
          class="text-xs text-gray-500 dark:text-gray-400 hover:text-primary-500 transition-colors flex items-center gap-1"
          @click="handleCopy"
        >
          <div class="i-carbon:copy text-sm" />
          <span>复制</span>
        </button>
        
        <span v-if="timestamp" class="text-xs text-gray-400 dark:text-gray-500">
          {{ timestamp }}
        </span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.bubble-user {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 1.5rem;
  border-bottom-right-radius: 0.375rem;
}

.bubble-assistant {
  border-radius: 1.5rem;
  border-bottom-left-radius: 0.375rem;
}

.message-bubble:hover {
  transform: translateX(0);
}

/* 打字机光标动画 */
@keyframes blink {
  0%, 49% {
    opacity: 1;
  }
  50%, 100% {
    opacity: 0;
  }
}

.animate-blink {
  animation: blink 1s step-end infinite;
}
</style>

<script setup lang="ts">
import { NModal, NSpin } from 'naive-ui';
import { fetchGetChunkContext } from '@/service/api';

defineOptions({ name: 'ChunkDetailModal' });

const props = defineProps<{
  visible: boolean;
  /** 当前 chunk 所属文件 */
  fileMd5: string;
  /** 当前 chunk 序号 */
  chunkId: number;
  /** 文件名 */
  fileName: string;
  /** 引用编号 */
  referenceNumber: number;
}>();

const emit = defineEmits<{
  (e: 'update:visible', v: boolean): void;
}>();

const show = computed({
  get: () => props.visible,
  set: (v) => emit('update:visible', v)
});

const loading = ref(false);
const contextList = ref<Api.Chat.ChunkContextItem[]>([]);
const errorMsg = ref('');

async function loadContext() {
  loading.value = true;
  errorMsg.value = '';
  contextList.value = [];
  try {
    const { error, data } = await fetchGetChunkContext({
      fileMd5: props.fileMd5,
      chunkId: props.chunkId,
      contextSize: 0
    });
    if (!error && data) {
      // 只展示当前最匹配的 chunk，过滤掉上下文片段
      const all = Array.isArray(data) ? data : [];
      contextList.value = all.filter(item => item.isCurrent);
    } else {
      errorMsg.value = '加载上下文失败';
    }
  } catch (e: any) {
    errorMsg.value = e?.message || '加载上下文失败';
  } finally {
    contextList.value = contextList.value ?? [];
    loading.value = false;
  }
}

watch(() => props.visible, (v) => {
  if (v) {
    loadContext();
  } else {
    contextList.value = [];
    errorMsg.value = '';
  }
});

// 组件挂载时，如果弹窗已经是可见状态（v-if 场景下组件被新建时），立即加载
onMounted(() => {
  if (props.visible) {
    loadContext();
  }
});

// 当 props 变化时重新加载（如用户切换到另一个 chunk）
watch([() => props.fileMd5, () => props.chunkId], () => {
  if (props.visible) {
    loadContext();
  }
});
</script>

<template>
  <NModal
    v-model:show="show"
    preset="card"
    title="文档片段详情"
    style="width: 700px; max-width: 90vw;"
    :mask-closable="true"
  >
    <!-- 文件信息头 -->
    <div class="mb-5 pb-4 border-b border-gray-100 dark:border-gray-700">
      <div class="flex items-center gap-2 text-sm">
        <span class="inline-flex items-center justify-center w-6 h-6 rounded-full bg-blue-100 dark:bg-blue-900/40 text-blue-600 dark:text-blue-400 text-xs font-bold">
          {{ referenceNumber }}
        </span>
        <span class="font-medium text-gray-800 dark:text-gray-200">{{ fileName }}</span>
        <span class="text-gray-400">· Chunk #{{ chunkId }}</span>
      </div>
    </div>

    <!-- 加载中 -->
    <NSpin :show="loading">
      <div v-if="errorMsg" class="py-8 text-center text-sm text-red-500">
        {{ errorMsg }}
      </div>
      <div
        v-else-if="contextList.length === 0 && !loading"
        class="py-8 text-center text-sm text-gray-400"
      >
        暂无上下文数据
      </div>
      <div v-else class="max-h-[28rem] overflow-y-auto space-y-3 pr-1">
        <div
          v-for="(item, idx) in contextList"
          :key="item?.chunkId ?? idx"
          class="rounded-lg border px-4 py-3 text-sm leading-relaxed transition-colors"
          :class="item?.isCurrent
            ? 'bg-blue-50 dark:bg-blue-900/20 border-blue-300 dark:border-blue-600 text-gray-800 dark:text-gray-200 shadow-sm'
            : 'bg-gray-50 dark:bg-gray-800 border-gray-200 dark:border-gray-700 text-gray-500 dark:text-gray-400'"
        >
          <div class="flex items-center gap-2 mb-2">
            <span
              class="inline-block w-5 h-5 rounded-full text-[11px] font-semibold leading-5 text-center"
              :class="item?.isCurrent
                ? 'bg-blue-500 text-white'
                : 'bg-gray-300 dark:bg-gray-600 text-gray-600 dark:text-gray-300'"
            >
              {{ item?.chunkId }}
            </span>
            <span class="text-xs text-gray-400 dark:text-gray-500">
              <template v-if="item?.isCurrent">当前片段</template>
              <template v-else>上下文 Chunk #{{ item?.chunkId }}</template>
            </span>
          </div>
          <p class="whitespace-pre-wrap break-words">{{ item?.textContent }}</p>
        </div>
      </div>
    </NSpin>
  </NModal>
</template>

<style scoped>
</style>
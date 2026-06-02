<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import type { EChartsOption } from 'echarts';
import { request } from '@/service/request';

defineOptions({
  name: 'KnowledgeStats'
});

interface KnowledgeBaseItem {
  name: string;
  docCount: number;
  totalSize: number;
  updateTime: string;
}

const knowledgeBases = ref<KnowledgeBaseItem[]>([]);
const loading = ref(false);

const totalDocs = computed(() => knowledgeBases.value.reduce((sum, kb) => sum + kb.docCount, 0));
const totalSize = computed(() => {
  const total = knowledgeBases.value.reduce((sum, kb) => sum + kb.totalSize, 0);
  return formatFileSize(total);
});

const chartColors = ['#667eea', '#06b6d4', '#10b981', '#f59e0b', '#8b5cf6', '#ec4899', '#6366f1', '#14b8a6'];

const chartOptions = computed<EChartsOption>(() => ({
  tooltip: {
    trigger: 'item',
    formatter: '{a} <br/>{b}: {c} ({d}%)'
  },
  legend: {
    orient: 'vertical',
    right: '10%',
    top: 'center',
    textStyle: {
      color: '#666'
    }
  },
  series: [
    {
      name: '文档分布',
      type: 'pie',
      radius: ['45%', '70%'],
      center: ['35%', '50%'],
      avoidLabelOverlap: false,
      itemStyle: {
        borderRadius: 8,
        borderColor: '#fff',
        borderWidth: 2
      },
      label: {
        show: false,
        position: 'center'
      },
      emphasis: {
        label: {
          show: true,
          fontSize: 20,
          fontWeight: 'bold'
        }
      },
      labelLine: {
        show: false
      },
      data: knowledgeBases.value.map((kb, index) => ({
        value: kb.docCount,
        name: kb.name,
        itemStyle: {
          color: chartColors[index % chartColors.length]
        }
      }))
    }
  ]
}));

function formatFileSize(bytes: number): string {
  if (!bytes || bytes === 0) return '0 B';
  const units = ['B', 'KB', 'MB', 'GB', 'TB'];
  const k = 1024;
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + units[i];
}

function formatTime(dateStr: string): string {
  if (!dateStr) return '--';
  try {
    const d = new Date(dateStr);
    const now = new Date();
    const diffMs = now.getTime() - d.getTime();
    const diffHrs = Math.floor(diffMs / (1000 * 60 * 60));
    if (diffHrs < 1) return '刚刚';
    if (diffHrs < 24) return `${diffHrs}小时前`;
    const diffDays = Math.floor(diffHrs / 24);
    if (diffDays < 30) return `${diffDays}天前`;
    return `${d.getMonth() + 1}-${d.getDate()}`;
  } catch {
    return dateStr;
  }
}

/** 从后端获取文件列表，按组织标签聚合 */
async function fetchKnowledgeStats() {
  loading.value = true;
  try {
    const { error, data } = await request<any[]>({ url: '/documents/uploads' });
    if (!error && data && data.length > 0) {
      // 按 orgTagName 聚合
      const tagMap = new Map<string, { count: number; totalSize: number; latestTime: string }>();
      
      data.forEach((file: any) => {
        const tagName = file.orgTagName || '未分类';
        const existing = tagMap.get(tagName);
        if (existing) {
          existing.count++;
          existing.totalSize += file.totalSize || 0;
          if (file.createdAt && (!existing.latestTime || file.createdAt > existing.latestTime)) {
            existing.latestTime = file.createdAt;
          }
        } else {
          tagMap.set(tagName, {
            count: 1,
            totalSize: file.totalSize || 0,
            latestTime: file.createdAt || ''
          });
        }
      });

      knowledgeBases.value = Array.from(tagMap.entries())
        .map(([name, info]) => ({
          name,
          docCount: info.count,
          totalSize: info.totalSize,
          updateTime: formatTime(info.latestTime)
        }))
        .sort((a, b) => b.docCount - a.docCount);
    } else {
      knowledgeBases.value = [];
    }
  } catch (e) {
    console.error('[KnowledgeStats] 获取知识库统计失败:', e);
  } finally {
    loading.value = false;
  }
}

onMounted(() => {
  fetchKnowledgeStats();
});
</script>

<template>
  <div class="knowledge-stats">
    <NCard>
      <template #header>
        <div class="flex items-center justify-between">
          <div>
            <h2 class="text-lg font-semibold text-gray-900 dark:text-white">知识库统计</h2>
            <p class="text-xs text-gray-500 dark:text-gray-400 mt-1">按组织标签统计的文档分布</p>
          </div>
          <NButton text @click="fetchKnowledgeStats">
            <template #icon>
              <icon-carbon:renew class="text-lg" />
            </template>
          </NButton>
        </div>
      </template>

      <NSpin :show="loading">
        <div v-if="knowledgeBases.length === 0" class="flex flex-col items-center justify-center py-12 text-gray-400">
          <icon-carbon:document-blank class="text-4xl mb-3" />
          <p class="text-sm">暂无文档数据</p>
          <p class="text-xs mt-1">上传文档后将在此显示统计</p>
        </div>

        <div v-else class="flex flex-col gap-4">
          <!-- 图表区域 -->
          <div class="chart-container">
            <VChart :option="chartOptions" autoresize />
          </div>

          <!-- 详细列表 -->
          <div class="knowledge-list space-y-2">
            <div
              v-for="(kb, index) in knowledgeBases"
              :key="kb.name"
              class="flex items-center justify-between p-3 rounded-lg bg-gray-50 dark:bg-gray-800 hover:bg-gray-100 dark:hover:bg-gray-700 transition-colors"
            >
              <div class="flex items-center gap-3 flex-1">
                <div
                  class="w-3 h-3 rounded-full flex-shrink-0"
                  :style="{
                    backgroundColor: chartColors[index % chartColors.length]
                  }"
                />
                <div class="flex-1 min-w-0">
                  <div class="text-sm font-medium text-gray-900 dark:text-white truncate">
                    {{ kb.name }}
                  </div>
                  <div class="text-xs text-gray-500 dark:text-gray-400">
                    更新于 {{ kb.updateTime }}
                  </div>
                </div>
              </div>
              <div class="flex flex-col items-end gap-1 flex-shrink-0 ml-4">
                <span class="text-sm font-semibold text-gray-900 dark:text-white">
                  {{ kb.docCount }} 篇
                </span>
                <span class="text-xs text-gray-500 dark:text-gray-400">
                  {{ formatFileSize(kb.totalSize) }}
                </span>
              </div>
            </div>
          </div>

          <!-- 统计摘要 -->
          <div class="flex items-center justify-between p-4 rounded-lg bg-gradient-to-r from-purple-50 to-blue-50 dark:from-purple-900/20 dark:to-blue-900/20 border border-purple-100 dark:border-purple-800">
            <div class="flex items-center gap-3">
              <div class="w-10 h-10 rounded-lg bg-purple-500/10 flex items-center justify-center">
                <icon-carbon:document class="text-xl text-purple-500" />
              </div>
              <div>
                <div class="text-xs text-gray-600 dark:text-gray-400">文档总数</div>
                <div class="text-lg font-bold text-gray-900 dark:text-white">{{ totalDocs }}</div>
              </div>
            </div>
            <div class="flex items-center gap-3">
              <div>
                <div class="text-xs text-gray-600 dark:text-gray-400">总容量</div>
                <div class="text-lg font-bold text-gray-900 dark:text-white">{{ totalSize }}</div>
              </div>
              <div class="w-10 h-10 rounded-lg bg-blue-500/10 flex items-center justify-center">
                <icon-carbon:data-base class="text-xl text-blue-500" />
              </div>
            </div>
          </div>
        </div>
      </NSpin>
    </NCard>
  </div>
</template>

<style scoped lang="scss">
.knowledge-stats {
  .chart-container {
    height: 280px;
    width: 100%;
  }
}
</style>
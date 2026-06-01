<script setup lang="ts">
import { ref, computed } from 'vue';
import type { EChartsOption } from 'echarts';

defineOptions({
  name: 'KnowledgeStats'
});

const knowledgeBases = ref([
  { name: '产品知识库', docCount: 423, size: '2.3GB', updateTime: '2小时前' },
  { name: '技术文档', docCount: 312, size: '1.8GB', updateTime: '5小时前' },
  { name: '客服话术', docCount: 267, size: '856MB', updateTime: '1天前' },
  { name: '营销资料', docCount: 156, size: '645MB', updateTime: '2天前' },
  { name: '其他', docCount: 90, size: '423MB', updateTime: '3天前' }
]);

const totalDocs = computed(() => knowledgeBases.value.reduce((sum, kb) => sum + kb.docCount, 0));

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
          color: [
            '#667eea',
            '#06b6d4',
            '#10b981',
            '#f59e0b',
            '#8b5cf6'
          ][index]
        }
      }))
    }
  ]
}));
</script>

<template>
  <div class="knowledge-stats">
    <NCard>
      <template #header>
        <div>
          <h2 class="text-lg font-semibold text-gray-900 dark:text-white">知识库统计</h2>
          <p class="text-xs text-gray-500 dark:text-gray-400 mt-1">文档分布与存储概览</p>
        </div>
      </template>

      <div class="flex flex-col gap-4">
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
                  backgroundColor: ['#667eea', '#06b6d4', '#10b981', '#f59e0b', '#8b5cf6'][index]
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
                {{ kb.size }}
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
              <div class="text-lg font-bold text-gray-900 dark:text-white">6.0 GB</div>
            </div>
            <div class="w-10 h-10 rounded-lg bg-blue-500/10 flex items-center justify-center">
              <icon-carbon:data-base class="text-xl text-blue-500" />
            </div>
          </div>
        </div>
      </div>
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

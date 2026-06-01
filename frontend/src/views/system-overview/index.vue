<script setup lang="ts">
import { ref } from 'vue';
import DataOverview from './modules/data-overview.vue';
import UsageTrends from './modules/usage-trends.vue';
import KnowledgeStats from './modules/knowledge-stats.vue';
import PerformanceMetrics from './modules/performance-metrics.vue';
import RecentActivities from './modules/recent-activities.vue';

defineOptions({
  name: 'SystemOverview'
});
</script>

<template>
  <div class="system-overview-page h-full overflow-auto bg-gray-50 dark:bg-gray-900">
    <div class="p-6 space-y-6">
      <!-- 页面标题 -->
      <div class="mb-6">
        <h1 class="text-2xl font-bold text-gray-900 dark:text-white">系统总览</h1>
        <p class="text-sm text-gray-500 dark:text-gray-400 mt-1">数据实时更新，展示系统运行状态和关键指标</p>
      </div>

      <!-- 核心数据概览 -->
      <DataOverview />

      <!-- 使用趋势图表 -->
      <UsageTrends />

      <!-- 第二行：知识库统计 + 性能指标 -->
      <div class="grid grid-cols-2 gap-6">
        <KnowledgeStats />
        <PerformanceMetrics />
      </div>

      <!-- 最近活动 -->
      <RecentActivities />
    </div>
  </div>
</template>

<style scoped lang="scss">
.system-overview-page {
  :deep(.n-card) {
    border-radius: 12px;
    box-shadow: 0 1px 3px 0 rgba(0, 0, 0, 0.1), 0 1px 2px 0 rgba(0, 0, 0, 0.06);
  }
}
</style>

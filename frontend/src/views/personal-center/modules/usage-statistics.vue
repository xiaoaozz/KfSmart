<script setup lang="ts">
import { ref, computed } from 'vue';

defineOptions({ name: 'UsageStatistics' });

const selectedRange = ref('7d');

const overview = ref([
  { label: '总对话次数', value: 128, unit: '次', icon: 'carbon:chat', color: 'text-purple-500', bg: 'bg-purple-50 dark:bg-purple-900/20', change: '+12%' },
  { label: '上传文档数', value: 47, unit: '份', icon: 'carbon:upload', color: 'text-blue-500', bg: 'bg-blue-50 dark:bg-blue-900/20', change: '+5份' },
  { label: '知识库数量', value: 8, unit: '个', icon: 'carbon:folder', color: 'text-green-500', bg: 'bg-green-50 dark:bg-green-900/20', change: '+1个' },
  { label: '本周活跃天', value: 5, unit: '天', icon: 'carbon:calendar', color: 'text-orange-500', bg: 'bg-orange-50 dark:bg-orange-900/20', change: '全勤' },
  { label: '累计使用时长', value: 36, unit: '小时', icon: 'carbon:time', color: 'text-cyan-500', bg: 'bg-cyan-50 dark:bg-cyan-900/20', change: '+2.5h' },
  { label: '收藏内容数', value: 24, unit: '项', icon: 'carbon:bookmark', color: 'text-pink-500', bg: 'bg-pink-50 dark:bg-pink-900/20', change: '+3项' },
]);

const dailyData7 = [
  { day: '6/28', chats: 14 },
  { day: '6/29', chats: 8 },
  { day: '6/30', chats: 22 },
  { day: '7/1', chats: 19 },
  { day: '7/2', chats: 31 },
  { day: '7/3', chats: 17 },
  { day: '7/4', chats: 25 },
];
const dailyData30 = Array.from({ length: 30 }, (_, i) => ({
  day: `${i + 1}日`,
  chats: Math.floor(Math.random() * 35) + 5
}));

const currentData = computed(() => selectedRange.value === '7d' ? dailyData7 : dailyData30);
const maxValue = computed(() => Math.max(...currentData.value.map(d => d.chats)));

const topKnowledge = [
  { name: '技术文档库', count: 42, percent: 90, color: '#5865F2' },
  { name: '大模型应用知识库', count: 35, percent: 75, color: '#3BA55C' },
  { name: 'Java 开发规范库', count: 28, percent: 60, color: '#FAA61A' },
  { name: '产品设计规范', count: 15, percent: 32, color: '#ED4245' },
  { name: '运营数据分析库', count: 8, percent: 17, color: '#EB459E' },
];

const featureUsage = [
  { label: '智能对话', value: 52, color: '#5865F2' },
  { label: '文档检索', value: 28, color: '#3BA55C' },
  { label: '知识库管理', value: 13, color: '#FAA61A' },
  { label: '其他功能', value: 7, color: '#9CA3AF' },
];
const total = featureUsage.reduce((a, b) => a + b.value, 0);
</script>

<template>
  <div class="usage-statistics space-y-6">
    <!-- 概览卡片 -->
    <div class="grid grid-cols-3 gap-3">
      <div
        v-for="item in overview"
        :key="item.label"
        class="flex items-center gap-3 p-3 bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700"
      >
        <div class="w-10 h-10 rounded-xl flex items-center justify-center flex-shrink-0" :class="item.bg">
          <component :is="item.icon" class="text-lg" :class="item.color" />
        </div>
        <div class="min-w-0">
          <p class="text-xs text-gray-400">{{ item.label }}</p>
          <div class="flex items-baseline gap-1">
            <span class="text-lg font-bold text-gray-800 dark:text-gray-100">{{ item.value }}</span>
            <span class="text-xs text-gray-400">{{ item.unit }}</span>
          </div>
          <p class="text-xs text-green-500 mt-0.5">{{ item.change }}</p>
        </div>
      </div>
    </div>

    <!-- 使用趋势图 -->
    <div class="bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 p-4">
      <div class="flex items-center justify-between mb-4">
        <div>
          <h3 class="text-sm font-semibold text-gray-800 dark:text-gray-100">使用趋势</h3>
          <p class="text-xs text-gray-400 mt-0.5">每日对话次数</p>
        </div>
        <NRadioGroup v-model:value="selectedRange" size="small">
          <NRadioButton value="7d">近7天</NRadioButton>
          <NRadioButton value="30d">近30天</NRadioButton>
        </NRadioGroup>
      </div>
      <div class="flex items-end gap-1 h-28">
        <div
          v-for="(day, idx) in currentData"
          :key="idx"
          class="flex-1 flex flex-col items-center gap-1 group cursor-pointer"
        >
          <NTooltip placement="top">
            <template #trigger>
              <div class="w-full flex flex-col items-center">
                <div
                  class="w-full rounded-sm bg-blue-400/70 hover:bg-blue-500 transition-all"
                  :style="{ height: `${(day.chats / maxValue) * 96}px` }"
                />
              </div>
            </template>
            {{ day.day }}：{{ day.chats }} 次对话
          </NTooltip>
          <span class="text-gray-400" style="font-size:9px">{{ day.day }}</span>
        </div>
      </div>
    </div>

    <!-- 底部双列 -->
    <div class="grid grid-cols-2 gap-4">
      <!-- 知识库使用 Top5 -->
      <div class="bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 p-4">
        <h3 class="text-sm font-semibold text-gray-800 dark:text-gray-100 mb-3">知识库使用 Top5</h3>
        <div class="space-y-3">
          <div v-for="(kb, idx) in topKnowledge" :key="idx">
            <div class="flex items-center justify-between mb-1">
              <div class="flex items-center gap-2">
                <span class="text-xs text-gray-400 w-3">{{ idx + 1 }}</span>
                <span class="text-xs text-gray-700 dark:text-gray-300 truncate max-w-28">{{ kb.name }}</span>
              </div>
              <span class="text-xs text-gray-500">{{ kb.count }} 次</span>
            </div>
            <div class="h-1.5 bg-gray-100 dark:bg-gray-700 rounded-full overflow-hidden">
              <div
                class="h-full rounded-full transition-all duration-700"
                :style="{ width: `${kb.percent}%`, background: kb.color }"
              />
            </div>
          </div>
        </div>
      </div>

      <!-- 功能使用分布 -->
      <div class="bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 p-4">
        <h3 class="text-sm font-semibold text-gray-800 dark:text-gray-100 mb-3">功能使用分布</h3>
        <div class="flex items-center gap-5">
          <div class="relative w-20 h-20 flex-shrink-0">
            <svg viewBox="0 0 36 36" class="w-full h-full -rotate-90">
              <circle cx="18" cy="18" r="15.9" fill="none" stroke="#F3F4F6" stroke-width="3.8" class="dark:stroke-gray-700" />
              <circle
                v-for="(feat, fi) in featureUsage"
                :key="fi"
                cx="18" cy="18" r="15.9" fill="none"
                :stroke="feat.color"
                stroke-width="3.8"
                :stroke-dasharray="`${(feat.value / total) * 100} ${100 - (feat.value / total) * 100}`"
                :stroke-dashoffset="-featureUsage.slice(0, fi).reduce((a, b) => a + (b.value / total) * 100, 0)"
              />
            </svg>
            <div class="absolute inset-0 flex items-center justify-center">
              <span class="text-xs font-bold text-gray-600 dark:text-gray-300">{{ total }}</span>
            </div>
          </div>
          <div class="flex-1 space-y-1.5">
            <div v-for="feat in featureUsage" :key="feat.label" class="flex items-center justify-between">
              <div class="flex items-center gap-1.5">
                <span class="w-2 h-2 rounded-full flex-shrink-0" :style="{ background: feat.color }" />
                <span class="text-xs text-gray-600 dark:text-gray-400">{{ feat.label }}</span>
              </div>
              <span class="text-xs font-medium text-gray-700 dark:text-gray-300">{{ feat.value }}%</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

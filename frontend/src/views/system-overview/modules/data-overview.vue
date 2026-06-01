<script setup lang="ts">
import { ref } from 'vue';

defineOptions({
  name: 'DataOverview'
});

const stats = ref([
  {
    icon: 'data-base',
    label: '知识库总数',
    value: '12',
    change: '+2',
    trend: 'up',
    changeLabel: '较昨日',
    color: 'blue',
    tooltip: '当前系统中创建的知识库总数量'
  },
  {
    icon: 'document',
    label: '文档总数',
    value: '1,248',
    change: '+36',
    trend: 'up',
    changeLabel: '较昨日',
    color: 'green',
    tooltip: '所有知识库中的文档总数'
  },
  {
    icon: 'user-multiple',
    label: '活跃用户',
    value: '86',
    change: '+12',
    trend: 'up',
    changeLabel: '本周',
    color: 'purple',
    tooltip: '本周有活动的用户数'
  },
  {
    icon: 'chat',
    label: '今日会话',
    value: '356',
    change: '+58',
    trend: 'up',
    changeLabel: '较昨日',
    color: 'orange',
    tooltip: '今日产生的会话总数'
  },
  {
    icon: 'chat-bot',
    label: '今日回答',
    value: '2,847',
    change: '+423',
    trend: 'up',
    changeLabel: '较昨日',
    color: 'cyan',
    tooltip: 'AI助手今日回答的问题数量'
  },
  {
    icon: 'time',
    label: '平均响应',
    value: '1.42s',
    change: '-0.21s',
    trend: 'down',
    changeLabel: '较昨日',
    color: 'pink',
    tooltip: 'AI回答的平均响应时间'
  },
  {
    icon: 'thumbs-up',
    label: '满意度',
    value: '94.2%',
    change: '+2.3%',
    trend: 'up',
    changeLabel: '本月',
    color: 'emerald',
    tooltip: '用户对回答的满意度评分'
  },
  {
    icon: 'checkmark-filled',
    label: '解决率',
    value: '87.5%',
    change: '+3.1%',
    trend: 'up',
    changeLabel: '本月',
    color: 'indigo',
    tooltip: '问题一次性解决的比例'
  }
]);

const getColorClasses = (color: string) => {
  const colorMap: Record<string, { icon: string; bg: string; text: string }> = {
    blue: {
      icon: 'text-blue-500',
      bg: 'bg-blue-50 dark:bg-blue-900/20',
      text: 'text-blue-600 dark:text-blue-400'
    },
    green: {
      icon: 'text-green-500',
      bg: 'bg-green-50 dark:bg-green-900/20',
      text: 'text-green-600 dark:text-green-400'
    },
    purple: {
      icon: 'text-purple-500',
      bg: 'bg-purple-50 dark:bg-purple-900/20',
      text: 'text-purple-600 dark:text-purple-400'
    },
    orange: {
      icon: 'text-orange-500',
      bg: 'bg-orange-50 dark:bg-orange-900/20',
      text: 'text-orange-600 dark:text-orange-400'
    },
    cyan: {
      icon: 'text-cyan-500',
      bg: 'bg-cyan-50 dark:bg-cyan-900/20',
      text: 'text-cyan-600 dark:text-cyan-400'
    },
    pink: {
      icon: 'text-pink-500',
      bg: 'bg-pink-50 dark:bg-pink-900/20',
      text: 'text-pink-600 dark:text-pink-400'
    },
    emerald: {
      icon: 'text-emerald-500',
      bg: 'bg-emerald-50 dark:bg-emerald-900/20',
      text: 'text-emerald-600 dark:text-emerald-400'
    },
    indigo: {
      icon: 'text-indigo-500',
      bg: 'bg-indigo-50 dark:bg-indigo-900/20',
      text: 'text-indigo-600 dark:text-indigo-400'
    }
  };
  return colorMap[color] || colorMap.blue;
};
</script>

<template>
  <div class="data-overview">
    <NCard>
      <template #header>
        <div class="flex items-center justify-between">
          <div>
            <h2 class="text-lg font-semibold text-gray-900 dark:text-white">核心数据概览</h2>
            <p class="text-xs text-gray-500 dark:text-gray-400 mt-1">系统关键指标实时监控</p>
          </div>
          <NButton text>
            <template #icon>
              <icon-carbon:renew class="text-lg" />
            </template>
            刷新
          </NButton>
        </div>
      </template>

      <div class="grid grid-cols-4 gap-4">
        <div
          v-for="stat in stats"
          :key="stat.label"
          class="stat-card bg-white dark:bg-gray-800 rounded-xl p-4 border border-gray-100 dark:border-gray-700 hover:shadow-lg transition-all cursor-pointer"
        >
          <NTooltip placement="top">
            <template #trigger>
              <div class="flex flex-col h-full">
                <div class="flex items-start justify-between mb-3">
                  <div :class="[
                    'w-11 h-11 rounded-lg flex items-center justify-center flex-shrink-0',
                    getColorClasses(stat.color).bg
                  ]">
                    <!-- 使用静态图标组件 -->
                    <icon-carbon:data-base v-if="stat.icon === 'data-base'" :class="['text-xl', getColorClasses(stat.color).icon]" />
                    <icon-carbon:document v-else-if="stat.icon === 'document'" :class="['text-xl', getColorClasses(stat.color).icon]" />
                    <icon-carbon:user-multiple v-else-if="stat.icon === 'user-multiple'" :class="['text-xl', getColorClasses(stat.color).icon]" />
                    <icon-carbon:chat v-else-if="stat.icon === 'chat'" :class="['text-xl', getColorClasses(stat.color).icon]" />
                    <icon-carbon:chat-bot v-else-if="stat.icon === 'chat-bot'" :class="['text-xl', getColorClasses(stat.color).icon]" />
                    <icon-carbon:time v-else-if="stat.icon === 'time'" :class="['text-xl', getColorClasses(stat.color).icon]" />
                    <icon-carbon:thumbs-up v-else-if="stat.icon === 'thumbs-up'" :class="['text-xl', getColorClasses(stat.color).icon]" />
                    <icon-carbon:checkmark-filled v-else-if="stat.icon === 'checkmark-filled'" :class="['text-xl', getColorClasses(stat.color).icon]" />
                  </div>
                </div>
                
                <div class="flex-1">
                  <div class="text-xs text-gray-500 dark:text-gray-400 mb-2">{{ stat.label }}</div>
                  <div class="text-2xl font-bold text-gray-900 dark:text-white mb-2">{{ stat.value }}</div>
                </div>

                <div class="flex items-center gap-1 text-xs pt-2 border-t border-gray-100 dark:border-gray-700">
                  <span :class="stat.trend === 'up' ? 'text-green-600 dark:text-green-400' : 'text-cyan-600 dark:text-cyan-400'">
                    {{ stat.changeLabel }} {{ stat.change }}
                  </span>
                  <icon-carbon:arrow-up 
                    v-if="stat.trend === 'up'"
                    class="text-xs text-green-600 dark:text-green-400"
                  />
                  <icon-carbon:arrow-down 
                    v-else
                    class="text-xs text-cyan-600 dark:text-cyan-400"
                  />
                </div>
              </div>
            </template>
            {{ stat.tooltip }}
          </NTooltip>
        </div>
      </div>
    </NCard>
  </div>
</template>

<style scoped lang="scss">
.data-overview {
  .stat-card {
    transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
    
    &:hover {
      transform: translateY(-4px);
      border-color: rgb(var(--primary-color));
    }
  }
}
</style>

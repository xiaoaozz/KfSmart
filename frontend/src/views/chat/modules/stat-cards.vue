<script setup lang="ts">
import { ref } from 'vue';

defineOptions({
  name: 'StatCards'
});

// 折叠状态
const isCollapsed = ref(false);

const stats = ref([
  {
    icon: 'data-base',
    label: '知识库数量',
    value: '12',
    change: '+2',
    trend: 'up',
    color: 'blue'
  },
  {
    icon: 'document',
    label: '文档总数',
    value: '1,248',
    change: '+36',
    trend: 'up',
    color: 'green'
  },
  {
    icon: 'chat',
    label: '今日回答',
    value: '356',
    change: '+58',
    trend: 'up',
    color: 'purple'
  },
  {
    icon: 'time',
    label: '平均响应时间',
    value: '1.42s',
    change: '-0.21s',
    trend: 'down',
    color: 'cyan'
  }
]);

// 切换折叠状态
const toggleCollapse = () => {
  isCollapsed.value = !isCollapsed.value;
};

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
    cyan: {
      icon: 'text-cyan-500',
      bg: 'bg-cyan-50 dark:bg-cyan-900/20',
      text: 'text-cyan-600 dark:text-cyan-400'
    }
  };
  return colorMap[color] || colorMap.blue;
};
</script>

<template>
  <div class="stat-cards-container">
    <!-- 折叠按钮 -->
    <div class="flex items-center justify-between mb-4">
      <h3 class="text-base font-semibold text-gray-700 dark:text-gray-300">数据概览</h3>
      <NButton text circle @click="toggleCollapse">
        <template #icon>
          <icon-carbon:chevron-up v-if="!isCollapsed" class="text-lg transition-transform" />
          <icon-carbon:chevron-down v-else class="text-lg transition-transform" />
        </template>
      </NButton>
    </div>

    <!-- 统计卡片 - 可折叠 -->
    <Transition name="collapse">
      <div v-show="!isCollapsed" class="grid grid-cols-4 gap-6">
        <div
          v-for="stat in stats"
          :key="stat.label"
          class="stat-card bg-white dark:bg-gray-800 rounded-xl p-4 border border-gray-100 dark:border-gray-700 hover:shadow-md transition-all"
        >
          <div class="flex items-start justify-between">
            <div class="flex-1">
              <div class="flex items-center gap-3 mb-3">
                <div :class="[
                  'w-10 h-10 rounded-lg flex items-center justify-center flex-shrink-0',
                  getColorClasses(stat.color).bg
                ]">
                  <!-- 使用静态图标组件 -->
                  <icon-carbon:data-base v-if="stat.icon === 'data-base'" :class="['text-xl', getColorClasses(stat.color).icon]" />
                  <icon-carbon:document v-else-if="stat.icon === 'document'" :class="['text-xl', getColorClasses(stat.color).icon]" />
                  <icon-carbon:chat v-else-if="stat.icon === 'chat'" :class="['text-xl', getColorClasses(stat.color).icon]" />
                  <icon-carbon:time v-else-if="stat.icon === 'time'" :class="['text-xl', getColorClasses(stat.color).icon]" />
                </div>
              </div>
              <div class="text-xs text-gray-500 dark:text-gray-400 mb-2">{{ stat.label }}</div>
              <div class="text-2xl font-bold text-gray-900 dark:text-white mb-2">{{ stat.value }}</div>
            </div>
          </div>
          <div class="flex items-center gap-1 text-xs">
            <span :class="stat.trend === 'up' ? 'text-green-600 dark:text-green-400' : 'text-cyan-600 dark:text-cyan-400'">
              较昨日 {{ stat.change }}
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
      </div>
    </Transition>
  </div>
</template>

<style scoped lang="scss">
.stat-cards-container {
  .stat-card {
    transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
    
    &:hover {
      transform: translateY(-2px);
    }
  }
}

// 折叠动画
.collapse-enter-active,
.collapse-leave-active {
  transition: all 0.3s ease;
  overflow: hidden;
}

.collapse-enter-from,
.collapse-leave-to {
  opacity: 0;
  max-height: 0;
  margin-top: -16px;
}

.collapse-enter-to,
.collapse-leave-from {
  opacity: 1;
  max-height: 500px;
  margin-top: 0;
}
</style>

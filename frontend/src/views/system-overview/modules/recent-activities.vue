<script setup lang="ts">
import { ref } from 'vue';

defineOptions({
  name: 'RecentActivities'
});

const activities = ref([
  {
    id: 1,
    type: 'conversation',
    icon: 'chat',
    title: '新建会话',
    description: '用户 张三 创建了新的会话「产品咨询」',
    time: '2分钟前',
    color: 'blue'
  },
  {
    id: 2,
    type: 'document',
    icon: 'document-add',
    title: '上传文档',
    description: '管理员上传了 5 个文档到「技术文档」知识库',
    time: '15分钟前',
    color: 'green'
  },
  {
    id: 3,
    type: 'user',
    icon: 'user-follow',
    title: '新用户注册',
    description: '用户 李四 完成注册并加入系统',
    time: '1小时前',
    color: 'purple'
  },
  {
    id: 4,
    type: 'knowledge',
    icon: 'data-base',
    title: '知识库更新',
    description: '「产品知识库」添加了 12 个新文档',
    time: '2小时前',
    color: 'cyan'
  },
  {
    id: 5,
    type: 'system',
    icon: 'settings-adjust',
    title: '系统配置',
    description: '管理员更新了系统参数配置',
    time: '3小时前',
    color: 'orange'
  },
  {
    id: 6,
    type: 'feedback',
    icon: 'thumbs-up',
    title: '用户反馈',
    description: '收到 23 条正面反馈,满意度提升',
    time: '5小时前',
    color: 'pink'
  }
]);

const getColorClasses = (color: string) => {
  const colorMap: Record<string, { icon: string; bg: string }> = {
    blue: {
      icon: 'text-blue-500',
      bg: 'bg-blue-50 dark:bg-blue-900/20'
    },
    green: {
      icon: 'text-green-500',
      bg: 'bg-green-50 dark:bg-green-900/20'
    },
    purple: {
      icon: 'text-purple-500',
      bg: 'bg-purple-50 dark:bg-purple-900/20'
    },
    cyan: {
      icon: 'text-cyan-500',
      bg: 'bg-cyan-50 dark:bg-cyan-900/20'
    },
    orange: {
      icon: 'text-orange-500',
      bg: 'bg-orange-50 dark:bg-orange-900/20'
    },
    pink: {
      icon: 'text-pink-500',
      bg: 'bg-pink-50 dark:bg-pink-900/20'
    }
  };
  return colorMap[color] || colorMap.blue;
};
</script>

<template>
  <div class="recent-activities">
    <NCard>
      <template #header>
        <div class="flex items-center justify-between">
          <div>
            <h2 class="text-lg font-semibold text-gray-900 dark:text-white">最近活动</h2>
            <p class="text-xs text-gray-500 dark:text-gray-400 mt-1">系统最新动态实时追踪</p>
          </div>
          <NButton text>
            <template #icon>
              <icon-carbon:view class="text-lg" />
            </template>
            查看全部
          </NButton>
        </div>
      </template>

      <div class="activities-list space-y-1">
        <div
          v-for="activity in activities"
          :key="activity.id"
          class="activity-item flex items-start gap-4 p-3 rounded-lg hover:bg-gray-50 dark:hover:bg-gray-800 transition-colors cursor-pointer"
        >
          <!-- 图标 -->
          <div :class="[
            'w-10 h-10 rounded-lg flex items-center justify-center flex-shrink-0',
            getColorClasses(activity.color).bg
          ]">
            <!-- 使用静态图标组件 -->
            <icon-carbon:chat v-if="activity.icon === 'chat'" :class="['text-lg', getColorClasses(activity.color).icon]" />
            <icon-carbon:document-add v-else-if="activity.icon === 'document-add'" :class="['text-lg', getColorClasses(activity.color).icon]" />
            <icon-carbon:user-follow v-else-if="activity.icon === 'user-follow'" :class="['text-lg', getColorClasses(activity.color).icon]" />
            <icon-carbon:data-base v-else-if="activity.icon === 'data-base'" :class="['text-lg', getColorClasses(activity.color).icon]" />
            <icon-carbon:settings-adjust v-else-if="activity.icon === 'settings-adjust'" :class="['text-lg', getColorClasses(activity.color).icon]" />
            <icon-carbon:thumbs-up v-else-if="activity.icon === 'thumbs-up'" :class="['text-lg', getColorClasses(activity.color).icon]" />
          </div>

          <!-- 内容 -->
          <div class="flex-1 min-w-0">
            <div class="flex items-start justify-between gap-2 mb-1">
              <h4 class="text-sm font-medium text-gray-900 dark:text-white">
                {{ activity.title }}
              </h4>
              <span class="text-xs text-gray-500 dark:text-gray-400 flex-shrink-0">
                {{ activity.time }}
              </span>
            </div>
            <p class="text-sm text-gray-600 dark:text-gray-400 line-clamp-1">
              {{ activity.description }}
            </p>
          </div>
        </div>
      </div>

      <!-- 活动统计 -->
      <div class="mt-4 pt-4 border-t border-gray-200 dark:border-gray-700">
        <div class="grid grid-cols-4 gap-4">
          <div class="text-center">
            <div class="text-2xl font-bold text-gray-900 dark:text-white">156</div>
            <div class="text-xs text-gray-500 dark:text-gray-400 mt-1">今日活动</div>
          </div>
          <div class="text-center">
            <div class="text-2xl font-bold text-gray-900 dark:text-white">1,247</div>
            <div class="text-xs text-gray-500 dark:text-gray-400 mt-1">本周活动</div>
          </div>
          <div class="text-center">
            <div class="text-2xl font-bold text-gray-900 dark:text-white">86</div>
            <div class="text-xs text-gray-500 dark:text-gray-400 mt-1">活跃用户</div>
          </div>
          <div class="text-center">
            <div class="text-2xl font-bold text-gray-900 dark:text-white">12</div>
            <div class="text-xs text-gray-500 dark:text-gray-400 mt-1">系统事件</div>
          </div>
        </div>
      </div>
    </NCard>
  </div>
</template>

<style scoped lang="scss">
.recent-activities {
  .activity-item {
    transition: all 0.2s ease;
    
    &:hover {
      transform: translateX(4px);
    }
  }
}
</style>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue';
import { useNotificationStore } from '@/store/modules/notification';

defineOptions({ name: 'MessageNotification' });

const notificationStore = useNotificationStore();
const saving = ref(false);

const settings = reactive({
  systemAlert: true,
  newMessage: true,
  knowledgeUpdate: false,
  uploadComplete: true,
  mentionMe: true,
  weeklyReport: false,
  emailDigest: false,
  browserPush: false
});

const groups = [
  {
    title: '系统通知',
    icon: 'carbon:notification',
    items: [
      { key: 'systemAlert', label: '系统警报', desc: '系统异常、维护通知等重要提醒' },
      { key: 'uploadComplete', label: '上传完成', desc: '文件上传和知识库构建完成通知' },
    ]
  },
  {
    title: '消息提醒',
    icon: 'carbon:chat',
    items: [
      { key: 'newMessage', label: '新消息提醒', desc: '有人给你发送新消息时通知' },
      { key: 'mentionMe', label: '@我的通知', desc: '有人在讨论中提到你时通知' },
    ]
  },
  {
    title: '内容更新',
    icon: 'carbon:document',
    items: [
      { key: 'knowledgeUpdate', label: '知识库更新', desc: '我参与的知识库有内容更新时通知' },
    ]
  },
  {
    title: '报告与摘要',
    icon: 'carbon:report',
    items: [
      { key: 'weeklyReport', label: '周报', desc: '每周一推送上周使用数据摘要' },
      { key: 'emailDigest', label: '邮件摘要', desc: '每日汇总重要通知通过邮件发送' },
    ]
  },
  {
    title: '推送渠道',
    icon: 'carbon:notification-new',
    items: [
      { key: 'browserPush', label: '浏览器推送', desc: '在浏览器中接收桌面通知（需授权）' },
    ]
  }
];

async function saveSettings() {
  saving.value = true;
  await new Promise(r => setTimeout(r, 800));
  saving.value = false;
  window.$message?.success('通知设置已保存');
}

onMounted(() => {
  notificationStore.fetchNotifications();
});

async function handleMarkAllRead() {
  await notificationStore.markAllAsRead();
  window.$message?.success('已全部标为已读');
}

async function handleMarkRead(id: number) {
  await notificationStore.markAsRead(id);
}

// 操作类型的中文标签
const actionTypeLabel: Record<string, string> = {
  UPDATE_KB: '知识库修改',
  DELETE_KB: '知识库删除',
  UPDATE_DOCUMENT: '文档修改',
  DELETE_DOCUMENT: '文档删除'
};
</script>

<template>
  <div class="notification-module space-y-6">
    <!-- ── 消息中心 ── -->
    <div>
      <div class="flex items-center justify-between mb-3">
        <div class="flex items-center gap-2">
          <h3 class="text-xs font-semibold text-gray-500 dark:text-gray-400 uppercase tracking-wider">消息中心</h3>
          <NTag v-if="notificationStore.unreadCount > 0" type="error" size="small" round>
            {{ notificationStore.unreadCount }}
          </NTag>
        </div>
        <NButton
          v-if="notificationStore.unreadCount > 0"
          size="small"
          text
          type="primary"
          @click="handleMarkAllRead"
        >
          <template #icon><icon-carbon:checkmark-outline /></template>
          全部已读
        </NButton>
      </div>

      <!-- 加载骨架 -->
      <div v-if="notificationStore.loading" class="space-y-2">
        <div
          v-for="i in 4"
          :key="i"
          class="h-16 rounded-xl bg-gray-100 dark:bg-gray-800 animate-pulse"
        />
      </div>

      <!-- 空状态 -->
      <div
        v-else-if="notificationStore.notifications.length === 0"
        class="flex flex-col items-center justify-center py-12 text-gray-400"
      >
        <icon-carbon:notification-off class="text-5xl mb-3 text-gray-300 dark:text-gray-600" />
        <p class="text-sm font-medium">暂无消息通知</p>
        <p class="text-xs mt-1 text-gray-400">当管理员操作您的资源时，您会在此处收到通知</p>
      </div>

      <!-- 通知列表 -->
      <div v-else class="space-y-2">
        <div
          v-for="item in notificationStore.notifications"
          :key="item.id"
          class="flex items-start gap-3 p-3.5 rounded-xl border cursor-pointer transition-colors"
          :class="item.isRead
            ? 'bg-white dark:bg-gray-800 border-gray-200 dark:border-gray-700 hover:bg-gray-50 dark:hover:bg-gray-700/50'
            : 'bg-blue-50/50 dark:bg-blue-900/10 border-blue-200 dark:border-blue-900/30 hover:bg-blue-50 dark:hover:bg-blue-900/20'"
          @click="handleMarkRead(item.id)"
        >
          <!-- 图标 -->
          <div
            class="w-9 h-9 rounded-xl flex items-center justify-center flex-shrink-0 mt-0.5"
            :class="notificationStore.getActionBgClass(item.actionType)"
          >
            <component
              :is="notificationStore.getActionIcon(item.actionType)"
              class="text-base"
              :class="notificationStore.getActionIconClass(item.actionType)"
            />
          </div>

          <!-- 内容 -->
          <div class="flex-1 min-w-0">
            <div class="flex items-start justify-between gap-2">
              <p
                class="text-sm text-gray-800 dark:text-gray-100 leading-snug"
                :class="item.isRead ? 'font-normal' : 'font-semibold'"
              >
                {{ item.message }}
                <span
                  v-if="!item.isRead"
                  class="inline-block w-1.5 h-1.5 bg-blue-500 rounded-full align-middle ml-1 mb-0.5"
                />
              </p>
              <span class="text-xs text-gray-400 flex-shrink-0 mt-0.5">
                {{ notificationStore.formatRelativeTime(item.createdAt) }}
              </span>
            </div>
            <!-- 操作类型标签 -->
            <div class="flex items-center gap-2 mt-1.5">
              <NTag size="tiny" :bordered="false" class="text-9px">
                {{ actionTypeLabel[item.actionType] ?? item.actionType }}
              </NTag>
              <span class="text-xs text-gray-400">
                操作者：{{ item.operatorUsername }}
              </span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- ── 通知偏好设置 ── -->
    <div>
      <div class="flex items-center justify-between mb-3">
        <h3 class="text-xs font-semibold text-gray-500 dark:text-gray-400 uppercase tracking-wider">通知偏好设置</h3>
      </div>
      <div class="space-y-3">
        <div
          v-for="group in groups"
          :key="group.title"
          class="bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 overflow-hidden"
        >
          <div class="flex items-center gap-2 px-4 py-2.5 bg-gray-50 dark:bg-gray-700/50 border-b border-gray-200 dark:border-gray-700">
            <component :is="group.icon" class="text-sm text-gray-500 dark:text-gray-400" />
            <span class="text-sm font-medium text-gray-700 dark:text-gray-300">{{ group.title }}</span>
          </div>
          <div class="divide-y divide-gray-100 dark:divide-gray-700/50">
            <div
              v-for="item in group.items"
              :key="item.key"
              class="flex items-center justify-between px-4 py-3"
            >
              <div>
                <p class="text-sm text-gray-700 dark:text-gray-300">{{ item.label }}</p>
                <p class="text-xs text-gray-400 mt-0.5">{{ item.desc }}</p>
              </div>
              <NSwitch v-model:value="(settings as any)[item.key]" size="small" />
            </div>
          </div>
        </div>
      </div>

      <div class="flex justify-end mt-4">
        <NButton size="small" type="primary" :loading="saving" @click="saveSettings">
          <template #icon><icon-carbon:checkmark-outline /></template>
          保存设置
        </NButton>
      </div>
    </div>
  </div>
</template>

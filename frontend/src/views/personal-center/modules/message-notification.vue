<script setup lang="ts">
import { ref, reactive, computed } from 'vue';

defineOptions({ name: 'MessageNotification' });

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

const msgList = ref([
  { id: 1, type: 'system', title: '系统维护通知', content: '系统将于今晚 22:00 进行例行维护，预计持续 30 分钟', time: '10分钟前', read: false },
  { id: 2, type: 'upload', title: '文件上传完成', content: '「技术文档v2.pdf」已成功上传并完成向量化处理', time: '1小时前', read: false },
  { id: 3, type: 'chat', title: '新消息', content: '管理员：请查看最新的知识库使用规范文档', time: '2小时前', read: true },
  { id: 4, type: 'update', title: '知识库更新', content: '「公共知识库」新增了 3 份文档', time: '昨天', read: true },
  { id: 5, type: 'system', title: '账号安全提醒', content: '检测到新设备登录，如非本人操作请及时修改密码', time: '3天前', read: true },
]);

const unreadCount = computed(() => msgList.value.filter(m => !m.read).length);

function markAllRead() {
  msgList.value.forEach(m => (m.read = true));
  window.$message?.success('已全部标为已读');
}

const typeIconMap: Record<string, string> = {
  system: 'carbon:warning',
  upload: 'carbon:upload',
  chat: 'carbon:chat',
  update: 'carbon:renew'
};
const typeBgMap: Record<string, string> = {
  system: 'bg-blue-50 dark:bg-blue-900/20',
  upload: 'bg-green-50 dark:bg-green-900/20',
  chat: 'bg-purple-50 dark:bg-purple-900/20',
  update: 'bg-orange-50 dark:bg-orange-900/20'
};
const typeIconColorMap: Record<string, string> = {
  system: 'text-blue-500',
  upload: 'text-green-500',
  chat: 'text-purple-500',
  update: 'text-orange-500'
};
</script>

<template>
  <div class="notification-module space-y-6">
    <!-- 消息列表 -->
    <div>
      <div class="flex items-center justify-between mb-3">
        <div class="flex items-center gap-2">
          <h3 class="text-xs font-semibold text-gray-500 dark:text-gray-400 uppercase tracking-wider">消息中心</h3>
          <NTag v-if="unreadCount > 0" type="error" size="small" round>{{ unreadCount }}</NTag>
        </div>
        <NButton size="small" text type="primary" @click="markAllRead">
          <template #icon><icon-carbon:checkmark-outline /></template>
          全部已读
        </NButton>
      </div>
      <div class="space-y-2">
        <div
          v-for="msg in msgList"
          :key="msg.id"
          class="flex items-start gap-3 p-3 rounded-xl border cursor-pointer transition-colors"
          :class="msg.read
            ? 'bg-white dark:bg-gray-800 border-gray-200 dark:border-gray-700'
            : 'bg-blue-50/40 dark:bg-blue-900/10 border-blue-200 dark:border-blue-900/30'"
          @click="msg.read = true"
        >
          <div class="w-8 h-8 rounded-lg flex items-center justify-center flex-shrink-0 mt-0.5" :class="typeBgMap[msg.type]">
            <component :is="typeIconMap[msg.type]" class="text-sm" :class="typeIconColorMap[msg.type]" />
          </div>
          <div class="flex-1 min-w-0">
            <div class="flex items-start justify-between gap-2">
              <p class="text-sm text-gray-800 dark:text-gray-100" :class="msg.read ? 'font-medium' : 'font-bold'">
                {{ msg.title }}
                <span v-if="!msg.read" class="inline-block w-1.5 h-1.5 bg-blue-500 rounded-full align-middle ml-1 mb-0.5" />
              </p>
              <span class="text-xs text-gray-400 flex-shrink-0">{{ msg.time }}</span>
            </div>
            <p class="text-xs text-gray-500 dark:text-gray-400 mt-0.5 line-clamp-2">{{ msg.content }}</p>
          </div>
        </div>
      </div>
    </div>

    <!-- 通知设置 -->
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

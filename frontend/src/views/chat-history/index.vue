<script setup lang="ts">
import type { NScrollbar } from 'naive-ui';
import { NDatePicker, NForm, NFormItem, NSpin, NEmpty, NSpace, NTag, NDivider } from 'naive-ui';
import { VueMarkdownItProvider } from 'vue-markdown-shiki';
import { ModernLayout, PageHeader } from '@/layouts/modern-layout';
import ChatMessage from '../chat/modules/chat-message.vue';

defineOptions({
  name: 'ChatHistory'
});

const scrollbarRef = ref<InstanceType<typeof NScrollbar>>();

const list = ref<Api.Chat.Message[]>([]);
const loading = ref(false);

const store = useAuthStore();

watch(() => [...list.value], scrollToBottom);

function scrollToBottom() {
  setTimeout(() => {
    scrollbarRef.value?.scrollBy({
      top: 999999999999999,
      behavior: 'auto'
    });
  }, 100);
}

const range = ref<[number, number]>([dayjs().subtract(7, 'day').valueOf(), dayjs().add(1, 'day').valueOf()]);
const userId = ref<number>(store.userInfo.id);

const params = computed(() => {
  return {
    userid: userId.value,
    start_date: dayjs(range.value[0]).format('YYYY-MM-DD'),
    end_date: dayjs(range.value[1]).format('YYYY-MM-DD')
  };
});

watchEffect(() => {
  getList();
});

async function getList() {
  if (!params.value.userid) return;
  loading.value = true;
  const { error, data } = await request<Api.Chat.Message[]>({
    url: 'admin/conversation',
    params: params.value
  });
  if (!error) {
    list.value = data;
    scrollToBottom();
  }
  loading.value = false;
}

// 按日期分组消息
const groupedMessages = computed(() => {
  const groups: Record<string, Api.Chat.Message[]> = {};
  list.value.forEach(msg => {
    const date = dayjs(msg.timestamp || new Date()).format('YYYY-MM-DD');
    if (!groups[date]) {
      groups[date] = [];
    }
    groups[date].push(msg);
  });
  return groups;
});

// 获取日期显示文本
function getDateLabel(dateStr: string) {
  const date = dayjs(dateStr);
  const today = dayjs();
  const yesterday = dayjs().subtract(1, 'day');
  
  if (date.isSame(today, 'day')) {
    return '今天';
  } else if (date.isSame(yesterday, 'day')) {
    return '昨天';
  } else if (date.isAfter(today.subtract(7, 'day'))) {
    return date.format('dddd');
  } else {
    return date.format('YYYY年MM月DD日');
  }
}

// 统计信息
const statistics = computed(() => {
  const totalMessages = list.value.length;
  const userMessages = list.value.filter(m => m.role === 'user').length;
  const aiMessages = list.value.filter(m => m.role === 'assistant').length;
  
  return {
    total: totalMessages,
    user: userMessages,
    ai: aiMessages,
    days: Object.keys(groupedMessages.value).length
  };
});
</script>

<template>
  <ModernLayout :show-sidebar="false">
    <PageHeader 
      title="聊天历史" 
      description="查看和管理您的对话记录"
      icon="i-carbon:time"
    >
      <template #actions>
        <NSpace align="center">
          <NForm :model="params" label-placement="left" :show-feedback="false" inline>
            <NFormItem label="用户">
              <TheSelect
                v-model:value="userId"
                url="admin/users/list"
                :params="{ page: 1, size: 999, orgTag: store.userInfo.primaryOrg }"
                key-field="content"
                value-field="userId"
                label-field="username"
                class="clear w-200px!"
                :clearable="false"
              />
            </NFormItem>
            <NFormItem label="时间范围">
              <NDatePicker v-model:value="range" type="daterange" class="clear w-300px" />
            </NFormItem>
          </NForm>
        </NSpace>
      </template>
    </PageHeader>

    <!-- 统计卡片 -->
    <div class="stats-bar">
      <div class="stat-item">
        <div class="stat-icon primary">
          <div class="i-carbon:chat text-xl" />
        </div>
        <div class="stat-content">
          <div class="stat-value">{{ statistics.total }}</div>
          <div class="stat-label">总消息数</div>
        </div>
      </div>
      <div class="stat-item">
        <div class="stat-icon info">
          <div class="i-carbon:user text-xl" />
        </div>
        <div class="stat-content">
          <div class="stat-value">{{ statistics.user }}</div>
          <div class="stat-label">我的提问</div>
        </div>
      </div>
      <div class="stat-item">
        <div class="stat-icon success">
          <div class="i-carbon:bot text-xl" />
        </div>
        <div class="stat-content">
          <div class="stat-value">{{ statistics.ai }}</div>
          <div class="stat-label">AI回复</div>
        </div>
      </div>
      <div class="stat-item">
        <div class="stat-icon warning">
          <div class="i-carbon:calendar text-xl" />
        </div>
        <div class="stat-content">
          <div class="stat-value">{{ statistics.days }}</div>
          <div class="stat-label">活跃天数</div>
        </div>
      </div>
    </div>

    <!-- 聊天历史时间轴 -->
    <div class="history-container">
      <NSpin :show="loading">
        <div v-if="Object.keys(groupedMessages).length > 0" class="timeline-wrapper">
          <div 
            v-for="(messages, date) in groupedMessages" 
            :key="date"
            class="timeline-group"
          >
            <!-- 日期分隔符 -->
            <div class="date-divider">
              <div class="date-line" />
              <div class="date-badge">
                <div class="i-carbon:calendar mr-1" />
                {{ getDateLabel(date) }}
                <span class="date-detail">{{ date }}</span>
              </div>
              <div class="date-line" />
            </div>

            <!-- 消息列表 -->
            <VueMarkdownItProvider>
              <div class="messages-list">
                <ChatMessage 
                  v-for="(item, index) in messages" 
                  :key="`${date}-${index}`" 
                  :msg="item"
                  class="message-item"
                />
              </div>
            </VueMarkdownItProvider>
          </div>
        </div>

        <!-- 空状态 -->
        <div v-else class="empty-container">
          <div class="empty-icon">
            <div class="i-carbon:chat-off text-8xl" />
          </div>
          <h3 class="empty-title">暂无聊天记录</h3>
          <p class="empty-description">选择其他时间范围或用户查看更多记录</p>
        </div>
      </NSpin>
    </div>
  </ModernLayout>
</template>

<style scoped lang="scss">
.stats-bar {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 16px;
  margin-bottom: 24px;
}

.stat-item {
  background: white;
  border-radius: 12px;
  border: 1px solid rgba(0, 0, 0, 0.06);
  padding: 20px;
  display: flex;
  align-items: center;
  gap: 16px;
  transition: all 0.25s ease;

  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
  }

  .dark & {
    background: rgba(255, 255, 255, 0.05);
    border-color: rgba(255, 255, 255, 0.1);
  }
}

.stat-icon {
  width: 48px;
  height: 48px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  flex-shrink: 0;

  &.primary {
    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  }

  &.info {
    background: linear-gradient(135deg, #4facfe 0%, #00f2fe 100%);
  }

  &.success {
    background: linear-gradient(135deg, #52c41a 0%, #7cb305 100%);
  }

  &.warning {
    background: linear-gradient(135deg, #faad14 0%, #fa8c16 100%);
  }
}

.stat-content {
  flex: 1;

  .stat-value {
    font-size: 24px;
    font-weight: 700;
    color: #1f1f1f;
    line-height: 1.2;

    .dark & {
      color: #f1f1f1;
    }
  }

  .stat-label {
    font-size: 12px;
    color: #666;
    margin-top: 2px;

    .dark & {
      color: #999;
    }
  }
}

.history-container {
  background: white;
  border-radius: 16px;
  border: 1px solid rgba(0, 0, 0, 0.06);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
  padding: 24px;
  min-height: 500px;

  .dark & {
    background: rgba(255, 255, 255, 0.05);
    border-color: rgba(255, 255, 255, 0.1);
  }
}

.timeline-wrapper {
  max-height: calc(100vh - 400px);
  overflow-y: auto;
  padding-right: 8px;

  &::-webkit-scrollbar {
    width: 6px;
  }

  &::-webkit-scrollbar-thumb {
    background: rgba(0, 0, 0, 0.1);
    border-radius: 3px;

    &:hover {
      background: rgba(0, 0, 0, 0.2);
    }
  }
}

.timeline-group {
  margin-bottom: 32px;

  &:last-child {
    margin-bottom: 0;
  }
}

.date-divider {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 24px;
  position: sticky;
  top: 0;
  background: white;
  padding: 12px 0;
  z-index: 10;

  .dark & {
    background: rgba(39, 39, 42, 0.95);
  }
}

.date-line {
  flex: 1;
  height: 1px;
  background: linear-gradient(to right, transparent, rgba(0, 0, 0, 0.1), transparent);

  .dark & {
    background: linear-gradient(to right, transparent, rgba(255, 255, 255, 0.1), transparent);
  }
}

.date-badge {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 8px 20px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border-radius: 20px;
  font-size: 14px;
  font-weight: 600;
  white-space: nowrap;
  box-shadow: 0 2px 8px rgba(102, 126, 234, 0.3);

  .date-detail {
    font-size: 12px;
    opacity: 0.8;
    margin-left: 4px;
  }
}

.messages-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.message-item {
  animation: fadeInUp 0.3s ease backwards;
}

@keyframes fadeInUp {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.empty-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 80px 20px;
  text-align: center;
}

.empty-icon {
  color: #d9d9d9;
  margin-bottom: 24px;

  .dark & {
    color: #52525b;
  }
}

.empty-title {
  font-size: 20px;
  font-weight: 600;
  color: #1f1f1f;
  margin: 0 0 12px 0;

  .dark & {
    color: #f1f1f1;
  }
}

.empty-description {
  font-size: 14px;
  color: #666;
  margin: 0;

  .dark & {
    color: #999;
  }
}
</style>

<script setup lang="ts">
import { NButton, NTag, NAvatar, NProgress, NGrid, NGridItem, NSpin, NModal, NSpace } from 'naive-ui';
import { ModernLayout, PageHeader, CardGridContainer } from '@/layouts/modern-layout';

const { userInfo } = storeToRefs(useAuthStore());

const tags = ref<Api.OrgTag.Mine>({
  orgTags: [],
  primaryOrg: '',
  orgTagDetails: []
});

const loading = ref(false);
const getOrgTags = async () => {
  loading.value = true;
  const { error, data } = await request<Api.OrgTag.Mine>({
    url: '/users/org-tags'
  });
  if (!error) {
    tags.value = data;
  }
  loading.value = false;
};

onMounted(() => {
  getOrgTags();
});

const visible = ref(false);
const currentTagId = ref('');
const showModal = (tagId: string) => {
  if (tagId === tags.value.primaryOrg) return;
  visible.value = true;
  currentTagId.value = tagId;
};
const submitLoading = ref(false);
const setPrimaryOrg = async () => {
  submitLoading.value = true;
  const { error } = await request({
    url: '/users/primary-org',
    method: 'PUT',
    data: { primaryOrg: currentTagId.value, userId: userInfo.value.id }
  });
  if (!error) {
    visible.value = false;
    getOrgTags();
  }
  submitLoading.value = false;
};

// 模拟统计数据
const stats = computed(() => [
  { label: '对话次数', value: 1248, icon: 'i-carbon:chat', color: '#667eea', increase: '+12.5%' },
  { label: '知识库数量', value: 36, icon: 'i-carbon:document-multiple', color: '#4facfe', increase: '+8' },
  { label: '活跃天数', value: 89, icon: 'i-carbon:calendar', color: '#52c41a', increase: '+5' },
  { label: '团队协作', value: 15, icon: 'i-carbon:collaborate', color: '#faad14', increase: '+3' }
]);

// 获取标签渐变色
const tagGradients = [
  'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
  'linear-gradient(135deg, #4facfe 0%, #00f2fe 100%)',
  'linear-gradient(135deg, #52c41a 0%, #7cb305 100%)',
  'linear-gradient(135deg, #faad14 0%, #fa8c16 100%)',
  'linear-gradient(135deg, #f5222d 0%, #cf1322 100%)'
];

function getTagGradient(index: number) {
  return tagGradients[index % tagGradients.length];
}
</script>

<template>
  <ModernLayout :show-sidebar="false">
    <NSpin :show="loading">
      <!-- 个人信息头部卡片 -->
      <div class="profile-header">
        <div class="profile-banner">
          <div class="banner-gradient" />
          <div class="profile-info">
            <div class="avatar-wrapper">
              <NAvatar 
                :size="120" 
                round
                class="profile-avatar"
              >
                <div class="i-carbon:user-avatar text-6xl" />
              </NAvatar>
              <div class="edit-avatar-btn">
                <div class="i-carbon:edit text-sm" />
              </div>
            </div>
            <div class="profile-details">
              <h1 class="profile-name">{{ userInfo.username }}</h1>
              <p class="profile-email">{{ userInfo.email || '未设置邮箱' }}</p>
              <NSpace class="mt-4">
                <NTag type="primary" round>
                  <template #icon>
                    <div class="i-carbon:user-role" />
                  </template>
                  普通用户
                </NTag>
                <NTag type="success" round>
                  <template #icon>
                    <div class="i-carbon:checkmark-filled" />
                  </template>
                  已认证
                </NTag>
              </NSpace>
            </div>
          </div>
        </div>
      </div>

      <!-- 数据统计卡片 -->
      <PageHeader 
        title="数据统计" 
        description="个人使用数据总览"
        icon="i-carbon:analytics"
        :show-divider="false"
      />
      
      <CardGridContainer :columns="4" :min-column-width="'240px'" :gap="'20px'" class="mb-8">
        <div
          v-for="(stat, index) in stats"
          :key="index"
          class="stat-card"
        >
          <div class="stat-icon" :style="{ background: stat.color }">
            <div :class="stat.icon" />
          </div>
          <div class="stat-content">
            <div class="stat-value">{{ stat.value }}</div>
            <div class="stat-label">{{ stat.label }}</div>
            <div class="stat-increase" :style="{ color: stat.color }">
              <div class="i-carbon:arrow-up inline-block" />
              {{ stat.increase }}
            </div>
          </div>
        </div>
      </CardGridContainer>

      <!-- 组织标签 -->
      <PageHeader 
        title="我的组织" 
        description="管理您的组织标签和权限"
        icon="i-carbon:tag-group"
        :show-divider="false"
      />

      <CardGridContainer :columns="3" :min-column-width="'280px'" :gap="'20px'">
        <div
          v-for="(tag, index) in tags.orgTagDetails"
          :key="tag.tagId"
          class="org-tag-card"
          :class="{ 'is-primary': tag.tagId === tags.primaryOrg }"
          @click="showModal(tag.tagId)"
        >
          <div class="tag-card-header" :style="{ background: getTagGradient(index) }">
            <div class="tag-icon">
              <div class="i-carbon:folder text-3xl" />
            </div>
            <NTag 
              v-if="tag.tagId === tags.primaryOrg" 
              class="primary-badge"
              type="warning"
              size="small"
              round
            >
              <template #icon>
                <div class="i-carbon:star-filled" />
              </template>
              主标签
            </NTag>
          </div>
          
          <div class="tag-card-body">
            <h3 class="tag-name">{{ tag.name }}</h3>
            <p class="tag-description">{{ tag.description || '暂无描述' }}</p>
            
            <div class="tag-stats">
              <div class="tag-stat-item">
                <div class="i-carbon:user-multiple" />
                <span>45 成员</span>
              </div>
              <div class="tag-stat-item">
                <div class="i-carbon:document" />
                <span>128 文档</span>
              </div>
            </div>
          </div>

          <div class="tag-card-footer">
            <NButton 
              v-if="tag.tagId !== tags.primaryOrg"
              text
              type="primary"
              size="small"
              @click.stop="showModal(tag.tagId)"
            >
              <template #icon>
                <div class="i-carbon:star" />
              </template>
              设为主标签
            </NButton>
            <span v-else class="text-success text-sm">
              <div class="i-carbon:checkmark-filled inline-block" />
              当前主标签
            </span>
          </div>
        </div>
      </CardGridContainer>

      <!-- 空状态 -->
      <div v-if="!tags.orgTagDetails.length" class="empty-state">
        <div class="i-carbon:folder-off text-6xl text-gray-300 mb-4" />
        <p class="text-gray-500">暂无组织标签</p>
      </div>
    </NSpin>

    <!-- 设置主标签对话框 -->
    <NModal
      v-model:show="visible"
      preset="dialog"
      title="设置主标签"
      positive-text="确认"
      negative-text="取消"
      :loading="submitLoading"
      @positive-click="setPrimaryOrg"
      @negative-click="visible = false"
    >
      <div class="modal-content">
        <div class="i-carbon:information text-4xl text-primary mb-4" />
        <p>确定将当前标签设置为主标签吗？</p>
        <p class="text-sm text-gray-500 mt-2">主标签将作为您的默认工作空间</p>
      </div>
    </NModal>
  </ModernLayout>
</template>

<style scoped lang="scss">
.profile-header {
  margin: -24px -24px 32px -24px;
  overflow: hidden;
}

.profile-banner {
  position: relative;
  height: 280px;
  background: white;
  border-radius: 0 0 24px 24px;
  overflow: hidden;

  .dark & {
    background: rgba(255, 255, 255, 0.05);
  }
}

.banner-gradient {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 160px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 50%, #4facfe 100%);
}

.profile-info {
  position: absolute;
  bottom: 32px;
  left: 48px;
  display: flex;
  align-items: flex-end;
  gap: 24px;
  z-index: 1;
}

.avatar-wrapper {
  position: relative;
  
  .profile-avatar {
    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
    color: white;
    border: 5px solid white;
    box-shadow: 0 8px 24px rgba(102, 126, 234, 0.4);

    .dark & {
      border-color: rgba(0, 0, 0, 0.3);
    }
  }

  .edit-avatar-btn {
    position: absolute;
    bottom: 8px;
    right: 8px;
    width: 32px;
    height: 32px;
    border-radius: 50%;
    background: white;
    display: flex;
    align-items: center;
    justify-content: center;
    cursor: pointer;
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
    transition: all 0.25s ease;

    &:hover {
      background: #667eea;
      color: white;
      transform: scale(1.1);
    }
  }
}

.profile-details {
  padding-bottom: 8px;

  .profile-name {
    font-size: 32px;
    font-weight: 700;
    color: #1f1f1f;
    margin: 0 0 8px 0;
    text-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);

    .dark & {
      color: #f1f1f1;
    }
  }

  .profile-email {
    font-size: 15px;
    color: #666;
    margin: 0;

    .dark & {
      color: #999;
    }
  }
}

.stat-card {
  background: white;
  border-radius: 16px;
  border: 1px solid rgba(0, 0, 0, 0.06);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
  padding: 24px;
  display: flex;
  gap: 16px;
  align-items: center;
  transition: all 0.3s cubic-bezier(0.33, 1, 0.68, 1);
  cursor: pointer;

  &:hover {
    transform: translateY(-4px);
    box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12);
  }

  .dark & {
    background: rgba(255, 255, 255, 0.05);
    border-color: rgba(255, 255, 255, 0.1);
  }
}

.stat-icon {
  width: 56px;
  height: 56px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 28px;
  flex-shrink: 0;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

.stat-content {
  flex: 1;

  .stat-value {
    font-size: 28px;
    font-weight: 700;
    color: #1f1f1f;
    line-height: 1.2;
    margin-bottom: 4px;

    .dark & {
      color: #f1f1f1;
    }
  }

  .stat-label {
    font-size: 13px;
    color: #666;
    margin-bottom: 6px;

    .dark & {
      color: #999;
    }
  }

  .stat-increase {
    font-size: 12px;
    font-weight: 600;
    display: flex;
    align-items: center;
    gap: 4px;
  }
}

.org-tag-card {
  background: white;
  border-radius: 16px;
  border: 1px solid rgba(0, 0, 0, 0.06);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
  overflow: hidden;
  transition: all 0.3s cubic-bezier(0.33, 1, 0.68, 1);
  cursor: pointer;

  &:hover {
    transform: translateY(-4px);
    box-shadow: 0 8px 24px rgba(102, 126, 234, 0.15);
  }

  &.is-primary {
    border-color: rgba(102, 126, 234, 0.3);
    box-shadow: 0 4px 16px rgba(102, 126, 234, 0.2);
  }

  .dark & {
    background: rgba(255, 255, 255, 0.05);
    border-color: rgba(255, 255, 255, 0.1);
  }
}

.tag-card-header {
  height: 120px;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  color: white;

  .tag-icon {
    font-size: 48px;
    opacity: 0.9;
  }

  .primary-badge {
    position: absolute;
    top: 12px;
    right: 12px;
  }
}

.tag-card-body {
  padding: 20px;

  .tag-name {
    font-size: 18px;
    font-weight: 600;
    color: #1f1f1f;
    margin: 0 0 8px 0;

    .dark & {
      color: #f1f1f1;
    }
  }

  .tag-description {
    font-size: 13px;
    color: #666;
    margin: 0 0 16px 0;
    line-height: 1.6;
    min-height: 42px;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;

    .dark & {
      color: #999;
    }
  }

  .tag-stats {
    display: flex;
    gap: 16px;
    padding-top: 12px;
    border-top: 1px solid rgba(0, 0, 0, 0.06);

    .dark & {
      border-top-color: rgba(255, 255, 255, 0.1);
    }
  }

  .tag-stat-item {
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 13px;
    color: #666;

    .dark & {
      color: #999;
    }
  }
}

.tag-card-footer {
  padding: 16px 20px;
  border-top: 1px solid rgba(0, 0, 0, 0.04);
  background: rgba(0, 0, 0, 0.01);
  display: flex;
  align-items: center;
  justify-content: center;

  .dark & {
    border-top-color: rgba(255, 255, 255, 0.06);
    background: rgba(255, 255, 255, 0.02);
  }
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 80px 20px;
}

.modal-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 20px;
  text-align: center;
}
</style>

<script setup lang="tsx">
import { NButton, NTag, NAvatar, NSpace, NTooltip, NSpin } from 'naive-ui';
import { ModernLayout, PageHeader, CardGridContainer } from '@/layouts/modern-layout';
import UserSearch from './modules/user-search.vue';
import OrgTagSettingDialog from './modules/org-tag-setting-dialog.vue';

const appStore = useAppStore();

function apiFn(params: Api.User.SearchParams) {
  return request<Api.User.List>({ url: '/admin/users/list', params });
}

const { columns, columnChecks, data, getData, loading, mobilePagination, searchParams, resetSearchParams } = useTable({
  apiFn,
  apiParams: {
    keyword: null,
    orgTag: null,
    status: null
  },
  columns: () => [
    {
      key: 'index',
      title: '序号',
      width: 64
    },
    {
      key: 'username',
      title: '用户名',
      minWidth: 100
    },
    {
      key: 'orgTags',
      title: '标签',
      render: row => (
        <div class="flex flex-wrap gap-2">
          {row.orgTags.map(tag => (
            <NTag key={tag.tagId} type={tag.tagId === row.primaryOrg ? 'primary' : 'default'}>
              {tag.name}
            </NTag>
          ))}
        </div>
      )
    },
    {
      key: 'email',
      title: '邮箱',
      width: 200
    },
    {
      key: 'status',
      title: '是否启用',
      width: 100,
      render: row => <NTag type={row.status ? 'success' : 'warning'}>{row.status ? '已启用' : '已禁用'}</NTag>
    },
    {
      key: 'createTime',
      title: '创建时间',
      width: 200,
      render: row => dayjs(row.createTime).format('YYYY-MM-DD HH:mm:ss')
    },
    {
      key: 'lastLoginTime',
      title: '最后登录时间',
      width: 200,
      render: row => dayjs(row.lastLoginTime).format('YYYY-MM-DD HH:mm:ss')
    },
    {
      key: 'operate',
      title: '操作',
      width: 130,
      render: row => (
        <NButton type="primary" ghost size="small" onClick={() => handleOrgTag(row)}>
          分配组织标签
        </NButton>
      )
    }
  ]
});

const visible = ref(false);
const editingData = ref<Api.User.Item | null>(null);
function handleOrgTag(row: Api.User.Item) {
  editingData.value = row;
  visible.value = true;
}

// 视图模式：卡片视图 vs 表格视图
const viewMode = ref<'card' | 'table'>('card');

// 获取用户状态标签颜色
function getStatusColor(status: boolean) {
  return status ? 'success' : 'warning';
}

// 获取标签类型颜色（基于索引）
const tagColors = ['primary', 'info', 'success', 'warning', 'error'];
function getTagColor(index: number) {
  return tagColors[index % tagColors.length];
}
</script>

<template>
  <ModernLayout :show-sidebar="false">
    <PageHeader 
      title="用户管理" 
      description="管理系统用户及权限配置"
      icon="i-carbon:user-multiple"
    >
      <template #actions>
        <NSpace>
          <UserSearch v-model:model="searchParams" @reset="resetSearchParams" @search="getData" />
          <NTooltip>
            <template #trigger>
              <button 
                class="icon-btn"
                :class="{ active: viewMode === 'card' }"
                @click="viewMode = 'card'"
              >
                <div class="i-carbon:grid text-lg" />
              </button>
            </template>
            卡片视图
          </NTooltip>
          <NTooltip>
            <template #trigger>
              <button 
                class="icon-btn"
                :class="{ active: viewMode === 'table' }"
                @click="viewMode = 'table'"
              >
                <div class="i-carbon:list text-lg" />
              </button>
            </template>
            表格视图
          </NTooltip>
          <button class="icon-btn" @click="getData">
            <div class="i-carbon:renew text-lg" />
          </button>
        </NSpace>
      </template>
    </PageHeader>

    <!-- 卡片视图 -->
    <NSpin :show="loading">
      <CardGridContainer v-if="viewMode === 'card'" :min-column-width="'320px'" :gap="'24px'">
        <div
          v-for="user in data"
          :key="user.id"
          class="user-card"
        >
          <div class="user-card-header">
            <div class="avatar-container">
              <NAvatar 
                :size="64" 
                round
                class="user-avatar"
              >
                <div class="i-carbon:user-avatar text-3xl" />
              </NAvatar>
              <div 
                class="status-indicator"
                :class="user.status ? 'online' : 'offline'"
              />
            </div>
            <div class="user-info">
              <h3 class="user-name">{{ user.username }}</h3>
              <p class="user-email">{{ user.email }}</p>
            </div>
          </div>

          <div class="user-card-body">
            <div class="info-row">
              <span class="info-label">
                <div class="i-carbon:tag inline-block mr-1" />
                组织标签
              </span>
              <div class="flex flex-wrap gap-1">
                <NTag 
                  v-for="(tag, index) in user.orgTags" 
                  :key="tag.tagId"
                  :type="tag.tagId === user.primaryOrg ? 'primary' : getTagColor(index)"
                  size="small"
                  round
                >
                  {{ tag.name }}
                </NTag>
              </div>
            </div>

            <div class="info-row">
              <span class="info-label">
                <div class="i-carbon:checkmark-outline inline-block mr-1" />
                账号状态
              </span>
              <NTag :type="getStatusColor(user.status)" size="small" round>
                {{ user.status ? '已启用' : '已禁用' }}
              </NTag>
            </div>

            <div class="info-row">
              <span class="info-label">
                <div class="i-carbon:time inline-block mr-1" />
                创建时间
              </span>
              <span class="info-value">{{ dayjs(user.createTime).format('YYYY-MM-DD') }}</span>
            </div>

            <div class="info-row">
              <span class="info-label">
                <div class="i-carbon:login inline-block mr-1" />
                最后登录
              </span>
              <span class="info-value">{{ dayjs(user.lastLoginTime).format('YYYY-MM-DD HH:mm') }}</span>
            </div>
          </div>

          <div class="user-card-footer">
            <NButton 
              type="primary" 
              ghost 
              block
              @click="handleOrgTag(user)"
            >
              <template #icon>
                <div class="i-carbon:tag-edit" />
              </template>
              分配组织标签
            </NButton>
          </div>
        </div>
      </CardGridContainer>

      <!-- 表格视图 -->
      <NCard v-else :bordered="false" class="table-card">
        <template #header-extra>
          <TableHeaderOperation v-model:columns="columnChecks" :addable="false" :loading="loading" @refresh="getData" />
        </template>
        <NDataTable
          :columns="columns"
          :data="data"
          size="small"
          :flex-height="!appStore.isMobile"
          :scroll-x="962"
          :loading="loading"
          remote
          :row-key="row => row.id"
          :pagination="mobilePagination"
          class="sm:h-full"
        />
      </NCard>
    </NSpin>

    <OrgTagSettingDialog v-model:visible="visible" :row-data="editingData!" @submitted="getData" />
  </ModernLayout>
</template>

<style scoped lang="scss">
.icon-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 8px;
  border: 1px solid rgba(0, 0, 0, 0.08);
  background: white;
  cursor: pointer;
  transition: all 0.25s ease;

  &:hover {
    background: rgba(102, 126, 234, 0.1);
    border-color: rgba(102, 126, 234, 0.3);
  }

  &.active {
    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
    color: white;
    border-color: transparent;
  }

  &:deep(.dark) & {
    background: rgba(255, 255, 255, 0.1);
    border-color: rgba(255, 255, 255, 0.1);

    &:hover {
      background: rgba(102, 126, 234, 0.2);
    }
  }
}

.user-card {
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
    border-color: rgba(102, 126, 234, 0.2);
  }

  .dark & {
    background: rgba(255, 255, 255, 0.05);
    border-color: rgba(255, 255, 255, 0.1);

    &:hover {
      background: rgba(255, 255, 255, 0.08);
      border-color: rgba(102, 126, 234, 0.3);
    }
  }
}

.user-card-header {
  padding: 24px;
  display: flex;
  gap: 16px;
  align-items: center;
  background: linear-gradient(135deg, rgba(102, 126, 234, 0.05) 0%, rgba(118, 75, 162, 0.05) 100%);
  border-bottom: 1px solid rgba(0, 0, 0, 0.04);

  .dark & {
    background: linear-gradient(135deg, rgba(102, 126, 234, 0.1) 0%, rgba(118, 75, 162, 0.1) 100%);
    border-bottom-color: rgba(255, 255, 255, 0.06);
  }
}

.avatar-container {
  position: relative;

  .user-avatar {
    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
    color: white;
    border: 3px solid white;
    box-shadow: 0 4px 12px rgba(102, 126, 234, 0.3);

    .dark & {
      border-color: rgba(255, 255, 255, 0.1);
    }
  }

  .status-indicator {
    position: absolute;
    bottom: 4px;
    right: 4px;
    width: 14px;
    height: 14px;
    border-radius: 50%;
    border: 2px solid white;
    
    &.online {
      background: #52c41a;
      box-shadow: 0 0 8px rgba(82, 196, 26, 0.6);
    }
    
    &.offline {
      background: #d9d9d9;
    }
  }
}

.user-info {
  flex: 1;
  min-width: 0;

  .user-name {
    font-size: 18px;
    font-weight: 600;
    color: #1f1f1f;
    margin: 0 0 4px 0;
    
    .dark & {
      color: #f1f1f1;
    }
  }

  .user-email {
    font-size: 13px;
    color: #666;
    margin: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;

    .dark & {
      color: #999;
    }
  }
}

.user-card-body {
  padding: 20px 24px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.info-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;

  .info-label {
    display: flex;
    align-items: center;
    font-size: 13px;
    color: #666;
    font-weight: 500;
    min-width: 90px;

    .dark & {
      color: #999;
    }
  }

  .info-value {
    font-size: 13px;
    color: #1f1f1f;
    text-align: right;

    .dark & {
      color: #f1f1f1;
    }
  }
}

.user-card-footer {
  padding: 16px 24px;
  border-top: 1px solid rgba(0, 0, 0, 0.04);
  background: rgba(0, 0, 0, 0.01);

  .dark & {
    border-top-color: rgba(255, 255, 255, 0.06);
    background: rgba(255, 255, 255, 0.02);
  }
}

.table-card {
  background: white;
  border-radius: 16px;
  border: 1px solid rgba(0, 0, 0, 0.06);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);

  .dark & {
    background: rgba(255, 255, 255, 0.05);
    border-color: rgba(255, 255, 255, 0.1);
  }
}
</style>

<script setup lang="tsx">
import { NButton, NPopconfirm, NTag, NTooltip, NSpin, NSpace } from 'naive-ui';
import { ModernLayout, PageHeader, CardGridContainer } from '@/layouts/modern-layout';
import OrgTagOperateDialog from './modules/org-tag-operate-dialog.vue';

const appStore = useAppStore();

const { columns, columnChecks, data, loading, getData } = useTable({
  apiFn: fetchGetOrgTagList,
  columns: () => [
    {
      key: 'name',
      title: '标签名称',
      width: 300,
      ellipsis: {
        tooltip: true
      }
    },
    {
      key: 'description',
      title: '描述',
      minWidth: 200,
      ellipsis: {
        tooltip: true
      }
    },
    {
      key: 'operate',
      title: '操作',
      width: 240,
      render: row => (
        <div class="flex gap-2">
          <NButton type="success" ghost size="small" onClick={() => addChild(row)}>
            新增下级
          </NButton>
          <NButton type="primary" ghost size="small" onClick={() => edit(row)}>
            编辑
          </NButton>
          <NPopconfirm onPositiveClick={() => handleDelete(row.tagId!)}>
            {{
              default: () => '确认删除当前标签吗？',
              trigger: () => (
                <NButton type="error" ghost size="small">
                  删除
                </NButton>
              )
            }}
          </NPopconfirm>
        </div>
      )
    }
  ]
});

const {
  dialogVisible,
  operateType,
  editingData,
  handleAdd,
  handleAddChild,
  handleEdit,
  onDeleted
} = useTableOperate<Api.OrgTag.Item>(getData);

function addChild(row: Api.OrgTag.Item) {
  handleAddChild(row);
}

function edit(row: Api.OrgTag.Item) {
  handleEdit(row);
}

async function handleDelete(tagId: string) {
  const { error } = await request({ url: `/admin/org-tags/${tagId}`, method: 'DELETE' });
  if (!error) {
    onDeleted();
  }
}

// 视图模式：卡片视图 vs 表格视图
const viewMode = ref<'card' | 'table'>('card');

// 标签颜色
const tagColors = [
  { bg: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)', text: '#667eea' },
  { bg: 'linear-gradient(135deg, #4facfe 0%, #00f2fe 100%)', text: '#4facfe' },
  { bg: 'linear-gradient(135deg, #52c41a 0%, #7cb305 100%)', text: '#52c41a' },
  { bg: 'linear-gradient(135deg, #faad14 0%, #fa8c16 100%)', text: '#faad14' },
  { bg: 'linear-gradient(135deg, #f5222d 0%, #cf1322 100%)', text: '#f5222d' },
  { bg: 'linear-gradient(135deg, #722ed1 0%, #531dab 100%)', text: '#722ed1' }
];

function getTagColor(index: number) {
  return tagColors[index % tagColors.length];
}

// 统计信息
const stats = computed(() => {
  const total = data.value.length;
  const withDescription = data.value.filter(t => t.description).length;
  
  return {
    total,
    withDescription,
    withoutDescription: total - withDescription
  };
});
</script>

<template>
  <ModernLayout :show-sidebar="false">
    <PageHeader 
      title="组织标签" 
      description="管理组织架构和标签体系"
      icon="i-carbon:tag-group"
    >
      <template #actions>
        <NSpace>
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
          <NButton type="primary" @click="handleAdd">
            <template #icon>
              <div class="i-carbon:add" />
            </template>
            新建标签
          </NButton>
          <button class="icon-btn" @click="getData">
            <div class="i-carbon:renew text-lg" />
          </button>
        </NSpace>
      </template>
    </PageHeader>

    <!-- 统计卡片 -->
    <div class="stats-bar">
      <div class="stat-card">
        <div class="stat-icon" style="background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);">
          <div class="i-carbon:tag text-2xl" />
        </div>
        <div class="stat-content">
          <div class="stat-value">{{ stats.total }}</div>
          <div class="stat-label">总标签数</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon" style="background: linear-gradient(135deg, #52c41a 0%, #7cb305 100%);">
          <div class="i-carbon:checkmark-outline text-2xl" />
        </div>
        <div class="stat-content">
          <div class="stat-value">{{ stats.withDescription }}</div>
          <div class="stat-label">已完善</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon" style="background: linear-gradient(135deg, #faad14 0%, #fa8c16 100%);">
          <div class="i-carbon:warning-alt text-2xl" />
        </div>
        <div class="stat-content">
          <div class="stat-value">{{ stats.withoutDescription }}</div>
          <div class="stat-label">待完善</div>
        </div>
      </div>
    </div>

    <NSpin :show="loading">
      <!-- 卡片视图 -->
      <CardGridContainer v-if="viewMode === 'card'" :columns="3" :min-column-width="'300px'" :gap="'20px'">
        <div
          v-for="(tag, index) in data"
          :key="tag.tagId"
          class="tag-card"
          :style="{ '--tag-color': getTagColor(index).text }"
        >
          <div class="tag-card-header" :style="{ background: getTagColor(index).bg }">
            <div class="tag-icon-wrapper">
              <div class="i-carbon:folder text-4xl" />
            </div>
            <div class="tag-index">#{{ index + 1 }}</div>
          </div>

          <div class="tag-card-body">
            <h3 class="tag-title">{{ tag.name }}</h3>
            <p class="tag-desc">{{ tag.description || '暂无描述信息' }}</p>
            
            <div class="tag-meta">
              <div class="meta-item">
                <div class="i-carbon:user-multiple" />
                <span>0 成员</span>
              </div>
              <div class="meta-item">
                <div class="i-carbon:tag" />
                <span>0 子标签</span>
              </div>
            </div>
          </div>

          <div class="tag-card-footer">
            <NButton text type="success" size="small" @click="addChild(tag)">
              <template #icon>
                <div class="i-carbon:add-alt" />
              </template>
              新增下级
            </NButton>
            <NButton text type="primary" size="small" @click="edit(tag)">
              <template #icon>
                <div class="i-carbon:edit" />
              </template>
              编辑
            </NButton>
            <NPopconfirm @positive-click="handleDelete(tag.tagId!)">
              <template #trigger>
                <NButton text type="error" size="small">
                  <template #icon>
                    <div class="i-carbon:trash-can" />
                  </template>
                  删除
                </NButton>
              </template>
              确认删除当前标签吗？
            </NPopconfirm>
          </div>
        </div>
      </CardGridContainer>

      <!-- 表格视图 -->
      <div v-else class="table-wrapper">
        <NDataTable
          remote
          :columns="columns"
          :data="data"
          size="small"
          :flex-height="!appStore.isMobile"
          :scroll-x="962"
          :loading="loading"
          :pagination="false"
          :row-key="item => item.tagId"
          class="modern-table"
        />
      </div>

      <!-- 空状态 -->
      <div v-if="!data.length" class="empty-state">
        <div class="i-carbon:tag-group text-8xl text-gray-300 mb-4" />
        <h3 class="text-xl font-semibold mb-2">暂无组织标签</h3>
        <p class="text-gray-500 mb-6">开始创建您的第一个组织标签</p>
        <NButton type="primary" size="large" @click="handleAdd">
          <template #icon>
            <div class="i-carbon:add" />
          </template>
          创建标签
        </NButton>
      </div>
    </NSpin>

    <OrgTagOperateDialog
      v-model:visible="dialogVisible"
      :operate-type="operateType"
      :row-data="editingData!"
      :data="data"
      @submitted="getData"
    />
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

  .dark & {
    background: rgba(255, 255, 255, 0.1);
    border-color: rgba(255, 255, 255, 0.1);

    &:hover {
      background: rgba(102, 126, 234, 0.2);
    }
  }
}

.stats-bar {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 16px;
  margin-bottom: 24px;
}

.stat-card {
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
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
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

.tag-card {
  background: white;
  border-radius: 16px;
  border: 1px solid rgba(0, 0, 0, 0.06);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
  overflow: hidden;
  transition: all 0.3s cubic-bezier(0.33, 1, 0.68, 1);
  cursor: pointer;

  &:hover {
    transform: translateY(-4px);
    box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12);
    border-color: var(--tag-color);
  }

  .dark & {
    background: rgba(255, 255, 255, 0.05);
    border-color: rgba(255, 255, 255, 0.1);
  }
}

.tag-card-header {
  height: 140px;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  color: white;

  .tag-icon-wrapper {
    font-size: 48px;
    opacity: 0.9;
  }

  .tag-index {
    position: absolute;
    top: 12px;
    right: 12px;
    font-size: 14px;
    font-weight: 600;
    padding: 4px 12px;
    background: rgba(255, 255, 255, 0.2);
    backdrop-filter: blur(4px);
    border-radius: 12px;
  }
}

.tag-card-body {
  padding: 20px;

  .tag-title {
    font-size: 18px;
    font-weight: 600;
    color: #1f1f1f;
    margin: 0 0 8px 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;

    .dark & {
      color: #f1f1f1;
    }
  }

  .tag-desc {
    font-size: 13px;
    color: #666;
    margin: 0 0 16px 0;
    line-height: 1.6;
    min-height: 40px;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;

    .dark & {
      color: #999;
    }
  }

  .tag-meta {
    display: flex;
    gap: 16px;
    padding-top: 12px;
    border-top: 1px solid rgba(0, 0, 0, 0.06);

    .dark & {
      border-top-color: rgba(255, 255, 255, 0.1);
    }
  }

  .meta-item {
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
  justify-content: space-between;
  gap: 8px;

  .dark & {
    border-top-color: rgba(255, 255, 255, 0.06);
    background: rgba(255, 255, 255, 0.02);
  }
}

.table-wrapper {
  background: white;
  border-radius: 16px;
  border: 1px solid rgba(0, 0, 0, 0.06);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
  padding: 16px;

  .dark & {
    background: rgba(255, 255, 255, 0.05);
    border-color: rgba(255, 255, 255, 0.1);
  }
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 80px 20px;
  text-align: center;
}
</style>

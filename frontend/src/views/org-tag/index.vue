<script setup lang="tsx">
import { NButton, NPagination, NPopconfirm } from 'naive-ui';
import OrgTagOperateDialog from './modules/org-tag-operate-dialog.vue';

const { columns, columnChecks, data, loading, getData, mobilePagination } = useTable({
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
  // closeDrawer
} = useTableOperate<Api.OrgTag.Item>(getData);

function addChild(row: Api.OrgTag.Item) {
  handleAddChild(row);
}

/** the editing row data */
function edit(row: Api.OrgTag.Item) {
  handleEdit(row);
}

async function handleDelete(tagId: string) {
  const { error } = await request({ url: `/admin/org-tags/${tagId}`, method: 'DELETE' });
  if (!error) {
    onDeleted();
  }
}
</script>

<template>
  <div class="org-tag-page h-full flex flex-col bg-gray-50 dark:bg-gray-900 overflow-y-auto">
    <div class="px-8 py-6 flex-1 min-h-0">
      <!-- 标题 -->
      <h1 class="text-2xl font-bold text-gray-900 dark:text-white mb-6">组织管理</h1>

      <!-- 工具栏 -->
      <div class="flex items-center justify-between mb-4">
        <TableColumnSetting v-model:columns="columnChecks" />
        <div class="flex items-center gap-3">
          <NButton :loading="loading" @click="getData">
            <template #icon>
              <icon-carbon:renew />
            </template>
            刷新
          </NButton>
          <NButton type="primary" @click="handleAdd">
            <template #icon>
              <icon-carbon:add />
            </template>
            新增标签
          </NButton>
        </div>
      </div>

      <!-- 表格 -->
      <div class="bg-white dark:bg-gray-800 rounded-xl shadow-sm border border-gray-100 dark:border-gray-700 overflow-hidden">
        <NDataTable
          remote
          :columns="columns"
          :data="data"
          size="small"
          :scroll-x="962"
          :loading="loading"
          :pagination="false"
          :row-key="item => item.tagId"
        />
        <div class="flex justify-end px-4 py-3 border-t border-gray-100 dark:border-gray-700">
          <NPagination v-bind="mobilePagination" />
        </div>
      </div>
    </div>

    <OrgTagOperateDialog
      v-model:visible="dialogVisible"
      :operate-type="operateType"
      :row-data="editingData!"
      @submitted="getData"
    />
  </div>
</template>

<style scoped></style>

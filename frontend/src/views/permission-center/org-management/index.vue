<script setup lang="tsx">
import { NButton, NInput, NPopconfirm } from 'naive-ui';
import debounce from 'lodash-es/debounce';
import { DEFAULT_PAGE_SIZE } from '@/constants/common';
import ListPagination from '@/components/common/list-pagination.vue';
import OrgTagOperateDialog from '@/views/org-tag/modules/org-tag-operate-dialog.vue';

const { columns, data, loading, getData } = useTable({
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
      },
      render: (row: Api.OrgTag.Item) => (
        <span class="text-gray-500 dark:text-gray-400">
          {row.description || '暂无描述'}
        </span>
      )
    },
    {
      key: 'operate',
      title: '操作',
      width: 220,
      align: 'center' as const,
      titleAlign: 'center' as const,
      render: (row: Api.OrgTag.Item) => (
        <div class="flex items-center justify-center gap-2">
          <NButton text size="small" type="success" onClick={() => addChild(row)}>
            新增下级
          </NButton>
          <NButton text size="small" type="primary" onClick={() => edit(row)}>
            编辑
          </NButton>
          <NPopconfirm onPositiveClick={() => handleDelete(row.tagId!)}>
            {{
              default: () => '确认删除当前标签吗？',
              trigger: () => (
                <NButton text size="small" type="error">
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

/** 搜索关键词 */
const keyword = ref('');

/** 过滤后的数据 */
const filteredData = computed(() => {
  const kw = keyword.value.trim().toLowerCase();
  if (!kw) return data.value;
  return data.value.filter(
    (item: Api.OrgTag.Item) =>
      item.name?.toLowerCase().includes(kw) ||
      item.description?.toLowerCase().includes(kw)
  );
});

/** 分页状态 */
const page = ref(1);
const pageSize = ref(DEFAULT_PAGE_SIZE);

/** 当前页数据 */
const pagedData = computed(() => {
  const start = (page.value - 1) * pageSize.value;
  return filteredData.value.slice(start, start + pageSize.value);
});

const debouncedSearch = debounce(() => {
  page.value = 1;
}, 300);

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
</script>

<template>
  <div class="org-management-page h-full flex flex-col bg-gray-50 dark:bg-gray-900 overflow-y-auto">
    <div class="flex flex-1 flex-col min-h-0 px-8 py-6">
      <!-- 标题 -->
      <h1 class="text-2xl font-bold text-gray-900 dark:text-white mb-6">组织管理</h1>

      <!-- 工具栏 -->
      <div class="flex items-center gap-3 mb-4">
        <NInput
          v-model:value="keyword"
          placeholder="搜索标签名称或描述"
          clearable
          class="max-w-220px"
          @input="debouncedSearch"
          @clear="debouncedSearch"
        >
          <template #prefix>
            <icon-carbon:search class="text-gray-400" />
          </template>
        </NInput>

        <div class="flex-1" />

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

      <!-- 表格 -->
      <div class="flex min-h-0 flex-1 flex-col overflow-hidden rounded-xl border border-gray-100 bg-white shadow-sm dark:border-gray-700 dark:bg-gray-800">
        <NDataTable
          remote
          :columns="columns"
          :data="pagedData"
          size="small"
          :scroll-x="962"
          :loading="loading"
          :pagination="false"
          :row-key="item => item.tagId"
        />
        <ListPagination
          v-model:page="page"
          v-model:page-size="pageSize"
          :item-count="filteredData.length"
          show-quick-jumper
          :page-slot="5"
        />
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

<style scoped lang="scss">
:deep(.n-data-table) {
  .n-data-table-th,
  .n-data-table-td {
    padding-top: 12px;
    padding-bottom: 12px;
  }

  th:first-child,
  td:first-child {
    padding-left: 40px !important;
  }
}
</style>

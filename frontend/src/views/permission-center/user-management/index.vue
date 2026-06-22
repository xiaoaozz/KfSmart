<script setup lang="tsx">
import { NButton, NInput, NSelect, NTag } from 'naive-ui';
import debounce from 'lodash-es/debounce';
import { enableStatusOptions } from '@/constants/common';
import ListPagination from '@/components/common/list-pagination.vue';
import OrgTagSettingDialog from '@/views/user/modules/org-tag-setting-dialog.vue';
import RoleSettingDialog from '@/views/user/modules/role-setting-dialog.vue';

function apiFn(params: Api.User.SearchParams) {
  return request<Api.User.List>({ url: '/admin/users/list', params });
}

const { columns, columnChecks, data, getData, getDataByPage, loading, mobilePagination, searchParams } = useTable({
  apiFn,
  apiParams: {
    keyword: null,
    orgTag: null,
    status: null
  },
  columns: () => [
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
          {row.orgTags?.map(tag => (
            <NTag key={tag.tagId} type={tag.tagId === row.primaryOrg ? 'primary' : 'default'}>
              {tag.name}
            </NTag>
          )) || []}
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
      width: 200,
      align: 'center',
      titleAlign: 'center',
      render: row => (
        <div class="flex items-center justify-center gap-2">
          <NButton text size="small" type="primary" onClick={() => handleOrgTag(row)}>
            分配标签
          </NButton>
          <NButton text size="small" type="info" onClick={() => handleRole(row)}>
            分配角色
          </NButton>
        </div>
      )
    }
  ]
});

const debouncedSearch = debounce(() => getDataByPage(), 300);

// 组织标签弹窗
const orgTagVisible = ref(false);
const editingData = ref<Api.User.Item | null>(null);
function handleOrgTag(row: Api.User.Item) {
  editingData.value = row;
  orgTagVisible.value = true;
}

// 角色分配弹窗
const roleVisible = ref(false);
const roleEditingData = ref<Api.User.Item | null>(null);
function handleRole(row: Api.User.Item) {
  roleEditingData.value = row;
  roleVisible.value = true;
}
</script>

<template>
  <div class="user-management-page h-full flex flex-col bg-gray-50 dark:bg-gray-900 overflow-y-auto">
    <div class="flex flex-1 flex-col min-h-0 px-8 py-6">
      <!-- 标题 -->
      <h1 class="text-2xl font-bold text-gray-900 dark:text-white mb-6">用户管理</h1>

      <!-- 工具栏 -->
      <div class="flex items-center gap-3 mb-4">
        <NInput
          v-model:value="searchParams.keyword"
          placeholder="搜索用户名或邮箱"
          clearable
          class="max-w-220px"
          @input="debouncedSearch"
          @clear="debouncedSearch"
        >
          <template #prefix>
            <icon-carbon:search class="text-gray-400" />
          </template>
        </NInput>

        <OrgTagCascader
          v-model:value="searchParams.orgTag"
          clearable
          placeholder="全部组织标签"
          class="w-200px"
          @update:value="() => getDataByPage()"
        />

        <NSelect
          v-model:value="searchParams.status"
          placeholder="全部状态"
          :options="enableStatusOptions"
          clearable
          class="w-150px"
          @update:value="() => getDataByPage()"
        />

        <div class="flex-1" />

        <TableColumnSetting v-model:columns="columnChecks" />
        <NButton :loading="loading" @click="getData">
          <template #icon>
            <icon-carbon:renew />
          </template>
          刷新
        </NButton>
      </div>

      <!-- 表格 -->
      <div class="flex min-h-0 flex-1 flex-col overflow-hidden rounded-xl border border-gray-100 bg-white shadow-sm dark:border-gray-700 dark:bg-gray-800">
        <NDataTable
          :columns="columns"
          :data="data"
          size="small"
          :scroll-x="962"
          :loading="loading"
          remote
          :row-key="row => row.id"
          :pagination="false"
        />
        <ListPagination v-bind="mobilePagination" />
      </div>
    </div>

    <OrgTagSettingDialog v-model:visible="orgTagVisible" :row-data="editingData!" @submitted="getData" />
    <RoleSettingDialog v-model:visible="roleVisible" :row-data="roleEditingData!" @submitted="getData" />
  </div>
</template>

<style scoped lang="scss">
/* 表格行间距优化 */
:deep(.n-data-table) {
  .n-data-table-th,
  .n-data-table-td {
    padding-top: 12px;
    padding-bottom: 12px;
  }

  // 首列左侧留白与右侧操作列对称
  th:first-child,
  td:first-child {
    padding-left: 40px !important;
  }
}
</style>

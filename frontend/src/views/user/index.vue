<script setup lang="tsx">
import { NButton, NInput, NPagination, NSelect, NTag } from 'naive-ui';
import OrgTagSettingDialog from './modules/org-tag-setting-dialog.vue';
import { enableStatusOptions } from '@/constants/common';
import debounce from 'lodash-es/debounce';

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
      width: 130,
      render: row => (
        <NButton type="primary" ghost size="small" onClick={() => handleOrgTag(row)}>
          分配组织标签
        </NButton>
      )
    }
  ]
});

const debouncedSearch = debounce(() => getDataByPage(), 300);

const visible = ref(false);
const editingData = ref<Api.User.Item | null>(null);
function handleOrgTag(row: Api.User.Item) {
  editingData.value = row;
  visible.value = true;
}

// async function setPrimaryOrgTag(userId: string, primaryOrg: string) {
//   loading.value = true;
//   const { error } = await request({ url: 'users/primary-org', method: 'PUT', data: { primaryOrg, userId } });
//   if (!error) {
//     window.$message?.success('操作成功');
//     await getData();
//   }
//   loading.value = false;
// }
</script>

<template>
  <div class="user-management-page h-full flex flex-col bg-gray-50 dark:bg-gray-900 overflow-y-auto">
    <div class="px-8 py-6 flex-1 min-h-0">
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
      <div class="bg-white dark:bg-gray-800 rounded-xl shadow-sm border border-gray-100 dark:border-gray-700 overflow-hidden">
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
        <div class="flex justify-end px-4 py-3 border-t border-gray-100 dark:border-gray-700">
          <NPagination v-bind="mobilePagination" />
        </div>
      </div>
    </div>

    <OrgTagSettingDialog v-model:visible="visible" :row-data="editingData!" @submitted="getData" />
  </div>
</template>

<style scoped></style>

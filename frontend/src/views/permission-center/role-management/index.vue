<script setup lang="tsx">
import { NButton, NDataTable, NInput, NModal, NPopconfirm, NTag } from 'naive-ui';
import type { DataTableColumns } from 'naive-ui';
import debounce from 'lodash-es/debounce';
import { DEFAULT_PAGE_SIZE } from '@/constants/common';
import { fetchDeleteRole, fetchGetAllRoles } from '@/service/api';
import ListPagination from '@/components/common/list-pagination.vue';
import RoleOperateDialog from './modules/role-operate-dialog.vue';

type OperateType = 'add' | 'edit';
const operateType = ref<OperateType>('add');
const dialogVisible = ref(false);
const editingData = ref<Api.Rbac.Role | null>(null);

const loading = ref(false);
const allData = ref<Api.Rbac.Role[]>([]);

/** 搜索关键词 */
const keyword = ref('');

/** 过滤后的数据 */
const filteredData = computed(() => {
  const kw = keyword.value.trim().toLowerCase();
  if (!kw) return allData.value;
  return allData.value.filter(
    r =>
      r.roleName?.toLowerCase().includes(kw) ||
      r.roleCode?.toLowerCase().includes(kw) ||
      r.description?.toLowerCase().includes(kw)
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

async function getData() {
  loading.value = true;
  const res = await fetchGetAllRoles();
  if (!res.error) {
    allData.value = res.data ?? [];
    const maxPage = Math.ceil(filteredData.value.length / pageSize.value) || 1;
    if (page.value > maxPage) page.value = 1;
  }
  loading.value = false;
}

function handleAdd() {
  operateType.value = 'add';
  editingData.value = null;
  dialogVisible.value = true;
}

function handleEdit(row: Api.Rbac.Role) {
  operateType.value = 'edit';
  editingData.value = { ...row };
  dialogVisible.value = true;
}

async function handleDelete(roleId: number) {
  loading.value = true;
  const res = await fetchDeleteRole(roleId);
  if (!res.error) {
    window.$message?.success('角色删除成功');
    await getData();
  }
  loading.value = false;
}

/** 查看权限弹窗 */
const permViewVisible = ref(false);
const viewingRole = ref<Api.Rbac.Role | null>(null);

function handleViewPermissions(role: Api.Rbac.Role) {
  viewingRole.value = role;
  permViewVisible.value = true;
}

/** 按资源类型分组展示权限 */
const permGroups = computed(() => {
  const perms = viewingRole.value?.permissions ?? [];
  const groups: Record<string, Api.Rbac.Permission[]> = {};
  perms.forEach(p => {
    const key = p.resourceType || '其他';
    if (!groups[key]) groups[key] = [];
    groups[key].push(p);
  });
  return groups;
});

/** 表格列定义 */
const columns: DataTableColumns<Api.Rbac.Role> = [
  {
    key: 'roleName',
    title: '角色名',
    minWidth: 120
  },
  {
    key: 'roleCode',
    title: '角色标识',
    minWidth: 140,
    render: row => (
      <NTag size="small" type="info">
        {row.roleCode}
      </NTag>
    )
  },
  {
    key: 'isSystem',
    title: '是否内置',
    width: 100,
    render: row => (
      <NTag size="small" type={row.isSystem ? 'warning' : 'default'}>
        {row.isSystem ? '是' : '否'}
      </NTag>
    )
  },
  {
    key: 'description',
    title: '角色描述',
    minWidth: 180,
    render: row => (
      <span class="text-gray-500 dark:text-gray-400">
        {row.description || '暂无描述'}
      </span>
    )
  },
  {
    key: 'permissions',
    title: '配置权限',
    width: 140,
    render: row => (
      <span class="text-sm">
        已配置{' '}
        <span class="font-medium text-primary">{row.permissions?.length ?? 0}</span>{' '}
        项权限
      </span>
    )
  },
  {
    key: 'operate',
    title: '操作',
    width: 220,
    align: 'center',
    titleAlign: 'center',
    render: row => (
      <div class="flex items-center justify-center gap-2">
        <NButton text size="small" type="primary" onClick={() => handleViewPermissions(row)}>
          查看权限
        </NButton>
        <NButton text size="small" type="primary" onClick={() => handleEdit(row)}>
          编辑权限
        </NButton>
        {!row.isSystem && (
          <NPopconfirm onPositiveClick={() => handleDelete(row.id)}>
            {{
              trigger: () => (
                <NButton text size="small" type="error">
                  删除
                </NButton>
              ),
              default: () => `确认删除角色「${row.roleName}」吗？删除后已分配该角色的用户将失去相关权限。`
            }}
          </NPopconfirm>
        )}
      </div>
    )
  }
];

onMounted(() => getData());
</script>

<template>
  <div class="role-management-page h-full flex flex-col bg-gray-50 dark:bg-gray-900 overflow-y-auto">
    <div class="flex flex-1 flex-col min-h-0 px-8 py-6">
      <!-- 标题 -->
      <h1 class="text-2xl font-bold text-gray-900 dark:text-white mb-6">角色管理</h1>

      <!-- 工具栏 -->
      <div class="flex items-center gap-3 mb-4">
        <NInput
          v-model:value="keyword"
          placeholder="搜索角色名、角色码或描述"
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
          新建角色
        </NButton>
      </div>

      <!-- 表格 -->
      <div class="flex min-h-0 flex-1 flex-col overflow-hidden rounded-xl border border-gray-100 bg-white shadow-sm dark:border-gray-700 dark:bg-gray-800">
        <NDataTable
          :columns="columns"
          :data="pagedData"
          size="small"
          :scroll-x="900"
          :loading="loading"
          :row-key="row => row.id"
          :pagination="false"
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

    <RoleOperateDialog
      v-model:visible="dialogVisible"
      :operate-type="operateType"
      :row-data="editingData"
      @submitted="getData"
    />

    <!-- 查看权限弹窗 -->
    <NModal
      v-model:show="permViewVisible"
      preset="card"
      :title="`${viewingRole?.roleName ?? ''} - 权限详情`"
      :show-icon="false"
      class="w-560px!"
    >
      <div v-if="viewingRole">
        <!-- 基本信息 -->
        <div class="flex items-center gap-2 mb-4 pb-4 border-b border-gray-100 dark:border-gray-700">
          <NTag size="small" :type="viewingRole.isSystem ? 'warning' : 'default'">
            {{ viewingRole.isSystem ? '内置' : '自定义' }}
          </NTag>
          <NTag size="small" type="info">{{ viewingRole.roleCode }}</NTag>
          <span class="text-sm text-gray-500 dark:text-gray-400 ml-1">{{ viewingRole.description || '暂无描述' }}</span>
        </div>

        <!-- 权限列表 -->
        <div v-if="viewingRole.permissions && viewingRole.permissions.length > 0">
          <div
            v-for="(perms, groupName) in permGroups"
            :key="groupName"
            class="mb-4 last:mb-0"
          >
            <div class="flex items-center gap-2 mb-2">
              <NTag size="small" type="warning">{{ groupName }}</NTag>
              <span class="text-xs text-gray-400">{{ perms.length }} 项</span>
            </div>
            <div class="flex flex-wrap gap-2 pl-2">
              <div
                v-for="perm in perms"
                :key="perm.permCode"
                class="flex flex-col px-3 py-1.5 bg-gray-50 dark:bg-gray-700 rounded-lg border border-gray-100 dark:border-gray-600"
              >
                <span class="text-sm font-medium text-gray-800 dark:text-gray-200">{{ perm.permName }}</span>
                <span class="text-xs text-gray-400 font-mono">{{ perm.permCode }}</span>
              </div>
            </div>
          </div>
        </div>

        <!-- 无权限 -->
        <div v-else class="flex flex-col items-center py-8 text-gray-400">
          <icon-carbon:no-image class="text-4xl mb-2 opacity-40" />
          <p class="text-sm">该角色暂未配置任何权限</p>
        </div>
      </div>

      <template #footer>
        <div class="flex justify-end">
          <NButton @click="permViewVisible = false">关闭</NButton>
        </div>
      </template>
    </NModal>
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

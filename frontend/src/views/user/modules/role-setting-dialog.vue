<script setup lang="ts">
import { fetchGetAllRoles, fetchAssignUserRoles, fetchGetUserRoles } from '@/service/api';
import type { TransferOption } from 'naive-ui';

defineOptions({
  name: 'RoleSettingDialog'
});

const props = defineProps<{
  rowData: Api.User.Item;
}>();

const emit = defineEmits<{ submitted: [] }>();

const visible = defineModel<boolean>('visible', { default: false });
const loading = ref(false);

/** 所有角色（左侧候选） */
const allRoles = ref<Api.Rbac.Role[]>([]);
/** 用户当前拥有的角色编码 */
const selectedRoleCodes = ref<string[]>([]);

/** Transfer 数据源 */
const transferOptions = computed<TransferOption[]>(() =>
  allRoles.value.map(r => ({
    label: r.roleName,
    value: r.roleCode,
    disabled: false
  }))
);

function close() {
  visible.value = false;
}

async function loadData() {
  loading.value = true;
  try {
    const [allRes, userRes] = await Promise.all([
      fetchGetAllRoles(),
      fetchGetUserRoles(Number(props.rowData.userId))
    ]);
    if (!allRes.error) {
      allRoles.value = allRes.data ?? [];
    }
    if (!userRes.error) {
      selectedRoleCodes.value = (userRes.data ?? []).map(r => r.roleCode);
    }
  } finally {
    loading.value = false;
  }
}

async function handleSubmit() {
  loading.value = true;
  const res = await fetchAssignUserRoles(Number(props.rowData.userId), selectedRoleCodes.value);
  if (!res.error) {
    window.$message?.success('角色分配成功');
    close();
    emit('submitted');
  }
  loading.value = false;
}

watch(visible, val => {
  if (val) {
    loadData();
  }
});
</script>

<template>
  <NModal
    v-model:show="visible"
    preset="dialog"
    title="角色权限设置"
    :show-icon="false"
    :mask-closable="false"
    class="w-600px!"
    @positive-click="handleSubmit"
  >
    <NForm label-placement="left" :label-width="100" mt-10>
      <NFormItem label="用户名">
        <NInput :value="rowData.username" readonly />
      </NFormItem>
      <NFormItem label="分配角色">
        <NTransfer
          v-model:value="selectedRoleCodes"
          :options="transferOptions"
          :disabled="loading"
          source-title="可选角色"
          target-title="已分配角色"
          class="w-full"
        />
      </NFormItem>
      <!-- 已分配角色权限预览 -->
      <NFormItem v-if="selectedRoleCodes.length > 0" label="包含权限">
        <div class="flex flex-wrap gap-1">
          <template
            v-for="role in allRoles.filter(r => selectedRoleCodes.includes(r.roleCode))"
            :key="role.roleCode"
          >
            <NTag
              v-for="perm in role.permissions"
              :key="perm.permCode"
              size="small"
              type="info"
            >
              {{ perm.permName }}
            </NTag>
          </template>
        </div>
      </NFormItem>
    </NForm>
    <template #action>
      <NSpace :size="16">
        <NButton @click="close">取消</NButton>
        <NButton type="primary" :loading="loading" @click="handleSubmit">保存</NButton>
      </NSpace>
    </template>
  </NModal>
</template>

<style scoped></style>

<script setup lang="ts">
import type { FormInst, FormRules, TransferOption } from 'naive-ui';
import { fetchGetAllPermissions, fetchCreateRole, fetchUpdateRole } from '@/service/api';

defineOptions({ name: 'RoleOperateDialog' });

type OperateType = 'add' | 'edit';

const props = defineProps<{
  operateType: OperateType;
  rowData?: Api.Rbac.Role | null;
}>();

const emit = defineEmits<{ submitted: [] }>();

const visible = defineModel<boolean>('visible', { default: false });
const loading = ref(false);
const formRef = ref<FormInst | null>(null);

type Model = {
  roleCode: string;
  roleName: string;
  description: string;
  permCodes: string[];
};

const model = ref<Model>(createDefaultModel());

function createDefaultModel(): Model {
  return { roleCode: '', roleName: '', description: '', permCodes: [] };
}

const rules: FormRules = {
  roleCode: [{ required: true, message: '请输入角色编码', trigger: 'blur' }],
  roleName: [{ required: true, message: '请输入角色名称', trigger: 'blur' }]
};

/** 所有权限列表（用于 Transfer 数据源） */
const allPermissions = ref<Api.Rbac.Permission[]>([]);

const transferOptions = computed<TransferOption[]>(() =>
  allPermissions.value.map(p => ({
    label: `${p.permName}（${p.permCode}）`,
    value: p.permCode
  }))
);

/** 按资源类型分组展示，当前选中权限的摘要 */
const selectedPermSummary = computed(() => {
  const selected = allPermissions.value.filter(p => model.value.permCodes.includes(p.permCode));
  const groups: Record<string, string[]> = {};
  selected.forEach(p => {
    const key = p.resourceType || '其他';
    if (!groups[key]) groups[key] = [];
    groups[key].push(p.permName);
  });
  return groups;
});

function close() {
  visible.value = false;
}

async function loadPermissions() {
  const res = await fetchGetAllPermissions();
  if (!res.error) {
    allPermissions.value = res.data ?? [];
  }
}

async function handleSubmit() {
  await formRef.value?.validate();
  loading.value = true;
  try {
    if (props.operateType === 'add') {
      const res = await fetchCreateRole({
        roleCode: model.value.roleCode,
        roleName: model.value.roleName,
        description: model.value.description
      });
      if (!res.error) {
        window.$message?.success('角色创建成功');
        close();
        emit('submitted');
      }
    } else {
      const res = await fetchUpdateRole(props.rowData!.id, {
        roleName: model.value.roleName,
        description: model.value.description,
        permCodes: model.value.permCodes
      });
      if (!res.error) {
        window.$message?.success('角色更新成功');
        close();
        emit('submitted');
      }
    }
  } finally {
    loading.value = false;
  }
}

watch(visible, async val => {
  if (val) {
    await loadPermissions();
    if (props.operateType === 'edit' && props.rowData) {
      model.value = {
        roleCode: props.rowData.roleCode,
        roleName: props.rowData.roleName,
        description: props.rowData.description ?? '',
        permCodes: (props.rowData.permissions ?? []).map(p => p.permCode)
      };
    } else {
      model.value = createDefaultModel();
    }
  }
});
</script>

<template>
  <NModal
    v-model:show="visible"
    preset="dialog"
    :title="operateType === 'add' ? '新建角色' : '编辑角色权限'"
    :show-icon="false"
    :mask-closable="false"
    class="w-660px!"
  >
    <NForm ref="formRef" :model="model" :rules="rules" label-placement="left" :label-width="90" mt-10>
      <NFormItem label="角色编码" path="roleCode">
        <NInput
          v-model:value="model.roleCode"
          placeholder="如: ROLE_KB_MANAGER"
          :disabled="operateType === 'edit'"
        />
      </NFormItem>
      <NFormItem label="角色名称" path="roleName">
        <NInput v-model:value="model.roleName" placeholder="如: 知识库管理员" />
      </NFormItem>
      <NFormItem label="角色描述">
        <NInput
          v-model:value="model.description"
          type="textarea"
          placeholder="请输入角色描述（可选）"
          :autosize="{ minRows: 2, maxRows: 4 }"
        />
      </NFormItem>
      <NFormItem label="权限配置">
        <NTransfer
          v-model:value="model.permCodes"
          :options="transferOptions"
          source-title="可选权限"
          target-title="已分配权限"
          class="w-full"
          style="height: 260px"
        />
      </NFormItem>

      <!-- 权限分组预览 -->
      <NFormItem v-if="Object.keys(selectedPermSummary).length > 0" label="权限预览">
        <div class="flex flex-col gap-2 w-full">
          <div
            v-for="(names, type) in selectedPermSummary"
            :key="type"
            class="flex items-start gap-2"
          >
            <NTag size="small" type="warning" class="shrink-0 mt-0.5">{{ type }}</NTag>
            <div class="flex flex-wrap gap-1">
              <NTag v-for="name in names" :key="name" size="small" type="info">{{ name }}</NTag>
            </div>
          </div>
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

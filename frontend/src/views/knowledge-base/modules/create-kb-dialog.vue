<script setup lang="ts">
import { fetchCreateKnowledgeBase } from '@/service/api/knowledge-base';

defineOptions({
  name: 'CreateKbDialog'
});

const loading = ref(false);
const visible = defineModel<boolean>('visible', { default: false });

const authStore = useAuthStore();

const { formRef, validate, restoreValidation } = useNaiveForm();
const { defaultRequiredRule } = useFormRules();

const model = ref(createDefaultModel());

function createDefaultModel() {
  return {
    name: '',
    description: '',
    orgTag: null as string | null,
    isPublic: false,
    icon: 'folder'
  };
}

const rules = ref<FormRules>({
  name: defaultRequiredRule,
  description: defaultRequiredRule,
  isPublic: defaultRequiredRule
});

function close() {
  visible.value = false;
}

function onUpdate(option: unknown) {
  if (option) {
    const tag = option as Api.OrgTag.Item;
    // 只记录orgTag值，不再混淆标签名称与知识库名称
  }
}

// 图标选项
const iconOptions = [
  { label: '文件夹', value: 'folder' },
  { label: '企业', value: 'enterprise' },
  { label: '产品', value: 'product' },
  { label: '代码', value: 'code' },
  { label: '工具箱', value: 'tool-kit' },
  { label: '数据分析', value: 'chart-line' },
  { label: '目录', value: 'catalog' },
  { label: '书签', value: 'bookmark' }
];

async function handleSubmit() {
  await validate();
  loading.value = true;

  try {
    // 调用独立的知识库创建API
    const { error, data } = await fetchCreateKnowledgeBase({
      name: model.value.name,
      description: model.value.description,
      orgTag: model.value.orgTag,
      isPublic: model.value.isPublic,
      icon: model.value.icon
    });

    if (!error) {
      window.$message?.success('知识库创建成功');
      close();
      emit('submitted');
    }
  } catch (e) {
    console.error('[知识库] 创建失败:', e);
  }

  loading.value = false;
}

watch(visible, () => {
  if (visible.value) {
    model.value = createDefaultModel();
    restoreValidation();
  }
});

const emit = defineEmits<{ submitted: [] }>();
</script>

<template>
  <NModal
    v-model:show="visible"
    preset="dialog"
    title="新建知识库"
    :show-icon="false"
    :mask-closable="false"
    class="w-560px!"
  >
    <NForm ref="formRef" :model="model" :rules="rules" label-placement="left" :label-width="100" mt-10>
      <!-- 知识库名称 -->
      <NFormItem label="知识库名称" path="name">
        <NInput v-model:value="model.name" placeholder="请输入知识库名称，如：研发文档库" maxlength="60" />
      </NFormItem>

      <!-- 知识库图标 -->
      <NFormItem label="图标" path="icon">
        <NSelect v-model:value="model.icon" :options="iconOptions" placeholder="选择知识库图标" />
      </NFormItem>

      <!-- 关联组织标签（可选，用于权限控制） -->
      <NFormItem label="关联组织标签" path="orgTag">
        <template #label>
          <div class="flex items-center gap-1">
            <span>关联组织标签</span>
            <NTooltip>
              <template #trigger>
                <icon-carbon:information class="text-gray-400 text-sm" />
              </template>
              组织标签用于权限控制，决定谁可以访问此知识库中的文档。此为可选设置。
            </NTooltip>
          </div>
        </template>
        <OrgTagCascader v-model:value="model.orgTag" @change="onUpdate" />
      </NFormItem>

      <!-- 知识库描述 -->
      <NFormItem label="知识库描述" path="description">
        <NInput
          v-model:value="model.description"
          type="textarea"
          placeholder="请描述知识库用途，如：存放产品技术文档、设计规范等"
          maxlength="300"
          clearable
          show-count
          :autosize="{ minRows: 3, maxRows: 6 }"
        />
      </NFormItem>

      <!-- 是否公开 -->
      <NFormItem label="是否公开" path="isPublic">
        <NRadioGroup v-model:value="model.isPublic" name="radiogroup">
          <NSpace :size="16">
            <NRadio :value="true">公开 — 所有用户可检索</NRadio>
            <NRadio :value="false">私有 — 仅组织内可见</NRadio>
          </NSpace>
        </NRadioGroup>
      </NFormItem>
    </NForm>

    <template #action>
      <NSpace :size="16">
        <NButton @click="close">取消</NButton>
        <NButton type="primary" :loading="loading" @click="handleSubmit">创建</NButton>
      </NSpace>
    </template>
  </NModal>
</template>

<style scoped></style>
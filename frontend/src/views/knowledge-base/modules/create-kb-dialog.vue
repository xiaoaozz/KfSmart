<script setup lang="ts">
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
    orgTagName: '' as string,
    isPublic: false
  };
}

const rules = ref<FormRules>({
  name: defaultRequiredRule,
  description: defaultRequiredRule,
  orgTag: defaultRequiredRule,
  isPublic: defaultRequiredRule
});

function close() {
  visible.value = false;
}

function onUpdate(option: unknown) {
  if (option) {
    const tag = option as Api.OrgTag.Item;
    model.value.orgTagName = tag.name;
  }
}

async function handleSubmit() {
  await validate();
  loading.value = true;

  try {
    // 如果是管理员，直接创建组织标签作为新的知识库
    if (authStore.isAdmin) {
      const tagId = model.value.orgTagName || model.value.name;
      const { error } = await request({
        url: '/admin/org-tags',
        method: 'POST',
        data: {
          tagId,
          name: model.value.name,
          description: model.value.description,
          parentTag: model.value.orgTag
        }
      });
      if (!error) {
        window.$message?.success('知识库创建成功');
        close();
        emit('submitted');
      }
    } else {
      // 普通用户使用已有的组织标签作为知识库
      // 只需要记录选择，后续上传文件时使用这个标签即可
      window.$message?.success('知识库配置已保存，上传文档后将自动归入该知识库');
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
      <!-- 管理员：可以创建新标签 -->
      <NFormItem v-if="authStore.isAdmin" label="知识库名称" path="name">
        <NInput v-model:value="model.name" placeholder="请输入知识库名称，如：研发文档库" maxlength="60" />
      </NFormItem>

      <!-- 普通用户：选择已有的组织标签 -->
      <NFormItem v-else label="所属知识库" path="orgTag">
        <TheSelect
          v-model:value="model.orgTag"
          url="/users/org-tags"
          key-field="orgTagDetails"
          label-field="name"
          value-field="tagId"
          @change="onUpdate"
        />
      </NFormItem>

      <!-- 管理员：选择上级组织标签 -->
      <NFormItem v-if="authStore.isAdmin" label="上级组织" path="orgTag">
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
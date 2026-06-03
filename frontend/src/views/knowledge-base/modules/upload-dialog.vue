<script setup lang="ts">
import { uploadAccept } from '@/constants/common';
import { fetchGetKnowledgeBases } from '@/service/api/knowledge-base';

defineOptions({
  name: 'UploadDialog'
});

const emit = defineEmits<{
  submitted: [];
}>();

const props = defineProps<{
  activeKbId?: string;
}>();

const loading = ref(false);
const visible = defineModel<boolean>('visible', { default: false });

const authStore = useAuthStore();

const { formRef, validate, restoreValidation } = useNaiveForm();
const { defaultRequiredRule } = useFormRules();

// 知识库列表选项
const knowledgeBases = ref<Api.KnowledgeBase.KnowledgeBaseInfo[]>([]);
const kbLoading = ref(false);

async function loadKnowledgeBases() {
  kbLoading.value = true;
  try {
    const { error, data } = await fetchGetKnowledgeBases();
    if (!error && data) {
      knowledgeBases.value = data;
    }
  } catch (e) {
    console.error('[上传文档] 加载知识库列表失败:', e);
  }
  kbLoading.value = false;
}

const model = ref<Api.KnowledgeBase.Form>(createDefaultModel());

function createDefaultModel(): Api.KnowledgeBase.Form {
  return {
    kbId: props.activeKbId || null,
    orgTag: null,
    orgTagName: '',
    isPublic: false,
    fileList: []
  };
}

const rules = computed<FormRules>(() => ({
  kbId: defaultRequiredRule,
  orgTag: authStore.isAdmin ? defaultRequiredRule : undefined,
  isPublic: defaultRequiredRule,
  fileList: defaultRequiredRule
}));

function close() {
  visible.value = false;
}

const store = useKnowledgeBaseStore();
async function handleSubmit() {
  await validate();
  loading.value = true;
  await store.enqueueUpload(model.value);
  loading.value = false;
  close();
  emit('submitted');
}

watch(visible, (newVal) => {
  if (newVal) {
    model.value = createDefaultModel();
    restoreValidation();
    loadKnowledgeBases();
  }
});

function onUpdate(option: unknown) {
  if (option) model.value.orgTagName = (option as Api.OrgTag.Item).name;
}
</script>

<template>
  <NModal
    v-model:show="visible"
    preset="dialog"
    title="文件上传"
    :show-icon="false"
    :mask-closable="false"
    class="w-500px!"
    @positive-click="handleSubmit"
  >
    <NForm ref="formRef" :model="model" :rules="rules" label-placement="left" :label-width="100" mt-10>
      <!-- 知识库选择 -->
      <NFormItem label="所属知识库" path="kbId">
        <NSelect
          v-model:value="model.kbId"
          :options="knowledgeBases.map(kb => ({ label: kb.name, value: kb.kbId }))"
          placeholder="请选择知识库"
          :loading="kbLoading"
          size="medium"
        />
      </NFormItem>

      <NFormItem v-if="authStore.isAdmin" label="组织标签" path="orgTag">
        <OrgTagCascader v-model:value="model.orgTag" @change="onUpdate" />
      </NFormItem>
      <NFormItem v-else label="组织标签" path="orgTag">
        <TheSelect
          v-model:value="model.orgTag"
          url="/users/org-tags"
          key-field="orgTagDetails"
          label-field="name"
          value-field="tagId"
          @change="onUpdate"
        />
      </NFormItem>

      <NFormItem label="是否公开" path="isPublic">
        <NRadioGroup v-model:value="model.isPublic" name="radiogroup">
          <NSpace :size="16">
            <NRadio :value="true">公开</NRadio>
            <NRadio :value="false">私有</NRadio>
          </NSpace>
        </NRadioGroup>
      </NFormItem>
      <NFormItem label="选择文件" path="fileList">
        <NUpload
          v-model:file-list="model.fileList"
          :accept="uploadAccept"
          :max="1"
          :multiple="false"
          :default-upload="false"
        >
          <NButton>上传文件</NButton>
        </NUpload>
      </NFormItem>
    </NForm>
    <template #action>
      <NSpace :size="16">
        <NButton @click="close">取消</NButton>
        <NButton type="primary" @click="handleSubmit">保存</NButton>
      </NSpace>
    </template>
  </NModal>
</template>

<style scoped></style>
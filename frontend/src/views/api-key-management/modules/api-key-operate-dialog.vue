<script setup lang="ts">
import type { FormRules } from 'naive-ui';

defineOptions({
  name: 'ApiKeyOperateDialog'
});

interface ApiKeyForm {
  id?: number;
  name: string;
  provider: string;
  apiUrl: string;
  apiKey: string;
  modelName: string;
  authType: string;
  temperature: number;
  maxTokens: number;
  topP: number;
  remark: string;
}

const props = defineProps<{
  operateType: 'add' | 'edit';
  rowData?: ApiKeyForm | null;
}>();

const emit = defineEmits<{ submitted: [] }>();

const visible = defineModel<boolean>('visible', { default: false });
const loading = ref(false);
const { formRef, validate, restoreValidation } = useNaiveForm();
const { defaultRequiredRule } = useFormRules();

const title = computed(() => (props.operateType === 'add' ? '新增 API Key 配置' : '编辑 API Key 配置'));

/** 预设提供商选项 */
const providerOptions = [
  { label: 'DeepSeek', value: 'deepseek' },
  { label: 'OpenAI', value: 'openai' },
  { label: '通义千问 (Qwen)', value: 'qwen' },
  { label: '智谱 AI (ChatGLM)', value: 'zhipu' },
  { label: '文心一言 (ERNIE)', value: 'ernie' },
  { label: 'Anthropic (Claude)', value: 'anthropic' },
  { label: '其他', value: 'other' }
];

/**
 * 身份验证方式选项：
 *  - bearer    ：标准 HTTP Bearer Token，适用于智谱AI、本地模型等
 *  - openai    ：OpenAI / DeepSeek / 通义千问等兼容 OpenAI 格式的接口（Bearer Token，路径 /v1/chat/completions）
 *  - anthropic ：Anthropic Claude 系列（x-api-key 请求头，路径 /messages）
 */
const authTypeOptions = [
  {
    label: 'HTTP Bearer（标准）',
    value: 'bearer',
    description: '通用 Bearer Token，适用于智谱AI、本地模型等'
  },
  {
    label: 'OpenAI 兼容格式',
    value: 'openai',
    description: '适用于 OpenAI / DeepSeek / 通义千问等 OpenAI 兼容接口'
  },
  {
    label: 'Anthropic（Claude）',
    value: 'anthropic',
    description: '适用于 Anthropic Claude 系列，使用 x-api-key 请求头'
  }
];

/** 根据提供商预填 API 地址和认证方式 */
const providerUrlMap: Record<string, { url: string; authType: string }> = {
  deepseek: { url: 'https://api.deepseek.com', authType: 'openai' },
  openai: { url: 'https://api.openai.com/v1', authType: 'openai' },
  qwen: { url: 'https://dashscope.aliyuncs.com/compatible-mode/v1', authType: 'openai' },
  zhipu: { url: 'https://open.bigmodel.cn/api/paas/v4', authType: 'bearer' },
  ernie: { url: 'https://aip.baidubce.com/rpc/2.0/ai_custom/v1/wenxinworkshop', authType: 'bearer' },
  anthropic: { url: 'https://api.anthropic.com/v1', authType: 'anthropic' },
  other: { url: '', authType: 'bearer' }
};

function createDefaultModel(): ApiKeyForm {
  return {
    name: '',
    provider: 'deepseek',
    apiUrl: 'https://api.deepseek.com',
    apiKey: '',
    modelName: '',
    authType: 'openai',
    temperature: 0.3,
    maxTokens: 2000,
    topP: 0.9,
    remark: ''
  };
}

const model = ref<ApiKeyForm>(createDefaultModel());

const rules = computed<FormRules>(() => ({
  name: defaultRequiredRule,
  provider: defaultRequiredRule,
  apiUrl: [defaultRequiredRule, { type: 'url', message: '请输入有效的 URL', trigger: 'blur' }],
  apiKey: props.operateType === 'add' ? [defaultRequiredRule] : [],
  modelName: defaultRequiredRule,
  authType: defaultRequiredRule
}));

/** 当前认证方式的说明文字 */
const authTypeDescription = computed(() => {
  const opt = authTypeOptions.find(o => o.value === model.value.authType);
  return opt?.description ?? '';
});

function onProviderChange(val: string) {
  const preset = providerUrlMap[val];
  if (preset) {
    if (preset.url) {
      model.value.apiUrl = preset.url;
    }
    model.value.authType = preset.authType;
  }
}

function close() {
  visible.value = false;
}

async function handleSubmit() {
  await validate();
  loading.value = true;
  try {
    if (props.operateType === 'edit' && model.value.id) {
      const { error } = await request({
        url: `/admin/api-keys/${model.value.id}`,
        method: 'PUT',
        data: model.value
      });
      if (!error) {
        window.$message?.success('更新成功');
        close();
        emit('submitted');
      }
    } else {
      const { error } = await request({
        url: '/admin/api-keys',
        method: 'POST',
        data: model.value
      });
      if (!error) {
        window.$message?.success('创建成功');
        close();
        emit('submitted');
      }
    }
  } finally {
    loading.value = false;
  }
}

watch(visible, () => {
  if (visible.value) {
    restoreValidation();
    if (props.operateType === 'edit' && props.rowData) {
      model.value = { ...props.rowData, apiKey: '' };
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
    :title="title"
    :show-icon="false"
    :mask-closable="false"
    class="w-600px!"
  >
    <NForm
      ref="formRef"
      :model="model"
      :rules="rules"
      label-placement="left"
      :label-width="110"
      mt-10
    >
      <NFormItem label="配置名称" path="name">
        <NInput v-model:value="model.name" placeholder="请输入配置名称，如：DeepSeek生产环境" maxlength="100" />
      </NFormItem>
      <NFormItem label="模型提供商" path="provider">
        <NSelect
          v-model:value="model.provider"
          :options="providerOptions"
          placeholder="请选择提供商"
          @update:value="onProviderChange"
        />
      </NFormItem>
      <NFormItem label="API 地址" path="apiUrl">
        <NInput v-model:value="model.apiUrl" placeholder="请输入 API 请求地址" maxlength="500" />
      </NFormItem>
      <NFormItem label="身份验证方式" path="authType">
        <div class="w-full flex flex-col gap-4px">
          <NSelect
            v-model:value="model.authType"
            :options="authTypeOptions"
            placeholder="请选择身份验证方式"
          />
          <div v-if="authTypeDescription" class="text-12px text-gray-400 pl-2px">
            {{ authTypeDescription }}
          </div>
        </div>
      </NFormItem>
      <NFormItem label="API Key" path="apiKey" :required="operateType === 'add'">
        <NInput
          v-model:value="model.apiKey"
          type="password"
          show-password-on="click"
          :placeholder="operateType === 'edit' ? '留空则保持原 Key 不变' : '请输入 API Key'"
          maxlength="500"
        />
      </NFormItem>
      <NFormItem label="模型名称" path="modelName">
        <NInput v-model:value="model.modelName" placeholder="如：deepseek-chat、gpt-4o、glm-4" maxlength="100" />
      </NFormItem>
      <NDivider title-placement="left">
        <span class="text-12px text-gray-400">生成参数（可选）</span>
      </NDivider>
      <NGrid :cols="2" :x-gap="16" :y-gap="0">
        <NFormItemGi label="温度 (temp)" path="temperature">
          <NInputNumber
            v-model:value="model.temperature"
            :min="0"
            :max="2"
            :step="0.1"
            :precision="1"
            placeholder="0.3"
            class="w-full"
          />
        </NFormItemGi>
        <NFormItemGi label="最大 Tokens" path="maxTokens">
          <NInputNumber
            v-model:value="model.maxTokens"
            :min="1"
            :max="32000"
            :step="100"
            placeholder="2000"
            class="w-full"
          />
        </NFormItemGi>
        <NFormItemGi label="Top-P" path="topP">
          <NInputNumber
            v-model:value="model.topP"
            :min="0"
            :max="1"
            :step="0.1"
            :precision="1"
            placeholder="0.9"
            class="w-full"
          />
        </NFormItemGi>
      </NGrid>
      <NFormItem label="备注" path="remark">
        <NInput
          v-model:value="model.remark"
          type="textarea"
          placeholder="可填写备注说明"
          maxlength="500"
          :autosize="{ minRows: 2, maxRows: 4 }"
        />
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

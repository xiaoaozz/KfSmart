<script setup lang="ts">
import { computed, reactive, ref } from 'vue';
import { useMessage } from 'naive-ui';
import { loginModuleRecord } from '@/constants/app';
import { useAuthStore } from '@/store/modules/auth';
import { useRouterPush } from '@/hooks/common/router';
import { useFormRules, useNaiveForm } from '@/hooks/common/form';
import { $t } from '@/locales';

defineOptions({
  name: 'PwdLogin'
});

const authStore = useAuthStore();
const { toggleLoginModule } = useRouterPush();
const { formRef, validate } = useNaiveForm();
const message = useMessage();

interface FormModel {
  userName: string;
  password: string;
  organization: string;
  rememberMe: boolean;
}

const model: FormModel = reactive({
  userName: 'admin',
  password: 'admin123',
  organization: '',
  rememberMe: false
});

const rules = computed<Record<keyof FormModel, App.Global.FormRule[]>>(() => {
  const { formRules } = useFormRules();

  return {
    userName: formRules.userName,
    password: formRules.pwd,
    organization: [],
    rememberMe: []
  };
});

async function handleSubmit() {
  await validate();
  await authStore.login(model.userName, model.password);
}

// SSO 登录
function handleSSOLogin() {
  message.warning('暂不支持SSO单点登录');
}

type AccountKey = 'admin' | 'user';

interface Account {
  key: AccountKey;
  label: string;
  userName: string;
  password: string;
}

const accounts = computed<Account[]>(() => [
  {
    key: 'admin',
    label: $t('page.login.pwdLogin.admin'),
    userName: 'admin',
    password: 'admin123'
  },
  {
    key: 'user',
    label: $t('page.login.pwdLogin.user'),
    userName: 'testuser',
    password: 'test123'
  }
]);

function handleAccountLogin(account: Account) {
  model.userName = account.userName;
  model.password = account.password;
  handleSubmit();
}
</script>

<template>
  <NForm ref="formRef" :model="model" :rules="rules" size="large" :show-label="false" @keyup.enter="handleSubmit">
    <!-- 账号标签 -->
    <div class="mb-1.5 text-sm font-medium text-gray-700 dark:text-gray-300">
      账号
    </div>
    <!-- 用户名 -->
    <NFormItem path="userName" class="mb-4">
      <NInput 
        v-model:value="model.userName" 
        placeholder="请输入账号 / 邮箱 / 手机号"
        class="login-input h-44px"
        size="large"
      >
        <template #prefix>
          <icon-carbon:user class="text-gray-400 text-lg" />
        </template>
      </NInput>
    </NFormItem>

    <!-- 密码标签 -->
    <div class="mb-1.5 text-sm font-medium text-gray-700 dark:text-gray-300">
      密码
    </div>
    <!-- 密码 -->
    <NFormItem path="password" class="mb-4">
      <NInput
        v-model:value="model.password"
        type="password"
        show-password-on="click"
        placeholder="请输入密码"
        class="login-input h-44px"
        size="large"
      >
        <template #prefix>
          <icon-carbon:locked class="text-gray-400 text-lg" />
        </template>
      </NInput>
    </NFormItem>

    <!-- 组织/公司标签 -->
    <div class="mb-1.5 flex items-center justify-between">
      <span class="text-sm font-medium text-gray-700 dark:text-gray-300">
        组织 / 公司
      </span>
      <span class="text-xs text-blue-500 dark:text-blue-400 flex items-center gap-1">
        <icon-carbon:information class="text-xs" />
        请选择您的企业
      </span>
    </div>
    <!-- 组织/公司选择 -->
    <NFormItem path="organization" class="mb-4">
      <NSelect
        v-model:value="model.organization"
        placeholder="点击选择企业，或输入企业名称"
        class="login-select h-44px"
        size="large"
        :options="[
          { label: 'KnowFlow（默认）', value: 'knowflow' },
          { label: '示例企业A', value: 'company_a' },
          { label: '示例企业B', value: 'company_b' }
        ]"
        filterable
        tag
        :fallback-option="false"
      >
        <template #prefix>
          <icon-carbon:enterprise class="text-gray-400 text-lg" />
        </template>
        <template #empty>
          <div class="text-center py-4">
            <icon-carbon:warning class="text-orange-400 text-2xl mb-2" />
            <div class="text-sm text-gray-500">未找到匹配的企业</div>
            <div class="text-xs text-gray-400 mt-1">您可以直接输入企业名称</div>
          </div>
        </template>
      </NSelect>
    </NFormItem>

    <!-- 记住我和忘记密码 -->
    <div class="flex items-center justify-between mb-5">
      <NCheckbox v-model:checked="model.rememberMe">
        <span class="text-sm text-gray-600 dark:text-gray-400">记住我</span>
      </NCheckbox>
      <div class="flex items-center gap-3">
        <NButton text type="primary" size="small" @click="toggleLoginModule('register')">
          <span class="text-sm">注册账号</span>
        </NButton>
        <NButton text type="primary" size="small" @click="toggleLoginModule('reset-pwd')">
          <span class="text-sm">忘记密码？</span>
        </NButton>
      </div>
    </div>

    <!-- 登录按钮 -->
    <NButton 
      type="primary" 
      size="large" 
      block
      class="login-btn h-44px mb-4"
      :loading="authStore.loginLoading" 
      @click="handleSubmit"
    >
      <span class="text-base font-medium">登录</span>
    </NButton>

    <!-- 分隔线 -->
    <div class="relative flex items-center justify-center my-4">
      <div class="absolute inset-0 flex items-center">
        <div class="w-full border-t border-gray-200 dark:border-gray-700"></div>
      </div>
      <div class="relative bg-white dark:bg-gray-900 px-4">
        <span class="text-xs text-gray-500">或</span>
      </div>
    </div>

    <!-- SSO 单点登录按钮 -->
    <NButton 
      block
      size="large"
      class="sso-btn h-44px mb-4"
      @click="handleSSOLogin"
    >
      <template #icon>
        <icon-carbon:security class="text-blue-500 text-lg" />
      </template>
      <span class="text-sm font-medium">SSO 单点登录</span>
    </NButton>

    <!-- 底部技术标签 - 紧凑版本 -->
    <div class="mt-4 grid grid-cols-3 gap-2 text-xs">
      <div class="flex flex-col items-center gap-1 p-1.5 rounded-lg bg-green-50 dark:bg-green-900/10">
        <icon-carbon-security class="text-green-500 text-base" />
        <span class="text-gray-600 dark:text-gray-400 text-center leading-tight text-xs">Spring<br/>Security</span>
        <span class="px-1.5 py-0.5 rounded bg-green-100 dark:bg-green-900/20 text-green-600" style="font-size: 10px;">安全认证</span>
      </div>
      <div class="flex flex-col items-center gap-1 p-1.5 rounded-lg bg-blue-50 dark:bg-blue-900/10">
        <icon-carbon-user-multiple class="text-blue-500 text-base" />
        <span class="text-gray-600 dark:text-gray-400 text-center leading-tight text-xs">多租户<br/>隔离</span>
        <span class="px-1.5 py-0.5 rounded bg-blue-100 dark:bg-blue-900/20 text-blue-600" style="font-size: 10px;">权限隔离</span>
      </div>
      <div class="flex flex-col items-center gap-1 p-1.5 rounded-lg bg-cyan-50 dark:bg-cyan-900/10">
        <icon-carbon-container-software class="text-cyan-500 text-base" />
        <span class="text-gray-600 dark:text-gray-400 text-center leading-tight text-xs">Docker<br/>部署</span>
        <span class="px-1.5 py-0.5 rounded bg-cyan-100 dark:bg-cyan-900/20 text-cyan-600" style="font-size: 10px;">可扩展</span>
      </div>
    </div>

    <!-- 底部版权和链接 -->
    <div class="mt-4 pt-3 border-t border-gray-200 dark:border-gray-700">
      <div class="text-center text-xs text-gray-500 mb-2">
        © 2025 KnowFlow，保留所有权利。
      </div>
      <div class="flex items-center justify-center gap-2 text-xs text-gray-500 mb-2">
        <a href="#" class="hover:text-primary transition-colors">隐私政策</a>
        <span>|</span>
        <a href="#" class="hover:text-primary transition-colors">服务协议</a>
        <span>|</span>
        <a href="#" class="hover:text-primary transition-colors">联系我们</a>
      </div>
      <div class="flex justify-center">
        <div class="inline-flex items-center gap-1 text-xs text-gray-500">
          <icon-carbon-security class="text-green-500 text-sm" />
          <span>您的数据将全受到保护</span>
        </div>
      </div>
    </div>
  </NForm>
</template>

<style scoped lang="scss">
.login-input, .login-select {
  :deep(.n-input), :deep(.n-base-selection) {
    border-radius: 12px;
    border: 1.5px solid #e5e7eb;
    transition: all 0.3s ease;
    
    &:hover {
      border-color: #3b82f6;
    }
    
    &:focus, &:focus-within {
      border-color: #3b82f6;
      box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.1);
    }
  }
  
  :deep(.n-input__prefix), :deep(.n-base-selection__prefix) {
    margin-right: 12px;
  }
}

.login-btn {
  background: linear-gradient(135deg, #3b82f6 0%, #2563eb 100%);
  border: none;
  border-radius: 12px;
  font-weight: 600;
  transition: all 0.3s ease;
  box-shadow: 0 4px 12px rgba(59, 130, 246, 0.25);
  
  &:hover:not(:disabled) {
    transform: translateY(-2px);
    box-shadow: 0 6px 20px rgba(59, 130, 246, 0.35);
  }

  &:active:not(:disabled) {
    transform: translateY(0);
  }
}

.sso-btn {
  border: 1.5px solid #e5e7eb;
  border-radius: 12px;
  background: white;
  transition: all 0.3s ease;
  
  &:hover {
    border-color: #3b82f6;
    background: #eff6ff;
    transform: translateY(-1px);
    box-shadow: 0 4px 12px rgba(59, 130, 246, 0.1);
  }
  
  &:active {
    transform: translateY(0);
  }
}

:deep(.dark) {
  .login-input, .login-select {
    .n-input, .n-base-selection {
      border-color: #374151;
      background: #1f2937;
      
      &:hover {
        border-color: #3b82f6;
      }
    }
  }
  
  .sso-btn {
    background: #1f2937;
    border-color: #374151;
    
    &:hover {
      background: #111827;
      border-color: #3b82f6;
    }
  }
}
</style>

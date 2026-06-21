<script setup lang="ts">
import type { UploadCustomRequestOptions, UploadFileInfo } from 'naive-ui';
import { fetchUpdateUserAvatar, fetchUpdateUserProfile } from '@/service/api';
import { useUserAvatar } from '@/utils/avatar';

defineOptions({ name: 'PersonalProfile' });

const authStore = useAuthStore();
const { userInfo } = storeToRefs(authStore);
const { avatarText } = useUserAvatar(userInfo);

const editing = ref(false);
const saving = ref(false);
const avatarUploading = ref(false);

const form = ref({
  email: '',
  phone: '',
  bio: '',
});

function syncFormFromStore() {
  form.value = {
    email: userInfo.value.email || '',
    phone: userInfo.value.phone || '',
    bio: userInfo.value.bio || ''
  };
}

async function saveProfile() {
  saving.value = true;
  const { data, error } = await fetchUpdateUserProfile({
    email: form.value.email,
    phone: form.value.phone,
    bio: form.value.bio
  });
  saving.value = false;
  if (error) {
    return;
  }
  Object.assign(userInfo.value, data);
  editing.value = false;
  window.$message?.success('个人资料保存成功');
}

function cancelEdit() {
  syncFormFromStore();
  editing.value = false;
}

function beforeAvatarUpload({ file }: { file: UploadFileInfo }) {
  const rawFile = file.file;
  if (!rawFile) {
    return false;
  }

  if (!['image/jpeg', 'image/png', 'image/webp', 'image/gif'].includes(rawFile.type)) {
    window.$message?.error('仅支持 JPG、PNG、WebP、GIF 格式头像');
    return false;
  }

  if (rawFile.size > 2 * 1024 * 1024) {
    window.$message?.error('头像文件不能超过 2MB');
    return false;
  }

  return true;
}

async function uploadAvatar({ file, onFinish, onError }: UploadCustomRequestOptions) {
  if (!file.file) {
    onError();
    return;
  }

  avatarUploading.value = true;
  const { data, error } = await fetchUpdateUserAvatar(file.file);
  avatarUploading.value = false;

  if (error || !data?.avatar) {
    onError();
    return;
  }

  authStore.setUserAvatar(data.avatar);
  onFinish();
  window.$message?.success('头像已更新');
}

const bioLength = computed(() => form.value.bio.length);

watch(
  userInfo,
  () => {
    if (!editing.value) {
      syncFormFromStore();
    }
  },
  { immediate: true, deep: true }
);
</script>

<template>
  <div class="profile-module mx-auto max-w-5xl space-y-6">
    <!-- 卡片一：头像 + 基础信息表格 -->
    <div class="bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 p-6 flex items-start gap-8">
      <!-- 头像区 -->
      <div class="flex flex-col items-center gap-3 flex-shrink-0">
        <Avatar
          :src="userInfo.avatar || ''"
          :text="avatarText"
          :version="userInfo.avatarVersion"
          :size="160"
          class="shadow-lg text-5xl font-bold"
        />
        <NUpload
          :show-file-list="false"
          accept="image/jpeg,image/png,image/webp,image/gif"
          :before-upload="beforeAvatarUpload"
          :custom-request="uploadAvatar"
        >
          <NButton size="small" secondary :loading="avatarUploading">
            <template #icon><icon-carbon:camera /></template>
            更换头像
          </NButton>
        </NUpload>
      </div>

      <!-- 信息表格 -->
      <div class="flex-1 min-w-0 border border-gray-200 dark:border-gray-700 rounded-lg overflow-hidden">
        <!-- 行1：用户名 + 姓名 -->
        <div class="grid grid-cols-2 border-b border-gray-200 dark:border-gray-700">
          <div class="flex border-r border-gray-200 dark:border-gray-700">
            <div class="w-28 flex-shrink-0 px-5 py-3.5 bg-gray-50 dark:bg-gray-700/50 text-sm text-gray-500 dark:text-gray-400 flex items-center border-r border-gray-200 dark:border-gray-700">
              用户名
            </div>
            <div class="flex-1 px-5 py-3.5 text-sm text-gray-800 dark:text-gray-100 flex items-center">
              {{ userInfo.username }}
            </div>
          </div>
          <div class="flex">
            <div class="w-28 flex-shrink-0 px-5 py-3.5 bg-gray-50 dark:bg-gray-700/50 text-sm text-gray-500 dark:text-gray-400 flex items-center border-r border-gray-200 dark:border-gray-700">
              姓名
            </div>
            <div class="flex-1 px-5 py-3.5 text-sm text-gray-800 dark:text-gray-100 flex items-center">
              {{ userInfo.email || '未设置' }}
            </div>
          </div>
        </div>

        <!-- 行2：部门 -->
        <div class="flex border-b border-gray-200 dark:border-gray-700">
          <div class="w-28 flex-shrink-0 px-5 py-3.5 bg-gray-50 dark:bg-gray-700/50 text-sm text-gray-500 dark:text-gray-400 flex items-center border-r border-gray-200 dark:border-gray-700">
            部门
          </div>
          <div class="flex-1 px-5 py-3.5 text-sm text-gray-800 dark:text-gray-100 flex items-center">
            {{ userInfo.orgTags?.join(' / ') || '未设置' }}
          </div>
        </div>

        <!-- 行3：角色 -->
        <div class="flex">
          <div class="w-28 flex-shrink-0 px-5 py-3.5 bg-gray-50 dark:bg-gray-700/50 text-sm text-gray-500 dark:text-gray-400 flex items-center border-r border-gray-200 dark:border-gray-700">
            角色
          </div>
          <div class="flex-1 px-5 py-3.5 text-sm text-gray-800 dark:text-gray-100 flex items-center">
            {{ userInfo.role === 'ADMIN' ? 'admin' : 'user' }}
          </div>
        </div>
      </div>
    </div>

    <!-- 卡片二：个人资料编辑 -->
    <div class="bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700">
      <!-- 卡片头 -->
      <div class="flex items-center justify-between px-6 py-4 border-b border-gray-200 dark:border-gray-700">
        <h3 class="text-sm font-semibold text-gray-800 dark:text-gray-100">个人资料</h3>
        <NButton v-if="!editing" type="primary" size="small" @click="editing = true">
          <template #icon><icon-carbon:edit /></template>
          编辑资料
        </NButton>
      </div>

      <!-- 表单内容 -->
      <div class="p-6">
        <div class="grid grid-cols-2 gap-x-6 gap-y-4 mb-4">
          <!-- 联系邮箱 -->
          <div>
            <p class="text-sm text-gray-600 dark:text-gray-400 mb-2">联系邮箱</p>
            <NInput
              v-model:value="form.email"
              :disabled="!editing"
              placeholder="请输入联系邮箱"
              size="medium"
            >
              <template #prefix><icon-carbon:email class="text-gray-400" /></template>
            </NInput>
          </div>
          <!-- 联系电话 -->
          <div>
            <p class="text-sm text-gray-600 dark:text-gray-400 mb-2">联系电话</p>
            <NInput
              v-model:value="form.phone"
              :disabled="!editing"
              placeholder="请输入联系电话"
              size="medium"
            >
              <template #prefix><icon-carbon:phone class="text-gray-400" /></template>
            </NInput>
          </div>
        </div>

        <!-- 个人简介 -->
        <div>
          <p class="text-sm text-gray-600 dark:text-gray-400 mb-2">个人简介</p>
          <NInput
            v-model:value="form.bio"
            type="textarea"
            :disabled="!editing"
            :maxlength="500"
            :rows="5"
            placeholder="介绍一下自己..."
            show-count
          />
        </div>

        <!-- 编辑模式下的操作按钮 -->
        <div v-if="editing" class="flex justify-end gap-2 mt-4">
          <NButton size="small" @click="cancelEdit">取消</NButton>
          <NButton size="small" type="primary" :loading="saving" @click="saveProfile">保存修改</NButton>
        </div>
      </div>
    </div>
  </div>
</template>

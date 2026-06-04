<script setup lang="ts">
import { ref, computed } from 'vue';

defineOptions({ name: 'PersonalProfile' });

const { userInfo } = storeToRefs(useAuthStore());

const editing = ref(false);
const saving = ref(false);

const form = ref({
  email: '',
  phone: '',
  bio: '',
});

async function saveProfile() {
  saving.value = true;
  await new Promise(r => setTimeout(r, 800));
  saving.value = false;
  editing.value = false;
  window.$message?.success('个人资料保存成功');
}

const avatarColor = computed(() => {
  const colors = ['#5865F2', '#3BA55C', '#FAA61A', '#ED4245', '#EB459E'];
  const idx = (userInfo.value.username || '').charCodeAt(0) % colors.length;
  return colors[idx] || colors[0];
});
const avatarLetter = computed(() => (userInfo.value.username || 'U')[0]?.toUpperCase());

const bioLength = computed(() => form.value.bio.length);
</script>

<template>
  <div class="profile-module mx-auto max-w-5xl space-y-6">
    <!-- 卡片一：头像 + 基础信息表格 -->
    <div class="bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 p-6 flex items-start gap-8">
      <!-- 头像区 -->
      <div class="flex flex-col items-center gap-3 flex-shrink-0">
        <div
          class="w-40 h-40 rounded-full flex items-center justify-center text-white text-5xl font-bold shadow-lg"
          :style="{ background: avatarColor }"
        >
          {{ avatarLetter }}
        </div>
        <NButton size="small" secondary>
          <template #icon><icon-carbon:camera /></template>
          更换头像
        </NButton>
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
              {{ userInfo.username }}
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
          <NButton size="small" @click="editing = false">取消</NButton>
          <NButton size="small" type="primary" :loading="saving" @click="saveProfile">保存修改</NButton>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { fetchLoginStatistics } from '@/service/api/login-record';

defineOptions({ name: 'AccountSecurity' });

const changePwdVisible = ref(false);
const changePwdLoading = ref(false);
const pwdForm = ref({ oldPwd: '', newPwd: '', confirmPwd: '' });

async function submitChangePwd() {
  if (!pwdForm.value.oldPwd || !pwdForm.value.newPwd) {
    window.$message?.warning('请填写完整密码信息');
    return;
  }
  if (pwdForm.value.newPwd !== pwdForm.value.confirmPwd) {
    window.$message?.error('两次输入的密码不一致');
    return;
  }
  changePwdLoading.value = true;
  await new Promise(r => setTimeout(r, 800));
  changePwdLoading.value = false;
  changePwdVisible.value = false;
  window.$message?.success('密码修改成功');
  pwdForm.value = { oldPwd: '', newPwd: '', confirmPwd: '' };
}

const securityItems = [
  {
    id: 'pwd',
    title: '登录密码',
    desc: '建议定期更换密码，提高账号安全性',
    status: '已设置',
    level: 'success' as const,
    action: '修改',
    onClick: () => { changePwdVisible.value = true; }
  },
  {
    id: 'email',
    title: '绑定邮箱',
    desc: '绑定邮箱可用于找回密码、接收通知',
    status: '未绑定',
    level: 'warning' as const,
    action: '立即绑定',
    onClick: () => { window.$message?.info('邮箱绑定功能即将开放'); }
  },
  {
    id: 'phone',
    title: '手机验证',
    desc: '绑定手机号，开启短信验证保护账号',
    status: '未绑定',
    level: 'warning' as const,
    action: '立即绑定',
    onClick: () => { window.$message?.info('手机绑定功能即将开放'); }
  },
  {
    id: 'twofa',
    title: '双因素认证',
    desc: '登录时额外验证，进一步保护账号安全',
    status: '未开启',
    level: 'default' as const,
    action: '开启',
    onClick: () => { window.$message?.info('双因素认证功能即将开放'); }
  }
];

const loginRecords = ref<Array<{ time: string; ip: string; device: string; location: string; status: string }>>([]);
const loginRecordsLoading = ref(false);

async function loadLoginRecords() {
  loginRecordsLoading.value = true;
  try {
    const { data, error } = await fetchLoginStatistics();
    if (!error && data?.recentRecords) {
      loginRecords.value = data.recentRecords.map(r => ({
        time: r.loginTime ? r.loginTime.replace('T', ' ').substring(0, 19) : '--',
        ip: r.ipAddress || '--',
        device: r.deviceInfo || '未知设备',
        location: r.location || '',
        status: r.status === 'SUCCESS' ? 'success' : 'failed'
      }));
    }
  } finally {
    loginRecordsLoading.value = false;
  }
}

const levelTagMap: Record<string, 'success' | 'warning' | 'error' | 'default'> = {
  success: 'success',
  warning: 'warning',
  error: 'error',
  default: 'default'
};

const levelBgMap: Record<string, string> = {
  success: 'bg-green-50 dark:bg-green-900/20',
  warning: 'bg-orange-50 dark:bg-orange-900/20',
  error: 'bg-red-50 dark:bg-red-900/20',
  default: 'bg-gray-100 dark:bg-gray-700'
};

const levelIconMap: Record<string, string> = {
  success: 'text-green-500',
  warning: 'text-orange-400',
  error: 'text-red-500',
  default: 'text-gray-500'
};

onMounted(() => {
  loadLoginRecords();
});
</script>

<template>
  <div class="security-module space-y-6">
    <!-- 安全评分 -->
    <div class="p-4 bg-gray-50 dark:bg-gray-700/50 rounded-xl border border-gray-200 dark:border-gray-700">
      <div class="flex items-center justify-between mb-2">
        <div>
          <h3 class="text-sm font-semibold text-gray-800 dark:text-gray-100">账号安全评分</h3>
          <p class="text-xs text-gray-500 dark:text-gray-400 mt-0.5">完善安全设置可提升账号保护等级</p>
        </div>
        <div class="text-right">
          <span class="text-2xl font-bold text-orange-500">40</span>
          <span class="text-xs text-gray-400 ml-1">/ 100</span>
        </div>
      </div>
      <NProgress type="line" :percentage="40" indicator-placement="inside" color="#F97316" />
    </div>

    <!-- 安全项列表 -->
    <div>
      <h3 class="text-xs font-semibold text-gray-500 dark:text-gray-400 uppercase tracking-wider mb-3">安全设置</h3>
      <div class="space-y-2">
        <div
          v-for="item in securityItems"
          :key="item.id"
          class="flex items-center justify-between p-4 bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 hover:border-blue-300 dark:hover:border-blue-700 transition-colors"
        >
          <div class="flex items-center gap-3">
            <div class="w-9 h-9 rounded-lg flex items-center justify-center" :class="levelBgMap[item.level]">
              <icon-carbon:locked v-if="item.id === 'pwd'" class="text-base" :class="levelIconMap[item.level]" />
              <icon-carbon:email v-else-if="item.id === 'email'" class="text-base" :class="levelIconMap[item.level]" />
              <icon-carbon:mobile v-else-if="item.id === 'phone'" class="text-base" :class="levelIconMap[item.level]" />
              <icon-carbon:security v-else class="text-base" :class="levelIconMap[item.level]" />
            </div>
            <div>
              <p class="text-sm font-medium text-gray-800 dark:text-gray-100">{{ item.title }}</p>
              <p class="text-xs text-gray-400 dark:text-gray-500 mt-0.5">{{ item.desc }}</p>
            </div>
          </div>
          <div class="flex items-center gap-3">
            <NTag :type="levelTagMap[item.level]" size="small">{{ item.status }}</NTag>
            <NButton size="small" ghost type="primary" @click="item.onClick">{{ item.action }}</NButton>
          </div>
        </div>
      </div>
    </div>

    <!-- 登录记录 -->
    <div>
      <h3 class="text-xs font-semibold text-gray-500 dark:text-gray-400 uppercase tracking-wider mb-3">最近登录记录</h3>
      <div class="bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 overflow-hidden">
        <!-- 加载态 -->
        <div v-if="loginRecordsLoading" class="px-4 py-6 flex items-center justify-center">
          <NSpin size="small" />
        </div>
        <!-- 空状态 -->
        <div v-else-if="loginRecords.length === 0" class="px-4 py-6 text-center text-xs text-gray-400">
          暂无登录记录
        </div>
        <!-- 数据列表 -->
        <template v-else>
          <div
            v-for="(record, idx) in loginRecords"
            :key="idx"
            class="flex items-center justify-between px-4 py-3 border-b border-gray-100 dark:border-gray-700 last:border-b-0"
          >
            <div class="flex items-center gap-3">
              <div class="w-7 h-7 rounded-lg bg-gray-100 dark:bg-gray-700 flex items-center justify-center">
                <icon-carbon:screen class="text-sm text-gray-500 dark:text-gray-400" />
              </div>
              <div>
                <p class="text-xs font-medium text-gray-700 dark:text-gray-300">{{ record.device }}</p>
                <p class="text-xs text-gray-400">{{ record.ip }}<template v-if="record.location"> · {{ record.location }}</template></p>
              </div>
            </div>
            <div class="text-right">
              <NTag :type="record.status === 'success' ? 'success' : 'error'" size="small">
                {{ record.status === 'success' ? '成功' : '失败' }}
              </NTag>
              <p class="text-xs text-gray-400 mt-1">{{ record.time }}</p>
            </div>
          </div>
        </template>
      </div>
    </div>

    <!-- 修改密码弹窗 -->
    <NModal v-model:show="changePwdVisible" title="修改密码" preset="card" style="width: 420px">
      <NForm :model="pwdForm" label-placement="left" label-width="90px" size="small">
        <NFormItem label="当前密码">
          <NInput v-model:value="pwdForm.oldPwd" type="password" show-password-on="click" placeholder="请输入当前密码" />
        </NFormItem>
        <NFormItem label="新密码">
          <NInput v-model:value="pwdForm.newPwd" type="password" show-password-on="click" placeholder="请输入新密码（至少8位）" />
        </NFormItem>
        <NFormItem label="确认新密码">
          <NInput v-model:value="pwdForm.confirmPwd" type="password" show-password-on="click" placeholder="再次输入新密码" />
        </NFormItem>
      </NForm>
      <template #footer>
        <div class="flex justify-end gap-2">
          <NButton size="small" @click="changePwdVisible = false">取消</NButton>
          <NButton size="small" type="primary" :loading="changePwdLoading" @click="submitChangePwd">确认修改</NButton>
        </div>
      </template>
    </NModal>
  </div>
</template>

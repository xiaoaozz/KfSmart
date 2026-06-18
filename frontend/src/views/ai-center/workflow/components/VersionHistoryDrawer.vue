<script setup lang="ts">
import { NButton, NDrawer, NEmpty, NSpin, NTag } from 'naive-ui';
import { ref, watch } from 'vue';
import { fetchWorkflowVersions, fetchRollbackWorkflowVersion } from '@/service/api/workflow';
import type { WorkflowVersion } from '../types/workflow';

const props = defineProps<{ workflowId: string; visible: boolean }>();
const emit = defineEmits<{ 'update:visible': [v: boolean]; rollback: [] }>();

const versions = ref<WorkflowVersion[]>([]);
const loading = ref(false);
const rolling = ref('');

async function loadVersions() {
  if (!props.workflowId) return;
  loading.value = true;
  const { error, data } = await fetchWorkflowVersions(props.workflowId);
  if (!error && data) {
    versions.value = data;
  }
  loading.value = false;
}

watch(() => props.visible, (val) => {
  if (val) loadVersions();
});

async function handleRollback(versionId: string) {
  rolling.value = versionId;
  const { error } = await fetchRollbackWorkflowVersion(props.workflowId, versionId);
  if (!error) {
    window.$message?.success('回滚成功');
    emit('rollback');
    await loadVersions();
  }
  rolling.value = '';
}
</script>

<template>
  <NDrawer :show="visible" :width="480" @update:show="emit('update:visible', $event)">
    <div class="p-5">
      <div class="mb-4 text-lg font-semibold">版本历史</div>
      <NSpin :show="loading">
        <div v-if="versions.length === 0 && !loading" class="py-10">
          <NEmpty description="暂无版本记录" />
        </div>
        <div class="space-y-3">
          <div
            v-for="v in versions"
            :key="v.versionId"
            class="rounded-lg border border-gray-100 p-4 dark:border-gray-700"
          >
            <div class="flex items-center justify-between mb-2">
              <div class="flex items-center gap-2">
                <span class="font-medium">v{{ v.versionNumber }}</span>
                <NTag v-if="v.isActive" type="success" size="small">已激活</NTag>
              </div>
              <span class="text-xs text-gray-400">{{ v.snapshotAt }}</span>
            </div>
            <div class="text-sm text-gray-600 dark:text-gray-400 mb-2">{{ v.name }}</div>
            <div class="text-xs text-gray-400 mb-3">操作人: {{ v.snapshotBy }} · {{ v.changeDescription }}</div>
            <NButton
              size="tiny"
              type="warning"
              ghost
              :loading="rolling === v.versionId"
              @click="handleRollback(v.versionId)"
            >
              回滚到此版本
            </NButton>
          </div>
        </div>
      </NSpin>
    </div>
  </NDrawer>
</template>

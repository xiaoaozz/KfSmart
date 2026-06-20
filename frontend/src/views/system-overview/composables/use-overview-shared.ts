import { type Ref, inject, provide, ref } from 'vue';
import { fetchGetSystemStats, fetchGetSystemStatus } from '@/service/api/system';

interface SystemOverviewSharedState {
  stats: Ref<Api.System.Stats | null>;
  status: Ref<Api.System.Status | null>;
  statsLoading: Ref<boolean>;
  statusLoading: Ref<boolean>;
  loadStats: () => Promise<void>;
  loadStatus: () => Promise<void>;
  loadAll: () => Promise<void>;
}

const SYSTEM_OVERVIEW_SHARED_KEY = Symbol('system-overview-shared');

export function provideSystemOverviewShared() {
  const stats = ref<Api.System.Stats | null>(null);
  const status = ref<Api.System.Status | null>(null);
  const statsLoading = ref(false);
  const statusLoading = ref(false);

  async function loadStats() {
    statsLoading.value = true;
    try {
      const { error, data } = await fetchGetSystemStats();
      if (!error && data) {
        stats.value = data;
      }
    } catch (error) {
      console.error('[SystemOverview] 获取统计数据失败:', error);
    } finally {
      statsLoading.value = false;
    }
  }

  async function loadStatus() {
    statusLoading.value = true;
    try {
      const { error, data } = await fetchGetSystemStatus();
      if (!error && data) {
        status.value = data;
      }
    } catch (error) {
      console.error('[SystemOverview] 获取系统状态失败:', error);
    } finally {
      statusLoading.value = false;
    }
  }

  async function loadAll() {
    await Promise.all([loadStats(), loadStatus()]);
  }

  const sharedState: SystemOverviewSharedState = {
    stats,
    status,
    statsLoading,
    statusLoading,
    loadStats,
    loadStatus,
    loadAll
  };

  provide(SYSTEM_OVERVIEW_SHARED_KEY, sharedState);

  return sharedState;
}

export function useSystemOverviewShared() {
  const sharedState = inject<SystemOverviewSharedState>(SYSTEM_OVERVIEW_SHARED_KEY);
  if (!sharedState) {
    throw new Error('System overview shared state is not provided');
  }
  return sharedState;
}

<script setup lang="ts">
import {
  NButton,
  NDivider,
  NEmpty,
  NInput,
  NModal,
  NScrollbar,
  NSelect,
  NSpin,
  NSwitch,
  NTag
} from 'naive-ui';
import dayjs from 'dayjs';
import { DEFAULT_PAGE_SIZE } from '@/constants/common';
import { fetchMcpTools, fetchPromptTemplates } from '@/service/api/resource';
import {
  fetchDeleteSkill,
  fetchPublishSkill,
  fetchRollbackSkill,
  fetchSaveSkill,
  fetchSkillDetail,
  fetchSkillHistories,
  fetchSkillStats,
  fetchSkillUsages,
  fetchSkills,
  fetchTestSkill,
  fetchToggleSkillStatus
} from '@/service/api/skills';
import FavoriteButton from '@/components/common/favorite-button.vue';
import ListPagination from '@/components/common/list-pagination.vue';

type SelectOption = {
  label: string;
  value: string;
  disabled?: boolean;
};

type PageView = 'list' | 'detail';
type CategoryMode = 'category' | 'status';

const loading = ref(false);
const statsLoading = ref(false);
const saving = ref(false);
const testing = ref(false);
const historyLoading = ref(false);
const usageLoading = ref(false);
const createVisible = ref(false);

const activeView = ref<PageView>('list');
const categoryMode = ref<CategoryMode>('category');
const keyword = ref('');
const activeCategory = ref('全部');
const page = ref(1);
const pageSize = ref(DEFAULT_PAGE_SIZE);
const total = ref(0);

const skillList = ref<Api.AgentCenter.Skill[]>([]);
const selectedSkill = ref<Api.AgentCenter.Skill | null>(null);
const histories = ref<Api.AgentCenter.SkillHistory[]>([]);
const usages = ref<Api.AgentCenter.SkillUsage[]>([]);
const testInputText = ref('{\n  "query": ""\n}');
const testResult = ref<Api.AgentCenter.SkillTestResult | null>(null);

const stats = ref<Api.AgentCenter.SkillStats>({
  total: 0,
  published: 0,
  draft: 0,
  disabled: 0,
  totalCalls: 0,
  avgDurationMs: 0,
  categories: {}
});

const promptOptions = ref<SelectOption[]>([]);
const toolOptions = ref<SelectOption[]>([]);
const createForm = ref({
  name: '',
  category: '通用技能',
  description: ''
});
const form = ref<Partial<Api.AgentCenter.Skill>>(createFormData());

const categoryOptions = computed(() => [
  { name: '全部', count: stats.value.total },
  ...Object.entries(stats.value.categories || {}).map(([name, count]) => ({ name, count }))
]);

const statusOptions = computed(() => [
  { name: '全部', count: stats.value.total },
  { name: '草稿', count: stats.value.draft },
  { name: '已发布', count: stats.value.published },
  { name: '已停用', count: stats.value.disabled }
]);

const currentCategoryOptions = computed(() =>
  categoryMode.value === 'category' ? categoryOptions.value : statusOptions.value
);

const statusSummary = computed(() => {
  const callCount = selectedSkill.value?.callCount ?? 0;
  const successCount = selectedSkill.value?.successCount ?? 0;
  return callCount > 0 ? `${Math.round((successCount / callCount) * 100)}%` : '-';
});

function createFormData(): Partial<Api.AgentCenter.Skill> {
  return {
    name: '',
    category: activeCategory.value !== '全部' && categoryMode.value === 'category' ? activeCategory.value : '通用技能',
    status: '草稿',
    ownerName: '',
    description: '',
    tags: '',
    instruction: '',
    systemPrompt: '',
    inputSchema: '{\n  "query": "string"\n}',
    outputSchema: '{\n  "answer": "string"\n}',
    runtimeConfig: '{\n  "timeoutMs": 15000,\n  "retryCount": 1,\n  "responseMode": "structured"\n}',
    exampleInput: '',
    exampleOutput: '',
    promptRefs: '',
    mcpToolRefs: '',
    version: 'v1'
  };
}

function getPageRecords<T>(data: any): T[] {
  return data?.records || data?.content || data?.data || [];
}

function splitCsv(value?: string | null) {
  if (!value) return [];
  return value
    .split(',')
    .map(item => item.trim())
    .filter(Boolean);
}

function joinCsv(values: string[] | null) {
  return Array.isArray(values) ? values.filter(Boolean).join(',') : '';
}

function formatJson(value: unknown) {
  if (value === null || value === undefined || value === '') return '';
  if (typeof value === 'string') return value;
  try {
    return JSON.stringify(value, null, 2);
  } catch {
    return String(value);
  }
}

function formatTime(value?: string | null) {
  if (!value) return '-';
  return dayjs(value).format('MM-DD HH:mm');
}

function getLabel(options: SelectOption[], value: string) {
  return options.find(item => item.value === value)?.label || value;
}

function getResourceLabels(options: SelectOption[], csv?: string | null) {
  return splitCsv(csv).map(item => getLabel(options, item));
}

function getStatusTagType(status?: string) {
  return ({
    已发布: 'success',
    草稿: 'warning',
    已停用: 'default'
  }[status || ''] || 'default') as 'success' | 'warning' | 'default';
}

function switchCategoryMode(mode: CategoryMode) {
  categoryMode.value = mode;
  activeCategory.value = '全部';
  page.value = 1;
  loadSkills();
}

function handleCategoryClick(category: string) {
  activeCategory.value = category;
  page.value = 1;
  loadSkills();
}

function handleSearch() {
  page.value = 1;
  loadSkills();
}

function updatePromptRefs(value: string[] | null) {
  form.value.promptRefs = joinCsv(value);
}

function updateMcpToolRefs(value: string[] | null) {
  form.value.mcpToolRefs = joinCsv(value);
}

function syncSelectedSkill(nextList: Api.AgentCenter.Skill[]) {
  if (!nextList.length) {
    selectedSkill.value = null;
    return;
  }
  if (!selectedSkill.value?.skillId) {
    selectedSkill.value = nextList[0];
    return;
  }
  const matched = nextList.find(item => item.skillId === selectedSkill.value?.skillId);
  selectedSkill.value = matched || nextList[0];
}

async function loadStats() {
  statsLoading.value = true;
  const { data, error } = await fetchSkillStats();
  statsLoading.value = false;
  if (!error && data) stats.value = data;
}

async function loadResources() {
  const [promptRes, toolRes] = await Promise.all([
    fetchPromptTemplates({ page: 1, size: 100 }),
    fetchMcpTools({ page: 1, size: 100 })
  ]);

  if (!promptRes.error && promptRes.data) {
    promptOptions.value = getPageRecords<Api.AgentCenter.PromptTemplate>(promptRes.data).map(item => ({
      label: item.name,
      value: item.templateId,
      disabled: item.status === '禁用'
    }));
  }

  if (!toolRes.error && toolRes.data) {
    toolOptions.value = getPageRecords<Api.AgentCenter.McpTool>(toolRes.data).map(item => ({
      label: item.name,
      value: item.toolId,
      disabled: item.status !== '在线'
    }));
  }
}

async function loadSkills() {
  loading.value = true;
  const category =
    categoryMode.value === 'category' && activeCategory.value !== '全部' ? activeCategory.value : undefined;
  const status = categoryMode.value === 'status' && activeCategory.value !== '全部' ? activeCategory.value : undefined;
  const { data, error } = await fetchSkills({
    page: page.value,
    size: pageSize.value,
    keyword: keyword.value || undefined,
    category,
    status
  });
  loading.value = false;
  if (!error && data) {
    skillList.value = getPageRecords<Api.AgentCenter.Skill>(data);
    total.value = data.total || data.totalElements || skillList.value.length;
    syncSelectedSkill(skillList.value);
  }
}

async function initialize() {
  await Promise.all([loadStats(), loadResources(), loadSkills()]);
}

function openCreate() {
  createForm.value = {
    name: '',
    category: activeCategory.value !== '全部' && categoryMode.value === 'category' ? activeCategory.value : '通用技能',
    description: ''
  };
  createVisible.value = true;
}

async function createSkill() {
  if (!createForm.value.name.trim()) {
    window.$message?.warning('请输入技能名称');
    return;
  }
  const payload = {
    ...createFormData(),
    name: createForm.value.name.trim(),
    category: createForm.value.category,
    description: createForm.value.description,
    status: '草稿'
  };
  const { error, data } = await fetchSaveSkill(payload);
  if (!error && data) {
    createVisible.value = false;
    window.$message?.success('创建成功');
    await Promise.all([loadStats(), loadSkills()]);
    await openEdit(data.skillId);
  }
}

async function openEdit(skillId: string) {
  const { data, error } = await fetchSkillDetail(skillId);
  if (!error && data) {
    form.value = { ...data };
    selectedSkill.value = { ...data };
    testInputText.value = data.exampleInput || '{\n  "query": ""\n}';
    testResult.value = null;
    activeView.value = 'detail';
    await loadSkillInsights(skillId);
  }
}

function selectSkill(skill: Api.AgentCenter.Skill) {
  selectedSkill.value = skill;
}

async function loadSkillInsights(skillId: string) {
  historyLoading.value = true;
  usageLoading.value = true;
  const [historyRes, usageRes] = await Promise.all([fetchSkillHistories(skillId), fetchSkillUsages(skillId)]);
  historyLoading.value = false;
  usageLoading.value = false;
  histories.value = !historyRes.error && historyRes.data ? historyRes.data : [];
  usages.value = !usageRes.error && usageRes.data ? usageRes.data : [];
}

async function saveSkill() {
  if (!form.value.name?.trim()) {
    window.$message?.warning('请输入技能名称');
    return;
  }
  saving.value = true;
  const { error, data } = await fetchSaveSkill(form.value);
  saving.value = false;
  if (!error && data) {
    form.value = { ...data };
    selectedSkill.value = { ...data };
    window.$message?.success('保存成功');
    await Promise.all([loadStats(), loadSkills(), loadSkillInsights(data.skillId)]);
  }
}

async function publishSkill(skillId: string) {
  const { error } = await fetchPublishSkill(skillId);
  if (!error) {
    window.$message?.success('技能已发布');
    await Promise.all([loadStats(), loadSkills()]);
    if (form.value.skillId === skillId) {
      await openEdit(skillId);
    }
  }
}

async function toggleStatus(skillId: string) {
  const { error } = await fetchToggleSkillStatus(skillId);
  if (!error) {
    window.$message?.success('状态已更新');
    await Promise.all([loadStats(), loadSkills()]);
    if (form.value.skillId === skillId) {
      await openEdit(skillId);
    }
  }
}

function toggleSelectedSkillStatus() {
  if (!selectedSkill.value?.skillId) return;
  toggleStatus(selectedSkill.value.skillId);
}

function deleteSkill(skillId: string) {
  window.$dialog?.warning({
    title: '删除技能',
    content: '删除后，相关引用关系需要手动清理，确认删除吗？',
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      const { error } = await fetchDeleteSkill(skillId);
      if (!error) {
        window.$message?.success('删除成功');
        if (selectedSkill.value?.skillId === skillId) selectedSkill.value = null;
        if (form.value.skillId === skillId) {
          form.value = createFormData();
          activeView.value = 'list';
        }
        await Promise.all([loadStats(), loadSkills()]);
      }
    }
  });
}

async function rollbackHistory(snapshotId: number) {
  if (!form.value.skillId) return;
  window.$dialog?.warning({
    title: '回滚技能版本',
    content: '确认回滚到该历史版本？当前内容会先自动保存为新快照。',
    positiveText: '回滚',
    negativeText: '取消',
    onPositiveClick: async () => {
      const { data, error } = await fetchRollbackSkill(form.value.skillId!, snapshotId);
      if (!error && data) {
        form.value = { ...data };
        selectedSkill.value = { ...data };
        testInputText.value = data.exampleInput || testInputText.value;
        window.$message?.success('回滚成功');
        await Promise.all([loadStats(), loadSkills(), loadSkillInsights(data.skillId)]);
      }
    }
  });
}

async function runSkillTest() {
  if (!form.value.skillId) {
    window.$message?.warning('请先保存技能后再试运行');
    return;
  }
  let payload: Record<string, any> = {};
  try {
    payload = testInputText.value.trim() ? JSON.parse(testInputText.value) : {};
  } catch {
    window.$message?.error('试运行输入不是合法 JSON');
    return;
  }
  testing.value = true;
  const { data, error } = await fetchTestSkill(form.value.skillId, payload);
  testing.value = false;
  if (!error && data) {
    testResult.value = data;
    window.$message?.[data.success ? 'success' : 'warning'](data.message);
  }
}

onMounted(initialize);
</script>

<template>
  <div class="h-full flex flex-col bg-[#f5f7fa] dark:bg-[#101014]">
    <template v-if="activeView === 'list'">
      <div class="h-full flex">
        <div
          class="w-180px flex flex-col flex-shrink-0 border-r border-gray-200 bg-white dark:border-gray-700 dark:bg-[#18181c]"
        >
          <div class="px-4 pb-2 pt-4">
            <h2 class="mb-3 text-sm text-gray-800 font-semibold dark:text-gray-100">Skills 分类</h2>
            <div class="flex rounded-lg bg-gray-100 p-0.5 dark:bg-gray-800">
              <button
                class="flex-1 rounded-md py-1 text-xs font-medium transition-all"
                :class="
                  categoryMode === 'category'
                    ? 'bg-white text-gray-800 shadow-sm dark:bg-[#1e1e22] dark:text-gray-100'
                    : 'text-gray-500 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-300'
                "
                @click="switchCategoryMode('category')"
              >
                按分类
              </button>
              <button
                class="flex-1 rounded-md py-1 text-xs font-medium transition-all"
                :class="
                  categoryMode === 'status'
                    ? 'bg-white text-gray-800 shadow-sm dark:bg-[#1e1e22] dark:text-gray-100'
                    : 'text-gray-500 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-300'
                "
                @click="switchCategoryMode('status')"
              >
                按状态
              </button>
            </div>
          </div>
          <NScrollbar class="flex-1">
            <div class="px-2 pt-1 space-y-0.5">
              <div
                v-for="item in currentCategoryOptions"
                :key="item.name"
                class="cursor-pointer rounded-lg px-3 py-2 text-sm transition-all"
                :class="
                  activeCategory === item.name
                    ? 'bg-primary-50 text-primary-600 font-medium dark:bg-primary-900/20 dark:text-primary-400'
                    : 'text-gray-600 hover:bg-gray-50 dark:text-gray-400 dark:hover:bg-gray-800'
                "
                @click="handleCategoryClick(item.name)"
              >
                <div class="flex items-center justify-between">
                  <div class="flex items-center gap-2">
                    <icon-carbon:catalog v-if="item.name === '全部'" class="text-base" />
                    <icon-carbon:tag v-else class="text-base" />
                    <span>{{ item.name }}</span>
                  </div>
                  <span class="text-xs opacity-60">{{ item.count }}</span>
                </div>
              </div>
            </div>
          </NScrollbar>
        </div>

        <div class="min-w-0 flex flex-col flex-1">
          <div class="border-b border-gray-100 bg-white px-5 py-3 dark:border-gray-700 dark:bg-[#18181c]">
            <div class="flex flex-wrap items-center justify-between gap-3">
              <div class="flex items-center gap-2">
                <NInput
                  v-model:value="keyword"
                  placeholder="搜索技能名称、描述、标签..."
                  clearable
                  class="w-240px"
                  size="small"
                  @keyup.enter="handleSearch"
                >
                  <template #prefix>
                    <icon-carbon:search class="text-gray-400" />
                  </template>
                </NInput>
                <NButton size="small" @click="handleSearch">搜索</NButton>
              </div>
              <div class="flex items-center gap-2">
                <NButton size="small" @click="loadStats">
                  <template #icon><icon-carbon:renew /></template>
                  刷新统计
                </NButton>
                <NButton size="small" type="primary" @click="openCreate">
                  <template #icon><icon-carbon:add /></template>
                  创建技能
                </NButton>
              </div>
            </div>
          </div>

          <NScrollbar class="flex-1">
            <div class="p-4 space-y-4">
              <NSpin :show="loading || statsLoading">
                <div v-if="skillList.length === 0 && !loading" class="py-20">
                  <NEmpty description="暂无技能">
                    <template #extra>
                      <NButton size="small" type="primary" @click="openCreate">创建技能</NButton>
                    </template>
                  </NEmpty>
                </div>
                <div v-else class="grid grid-cols-1 gap-3 xl:grid-cols-2">
                  <div
                    v-for="item in skillList"
                    :key="item.skillId"
                    class="cursor-pointer border rounded-xl bg-white p-4 transition-all dark:bg-[#1e1e22] hover:shadow-md"
                    :class="
                      selectedSkill?.skillId === item.skillId
                        ? 'border-primary-400 shadow-sm ring-1 ring-primary-200 dark:border-primary-500 dark:ring-primary-800'
                        : 'border-gray-200 hover:border-primary-300 dark:border-gray-700 dark:hover:border-primary-600'
                    "
                    @click="selectSkill(item)"
                  >
                    <div class="mb-3 flex items-start justify-between gap-3">
                      <div class="min-w-0 flex items-center gap-2">
                        <div
                          class="h-9 w-9 flex items-center justify-center rounded-lg bg-primary-50 text-primary-500 dark:bg-primary-900/30"
                        >
                          <icon-carbon:skill-level-advanced class="text-lg" />
                        </div>
                        <div class="min-w-0">
                          <h3 class="truncate text-sm text-gray-900 font-semibold dark:text-gray-100">
                            {{ item.name }}
                          </h3>
                          <p class="line-clamp-1 mt-0.5 text-xs text-gray-500 dark:text-gray-400">
                            {{ item.description || '暂无技能描述' }}
                          </p>
                        </div>
                      </div>
                      <div class="flex flex-shrink-0 items-center gap-1">
                        <FavoriteButton
                          type="skill"
                          :target-id="item.skillId"
                          :title="item.name"
                          :description="item.description"
                          :meta="item.category || item.status || ''"
                        />
                        <NTag :type="getStatusTagType(item.status)" size="small" :bordered="false">
                          {{ item.status || '草稿' }}
                        </NTag>
                      </div>
                    </div>

                    <div class="grid grid-cols-3 gap-2 text-xs">
                      <div class="rounded-lg bg-gray-50 px-3 py-2 text-center dark:bg-[#18181c]">
                        <div class="text-gray-800 font-semibold dark:text-gray-200">
                          {{ item.category || '通用技能' }}
                        </div>
                        <div class="mt-1 text-gray-400">分类</div>
                      </div>
                      <div class="rounded-lg bg-gray-50 px-3 py-2 text-center dark:bg-[#18181c]">
                        <div class="text-gray-800 font-semibold dark:text-gray-200">
                          {{ splitCsv(item.promptRefs).length }}
                        </div>
                        <div class="mt-1 text-gray-400">Prompt</div>
                      </div>
                      <div class="rounded-lg bg-gray-50 px-3 py-2 text-center dark:bg-[#18181c]">
                        <div class="text-gray-800 font-semibold dark:text-gray-200">
                          {{ splitCsv(item.mcpToolRefs).length }}
                        </div>
                        <div class="mt-1 text-gray-400">工具</div>
                      </div>
                    </div>

                    <div class="mt-3 flex flex-wrap gap-1.5">
                      <NTag
                        v-for="tag in splitCsv(item.tags).slice(0, 3)"
                        :key="tag"
                        size="small"
                        :bordered="false"
                        round
                      >
                        {{ tag }}
                      </NTag>
                      <span v-if="!splitCsv(item.tags).length" class="text-xs text-gray-400">未打标签</span>
                    </div>

                    <div class="mt-3 flex items-center justify-between text-xs text-gray-400 dark:text-gray-500">
                      <div class="flex items-center gap-3">
                        <span class="flex items-center gap-1">
                          <icon-carbon:chart-line class="text-sm" />
                          {{ item.callCount?.toLocaleString() ?? 0 }} 次
                        </span>
                        <span class="flex items-center gap-1">
                          <icon-carbon:time class="text-sm" />
                          {{ item.avgDurationMs ?? 0 }} ms
                        </span>
                      </div>
                      <span>{{ formatTime(item.updatedAt) }}</span>
                    </div>
                  </div>
                </div>
              </NSpin>
            </div>
          </NScrollbar>

          <ListPagination
            v-model:page="page"
            v-model:page-size="pageSize"
            :item-count="total"
            class="px-5 dark:bg-[#18181c]"
            @update:page="loadSkills"
            @update:page-size="loadSkills"
          />
        </div>

        <div
          class="w-[380px] flex flex-col flex-shrink-0 border-l border-gray-200 bg-white dark:border-gray-700 dark:bg-[#18181c]"
        >
          <template v-if="selectedSkill">
            <div class="border-b border-gray-100 px-5 py-3 dark:border-gray-700">
              <h2 class="text-sm text-gray-800 font-semibold dark:text-gray-100">技能详情</h2>
            </div>

            <NScrollbar class="flex-1">
              <div class="p-5 space-y-4">
                <div class="flex items-center gap-3">
                  <div
                    class="h-12 w-12 flex items-center justify-center rounded-xl bg-primary-50 text-primary-500 dark:bg-primary-900/30"
                  >
                    <icon-carbon:skill-level-advanced class="text-2xl" />
                  </div>
                  <div class="min-w-0">
                    <h3 class="text-base text-gray-900 font-semibold dark:text-gray-50">{{ selectedSkill.name }}</h3>
                    <p class="mt-0.5 text-sm text-gray-500 dark:text-gray-400">
                      {{ selectedSkill.description || '暂无技能描述' }}
                    </p>
                  </div>
                </div>

                <div class="flex items-center gap-2">
                  <NTag :type="getStatusTagType(selectedSkill.status)" size="small" :bordered="false">
                    {{ selectedSkill.status || '草稿' }}
                  </NTag>
                  <NTag type="info" size="small" :bordered="false">
                    {{ selectedSkill.category || '通用技能' }}
                  </NTag>
                  <span class="text-xs text-gray-400">版本 {{ selectedSkill.version || '-' }}</span>
                </div>

                <NDivider class="!my-2" />

                <div class="space-y-2">
                  <div class="flex items-center justify-between text-sm">
                    <span class="text-xs text-gray-500 dark:text-gray-400">调用次数</span>
                    <span class="text-gray-700 dark:text-gray-300">
                      {{ selectedSkill.callCount?.toLocaleString() ?? 0 }}
                    </span>
                  </div>
                  <div class="flex items-center justify-between text-sm">
                    <span class="text-xs text-gray-500 dark:text-gray-400">成功率</span>
                    <span :class="statusSummary !== '-' ? 'text-emerald-600 font-medium' : 'text-gray-400'">
                      {{ statusSummary }}
                    </span>
                  </div>
                  <div class="flex items-center justify-between text-sm">
                    <span class="text-xs text-gray-500 dark:text-gray-400">平均耗时</span>
                    <span class="text-gray-700 dark:text-gray-300">{{ selectedSkill.avgDurationMs ?? 0 }} ms</span>
                  </div>
                </div>

                <NDivider class="!my-2" />

                <div>
                  <div class="mb-2 flex items-center gap-1.5 text-xs text-gray-500 font-medium dark:text-gray-400">
                    <icon-carbon:text-link class="text-primary-500" />
                    Prompt 模板
                  </div>
                  <div class="flex flex-wrap gap-1.5">
                    <NTag
                      v-for="prompt in getResourceLabels(promptOptions, selectedSkill.promptRefs)"
                      :key="prompt"
                      size="small"
                      :bordered="false"
                      type="success"
                    >
                      {{ prompt }}
                    </NTag>
                    <span v-if="!splitCsv(selectedSkill.promptRefs).length" class="text-xs text-gray-400">未绑定</span>
                  </div>
                </div>

                <div>
                  <div class="mb-2 flex items-center gap-1.5 text-xs text-gray-500 font-medium dark:text-gray-400">
                    <icon-carbon:tool-kit class="text-primary-500" />
                    MCP 工具
                  </div>
                  <div class="flex flex-wrap gap-1.5">
                    <NTag
                      v-for="tool in getResourceLabels(toolOptions, selectedSkill.mcpToolRefs)"
                      :key="tool"
                      size="small"
                      :bordered="false"
                    >
                      {{ tool }}
                    </NTag>
                    <span v-if="!splitCsv(selectedSkill.mcpToolRefs).length" class="text-xs text-gray-400">未绑定</span>
                  </div>
                </div>

                <div>
                  <div class="mb-2 flex items-center gap-1.5 text-xs text-gray-500 font-medium dark:text-gray-400">
                    <icon-carbon:tag class="text-primary-500" />
                    标签
                  </div>
                  <div class="flex flex-wrap gap-1.5">
                    <NTag v-for="tag in splitCsv(selectedSkill.tags)" :key="tag" size="small" :bordered="false" round>
                      {{ tag }}
                    </NTag>
                    <span v-if="!splitCsv(selectedSkill.tags).length" class="text-xs text-gray-400">未打标签</span>
                  </div>
                </div>

                <NDivider class="!my-2" />

                <div class="text-xs text-gray-400 space-y-1.5 dark:text-gray-500">
                  <div class="flex items-center gap-2">
                    <icon-carbon:user-avatar class="text-sm" />
                    <span>负责人：{{ selectedSkill.ownerName || '未指定' }}</span>
                  </div>
                  <div class="flex items-center gap-2">
                    <icon-carbon:time class="text-sm" />
                    <span>更新：{{ formatTime(selectedSkill.updatedAt) }}</span>
                  </div>
                  <div class="flex items-center gap-2">
                    <icon-carbon:identification class="text-sm" />
                    <span>ID：{{ selectedSkill.skillId }}</span>
                  </div>
                </div>

                <div class="flex flex-wrap gap-2 pt-1">
                  <FavoriteButton
                    type="skill"
                    :target-id="selectedSkill.skillId"
                    :title="selectedSkill.name"
                    :description="selectedSkill.description"
                    :meta="selectedSkill.category || selectedSkill.status || ''"
                    size="small"
                    :text="false"
                    show-label
                  />
                  <NButton size="small" type="primary" @click="openEdit(selectedSkill.skillId)">
                    <template #icon><icon-carbon:edit /></template>
                    编辑
                  </NButton>
                  <NButton
                    v-if="selectedSkill.status !== '已发布'"
                    size="small"
                    secondary
                    type="success"
                    @click="publishSkill(selectedSkill.skillId)"
                  >
                    <template #icon><icon-carbon:launch /></template>
                    发布
                  </NButton>
                  <div
                    class="inline-flex items-center gap-2 rounded-lg bg-gray-100 px-2.5 py-1 text-xs text-gray-600 dark:bg-[#1e1e22] dark:text-gray-300"
                  >
                    <span>{{ selectedSkill.status === '已停用' ? '已停用' : '启用中' }}</span>
                    <NSwitch
                      :value="selectedSkill.status !== '已停用'"
                      size="small"
                      @update:value="toggleSelectedSkillStatus"
                    />
                  </div>
                  <NButton size="small" secondary type="error" @click="deleteSkill(selectedSkill.skillId)">
                    <template #icon><icon-carbon:trash-can /></template>
                    删除
                  </NButton>
                </div>
              </div>
            </NScrollbar>
          </template>

          <template v-else>
            <div class="flex flex-1 items-center justify-center">
              <NEmpty description="选择一个技能查看详情">
                <template #icon>
                  <icon-carbon:skill-level-advanced class="text-4xl text-gray-300 dark:text-gray-600" />
                </template>
              </NEmpty>
            </div>
          </template>
        </div>
      </div>
    </template>

    <template v-else>
      <div class="h-full flex flex-col">
        <div
          class="flex items-center justify-between border-b border-gray-200 bg-white px-6 py-3 dark:border-gray-700 dark:bg-[#18181c]"
        >
          <div class="flex items-center gap-3">
            <NButton text @click="activeView = 'list'">
              <template #icon><icon-carbon:arrow-left class="text-lg" /></template>
              返回列表
            </NButton>
            <NDivider vertical />
            <div class="flex items-center gap-2">
              <div
                class="h-8 w-8 flex items-center justify-center rounded-lg bg-primary-50 text-primary-500 dark:bg-primary-900/30"
              >
                <icon-carbon:skill-level-advanced class="text-lg" />
              </div>
              <span class="text-base text-gray-800 font-semibold dark:text-gray-100">
                {{ form.name || '未命名技能' }}
              </span>
              <span class="text-xs text-gray-400">{{ form.category || '通用技能' }}</span>
            </div>
          </div>
          <div class="flex items-center gap-2">
            <NButton size="small" :loading="saving" type="primary" @click="saveSkill">
              <template #icon><icon-carbon:save /></template>
              保存
            </NButton>
            <NButton
              v-if="form.skillId && form.status !== '已发布'"
              size="small"
              type="success"
              @click="publishSkill(form.skillId)"
            >
              <template #icon><icon-carbon:launch /></template>
              发布
            </NButton>
          </div>
        </div>

        <div class="flex flex-1 overflow-hidden divide-x divide-gray-100 dark:divide-gray-700">
          <div class="flex-1 basis-1/3 overflow-y-auto bg-white px-4 py-4 dark:bg-[#18181c]">
            <div class="mb-4 text-xs text-gray-400 font-semibold tracking-wider uppercase">基础信息</div>
            <div class="space-y-4">
              <div>
                <div class="mb-1 text-xs text-gray-500 dark:text-gray-400">技能名称</div>
                <NInput v-model:value="form.name" placeholder="例如：合同条款风险扫描" />
              </div>
              <div class="grid grid-cols-2 gap-3">
                <div>
                  <div class="mb-1 text-xs text-gray-500 dark:text-gray-400">分类</div>
                  <NInput v-model:value="form.category" placeholder="如：客服、审查、分析" />
                </div>
                <div>
                  <div class="mb-1 text-xs text-gray-500 dark:text-gray-400">负责人</div>
                  <NInput v-model:value="form.ownerName" placeholder="填写团队或负责人" />
                </div>
              </div>
              <div>
                <div class="mb-1 text-xs text-gray-500 dark:text-gray-400">标签</div>
                <NInput v-model:value="form.tags" placeholder="多个标签用逗号分隔" />
              </div>
              <div>
                <div class="mb-1 text-xs text-gray-500 dark:text-gray-400">技能描述</div>
                <NInput v-model:value="form.description" type="textarea" :autosize="{ minRows: 4, maxRows: 6 }" />
              </div>
              <div>
                <div class="mb-1 text-xs text-gray-500 dark:text-gray-400">执行说明</div>
                <NInput v-model:value="form.instruction" type="textarea" :autosize="{ minRows: 8, maxRows: 12 }" />
              </div>
              <div>
                <div class="mb-1 text-xs text-gray-500 dark:text-gray-400">System Prompt</div>
                <NInput v-model:value="form.systemPrompt" type="textarea" :autosize="{ minRows: 8, maxRows: 12 }" />
              </div>
            </div>
          </div>

          <div class="flex-1 basis-1/3 overflow-y-auto bg-[#fbfcfe] px-4 py-4 dark:bg-[#141418]">
            <div class="mb-4 text-xs text-gray-400 font-semibold tracking-wider uppercase">依赖与契约</div>
            <div class="space-y-4">
              <div class="border border-gray-200 rounded-xl bg-white p-4 dark:border-gray-700 dark:bg-[#18181c]">
                <div class="mb-3 text-sm text-gray-800 font-medium dark:text-gray-100">必要依赖</div>
                <div class="space-y-3">
                  <div>
                    <div class="mb-1 text-xs text-gray-500 dark:text-gray-400">Prompt 模板</div>
                    <NSelect
                      :value="splitCsv(form.promptRefs)"
                      :options="promptOptions"
                      filterable
                      multiple
                      clearable
                      placeholder="选择关联的 Prompt 模板"
                      @update:value="updatePromptRefs"
                    />
                  </div>
                  <div>
                    <div class="mb-1 text-xs text-gray-500 dark:text-gray-400">MCP 工具</div>
                    <NSelect
                      :value="splitCsv(form.mcpToolRefs)"
                      :options="toolOptions"
                      filterable
                      multiple
                      clearable
                      placeholder="选择关联的 MCP 工具"
                      @update:value="updateMcpToolRefs"
                    />
                  </div>
                </div>
              </div>

              <div class="border border-gray-200 rounded-xl bg-white p-4 dark:border-gray-700 dark:bg-[#18181c]">
                <div class="mb-3 text-sm text-gray-800 font-medium dark:text-gray-100">输入输出约定</div>
                <div class="space-y-3">
                  <div>
                    <div class="mb-1 text-xs text-gray-500 dark:text-gray-400">输入 Schema</div>
                    <NInput v-model:value="form.inputSchema" type="textarea" :autosize="{ minRows: 6, maxRows: 10 }" />
                  </div>
                  <div>
                    <div class="mb-1 text-xs text-gray-500 dark:text-gray-400">输出 Schema</div>
                    <NInput v-model:value="form.outputSchema" type="textarea" :autosize="{ minRows: 6, maxRows: 10 }" />
                  </div>
                  <div>
                    <div class="mb-1 text-xs text-gray-500 dark:text-gray-400">运行参数配置</div>
                    <NInput
                      v-model:value="form.runtimeConfig"
                      type="textarea"
                      :autosize="{ minRows: 6, maxRows: 10 }"
                    />
                  </div>
                </div>
              </div>

              <div class="border border-gray-200 rounded-xl bg-white p-4 dark:border-gray-700 dark:bg-[#18181c]">
                <div class="mb-3 text-sm text-gray-800 font-medium dark:text-gray-100">示例数据</div>
                <div class="space-y-3">
                  <div>
                    <div class="mb-1 text-xs text-gray-500 dark:text-gray-400">示例输入</div>
                    <NInput v-model:value="form.exampleInput" type="textarea" :autosize="{ minRows: 4, maxRows: 8 }" />
                  </div>
                  <div>
                    <div class="mb-1 text-xs text-gray-500 dark:text-gray-400">示例输出</div>
                    <NInput v-model:value="form.exampleOutput" type="textarea" :autosize="{ minRows: 4, maxRows: 8 }" />
                  </div>
                </div>
              </div>
            </div>
          </div>

          <div class="flex-1 basis-1/3 overflow-y-auto bg-white px-4 py-4 dark:bg-[#18181c]">
            <div class="mb-4 text-xs text-gray-400 font-semibold tracking-wider uppercase">验证与引用</div>
            <div class="space-y-4">
              <div class="border border-gray-200 rounded-xl bg-white p-4 dark:border-gray-700 dark:bg-[#18181c]">
                <div class="mb-3 flex items-center justify-between gap-3">
                  <div>
                    <div class="text-sm text-gray-800 font-medium dark:text-gray-100">试运行</div>
                    <div class="mt-1 text-xs text-gray-500 dark:text-gray-400">
                      校验输入契约、依赖和运行参数，不执行工作流。
                    </div>
                  </div>
                  <NButton type="primary" size="small" :loading="testing" @click="runSkillTest">开始试运行</NButton>
                </div>
                <div class="space-y-3">
                  <NInput v-model:value="testInputText" type="textarea" :autosize="{ minRows: 5, maxRows: 9 }" />
                  <template v-if="testResult">
                    <div class="grid grid-cols-3 gap-2">
                      <div class="rounded-lg bg-gray-50 px-3 py-2 dark:bg-[#141418]">
                        <div class="text-xs text-gray-400">结果</div>
                        <div
                          class="mt-1 text-sm font-semibold"
                          :class="testResult.success ? 'text-emerald-600' : 'text-amber-600'"
                        >
                          {{ testResult.message }}
                        </div>
                      </div>
                      <div class="rounded-lg bg-gray-50 px-3 py-2 dark:bg-[#141418]">
                        <div class="text-xs text-gray-400">缺失字段</div>
                        <div class="mt-1 text-sm text-gray-800 font-semibold dark:text-gray-200">
                          {{ testResult.validation.missingFields.length }}
                        </div>
                      </div>
                      <div class="rounded-lg bg-gray-50 px-3 py-2 dark:bg-[#141418]">
                        <div class="text-xs text-gray-400">类型错误</div>
                        <div class="mt-1 text-sm text-gray-800 font-semibold dark:text-gray-200">
                          {{ testResult.validation.typeErrors.length }}
                        </div>
                      </div>
                    </div>
                    <div class="rounded-lg bg-gray-950 p-3 text-xs text-gray-100 leading-6">
                      <div class="mb-2 text-gray-400">执行计划</div>
                      <div v-if="testResult.executionPlan.length" class="space-y-1">
                        <div v-for="step in testResult.executionPlan" :key="step">{{ step }}</div>
                      </div>
                      <div v-else>暂无执行计划</div>
                    </div>
                    <div class="grid grid-cols-1 gap-3">
                      <div class="border border-gray-200 rounded-lg p-3 dark:border-gray-700">
                        <div class="mb-2 text-xs text-gray-500 font-medium dark:text-gray-400">告警</div>
                        <div v-if="testResult.warnings.length" class="text-sm text-amber-600 space-y-1">
                          <div v-for="warning in testResult.warnings" :key="warning">{{ warning }}</div>
                        </div>
                        <div v-else class="text-sm text-gray-500">无额外告警</div>
                      </div>
                      <div class="border border-gray-200 rounded-lg p-3 dark:border-gray-700">
                        <div class="mb-2 text-xs text-gray-500 font-medium dark:text-gray-400">模拟输出</div>
                        <pre
                          class="max-h-52 overflow-auto rounded-lg bg-gray-950 p-3 text-xs text-gray-100 leading-6"
                          >{{ formatJson(testResult.mockOutput) }}</pre
                        >
                      </div>
                    </div>
                  </template>
                </div>
              </div>

              <div class="border border-gray-200 rounded-xl bg-white p-4 dark:border-gray-700 dark:bg-[#18181c]">
                <div class="mb-3 text-sm text-gray-800 font-medium dark:text-gray-100">版本历史</div>
                <NSpin :show="historyLoading">
                  <div v-if="histories.length" class="space-y-3">
                    <div
                      v-for="history in histories"
                      :key="history.id"
                      class="border border-gray-100 rounded-lg bg-gray-50 p-3 dark:border-gray-700 dark:bg-[#141418]"
                    >
                      <div class="flex items-start justify-between gap-3">
                        <div class="min-w-0">
                          <div class="text-sm text-gray-900 font-medium dark:text-gray-100">
                            {{ history.version }} · {{ history.changeDescription }}
                          </div>
                          <div class="mt-1 text-xs text-gray-500 dark:text-gray-400">
                            {{ history.snapshotBy || 'system' }} · {{ formatTime(history.snapshotAt) }}
                          </div>
                        </div>
                        <NButton size="tiny" secondary @click="rollbackHistory(history.id)">回滚</NButton>
                      </div>
                    </div>
                  </div>
                  <NEmpty v-else description="暂无历史版本" />
                </NSpin>
              </div>

              <div class="border border-gray-200 rounded-xl bg-white p-4 dark:border-gray-700 dark:bg-[#18181c]">
                <div class="mb-3 text-sm text-gray-800 font-medium dark:text-gray-100">使用方引用</div>
                <NSpin :show="usageLoading">
                  <div v-if="usages.length" class="space-y-3">
                    <div
                      v-for="usage in usages"
                      :key="`${usage.type}-${usage.refId}`"
                      class="flex items-center justify-between gap-3 border border-gray-100 rounded-lg bg-gray-50 px-3 py-3 dark:border-gray-700 dark:bg-[#141418]"
                    >
                      <div class="min-w-0">
                        <div class="truncate text-sm text-gray-900 font-medium dark:text-gray-100">
                          {{ usage.name }}
                        </div>
                        <div class="mt-1 text-xs text-gray-500 dark:text-gray-400">
                          {{ usage.type }} · {{ usage.refId }} · {{ usage.ownerName || '未指定负责人' }}
                        </div>
                      </div>
                      <div class="text-right">
                        <NTag
                          size="small"
                          :bordered="false"
                          :type="usage.status === '运行中' || usage.status === '已发布' ? 'success' : 'default'"
                        >
                          {{ usage.status }}
                        </NTag>
                        <div class="mt-1 text-xs text-gray-400">{{ formatTime(usage.updatedAt) }}</div>
                      </div>
                    </div>
                  </div>
                  <NEmpty v-else description="当前没有 Agent 或 Workflow 引用此技能" />
                </NSpin>
              </div>
            </div>
          </div>
        </div>
      </div>
    </template>

    <NModal v-model:show="createVisible" preset="card" title="创建技能" class="max-w-480px">
      <div class="space-y-4">
        <div>
          <div class="mb-1.5 text-xs text-gray-500 font-medium dark:text-gray-400">
            技能名称
            <span class="text-red-500">*</span>
          </div>
          <NInput v-model:value="createForm.name" placeholder="例如：合同条款风险扫描" @keyup.enter="createSkill" />
        </div>
        <div>
          <div class="mb-1.5 text-xs text-gray-500 font-medium dark:text-gray-400">分类</div>
          <NInput v-model:value="createForm.category" placeholder="如：客服、运营、分析" />
        </div>
        <div>
          <div class="mb-1.5 text-xs text-gray-500 font-medium dark:text-gray-400">描述</div>
          <NInput
            v-model:value="createForm.description"
            type="textarea"
            :rows="3"
            placeholder="描述技能解决的问题和适用场景"
          />
        </div>
      </div>
      <template #footer>
        <div class="flex justify-end gap-3">
          <NButton @click="createVisible = false">取消</NButton>
          <NButton type="primary" @click="createSkill">创建</NButton>
        </div>
      </template>
    </NModal>
  </div>
</template>

<style scoped>
.line-clamp-1 {
  display: -webkit-box;
  -webkit-line-clamp: 1;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
</style>

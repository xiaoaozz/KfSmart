<script setup lang="ts">
import {
  NButton,
  NCard,
  NDivider,
  NDrawer,
  NDrawerContent,
  NEmpty,
  NInput,
  NPagination,
  NSelect,
  NSpin,
  NSwitch,
  NTag
} from 'naive-ui';
import {
  fetchMcpTools,
  fetchPromptTemplates
} from '@/service/api/resource';
import {
  fetchDeleteSkill,
  fetchRollbackSkill,
  fetchPublishSkill,
  fetchSaveSkill,
  fetchSkillDetail,
  fetchSkillHistories,
  fetchSkills,
  fetchSkillStats,
  fetchSkillUsages,
  fetchTestSkill,
  fetchToggleSkillStatus
} from '@/service/api/skills';

type SelectOption = {
  label: string;
  value: string;
  disabled?: boolean;
};

const loading = ref(false);
const statsLoading = ref(false);
const drawerVisible = ref(false);
const saving = ref(false);
const testing = ref(false);
const historyLoading = ref(false);
const usageLoading = ref(false);
const keyword = ref('');
const activeCategory = ref('全部');
const statusFilter = ref<string | null>(null);
const page = ref(1);
const pageSize = ref(12);
const total = ref(0);
const skillList = ref<Api.AgentCenter.Skill[]>([]);
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
const histories = ref<Api.AgentCenter.SkillHistory[]>([]);
const usages = ref<Api.AgentCenter.SkillUsage[]>([]);
const testInputText = ref('{\n  "query": ""\n}');
const testResult = ref<Api.AgentCenter.SkillTestResult | null>(null);

const form = ref<Partial<Api.AgentCenter.Skill>>(createForm());

const statusOptions = [
  { label: '全部状态', value: null },
  { label: '草稿', value: '草稿' },
  { label: '已发布', value: '已发布' },
  { label: '已停用', value: '已停用' }
];

const categoryOptions = computed(() => {
  const categories = Object.entries(stats.value.categories || {});
  return [
    { name: '全部', count: stats.value.total },
    ...categories.map(([name, count]) => ({ name, count }))
  ];
});

const statCards = computed(() => [
  { label: '技能总数', value: stats.value.total, accent: 'bg-slate-900 text-white', note: '统一管理可复用能力' },
  { label: '已发布', value: stats.value.published, accent: 'bg-blue-50 text-blue-700', note: '可直接被业务场景复用' },
  { label: '草稿', value: stats.value.draft, accent: 'bg-amber-50 text-amber-700', note: '待补齐技能描述或依赖' },
  { label: '平均耗时', value: `${stats.value.avgDurationMs} ms`, accent: 'bg-emerald-50 text-emerald-700', note: `累计调用 ${stats.value.totalCalls}` }
]);

function createForm(): Partial<Api.AgentCenter.Skill> {
  return {
    name: '',
    category: activeCategory.value !== '全部' ? activeCategory.value : '通用技能',
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

function splitCsv(value?: string | null) {
  if (!value) return [];
  return value.split(',').map(item => item.trim()).filter(Boolean);
}

function formatMultiValue(values: string[]) {
  return values.join(',');
}

function assignMultiValue(field: keyof Api.AgentCenter.Skill, value: string[] | null) {
  form.value[field] = formatMultiValue(Array.isArray(value) ? value : []);
}

function updatePromptRefs(value: string[] | null) {
  assignMultiValue('promptRefs', value);
}

function updateMcpToolRefs(value: string[] | null) {
  assignMultiValue('mcpToolRefs', value);
}

function getPageRecords<T>(data: any): T[] {
  return data?.records || data?.content || data?.data || [];
}

function formatJson(value: unknown) {
  if (value == null || value === '') return '';
  if (typeof value === 'string') return value;
  try {
    return JSON.stringify(value, null, 2);
  } catch {
    return String(value);
  }
}

function formatTime(value?: string | null) {
  if (!value) return '-';
  return value.replace('T', ' ').slice(0, 16);
}

function getLabel(options: SelectOption[], value: string) {
  return options.find(item => item.value === value)?.label || value;
}

function getResourceLabels(options: SelectOption[], csv?: string | null) {
  return splitCsv(csv).map(item => getLabel(options, item));
}

async function loadStats() {
  statsLoading.value = true;
  const { data, error } = await fetchSkillStats();
  statsLoading.value = false;
  if (!error && data) {
    stats.value = data;
  }
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
  const { data, error } = await fetchSkills({
    page: page.value,
    size: pageSize.value,
    keyword: keyword.value || undefined,
    category: activeCategory.value !== '全部' ? activeCategory.value : undefined,
    status: statusFilter.value || undefined
  });
  loading.value = false;
  if (!error && data) {
    skillList.value = getPageRecords<Api.AgentCenter.Skill>(data);
    total.value = data.total || data.totalElements || skillList.value.length;
  }
}

async function initialize() {
  await Promise.all([loadStats(), loadResources(), loadSkills()]);
}

function openCreate() {
  form.value = createForm();
  histories.value = [];
  usages.value = [];
  testResult.value = null;
  testInputText.value = '{\n  "query": ""\n}';
  drawerVisible.value = true;
}

async function openEdit(skillId: string) {
  const { data, error } = await fetchSkillDetail(skillId);
  if (!error && data) {
    form.value = { ...data };
    testInputText.value = data.exampleInput || '{\n  "query": ""\n}';
    testResult.value = null;
    await loadSkillInsights(skillId);
    drawerVisible.value = true;
  }
}

async function saveSkill() {
  if (!form.value.name?.trim()) {
    window.$message?.warning('请输入技能名称');
    return;
  }
  saving.value = true;
  const { error, data } = await fetchSaveSkill(form.value);
  saving.value = false;
  if (!error) {
    window.$message?.success('保存成功');
    if (data) {
      form.value = { ...data };
      await loadSkillInsights(data.skillId);
    }
    await Promise.all([loadStats(), loadSkills()]);
  }
}

async function loadSkillInsights(skillId: string) {
  historyLoading.value = true;
  usageLoading.value = true;
  const [historyRes, usageRes] = await Promise.all([
    fetchSkillHistories(skillId),
    fetchSkillUsages(skillId)
  ]);
  historyLoading.value = false;
  usageLoading.value = false;
  histories.value = !historyRes.error && historyRes.data ? historyRes.data : [];
  usages.value = !usageRes.error && usageRes.data ? usageRes.data : [];
}

async function publishSkill(skillId: string) {
  const { error } = await fetchPublishSkill(skillId);
  if (!error) {
    window.$message?.success('技能已发布');
    if (form.value.skillId === skillId) {
      await openEdit(skillId);
    }
    await Promise.all([loadStats(), loadSkills()]);
  }
}

async function toggleStatus(skillId: string) {
  const { error } = await fetchToggleSkillStatus(skillId);
  if (!error) {
    window.$message?.success('状态已更新');
    if (form.value.skillId === skillId) {
      await openEdit(skillId);
    }
    await Promise.all([loadStats(), loadSkills()]);
  }
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
        testInputText.value = data.exampleInput || testInputText.value;
        window.$message?.success('回滚成功');
        await Promise.all([loadSkillInsights(data.skillId), loadStats(), loadSkills()]);
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
        await Promise.all([loadStats(), loadSkills()]);
      }
    }
  });
}

function handleSearch() {
  page.value = 1;
  loadSkills();
}

function changeCategory(category: string) {
  activeCategory.value = category;
  page.value = 1;
  loadSkills();
}

function getStatusClass(status: string) {
  return (
    {
      已发布: 'success',
      草稿: 'warning',
      已停用: 'default'
    }[status] || 'default'
  ) as 'success' | 'warning' | 'default';
}

onMounted(initialize);
</script>

<template>
  <div class="flex h-full overflow-hidden bg-[#f6f7fb] dark:bg-gray-950">
    <aside class="w-68 shrink-0 border-r border-gray-200 bg-white px-4 py-6 dark:border-gray-800 dark:bg-gray-900">
      <div class="mb-6 px-2">
        <h1 class="text-xl font-semibold text-gray-950 dark:text-white">Skills 技能中心</h1>
        <p class="mt-1 text-xs leading-5 text-gray-500 dark:text-gray-400">
          管理独立技能定义，明确技能说明、输入输出约定和必要依赖。
        </p>
      </div>

      <div class="mb-4 flex items-center justify-between px-2">
        <span class="text-xs font-medium uppercase tracking-wide text-gray-400">分类</span>
        <NButton text type="primary" @click="loadStats">
          <template #icon>
            <icon-carbon:renew />
          </template>
        </NButton>
      </div>

      <NSpin :show="statsLoading">
        <div class="space-y-1">
          <button
            v-for="category in categoryOptions"
            :key="category.name"
            class="flex w-full items-center justify-between rounded-lg px-3 py-2 text-left text-sm transition-colors"
            :class="
              activeCategory === category.name
                ? 'bg-slate-900 text-white'
                : 'text-gray-600 hover:bg-gray-100 dark:text-gray-300 dark:hover:bg-gray-800'
            "
            @click="changeCategory(category.name)"
          >
            <span class="font-medium">{{ category.name }}</span>
            <span class="text-xs opacity-80">{{ category.count }}</span>
          </button>
        </div>
      </NSpin>
    </aside>

    <main class="min-w-0 flex-1 overflow-y-auto px-8 py-6">
      <div class="mb-6 flex flex-wrap items-center justify-between gap-3">
        <div>
          <h2 class="text-2xl font-bold text-gray-950 dark:text-white">技能定义</h2>
          <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
            统一管理技能说明、契约和最小依赖，不再和流程编排做反向耦合。
          </p>
        </div>
        <div class="flex items-center gap-3">
          <NInput v-model:value="keyword" class="w-72" clearable placeholder="搜索技能名称、描述、标签" @keyup.enter="handleSearch">
            <template #prefix>
              <icon-carbon:search />
            </template>
          </NInput>
          <NSelect
            v-model:value="statusFilter"
            class="w-36"
            clearable
            :options="statusOptions.filter(item => item.value !== null)"
            placeholder="全部状态"
            @update:value="handleSearch"
          />
          <NButton @click="handleSearch">
            查询
          </NButton>
          <NButton type="primary" @click="openCreate">
            <template #icon>
              <icon-carbon:add />
            </template>
            新建技能
          </NButton>
        </div>
      </div>

      <div class="mb-6 grid grid-cols-1 gap-4 xl:grid-cols-4">
        <NCard
          v-for="card in statCards"
          :key="card.label"
          :bordered="false"
          class="overflow-hidden rounded-2xl shadow-sm"
          content-class="p-0"
        >
          <div class="p-5" :class="card.accent">
            <p class="text-sm opacity-80">{{ card.label }}</p>
            <p class="mt-3 text-3xl font-semibold">{{ card.value }}</p>
            <p class="mt-2 text-xs opacity-70">{{ card.note }}</p>
          </div>
        </NCard>
      </div>

      <NSpin :show="loading">
        <div v-if="skillList.length" class="grid grid-cols-1 gap-4 xl:grid-cols-2 2xl:grid-cols-3">
          <article
            v-for="skill in skillList"
            :key="skill.skillId"
            class="rounded-2xl border border-gray-200 bg-white p-5 shadow-sm transition-shadow hover:shadow-md dark:border-gray-800 dark:bg-gray-900"
          >
            <div class="mb-4 flex items-start justify-between gap-3">
              <div class="min-w-0">
                <div class="flex items-center gap-2">
                  <h3 class="truncate text-lg font-semibold text-gray-950 dark:text-white">{{ skill.name }}</h3>
                  <NTag :type="getStatusClass(skill.status)" size="small" round>{{ skill.status }}</NTag>
                </div>
                <p class="mt-1 text-xs text-gray-500 dark:text-gray-400">
                  {{ skill.category || '通用技能' }} · {{ skill.version }} · {{ skill.ownerName || '未指定负责人' }}
                </p>
              </div>
              <button class="rounded-full p-2 text-gray-400 transition hover:bg-gray-100 hover:text-gray-700 dark:hover:bg-gray-800 dark:hover:text-gray-200" @click="openEdit(skill.skillId)">
                <icon-carbon:edit />
              </button>
            </div>

            <p class="min-h-12 line-clamp-3 text-sm leading-6 text-gray-600 dark:text-gray-300">
              {{ skill.description || '暂无技能描述' }}
            </p>

            <div class="mt-4 flex flex-wrap gap-2">
              <NTag v-for="tag in splitCsv(skill.tags)" :key="tag" size="small" round>{{ tag }}</NTag>
              <NTag v-if="!splitCsv(skill.tags).length" size="small" round type="default">未打标签</NTag>
            </div>

            <div class="mt-4 grid grid-cols-2 gap-3 rounded-xl bg-gray-50 p-3 text-xs dark:bg-gray-800/80">
              <div>
                <p class="text-gray-400">Prompt</p>
                <p class="mt-1 font-medium text-gray-900 dark:text-white">{{ splitCsv(skill.promptRefs).length }}</p>
              </div>
              <div>
                <p class="text-gray-400">工具</p>
                <p class="mt-1 font-medium text-gray-900 dark:text-white">{{ splitCsv(skill.mcpToolRefs).length }}</p>
              </div>
            </div>

            <div class="mt-4 space-y-2 text-xs text-gray-500 dark:text-gray-400">
              <p class="truncate">Prompt: {{ getResourceLabels(promptOptions, skill.promptRefs).join(' / ') || '未绑定' }}</p>
              <p class="truncate">MCP: {{ getResourceLabels(toolOptions, skill.mcpToolRefs).join(' / ') || '未绑定' }}</p>
            </div>

            <div class="mt-5 flex flex-wrap items-center gap-2 border-t border-gray-100 pt-4 dark:border-gray-800">
              <NButton size="small" secondary @click="openEdit(skill.skillId)">编辑</NButton>
              <NButton v-if="skill.status !== '已发布'" size="small" type="primary" ghost @click="publishSkill(skill.skillId)">发布</NButton>
              <div class="flex items-center gap-2 rounded-full bg-gray-100 px-2 py-1 text-xs dark:bg-gray-800">
                <span>{{ skill.status === '已停用' ? '已停用' : '启用中' }}</span>
                <NSwitch :value="skill.status !== '已停用'" size="small" @update:value="() => toggleStatus(skill.skillId)" />
              </div>
              <NButton size="small" type="error" quaternary @click="deleteSkill(skill.skillId)">删除</NButton>
            </div>
          </article>
        </div>

        <div v-else class="flex min-h-90 items-center justify-center rounded-2xl border border-dashed border-gray-200 bg-white dark:border-gray-800 dark:bg-gray-900">
          <NEmpty description="暂无匹配技能" />
        </div>
      </NSpin>

      <div class="mt-6 flex justify-end">
        <NPagination
          v-model:page="page"
          v-model:page-size="pageSize"
          :item-count="total"
          :page-sizes="[12, 24, 48]"
          show-size-picker
          @update:page="loadSkills"
          @update:page-size="loadSkills"
        />
      </div>
    </main>

      <NDrawer v-model:show="drawerVisible" :width="760" placement="right">
      <NDrawerContent :title="form.skillId ? '编辑技能' : '新建技能'" closable>
        <div class="space-y-6">
          <div v-if="form.skillId" class="flex flex-wrap items-center gap-2 rounded-2xl bg-slate-900 px-4 py-3 text-white">
            <span class="text-sm font-medium">{{ form.name || '未命名技能' }}</span>
            <NTag size="small" round :bordered="false" :type="getStatusClass(form.status || '草稿')">{{ form.status || '草稿' }}</NTag>
            <span class="text-xs opacity-70">版本 {{ form.version }}</span>
            <span class="text-xs opacity-70">更新时间 {{ formatTime(form.updatedAt) }}</span>
          </div>

          <section class="grid grid-cols-1 gap-4 md:grid-cols-2">
            <div>
              <p class="mb-2 text-sm font-medium text-gray-700 dark:text-gray-200">技能名称</p>
              <NInput v-model:value="form.name" placeholder="例如：合同条款风险扫描" />
            </div>
            <div>
              <p class="mb-2 text-sm font-medium text-gray-700 dark:text-gray-200">负责人</p>
              <NInput v-model:value="form.ownerName" placeholder="填写负责人或团队名称" />
            </div>
            <div>
              <p class="mb-2 text-sm font-medium text-gray-700 dark:text-gray-200">分类</p>
              <NInput v-model:value="form.category" placeholder="如：客服、审查、分析、运营" />
            </div>
            <div>
              <p class="mb-2 text-sm font-medium text-gray-700 dark:text-gray-200">标签</p>
              <NInput v-model:value="form.tags" placeholder="多个标签用逗号分隔" />
            </div>
          </section>

          <section>
            <p class="mb-2 text-sm font-medium text-gray-700 dark:text-gray-200">技能描述</p>
            <NInput v-model:value="form.description" type="textarea" :autosize="{ minRows: 3, maxRows: 5 }" placeholder="描述技能解决什么问题、适用于哪些场景" />
          </section>

          <section>
            <p class="mb-2 text-sm font-medium text-gray-700 dark:text-gray-200">执行说明</p>
            <NInput v-model:value="form.instruction" type="textarea" :autosize="{ minRows: 5, maxRows: 8 }" placeholder="说明技能调用顺序、边界条件和失败处理规则" />
          </section>

          <section>
            <p class="mb-2 text-sm font-medium text-gray-700 dark:text-gray-200">System Prompt</p>
            <NInput v-model:value="form.systemPrompt" type="textarea" :autosize="{ minRows: 4, maxRows: 8 }" placeholder="可选：定义技能的角色、语气和安全边界" />
          </section>

          <section>
            <p class="mb-2 text-sm font-medium text-gray-700 dark:text-gray-200">运行参数配置</p>
            <NInput
              v-model:value="form.runtimeConfig"
              type="textarea"
              :autosize="{ minRows: 4, maxRows: 8 }"
              placeholder="配置超时、重试次数、返回模式等启用参数"
            />
          </section>

          <NDivider title-placement="left">必要依赖</NDivider>

          <section class="grid grid-cols-1 gap-4 md:grid-cols-2">
            <div>
              <p class="mb-2 text-sm font-medium text-gray-700 dark:text-gray-200">Prompt 模板</p>
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
              <p class="mb-2 text-sm font-medium text-gray-700 dark:text-gray-200">MCP 工具</p>
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
          </section>

          <NDivider title-placement="left">输入输出约定</NDivider>

          <section class="grid grid-cols-1 gap-4">
            <div>
              <p class="mb-2 text-sm font-medium text-gray-700 dark:text-gray-200">输入 Schema</p>
              <NInput v-model:value="form.inputSchema" type="textarea" :autosize="{ minRows: 4, maxRows: 8 }" />
            </div>
            <div>
              <p class="mb-2 text-sm font-medium text-gray-700 dark:text-gray-200">输出 Schema</p>
              <NInput v-model:value="form.outputSchema" type="textarea" :autosize="{ minRows: 4, maxRows: 8 }" />
            </div>
            <div>
              <p class="mb-2 text-sm font-medium text-gray-700 dark:text-gray-200">示例输入</p>
              <NInput v-model:value="form.exampleInput" type="textarea" :autosize="{ minRows: 3, maxRows: 6 }" />
            </div>
            <div>
              <p class="mb-2 text-sm font-medium text-gray-700 dark:text-gray-200">示例输出</p>
              <NInput v-model:value="form.exampleOutput" type="textarea" :autosize="{ minRows: 3, maxRows: 6 }" />
            </div>
          </section>

          <template v-if="form.skillId">
            <NDivider title-placement="left">试运行</NDivider>

            <section class="space-y-4 rounded-2xl border border-gray-200 p-4 dark:border-gray-800">
              <div class="flex items-center justify-between gap-3">
                <div>
                  <p class="text-sm font-medium text-gray-800 dark:text-gray-100">试运行输入</p>
                  <p class="mt-1 text-xs text-gray-500 dark:text-gray-400">用于校验输入契约、运行参数和依赖解析，不会触发工作流执行。</p>
                </div>
                <NButton type="primary" :loading="testing" @click="runSkillTest">开始试运行</NButton>
              </div>

              <NInput v-model:value="testInputText" type="textarea" :autosize="{ minRows: 5, maxRows: 10 }" />

              <div v-if="testResult" class="space-y-4">
                <div class="grid grid-cols-1 gap-3 md:grid-cols-3">
                  <div class="rounded-xl bg-gray-50 p-3 dark:bg-gray-800/70">
                    <p class="text-xs text-gray-400">校验结果</p>
                    <p class="mt-1 text-sm font-semibold" :class="testResult.success ? 'text-emerald-600' : 'text-amber-600'">
                      {{ testResult.message }}
                    </p>
                  </div>
                  <div class="rounded-xl bg-gray-50 p-3 dark:bg-gray-800/70">
                    <p class="text-xs text-gray-400">缺失字段</p>
                    <p class="mt-1 text-sm font-semibold text-gray-900 dark:text-white">{{ testResult.validation.missingFields.length }}</p>
                  </div>
                  <div class="rounded-xl bg-gray-50 p-3 dark:bg-gray-800/70">
                    <p class="text-xs text-gray-400">类型错误</p>
                    <p class="mt-1 text-sm font-semibold text-gray-900 dark:text-white">{{ testResult.validation.typeErrors.length }}</p>
                  </div>
                </div>

                <div class="grid grid-cols-1 gap-4 md:grid-cols-2">
                  <div class="rounded-xl border border-gray-200 p-3 dark:border-gray-800">
                    <p class="mb-2 text-sm font-medium text-gray-800 dark:text-gray-100">执行计划</p>
                    <div class="space-y-2 text-sm text-gray-600 dark:text-gray-300">
                      <p v-for="step in testResult.executionPlan" :key="step">{{ step }}</p>
                    </div>
                  </div>
                  <div class="rounded-xl border border-gray-200 p-3 dark:border-gray-800">
                    <p class="mb-2 text-sm font-medium text-gray-800 dark:text-gray-100">告警</p>
                    <div v-if="testResult.warnings.length" class="space-y-2 text-sm text-amber-600">
                      <p v-for="warning in testResult.warnings" :key="warning">{{ warning }}</p>
                    </div>
                    <p v-else class="text-sm text-gray-500 dark:text-gray-400">无额外告警</p>
                  </div>
                </div>

                <div class="grid grid-cols-1 gap-4 md:grid-cols-2">
                  <div class="rounded-xl border border-gray-200 p-3 dark:border-gray-800">
                    <p class="mb-2 text-sm font-medium text-gray-800 dark:text-gray-100">解析到的 Prompt</p>
                    <div v-if="testResult.resolvedPrompts.length" class="space-y-2">
                      <div v-for="prompt in testResult.resolvedPrompts" :key="prompt.templateId" class="rounded-lg bg-gray-50 px-3 py-2 text-sm dark:bg-gray-800/70">
                        {{ prompt.name }} · {{ prompt.version }}
                      </div>
                    </div>
                    <p v-else class="text-sm text-gray-500 dark:text-gray-400">未绑定</p>
                  </div>
                  <div class="rounded-xl border border-gray-200 p-3 dark:border-gray-800">
                    <p class="mb-2 text-sm font-medium text-gray-800 dark:text-gray-100">解析到的工具</p>
                    <div v-if="testResult.resolvedTools.length" class="space-y-2">
                      <div v-for="tool in testResult.resolvedTools" :key="tool.toolId" class="rounded-lg bg-gray-50 px-3 py-2 text-sm dark:bg-gray-800/70">
                        {{ tool.name }} · {{ tool.status }}
                      </div>
                    </div>
                    <p v-else class="text-sm text-gray-500 dark:text-gray-400">未绑定</p>
                  </div>
                </div>

                <div class="grid grid-cols-1 gap-4 md:grid-cols-2">
                  <div>
                    <p class="mb-2 text-sm font-medium text-gray-800 dark:text-gray-100">运行参数解析</p>
                    <pre class="max-h-64 overflow-auto rounded-xl bg-gray-950 p-3 text-xs leading-6 text-gray-100">{{ formatJson(testResult.runtimeConfig) }}</pre>
                  </div>
                  <div>
                    <p class="mb-2 text-sm font-medium text-gray-800 dark:text-gray-100">模拟输出</p>
                    <pre class="max-h-64 overflow-auto rounded-xl bg-gray-950 p-3 text-xs leading-6 text-gray-100">{{ formatJson(testResult.mockOutput) }}</pre>
                  </div>
                </div>
              </div>
            </section>

            <NDivider title-placement="left">版本历史</NDivider>

            <section class="rounded-2xl border border-gray-200 p-4 dark:border-gray-800">
              <NSpin :show="historyLoading">
                <div v-if="histories.length" class="space-y-3">
                  <div
                    v-for="history in histories"
                    :key="history.id"
                    class="flex flex-col gap-3 rounded-xl border border-gray-100 p-3 dark:border-gray-800"
                  >
                    <div class="flex flex-wrap items-center justify-between gap-2">
                      <div>
                        <p class="text-sm font-medium text-gray-900 dark:text-white">{{ history.version }} · {{ history.changeDescription }}</p>
                        <p class="mt-1 text-xs text-gray-500 dark:text-gray-400">{{ history.snapshotBy || 'system' }} · {{ formatTime(history.snapshotAt) }}</p>
                      </div>
                      <NButton size="small" secondary @click="rollbackHistory(history.id)">回滚到此版本</NButton>
                    </div>
                    <p class="text-sm text-gray-600 dark:text-gray-300">{{ history.description || '无描述' }}</p>
                  </div>
                </div>
                <NEmpty v-else description="暂无历史版本" />
              </NSpin>
            </section>

            <NDivider title-placement="left">使用方引用</NDivider>

            <section class="rounded-2xl border border-gray-200 p-4 dark:border-gray-800">
              <NSpin :show="usageLoading">
                <div v-if="usages.length" class="space-y-3">
                  <div
                    v-for="usage in usages"
                    :key="`${usage.type}-${usage.refId}`"
                    class="flex items-center justify-between gap-3 rounded-xl border border-gray-100 px-3 py-3 dark:border-gray-800"
                  >
                    <div class="min-w-0">
                      <p class="truncate text-sm font-medium text-gray-900 dark:text-white">{{ usage.name }}</p>
                      <p class="mt-1 text-xs text-gray-500 dark:text-gray-400">{{ usage.type }} · {{ usage.refId }} · {{ usage.ownerName || '未指定负责人' }}</p>
                    </div>
                    <div class="text-right">
                      <NTag size="small" round :type="usage.status === '运行中' || usage.status === '已发布' ? 'success' : 'default'">{{ usage.status }}</NTag>
                      <p class="mt-1 text-xs text-gray-400">{{ formatTime(usage.updatedAt) }}</p>
                    </div>
                  </div>
                </div>
                <NEmpty v-else description="当前没有 Agent 或 Workflow 引用此技能" />
              </NSpin>
            </section>
          </template>

          <div class="flex justify-end gap-3 border-t border-gray-100 pt-5 dark:border-gray-800">
            <NButton @click="drawerVisible = false">{{ form.skillId ? '关闭' : '取消' }}</NButton>
            <NButton type="primary" :loading="saving" @click="saveSkill">保存技能</NButton>
          </div>
        </div>
      </NDrawerContent>
    </NDrawer>
  </div>
</template>

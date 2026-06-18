<script setup lang="tsx">
import {
  NButton,
  NDivider,
  NEmpty,
  NInput,
  NScrollbar,
  NSelect,
  NSpin,
  NTag,
  NTooltip
} from 'naive-ui';
import {
  fetchDeletePromptTemplate,
  fetchPromptCategories,
  fetchPromptDetail,
  fetchPromptHistories,
  fetchPromptTemplates,
  fetchRollbackPrompt,
  fetchSavePromptTemplate,
  fetchTogglePromptStatus
} from '@/service/api/resource';

type PageView = 'list' | 'edit';

const activeView = ref<PageView>('list');
const keyword = ref('');
const activeCategory = ref('全部');
const categories = ref<string[]>([]);
const promptList = ref<Api.AgentCenter.PromptTemplate[]>([]);
const loading = ref(false);
const selectedPrompt = ref<Api.AgentCenter.PromptTemplate | null>(null);

// 编辑表单
const editForm = ref<Partial<Api.AgentCenter.PromptTemplate>>({});
const isEdit = ref(false);
const saving = ref(false);

// 历史版本状态
const showHistoryPanel = ref(false);
const historyList = ref<Api.AgentCenter.PromptHistory[]>([]);
const historyLoading = ref(false);
const selectedHistory = ref<Api.AgentCenter.PromptHistory | null>(null);

// 通用分类
const defaultCategories = ['通用指令', '任务执行', '编码任务', '知识库回答'];

// 各分类数量统计
const categoryCounts = computed(() => {
  const counts: Record<string, number> = { '全部': promptList.value.length };
  promptList.value.forEach(item => {
    const cat = item.category;
    if (cat) {
      counts[cat] = (counts[cat] || 0) + 1;
    }
  });
  return counts;
});

function getPageRecords<T>(data: any): T[] {
  return data?.records || data?.content || data?.data || [];
}

async function loadCategories() {
  const { data, error } = await fetchPromptCategories();
  if (!error && data && data.length > 0) {
    const merged = [...new Set([...defaultCategories, ...data])];
    categories.value = merged;
  } else {
    categories.value = defaultCategories;
  }
}

async function loadPrompts() {
  loading.value = true;
  const params: Record<string, any> = { page: 1, size: 50 };
  if (keyword.value) params.keyword = keyword.value;
  if (activeCategory.value !== '全部') params.category = activeCategory.value;

  const { data, error } = await fetchPromptTemplates(params);
  loading.value = false;
  if (!error && data) {
    promptList.value = getPageRecords<Api.AgentCenter.PromptTemplate>(data);
  }
}

function selectPrompt(item: Api.AgentCenter.PromptTemplate) {
  selectedPrompt.value = item;
}

function handleCategoryClick(cat: string) {
  activeCategory.value = cat;
  loadPrompts();
}

async function handleSearch() {
  await loadPrompts();
}

// ---- 编辑模式 ----

function openCreate() {
  isEdit.value = false;
  editForm.value = {
    name: '',
    description: '',
    category: activeCategory.value !== '全部' ? activeCategory.value : (categories.value[0] ?? defaultCategories[0]),
    version: '',
    systemContent: '',
    content: '',
    variables: '',
    tags: '',
    status: '启用'
  };
  activeView.value = 'edit';
  showHistoryPanel.value = false;
}

function openEdit(item: Api.AgentCenter.PromptTemplate) {
  isEdit.value = true;
  editForm.value = { ...item };
  activeView.value = 'edit';
  showHistoryPanel.value = false;
}

function openEditWithHistory(item: Api.AgentCenter.PromptTemplate) {
  isEdit.value = true;
  editForm.value = { ...item };
  activeView.value = 'edit';
  showHistoryPanel.value = true;
  historyList.value = [];
  selectedHistory.value = null;
  loadHistories(item.templateId);
}

function goBackToList() {
  activeView.value = 'list';
  loadPrompts();
}

async function savePrompt() {
  if (!editForm.value.name) {
    window.$message?.warning('请输入模板名称');
    return;
  }
  saving.value = true;
  // 提交时不发送版本号，由后端自动递增
  const submitData = { ...editForm.value };
  delete (submitData as any).version;
  const { data, error } = await fetchSavePromptTemplate(submitData);
  saving.value = false;
  if (!error) {
    window.$message?.success(isEdit.value ? '更新成功' : '创建成功');
    if (data) {
      editForm.value = { ...data };
    }
    isEdit.value = true;
  }
}

async function toggleStatus(templateId: string) {
  const { error } = await fetchTogglePromptStatus(templateId);
  if (!error) {
    window.$message?.success('状态切换成功');
    await loadPrompts();
  }
}

async function deletePrompt(templateId: string) {
  window.$dialog?.warning({
    title: '删除模板',
    content: '确认删除该模板吗？此操作不可恢复。',
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      const { error } = await fetchDeletePromptTemplate(templateId);
      if (!error) {
        window.$message?.success('删除成功');
        if (selectedPrompt.value?.templateId === templateId) {
          selectedPrompt.value = null;
        }
        await loadPrompts();
      }
    }
  });
}

// ---- 历史版本功能 ----

async function loadHistories(templateId?: string) {
  const tid = templateId || editForm.value.templateId;
  if (!tid) return;
  historyLoading.value = true;
  const { data, error } = await fetchPromptHistories(tid);
  historyLoading.value = false;
  if (!error && data) {
    historyList.value = (data as any).records || (data as any).content || data || [];
  }
}

function toggleHistoryPanel() {
  showHistoryPanel.value = !showHistoryPanel.value;
  if (showHistoryPanel.value) {
    historyList.value = [];
    selectedHistory.value = null;
    loadHistories();
  }
}

async function handleRollback(snapshot: Api.AgentCenter.PromptHistory) {
  if (!editForm.value.templateId) return;
  window.$dialog?.warning({
    title: '回滚版本',
    content: '确认回滚到此版本？当前版本将自动保存为历史记录，版本号继续递增。',
    positiveText: '回滚',
    negativeText: '取消',
    onPositiveClick: async () => {
      const { data, error } = await fetchRollbackPrompt(editForm.value.templateId!, snapshot.id);
      if (!error && data) {
        window.$message?.success('回滚成功，版本号已自动递增');
        editForm.value = { ...data };
        if (editForm.value.templateId) {
          const { data: refreshed } = await fetchPromptDetail(editForm.value.templateId);
          if (refreshed) {
            editForm.value = { ...refreshed };
          }
        }
        loadHistories();
      }
    }
  });
}

/** 基于历史版本编辑：用历史版本的内容填充编辑表单，但保留当前模板ID和最新版本号 */
function editFromHistory(snapshot: Api.AgentCenter.PromptHistory) {
  editForm.value = {
    ...editForm.value,
    name: snapshot.name,
    description: snapshot.description,
    category: snapshot.category,
    systemContent: snapshot.systemContent,
    content: snapshot.content,
    variables: snapshot.variables,
    tags: snapshot.tags,
    status: snapshot.status
  };
  // 切换到模板设置面板
  showHistoryPanel.value = false;
  window.$message?.info('已加载历史版本内容，保存后版本号将自动递增');
}

function selectHistory(h: Api.AgentCenter.PromptHistory) {
  selectedHistory.value = selectedHistory.value?.id === h.id ? null : h;
}

// ---- 工具函数 ----

function getTagList(tags: string | undefined | null): string[] {
  if (!tags) return [];
  return tags.split(',').map(t => t.trim()).filter(Boolean);
}

function getVarList(variables: string | undefined | null): string[] {
  if (!variables) return [];
  return variables.split(',').map(v => v.trim()).filter(Boolean);
}

function formatVarName(v: string): string {
  return '{{' + v + '}}';
}

function formatTime(time: string | undefined | null, fmt = 'YYYY-MM-DD HH:mm:ss'): string {
  if (!time) return '-';
  return dayjs(time).format(fmt);
}

function removeTag(tag: string) {
  if (editForm.value.tags) {
    editForm.value.tags = editForm.value.tags
      .split(',')
      .map(t => t.trim())
      .filter(t => t !== tag)
      .join(', ');
  }
}

function syncVariablesFromContent() {
  const sysText = editForm.value.systemContent || '';
  const userText = editForm.value.content || '';
  const combined = `${sysText}\n${userText}`;

  // 使用 [^}]+ 匹配任意占位符内容（支持 input.query 等含点号的变量名）
  const matches = combined.match(/\{\{([^}]+)\}\}/g);
  const extracted: string[] = matches
    ? [...new Set(matches.map(m => m.replace(/^\{\{|\}\}$/g, '').trim()))]
    : [];

  editForm.value.variables = extracted.join(', ');
}

const systemPlaceholder = '输入 System Prompt，定义 AI 的角色、行为约束和通用指令\n\n例如：\n你是一个专业的{{role}}助手。请遵循以下规则：\n1. 始终基于提供的参考资料回答问题\n2. 如果参考资料中没有相关信息，请明确告知用户\n3. 回答时保持专业、准确、简洁';

const contentPlaceholder = '输入 User Prompt 模板，定义用户消息的格式和变量占位符\n\n例如：\n请根据以下参考资料回答用户的问题。\n\n参考资料：\n{{documents}}\n\n用户问题：{{query}}';

onMounted(async () => {
  await loadCategories();
  await loadPrompts();
});
</script>

<template>
  <div class="h-full flex flex-col bg-[#f5f7fa] dark:bg-[#101014]">
    <!-- ==================== 列表视图 ==================== -->
    <template v-if="activeView === 'list'">
      <div class="h-full flex">
        <!-- 左侧分类导航 -->
        <div class="w-180px flex-shrink-0 border-r border-gray-200 dark:border-gray-700 bg-white dark:bg-[#18181c] flex flex-col">
          <div class="px-4 py-5">
            <h2 class="text-sm font-semibold text-gray-800 dark:text-gray-100">Prompt分类</h2>
          </div>
          <NScrollbar class="flex-1">
            <div class="px-2 space-y-0.5">
              <div
                class="cursor-pointer rounded-lg px-3 py-2 text-sm transition-all"
                :class="activeCategory === '全部'
                  ? 'bg-primary-50 text-primary-600 font-medium dark:bg-primary-900/20 dark:text-primary-400'
                  : 'text-gray-600 hover:bg-gray-50 dark:text-gray-400 dark:hover:bg-gray-800'"
                @click="handleCategoryClick('全部')"
              >
                <div class="flex items-center justify-between">
                  <div class="flex items-center gap-2">
                    <icon-carbon:catalog class="text-base" />
                    <span>全部</span>
                  </div>
                  <span class="text-xs opacity-60">{{ categoryCounts['全部'] ?? 0 }}</span>
                </div>
              </div>
              <div
                v-for="cat in categories"
                :key="cat"
                class="cursor-pointer rounded-lg px-3 py-2 text-sm transition-all"
                :class="activeCategory === cat
                  ? 'bg-primary-50 text-primary-600 font-medium dark:bg-primary-900/20 dark:text-primary-400'
                  : 'text-gray-600 hover:bg-gray-50 dark:text-gray-400 dark:hover:bg-gray-800'"
                @click="handleCategoryClick(cat)"
              >
                <div class="flex items-center justify-between">
                  <div class="flex items-center gap-2">
                    <icon-carbon:tag class="text-base" />
                    <span>{{ cat }}</span>
                  </div>
                  <span class="text-xs opacity-60">{{ categoryCounts[cat] ?? 0 }}</span>
                </div>
              </div>
            </div>
          </NScrollbar>
        </div>

        <!-- 中间卡片列表 -->
        <div class="flex-1 flex flex-col min-w-0">
          <div class="flex items-center justify-between px-5 py-3 border-b border-gray-100 dark:border-gray-700 bg-white dark:bg-[#18181c]">
            <div class="flex items-center gap-2">
              <NInput
                v-model:value="keyword"
                placeholder="搜索Prompt模板..."
                clearable
                class="w-200px"
                size="small"
                @keyup.enter="handleSearch"
              >
                <template #prefix>
                  <icon-carbon:search class="text-gray-400" />
                </template>
              </NInput>
              <NButton size="small" @click="handleSearch">搜索</NButton>
            </div>
            <NButton size="small" type="primary" @click="openCreate">
              <template #icon><icon-carbon:add /></template>
              新建模板
            </NButton>
          </div>

          <NScrollbar class="flex-1">
            <div class="p-4">
              <NSpin :show="loading">
                <div v-if="promptList.length === 0 && !loading" class="py-20">
                  <NEmpty description="暂无Prompt模板">
                    <template #extra>
                      <NButton size="small" type="primary" @click="openCreate">新建模板</NButton>
                    </template>
                  </NEmpty>
                </div>
                <div v-else class="grid grid-cols-1 xl:grid-cols-2 gap-3">
                  <div
                    v-for="item in promptList"
                    :key="item.templateId"
                    class="cursor-pointer rounded-xl border bg-white p-4 transition-all hover:shadow-md dark:bg-[#1e1e22] dark:border-gray-700"
                    :class="selectedPrompt?.templateId === item.templateId
                      ? 'border-primary-400 shadow-sm ring-1 ring-primary-200 dark:border-primary-500 dark:ring-primary-800'
                      : 'border-gray-200 hover:border-primary-300 dark:border-gray-700 dark:hover:border-primary-600'"
                    @click="selectPrompt(item)"
                  >
                    <div class="flex items-start justify-between mb-2">
                      <div class="flex-1 min-w-0">
                        <h3 class="text-sm font-semibold text-gray-900 dark:text-gray-100 truncate">{{ item.name }}</h3>
                        <p v-if="item.description" class="mt-1 text-xs text-gray-500 dark:text-gray-400 line-clamp-2">{{ item.description }}</p>
                      </div>
                      <NTag
                        :type="item.status === '启用' ? 'success' : 'default'"
                        size="small"
                        :bordered="false"
                        class="ml-2 flex-shrink-0"
                      >
                        {{ item.status }}
                      </NTag>
                    </div>

                    <div v-if="getTagList(item.tags).length > 0" class="flex flex-wrap gap-1.5 mb-3">
                      <NTag
                        v-for="tag in getTagList(item.tags).slice(0, 3)"
                        :key="tag"
                        size="small"
                        :bordered="false"
                        type="info"
                      >
                        {{ tag }}
                      </NTag>
                      <NTag v-if="getTagList(item.tags).length > 3" size="small" :bordered="false">
                        +{{ getTagList(item.tags).length - 3 }}
                      </NTag>
                    </div>

                    <div class="flex items-center justify-between text-xs text-gray-400 dark:text-gray-500">
                      <div class="flex items-center gap-3">
                        <span class="flex items-center gap-1">
                          <icon-carbon:folder class="text-sm" />
                          {{ item.category }}
                        </span>
                        <span class="flex items-center gap-1">
                          <icon-carbon:version class="text-sm" />
                          {{ item.version }}
                        </span>
                      </div>
                      <span>{{ formatTime(item.updatedAt, 'MM-DD HH:mm') }}</span>
                    </div>
                  </div>
                </div>
              </NSpin>
            </div>
          </NScrollbar>
        </div>

        <!-- 右侧详情面板 -->
        <div class="w-[380px] flex-shrink-0 flex flex-col bg-white dark:bg-[#18181c] border-l border-gray-200 dark:border-gray-700">
          <template v-if="selectedPrompt">
            <div class="px-5 py-3 border-b border-gray-100 dark:border-gray-700">
              <h2 class="text-sm font-semibold text-gray-800 dark:text-gray-100">模板详情</h2>
            </div>

            <NScrollbar class="flex-1">
              <div class="p-5 space-y-4">
                <div>
                  <h3 class="mb-1 text-base font-semibold text-gray-900 dark:text-gray-50">{{ selectedPrompt.name }}</h3>
                  <p v-if="selectedPrompt.description" class="text-sm text-gray-500 dark:text-gray-400">
                    {{ selectedPrompt.description }}
                  </p>
                </div>

                <div class="flex items-center gap-2">
                  <NTag :type="selectedPrompt.status === '启用' ? 'success' : 'default'" :bordered="false" size="small">
                    {{ selectedPrompt.status }}
                  </NTag>
                  <NTag type="info" :bordered="false" size="small">{{ selectedPrompt.category }}</NTag>
                  <NTag :bordered="false" size="small">{{ selectedPrompt.version }}</NTag>
                </div>

                <div v-if="getTagList(selectedPrompt.tags).length > 0">
                  <div class="mb-1.5 text-xs font-medium text-gray-500 dark:text-gray-400">标签</div>
                  <div class="flex flex-wrap gap-1.5">
                    <NTag v-for="tag in getTagList(selectedPrompt.tags)" :key="tag" size="small" :bordered="false" type="info">
                      {{ tag }}
                    </NTag>
                  </div>
                </div>

                <NDivider class="!my-2" />

                <div v-if="getVarList(selectedPrompt.variables).length > 0">
                  <div class="mb-1.5 text-xs font-medium text-gray-500 dark:text-gray-400">变量</div>
                  <div class="flex flex-wrap gap-1.5">
                    <NTag v-for="v in getVarList(selectedPrompt.variables)" :key="v" size="small" round :bordered="false" type="warning">
                      {{ formatVarName(v) }}
                    </NTag>
                  </div>
                </div>

                <div v-if="selectedPrompt.systemContent">
                  <div class="mb-1.5 text-xs font-medium text-blue-500 dark:text-blue-400">System Prompt</div>
                  <div class="rounded-lg border border-blue-200 bg-blue-50 p-3 dark:border-blue-800 dark:bg-blue-900/10">
                    <pre class="whitespace-pre-wrap text-xs text-gray-800 dark:text-gray-200 font-mono leading-relaxed max-h-200px overflow-y-auto">{{ selectedPrompt.systemContent }}</pre>
                  </div>
                </div>

                <div>
                  <div class="mb-1.5 text-xs font-medium text-green-500 dark:text-green-400">User Prompt</div>
                  <div class="rounded-lg border border-green-200 bg-green-50 p-3 dark:border-green-800 dark:bg-green-900/10">
                    <pre class="whitespace-pre-wrap text-xs text-gray-800 dark:text-gray-200 font-mono leading-relaxed max-h-200px overflow-y-auto">{{ selectedPrompt.content }}</pre>
                  </div>
                </div>

                <NDivider class="!my-2" />

                <div class="space-y-1.5 text-xs text-gray-400 dark:text-gray-500">
                  <div class="flex items-center gap-2">
                    <icon-carbon:time class="text-sm" />
                    <span>创建：{{ formatTime(selectedPrompt.createdAt) }}</span>
                  </div>
                  <div class="flex items-center gap-2">
                    <icon-carbon:update-now class="text-sm" />
                    <span>更新：{{ formatTime(selectedPrompt.updatedAt) }}</span>
                  </div>
                  <div class="flex items-center gap-2">
                    <icon-carbon:identification class="text-sm" />
                    <span>ID：{{ selectedPrompt.templateId }}</span>
                  </div>
                </div>

                <div class="flex flex-wrap gap-2 pt-1">
                  <NButton size="small" type="primary" @click="openEdit(selectedPrompt)">
                    <template #icon><icon-carbon:edit /></template>
                    编辑
                  </NButton>
                  <NButton
                    size="small"
                    secondary
                    :type="selectedPrompt.status === '启用' ? 'warning' : 'success'"
                    @click="toggleStatus(selectedPrompt.templateId)"
                  >
                    <template #icon>
                      <icon-carbon:checkmark-filled v-if="selectedPrompt.status !== '启用'" />
                      <icon-carbon:close-filled v-else />
                    </template>
                    {{ selectedPrompt.status === '启用' ? '禁用' : '启用' }}
                  </NButton>
                  <NButton size="small" secondary type="error" @click="deletePrompt(selectedPrompt.templateId)">
                    <template #icon><icon-carbon:trash-can /></template>
                    删除
                  </NButton>
                </div>
              </div>
            </NScrollbar>
          </template>

          <template v-else>
            <div class="flex flex-1 items-center justify-center">
              <NEmpty description="选择一个模板查看详情">
                <template #icon>
                  <icon-carbon:document class="text-4xl text-gray-300 dark:text-gray-600" />
                </template>
              </NEmpty>
            </div>
          </template>
        </div>
      </div>
    </template>

    <!-- ==================== 编辑视图 ==================== -->
    <template v-else-if="activeView === 'edit'">
      <div class="h-full flex flex-col">
        <!-- 顶部操作栏 -->
        <div class="flex items-center justify-between px-6 py-3 border-b border-gray-200 dark:border-gray-700 bg-white dark:bg-[#18181c]">
          <div class="flex items-center gap-3">
            <NButton text @click="goBackToList">
              <template #icon><icon-carbon:arrow-left class="text-lg" /></template>
              返回列表
            </NButton>
            <NDivider vertical />
            <span class="text-base font-semibold text-gray-800 dark:text-gray-100">
              {{ isEdit ? '编辑Prompt模板' : '新建Prompt模板' }}
            </span>
          </div>
          <div class="flex items-center gap-2">
            <NButton size="small" @click="toggleHistoryPanel">
              <template #icon><icon-carbon:time /></template>
              版本历史
            </NButton>
            <NButton @click="goBackToList">取消</NButton>
            <NButton type="primary" :loading="saving" @click="savePrompt">
              <template #icon><icon-carbon:checkmark /></template>
              {{ isEdit ? '保存修改' : '创建模板' }}
            </NButton>
          </div>
        </div>

        <!-- 编辑主体 -->
        <div class="flex-1 flex min-h-0">
          <!-- 左侧：Prompt 内容编辑 -->
          <div class="flex-1 flex flex-col min-w-0 p-6 gap-4">
            <div class="flex-1 flex flex-col min-h-0">
              <div class="mb-2 flex items-center justify-between">
                <span class="text-sm font-medium text-gray-700 dark:text-gray-300">
                  <icon-carbon:settings-adjust class="mr-1 align-middle" />System Prompt
                </span>
                <span class="text-xs text-gray-400">
                  {{ editForm.systemContent?.length || 0 }} 字符
                </span>
              </div>
              <div class="flex-1 rounded-xl border border-blue-200 dark:border-blue-800 overflow-hidden bg-white dark:bg-[#1e1e22] flex flex-col">
                <div class="px-4 py-1.5 border-b border-blue-100 dark:border-blue-900 bg-blue-50 dark:bg-blue-900/20">
                  <span class="text-xs text-blue-500 dark:text-blue-400">角色设定 · 行为约束 · 通用指令</span>
                </div>
                <textarea
                  v-model="editForm.systemContent"
                  class="flex-1 w-full resize-none p-4 text-sm font-mono leading-relaxed text-gray-800 dark:text-gray-200 bg-transparent outline-none"
                  :placeholder="systemPlaceholder"
                  @blur="syncVariablesFromContent"
                />
              </div>
            </div>

            <div class="flex-1 flex flex-col min-h-0">
              <div class="mb-2 flex items-center justify-between">
                <span class="text-sm font-medium text-gray-700 dark:text-gray-300">
                  <icon-carbon:user class="mr-1 align-middle" />User Prompt
                </span>
                <span class="text-xs text-gray-400">
                  {{ editForm.content?.length || 0 }} 字符
                </span>
              </div>
              <div class="flex-1 rounded-xl border border-green-200 dark:border-green-800 overflow-hidden bg-white dark:bg-[#1e1e22] flex flex-col">
                <div class="px-4 py-1.5 border-b border-green-100 dark:border-green-900 bg-green-50 dark:bg-green-900/20">
                  <span class="text-xs text-green-500 dark:text-green-400">用户消息模板 · 变量占位符</span>
                </div>
                <textarea
                  v-model="editForm.content"
                  class="flex-1 w-full resize-none p-4 text-sm font-mono leading-relaxed text-gray-800 dark:text-gray-200 bg-transparent outline-none"
                  :placeholder="contentPlaceholder"
                  @blur="syncVariablesFromContent"
                />
              </div>
            </div>
          </div>

          <!-- 右侧面板：元信息 或 历史版本 -->
          <div class="w-360px flex-shrink-0 border-l border-gray-200 dark:border-gray-700 bg-white dark:bg-[#18181c] flex flex-col">
            <!-- 元信息面板 -->
            <template v-if="!showHistoryPanel">
              <div class="px-5 py-3 border-b border-gray-100 dark:border-gray-700">
                <span class="text-sm font-semibold text-gray-800 dark:text-gray-100">模板设置</span>
              </div>
              <NScrollbar class="flex-1">
                <div class="p-5 space-y-5">
                  <div>
                    <div class="mb-1.5 text-xs font-medium text-gray-500 dark:text-gray-400">模板名称 <span class="text-red-500">*</span></div>
                    <NInput v-model:value="editForm.name" placeholder="请输入模板名称" />
                  </div>

                  <div>
                    <div class="mb-1.5 text-xs font-medium text-gray-500 dark:text-gray-400">描述</div>
                    <NInput v-model:value="editForm.description" type="textarea" :rows="3" placeholder="简要描述模板用途" />
                  </div>

                  <NDivider class="!my-1" />

                  <div>
                    <div class="mb-1.5 text-xs font-medium text-gray-500 dark:text-gray-400">分类</div>
                    <NSelect
                      v-model:value="editForm.category"
                      :options="categories.map(c => ({ label: c, value: c }))"
                      tag
                      placeholder="选择或输入分类"
                    />
                  </div>

                  <!-- 版本号只读显示 -->
                  <div>
                    <div class="mb-1.5 text-xs font-medium text-gray-500 dark:text-gray-400">版本号</div>
                    <div class="px-3 py-2 rounded-lg border border-gray-200 bg-gray-50 dark:border-gray-700 dark:bg-[#1e1e22] text-sm text-gray-700 dark:text-gray-300 font-mono">
                      {{ editForm.version || 'v1（首次保存自动生成）' }}
                    </div>
                    <div class="mt-1 text-xs text-gray-400 dark:text-gray-500">
                      <icon-carbon:information class="text-xs mr-0.5 align-middle" />版本号由系统自动管理，每次保存后递增，不可手动修改
                    </div>
                  </div>

                  <div>
                    <div class="mb-1.5 text-xs font-medium text-gray-500 dark:text-gray-400">标签</div>
                    <NInput v-model:value="editForm.tags" placeholder="逗号分隔，如：RAG,问答,通用" />
                    <div v-if="getTagList(editForm.tags).length > 0" class="mt-2 flex flex-wrap gap-1.5">
                      <NTag v-for="tag in getTagList(editForm.tags)" :key="tag" size="small" :bordered="false" type="info" closable @close="removeTag(tag)">
                        {{ tag }}
                      </NTag>
                    </div>
                  </div>

                  <div>
                    <div class="mb-1.5 text-xs font-medium text-gray-500 dark:text-gray-400">变量</div>
                    <NInput v-model:value="editForm.variables" placeholder="逗号分隔，如：query,documents,context" />
                    <div v-if="getVarList(editForm.variables).length > 0" class="mt-2 flex flex-wrap gap-1.5">
                      <NTag v-for="v in getVarList(editForm.variables)" :key="v" size="small" round :bordered="false" type="warning">
                        {{ formatVarName(v) }}
                      </NTag>
                    </div>
                  </div>

                  <NDivider class="!my-1" />

                  <!-- 状态 -->
                  <div>
                    <div class="mb-1.5 text-xs font-medium text-gray-500 dark:text-gray-400">状态</div>
                    <div class="flex gap-2">
                      <div
                        class="cursor-pointer rounded-lg border px-4 py-1.5 text-sm transition-all"
                        :class="editForm.status === '启用'
                          ? 'border-green-400 bg-green-50 text-green-600 dark:border-green-500 dark:bg-green-900/20 dark:text-green-400'
                          : 'border-gray-200 text-gray-500 hover:border-green-300 dark:border-gray-600 dark:text-gray-400'"
                        @click="editForm.status = '启用'"
                      >
                        <icon-carbon:checkmark-filled class="mr-1 align-middle" />启用
                      </div>
                      <div
                        class="cursor-pointer rounded-lg border px-4 py-1.5 text-sm transition-all"
                        :class="editForm.status === '禁用'
                          ? 'border-gray-400 bg-gray-50 text-gray-600 dark:border-gray-500 dark:bg-gray-800 dark:text-gray-300'
                          : 'border-gray-200 text-gray-500 hover:border-gray-300 dark:border-gray-600 dark:text-gray-400'"
                        @click="editForm.status = '禁用'"
                      >
                        <icon-carbon:close-filled class="mr-1 align-middle" />禁用
                      </div>
                    </div>
                  </div>

                  <template v-if="isEdit && editForm.templateId">
                    <NDivider class="!my-1" />
                    <div class="space-y-1.5 text-xs text-gray-400 dark:text-gray-500">
                      <div>模板ID：{{ editForm.templateId }}</div>
                      <div>创建时间：{{ formatTime(editForm.createdAt) }}</div>
                      <div>更新时间：{{ formatTime(editForm.updatedAt) }}</div>
                    </div>
                  </template>
                </div>
              </NScrollbar>
            </template>

            <!-- 历史版本面板 -->
            <template v-else>
              <div class="flex items-center justify-between px-5 py-3 border-b border-gray-100 dark:border-gray-700">
                <h2 class="text-sm font-semibold text-gray-800 dark:text-gray-100">版本历史</h2>
                <NButton size="small" @click="showHistoryPanel = false">
                  <template #icon><icon-carbon:settings /></template>
                  返回设置
                </NButton>
              </div>
              <NScrollbar class="flex-1">
                <NSpin :show="historyLoading">
                  <div v-if="historyList.length === 0 && !historyLoading" class="py-10">
                    <NEmpty description="暂无历史版本">
                      <template #icon>
                        <icon-carbon:time class="text-3xl text-gray-300 dark:text-gray-600" />
                      </template>
                    </NEmpty>
                  </div>
                  <div v-else class="p-4 space-y-2">
                    <div
                      v-for="h in historyList"
                      :key="h.id"
                      class="rounded-lg border p-3 transition-all cursor-pointer hover:shadow-sm"
                      :class="selectedHistory?.id === h.id
                        ? 'border-primary-400 bg-primary-50 dark:border-primary-500 dark:bg-primary-900/10 ring-1 ring-primary-200 dark:ring-primary-800'
                        : 'border-gray-200 bg-white dark:border-gray-700 dark:bg-[#1e1e22] hover:border-primary-300 dark:hover:border-primary-600'"
                      @click="selectHistory(h)"
                    >
                      <!-- 版本号 & 时间 -->
                      <div class="flex items-center justify-between mb-1.5">
                        <NTag size="tiny" :bordered="false" type="info">{{ h.version }}</NTag>
                        <span class="text-xs text-gray-400 dark:text-gray-500 min-w-0 text-right">{{ formatTime(h.snapshotAt) }}</span>
                      </div>

                      <!-- 变更说明 -->
                      <div v-if="h.changeDescription" class="mb-1.5">
                        <span class="text-xs text-orange-500 dark:text-orange-400">
                          <icon-carbon:edit class="mr-0.5 align-middle text-xs" />{{ h.changeDescription }}
                        </span>
                      </div>

                      <!-- 名称和描述 -->
                      <div class="mb-2">
                        <div class="text-xs font-medium text-gray-800 dark:text-gray-100 line-clamp-1">{{ h.name }}</div>
                        <div v-if="h.description" class="text-xs text-gray-400 dark:text-gray-500 line-clamp-1">{{ h.description }}</div>
                      </div>

                      <!-- 操作人和分类信息 -->
                      <div class="flex items-center justify-between mb-2">
                        <span class="text-xs text-gray-400 dark:text-gray-500">
                          {{ h.category }} · {{ h.status }}
                        </span>
                        <span v-if="h.snapshotBy" class="text-xs text-gray-400 dark:text-gray-500">
                          <icon-carbon:user-avatar class="mr-0.5 align-middle text-xs" />{{ h.snapshotBy }}
                        </span>
                      </div>

                      <!-- 操作按钮 -->
                      <div class="flex items-center justify-end gap-1.5">
                        <NTooltip>
                          <template #trigger>
                            <NButton size="tiny" type="primary" ghost @click.stop="editFromHistory(h)">
                              <template #icon><icon-carbon:edit /></template>
                              编辑
                            </NButton>
                          </template>
                          基于此版本内容编辑，版本号将自动递增
                        </NTooltip>
                        <NButton size="tiny" type="warning" ghost @click.stop="handleRollback(h)">
                              <template #icon><icon-carbon:undo /></template>
                              回滚
                            </NButton>
                      </div>

                      <!-- 展开预览 -->
                      <div v-if="selectedHistory?.id === h.id" class="mt-3 space-y-2">
                        <NDivider class="!my-2" />

                        <!-- 变量预览 -->
                        <div v-if="getVarList(h.variables).length > 0">
                          <div class="mb-1 text-xs font-medium text-amber-500 dark:text-amber-400">变量</div>
                          <div class="flex flex-wrap gap-1">
                            <NTag v-for="v in getVarList(h.variables)" :key="v" size="tiny" round :bordered="false" type="warning">
                              {{ formatVarName(v) }}
                            </NTag>
                          </div>
                        </div>

                        <div v-if="h.systemContent">
                          <div class="mb-1 text-xs font-medium text-blue-500 dark:text-blue-400">System Prompt</div>
                          <div class="rounded border border-blue-200 bg-blue-50 p-2 dark:border-blue-800 dark:bg-blue-900/10">
                            <pre class="whitespace-pre-wrap text-xs text-gray-800 dark:text-gray-200 font-mono max-h-120px overflow-y-auto">{{ h.systemContent }}</pre>
                          </div>
                        </div>
                        <div v-if="h.content">
                          <div class="mb-1 text-xs font-medium text-green-500 dark:text-green-400">User Prompt</div>
                          <div class="rounded border border-green-200 bg-green-50 p-2 dark:border-green-800 dark:bg-green-900/10">
                            <pre class="whitespace-pre-wrap text-xs text-gray-800 dark:text-gray-200 font-mono max-h-120px overflow-y-auto">{{ h.content }}</pre>
                          </div>
                        </div>

                        <!-- 展开后的操作 -->
                        <div class="flex justify-center pt-1">
                          <NButton size="small" type="primary" @click.stop="editFromHistory(h)">
                            <template #icon><icon-carbon:edit /></template>
                            基于此版本编辑
                          </NButton>
                        </div>
                      </div>
                    </div>
                  </div>
                </NSpin>
              </NScrollbar>
            </template>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.line-clamp-1 {
  display: -webkit-box;
  -webkit-line-clamp: 1;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.line-clamp-2 {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
</style>

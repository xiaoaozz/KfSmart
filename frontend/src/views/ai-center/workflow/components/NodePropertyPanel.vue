<script setup lang="ts">
import { NButton, NInput, NInputNumber, NSelect } from 'naive-ui';
import type { WorkflowNode } from '../types/workflow';

defineProps<{
  node: WorkflowNode;
  modelOptions: { label: string; value: string }[];
  knowledgeBaseOptions: { label: string; value: string }[];
  promptOptions: { label: string; value: string }[];
  mcpToolOptions: { label: string; value: string }[];
}>();

const emit = defineEmits<{ delete: [] }>();

function emptyOptions(label: string) {
  return [{ label, value: '', disabled: true }];
}

defineExpose({ emptyOptions });
</script>

<template>
  <div class="space-y-3">
    <!-- 通用属性 -->
    <div class="border-b border-gray-100 pb-3 dark:border-gray-700">
      <div class="mb-2 text-xs font-medium text-gray-500 uppercase tracking-wide">基本信息</div>
      <NInput v-model:value="node.name" class="mb-2" placeholder="节点名称" />
      <NInput :value="node.id" disabled class="mb-2" placeholder="节点ID" />
      <NInput v-model:value="node.description" type="textarea" :rows="2" placeholder="节点描述（可选）" />
    </div>

    <div class="border-b border-gray-100 pb-3 dark:border-gray-700">
      <div class="mb-2 text-xs font-medium text-gray-500 uppercase tracking-wide">位置</div>
      <div class="grid grid-cols-2 gap-2">
        <NInputNumber v-model:value="node.x" :min="0" class="w-full" placeholder="X" />
        <NInputNumber v-model:value="node.y" :min="0" class="w-full" placeholder="Y" />
      </div>
    </div>

    <!-- 开始节点 -->
    <template v-if="node.type === '开始'">
      <div class="mb-2 text-xs font-medium text-gray-500 uppercase tracking-wide">触发配置</div>
      <NSelect v-model:value="node.config!.triggerType" :options="['手动触发', '定时触发', 'API触发', 'Webhook触发'].map(v => ({ label: v, value: v }))" placeholder="触发方式" />
      <NInput v-model:value="node.config!.inputSchema" type="textarea" :rows="3" placeholder="输入参数 Schema（JSON）" />
      <div class="flex items-center gap-2">
        <span class="text-sm text-gray-600 dark:text-gray-400 shrink-0">超时(秒)</span>
        <NInputNumber v-model:value="node.config!.timeout" :min="1" :max="3600" class="flex-1" />
      </div>
    </template>

    <!-- 结束节点 -->
    <template v-if="node.type === '结束'">
      <div class="mb-2 text-xs font-medium text-gray-500 uppercase tracking-wide">输出配置</div>
      <NSelect v-model:value="node.config!.outputMode" :options="['直接输出', '模板渲染', '变量映射'].map(v => ({ label: v, value: v }))" placeholder="输出模式" />
      <NInput v-model:value="node.config!.outputTemplate" type="textarea" :rows="3" placeholder="输出模板，支持 {{变量}} 语法" />
    </template>

    <!-- 变量节点 -->
    <template v-if="node.type === '变量'">
      <div class="mb-2 text-xs font-medium text-gray-500 uppercase tracking-wide">变量配置</div>
      <NInput v-model:value="node.config!.varName" placeholder="变量名称" />
      <NSelect v-model:value="node.config!.varType" :options="['string', 'number', 'boolean', 'array', 'object'].map(v => ({ label: v, value: v }))" placeholder="变量类型" />
      <NInput v-model:value="node.config!.varValue" type="textarea" :rows="2" placeholder="默认值" />
      <NSelect v-model:value="node.config!.scope" :options="['全局', '流程内', '当前节点'].map(v => ({ label: v, value: v }))" placeholder="作用域" />
    </template>

    <!-- 条件判断 -->
    <template v-if="node.type === '条件判断'">
      <div class="mb-2 text-xs font-medium text-gray-500 uppercase tracking-wide">条件配置</div>
      <NInput v-model:value="node.config!.conditionExpr" type="textarea" :rows="2" placeholder="条件表达式，如：{{input.score}} >= 60" />
      <div class="grid grid-cols-2 gap-2">
        <NInput v-model:value="node.config!.trueLabel" placeholder="是（True）分支名" />
        <NInput v-model:value="node.config!.falseLabel" placeholder="否（False）分支名" />
      </div>
    </template>

    <!-- LLM 节点 -->
    <template v-if="node.type === 'LLM'">
      <div class="mb-2 text-xs font-medium text-gray-500 uppercase tracking-wide">模型配置</div>
      <NSelect v-model:value="node.config!.model" clearable :options="modelOptions.length ? modelOptions : emptyOptions('暂无可用模型')" placeholder="选择模型" />
      <div class="flex items-center gap-2">
        <span class="text-sm text-gray-600 dark:text-gray-400 shrink-0 w-24">Temperature</span>
        <NInputNumber v-model:value="node.config!.temperature" :min="0" :max="2" :step="0.1" :precision="1" class="flex-1" />
      </div>
      <div class="flex items-center gap-2">
        <span class="text-sm text-gray-600 dark:text-gray-400 shrink-0 w-24">最大Token数</span>
        <NInputNumber v-model:value="node.config!.maxTokens" :min="1" :max="32768" class="flex-1" />
      </div>
      <div class="flex items-center gap-2">
        <span class="text-sm text-gray-600 dark:text-gray-400 shrink-0 w-24">Top P</span>
        <NInputNumber v-model:value="node.config!.topP" :min="0" :max="1" :step="0.05" :precision="2" class="flex-1" />
      </div>
      <NInput v-model:value="node.config!.systemPrompt" type="textarea" :rows="4" placeholder="系统提示词" />
    </template>

    <!-- 知识库检索 -->
    <template v-if="node.type === '知识库检索'">
      <div class="mb-2 text-xs font-medium text-gray-500 uppercase tracking-wide">检索配置</div>
      <NSelect v-model:value="node.config!.knowledgeBase" clearable filterable :options="knowledgeBaseOptions.length ? knowledgeBaseOptions : emptyOptions('暂无知识库')" placeholder="选择知识库" />
      <NSelect v-model:value="node.config!.searchMode" :options="['向量检索', '关键词检索', '混合检索'].map(v => ({ label: v, value: v }))" placeholder="检索模式" />
      <div class="flex items-center gap-2">
        <span class="text-sm text-gray-600 dark:text-gray-400 shrink-0 w-24">TopK</span>
        <NInputNumber v-model:value="node.config!.topK" :min="1" :max="20" class="flex-1" />
      </div>
      <div class="flex items-center gap-2">
        <span class="text-sm text-gray-600 dark:text-gray-400 shrink-0 w-24">相似度阈值</span>
        <NInputNumber v-model:value="node.config!.scoreThreshold" :min="0" :max="1" :step="0.05" :precision="2" class="flex-1" />
      </div>
    </template>

    <!-- Prompt模板 -->
    <template v-if="node.type === 'Prompt模板'">
      <div class="mb-2 text-xs font-medium text-gray-500 uppercase tracking-wide">Prompt 配置</div>
      <NSelect v-model:value="node.config!.templateId" clearable filterable :options="promptOptions.length ? promptOptions : emptyOptions('暂无Prompt模板')" placeholder="选择模板" />
      <NInput v-model:value="node.config!.templateContent" type="textarea" :rows="5" placeholder="Prompt 内容" />
    </template>

    <!-- MCP工具 -->
    <template v-if="node.type === 'MCP工具'">
      <div class="mb-2 text-xs font-medium text-gray-500 uppercase tracking-wide">工具配置</div>
      <NSelect v-model:value="node.config!.toolId" clearable filterable :options="mcpToolOptions.length ? mcpToolOptions : emptyOptions('暂无MCP工具')" placeholder="选择 MCP 工具" />
      <NInput v-model:value="node.config!.inputMapping" type="textarea" :rows="2" placeholder="输入参数映射（JSON）" />
      <NInput v-model:value="node.config!.outputField" placeholder="输出字段名" />
    </template>

    <!-- HTTP请求 -->
    <template v-if="node.type === 'HTTP请求'">
      <div class="mb-2 text-xs font-medium text-gray-500 uppercase tracking-wide">HTTP 配置</div>
      <div class="flex gap-2">
        <NSelect v-model:value="node.config!.method" class="w-28 shrink-0" :options="['GET', 'POST', 'PUT', 'PATCH', 'DELETE'].map(v => ({ label: v, value: v }))" />
        <NInput v-model:value="node.config!.url" class="flex-1" placeholder="请求 URL" />
      </div>
      <NInput v-model:value="node.config!.headers" type="textarea" :rows="2" placeholder="请求头（JSON）" />
      <NInput v-model:value="node.config!.body" type="textarea" :rows="3" placeholder="请求体" />
      <NSelect v-model:value="node.config!.authType" :options="['none', 'Bearer Token', 'Basic Auth', 'API Key'].map(v => ({ label: v, value: v }))" placeholder="认证方式" />
    </template>

    <!-- SQL查询 -->
    <template v-if="node.type === 'SQL查询'">
      <div class="mb-2 text-xs font-medium text-gray-500 uppercase tracking-wide">SQL 配置</div>
      <NInput v-model:value="node.config!.datasource" placeholder="数据源名称" />
      <NInput v-model:value="node.config!.sql" type="textarea" :rows="5" placeholder="SQL 语句" style="font-family: monospace; font-size: 12px;" />
      <NSelect v-model:value="node.config!.resultType" :options="['列表', '单行', '单值', '影响行数'].map(v => ({ label: v, value: v }))" placeholder="结果类型" />
    </template>

    <!-- 代码执行/Python -->
    <template v-if="node.type === '代码执行' || node.type === 'Python执行'">
      <div class="mb-2 text-xs font-medium text-gray-500 uppercase tracking-wide">代码配置</div>
      <NSelect v-if="node.type === '代码执行'" v-model:value="node.config!.language" :options="['JavaScript', 'TypeScript', 'Python', 'Shell'].map(v => ({ label: v, value: v }))" placeholder="执行语言" />
      <NInput v-model:value="node.config!.code" type="textarea" :rows="7" placeholder="代码内容" style="font-family: monospace; font-size: 12px;" />
    </template>

    <!-- 审批 -->
    <template v-if="node.type === '审批'">
      <div class="mb-2 text-xs font-medium text-gray-500 uppercase tracking-wide">审批配置</div>
      <NInput v-model:value="node.config!.approvers" placeholder="审批人" />
      <NSelect v-model:value="node.config!.approvalType" :options="['任一审批', '全部审批', '顺序审批'].map(v => ({ label: v, value: v }))" placeholder="审批类型" />
    </template>

    <!-- 消息/通知节点通用 -->
    <template v-if="['消息通知', '邮件发送', 'Webhook', '飞书通知', '企业微信通知'].includes(node.type)">
      <div class="mb-2 text-xs font-medium text-gray-500 uppercase tracking-wide">{{ node.type }} 配置</div>
      <template v-if="node.config!.webhookUrl !== undefined">
        <NInput v-model:value="node.config!.webhookUrl" placeholder="Webhook URL" />
      </template>
      <template v-if="node.config!.recipients !== undefined">
        <NInput v-model:value="node.config!.recipients" placeholder="接收人" />
      </template>
      <template v-if="node.config!.to !== undefined">
        <NInput v-model:value="node.config!.to" placeholder="收件人" />
        <NInput v-model:value="node.config!.subject" placeholder="主题" />
      </template>
      <NInput v-if="node.config!.content !== undefined" v-model:value="node.config!.content" type="textarea" :rows="3" placeholder="内容（支持 {{变量}}）" />
    </template>

    <!-- 删除按钮 -->
    <div class="pt-2">
      <NButton size="tiny" type="error" ghost block @click="emit('delete')">删除节点</NButton>
    </div>
  </div>
</template>

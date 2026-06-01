<script setup lang="ts">
import { computed, onMounted } from 'vue';
import type { Component } from 'vue';
import { mixColor } from '@sa/color';
import { loginModuleRecord } from '@/constants/app';
import { useAppStore } from '@/store/modules/app';
import { useThemeStore } from '@/store/modules/theme';
import { $t } from '@/locales';
import systemLogoUrl from '@/assets/svg-icon/logo.svg';
import PwdLogin from './modules/pwd-login.vue';
import CodeLogin from './modules/code-login.vue';
import Register from './modules/register.vue';
import ResetPwd from './modules/reset-pwd.vue';
import BindWechat from './modules/bind-wechat.vue';

interface Props {
  /** The login module */
  module?: UnionKey.LoginModule;
}

const props = defineProps<Props>();

const appStore = useAppStore();
const themeStore = useThemeStore();

interface LoginModule {
  label: string;
  component: Component;
}

const moduleMap: Record<UnionKey.LoginModule, LoginModule> = {
  'pwd-login': { label: loginModuleRecord['pwd-login'], component: PwdLogin },
  'code-login': { label: loginModuleRecord['code-login'], component: CodeLogin },
  register: { label: loginModuleRecord.register, component: Register },
  'reset-pwd': { label: loginModuleRecord['reset-pwd'], component: ResetPwd },
  'bind-wechat': { label: loginModuleRecord['bind-wechat'], component: BindWechat }
};

const activeModule = computed(() => moduleMap[props.module || 'pwd-login']);

const bgColor = computed(() => {
  const ratio = themeStore.darkMode ? 0.5 : 0;
  return mixColor('#fff', '#000', ratio);
});


// 左侧功能特性数据
const features = [
  {
    icon: 'document',
    title: '智能文档处理',
    description: '解析、清洗、理解',
    bgColor: 'rgba(96, 165, 250, 0.15)'
  },
  {
    icon: 'search',
    title: '企业级搜索',
    description: '语义检索、精准召回',
    bgColor: 'rgba(168, 85, 247, 0.15)'
  },
  {
    icon: 'chat',
    title: 'RAG 增强生成',
    description: '知识驱动、可信回答',
    bgColor: 'rgba(34, 211, 238, 0.15)'
  },
  {
    icon: 'users',
    title: '多租户协作',
    description: '权限隔离、协同共享',
    bgColor: 'rgba(99, 102, 241, 0.15)'
  }
];

// 技术栈
const techStack = [
  { name: 'ElasticSearch', color: '#20B2AA' },
  { name: 'Kafka', color: '#000000' },
  { name: 'Redis', color: '#DC382D' },
  { name: 'MySQL', color: '#00758F' }
];

onMounted(() => {
  // 初始化动画
});
</script>

<template>
  <div class="login-page relative min-h-screen overflow-hidden">
    <!-- 背景装饰点 -->
    <div class="absolute inset-0 overflow-hidden pointer-events-none">
      <div class="floating-dot dot-1"></div>
      <div class="floating-dot dot-2"></div>
      <div class="floating-dot dot-3"></div>
      <div class="floating-dot dot-4"></div>
      <div class="floating-dot dot-5"></div>
      <div class="floating-dot dot-6"></div>
    </div>

    <!-- 主容器 -->
    <div class="relative z-10 min-h-screen flex">
      <!-- 左侧：产品展示区 -->
      <div class="left-section flex-1 px-8 lg:px-12 py-8 flex flex-col">
        <!-- Logo 和标题 -->
        <div class="flex items-center gap-2.5 mb-8">
          <div class="w-28px h-28px flex items-center justify-center flex-shrink-0">
            <img :src="systemLogoUrl" alt="KnowFlow logo" class="login-top-logo">
          </div>
          <h1 class="text-2xl font-bold leading-none text-gray-900 dark:text-white">
            KnowFlow
          </h1>
        </div>

        <!-- 主标题 - 居中 -->
        <div class="mb-8 text-center">
          <h2 class="text-3xl lg:text-4xl font-bold text-gray-900 dark:text-white mb-3 leading-tight">
            企业级 <span class="text-primary">AI</span> 知识库管理系统
          </h2>
          <p class="text-gray-600 dark:text-gray-400 text-base">
            智能文档处理、检索增强生成、多租户协作
          </p>
        </div>

        <!-- 特性卡片 - 横向排列 -->
        <div class="features-row grid grid-cols-4 gap-4 mb-8">
          <div
            v-for="feature in features"
            :key="feature.title"
            class="feature-card text-center transition-all duration-300 hover:-translate-y-1"
          >
            <div
              class="w-14 h-14 mx-auto rounded-2xl flex items-center justify-center mb-3"
              :style="{ backgroundColor: feature.bgColor }"
            >
              <icon-carbon-document v-if="feature.icon === 'document'" class="text-blue-600 text-2xl" />
              <icon-carbon-search v-else-if="feature.icon === 'search'" class="text-purple-600 text-2xl" />
              <icon-carbon-chat v-else-if="feature.icon === 'chat'" class="text-cyan-600 text-2xl" />
              <icon-carbon-user-multiple v-else-if="feature.icon === 'users'" class="text-indigo-600 text-2xl" />
            </div>
            <h3 class="text-sm font-bold text-gray-900 dark:text-white mb-1">
              {{ feature.title }}
            </h3>
            <p class="text-xs text-gray-600 dark:text-gray-400">
              {{ feature.description }}
            </p>
          </div>
        </div>

        <!-- 中央3D展示区 -->
        <div class="central-area flex-1 relative min-h-460px mb-6">
          <!-- 背景连接线 SVG -->
          <svg class="absolute inset-0 w-full h-full pointer-events-none" style="z-index: 1">
            <defs>
              <pattern id="dotPattern" patternUnits="userSpaceOnUse" width="4" height="4">
                <circle cx="2" cy="2" r="1" fill="#a5b4fc" opacity="0.4" />
              </pattern>
            </defs>
            <!-- 连接圆环虚线 -->
            <circle cx="50%" cy="50%" r="200" fill="none" stroke="#c7d2fe" stroke-width="1" stroke-dasharray="3,5" opacity="0.5" />
            <circle cx="50%" cy="50%" r="240" fill="none" stroke="#c7d2fe" stroke-width="1" stroke-dasharray="3,5" opacity="0.3" />
          </svg>

          <!-- 中央大Logo + 圆形底座 -->
          <div class="absolute top-1/2 left-1/2 transform -translate-x-1/2 -translate-y-1/2 logo-3d-container">
            <!-- 光晕 -->
            <div class="logo-3d-shadow"></div>
            <!-- 底座光环 -->
            <div class="podium">
              <div class="podium-ring ring-1"></div>
              <div class="podium-ring ring-2"></div>
              <div class="podium-ring ring-3"></div>
            </div>
            <img :src="systemLogoUrl" alt="KnowFlow logo" class="logo-3d" />
          </div>

          <!-- 顶部中央：企业级搜索 -->
          <div class="floating-card absolute top-0 left-1/2 transform -translate-x-1/2">
            <div class="p-3 bg-white/95 dark:bg-gray-800/95 backdrop-blur-md rounded-xl shadow-lg border border-gray-200/50 w-220px">
              <div class="text-xs font-medium text-gray-900 dark:text-white mb-2">企业级搜索</div>
              <div class="flex items-center gap-2 px-2.5 py-1.5 bg-gray-50 dark:bg-gray-700 rounded-lg">
                <span class="flex-1 text-xs text-gray-400">请输入关键词进行搜索...</span>
                <icon-carbon-search class="text-purple-500 text-sm" />
              </div>
            </div>
          </div>

          <!-- 浮动卡片 - 左上：文档解析 -->
          <div class="floating-card absolute top-16 left-2 lg:left-6">
            <div class="p-3 bg-white/95 dark:bg-gray-800/95 backdrop-blur-md rounded-xl shadow-lg border border-gray-200/50 w-130px">
              <div class="text-xs font-semibold text-gray-900 dark:text-white mb-2">文档解析</div>
              <div class="bg-gray-50 dark:bg-gray-700 rounded-md p-2">
                <div class="inline-block px-1.5 py-0.5 bg-blue-100 text-blue-600 text-[10px] rounded mb-1.5 font-medium">DOCX</div>
                <div class="space-y-1">
                  <div class="h-1 bg-gray-200 rounded-full w-full"></div>
                  <div class="h-1 bg-gray-200 rounded-full w-4/5"></div>
                  <div class="h-1 bg-gray-200 rounded-full w-3/5"></div>
                </div>
              </div>
            </div>
          </div>

          <!-- 浮动卡片 - 右上：知识图谱 -->
          <div class="floating-card absolute top-16 right-2 lg:right-6">
            <div class="p-3 bg-white/95 dark:bg-gray-800/95 backdrop-blur-md rounded-xl shadow-lg border border-gray-200/50 w-150px">
              <div class="text-xs font-semibold text-gray-900 dark:text-white mb-2">知识图谱</div>
              <svg viewBox="0 0 130 70" class="w-full h-12">
                <!-- 连接线 -->
                <line x1="20" y1="20" x2="60" y2="15" stroke="#c7d2fe" stroke-width="1" stroke-dasharray="2,2" />
                <line x1="60" y1="15" x2="100" y2="25" stroke="#c7d2fe" stroke-width="1" stroke-dasharray="2,2" />
                <line x1="20" y1="20" x2="40" y2="50" stroke="#c7d2fe" stroke-width="1" stroke-dasharray="2,2" />
                <line x1="40" y1="50" x2="80" y2="55" stroke="#c7d2fe" stroke-width="1" stroke-dasharray="2,2" />
                <line x1="80" y1="55" x2="100" y2="25" stroke="#c7d2fe" stroke-width="1" stroke-dasharray="2,2" />
                <line x1="60" y1="15" x2="80" y2="55" stroke="#c7d2fe" stroke-width="1" stroke-dasharray="2,2" />
                <!-- 节点 -->
                <circle cx="20" cy="20" r="5" fill="#818cf8" />
                <circle cx="60" cy="15" r="6" fill="#6366f1" />
                <circle cx="100" cy="25" r="5" fill="#a78bfa" />
                <circle cx="40" cy="50" r="4" fill="#22d3ee" />
                <circle cx="80" cy="55" r="6" fill="#10b981" />
              </svg>
            </div>
          </div>

          <!-- 浮动卡片 - 左下：对话问答 -->
          <div class="floating-card absolute bottom-12 left-2 lg:left-6">
            <div class="p-3 bg-white/95 dark:bg-gray-800/95 backdrop-blur-md rounded-xl shadow-lg border border-gray-200/50 w-200px">
              <div class="text-xs font-semibold text-gray-900 dark:text-white mb-2">对话问答</div>
              <div class="flex items-start gap-1.5 mb-2">
                <div class="w-5 h-5 rounded-md bg-blue-500 flex items-center justify-center flex-shrink-0 mt-0.5">
                  <icon-carbon-chat class="text-white text-[10px]" />
                </div>
                <div class="bg-blue-50 rounded-lg px-2 py-1.5 text-[11px] text-gray-700 leading-snug">
                  根据公司年假政策,<br />入职不满半年有年假吗？
                </div>
              </div>
              <div class="bg-gray-50 rounded-lg px-2 py-1.5 text-[11px] text-gray-700 leading-snug ml-6">
                根据企业年假政策，入职不满半年，暂无年假。
              </div>
            </div>
          </div>

          <!-- 浮动卡片 - 右下：检索增强生成 (RAG) -->
          <div class="floating-card absolute bottom-12 right-2 lg:right-6">
            <div class="p-3 bg-white/95 dark:bg-gray-800/95 backdrop-blur-md rounded-xl shadow-lg border border-gray-200/50 w-180px">
              <div class="flex items-center justify-between mb-2">
                <div class="text-xs font-semibold text-gray-900 dark:text-white">检索增强生成（RAG）</div>
              </div>
              <div class="space-y-1 mb-2">
                <div class="flex items-center gap-1.5 text-[11px] text-gray-700 bg-gray-50 px-2 py-1 rounded-md">
                  <icon-carbon-document class="text-blue-500 text-xs flex-shrink-0" />
                  <span>相关文档 1</span>
                </div>
                <div class="flex items-center gap-1.5 text-[11px] text-gray-700 bg-gray-50 px-2 py-1 rounded-md">
                  <icon-carbon-document class="text-blue-500 text-xs flex-shrink-0" />
                  <span>相关文档 2</span>
                </div>
                <div class="flex items-center gap-1.5 text-[11px] text-gray-700 bg-gray-50 px-2 py-1 rounded-md">
                  <icon-carbon-document class="text-blue-500 text-xs flex-shrink-0" />
                  <span>相关文档 3</span>
                </div>
              </div>
              <div class="absolute -bottom-2 -right-2 w-9 h-9 rounded-full bg-gradient-to-br from-blue-400 to-blue-600 flex items-center justify-center shadow-lg">
                <icon-carbon-checkmark class="text-white text-base" />
              </div>
            </div>
          </div>

          <!-- 小连接图标：左侧文档图标 -->
          <div class="connector-icon absolute top-1/2 left-1/4 transform -translate-y-1/2">
            <div class="w-8 h-8 rounded-full bg-gradient-to-br from-cyan-400 to-cyan-600 flex items-center justify-center shadow-md">
              <icon-carbon-document class="text-white text-sm" />
            </div>
          </div>

          <!-- 小连接图标：右侧用户图标 -->
          <div class="connector-icon absolute top-1/2 right-1/4 transform -translate-y-1/2">
            <div class="w-8 h-8 rounded-full bg-gradient-to-br from-indigo-400 to-indigo-600 flex items-center justify-center shadow-md">
              <icon-carbon-user-multiple class="text-white text-sm" />
            </div>
          </div>

          <!-- 小连接图标：底部对话图标 -->
          <div class="connector-icon absolute bottom-0 left-1/2 transform -translate-x-1/2">
            <div class="w-9 h-9 rounded-full bg-gradient-to-br from-purple-400 to-purple-600 flex items-center justify-center shadow-md">
              <icon-carbon-chat class="text-white text-base" />
            </div>
          </div>
        </div>

        <!-- 底部技术栈 -->
        <div class="mt-auto">
          <p class="text-xs text-gray-500 dark:text-gray-400 mb-3">基于成熟可靠的技术栈构建</p>
          <div class="flex items-center gap-6">
            <div
              v-for="tech in techStack"
              :key="tech.name"
              class="flex items-center gap-2 group cursor-pointer"
            >
              <div class="w-5 h-5 rounded flex items-center justify-center" :style="{ backgroundColor: tech.color + '20' }">
                <div class="w-2.5 h-2.5 rounded" :style="{ backgroundColor: tech.color }"></div>
              </div>
              <span class="text-sm text-gray-600 dark:text-gray-400 group-hover:text-gray-900 dark:group-hover:text-white transition-colors">
                {{ tech.name }}
              </span>
            </div>
          </div>
        </div>
      </div>

      <!-- 右侧：登录表单区 -->
      <div class="right-section w-full lg:w-520px xl:w-600px flex items-center justify-center p-6 bg-white/50 dark:bg-gray-900/50 backdrop-blur-sm border-l border-gray-200/50">
        <div class="w-full max-w-440px">
          <!-- 玻璃态卡片 -->
          <div class="login-card bg-white/95 dark:bg-gray-900/95 backdrop-blur-xl rounded-3xl p-6 lg:p-8 shadow-xl border border-gray-200/50 dark:border-gray-700/50">
            <!-- 头部 - 只在移动端显示Logo -->
            <div class="flex items-center justify-end mb-4 lg:hidden">
              <div class="flex items-center gap-3 mr-auto">
                <SystemLogo class="text-32px text-primary" />
                <h2 class="text-lg font-700 text-primary">KnowFlow</h2>
              </div>
            </div>

            <!-- 语言切换 - 桌面端右上角 -->
            <div class="hidden lg:flex justify-end mb-4">
              <LangSwitch
                v-if="themeStore.header.multilingual.visible"
                :lang="appStore.locale"
                :lang-options="appStore.localeOptions"
                :show-tooltip="false"
                @change-lang="appStore.changeLocale"
              />
            </div>

            <!-- 表单标题 -->
            <div class="mb-5">
              <h3 class="text-xl lg:text-2xl font-700 text-gray-900 dark:text-white mb-1.5">
                欢迎登录
              </h3>
              <p class="text-sm text-gray-600 dark:text-gray-400">
                登录 KnowFlow，开启智能知识管理之旅
              </p>
            </div>

            <!-- 登录表单 -->
            <div class="login-form-container">
              <Transition :name="themeStore.page.animateMode" mode="out-in" appear>
                <component :is="activeModule.component" />
              </Transition>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.login-page {
  min-height: 100vh;
  background: linear-gradient(135deg, #e8f0fe 0%, #f0f4ff 50%, #e8f2ff 100%);
  position: relative;
}

// 浮动装饰点
.floating-dot {
  position: absolute;
  border-radius: 50%;
  animation: float 20s infinite ease-in-out;

  &.dot-1 {
    width: 8px;
    height: 8px;
    top: 15%;
    left: 20%;
    background: #60a5fa;
    animation-delay: 0s;
  }

  &.dot-2 {
    width: 12px;
    height: 12px;
    top: 40%;
    left: 15%;
    background: #a78bfa;
    animation-delay: -3s;
  }

  &.dot-3 {
    width: 6px;
    height: 6px;
    top: 60%;
    left: 25%;
    background: #22d3ee;
    animation-delay: -6s;
  }

  &.dot-4 {
    width: 10px;
    height: 10px;
    top: 25%;
    right: 30%;
    background: #818cf8;
    animation-delay: -9s;
  }

  &.dot-5 {
    width: 8px;
    height: 8px;
    top: 70%;
    right: 25%;
    background: #60a5fa;
    animation-delay: -12s;
  }

  &.dot-6 {
    width: 12px;
    height: 12px;
    bottom: 20%;
    left: 30%;
    background: #c084fc;
    animation-delay: -15s;
  }
}

@keyframes float {
  0%, 100% {
    transform: translate(0, 0);
    opacity: 0.3;
  }
  25% {
    transform: translate(20px, -20px);
    opacity: 0.6;
  }
  50% {
    transform: translate(-15px, 15px);
    opacity: 0.4;
  }
  75% {
    transform: translate(15px, 10px);
    opacity: 0.5;
  }
}

// 左侧区域
.left-section {
  position: relative;
  overflow: visible;
}

// 横向特性卡片 - 简洁居中风格（无背景卡片）
.features-row {
  .feature-card {
    transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);

    &:hover {
      transform: translateY(-4px);
    }
  }
}

// 中央展示区
.central-area {
  position: relative;
}

// 3D Logo 效果
.logo-3d-container {
  position: relative;
  z-index: 5;
  width: 280px;
  height: 280px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.logo-3d-shadow {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  width: 320px;
  height: 320px;
  background: radial-gradient(circle, rgba(96, 165, 250, 0.35) 0%, rgba(147, 197, 253, 0.2) 40%, transparent 70%);
  border-radius: 50%;
  filter: blur(50px);
  animation: pulse 4s ease-in-out infinite;
  z-index: 1;
}

// 圆形底座 (光环)
.podium {
  position: absolute;
  top: 65%;
  left: 50%;
  transform: translate(-50%, -50%);
  width: 280px;
  height: 80px;
  z-index: 2;
  pointer-events: none;
}

.podium-ring {
  position: absolute;
  left: 50%;
  top: 50%;
  border-radius: 50%;
  border: 1.5px solid rgba(96, 165, 250, 0.4);
  transform: translate(-50%, -50%);

  &.ring-1 {
    width: 280px;
    height: 60px;
    border-color: rgba(96, 165, 250, 0.5);
    box-shadow: 0 0 30px rgba(96, 165, 250, 0.3);
    background: radial-gradient(ellipse at center, rgba(96, 165, 250, 0.15) 0%, transparent 70%);
  }

  &.ring-2 {
    width: 220px;
    height: 45px;
    border-color: rgba(147, 197, 253, 0.4);
    background: radial-gradient(ellipse at center, rgba(147, 197, 253, 0.12) 0%, transparent 60%);
    animation: ringPulse 3s ease-in-out infinite;
  }

  &.ring-3 {
    width: 160px;
    height: 30px;
    border-color: rgba(191, 219, 254, 0.5);
    background: radial-gradient(ellipse at center, rgba(191, 219, 254, 0.18) 0%, transparent 60%);
    animation: ringPulse 3s ease-in-out infinite 0.5s;
  }
}

@keyframes ringPulse {
  0%, 100% {
    opacity: 0.6;
    transform: translate(-50%, -50%) scale(1);
  }
  50% {
    opacity: 1;
    transform: translate(-50%, -50%) scale(1.05);
  }
}

.login-top-logo {
  width: 100%;
  height: 100%;
  display: block;
  object-fit: contain;
}

:deep(.logo-3d) {
  position: relative;
  z-index: 3;
  opacity: 0.9;
  filter: drop-shadow(0 20px 40px rgba(96, 165, 250, 0.5))
          drop-shadow(0 10px 20px rgba(59, 130, 246, 0.4))
          drop-shadow(0 0 60px rgba(147, 197, 253, 0.3));
  animation: float3d 8s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% {
    transform: translate(-50%, -50%) scale(1);
    opacity: 0.8;
  }
  50% {
    transform: translate(-50%, -50%) scale(1.1);
    opacity: 1;
  }
}

@keyframes float3d {
  0%, 100% {
    transform: translateY(0px) rotateZ(0deg);
  }
  25% {
    transform: translateY(-10px) rotateZ(2deg);
  }
  50% {
    transform: translateY(0px) rotateZ(0deg);
  }
  75% {
    transform: translateY(-6px) rotateZ(-2deg);
  }
}

// 浮动卡片
.floating-card {
  animation: cardFloat 6s infinite ease-in-out;
  z-index: 10;

  &:nth-child(odd) {
    animation-delay: -2s;
  }

  &:nth-child(even) {
    animation-delay: -4s;
  }
}

@keyframes cardFloat {
  0%, 100% {
    transform: translateY(0px);
  }
  50% {
    transform: translateY(-6px);
  }
}

// 顶部中央卡片需要单独动画（保留 translateX）
.floating-card.top-0 {
  animation: cardFloatTopCenter 6s infinite ease-in-out;
}

@keyframes cardFloatTopCenter {
  0%, 100% {
    transform: translateX(-50%) translateY(0px);
  }
  50% {
    transform: translateX(-50%) translateY(-6px);
  }
}

// 底部中央对话图标也保留 translateX
.connector-icon.bottom-0 {
  animation: cardFloatTopCenter 5s infinite ease-in-out;
  z-index: 11;
}

// 连接小图标
.connector-icon {
  z-index: 8;
  animation: cardFloat 5s infinite ease-in-out;

  &.left-1\/4 {
    animation-delay: -1s;
  }

  &.right-1\/4 {
    animation-delay: -3s;
  }
}

// 右侧登录区
.right-section {
  border-left: 1px solid rgba(229, 231, 235, 0.3);
}

.login-card {
  transition: all 0.3s ease;
}

// 响应式
@media (max-width: 1024px) {
  .left-section {
    display: none;
  }

  .right-section {
    width: 100% !important;
    border-left: none;
  }

  .central-area {
    display: none;
  }
}

@media (max-width: 640px) {
  .right-section {
    padding: 1rem;
  }

  .login-card {
    padding: 1.5rem !important;
  }
}
</style>

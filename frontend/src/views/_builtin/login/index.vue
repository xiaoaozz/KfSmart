<script setup lang="ts">
import { computed } from 'vue';
import type { Component } from 'vue';
import { loginModuleRecord } from '@/constants/app';
import { useAppStore } from '@/store/modules/app';
import { useThemeStore } from '@/store/modules/theme';
import { $t } from '@/locales';
import ParticlesBackground from '@/components/modern/ParticlesBackground.vue';
import GlassCard from '@/components/modern/GlassCard.vue';
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
</script>

<template>
  <div class="modern-login-container relative size-full overflow-hidden">
    <!-- 渐变背景 -->
    <div class="gradient-bg absolute inset-0" />

    <!-- 粒子动画背景 -->
    <ParticlesBackground
      :particle-count="60"
      particle-color="rgba(255, 255, 255, 0.6)"
      :particle-size="2"
      :speed="0.3"
      :connect-distance="120"
      :interactive="true"
    />

    <!-- 装饰性渐变球 -->
    <div class="decoration-orb orb-1" />
    <div class="decoration-orb orb-2" />
    <div class="decoration-orb orb-3" />

    <!-- 登录卡片容器 -->
    <div class="login-content relative z-10 flex-cc size-full px-4">
      <div class="animate-scale-in">
        <!-- 玻璃态登录卡片 -->
        <GlassCard
          :blur="16"
          :opacity="0.15"
          :border-opacity="0.3"
          size="lg"
          rounded="2xl"
          :hoverable="false"
        >
          <div class="login-card-inner w-140 lt-sm:w-90">
            <!-- Logo和标题 -->
            <header class="text-center mb-10">
              <div class="flex-cc mb-6">
                <SystemLogo class="text-20 text-white animate-bounce" style="animation-duration: 2s" />
              </div>
              
              <h1 class="text-4xl lt-sm:text-3xl font-700 text-white mb-2 gradient-text-glow">
                {{ $t('system.title') }}
              </h1>
              
              <p class="text-white/70 text-sm">
                现代化AI智能助手平台
              </p>
            </header>

            <!-- 主题和语言切换 -->
            <div class="flex-cc gap-4 mb-8">
              <ThemeSchemaSwitch
                :theme-schema="themeStore.themeScheme"
                :show-tooltip="false"
                class="text-white/80 hover:text-white text-xl transition-colors"
                @switch="themeStore.toggleThemeScheme"
              />
              <LangSwitch
                v-if="themeStore.header.multilingual.visible"
                :lang="appStore.locale"
                :lang-options="appStore.localeOptions"
                :show-tooltip="false"
                class="text-white/80 hover:text-white"
                @change-lang="appStore.changeLocale"
              />
            </div>

            <!-- 登录模块标题 -->
            <div class="mb-6">
              <h2 class="text-2xl lt-sm:text-xl font-600 text-white text-center">
                {{ $t(activeModule.label) }}
              </h2>
            </div>

            <!-- 登录表单 -->
            <div class="login-form-wrapper">
              <Transition :name="themeStore.page.animateMode" mode="out-in" appear>
                <component :is="activeModule.component" />
              </Transition>
            </div>
          </div>
        </GlassCard>

        <!-- 底部版权信息 -->
        <div class="text-center mt-6 text-white/60 text-sm">
          <p>© 2024 PaiSmart. All rights reserved.</p>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.modern-login-container {
  /* 渐变背景 */
  .gradient-bg {
    background: linear-gradient(135deg, #667eea 0%, #764ba2 40%, #4facfe 70%, #00f2fe 100%);
    background-size: 400% 400%;
    animation: gradientShift 15s ease infinite;
  }

  /* 装饰性渐变球 */
  .decoration-orb {
    position: absolute;
    border-radius: 50%;
    filter: blur(80px);
    opacity: 0.3;
    animation: float 20s ease-in-out infinite;
  }

  .orb-1 {
    top: 10%;
    left: 10%;
    width: 300px;
    height: 300px;
    background: radial-gradient(circle, rgba(255, 255, 255, 0.8) 0%, transparent 70%);
    animation-delay: 0s;
  }

  .orb-2 {
    bottom: 15%;
    right: 15%;
    width: 400px;
    height: 400px;
    background: radial-gradient(circle, rgba(102, 126, 234, 0.6) 0%, transparent 70%);
    animation-delay: 5s;
  }

  .orb-3 {
    top: 50%;
    left: 50%;
    width: 350px;
    height: 350px;
    background: radial-gradient(circle, rgba(79, 172, 254, 0.5) 0%, transparent 70%);
    animation-delay: 10s;
    transform: translate(-50%, -50%);
  }
}

/* 渐变文字发光效果 */
.gradient-text-glow {
  text-shadow: 0 0 20px rgba(255, 255, 255, 0.5), 0 0 40px rgba(255, 255, 255, 0.3);
}

/* 登录表单容器样式 */
.login-form-wrapper {
  :deep(.n-input) {
    background-color: rgba(255, 255, 255, 0.1);
    border: 1px solid rgba(255, 255, 255, 0.2);
    backdrop-filter: blur(10px);
    transition: all 0.3s ease;

    &:hover {
      background-color: rgba(255, 255, 255, 0.15);
      border-color: rgba(255, 255, 255, 0.3);
    }

    &:focus-within {
      background-color: rgba(255, 255, 255, 0.2);
      border-color: rgba(255, 255, 255, 0.5);
      box-shadow: 0 0 0 3px rgba(255, 255, 255, 0.1);
    }

    input {
      color: white !important;
      
      &::placeholder {
        color: rgba(255, 255, 255, 0.5);
      }
    }

    .n-input__prefix {
      color: rgba(255, 255, 255, 0.7);
    }

    .n-input__suffix {
      color: rgba(255, 255, 255, 0.7);
    }
  }

  :deep(.n-button) {
    transition: all 0.3s ease;

    &.n-button--primary-type {
      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
      border: none;
      box-shadow: 0 4px 15px rgba(102, 126, 234, 0.4);

      &:hover:not(:disabled) {
        transform: translateY(-2px);
        box-shadow: 0 6px 20px rgba(102, 126, 234, 0.6);
      }

      &:active:not(:disabled) {
        transform: translateY(0);
      }
    }

    &:not(.n-button--primary-type) {
      background-color: rgba(255, 255, 255, 0.1);
      border: 1px solid rgba(255, 255, 255, 0.2);
      color: white;

      &:hover:not(:disabled) {
        background-color: rgba(255, 255, 255, 0.2);
        border-color: rgba(255, 255, 255, 0.3);
      }
    }
  }

  :deep(.n-divider) {
    .n-divider__line {
      background-color: rgba(255, 255, 255, 0.2);
    }

    .n-divider__title {
      color: rgba(255, 255, 255, 0.7);
    }
  }
}

/* 动画定义 */
@keyframes gradientShift {
  0%, 100% {
    background-position: 0% 50%;
  }
  50% {
    background-position: 100% 50%;
  }
}

@keyframes float {
  0%, 100% {
    transform: translateY(0) scale(1);
  }
  33% {
    transform: translateY(-30px) scale(1.1);
  }
  66% {
    transform: translateY(30px) scale(0.9);
  }
}
</style>

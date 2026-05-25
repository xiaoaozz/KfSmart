import { defineConfig } from '@unocss/vite';
import transformerDirectives from '@unocss/transformer-directives';
import transformerVariantGroup from '@unocss/transformer-variant-group';
import presetWind3 from '@unocss/preset-wind3';
import type { Theme } from '@unocss/preset-uno';
import { presetSoybeanAdmin } from '@sa/uno-preset';
import { themeVars } from './src/theme/vars';

export default defineConfig<Theme>({
  content: {
    pipeline: {
      exclude: ['node_modules', 'dist']
    }
  },
  theme: {
    ...themeVars,
    fontSize: {
      'icon-xs': '0.875rem',
      'icon-small': '1rem',
      icon: '1.125rem',
      'icon-large': '1.5rem',
      'icon-xl': '2rem'
    },
    colors: {
      primary: {
        DEFAULT: '#667eea',
        50: '#f5f7ff',
        100: '#ebefff',
        200: '#d6deff',
        300: '#b8c5ff',
        400: '#8b9eff',
        500: '#667eea',
        600: '#5568d3',
        700: '#4552b5',
        800: '#364199',
        900: '#2a347d'
      },
      secondary: {
        DEFAULT: '#4facfe',
        50: '#f0f9ff',
        100: '#e0f4ff',
        200: '#b9e9ff',
        300: '#78c4ff',
        400: '#4facfe',
        500: '#2d8fd1',
        600: '#1e75b5',
        700: '#155d94',
        800: '#0f4a77',
        900: '#0a3a5f'
      },
      success: {
        DEFAULT: '#52c41a',
        light: '#95de64',
        dark: '#389e0d'
      },
      warning: {
        DEFAULT: '#faad14',
        light: '#ffc53d',
        dark: '#d48806'
      },
      error: {
        DEFAULT: '#f5222d',
        light: '#ff4d4f',
        dark: '#cf1322'
      }
    },
    borderRadius: {
      xs: '4px',
      sm: '8px',
      md: '12px',
      lg: '16px',
      xl: '20px',
      '2xl': '24px',
      '3xl': '32px'
    },
    boxShadow: {
      xs: '0 1px 2px rgba(0, 0, 0, 0.05)',
      sm: '0 2px 8px rgba(0, 0, 0, 0.08)',
      md: '0 4px 16px rgba(0, 0, 0, 0.12)',
      lg: '0 8px 24px rgba(0, 0, 0, 0.15)',
      xl: '0 12px 32px rgba(0, 0, 0, 0.2)',
      card: '0 4px 20px rgba(102, 126, 234, 0.15)',
      'card-hover': '0 8px 30px rgba(102, 126, 234, 0.25)',
      glow: '0 0 20px rgba(102, 126, 234, 0.4)',
      'glow-strong': '0 0 30px rgba(102, 126, 234, 0.6)'
    }
  },
  shortcuts: {
    // 原有快捷方式
    'card-wrapper': 'rd-lg shadow-card bg-white dark:bg-dark-container transition-all duration-250',
    'flex-cc': 'flex items-center justify-center',
    
    // 现代化渐变快捷方式
    'gradient-primary': 'bg-gradient-to-br from-primary-500 to-primary-700',
    'gradient-secondary': 'bg-gradient-to-br from-secondary-400 to-secondary-600',
    'gradient-success': 'bg-gradient-to-br from-success to-success-dark',
    'gradient-warning': 'bg-gradient-to-br from-warning to-warning-dark',
    'gradient-error': 'bg-gradient-to-br from-error to-error-dark',
    
    // 卡片样式
    'card-modern': 'rd-lg shadow-card bg-white dark:bg-dark-container p-6 transition-all hover:shadow-card-hover',
    'card-gradient': 'gradient-primary text-white rd-lg shadow-lg p-6',
    
    // 玻璃态效果
    'glass-effect': 'backdrop-blur-10 bg-white/10 border border-white/20 rd-lg',
    'glass-dark': 'backdrop-blur-10 bg-black/10 border border-white/10 rd-lg',
    
    // 按钮样式
    'btn-gradient': 'gradient-primary text-white rd-md shadow-md hover:shadow-lg transition-all cursor-pointer',
    'btn-modern': 'rd-md px-6 py-3 font-500 transition-all hover:scale-105 active:scale-95',
    
    // 悬浮效果
    'hover-lift': 'transition-all duration-250 hover:-translate-y-1 hover:shadow-lg',
    'hover-glow': 'transition-all duration-250 hover:shadow-glow',
    
    // 文字样式
    'text-gradient': 'bg-gradient-to-r from-primary-500 to-primary-700 bg-clip-text text-transparent',
    
    // 容器
    'container-modern': 'max-w-7xl mx-auto px-4 sm:px-6 lg:px-8',
    
    // 输入框
    'input-modern': 'rd-md border-2 border-gray-200 focus:border-primary-500 transition-colors px-4 py-2 outline-none',
    
    // 消息气泡
    'bubble-user': 'gradient-primary text-white rd-2xl rd-br-sm shadow-md max-w-2xl p-4',
    'bubble-assistant': 'bg-white dark:bg-dark-container shadow-md rd-2xl rd-bl-sm max-w-3xl p-4'
  },
  transformers: [transformerDirectives(), transformerVariantGroup()],
  presets: [presetWind3({ dark: 'class' }), presetSoybeanAdmin()]
});

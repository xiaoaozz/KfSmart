import { theme as antdTheme } from 'antd'
import type { ThemeConfig } from 'antd'

export const getAntdTheme = (isDark: boolean): ThemeConfig => ({
  cssVar: { prefix: 'ant' },
  algorithm: isDark ? antdTheme.darkAlgorithm : antdTheme.defaultAlgorithm,
  token: {
    // Brand
    colorPrimary: isDark ? '#4D7CFF' : '#0052FF',
    colorLink: isDark ? '#4D7CFF' : '#0052FF',

    // Background
    colorBgBase: isDark ? '#0A0A0F' : '#FAFAFA',
    colorBgContainer: isDark ? '#141420' : '#FFFFFF',
    colorBgElevated: isDark ? '#1A1A2E' : '#FFFFFF',
    colorBgLayout: isDark ? '#0A0A0F' : '#FAFAFA',
    colorBgSpotlight: isDark ? '#1A1A2E' : '#F1F5F9',

    // Border
    colorBorder: isDark ? '#252540' : '#E2E8F0',
    colorBorderSecondary: isDark ? '#1E1E35' : '#F1F5F9',

    // Text
    colorText: isDark ? '#F0F4FF' : '#0F172A',
    colorTextSecondary: isDark ? '#8892AA' : '#64748B',
    colorTextTertiary: isDark ? '#5A6480' : '#94A3B8',
    colorTextPlaceholder: isDark ? '#5A6480' : '#94A3B8',

    // Typography
    fontFamily: '"Inter", system-ui, -apple-system, sans-serif',
    fontFamilyCode: '"JetBrains Mono", "Fira Code", monospace',
    fontSize: 14,
    fontSizeLG: 16,
    fontSizeSM: 12,

    // Radius
    borderRadius: 8,
    borderRadiusLG: 12,
    borderRadiusSM: 6,
    borderRadiusXS: 4,

    // Shadow
    boxShadow: isDark ? '0 4px 6px rgba(0,0,0,0.35)' : '0 4px 6px rgba(0,0,0,0.07)',
    boxShadowSecondary: isDark ? '0 1px 3px rgba(0,0,0,0.3)' : '0 1px 3px rgba(0,0,0,0.06)',

    // Motion
    motionDurationFast: '0.1s',
    motionDurationMid: '0.2s',
    motionDurationSlow: '0.3s',
  },
  components: {
    Layout: {
      siderBg: isDark ? '#0F0F1A' : '#FFFFFF',
      headerBg: isDark ? '#0A0A0F' : '#FFFFFF',
      bodyBg: isDark ? '#0A0A0F' : '#FAFAFA',
      triggerBg: isDark ? '#141420' : '#F1F5F9',
      triggerColor: isDark ? '#8892AA' : '#64748B',
    },
    Menu: {
      itemBg: 'transparent',
      subMenuItemBg: 'transparent',
      itemSelectedBg: isDark ? 'rgba(77,124,255,0.15)' : 'rgba(0,82,255,0.08)',
      itemSelectedColor: isDark ? '#6B93FF' : '#0052FF',
      itemHoverBg: isDark ? 'rgba(77,124,255,0.06)' : '#F1F5F9',
      itemHoverColor: isDark ? '#F0F4FF' : '#0F172A',
      itemActiveBg: isDark ? 'rgba(77,124,255,0.2)' : 'rgba(0,82,255,0.12)',
      itemColor: isDark ? '#8892AA' : '#64748B',
      iconSize: 16,
    },
    Button: {
      primaryShadow: isDark ? '0 4px 14px rgba(77,124,255,0.3)' : '0 4px 14px rgba(0,82,255,0.25)',
      borderRadius: 8,
      borderRadiusLG: 10,
      controlHeight: 36,
      controlHeightLG: 44,
      controlHeightSM: 28,
    },
    Input: {
      borderRadius: 8,
      controlHeight: 36,
      controlHeightLG: 44,
    },
    Select: {
      borderRadius: 8,
      controlHeight: 36,
    },
    Card: {
      borderRadius: 12,
      boxShadow: isDark ? '0 1px 3px rgba(0,0,0,0.3)' : '0 1px 3px rgba(0,0,0,0.06)',
    },
    Table: {
      borderRadius: 12,
      headerBg: isDark ? '#1A1A2E' : '#F1F5F9',
      rowHoverBg: isDark ? '#1E1E35' : '#F8FAFF',
    },
    Modal: {
      borderRadius: 16,
    },
    Drawer: {
      borderRadius: 16,
    },
    Tag: {
      borderRadius: 4,
    },
    Tabs: {
      inkBarColor: isDark ? '#4D7CFF' : '#0052FF',
      itemSelectedColor: isDark ? '#4D7CFF' : '#0052FF',
      itemHoverColor: isDark ? '#6B93FF' : '#0047E0',
    },
  },
})

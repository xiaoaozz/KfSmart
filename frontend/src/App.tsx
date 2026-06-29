import { App as AntdApp, ConfigProvider } from 'antd'
import zhCN from 'antd/locale/zh_CN'
import enUS from 'antd/locale/en_US'
import jaJP from 'antd/locale/ja_JP'
import { useEffect, type ReactNode } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { RouterProvider } from 'react-router-dom'
import { useTranslation } from 'react-i18next'

import { useThemeStore, DARK_THEMES } from '@/stores/theme'
import type { ThemeId } from '@/stores/theme'
import { useLocaleStore } from '@/stores/locale'
import { getAntdTheme } from '@/theme/antd'
import { router } from '@/router'
import LoadingOverlay from '@/components/business/LoadingOverlay'

import '@xyflow/react/dist/style.css'
import '@/styles/variables.css'
import '@/styles/global.css'
import '@/styles/antd-override.css'

const ANTD_LOCALES = { 'zh-CN': zhCN, 'en-US': enUS, 'ja-JP': jaJP }

const queryClient = new QueryClient({
  defaultOptions: {
    queries: { staleTime: 60_000, retry: 1 },
    mutations: { retry: false },
  },
})

function ThemeSync() {
  const themeId = useThemeStore((s) => s.themeId)
  const setDark = useThemeStore((s) => s.setDark)

  useEffect(() => {
    const stored = localStorage.getItem('kf-theme')
    if (!stored) {
      const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches
      setDark(prefersDark)
    }
    const mq = window.matchMedia('(prefers-color-scheme: dark)')
    const handler = (e: MediaQueryListEvent) => {
      if (!localStorage.getItem('kf-theme')) setDark(e.matches)
    }
    mq.addEventListener('change', handler)
    return () => mq.removeEventListener('change', handler)
  }, [setDark])

  useEffect(() => {
    document.documentElement.dataset.theme = themeId
    document.documentElement.style.colorScheme = DARK_THEMES.has(themeId) ? 'dark' : 'light'
  }, [themeId])

  return null
}

function LocaleSync() {
  const locale = useLocaleStore((s) => s.locale)
  const { i18n } = useTranslation()

  useEffect(() => {
    if (i18n.language !== locale) {
      i18n.changeLanguage(locale)
    }
  }, [locale, i18n])

  return null
}

interface AppProps {
  children?: ReactNode
}

export default function App({ children }: AppProps) {
  const themeId = useThemeStore((s) => s.themeId) as ThemeId
  const locale = useLocaleStore((s) => s.locale)

  return (
    <QueryClientProvider client={queryClient}>
      <ConfigProvider theme={getAntdTheme(themeId)} locale={ANTD_LOCALES[locale]}>
        <AntdApp>
          <ThemeSync />
          <LocaleSync />
          <LoadingOverlay />
          {children ?? <RouterProvider router={router} />}
        </AntdApp>
      </ConfigProvider>
    </QueryClientProvider>
  )
}

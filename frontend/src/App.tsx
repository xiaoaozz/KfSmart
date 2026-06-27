import { App as AntdApp, ConfigProvider } from 'antd'
import zhCN from 'antd/locale/zh_CN'
import { useEffect, type ReactNode } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { RouterProvider } from 'react-router-dom'

import { useThemeStore } from '@/stores/theme'
import { getAntdTheme } from '@/theme/antd'
import { router } from '@/router'
import LoadingOverlay from '@/components/business/LoadingOverlay'

import '@/styles/variables.css'
import '@/styles/global.css'
import '@/styles/antd-override.css'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: { staleTime: 60_000, retry: 1 },
    mutations: { retry: false },
  },
})

function ThemeSync() {
  const isDark = useThemeStore((s) => s.isDark)
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
    document.documentElement.dataset.theme = isDark ? 'dark' : 'light'
    document.documentElement.style.colorScheme = isDark ? 'dark' : 'light'
  }, [isDark])

  return null
}

interface AppProps {
  children?: ReactNode
}

export default function App({ children }: AppProps) {
  const isDark = useThemeStore((s) => s.isDark)

  return (
    <QueryClientProvider client={queryClient}>
      <ConfigProvider theme={getAntdTheme(isDark)} locale={zhCN}>
        <AntdApp>
          <ThemeSync />
          <LoadingOverlay />
          {children ?? <RouterProvider router={router} />}
        </AntdApp>
      </ConfigProvider>
    </QueryClientProvider>
  )
}

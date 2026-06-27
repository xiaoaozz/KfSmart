import { useEffect } from 'react'

import { useThemeStore } from '@/stores/theme'

export function useTheme() {
  const { isDark, toggle, setDark } = useThemeStore()

  // Apply data-theme attribute + transition class on change
  useEffect(() => {
    const root = document.documentElement
    root.dataset.theme = isDark ? 'dark' : 'light'
    // Brief transition suppressor on initial load to avoid flash
    root.style.setProperty('color-scheme', isDark ? 'dark' : 'light')
  }, [isDark])

  // Initialize: respect OS preference if no persisted value
  useEffect(() => {
    const stored = localStorage.getItem('kf-theme')
    if (!stored) {
      const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches
      setDark(prefersDark)
    }
    // Listen for OS preference changes
    const mq = window.matchMedia('(prefers-color-scheme: dark)')
    const handler = (e: MediaQueryListEvent) => {
      // Only auto-switch if user hasn't manually set a preference
      const hasStored = localStorage.getItem('kf-theme')
      if (!hasStored) setDark(e.matches)
    }
    mq.addEventListener('change', handler)
    return () => mq.removeEventListener('change', handler)
  }, [setDark])

  return { isDark, toggle, setDark }
}

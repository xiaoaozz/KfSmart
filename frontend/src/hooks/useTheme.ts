import { useThemeStore } from '@/stores/theme'
import type { ThemeId } from '@/stores/theme'

export function useTheme() {
  const { themeId, isDark, toggle, setDark, setTheme } = useThemeStore()

  return { themeId, isDark, toggle, setDark, setTheme } as {
    themeId: ThemeId
    isDark: boolean
    toggle: () => void
    setDark: (dark: boolean) => void
    setTheme: (id: ThemeId) => void
  }
}

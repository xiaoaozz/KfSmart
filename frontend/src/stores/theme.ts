import { create } from 'zustand'
import { persist } from 'zustand/middleware'

export type ThemeId =
  | 'light'
  | 'dark'
  | 'earth'
  | 'corporate'
  | 'neo'
  | 'mono'
  | 'terminal'
  | 'organic'
  | 'vaporwave'
  | 'professional'

const THEME_ORDER: ThemeId[] = [
  'light',
  'dark',
  'earth',
  'corporate',
  'neo',
  'mono',
  'terminal',
  'organic',
  'vaporwave',
  'professional',
]

/** Themes that use dark color-scheme (browser UI, scrollbars) */
export const DARK_THEMES = new Set<ThemeId>(['dark', 'terminal', 'vaporwave'])

interface ThemeState {
  themeId: ThemeId
  isDark: boolean
  setTheme: (id: ThemeId) => void
  toggle: () => void
  setDark: (dark: boolean) => void
}

export const useThemeStore = create<ThemeState>()(
  persist(
    (set) => ({
      themeId: 'light' as ThemeId,
      isDark: false,
      setTheme: (id: ThemeId) => set({ themeId: id, isDark: DARK_THEMES.has(id) }),
      toggle: () =>
        set((s) => {
          const next = THEME_ORDER[(THEME_ORDER.indexOf(s.themeId) + 1) % THEME_ORDER.length]
          return { themeId: next, isDark: DARK_THEMES.has(next) }
        }),
      setDark: (dark: boolean) => {
        const id: ThemeId = dark ? 'dark' : 'light'
        return set({ themeId: id, isDark: dark })
      },
    }),
    {
      name: 'kf-theme',
      version: 1,
      migrate: (persisted: unknown, version: number) => {
        if (version === 0) {
          const old = persisted as { isDark?: boolean }
          const themeId: ThemeId = old.isDark ? 'dark' : 'light'
          return { themeId, isDark: old.isDark ?? false }
        }
        return persisted as ThemeState
      },
    },
  ),
)

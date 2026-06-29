import { create } from 'zustand'
import { persist } from 'zustand/middleware'

export type Locale = 'zh-CN' | 'en-US' | 'ja-JP'

interface LocaleState {
  locale: Locale
  setLocale: (locale: Locale) => void
}

export const useLocaleStore = create<LocaleState>()(
  persist(
    (set) => ({
      locale: 'zh-CN',
      setLocale: (locale) => set({ locale }),
    }),
    { name: 'kf-locale' },
  ),
)

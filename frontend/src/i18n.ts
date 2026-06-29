import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'

import zhCN from './locales/zh-CN.json'
import enUS from './locales/en-US.json'
import jaJP from './locales/ja-JP.json'

// Read persisted locale directly from localStorage to avoid a flash of the
// wrong language before the Zustand store hydrates (Zustand hydration is async).
function getSavedLocale(): string {
  try {
    const raw = localStorage.getItem('kf-locale')
    return (raw ? JSON.parse(raw) : {}).state?.locale ?? 'zh-CN'
  } catch {
    return 'zh-CN'
  }
}

i18n.use(initReactI18next).init({
  resources: {
    'zh-CN': { translation: zhCN },
    'en-US': { translation: enUS },
    'ja-JP': { translation: jaJP },
  },
  lng: getSavedLocale(),
  fallbackLng: 'zh-CN',
  interpolation: {
    escapeValue: false,
  },
})

export default i18n

import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'

import App from './App'
import { initErrorMonitor } from './monitoring/errors'
import { initVitals } from './monitoring/vitals'

initErrorMonitor()

// Prevent flash of wrong theme before React hydrates
;(function () {
  try {
    const stored = localStorage.getItem('kf-theme')
    const parsed = stored ? JSON.parse(stored) : null
    const isDark =
      parsed?.state?.isDark ?? window.matchMedia('(prefers-color-scheme: dark)').matches
    document.documentElement.dataset.theme = isDark ? 'dark' : 'light'
    document.documentElement.style.colorScheme = isDark ? 'dark' : 'light'
  } catch {
    // ignore parse errors
  }
})()

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
)

// Collect Core Web Vitals after first paint
initVitals()

import { createRoot } from 'react-dom/client'

import './i18n'
import App from './App'
import { initErrorMonitor } from './monitoring/errors'
import { initVitals } from './monitoring/vitals'

initErrorMonitor()

// Prevent flash of wrong theme before React hydrates
;(function () {
  try {
    const stored = localStorage.getItem('kf-theme')
    const parsed = stored ? JSON.parse(stored) : null
    const themeId: string =
      parsed?.state?.themeId ??
      (parsed?.state?.isDark ? 'dark' : null) ??
      (window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light')
    document.documentElement.dataset.theme = themeId
    const darkThemes = ['dark', 'terminal', 'vaporwave']
    document.documentElement.style.colorScheme = darkThemes.includes(themeId) ? 'dark' : 'light'
  } catch {
    // ignore parse errors
  }
})()

// StrictMode is intentionally omitted: @xyflow/react v12's StoreUpdater calls reset() in its
// cleanup effect, which destroys handleBounds before ResizeObserver can re-measure them.
// This causes getEdgePosition() to return null during the double-mount cycle, so newly
// connected edges are never rendered. Remove StrictMode until upstream fixes this.
createRoot(document.getElementById('root')!).render(<App />)

// Collect Core Web Vitals after first paint
initVitals()

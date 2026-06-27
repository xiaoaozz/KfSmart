export interface ErrorReport {
  message: string
  stack?: string
  source?: string
  lineno?: number
  colno?: number
  timestamp: number
  userAgent: string
  url: string
}

const ENDPOINT = '/api/metrics/errors'
const QUEUE: ErrorReport[] = []
let flushTimer: ReturnType<typeof setTimeout> | null = null

function flush(): void {
  if (!QUEUE.length) return
  const batch = QUEUE.splice(0)
  if (navigator.sendBeacon) {
    navigator.sendBeacon(ENDPOINT, new Blob([JSON.stringify(batch)], { type: 'application/json' }))
  }
}

function enqueue(report: ErrorReport): void {
  if (import.meta.env.DEV) {
    console.error('[ErrorMonitor]', report.message, report.stack ?? '')
    return
  }
  QUEUE.push(report)
  if (flushTimer) clearTimeout(flushTimer)
  flushTimer = setTimeout(flush, 2000)
}

function buildReport(
  message: string,
  error?: Error,
  source?: string,
  lineno?: number,
  colno?: number,
): ErrorReport {
  return {
    message: message.slice(0, 2000),
    stack: error?.stack?.slice(0, 4000),
    source,
    lineno,
    colno,
    timestamp: Date.now(),
    userAgent: navigator.userAgent,
    url: location.href,
  }
}

/**
 * Register global error and unhandled-rejection handlers.
 * Call once in main.tsx before React renders.
 */
export function initErrorMonitor(): void {
  window.addEventListener('error', (event) => {
    if (event.error instanceof Error) {
      enqueue(buildReport(event.message, event.error, event.filename, event.lineno, event.colno))
    }
  })

  window.addEventListener('unhandledrejection', (event) => {
    const reason = event.reason
    const message =
      reason instanceof Error
        ? reason.message
        : typeof reason === 'string'
          ? reason
          : 'Unhandled promise rejection'
    enqueue(buildReport(message, reason instanceof Error ? reason : undefined))
  })

  // Flush on page hide (instead of beforeunload which may block navigation)
  document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'hidden') flush()
  })
}

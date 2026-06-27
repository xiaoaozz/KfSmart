import { onCLS, onFCP, onINP, onLCP, onTTFB, type MetricType } from 'web-vitals'

export interface VitalEntry {
  name: MetricType['name']
  value: number
  rating: MetricType['rating']
  delta: number
  id: string
  navigationType: MetricType['navigationType']
}

type ReportFn = (entry: VitalEntry) => void

const ENDPOINT = '/api/metrics/vitals'

function sendToServer(entry: VitalEntry): void {
  if (!navigator.sendBeacon) return
  const body = JSON.stringify(entry)
  navigator.sendBeacon(ENDPOINT, new Blob([body], { type: 'application/json' }))
}

function log(entry: VitalEntry): void {
  const { name, value, rating, delta } = entry
  const prefix = rating === 'good' ? '✓' : rating === 'needs-improvement' ? '△' : '✗'
  console.warn(
    `[WebVital] ${prefix} ${name} ${value.toFixed(1)} (Δ ${delta.toFixed(1)}) [${rating}]`,
  )
}

function handle(report: ReportFn) {
  return (metric: MetricType) => {
    const entry: VitalEntry = {
      name: metric.name,
      value: metric.value,
      rating: metric.rating,
      delta: metric.delta,
      id: metric.id,
      navigationType: metric.navigationType,
    }
    report(entry)
  }
}

/**
 * Initialize Core Web Vitals collection.
 * Logs to console in development; sends to /api/metrics/vitals in production.
 */
export function initVitals(): void {
  const isDev = import.meta.env.DEV
  const report: ReportFn = isDev ? log : sendToServer

  const h = handle(report)
  onCLS(h)
  onFCP(h)
  onINP(h)
  onLCP(h)
  onTTFB(h)
}

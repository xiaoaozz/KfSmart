import { createRequire } from 'module'
const require = createRequire(import.meta.url)
const { chromium } = require('./node_modules/.pnpm/playwright@1.61.1/node_modules/playwright/index.js')

const BASE = 'http://localhost:3001'

const wf = {
  id: 1,
  name: 'Probe Workflow',
  description: '',
  status: 'draft',
  runCount: 0,
  createTime: '2026-06-01T00:00:00Z',
  updateTime: '2026-06-01T00:00:00Z',
  nodes: [
    { id: 'start-1', type: 'start', position: { x: 250, y: 40 }, data: { inputVariable: 'input' } },
    { id: 'llm-1', type: 'llm', position: { x: 250, y: 240 }, data: { model: 'deepseek', systemPrompt: '', temperature: 0.7, maxTokens: 1024 } },
    { id: 'end-1', type: 'end', position: { x: 250, y: 440 }, data: { outputVariable: 'output' } },
  ],
  edges: [], // intentionally empty so we can test creating connections
}

const me = {
  id: 1,
  username: 'probe',
  role: 'ADMIN',
  permissions: ['*'],
  avatar: '',
}

function envelope(data) {
  return { code: 0, message: 'ok', data }
}

const browser = await chromium.launch({ headless: true })
const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } })
const page = await ctx.newPage()

const consoleErrs = []
page.on('console', (m) => { if (m.type() === 'error') consoleErrs.push(m.text()) })
page.on('pageerror', (e) => consoleErrs.push('PAGEERROR: ' + e.message))
page.on('framenavigated', (f) => { if (f === page.mainFrame()) console.log('NAV', f.url()) })

// Inject fake auth token before app boots
await ctx.addInitScript(() => {
  localStorage.setItem('kf-auth', JSON.stringify({ token: 'fake-token', refreshToken: 'fake-refresh' }))
  localStorage.setItem('kf-locale', JSON.stringify({ state: { locale: 'zh-CN' }, version: 0 }))
})

// Mock backend
await page.route('**/api/v1/users/me', (r) => r.fulfill({ json: envelope(me) }))
await page.route('**/api/v1/workflows/1', (r) => r.fulfill({ json: envelope(wf) }))
await page.route('**/api/v1/workflows/1/graph', (r) => r.fulfill({ json: envelope({ ...wf, edges: [] }) }))
await page.route('**/api/v1/auth/refreshToken', (r) => r.fulfill({ json: envelope({ token: 'fake-token' }) }))
// Catch-all: return empty 200 envelope for any other api call (lists, etc.) to avoid 401 loops
await page.route('**/api/v1/**', (r) => {
  const u = r.request().url()
  if (u.endsWith('/users/me') || u.endsWith('/workflows/1') || u.includes('/auth/refreshToken')) return r.fallback()
  return r.fulfill({ json: envelope({ list: [], total: 0, records: [] }) })
})

// Pre-warm /users/me cache on /dashboard so RequireAuth is authenticated before entering editor
await page.goto(`${BASE}/dashboard`, { waitUntil: 'domcontentloaded' })
await page.waitForTimeout(1500)
await page.goto(`${BASE}/workflows/1/edit`, { waitUntil: 'domcontentloaded' })
await page.waitForSelector('.react-flow', { timeout: 15000 })
// let nodes render
await page.waitForSelector('.react-flow__node', { timeout: 10000 })
await page.waitForTimeout(800)

const handleInfo = await page.evaluate(() => {
  const hs = Array.from(document.querySelectorAll('.react-flow__handle'))
  return hs.map((h) => {
    const r = h.getBoundingClientRect()
    return {
      nodeid: h.getAttribute('data-nodeid'),
      handlepos: h.getAttribute('data-handlepos'),
      handleid: h.getAttribute('data-handleid'),
      classes: h.className,
      cx: r.left + r.width / 2,
      cy: r.top + r.height / 2,
      w: r.width,
      h: r.height,
      pointerEvents: getComputedStyle(h).pointerEvents,
    }
  })
})
console.log('HANDLES:', JSON.stringify(handleInfo, null, 2))

const edgeCountBefore = await page.locator('.react-flow__edge').count()
console.log('edge count before drag:', edgeCountBefore)

// Find start-1 bottom (source) and llm-1 top (target)
const src = handleInfo.find((h) => h.nodeid === 'start-1' && h.handlepos === 'bottom')
const tgt = handleInfo.find((h) => h.nodeid === 'llm-1' && h.handlepos === 'top')
console.log('src handle:', JSON.stringify(src))
console.log('tgt handle:', JSON.stringify(tgt))

if (src && tgt) {
  // Move onto source, press, drag toward target with intermediate points, release on target
  await page.mouse.move(src.cx, src.cy)
  await page.mouse.move(src.cx, src.cy)
  await page.waitForTimeout(120)
  await page.mouse.down()
  // step toward target
  const steps = 8
  for (let i = 1; i <= steps; i++) {
    const x = src.cx + ((tgt.cx - src.cx) * i) / steps
    const y = src.cy + ((tgt.cy - src.cy) * i) / steps
    await page.mouse.move(x, y)
    await page.waitForTimeout(40)
  }
  // hover exactly on target to register dropzone
  await page.mouse.move(tgt.cx, tgt.cy)
  await page.waitForTimeout(150)
  await page.mouse.up()
  await page.waitForTimeout(500)
}

const edgeCountAfter = await page.locator('.react-flow__edge').count()
console.log('edge count after drag:', edgeCountAfter)

// also check connectionline presence during a second drag for visibility
await page.screenshot({ path: '/tmp/kf_wf_probe.png', fullPage: false })

console.log('CONSOLE ERRORS:', JSON.stringify(consoleErrs, null, 2))

await browser.close()

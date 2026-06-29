import { test, expect } from '@playwright/test'

// These tests assume a logged-in session can be set up via storageState or login fixture.
// For now, each test that needs auth will call a helper to set the token in localStorage.

async function setAuthToken(page: import('@playwright/test').Page) {
  await page.goto('/login')
  await page.evaluate(() => {
    const auth = { state: { token: 'test-token', refreshToken: 'test-refresh' }, version: 0 }
    localStorage.setItem('kf-auth', JSON.stringify(auth))
  })
}

test.describe('Navigation', () => {
  test.beforeEach(async ({ page }) => {
    await setAuthToken(page)
  })

  test('sidebar is visible on the dashboard', async ({ page }) => {
    await page.goto('/dashboard')
    await expect(page.locator('aside, nav').first()).toBeVisible()
  })

  test('clicking sidebar item navigates to knowledge bases', async ({ page }) => {
    await page.goto('/dashboard')
    await page.getByRole('menuitem', { name: '知识库' }).click()
    await expect(page).toHaveURL(/\/knowledge-bases/)
  })

  test('clicking AI 对话 navigates to chat', async ({ page }) => {
    await page.goto('/dashboard')
    await page.getByRole('menuitem', { name: 'AI 对话' }).click()
    await expect(page).toHaveURL(/\/chat/)
  })

  test('logo click navigates to dashboard', async ({ page }) => {
    await page.goto('/knowledge-bases')
    await page.locator('.logo, [class*="logo"]').first().click()
    await expect(page).toHaveURL(/\/dashboard/)
  })

  test('user dropdown opens on avatar click', async ({ page }) => {
    await page.goto('/dashboard')
    await page.locator('header .ant-avatar, header [class*="avatar"]').first().click()
    await expect(page.getByRole('menuitem', { name: '个人中心' })).toBeVisible()
  })
})

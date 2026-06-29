import { test, expect } from '@playwright/test'

test.describe('Authentication', () => {
  test('login page renders email and password fields', async ({ page }) => {
    await page.goto('/login')
    await expect(page.locator('input[type="email"], input[autocomplete="email"]')).toBeVisible()
    await expect(page.locator('input[type="password"]')).toBeVisible()
  })

  test('shows validation error on empty submit', async ({ page }) => {
    await page.goto('/login')
    await page.getByRole('button', { name: '登录' }).click()
    await expect(page.getByText('请输入邮箱')).toBeVisible()
  })

  test('shows email format error on invalid email', async ({ page }) => {
    await page.goto('/login')
    await page.locator('input[autocomplete="email"]').fill('notanemail')
    await page.getByRole('button', { name: '登录' }).click()
    await expect(page.getByText('邮箱格式不正确')).toBeVisible()
  })

  test('unauthenticated access to dashboard redirects to login', async ({ page }) => {
    await page.goto('/dashboard')
    await expect(page).toHaveURL(/\/login/)
  })

  test('register page is accessible from login', async ({ page }) => {
    await page.goto('/login')
    await page.getByRole('link', { name: /注册/ }).click()
    await expect(page).toHaveURL(/\/register/)
  })
})

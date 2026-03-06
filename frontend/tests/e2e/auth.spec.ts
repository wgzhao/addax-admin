import { test, expect } from '@playwright/test'
import { login } from './helpers/auth'

test.use({ storageState: { cookies: [], origins: [] } })

test('should login with default account', async ({ page }) => {
  await login(page)
  await expect(page.getByText('统一采集管理系统')).toBeVisible()
})

test('should reject invalid credentials', async ({ page }) => {
  await page.goto('/login')
  await page.getByLabel('Username/Email').fill('admin')
  await page.getByLabel('Password').fill('invalid-password')
  await page.getByRole('button', { name: 'Login' }).click()
  await expect(page).toHaveURL(/\/login$/)
})

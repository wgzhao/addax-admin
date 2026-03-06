import { expect, type Page } from '@playwright/test'

const username = process.env.E2E_USERNAME || 'admin'
const password = process.env.E2E_PASSWORD || 'admin123'

export async function login(page: Page): Promise<void> {
  await page.goto('/login')
  await page.getByLabel('Username/Email').fill(username)
  await page.getByLabel('Password').fill(password)
  await page.getByRole('button', { name: 'Login' }).click()
  await expect(page).toHaveURL('/')
}

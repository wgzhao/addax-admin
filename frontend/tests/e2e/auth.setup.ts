import { test, expect } from '@playwright/test'
import fs from 'node:fs'
import path from 'node:path'

const username = process.env.E2E_USERNAME || 'admin'
const password = process.env.E2E_PASSWORD || 'admin123'
const authFile = path.join(__dirname, '.auth', 'user.json')

test('authenticate once and save storage state', async ({ page }) => {
  fs.mkdirSync(path.dirname(authFile), { recursive: true })

  await page.goto('/login')
  await page.getByLabel('Username/Email').fill(username)
  await page.getByLabel('Password').fill(password)
  await page.getByRole('button', { name: 'Login' }).click()
  await expect(page).toHaveURL('/')

  await page.context().storageState({ path: authFile })
})

import { test, expect } from '@playwright/test';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const username = process.env.E2E_USERNAME || 'admin';
const password = process.env.E2E_PASSWORD || 'admin123';
const authDir = fileURLToPath(new URL('./.auth', import.meta.url));
const authFile = path.join(authDir, 'user.json');

test('authenticate once and save storage state', async ({ page }) => {
  fs.mkdirSync(path.dirname(authFile), { recursive: true });

  await page.goto('/login');
  // Hydration can clear a value written before Vue takes over, so confirm each field and wait for the
  // submit button to leave its disabled (invalid form) state before clicking.
  const usernameField = page.locator('input[autocomplete="username"]');
  const passwordField = page.locator('input[autocomplete="current-password"]');
  await usernameField.fill(username);
  await expect(usernameField).toHaveValue(username);
  await passwordField.fill(password);
  await expect(passwordField).toHaveValue(password);

  const loginButton = page.getByRole('button', { name: 'Login' });
  await expect(loginButton).toBeEnabled();
  await loginButton.click();
  await expect(page).toHaveURL('/', { timeout: 20_000 });

  await page.context().storageState({ path: authFile });
});

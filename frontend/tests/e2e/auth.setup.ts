import { test, expect } from '@playwright/test';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { apiLogin, username } from './helpers/auth';

const authDir = fileURLToPath(new URL('./.auth', import.meta.url));
const authFile = path.join(authDir, 'user.json');

test('authenticate once and save storage state', async ({ page, request }) => {
  fs.mkdirSync(path.dirname(authFile), { recursive: true });

  // Seed the session through the API instead of driving the form: `vite --force` re-optimizes
  // dependencies and reloads the page mid-interaction, which made this step flaky. The login form
  // itself is covered by auth.spec.ts.
  const token = await apiLogin(request);

  await page.addInitScript(
    ([token, user]) => {
      window.localStorage.setItem('authToken', token);
      window.localStorage.setItem('authUsername', user);
    },
    [token, username]
  );
  await page.goto('/');
  await expect(page).toHaveURL('/');

  await page.context().storageState({ path: authFile });
});

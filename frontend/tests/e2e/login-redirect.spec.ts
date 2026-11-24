import { test, expect } from '@playwright/test';

// These tests exercise the unauthenticated entry path, so they must not inherit the seeded
// admin session from the setup project.
test.use({ storageState: { cookies: [], origins: [] } });

const username = process.env.E2E_USERNAME || 'admin';
const password = process.env.E2E_PASSWORD || 'admin123';

async function signIn(page: import('@playwright/test').Page) {
  await page.locator('input[autocomplete="username"]').fill(username);
  await page.locator('input[autocomplete="current-password"]').fill(password);
  await page.getByRole('button', { name: '登录' }).click();
}

test('login returns to the originally requested url', async ({ page }) => {
  const target = '/table?q=orders&page=2&size=50';
  await page.goto(target);

  // The guard keeps the full URL, not just the pathname.
  await expect(page).toHaveURL(/\/login\?/);
  expect(new URL(page.url()).searchParams.get('redirect')).toBe(target);

  await signIn(page);
  await expect(page).toHaveURL(url => {
    return (
      url.pathname === '/table' &&
      url.searchParams.get('q') === 'orders' &&
      url.searchParams.get('page') === '2' &&
      url.searchParams.get('size') === '50'
    );
  });
});

test('login ignores an off-site redirect', async ({ page }) => {
  await page.goto('/login?redirect=//evil.example.test/steal');
  await signIn(page);

  await expect(page).toHaveURL(url => url.pathname === '/' && url.host !== 'evil.example.test');
});

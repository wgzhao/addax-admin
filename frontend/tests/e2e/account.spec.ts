import { expect, test, type Page } from '@playwright/test';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { apiLogin, username as adminUsername } from './helpers/auth';

const artifactDir = fileURLToPath(new URL('./artifacts', import.meta.url));

interface CurrentUser {
  username: string;
  displayName?: string | null;
  email?: string | null;
  remark?: string | null;
  authorities: string[];
  grants?: Array<{ sourceId: number; sourceName: string; accessLevel: string }>;
  createdAt?: string | null;
  lastLoginAt?: string | null;
  passwordChangedAt?: string | null;
}

const formatDateTime = (value?: string | null) =>
  value ? value.replace('T', ' ').slice(0, 19) : '-';

async function seedSession(page: Page, token: string, username: string) {
  await page.addInitScript(
    ([seedToken, seedUser]) => {
      window.localStorage.setItem('authToken', seedToken);
      window.localStorage.setItem('authUsername', seedUser);
    },
    [token, username]
  );
}

test.describe('account page', () => {
  test('is reachable from the account menu and mirrors /users/me', async ({ page, request }) => {
    const token = await apiLogin(request);
    const meRes = await request.get('/api/v1/users/me', {
      headers: { Authorization: `Bearer ${token}` },
    });
    expect(meRes.ok()).toBeTruthy();
    const me = (await meRes.json()) as CurrentUser;
    expect(me.username).toBe(adminUsername);

    await page.goto('/');
    await page.getByRole('button', { name: '账号菜单', exact: true }).click();

    const menu = page.locator('.user-nav-menu-list');
    await expect(menu.locator('.top-nav-menu-item', { hasText: '我的账号' })).toBeVisible();
    await expect(menu.locator('.top-nav-menu-item', { hasText: '修改密码' })).toHaveCount(0);
    await expect(menu.locator('.top-nav-menu-item', { hasText: '使用系统主题' })).toHaveCount(0);
    await expect(menu.locator('.top-nav-menu-item', { hasText: '系统版本' })).toBeVisible();
    await expect(menu.locator('.top-nav-menu-item', { hasText: '注销' })).toBeVisible();

    const menuItems = await menu.locator('.top-nav-menu-item .v-list-item-title').allInnerTexts();

    await menu.locator('.top-nav-menu-item', { hasText: '我的账号' }).click();
    await expect(page).toHaveURL(/\/account$/);
    await expect(page.getByRole('heading', { name: '我的账号' })).toBeVisible();

    const profile = page.getByTestId('account-profile-card');
    await expect(profile).toContainText(me.username);
    await expect(profile).toContainText(me.displayName || me.username);
    await expect(profile).toContainText(me.email || '未配置');
    await expect(profile).toContainText(formatDateTime(me.createdAt));
    await expect(profile).toContainText(formatDateTime(me.lastLoginAt));

    // Admin holds implicit access to every source, so the grant table must not render.
    await expect(page.getByTestId('account-role')).toHaveText('管理员');
    await expect(page.getByTestId('account-grants-card')).toContainText(
      '管理员账号可操作全部采集源'
    );

    const sections = await page.locator('.ds-card .ds-section-title').allInnerTexts();

    fs.mkdirSync(artifactDir, { recursive: true });
    await page.screenshot({ path: path.join(artifactDir, 'account-page.png'), fullPage: true });
    fs.writeFileSync(
      path.join(artifactDir, 'account.json'),
      JSON.stringify(
        {
          url: page.url(),
          username: me.username,
          role: await page.getByTestId('account-role').innerText(),
          sections,
          menuItems,
          grantsNotice: await page.getByTestId('account-grants-card').innerText(),
          passwordChangedAt: formatDateTime(me.passwordChangedAt),
        },
        null,
        2
      )
    );
  });

  test('survives a deep link reload and keeps the old password route working', async ({ page }) => {
    await page.goto('/account');
    await expect(page.getByRole('heading', { name: '我的账号' })).toBeVisible();
    await expect(page.getByTestId('account-profile-card')).toBeVisible();

    await page.reload();
    await expect(page.getByRole('heading', { name: '我的账号' })).toBeVisible();
    await expect(page.getByTestId('account-profile-card')).toContainText(adminUsername);

    await page.goto('/change-password');
    await expect(page).toHaveURL(/\/account$/);
    await expect(page.getByTestId('account-security-card')).toBeVisible();
  });

  test('persists the appearance preference per browser', async ({ page }) => {
    await page.goto('/account');
    const hint = page.getByTestId('account-theme-hint');

    await page.getByRole('radio', { name: '深色' }).check();
    await expect.poll(() => page.evaluate(() => localStorage.getItem('theme'))).toBe('dark');
    await expect(hint).toContainText('当前使用深色外观');

    await page.reload();
    await expect(page.getByRole('radio', { name: '深色' })).toBeChecked();
    await expect(hint).toContainText('当前使用深色外观');

    await page.getByRole('radio', { name: '跟随系统' }).check();
    await expect.poll(() => page.evaluate(() => localStorage.getItem('theme'))).toBe('system');
    await expect(hint).toContainText('当前跟随系统设置');
    await expect(hint).toContainText('仅保存在当前浏览器');
  });
});

test.describe('account page as a scoped account', () => {
  test.use({ storageState: { cookies: [], origins: [] } });

  test('lists exactly the OPERATE grants and explains an empty scope', async ({
    page,
    request,
  }) => {
    const adminToken = await apiLogin(request);
    const auth = { Authorization: `Bearer ${adminToken}` };

    const sourcesRes = await request.get('/api/v1/sources', { headers: auth });
    expect(sourcesRes.ok()).toBeTruthy();
    const sources = (await sourcesRes.json()) as Array<{
      id: number;
      name: string;
      enabled?: boolean;
    }>;
    const source = sources.find(item => item.enabled !== false);
    test.skip(!source, 'no enabled source in the test database');

    const suffix = Date.now().toString(36);
    const grantedUser = `e2e-account-${suffix}`;
    const emptyUser = `e2e-empty-${suffix}`;
    const password = 'e2eAccount123';

    try {
      for (const name of [grantedUser, emptyUser]) {
        const created = await request.post('/api/v1/users', {
          headers: auth,
          data: { username: name, password, enabled: true, authority: 'user' },
        });
        expect(created.ok()).toBeTruthy();
      }

      const grant = await request.put(`/api/v1/users/${grantedUser}/grants`, {
        headers: auth,
        data: [{ sourceId: source!.id, accessLevel: 'OPERATE' }],
      });
      expect(grant.ok()).toBeTruthy();

      const grantedLogin = await request.post('/api/v1/auth/login', {
        data: { username: grantedUser, password },
      });
      expect(grantedLogin.ok()).toBeTruthy();

      await page.goto('/login');
      await page.evaluate(
        ([token, name]) => {
          window.localStorage.setItem('authToken', token);
          window.localStorage.setItem('authUsername', name);
        },
        [(await grantedLogin.json()).data as string, grantedUser]
      );
      await page.goto('/account');
      await expect(page.getByTestId('account-role')).toHaveText('操作员');
      const grantsCard = page.getByTestId('account-grants-card');
      const rows = grantsCard.locator('tbody tr');
      await expect(rows).toHaveCount(1);
      await expect(rows.first()).toContainText(source!.name);
      await expect(rows.first()).toContainText('可操作');

      // A user without grants still reads everything but operates nothing; the card must say so
      // instead of rendering an empty table.
      const emptyLogin = await request.post('/api/v1/auth/login', {
        data: { username: emptyUser, password },
      });
      expect(emptyLogin.ok()).toBeTruthy();
      await page.evaluate(
        ([token, name]) => {
          window.localStorage.setItem('authToken', token);
          window.localStorage.setItem('authUsername', name);
        },
        [(await emptyLogin.json()).data as string, emptyUser]
      );

      await page.reload();
      await expect(page.getByTestId('account-role')).toHaveText('操作员');
      await expect(grantsCard).toContainText('尚未授权任何采集源');
      await expect(grantsCard.locator('tbody tr')).toHaveCount(0);
    } finally {
      for (const name of [grantedUser, emptyUser]) {
        await request.delete(`/api/v1/users/${name}`, { headers: auth });
      }
    }
  });

  test('edits its own descriptive fields and falls back to the username', async ({
    page,
    request,
  }) => {
    const adminToken = await apiLogin(request);
    const auth = { Authorization: `Bearer ${adminToken}` };
    const suffix = Date.now().toString(36);
    const name = `e2e-profile-${suffix}`;
    const password = 'e2eProfile123';

    try {
      const created = await request.post('/api/v1/users', {
        headers: auth,
        data: { username: name, password, enabled: true, authority: 'user' },
      });
      expect(created.ok()).toBeTruthy();

      const login = await request.post('/api/v1/auth/login', {
        data: { username: name, password },
      });
      expect(login.ok()).toBeTruthy();
      await seedSession(page, (await login.json()).data as string, name);

      await page.goto('/account');
      const profile = page.getByTestId('account-profile-card');
      // A freshly created account has no display name, so the card falls back to the username.
      await expect(profile).toContainText(name);

      await page.getByTestId('account-edit-profile').click();
      await page.getByLabel('姓名').fill('端到端用户');
      await page.getByLabel('邮箱').fill('e2e-profile@example.com');
      await page.getByLabel('备注').fill('自助维护');
      await page.getByTestId('account-save-profile').click();

      await expect(profile).toContainText('端到端用户');
      await expect(profile).toContainText('e2e-profile@example.com');
      await expect(profile).toContainText('自助维护');
      // The topbar reads the same value from the auth store.
      await expect(page.getByRole('button', { name: '账号菜单', exact: true })).toHaveText(
        '端到端用户'
      );

      await page.reload();
      await expect(profile).toContainText('端到端用户');

      const relogin = await request.post('/api/v1/auth/login', {
        data: { username: name, password },
      });
      const selfToken = (await relogin.json()).data as string;
      const persisted = await request.get('/api/v1/users/me', {
        headers: { Authorization: `Bearer ${selfToken}` },
      });
      const me = (await persisted.json()) as CurrentUser;
      expect(me.displayName).toBe('端到端用户');
      expect(me.email).toBe('e2e-profile@example.com');
      expect(me.remark).toBe('自助维护');
      expect(me.authorities).toEqual(['user']);

      // Clearing the name is allowed and falls back to the username server-side.
      await page.getByTestId('account-edit-profile').click();
      await page.getByLabel('姓名').fill('');
      await page.getByTestId('account-save-profile').click();
      await expect(profile).toContainText(name);
    } finally {
      await request.delete(`/api/v1/users/${name}`, { headers: auth });
    }
  });

  test('rejects a wrong current password, then forces re-login after a real change', async ({
    page,
    request,
  }) => {
    const adminToken = await apiLogin(request);
    const auth = { Authorization: `Bearer ${adminToken}` };
    const suffix = Date.now().toString(36);
    const name = `e2e-passwd-${suffix}`;
    const oldPassword = 'e2ePassword123';
    const newPassword = 'e2ePassword456';

    try {
      const created = await request.post('/api/v1/users', {
        headers: auth,
        data: { username: name, password: oldPassword, enabled: true, authority: 'user' },
      });
      expect(created.ok()).toBeTruthy();

      const login = await request.post('/api/v1/auth/login', {
        data: { username: name, password: oldPassword },
      });
      expect(login.ok()).toBeTruthy();
      await seedSession(page, (await login.json()).data as string, name);

      await page.goto('/account');
      await page.getByRole('button', { name: '修改密码' }).click();
      await page.locator('input[autocomplete="current-password"]').fill('wrong-password');
      await page.locator('input[autocomplete="new-password"]').first().fill(newPassword);
      const confirm = page.locator('input[autocomplete="new-password"]').nth(1);
      await confirm.fill(newPassword);
      const submit = page.getByRole('button', { name: '提交' });
      await expect(submit).toBeEnabled();
      await submit.click();

      await expect(page.locator('.notice-text', { hasText: '当前密码不正确' })).toBeVisible();
      await expect(page).toHaveURL(/\/account$/);
      await expect(page.getByTestId('account-profile-card')).toBeVisible();

      await page.locator('input[autocomplete="current-password"]').fill(oldPassword);
      await page.locator('input[autocomplete="new-password"]').first().fill(newPassword);
      await confirm.fill(newPassword);
      await page.getByRole('button', { name: '提交' }).click();

      await expect(page).toHaveURL(/\/login/);
      await expect(page.locator('.notice-text', { hasText: '密码修改成功' })).toBeVisible();

      const relogin = await request.post('/api/v1/auth/login', {
        data: { username: name, password: newPassword },
      });
      expect(relogin.ok()).toBeTruthy();
    } finally {
      await request.delete(`/api/v1/users/${name}`, { headers: auth });
    }
  });
});

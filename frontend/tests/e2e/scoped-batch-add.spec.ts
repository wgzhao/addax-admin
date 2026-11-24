import { expect, test } from '@playwright/test';
import { apiLogin } from './helpers/auth';

test.use({ storageState: { cookies: [], origins: [] } });

test('a user with an OPERATE grant can open the batch-add wizard', async ({ page, request }) => {
  const adminToken = await apiLogin(request);
  const auth = { Authorization: `Bearer ${adminToken}` };

  const sourcesRes = await request.get('/api/v1/sources', { headers: auth });
  expect(sourcesRes.ok()).toBeTruthy();
  const sources = (await sourcesRes.json()) as Array<{ id: number; enabled?: boolean }>;
  const source = sources.find(item => item.enabled !== false);
  test.skip(!source, 'no enabled source in the test database');

  const suffix = Date.now().toString(36);
  const username = `e2e-scoped-${suffix}`;
  const password = 'e2eScoped123';

  try {
    const create = await request.post('/api/v1/users', {
      headers: auth,
      data: { username, password, enabled: true, authority: 'user' },
    });
    expect(create.ok()).toBeTruthy();

    const grant = await request.put(`/api/v1/users/${username}/grants`, {
      headers: auth,
      data: [{ sourceId: source!.id, accessLevel: 'OPERATE' }],
    });
    expect(grant.ok()).toBeTruthy();

    const login = await request.post('/api/v1/auth/login', {
      data: { username, password },
    });
    expect(login.ok()).toBeTruthy();
    const token = (await login.json()).data as string;

    await page.addInitScript(
      ([seedToken, seedUser]) => {
        window.localStorage.setItem('authToken', seedToken);
        window.localStorage.setItem('authUsername', seedUser);
      },
      [token, username]
    );

    await page.goto('/table');
    const addButton = page.getByRole('button', { name: '新增表' });
    await expect(addButton).toBeVisible();
    await addButton.click();

    // The route must no longer be admin-only: the backend already enforces OPERATE per source.
    await expect(page).toHaveURL(/\/table\/batch-add$/);
    await expect(page.getByRole('heading', { name: '批量新增采集表' })).toBeVisible();
    await expect(page).not.toHaveURL(/\/forbidden/);
  } finally {
    await request.delete(`/api/v1/users/${username}`, { headers: auth });
  }
});

test('a user without grants does not get the batch-add entry', async ({ page, request }) => {
  const adminToken = await apiLogin(request);
  const auth = { Authorization: `Bearer ${adminToken}` };
  const suffix = Date.now().toString(36);
  const username = `e2e-plain-${suffix}`;
  const password = 'e2ePlain123';

  try {
    const create = await request.post('/api/v1/users', {
      headers: auth,
      data: { username, password, enabled: true, authority: 'user' },
    });
    expect(create.ok()).toBeTruthy();

    const login = await request.post('/api/v1/auth/login', {
      data: { username, password },
    });
    const token = (await login.json()).data as string;
    await page.addInitScript(
      ([seedToken, seedUser]) => {
        window.localStorage.setItem('authToken', seedToken);
        window.localStorage.setItem('authUsername', seedUser);
      },
      [token, username]
    );

    await page.goto('/table');
    await expect(page.getByRole('button', { name: '新增表' })).toBeHidden();
  } finally {
    await request.delete(`/api/v1/users/${username}`, { headers: auth });
  }
});

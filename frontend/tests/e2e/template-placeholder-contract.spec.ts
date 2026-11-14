import { expect, test, type APIRequestContext } from '@playwright/test';
import { apiLogin } from './helpers/auth';

interface SysItem {
  dictCode: number;
  itemKey: string;
  itemValue: string;
  remark?: string | null;
}

const authHeaders = (token: string) => ({ Authorization: `Bearer ${token}` });

async function getTemplates(
  request: APIRequestContext,
  token: string
): Promise<Record<string, SysItem>> {
  const res = await request.get('/api/v1/settings/job-templates', {
    headers: authHeaders(token),
  });
  expect(res.ok()).toBeTruthy();
  return (await res.json()) as Record<string, SysItem>;
}

async function putTemplates(
  request: APIRequestContext,
  token: string,
  items: SysItem[]
): Promise<{ status: number; text: string }> {
  const res = await request.put('/api/v1/settings/job-templates', {
    headers: { ...authHeaders(token), 'Content-Type': 'application/json' },
    data: items,
  });
  return { status: res.status(), text: await res.text() };
}

test('backend rejects any change to the template placeholder contract', async ({ request }) => {
  const token = await apiLogin(request);
  const before = await getTemplates(request, token);
  const wR = before.wR;
  expect(wR).toBeTruthy();
  expect(wR.itemValue).toContain('${name}');

  // Renaming a placeholder is a missing + extra token and must be refused.
  const renamed = { ...wR, itemValue: wR.itemValue.replace('${name}', '${pluginName}') };
  let result = await putTemplates(request, token, [renamed]);
  expect(result.status).toBe(400);
  expect(result.text).toContain('占位符');

  // Removing a required placeholder must be refused.
  const removed = { ...wR, itemValue: wR.itemValue.replace(/\$\{column\}/, '') };
  result = await putTemplates(request, token, [removed]);
  expect(result.status).toBe(400);

  // A save that keeps the placeholder set intact must still be accepted (round-trip).
  result = await putTemplates(request, token, [wR]);
  expect(result.status).toBe(200);
});

test('the template editor marks placeholders as a locked contract', async ({ page, request }) => {
  test.setTimeout(90_000);
  await page.setViewportSize({ width: 1440, height: 1000 });
  await apiLogin(request);

  await page.goto('/sys-settings');
  await expect(page.getByRole('heading', { name: '作业模板' })).toBeVisible({ timeout: 20_000 });

  await page.locator('.editor-tab', { hasText: 'RDBMS 写入' }).click();
  const editor = page.locator('textarea.editor-input');
  const original = await editor.inputValue();
  expect(original).toContain('${name}');
  await expect(page.locator('.slots-label')).toContainText('契约占位符');

  // Renaming a placeholder surfaces the violation without saving.
  await editor.fill(original.replace('${name}', '${nameRenamed}'));
  await expect(page.locator('.editor-contract-error')).toBeVisible();
  await expect(page.locator('.editor-contract-error')).toContainText('nameRenamed');
  await expect(page.locator('.editor-contract-error')).toContainText('name');

  // Restore so the page is left clean.
  await editor.fill(original);
  await expect(page.locator('.editor-contract-error')).toHaveCount(0);
});

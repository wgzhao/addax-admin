import { expect, test, type APIRequestContext } from '@playwright/test';
import { apiLogin } from './helpers/auth';

interface TargetRow {
  id: number;
  code: string;
  name: string;
  targetType: string;
  writerTemplateKey?: string | null;
  connectConfig?: string | null;
}

const authHeaders = (token: string) => ({ Authorization: `Bearer ${token}` });

async function findTarget(
  request: APIRequestContext,
  token: string,
  code: string
): Promise<TargetRow | undefined> {
  const res = await request.get('/api/v1/targets', { headers: authHeaders(token) });
  expect(res.ok()).toBeTruthy();
  const targets = (await res.json()) as TargetRow[];
  return targets.find(target => target.code === code);
}

test('Hive settings live on the HDFS target endpoint, not in system settings', async ({
  page,
  request,
}) => {
  test.setTimeout(90_000);
  await page.setViewportSize({ width: 1440, height: 1000 });
  const token = await apiLogin(request);
  const code = `E2E_HIVE_${Date.now()}`;

  try {
    // 1. The global HiveServer2 / HDFS defaults are gone; operational settings remain.
    await page.goto('/sys-settings');
    await expect(page.getByRole('heading', { name: '运行基础' })).toBeVisible({ timeout: 20_000 });
    await expect(page.getByRole('heading', { name: '作业模板' })).toBeVisible();
    await expect(page.getByText('HiveServer2')).toHaveCount(0);
    await expect(page.getByText('HDFS 目录前缀')).toHaveCount(0);
    await expect(page.getByText('默认 HDFS 存储格式')).toHaveCount(0);
    await expect(page.locator('#cfg-hive')).toHaveCount(0);

    // 2. The target dialog owns the HiveServer2 connection and HDFS writer defaults.
    await page.goto('/target?action=create');
    const dialog = page.getByRole('dialog');
    await expect(dialog).toBeVisible();

    await page.locator('#target-code').fill(code);
    await page.locator('#target-name').fill('E2E Hive Target');
    await page.locator('#target-url').fill('jdbc:hive2://127.0.0.1:1/default');
    await expect(page.locator('#target-default-fs')).toBeVisible();
    await page.locator('#target-default-fs').fill('hdfs://nameservice1');
    await expect(page.locator('#target-default-fs')).toHaveValue('hdfs://nameservice1');
    await page.locator('#target-hdfs-prefix').fill('/warehouse');
    await page.locator('#target-hadoop-config').fill('{"dfs.replication":"2"}');

    await dialog.getByRole('button', { name: '查看连接 JSON' }).click();
    const connectJson = page.locator('#target-connect-json');
    await expect(connectJson).toBeVisible();
    await expect(connectJson).toHaveValue(/hdfs:\/\/nameservice1/);
    expect(JSON.parse(await connectJson.inputValue())).toMatchObject({
      url: 'jdbc:hive2://127.0.0.1:1/default',
      defaultFS: 'hdfs://nameservice1',
      hdfsPrefix: '/warehouse',
      storageFormat: 'orc',
      compressFormat: 'snappy',
      hadoopConfig: { 'dfs.replication': '2' },
    });

    await dialog.getByRole('button', { name: '保存' }).click();
    await expect(page.getByText('保存成功')).toBeVisible({ timeout: 20_000 });
    // The list refreshes asynchronously after the dialog closes; reload for a deterministic view.
    await page.reload();

    // 3. The saved endpoint round-trips through the API and the edit dialog.
    const saved = await findTarget(request, token, code);
    expect(saved, 'created target should be listed').toBeTruthy();
    expect(saved!.writerTemplateKey).toBe('wH');
    expect(JSON.parse(saved!.connectConfig || '{}')).toMatchObject({
      defaultFS: 'hdfs://nameservice1',
      hdfsPrefix: '/warehouse',
    });

    await page.getByLabel('搜索编码 / 名称 / 模板键').fill(code);
    const row = page.locator('tbody tr').filter({ hasText: code }).first();
    await expect(row).toBeVisible({ timeout: 20_000 });
    await row.getByRole('button', { name: '编辑' }).click();
    await expect(page.locator('#target-default-fs')).toHaveValue('hdfs://nameservice1');
    await expect(page.locator('#target-url')).toHaveValue('jdbc:hive2://127.0.0.1:1/default');
    await dialog.getByRole('button', { name: '取消' }).click();

    // 4. A missing header service must fail as a 400 with an actionable message, not a 500.
    const testRes = await request.post(`/api/v1/targets/${saved!.id}/test-connect`, {
      headers: authHeaders(token),
    });
    expect(testRes.status()).toBe(400);
    const testBody = await testRes.text();
    expect(testBody.toLowerCase()).toMatch(/hive|连接|driver/);

    const summary = [
      `target=${code} id=${saved!.id} template=${saved!.writerTemplateKey}`,
      'system settings no longer render HiveServer2 or HDFS defaults',
      'HDFS target dialog round-trips url/defaultFS/hdfsPrefix/hadoopConfig',
      `test-connect status=${testRes.status()} body=${testBody.slice(0, 120)}`,
    ].join('\n');
    await test.info().attach('target-hive-config-summary', {
      body: summary,
      contentType: 'text/plain',
    });
  } finally {
    const target = await findTarget(request, token, code);
    if (target) {
      await request.delete(`/api/v1/targets/${target.id}`, {
        headers: authHeaders(token),
      });
    }
  }
});

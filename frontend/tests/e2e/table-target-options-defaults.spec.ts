import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { expect, test, type Page, type Route } from '@playwright/test';

// A non-partitioned table must stay non-partitioned when an unrelated field is saved. The Hive
// product defaults (logdate binding + PLATFORM mode) belong to the new-table flow; merging them into
// an existing row silently rewrote the definition and made every run fail at ALTER TABLE ADD PARTITION.
const artifactDir = fileURLToPath(new URL('./artifacts', import.meta.url));

const token = [
  Buffer.from(JSON.stringify({ alg: 'none', typ: 'JWT' })).toString('base64url'),
  Buffer.from(JSON.stringify({ sub: 'admin', exp: Math.floor(Date.now() / 1000) + 3600 })).toString(
    'base64url'
  ),
  'signature',
].join('.');

const tableId = 1594;

const table = {
  id: tableId,
  sid: 3,
  name: '杭州中间库',
  sourceDb: 'db_permissions',
  sourceTable: 't_permission_detail',
  targetDb: 'ods',
  targetTable: 'ods_db_permissions_t_permission_detail_0db',
  targetId: 1,
  targetName: 'HDFS(默认)',
  targetType: 'HDFS',
  targetWriterTemplateKey: 'wH',
  targetEnabled: true,
  startAt: '01:15:00',
  maxRuntime: null,
  retryCnt: 3,
  status: 'Y',
  writeMode: 'overwrite',
  splitPk: '',
  autoPk: true,
  filter: '1=1',
  targetOptions: {
    hdfs: {
      partKind: 'D',
      partName: '',
      partFormat: 'yyyyMMdd',
      storageFormat: 'orc',
      compressFormat: 'lz4',
    },
    rdbms: {},
    doris: {},
  },
  readerPluginConfig: null,
  writerPluginConfig: null,
  createdAt: '2025-03-27 00:00:00',
  updatedAt: '2026-11-25 08:11:39',
  startTime: '2026-11-25 08:03:55',
  endTime: '2026-11-25 08:11:39',
  duration: 464,
  enabled: true,
  dbType: 'mysql',
  calendar: null,
  sourceStartAt: '22:30:00',
  maxConcurrency: 20,
  schemaManagementMode: 'MANAGED',
  targetSchemaFingerprint: null,
  tblComment: null,
};

async function mockSession(page: Page) {
  await page.addInitScript(
    ([authToken, username]) => {
      localStorage.setItem('authToken', authToken);
      localStorage.setItem('authUsername', username);
    },
    [token, 'admin']
  );
}

async function mockApi(page: Page, onSave: (payload: Record<string, any>) => void) {
  await page.route('**/api/v1/**', async (route: Route) => {
    const request = route.request();
    const pathname = new URL(request.url()).pathname;
    const method = request.method();

    if (pathname === `/api/v1/tables/${tableId}` && method === 'GET') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(table),
      });
      return;
    }
    if (pathname === `/api/v1/tables/${tableId}` && method === 'PUT') {
      const payload = request.postDataJSON() as Record<string, any>;
      onSave(payload);
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ ...table, ...payload }),
      });
      return;
    }

    let body: unknown = [];
    if (pathname === '/api/v1/users/me') {
      body = { username: 'admin', enabled: true, authorities: ['ROLE_ADMIN'] };
    }
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(body),
    });
  });
}

test('saving a non-partitioned table keeps the product partition defaults out', async ({ page }) => {
  test.setTimeout(60_000);
  await page.setViewportSize({ width: 1440, height: 1200 });
  await mockSession(page);
  const saves: Record<string, any>[] = [];
  await mockApi(page, payload => saves.push(payload));

  await page.goto(`/table/detail/${tableId}?tab=info`);
  await expect(page.getByRole('heading', { name: '执行配置' })).toBeVisible({ timeout: 15_000 });

  await page.getByRole('button', { name: '编辑配置' }).click();
  // The stored bag has no bindings, so the form must not invent a logdate row.
  await expect(page.getByText('未配置分区键，目标表将按非分区表校验。')).toBeVisible();
  await expect(page.getByLabel('目标分区键')).toHaveCount(0);

  const filterBlock = page
    .locator('.field-block')
    .filter({ has: page.getByText('过滤规则', { exact: true }) })
    .first();
  await filterBlock.locator('input').fill('1=1 and id > 0');

  await page.getByRole('button', { name: '保存更改' }).click();
  await expect.poll(() => saves.length).toBe(1);

  const saved = saves[0];
  const hdfs = saved.targetOptions?.hdfs ?? {};
  expect(saved.filter).toBe('1=1 and id > 0');
  expect(hdfs.partitionBindings ?? []).toHaveLength(0);
  expect(hdfs.partitionCreationMode).toBeUndefined();
  expect(hdfs.partName).toBe('');
  expect(hdfs.storageFormat).toBe('orc');
  expect(hdfs.compressFormat).toBe('lz4');

  fs.mkdirSync(artifactDir, { recursive: true });
  const artifactPath = path.join(artifactDir, 'table-target-options-defaults.json');
  fs.writeFileSync(
    artifactPath,
    JSON.stringify(
      {
        savedTargetOptions: saved.targetOptions,
        checks: [
          'a non-partitioned table shows the empty partition state in the edit form',
          'saving an unrelated field does not add a logdate partition binding',
          'saving an unrelated field does not add partitionCreationMode or a partName',
        ],
      },
      null,
      2
    )
  );
  await test.info().attach('table-target-options-defaults', {
    path: artifactPath,
    contentType: 'application/json',
  });
});

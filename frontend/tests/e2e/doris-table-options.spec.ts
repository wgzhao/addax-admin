import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { expect, test, type Page, type Route } from '@playwright/test';

const artifactDir = fileURLToPath(new URL('./artifacts', import.meta.url));

const token = [
  Buffer.from(JSON.stringify({ alg: 'none', typ: 'JWT' })).toString('base64url'),
  Buffer.from(JSON.stringify({ sub: 'admin', exp: Math.floor(Date.now() / 1000) + 3600 })).toString(
    'base64url'
  ),
  'signature',
].join('.');

const tableId = 131;

const dorisTable = {
  id: tableId,
  sid: 7,
  name: 'Doris 目标端 E2E',
  sourceDb: 'ods',
  sourceTable: 'customer_order',
  targetDb: 'dwd',
  targetTable: 'dwd_customer_order',
  targetId: 23,
  targetName: 'Doris 数仓',
  targetType: 'DORIS',
  targetWriterTemplateKey: 'wD',
  targetEnabled: true,
  startAt: null,
  maxRuntime: 7200,
  retryCnt: 2,
  status: 'U',
  writeMode: 'overwrite',
  splitPk: 'id',
  autoPk: true,
  filter: '1=1',
  targetOptions: {
    hdfs: {},
    rdbms: { businessDateColumn: 'biz_date', createPrimaryKey: true },
    doris: {
      keyModel: 'AUTO',
      keyColumns: [],
      distributionType: 'HASH',
      distributionColumns: [],
      buckets: 'AUTO',
      replicationNum: 1,
      partitionMode: 'NONE',
      partitionColumn: '',
      partitionStart: -30,
      partitionEnd: 3,
      partitionPrefix: 'p',
    },
  },
  readerPluginConfig: null,
  writerPluginConfig: null,
  createdAt: '2026-11-20 10:00:00',
  updatedAt: '2026-11-20 10:00:00',
  enabled: true,
};

const targets = [
  {
    id: 23,
    code: 'DORIS_E2E',
    name: 'Doris 数仓',
    targetType: 'DORIS',
    writerTemplateKey: 'wD',
    enabled: true,
  },
];

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
        body: JSON.stringify(dorisTable),
      });
      return;
    }
    if (pathname === `/api/v1/tables/${tableId}` && method === 'PUT') {
      const payload = request.postDataJSON() as Record<string, any>;
      onSave(payload);
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ ...dorisTable, ...payload }),
      });
      return;
    }

    let body: unknown = [];
    if (pathname === '/api/v1/targets') {
      body = targets;
    } else if (pathname === '/api/v1/users/me') {
      body = { username: 'admin', enabled: true, authorities: ['ROLE_ADMIN'] };
    }
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(body),
    });
  });
}

test('Doris table detail exposes the managed model and persists it with the rdbms options', async ({
  page,
}) => {
  test.setTimeout(60_000);
  await page.setViewportSize({ width: 1440, height: 1200 });
  await mockSession(page);
  const saves: Record<string, any>[] = [];
  await mockApi(page, payload => saves.push(payload));

  await page.goto(`/table/detail/${tableId}?tab=info`);
  await expect(page.getByRole('heading', { name: '执行配置' })).toBeVisible({ timeout: 15_000 });
  await expect(page.getByText('Doris 数据模型', { exact: true })).toBeVisible();
  await expect(page.getByText('源端主键', { exact: true })).toBeVisible();

  await page.getByRole('button', { name: '编辑配置' }).click();
  await expect(page.getByText('Doris 表模型', { exact: true })).toBeVisible();
  // The RDBMS primary-key switch is meaningless for Doris: the key model is the decision.
  await expect(page.getByText('按源端主键建主键')).toHaveCount(0);
  await expect(page.getByText('键列（可选）', { exact: true })).toBeVisible();
  await expect(page.getByText('分桶数', { exact: true })).toBeVisible();
  await expect(page.getByText('分区方式', { exact: true })).toBeVisible();

  const modelField = page
    .locator('.field-block')
    .filter({ has: page.getByText('数据模型', { exact: true }) })
    .first();
  await modelField.locator('.v-field').click();
  await page
    .locator('.v-overlay-container .v-list-item')
    .filter({ hasText: 'DUPLICATE（追加写入）' })
    .click();

  await page.getByRole('button', { name: '保存更改' }).click();
  await expect.poll(() => saves.length).toBe(1);
  const saved = saves[0];
  expect(saved.targetOptions.doris.keyModel).toBe('DUPLICATE');
  // The shared snapshot business-date column must survive the Doris save.
  expect(saved.targetOptions.rdbms.businessDateColumn).toBe('biz_date');
  expect(saved.targetOptions.doris.buckets).toBe('AUTO');
  expect(saved.targetOptions.doris.partitionMode).toBe('NONE');

  fs.mkdirSync(artifactDir, { recursive: true });
  const artifactPath = path.join(artifactDir, 'doris-table-options.json');
  fs.writeFileSync(
    artifactPath,
    JSON.stringify(
      {
        savedTargetOptions: saved.targetOptions,
        checks: [
          'Doris table detail renders the Doris model instead of the RDBMS primary-key item',
          'the edit form exposes key columns, bucket count and partition mode',
          'saving persists doris.keyModel while keeping rdbms.businessDateColumn',
        ],
      },
      null,
      2
    )
  );
  await test.info().attach('doris-table-options', {
    path: artifactPath,
    contentType: 'application/json',
  });
});

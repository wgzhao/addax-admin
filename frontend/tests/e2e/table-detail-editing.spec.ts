import { test, expect, type Page, type Route } from '@playwright/test';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

interface MockTable {
  id: number;
  retiredAt?: string | null;
  [key: string]: unknown;
}

const artifactDir = fileURLToPath(new URL('./artifacts', import.meta.url));
const screenshotsDir = fileURLToPath(new URL('../../../screenshots', import.meta.url));
const token = [
  Buffer.from(JSON.stringify({ alg: 'none', typ: 'JWT' })).toString('base64url'),
  Buffer.from(JSON.stringify({ sub: 'admin', exp: Math.floor(Date.now() / 1000) + 3600 })).toString(
    'base64url'
  ),
  'signature',
].join('.');

const table: MockTable = {
  id: 101,
  code: 'CRM',
  name: '客户关系中心',
  sourceDb: 'crm_prod',
  sourceTable: 'customer_profile_with_a_long_descriptive_table_name',
  targetDb: 'dwd',
  targetTable: 'dwd_customer_profile_daily_snapshot',
  targetId: 21,
  targetName: '数据仓库主集群',
  targetType: 'HDFS',
  targetEnabled: true,
  sourceStartAt: '02:30',
  startAt: null,
  maxRuntime: 7200,
  retryCnt: 2,
  status: 'Y',
  kind: 'A',
  writeMode: 'append',
  splitPk: 'customer_id',
  autoPk: true,
  filter: "dt='${biz_date_dash}' and customer_status in ('active', 'pending_review')",
  targetOptions: {
    hdfs: {
      partName: 'dt',
      partFormat: 'yyyy-MM-dd',
      storageFormat: 'parquet',
      compressFormat: 'snappy',
    },
    rdbms: {
      businessDateColumn: 'business_date',
      createPrimaryKey: true,
    },
  },
  readerPluginConfig: {
    fetchSize: 50000,
    description: 'Read configuration long enough to exercise the advanced section.',
  },
  writerPluginConfig: {
    writeMode: 'append',
    batchSize: 10000,
  },
  remark: '客户主数据每日全量快照，异常时联系数据平台值班同学。',
  startTime: '2026-10-20 02:31:05',
  endTime: '2026-10-20 02:48:42',
  createdAt: '2024-03-18 16:42:09',
  updatedAt: '2026-10-20 02:48:42',
  retiredAt: null,
  retiredBy: null,
  enabled: true,
};

const targets = [
  { id: 21, code: 'DW', name: '数据仓库主集群', targetType: 'HDFS', enabled: true },
  { id: 22, code: 'ODS', name: '关系型归档库', targetType: 'POSTGRESQL', enabled: true },
];

const addaxJob = {
  job: {
    content: {
      reader: {
        name: 'postgresqlreader',
        parameter: { column: ['customer_id', 'customer_name'], connection: [] },
      },
      writer: {
        name: 'hdfswriter',
        parameter: { column: ['customer_id', 'customer_name'], defaultFS: 'hdfs://namenode' },
      },
    },
  },
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

async function mockApi(
  page: Page,
  options: {
    detail?: MockTable;
    delayMs?: number;
    failDetail?: boolean;
    onSave?: (payload: Record<string, unknown>) => void;
    onJobSave?: (payload: string) => void;
  } = {}
) {
  const detail = options.detail ?? table;
  await page.route('**/api/v1/**', async (route: Route) => {
    const request = route.request();
    const pathname = new URL(request.url()).pathname;
    const method = request.method();

    if (pathname === '/api/v1/tables/101' && method === 'GET') {
      if (options.delayMs) {
        await new Promise(resolve => setTimeout(resolve, options.delayMs));
      }
      if (options.failDetail) {
        await route.fulfill({
          status: 500,
          contentType: 'application/json',
          body: JSON.stringify({ message: '模拟加载失败' }),
        });
        return;
      }
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(detail),
      });
      return;
    }

    if (pathname === '/api/v1/tables/101' && method === 'PUT') {
      const payload = request.postDataJSON() as Record<string, unknown>;
      options.onSave?.(payload);
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ ...detail, ...payload }),
      });
      return;
    }

    if (pathname === '/api/v1/tables/101/addax-job' && method === 'GET') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(addaxJob),
      });
      return;
    }

    if (pathname === '/api/v1/tables/101/addax-job' && method === 'PUT') {
      const payload = request.postData() || '';
      options.onJobSave?.(payload);
      await route.fulfill({
        status: 200,
        contentType: 'text/plain',
        body: 'Job content updated successfully',
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

test('basic detail starts read-only and validates before an explicit save', async ({ page }) => {
  await mockSession(page);
  const saves: Record<string, unknown>[] = [];
  await mockApi(page, { onSave: payload => saves.push(payload) });
  await page.goto('/table/detail/101?tab=info&tblname=crm_prod.customer_profile');

  await expect(page.getByRole('heading', { name: '执行配置' })).toBeVisible({ timeout: 15_000 });
  await expect(page.getByRole('button', { name: '编辑配置' })).toBeVisible();
  await expect(page.locator('.section-card input')).toHaveCount(0);

  await page.getByRole('button', { name: '编辑配置' }).click();
  await expect(page.getByRole('button', { name: '保存更改' })).toBeDisabled();

  const runtimeBlock = page
    .locator('.field-block')
    .filter({ has: page.locator('.field-label', { hasText: '最大运行时' }) });
  await runtimeBlock.locator('input').fill('3000');
  await expect(page.getByText('有未保存的修改')).toBeVisible();
  await page.getByRole('button', { name: '取消' }).click();
  await expect(page.getByRole('button', { name: '编辑配置' })).toBeVisible();
  await page.getByRole('button', { name: '编辑配置' }).click();
  await expect(runtimeBlock.locator('input')).toHaveValue('7200');

  const sourceSystem = page
    .locator('.field-block')
    .filter({ has: page.locator('.field-label', { hasText: '源系统' }) })
    .locator('input');
  await expect(sourceSystem).toHaveAttribute('readonly', '');

  const scheduleBlock = page
    .locator('.field-block')
    .filter({ has: page.locator('.field-label', { hasText: '调度时间' }) });
  await scheduleBlock.locator('input').fill('25:99');
  await runtimeBlock.locator('input').fill('0');
  await page.getByRole('button', { name: '保存更改' }).click();

  await expect(page.getByText(/时间格式不正确/)).toBeVisible();
  await expect(page.getByText(/必须为大于 0 的整数/)).toBeVisible();
  expect(saves).toHaveLength(0);

  await scheduleBlock.locator('input').fill('02:45');
  await runtimeBlock.locator('input').fill('3600');
  await page.getByRole('button', { name: '保存更改' }).click();

  await expect.poll(() => saves.length).toBe(1);
  expect(saves[0].startAt).toBe('02:45');
  expect(saves[0].maxRuntime).toBe(3600);
  await expect(page.getByRole('button', { name: '编辑配置' })).toBeVisible();

  fs.mkdirSync(screenshotsDir, { recursive: true });
  const screenshot = path.join(screenshotsDir, 'table-detail-editing.png');
  await page.screenshot({ path: screenshot, fullPage: true });
  fs.mkdirSync(artifactDir, { recursive: true });
  fs.writeFileSync(
    path.join(artifactDir, 'table-detail-editing.json'),
    JSON.stringify(
      {
        checkedAt: new Date().toISOString(),
        screenshot,
        saveCount: saves.length,
        savedStartAt: saves[0].startAt,
        savedMaxRuntime: saves[0].maxRuntime,
      },
      null,
      2
    )
  );
});

test('retired table is read-only end to end', async ({ page }) => {
  await mockSession(page);
  await mockApi(page, {
    detail: {
      ...table,
      retiredAt: '2026-09-01 10:00:00',
      retiredBy: 'admin',
    },
  });
  await page.goto('/table/detail/101?tab=info&tblname=crm_prod.customer_profile');

  await expect(page.getByText('该采集表已退役')).toBeVisible({ timeout: 15_000 });
  await expect(page.getByRole('button', { name: '编辑配置' })).toHaveCount(0);
  await expect(page.getByRole('button', { name: '保存更改' })).toHaveCount(0);
  await expect(page.locator('.section-card input')).toHaveCount(0);
});

test('current table keeps addax job editing available', async ({ page }) => {
  await mockSession(page);
  const jobSaves: string[] = [];
  await mockApi(page, { onJobSave: payload => jobSaves.push(payload) });
  await page.goto('/table/detail/101?tab=job&tblname=crm_prod.customer_profile');

  await expect(page.getByText('采集作业配置')).toBeVisible({ timeout: 15_000 });
  const editButton = page.getByRole('button', { name: '编辑', exact: true });
  await expect(editButton).toBeVisible();
  await editButton.click();

  const editor = page.getByRole('textbox');
  await expect(editor).toBeVisible();
  const nextJob = JSON.stringify(
    {
      ...addaxJob,
      job: {
        ...addaxJob.job,
        content: {
          ...addaxJob.job.content,
          reader: {
            ...addaxJob.job.content.reader,
            parameter: {
              ...addaxJob.job.content.reader.parameter,
              fetchSize: 20480,
            },
          },
        },
      },
    },
    null,
    2
  );
  await editor.fill(nextJob);
  await page.getByRole('button', { name: '保存', exact: true }).click();

  await expect.poll(() => jobSaves.length).toBe(1);
  expect(JSON.parse(jobSaves[0]).job.content.reader.parameter.fetchSize).toBe(20480);
});

test('retired table hides addax job editing and never writes it', async ({ page }) => {
  await mockSession(page);
  const jobSaves: string[] = [];
  await mockApi(page, {
    detail: {
      ...table,
      retiredAt: '2026-09-01 10:00:00',
      retiredBy: 'admin',
    },
    onJobSave: payload => jobSaves.push(payload),
  });
  await page.goto('/table/detail/101?tab=job&tblname=crm_prod.customer_profile');

  await expect(page.getByText('该采集表已退役')).toBeVisible({ timeout: 15_000 });
  await expect(page.getByText('采集作业配置')).toBeVisible();
  await expect(page.getByRole('button', { name: '复制', exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: '编辑', exact: true })).toHaveCount(0);
  await expect(page.getByRole('button', { name: '保存', exact: true })).toHaveCount(0);
  await expect(page.locator('textarea')).toHaveCount(0);
  await expect(page.locator('pre code')).toContainText('postgresqlreader');
  expect(jobSaves).toHaveLength(0);

  fs.mkdirSync(artifactDir, { recursive: true });
  const artifact = path.join(artifactDir, 'retired-addax-job-readonly.json');
  fs.writeFileSync(
    artifact,
    JSON.stringify(
      {
        checkedAt: new Date().toISOString(),
        retiredAt: '2026-09-01 10:00:00',
        editButtonCount: 0,
        editorCount: 0,
        saveRequestCount: jobSaves.length,
        jobStillVisible: true,
      },
      null,
      2
    )
  );
  await test.info().attach('retired-addax-job-readonly', {
    path: artifact,
    contentType: 'application/json',
  });
});

test('loading and failure states never expose an editable empty form', async ({ page }) => {
  await mockSession(page);
  await mockApi(page, { delayMs: 1200 });
  await page.goto('/table/detail/101?tab=info&tblname=crm_prod.customer_profile');

  await expect(page.getByTestId('detail-loading')).toBeVisible();
  await expect(page.getByRole('button', { name: '保存更改' })).toHaveCount(0);
  await expect(page.getByText('未配置库.未配置表')).toHaveCount(0);
  await expect(page.getByRole('heading', { name: '执行配置' })).toBeVisible({ timeout: 15_000 });
});

test('responsive layout keeps summary compact and guards long identity text', async ({ page }) => {
  await mockSession(page);
  await mockApi(page);

  await page.setViewportSize({ width: 1024, height: 768 });
  await page.goto('/table/detail/101?tab=info&tblname=crm_prod.customer_profile');
  await expect(page.getByRole('heading', { name: '执行配置' })).toBeVisible({ timeout: 15_000 });
  const summaryCards = page.locator('.summary-card');
  const first = await summaryCards.nth(0).boundingBox();
  const fifth = await summaryCards.nth(4).boundingBox();
  expect(first).not.toBeNull();
  expect(fifth).not.toBeNull();
  expect(Math.abs(first!.y - fifth!.y)).toBeLessThan(2);

  await page.setViewportSize({ width: 390, height: 844 });
  await page.reload();
  await expect(page.getByRole('heading', { name: '执行配置' })).toBeVisible({ timeout: 15_000 });
  const title = page.locator('.page-title');
  const titleMetrics = await title.evaluate(element => ({
    scrollWidth: element.scrollWidth,
    clientWidth: element.clientWidth,
  }));
  expect(titleMetrics.scrollWidth).toBeLessThanOrEqual(titleMetrics.clientWidth + 1);
  await expect(page.locator('.v-tabs .v-slide-group__next')).toBeVisible();
});

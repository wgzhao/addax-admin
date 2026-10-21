import { test, expect, type Page } from '@playwright/test';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const artifactDir = fileURLToPath(new URL('./artifacts', import.meta.url));
const screenshotsDir = fileURLToPath(new URL('../../../screenshots', import.meta.url));
const token = [
  Buffer.from(JSON.stringify({ alg: 'none', typ: 'JWT' })).toString('base64url'),
  Buffer.from(JSON.stringify({ sub: 'admin', exp: Math.floor(Date.now() / 1000) + 3600 })).toString(
    'base64url'
  ),
  'signature',
].join('.');

const table = {
  id: 101,
  code: 'CRM',
  name: '客户关系中心',
  sourceDb: 'crm_prod',
  sourceTable: 'customer_profile',
  targetDb: 'dwd',
  targetTable: 'dwd_customer_profile_daily_snapshot',
  targetId: 21,
  targetName: '数据仓库主集群',
  targetType: 'HDFS',
  sourceStartAt: '02:30',
  startAt: null,
  maxRuntime: 7200,
  retryCnt: 2,
  status: 'Y',
  kind: 'A',
  writeMode: 'append',
  splitPk: 'customer_id',
  autoPk: true,
  filter: "dt='${biz_date_dash}'",
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
  readerPluginConfig: {},
  writerPluginConfig: {},
  remark: '',
  createdAt: '2024-03-18 16:42:09',
  updatedAt: '2026-10-20 02:48:42',
  retiredAt: null,
  enabled: true,
};

async function preparePage(page: Page) {
  await page.addInitScript(
    ([authToken]) => {
      localStorage.setItem('authToken', authToken);
      localStorage.setItem('authUsername', 'admin');
    },
    [token]
  );
  await page.route('**/api/v1/**', async route => {
    const pathname = new URL(route.request().url()).pathname;
    let body: unknown = [];
    if (pathname === '/api/v1/tables/101') body = table;
    if (pathname === '/api/v1/targets') {
      body = [{ id: 21, code: 'DW', name: '数据仓库主集群', targetType: 'HDFS', enabled: true }];
    }
    if (pathname === '/api/v1/users/me') {
      body = { username: 'admin', enabled: true, authorities: ['ROLE_ADMIN'] };
    }
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(body),
    });
  });
  await page.goto('/table/detail/101?tab=info&tblname=crm_prod.customer_profile');
  await expect(page.getByRole('heading', { name: '执行配置' })).toBeVisible({ timeout: 15_000 });
}

async function dialogGeometry(page: Page) {
  return page.locator('[role="dialog"]').evaluate(dialog => {
    const header = dialog.querySelector('.help-dialog__header')?.getBoundingClientRect();
    const footer = dialog.querySelector('.help-dialog__footer')?.getBoundingClientRect();
    const body = dialog.querySelector('.help-dialog__body') as HTMLElement | null;
    const inViewport = (rect?: DOMRect) =>
      !!rect && rect.top >= 0 && rect.bottom <= window.innerHeight;
    return {
      headerVisible: inViewport(header),
      footerVisible: inViewport(footer),
      bodyClientHeight: body?.clientHeight || 0,
      bodyScrollHeight: body?.scrollHeight || 0,
      bodyScrollable: !!body && body.scrollHeight > body.clientHeight,
    };
  });
}

test('dynamic naming help is searchable, copyable and keeps controls fixed', async ({
  page,
  context,
}) => {
  await context.grantPermissions(['clipboard-read', 'clipboard-write']);
  await preparePage(page);
  await page.getByRole('button', { name: '动态命名' }).click();

  await expect(page.getByRole('heading', { name: '源表动态命名说明', level: 2 })).toBeVisible();
  await expect(page.getByText('业务日期可能不等于实际运行日期')).toBeVisible();
  await expect(page.getByRole('button', { name: '关闭' }).first()).toBeVisible();

  const initialGeometry = await dialogGeometry(page);
  expect(initialGeometry.headerVisible).toBe(true);
  expect(initialGeometry.footerVisible).toBe(true);
  expect(initialGeometry.bodyScrollable).toBe(true);

  const search = page.getByPlaceholder('搜索变量名、含义或日期格式');
  await search.fill('datetime_0');
  await expect(page.getByText('biz_datetime_0_dash', { exact: true })).toBeVisible();
  await expect(page.getByText('biz_date_short', { exact: true })).toHaveCount(0);
  await expect(page.getByText(/找到 4 个变量/)).toBeVisible();

  await search.fill('not-found-variable');
  await expect(page.getByText('没有匹配的动态变量')).toBeVisible();
  await search.fill('');

  const copyButton = page.getByRole('button', { name: '复制 biz_date_short' });
  await copyButton.click();
  await expect(copyButton).toHaveAttribute('aria-label', '已复制 biz_date_short');
  expect(await page.evaluate(() => navigator.clipboard.readText())).toBe('biz_date_short');

  await page.screenshot({
    path: path.join(screenshotsDir, 'dynamic-name-help-dialog-desktop.png'),
    fullPage: false,
  });

  await page.getByRole('button', { name: '关闭' }).last().click();
  await page.setViewportSize({ width: 390, height: 844 });
  await page.reload();
  await expect(page.getByRole('heading', { name: '执行配置' })).toBeVisible({ timeout: 15_000 });
  await page.getByRole('button', { name: '动态命名' }).click();
  const mobileGeometry = await dialogGeometry(page);
  expect(mobileGeometry.headerVisible).toBe(true);
  expect(mobileGeometry.footerVisible).toBe(true);
  expect(mobileGeometry.bodyScrollable).toBe(true);
  await page.screenshot({
    path: path.join(screenshotsDir, 'dynamic-name-help-dialog-mobile.png'),
    fullPage: false,
  });
});

test('filter help prioritises common recipes and keeps the footer reachable on mobile', async ({
  page,
  context,
}) => {
  await context.grantPermissions(['clipboard-read', 'clipboard-write']);
  await page.setViewportSize({ width: 390, height: 844 });
  await preparePage(page);
  await page.getByRole('button', { name: '过滤规则' }).click();

  await expect(page.getByRole('heading', { name: '过滤规则说明', level: 2 })).toBeVisible();
  await expect(page.getByText('留空时无需填写 1=1')).toBeVisible();
  await expect(page.getByText('从目标端上一业务日期分区读取')).toBeVisible();
  await expect(page.getByText('__max__id → id > 123456')).toBeVisible();

  const geometry = await dialogGeometry(page);
  expect(geometry.headerVisible).toBe(true);
  expect(geometry.footerVisible).toBe(true);

  const copyButton = page.getByRole('button', { name: '复制 __max__id' });
  await copyButton.click();
  expect(await page.evaluate(() => navigator.clipboard.readText())).toBe('__max__id');

  await page.screenshot({
    path: path.join(screenshotsDir, 'filter-rule-help-dialog-mobile.png'),
    fullPage: false,
  });
  fs.mkdirSync(artifactDir, { recursive: true });
  fs.writeFileSync(
    path.join(artifactDir, 'help-dialogs.json'),
    JSON.stringify(
      {
        checkedAt: new Date().toISOString(),
        mobileFilterGeometry: geometry,
        screenshots: [
          'screenshots/dynamic-name-help-dialog-desktop.png',
          'screenshots/dynamic-name-help-dialog-mobile.png',
          'screenshots/filter-rule-help-dialog-mobile.png',
        ],
      },
      null,
      2
    )
  );
});

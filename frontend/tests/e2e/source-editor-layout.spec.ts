import { test, expect, type Page, type Route } from '@playwright/test';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const artifactDir = fileURLToPath(new URL('./artifacts', import.meta.url));
const token = [
  Buffer.from(JSON.stringify({ alg: 'none', typ: 'JWT' })).toString('base64url'),
  Buffer.from(JSON.stringify({ sub: 'admin', exp: Math.floor(Date.now() / 1000) + 3600 })).toString(
    'base64url'
  ),
  'signature',
].join('.');

const source = {
  id: 11,
  code: 'WG',
  name: '维格数仓',
  url: 'jdbc:postgresql://warehouse.internal:5432/analytics',
  username: 'etl_reader',
  pass: 'secret',
  startAt: '03:10',
  calendar: 'WORK_DAY',
  prerequisite: '',
  preScript: '',
  remark: '核心业务库，异常时联系数据平台值班同学。',
  enabled: true,
  maxConcurrency: 12,
  tableCount: 86,
  validTableCount: 74,
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

async function mockApi(page: Page, saves: Array<Record<string, unknown>>) {
  await page.route('**/api/v1/**', async (route: Route) => {
    const request = route.request();
    const pathname = new URL(request.url()).pathname;
    const method = request.method();

    if (pathname === '/api/v1/sources/11' && method === 'GET') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(source),
      });
      return;
    }

    if (pathname === '/api/v1/sources/11' && method === 'PUT') {
      saves.push(request.postDataJSON() as Record<string, unknown>);
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: request.postData() || '{}',
      });
      return;
    }

    if (pathname === '/api/v1/sources/test-connect' && method === 'POST') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: 'true',
      });
      return;
    }

    let body: unknown = [];
    if (pathname === '/api/v1/sources/enabled' || pathname === '/api/v1/sources') {
      body = [source];
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

test('source edit dialog keeps a clear hierarchy and actionable state across breakpoints', async ({
  page,
}) => {
  await mockSession(page);
  const saves: Array<Record<string, unknown>> = [];
  await mockApi(page, saves);
  await page.setViewportSize({ width: 1440, height: 900 });
  await page.goto('/source');

  const row = page.getByRole('row', { name: /WG/ });
  await expect(row).toBeVisible();
  await row.getByRole('button', { name: '编辑', exact: true }).click();

  const dialog = page.locator('.v-dialog').filter({ hasText: '编辑采集源' });
  const card = dialog.locator('.add-source-card');
  await expect(card).toBeVisible();
  await expect(dialog.locator('.source-context__code')).toHaveText('WG');
  await expect(dialog.locator('.source-context')).toContainText('调度已启用');
  await expect(dialog.locator('.source-context')).toContainText('03:10');
  await expect(dialog.locator('.source-section h3')).toHaveText(['基本信息', '连接信息', '备注']);

  const saveButton = dialog.getByRole('button', { name: '保存修改' });
  await expect(saveButton).toBeDisabled();
  await expect(dialog.locator('.ds-dialog-actions__state')).toContainText('修改后保存立即生效');

  const nameInput = dialog.locator('#src-name');
  await nameInput.fill('维格数仓（测试）');
  await expect(dialog.locator('.ds-dialog-actions__state')).toContainText('有未保存的修改');
  await expect(saveButton).toBeEnabled();
  await nameInput.fill(source.name);
  await expect(saveButton).toBeDisabled();

  await dialog.getByRole('button', { name: '测试连接' }).click();
  await expect(dialog.getByRole('status')).toContainText('连接成功，可以保存配置');

  await nameInput.fill('维格数仓（新）');
  await saveButton.click();
  await expect.poll(() => saves.length).toBe(1);
  expect(saves[0].name).toBe('维格数仓（新）');
  await expect(dialog).toBeHidden();

  await row.getByRole('button', { name: '编辑', exact: true }).click();
  await expect(card).toBeVisible();
  const desktopMetrics = await card.evaluate(element => {
    const rect = element.getBoundingClientRect();
    return { width: Math.round(rect.width), height: Math.round(rect.height) };
  });
  expect(desktopMetrics.width).toBeGreaterThan(980);

  await page.setViewportSize({ width: 1024, height: 768 });
  await page.waitForTimeout(200);
  const tabletMetrics = await page.evaluate(() => {
    const basic = document.querySelectorAll('.source-section')[0]?.getBoundingClientRect();
    const connection = document.querySelectorAll('.source-section')[1]?.getBoundingClientRect();
    return {
      sameRow: Boolean(basic && connection && Math.abs(basic.y - connection.y) < 2),
      horizontalOverflow: document.documentElement.scrollWidth > window.innerWidth,
    };
  });
  expect(tabletMetrics.sameRow).toBe(true);
  expect(tabletMetrics.horizontalOverflow).toBe(false);

  await page.setViewportSize({ width: 390, height: 844 });
  await page.waitForTimeout(250);
  const mobileMetrics = await card.evaluate(element => {
    const rect = element.getBoundingClientRect();
    const body = element.querySelector('.add-source-body') as HTMLElement;
    const actions = element.querySelector('.ds-dialog-actions') as HTMLElement;
    return {
      width: Math.round(rect.width),
      bodyClientHeight: body.clientHeight,
      bodyScrollHeight: body.scrollHeight,
      actionsBottom: Math.round(actions.getBoundingClientRect().bottom),
      viewportWidth: window.innerWidth,
      viewportHeight: window.innerHeight,
      documentScrollWidth: document.documentElement.scrollWidth,
      sectionsStacked:
        (element.querySelectorAll('.source-section')[1] as HTMLElement).getBoundingClientRect()
          .top >=
        (element.querySelectorAll('.source-section')[0] as HTMLElement).getBoundingClientRect()
          .bottom,
    };
  });
  expect(mobileMetrics.width).toBeLessThanOrEqual(358);
  expect(mobileMetrics.bodyScrollHeight).toBeGreaterThan(mobileMetrics.bodyClientHeight);
  expect(mobileMetrics.actionsBottom).toBeLessThanOrEqual(mobileMetrics.viewportHeight);
  expect(mobileMetrics.documentScrollWidth).toBeLessThanOrEqual(mobileMetrics.viewportWidth);
  expect(mobileMetrics.sectionsStacked).toBe(true);

  fs.mkdirSync(artifactDir, { recursive: true });
  const screenshot = path.join(artifactDir, 'source-editor-layout-mobile.png');
  await page.screenshot({ path: screenshot, fullPage: true });
  const artifact = path.join(artifactDir, 'source-editor-layout.json');
  fs.writeFileSync(
    artifact,
    JSON.stringify(
      {
        checkedAt: new Date().toISOString(),
        desktopMetrics,
        tabletMetrics,
        mobileMetrics,
        sections: ['基本信息', '连接信息', '备注'],
        savePayloadName: saves[0].name,
        screenshot,
      },
      null,
      2
    )
  );
  await test.info().attach('source-editor-layout', {
    path: artifact,
    contentType: 'application/json',
  });
});

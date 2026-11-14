import { test, expect, type Page } from '@playwright/test';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

// Regenerates the README/dashboard screenshots against a seeded demo database.
// The split hero is built here as well: the light and dark captures are
// composited along the anti-diagonal so the top-left stays light and the
// bottom-right stays dark.
//
// Run against the demo environment:
//   E2E_PASSWORD=... PLAYWRIGHT_BASE_URL=http://127.0.0.1:15080 \
//     npx playwright test dashboard-screenshots.spec.ts
const screenshotsDir = fileURLToPath(new URL('../../../screenshots', import.meta.url));
const artifactDir = fileURLToPath(new URL('./artifacts', import.meta.url));

test.use({ viewport: { width: 1440, height: 900 } });

async function openDashboard(page: Page, theme: 'light' | 'dark') {
  await page.addInitScript(value => localStorage.setItem('theme', value), theme);
  await page.goto('/');
  await expect(page.locator('.stat-tile').first()).toBeVisible({ timeout: 20_000 });
  await expect(page.locator('.v-window-item--active .pulse-rows')).toBeVisible({
    timeout: 20_000,
  });
  // Every chart in the active window must finish before the frame is captured.
  const charts = page.locator('.v-window-item--active [data-chart-state]:visible');
  for (let i = 0; i < (await charts.count()); i++) {
    await expect(charts.nth(i)).toHaveAttribute('data-chart-state', /ready|empty/, {
      timeout: 20_000,
    });
  }
  await page.waitForTimeout(600);
}

// The demo build carries a "开发" chip next to the brand; marketing screenshots
// should read as a delivered product rather than a dev server.
async function hideDevChrome(page: Page) {
  await page.addStyleTag({ content: '.brand-env { display: none !important; }' });
}

// Full-page captures expand past the viewport; Vuetify keeps the footer fixed
// with an inline style, which would freeze it over the middle of the image.
async function hideFooter(page: Page) {
  await page.addStyleTag({ content: '.app-footer { display: none !important; }' });
}

test('captures the refreshed dashboard tabs in light theme', async ({ page }) => {
  fs.mkdirSync(screenshotsDir, { recursive: true });
  await openDashboard(page, 'light');
  await hideDevChrome(page);

  await page.screenshot({ path: path.join(screenshotsDir, 'dashboard-overview.png') });

  const tabs = [
    { label: '今日运行', file: 'dashboard-today.png' },
    { label: '运行健康', file: 'dashboard-health.png' },
    { label: '数据质量', file: 'dashboard-quality.png' },
    { label: '耗时与 SLA', file: 'dashboard-sla.png' },
  ];
  await hideFooter(page);
  for (const tab of tabs) {
    await page.getByRole('tab', { name: tab.label }).click();
    await expect(page.locator('.v-window-item--active')).toHaveCount(1);
    await page.waitForTimeout(500);
    const charts = page.locator('.v-window-item--active [data-chart-state]:visible');
    for (let i = 0; i < (await charts.count()); i++) {
      await expect(charts.nth(i)).toHaveAttribute('data-chart-state', /ready|empty/, {
        timeout: 20_000,
      });
    }
    await page.screenshot({
      path: path.join(screenshotsDir, tab.file),
      fullPage: true,
    });
  }

  // The overview is the hero of the README: it must show the new layout.
  await page.getByRole('tab', { name: '经营总览' }).click();
  await expect(page.locator('.v-window-item--active .pulse-card')).toBeVisible();
  const pulse = page.locator('.v-window-item--active .pulse-card');
  expect((await pulse.evaluate(el => el.getBoundingClientRect().height)) < 800).toBeTruthy();
});

test.describe('light and dark split hero', () => {
  test.use({ viewport: { width: 1440, height: 900 }, deviceScaleFactor: 2 });

  test('composites the overview along the diagonal', async ({ page }) => {
    fs.mkdirSync(screenshotsDir, { recursive: true });
    fs.mkdirSync(artifactDir, { recursive: true });

    await openDashboard(page, 'light');
    await hideDevChrome(page);
    const lightPath = path.join(artifactDir, 'hero-light@2x.png');
    await page.screenshot({ path: lightPath });

    // Toggle through the UI instead of reloading: the light init script would
    // otherwise run again on reload and undo the dark preference.
    await page.getByRole('button', { name: '切换为深色模式' }).click();
    await expect(page.locator('.v-application')).toHaveClass(/v-theme--dark/);
    await hideDevChrome(page);
    await page.waitForTimeout(600);
    const darkPath = path.join(artifactDir, 'hero-dark@2x.png');
    await page.screenshot({ path: darkPath });

    const lightB64 = fs.readFileSync(lightPath).toString('base64');
    const darkB64 = fs.readFileSync(darkPath).toString('base64');
    const composite = await page.evaluate(
      async ({ light, dark }) => {
        const load = (b64: string) =>
          new Promise<HTMLImageElement>((resolve, reject) => {
            const img = new Image();
            img.onload = () => resolve(img);
            img.onerror = reject;
            img.src = `data:image/png;base64,${b64}`;
          });
        const [lightImg, darkImg] = await Promise.all([load(light), load(dark)]);
        const canvas = document.createElement('canvas');
        canvas.width = lightImg.width;
        canvas.height = lightImg.height;
        const ctx = canvas.getContext('2d')!;
        ctx.drawImage(lightImg, 0, 0);

        // Why: the anti-diagonal leaves the top-left triangle light and the
        // bottom-right triangle dark, the usual way to show both themes at once.
        ctx.save();
        ctx.beginPath();
        ctx.moveTo(canvas.width, 0);
        ctx.lineTo(canvas.width, canvas.height);
        ctx.lineTo(0, canvas.height);
        ctx.closePath();
        ctx.clip();
        ctx.drawImage(darkImg, 0, 0);
        ctx.restore();

        ctx.save();
        ctx.strokeStyle = 'rgba(255,255,255,0.38)';
        ctx.lineWidth = 2;
        ctx.beginPath();
        ctx.moveTo(canvas.width, 0);
        ctx.lineTo(0, canvas.height);
        ctx.stroke();
        ctx.restore();

        return {
          png: canvas.toDataURL('image/png'),
          width: canvas.width,
          height: canvas.height,
        };
      },
      { light: lightB64, dark: darkB64 }
    );

    const splitPath = path.join(screenshotsDir, 'dashboard-overview-split.png');
    fs.writeFileSync(splitPath, Buffer.from(composite.png.split(',')[1], 'base64'));

    // Verify the seam really flips the theme: sample equal distances either
    // side of the diagonal and require a large luminance gap.
    const samples = await page.evaluate(async ({ png, width, height }) => {
      const img = await new Promise<HTMLImageElement>((resolve, reject) => {
        const el = new Image();
        el.onload = () => resolve(el);
        el.onerror = reject;
        el.src = png;
      });
      const canvas = document.createElement('canvas');
      canvas.width = img.width;
      canvas.height = img.height;
      const ctx = canvas.getContext('2d')!;
      ctx.drawImage(img, 0, 0);
      const rgbAt = (x: number, y: number) => {
        const d = ctx.getImageData(x, y, 1, 1).data;
        return { r: d[0], g: d[1], b: d[2] };
      };
      const lumen = ({ r, g, b }: { r: number; g: number; b: number }) =>
        0.2126 * r + 0.7152 * g + 0.0722 * b;
      const topLeft = rgbAt(Math.round(width * 0.25), Math.round(height * 0.18));
      const bottomRight = rgbAt(Math.round(width * 0.75), Math.round(height * 0.82));
      const aboveSeam = rgbAt(Math.round(width / 2), Math.round(height / 2) - 60);
      const belowSeam = rgbAt(Math.round(width / 2), Math.round(height / 2) + 60);
      return {
        width,
        height,
        topLeft,
        bottomRight,
        aboveSeam,
        belowSeam,
        topLumen: lumen(topLeft),
        bottomLumen: lumen(bottomRight),
        seamLumen: lumen(aboveSeam) - lumen(belowSeam),
      };
    }, composite);

    expect(samples.width).toBe(2880);
    expect(samples.height).toBe(1800);
    expect(samples.topLumen).toBeGreaterThan(200);
    expect(samples.bottomLumen).toBeLessThan(80);
    expect(samples.seamLumen).toBeGreaterThan(100);

    fs.writeFileSync(
      path.join(artifactDir, 'dashboard-split.json'),
      JSON.stringify(samples, null, 2)
    );

    // Keep the two raw captures for later recomposition, not in the repo.
    expect(fs.statSync(splitPath).size).toBeGreaterThan(100_000);
  });
});

test.describe('management pages', () => {
  test.use({ viewport: { width: 1920, height: 1200 } });

  test('captures the refreshed source, table and detail screenshots', async ({ page }) => {
    fs.mkdirSync(screenshotsDir, { recursive: true });
    const shot = (file: string, fullPage = true) =>
      page.screenshot({ path: path.join(screenshotsDir, file), fullPage });

    await page.goto('/source');
    await expect(page.locator('.v-data-table tbody tr').first()).toBeVisible({ timeout: 20_000 });
    await hideDevChrome(page);
    await hideFooter(page);
    await page.waitForTimeout(400);
    await shot('source.png');

    await page.goto('/table');
    await expect(page.locator('.v-data-table tbody tr').first()).toBeVisible({ timeout: 20_000 });
    await hideFooter(page);
    await page.waitForTimeout(400);
    await shot('ods-etl.png');

    await page.goto('/target');
    await expect(page.locator('.v-data-table tbody tr').first()).toBeVisible({ timeout: 20_000 });
    await hideFooter(page);
    await page.waitForTimeout(400);
    await shot('target.png');

    const openDetail = async () => {
      await page.goto('/table/detail/1013');
      await expect(page.getByText('oms.oms_order').first()).toBeVisible({ timeout: 20_000 });
      await hideFooter(page);
      await page.waitForTimeout(500);
    };

    await openDetail();
    await shot('table-detail.png');

    await page.getByRole('tab', { name: '采集日志' }).click();
    await expect(page.locator('.log-list .v-list-item')).toHaveCount(3);
    await page.waitForTimeout(500);
    await shot('etl-log.png');

    await page.getByRole('tab', { name: '采集任务配置' }).click();
    await expect(page.locator('.code-shell__body')).toContainText('mysqlreader');
    await page.waitForTimeout(500);
    // The expanded job is taller than any sensible README image; keep the top.
    await shot('addax-json.png', false);

    await page.getByRole('tab', { name: '字段比较' }).click();
    await expect(page.getByText('20 个映射')).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText('目标字段缺失')).toHaveCount(0);
    await page.waitForTimeout(500);
    await shot('table-field.png');

    await page.getByRole('tab', { name: '采集结果' }).click();
    await expect(page.getByText('结果明细表')).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText('累计记录数')).toBeVisible();
    await page.waitForTimeout(500);
    await shot('addax-result.png');

    await page.getByRole('tab', { name: '执行快照' }).click();
    await expect(page.locator('.execution-card tbody tr')).toHaveCount(10);
    await page.waitForTimeout(300);
    await shot('table-execution.png');

    await page.goto('/audit');
    await expect(page.getByText('安全审计')).toBeVisible({ timeout: 20_000 });
    // Login successes from repeated demo runs would otherwise fill the first
    // page; show the warning trail this page exists for.
    await page.getByRole('combobox', { name: '级别' }).click({ force: true });
    await page.getByRole('option', { name: 'WARNING' }).click();
    await page.getByRole('button', { name: '查询' }).click();
    await expect(page.locator('.audit-table tbody tr').first()).toBeVisible({ timeout: 20_000 });
    await page.waitForTimeout(600);
    await hideFooter(page);
    await shot('audit.png');

    await page.goto('/calendars');
    await expect(page.getByText('采集日历')).toBeVisible({ timeout: 20_000 });
    await page.waitForTimeout(500);
    await hideFooter(page);
    await shot('etl-calendar.png');

    await page.goto('/type-mappings');
    await expect(page.getByText('类型映射')).toBeVisible({ timeout: 20_000 });
    await page.waitForTimeout(500);
    await hideFooter(page);
    await shot('etl-type-mappings.png');

    await page.goto('/sys-settings');
    await expect(page.getByText('系统配置')).toBeVisible({ timeout: 20_000 });
    await page.waitForTimeout(1200);
    await hideFooter(page);
    await shot('sys-setting.png');
  });
});

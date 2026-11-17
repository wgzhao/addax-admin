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

const sources = [
  {
    id: 1,
    name: '维格数仓核心交易库（含历史归档与备份实例）',
    url: 'jdbc:postgresql://warehouse.internal.prod.example.com:5432/analytics_core_transaction?useSSL=false&connectTimeout=10000',
    username: 'etl_reader',
    pass: 'secret',
    startAt: '03:10',
    calendar: 'WORK_DAY',
    prerequisite: '',
    preScript: '',
    remark: '',
    enabled: true,
    maxConcurrency: 12,
    tableCount: 1286,
    validTableCount: 1174,
  },
  {
    id: 2,
    name: '日志检索集群',
    url: 'jdbc:mysql://10.0.0.2:3306/log',
    username: 'etl',
    pass: 'secret',
    startAt: '',
    calendar: '',
    prerequisite: '',
    preScript: '',
    remark: '',
    dbType: 'elasticsearch',
    enabled: false,
    maxConcurrency: 4,
    tableCount: 9,
    validTableCount: 9,
  },
  {
    id: 3,
    name: 'Mongo',
    url: 'mongodb://10.0.0.3:27017/app',
    username: 'etl',
    pass: 'secret',
    startAt: '23:59',
    calendar: 'TRADE_DAY',
    prerequisite: '',
    preScript: '',
    remark: '',
    dbType: 'mongodb',
    enabled: true,
    maxConcurrency: 2,
    tableCount: 42,
    validTableCount: 40,
  },
];

const fixedColumns = [
  { title: 'ID', width: 68 },
  // Capped at 10 CJK characters so a long name never steals from the URL.
  { title: '采集源', width: 168 },
  { title: '类型', width: 136 },
  { title: '状态', width: 96 },
  { title: '表数量', width: 100 },
  { title: '有效表数量', width: 132 },
  { title: '采集时间', width: 120 },
  { title: '采集日历', width: 112 },
  { title: '操作', width: 264 },
];

const wideViewports = [1366, 1440, 1920];

async function mockSession(page: Page) {
  await page.addInitScript(
    ([authToken, username]) => {
      localStorage.setItem('authToken', authToken);
      localStorage.setItem('authUsername', username);
    },
    [token, 'admin']
  );
}

async function mockApi(page: Page) {
  await page.route('**/api/v1/**', async (route: Route) => {
    const request = route.request();
    const pathname = new URL(request.url()).pathname;

    let body: unknown = [];
    if (pathname === '/api/v1/sources/enabled' || pathname === '/api/v1/sources') {
      body = sources;
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

type ListMetrics = {
  widths: Record<string, number>;
  wrappedHeaders: string[];
  clippedCells: string[];
  perRowWidths: Record<string, number[]>;
  actionRows: number;
  scroll: number;
  actionPinned: boolean;
  visibleWidth: number;
};

const measureList = (page: Page) =>
  page.locator('.v-data-table').evaluate<ListMetrics>(table => {
    const ths = Array.from(table.querySelectorAll('thead th')) as HTMLElement[];
    const titles = ths.map(th => th.innerText.trim());
    const widths: Record<string, number> = {};
    ths.forEach((th, index) => {
      widths[titles[index]] = Math.round(th.getBoundingClientRect().width);
    });

    // A header only wraps when its content box grows past one text line.
    const wrappedHeaders = ths
      .filter(th => {
        const content = th.querySelector('.v-data-table-header__content') as HTMLElement | null;
        return content ? content.getBoundingClientRect().height > 26 : false;
      })
      .map(th => th.innerText.trim());

    const rows = Array.from(table.querySelectorAll('tbody tr')) as HTMLElement[];
    const perRowWidths: Record<string, number[]> = {};
    titles.forEach((title, index) => {
      perRowWidths[title] = rows.map(row =>
        Math.round((row.children[index] as HTMLElement).getBoundingClientRect().width)
      );
    });
    const clippedCells = new Set<string>();
    rows.forEach(row =>
      Array.from(row.children).forEach((cell, index) => {
        const element = cell as HTMLElement;
        if (element.scrollWidth > element.clientWidth + 1) clippedCells.add(titles[index]);
      })
    );

    const actionCell = rows[0].children[rows[0].children.length - 1] as HTMLElement;
    const actionRows = new Set(
      Array.from(actionCell.querySelectorAll('.v-btn')).map(button =>
        Math.round(button.getBoundingClientRect().top)
      )
    ).size;

    const wrapper = table.querySelector('.v-table__wrapper') as HTMLElement;
    return {
      widths,
      wrappedHeaders,
      clippedCells: Array.from(clippedCells),
      perRowWidths,
      actionRows,
      scroll: wrapper.scrollWidth - wrapper.clientWidth,
      actionPinned:
        Math.abs(
          actionCell.getBoundingClientRect().right - wrapper.getBoundingClientRect().right
        ) <= 2,
      visibleWidth: wrapper.clientWidth,
    };
  });

const expectFixedColumnsHold = (metrics: ListMetrics) => {
  for (const column of fixedColumns) {
    expect(Math.abs(metrics.widths[column.title] - column.width)).toBeLessThanOrEqual(2);
    // Same width on every row, whatever the cell contains.
    expect(new Set(metrics.perRowWidths[column.title]).size).toBe(1);
  }
  expect(metrics.wrappedHeaders).toEqual([]);
  expect(metrics.clippedCells).toEqual([]);
  expect(metrics.actionRows).toBe(1);
};

test('source list keeps predictable columns fixed and lets name/url absorb the rest', async ({
  page,
}) => {
  await mockSession(page);
  await mockApi(page);
  fs.mkdirSync(artifactDir, { recursive: true });

  const measured: Record<string, ListMetrics> = {};
  for (const width of wideViewports) {
    await page.setViewportSize({ width, height: 900 });
    await page.goto('/source');
    await expect(page.getByRole('row', { name: /维格数仓/ })).toBeVisible();
    const metrics = await measureList(page);
    measured[width] = metrics;

    expectFixedColumnsHold(metrics);
    // At and above the design width nothing has to scroll.
    expect(metrics.scroll).toBe(0);
    expect(metrics.widths['连接串']).toBeGreaterThanOrEqual(96);
  }

  // The connection string is the only column that grows with the viewport.
  expect(measured[1920].widths['连接串']).toBeGreaterThan(measured[1366].widths['连接串'] + 400);

  // Narrower than that the table scrolls horizontally, but the predictable
  // columns keep their width and the actions stay pinned in view.
  await page.setViewportSize({ width: 1024, height: 900 });
  await page.goto('/source');
  await expect(page.getByRole('row', { name: /维格数仓/ })).toBeVisible();
  const narrow = await measureList(page);
  measured[1024] = narrow;
  expectFixedColumnsHold(narrow);
  expect(narrow.scroll).toBeGreaterThan(0);
  expect(narrow.actionPinned).toBe(true);

  const screenshots: string[] = [];
  for (const width of [1366, 1024]) {
    await page.setViewportSize({ width, height: 900 });
    await page.waitForTimeout(150);
    const screenshot = path.join(artifactDir, `source-list-columns-${width}.png`);
    await page.screenshot({ path: screenshot, fullPage: true });
    screenshots.push(screenshot);
  }

  const artifact = path.join(artifactDir, 'source-list-columns.json');
  fs.writeFileSync(
    artifact,
    JSON.stringify({ checkedAt: new Date().toISOString(), measured, screenshots }, null, 2)
  );
  await test.info().attach('source-list-columns', {
    path: artifact,
    contentType: 'application/json',
  });
});

import { test, expect } from '@playwright/test';
import { execFileSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

// 慢表 Top 10 的条形可点击进入对应采集表详情；用真实图表命中测试验证点击索引与 tid 的映射
const artifactDir = fileURLToPath(new URL('./artifacts', import.meta.url));
const SLOW_TABLES_API = '/api/v1/dashboard/slow-top-tables?days=30&limit=10';
const dbName = process.env.E2E_DB_NAME || 'addax_admin';
const dbHost = process.env.E2E_DB_HOST || 'localhost';
const dbUser = process.env.E2E_DB_USER || 'addax_admin';
const dbPassword = process.env.E2E_DB_PASSWORD || 'addax_admin@123';

test.use({ viewport: { width: 1440, height: 900 } });

function psql(sql: string): string {
  return execFileSync(
    'psql',
    ['-h', dbHost, '-U', dbUser, '-d', dbName, '-v', 'ON_ERROR_STOP=1', '-q', '-At', '-c', sql],
    { encoding: 'utf8', env: { ...process.env, PGPASSWORD: dbPassword } }
  ).trim();
}

// 本地库缺少统计数据时图表为空；自建两张高耗时表保证进入 Top 10，并按平均耗时区分先后
function seed(): void {
  psql(`
    delete from etl_statistic
    where tid in (select id from etl_table where source_table like 'slow_e2e_%');
    delete from etl_table where source_table like 'slow_e2e_%';
    delete from etl_source where name = 'Slow Table E2E Source';

    insert into etl_source (name, url, username, pass, start_at, enabled, max_concurrency, db_type, calendar)
    values ('Slow Table E2E Source', 'jdbc:postgresql://localhost:5432/demo', 'demo', 'demo', '23:00:00', true, 2, 'POSTGRESQL', 'NATURAL_DAY');

    insert into etl_table (
      source_db, source_table, target_db, target_table, target_options, filter, retry_cnt,
      start_time, end_time, max_runtime, sid, duration, tbl_comment, status, split_pk, auto_pk,
      write_mode, target_id, created_at, updated_at
    )
    select 'slow_e2e_db', v.tbl, 'ods', v.tbl, '{}'::jsonb, '1=1', 3,
           now() - interval '2 day', now() - interval '1 day', 120, s.id, 0, 'slow table link e2e',
           'Y', '', true, 'overwrite', t.id, now(), now()
    from (values ('slow_e2e_a'), ('slow_e2e_b')) as v(tbl)
    cross join etl_source s
    cross join (select id from etl_target order by id limit 1) t
    where s.name = 'Slow Table E2E Source';

    insert into etl_statistic (tid, biz_date, take_secs, start_at, end_at)
    select t.id,
           current_date - g,
           case t.source_table when 'slow_e2e_a' then 900 else 300 end,
           now() - interval '1 day',
           now() - interval '1 day' + interval '5 minute'
    from etl_table t
    cross join generate_series(0, 2) as g
    where t.source_table like 'slow_e2e_%';
  `);
}

function cleanup(): void {
  psql(`
    delete from etl_statistic where tid in (select id from etl_table where source_table like 'slow_e2e_%');
    delete from etl_table where source_table like 'slow_e2e_%';
    delete from etl_source where name = 'Slow Table E2E Source';
  `);
}

test.beforeAll(() => {
  seed();
});

test.afterAll(() => {
  cleanup();
});

interface SlowTableRow {
  tid: number;
  source_db: string;
  source_table: string;
  source_name: string;
  avg_secs: number;
}

test.describe('dashboard slow table links', () => {
  test('clicking a slow-table bar opens that table detail', async ({ page }) => {
    await page.goto('/');
    const token = await page.evaluate(() => localStorage.getItem('authToken'));
    expect(token).toBeTruthy();

    const res = await page.request.get(SLOW_TABLES_API, {
      headers: { Authorization: `Bearer ${token}` },
    });
    expect(res.ok()).toBeTruthy();
    const rows: SlowTableRow[] = await res.json();
    // 选择第二条而不是第一条：同时验证 label 索引到 tid 的映射没有整体偏移
    expect(rows.length, '需要至少两条慢表才能验证索引映射').toBeGreaterThan(1);
    const expected = rows[1];

    await page.getByRole('tab', { name: '耗时与 SLA' }).click();
    const card = page.locator('.section-card').filter({ hasText: '慢表 Top 10' });
    await expect(card).toBeVisible();
    await expect(card.locator('[data-chart-state]')).toHaveAttribute('data-chart-state', 'ready', {
      timeout: 20_000,
    });

    const canvas = card.locator('canvas');
    await expect(canvas).toBeVisible();
    // 等条形动画结束，避免拿到动画中间态的几何值
    await page.waitForTimeout(1200);

    const geometry = await canvas.evaluate((el, idx) => {
      // Chart.js 只画在 canvas 上，位置必须从图表实例的 meta 里取；
      // vue-chartjs 通过 expose 暴露了 chart ref，开发模式下可从组件实例拿到。
      let inst = (el as unknown as { __vueParentComponent?: any }).__vueParentComponent;
      while (inst && !(inst.exposed && inst.exposed.chart)) inst = inst.parent;
      if (!inst) throw new Error('chart instance is not exposed on the canvas component chain');
      const ref = inst.exposed.chart;
      const chart = ref && ref.value ? ref.value : ref;
      const bar = chart.getDatasetMeta(0).data[idx];
      return bar.getProps(['x', 'y', 'base'], true) as { x: number; y: number; base: number };
    }, 1);

    const box = await canvas.boundingBox();
    expect(box).toBeTruthy();
    // 点在条形内部靠左的位置，避开末端抗锯齿边缘
    const clickX =
      box!.x + geometry.base + Math.max(6, Math.min((geometry.x - geometry.base) / 2, 40));
    const clickY = box!.y + geometry.y;

    await page.mouse.move(clickX, clickY);
    await expect
      .poll(() => canvas.evaluate(el => (el as HTMLElement).style.cursor))
      .toBe('pointer');

    await page.mouse.click(clickX, clickY);

    await expect(page).toHaveURL(new RegExp(`/table/detail/${expected.tid}(\\?|$)`));
    await expect(page.locator('.page-title__identity')).toHaveText(
      `${expected.source_db}.${expected.source_table}`
    );

    fs.mkdirSync(artifactDir, { recursive: true });
    await page.screenshot({
      path: path.join(artifactDir, 'slow-table-link-detail.png'),
      fullPage: true,
    });
    fs.writeFileSync(
      path.join(artifactDir, 'slow-table-link.json'),
      JSON.stringify(
        {
          clickedIndex: 1,
          tid: expected.tid,
          table: `${expected.source_db}.${expected.source_table}`,
          avgSecs: expected.avg_secs,
          url: page.url(),
        },
        null,
        2
      )
    );
  });
});

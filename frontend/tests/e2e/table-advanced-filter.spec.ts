import { test, expect } from '@playwright/test';
import { execFileSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

/**
 * ADR-0017: the collection-table list filters by target endpoint/type, write mode, comment and
 * dates, shows the target endpoint inline, and keeps every condition in the URL.
 *
 * The scenario is deliberately mixed: two target families, two sources (one disabled), four tables
 * (one retired), a comment containing a literal `%`, and a legacy `lifecycle=` deep link. That
 * combination exercises target filtering, LIKE escaping, default scope, and URL restore in one run.
 */
const dbName = process.env.E2E_DB_NAME || 'addax_admin';
const artifactDir = fileURLToPath(new URL('./artifacts', import.meta.url));

function psql(sql: string): string {
  return execFileSync('psql', ['-d', dbName, '-v', 'ON_ERROR_STOP=1', '-q', '-At', '-c', sql], {
    encoding: 'utf8',
  }).trim();
}

function seed(): void {
  psql(`
    delete from etl_table where source_table like 'filter_e2e_%';
    delete from etl_source where name in ('Filter E2E MySQL', 'Filter E2E Oracle');
    delete from etl_target where code in ('FT_DORIS', 'FT_HDFS');

    insert into etl_target (code, name, target_type, connect_config, writer_template_key, enabled, is_default, remark)
    values
      ('FT_DORIS', 'Filter E2E Doris', 'DORIS', '{}', 'wD', true, false, 'advanced filter e2e'),
      ('FT_HDFS',  'Filter E2E HDFS',  'HDFS',  '{}', 'wH', true, false, 'advanced filter e2e');

    insert into etl_source (name, url, username, pass, start_at, enabled, max_concurrency, db_type, calendar)
    values
      ('Filter E2E MySQL',  'jdbc:mysql://localhost:3306/demo', 'demo', 'demo', '23:00:00', true,  2, 'MYSQL',  'NATURAL_DAY'),
      ('Filter E2E Oracle', 'jdbc:oracle:thin:@localhost:1521/demo', 'demo', 'demo', '23:30:00', false, 1, 'oracle', 'NATURAL_DAY');

    insert into etl_table (
      source_db, source_table, target_db, target_table, target_options, filter, retry_cnt,
      start_time, end_time, max_runtime, sid, duration, tbl_comment, status, split_pk, auto_pk,
      write_mode, target_id, created_at, updated_at, retired_at, retired_by
    )
    select v.source_db, v.source_table, v.target_db, v.target_table, '{}'::jsonb, '1=1', 3,
           now() - interval '2 day', v.end_time, 120, s.id, 0, v.comment, v.status, '', true,
           v.write_mode, t.id, now(), now(), v.retired_at, v.retired_by
    from (values
      ('public', 'filter_e2e_doris_append',    'dw_ods', 'ft_target_append',    now() - interval '1 day',  '订单明细',   'Y', 'append',    'FT_DORIS', null::timestamptz, null::varchar),
      ('public', 'filter_e2e_doris_overwrite', 'dw_ods', 'ft_target_overwrite', now() - interval '10 day', '100%订单',   'Y', 'overwrite', 'FT_DORIS', null::timestamptz, null::varchar),
      ('public', 'filter_e2e_hdfs_insert',     'ods',    'ft_target_hdfs',      now() - interval '2 day',  '日志',       'Y', 'insert',    'FT_HDFS',  null::timestamptz, null::varchar),
      ('public', 'filter_e2e_hdfs_retired',    'ods',    'ft_target_retired',   now() - interval '20 day', '订单归档',   'Y', 'insert',    'FT_HDFS',  now(),             'admin')
    ) as v(source_db, source_table, target_db, target_table, end_time, comment, status, write_mode, target_code, retired_at, retired_by)
    join etl_target t on t.code = v.target_code
    join etl_source s on s.name = case when v.target_code = 'FT_DORIS' then 'Filter E2E MySQL' else 'Filter E2E Oracle' end;
  `);
}

function cleanup(): void {
  psql(`
    delete from etl_table where source_table like 'filter_e2e_%';
    delete from etl_source where name in ('Filter E2E MySQL', 'Filter E2E Oracle');
    delete from etl_target where code in ('FT_DORIS', 'FT_HDFS');
  `);
}

test.describe.configure({ mode: 'serial' });

test.beforeAll(() => {
  seed();
});

test.afterAll(() => {
  cleanup();
});

test('advanced filters narrow the list, escape LIKE input and survive a reload', async ({ page }) => {
  const evidence: Record<string, unknown> = {};

  // Target-type filter: only the two Doris rows are current.
  await page.goto('/table?targetTypes=DORIS');
  await expect(page.getByRole('row', { name: /filter_e2e_doris_append/ })).toBeVisible({
    timeout: 20_000,
  });
  await expect(page.getByText('共 2 条')).toBeVisible();
  await expect(page.getByRole('row', { name: /filter_e2e_hdfs_insert/ })).toHaveCount(0);

  // The target endpoint is now visible on the row itself, not only on the detail page.
  const dorisRow = page.getByRole('row', { name: /filter_e2e_doris_append/ });
  await expect(dorisRow).toContainText('Filter E2E Doris');
  await expect(dorisRow).toContainText('Doris');
  evidence.dorisRow = (await dorisRow.innerText()).replace(/\s+/g, ' ').trim();

  // Combined filter: family + write mode leaves exactly one row.
  await page.goto('/table?targetTypes=DORIS&writeModes=append');
  await expect(page.getByText('共 1 条')).toBeVisible({ timeout: 20_000 });
  await expect(page.getByRole('row', { name: /filter_e2e_doris_append/ })).toBeVisible();
  await expect(page.getByRole('row', { name: /filter_e2e_doris_overwrite/ })).toHaveCount(0);

  // Reload keeps the URL conditions and the result.
  await page.reload();
  await expect(page.getByText('共 1 条')).toBeVisible({ timeout: 20_000 });
  expect(page.url()).toContain('targetTypes=DORIS');
  expect(page.url()).toContain('writeModes=append');

  // Dropping one chip widens the result set again (Doris rows only).
  const writeModeChip = page
    .locator('.filter-chips .v-chip')
    .filter({ hasText: '写入模式' });
  await writeModeChip.locator('.v-chip__close').click();
  await expect(page.getByText('共 2 条')).toBeVisible({ timeout: 20_000 });
  expect(page.url()).not.toContain('writeModes=append');

  // A literal `%` in the comment is escaped: it matches one row, not every row.
  await page.goto('/table?comment=100%25');
  await expect(page.getByText('共 1 条')).toBeVisible({ timeout: 20_000 });
  await expect(page.getByRole('row', { name: /filter_e2e_doris_overwrite/ })).toBeVisible();
  evidence.likeEscaping = 'comment=100% matched 1 row';

  // Legacy rows store db_type in upper case (MYSQL); the filter compares case-insensitively.
  await page.goto('/table?dbTypes=mysql');
  await expect(page.getByText('共 2 条')).toBeVisible({ timeout: 20_000 });
  await expect(page.getByRole('row', { name: /filter_e2e_hdfs_insert/ })).toHaveCount(0);

  // The keyword now reaches the target name (filterColumn extension).
  await page.goto('/table?q=Filter%20E2E%20Doris');
  await expect(page.getByText('共 2 条')).toBeVisible({ timeout: 20_000 });

  // Legacy deep link still works.
  await page.goto('/table?lifecycle=RETIRED&q=filter_e2e_hdfs_retired');
  await expect(page.getByRole('row', { name: /filter_e2e_hdfs_retired/ })).toBeVisible({
    timeout: 20_000,
  });
  await expect(page.getByText(/已退役 · admin/)).toBeVisible();

  // An unknown sort field must not 500; the default order kicks in.
  await page.goto('/table?sortField=evil&sortOrder=desc');
  await expect(page.getByRole('row', { name: /filter_e2e_doris_append/ })).toBeVisible({
    timeout: 20_000,
  });
  evidence.invalidSort = 'page rendered with default order';

  fs.mkdirSync(artifactDir, { recursive: true });
  const artifactPath = path.join(artifactDir, 'table-advanced-filter.json');
  fs.writeFileSync(artifactPath, JSON.stringify(evidence, null, 2));
  await test.info().attach('table-advanced-filter', {
    body: JSON.stringify(evidence, null, 2),
    contentType: 'application/json',
  });
});

test('the filter panel applies with one click and the empty state clears itself', async ({ page }) => {
  await page.goto('/table');
  await expect(page.getByRole('button', { name: '筛选', exact: true })).toBeVisible({
    timeout: 20_000,
  });

  await page.getByRole('button', { name: '筛选', exact: true }).click();
  const panel = page.locator('.filter-panel');
  await expect(panel).toBeVisible();
  await panel.locator('.filter-group__families .v-chip').filter({ hasText: 'HDFS' }).click();

  await expect(page.getByRole('row', { name: /filter_e2e_hdfs_insert/ })).toBeVisible({
    timeout: 20_000,
  });
  await expect(page.getByText('共 1 条')).toBeVisible();
  // HDFS family expands to both concrete types the backend stores.
  await expect(page).toHaveURL(/targetTypes=HDFS,HIVE/);
  // The selected types render their labels, not "[object Object]".
  await expect(panel.locator('.v-chip').filter({ hasText: 'Hive' })).toBeVisible();
  await expect(panel.getByText('[object Object]')).toHaveCount(0);

  // A filter with no hits shows the dedicated empty state, and its primary action resets.
  await page.goto('/table?targetTypes=DORIS&comment=no-such-comment');
  await expect(page.getByText('没有匹配的采集表')).toBeVisible({ timeout: 20_000 });
  await page.getByRole('button', { name: '清除筛选' }).click();
  await expect(page.getByRole('row', { name: /filter_e2e_doris_append/ })).toBeVisible({
    timeout: 20_000,
  });
  expect(page.url()).not.toContain('targetTypes=');
});

test('source grouping orders rows by source and separates the blocks', async ({ page }) => {
  await page.goto('/table?q=filter_e2e');
  const rows = page.locator('table tbody tr');
  await expect(rows).toHaveCount(3, { timeout: 20_000 });

  await page.getByRole('button', { name: /显示设置/ }).click();
  await page.getByText('按采集源分组').click();

  await expect(rows).toHaveCount(3, { timeout: 20_000 });
  // Source order: "Filter E2E MySQL" before "Filter E2E Oracle".
  await expect(rows.first()).toContainText('filter_e2e_doris');
  await expect(rows.last()).toContainText('filter_e2e_hdfs_insert');
  // Two distinct sources → two block starts.
  await expect(page.locator('tr.source-group-start')).toHaveCount(2);
});

test('target endpoint filter works from a deep link and shows its chip', async ({ page }) => {
  const targetId = psql("select id from etl_target where code = 'FT_DORIS'").trim();
  await page.goto(`/table?targets=${targetId}`);

  await expect(page.getByRole('row', { name: /filter_e2e_doris_append/ })).toBeVisible({
    timeout: 20_000,
  });
  await expect(page.getByRole('row', { name: /filter_e2e_hdfs_insert/ })).toHaveCount(0);
  await expect(page.getByText('共 2 条')).toBeVisible();
  await expect(
    page.locator('.filter-chips .v-chip').filter({ hasText: 'Filter E2E Doris' })
  ).toBeVisible();
});

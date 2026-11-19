import { test, expect, type APIRequestContext } from '@playwright/test';
import dayjs, { type Dayjs } from 'dayjs';
import { execFileSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

/**
 * 采集缺失日历的期望值必须跟着每张表所属的采集日历走。
 *
 * 这个用例不 mock API：它在一个专用库里落三种日历的源和表（自然日 3 张、工作日 2 张、交易日 1 张），
 * 再写入与日历一致的 etl_statistic，然后断言近 56 天每天的应采/实采/缺失。
 * 回归点是周末：工作日和交易日源在周末本来就不采，如果按自然日算应采，周末会被报成整批缺失。
 *
 * 运行前提：后端连的是 E2E_DB_NAME 这个库（默认 addax_admin_missing_cal_e2e），
 * 并且 Redis 用独立 DB 避免和生产/本地实例抢配置：
 *
 *   VITE_API_HOST=http://localhost:50602 E2E_DB_NAME=addax_admin_missing_cal_e2e \
 *   E2E_API_HOST=http://localhost:50602 yarn e2e tests/e2e/dashboard-missing-calendar.spec.ts
 */

const dbName = process.env.E2E_DB_NAME || 'addax_admin_missing_cal_e2e';
const dbHost = process.env.E2E_DB_HOST || 'localhost';
const dbUser = process.env.E2E_DB_USER || 'addax_admin';
const dbPassword = process.env.E2E_DB_PASSWORD || 'addax_admin@123';
const apiBase = (process.env.E2E_API_HOST || 'http://localhost:50602').replace(/\/$/, '');
const artifactDir = fileURLToPath(new URL('./artifacts', import.meta.url));
const DAYS = 56;
const NATURAL_TABLES = 3;
const WORK_TABLES = 2;
const TRADE_TABLES = 1;

if (!dbName.includes('e2e')) {
  throw new Error(`E2E_DB_NAME must point at a dedicated e2e database, got "${dbName}"`);
}

function psql(sql: string): string {
  return execFileSync(
    'psql',
    ['-h', dbHost, '-U', dbUser, '-d', dbName, '-v', 'ON_ERROR_STOP=1', '-q', '-At', '-c', sql],
    { encoding: 'utf8', env: { ...process.env, PGPASSWORD: dbPassword } }
  ).trim();
}

const dateLiteral = (d: Dayjs) => `'${d.format('YYYY-MM-DD')}'`;

// Latest weekday on or before d; dayjs day(): 0=Sunday..6=Saturday
function latestOnOrBefore(d: Dayjs, weekday: number): Dayjs {
  return d.subtract((d.day() - weekday + 7) % 7, 'day');
}

const today = dayjs().startOf('day');
// 两个“节假日”：最近的周三与前一周周三，用来证明日历里的调休休息日（工作日但不开工）不报缺失
const holiday1 = latestOnOrBefore(today, 3);
const holiday2 = holiday1.subtract(7, 'day');
// 故意漏采的那一天：最近一个周四，落在 56 天窗口内
const missDay = latestOnOrBefore(today, 4);
const rangeStart = today.subtract(70, 'day');
const statEnd = today.add(7, 'day');

const isWeekday = (d: Dayjs) => d.day() >= 1 && d.day() <= 5;
const isHoliday = (d: Dayjs) => d.isSame(holiday1, 'day') || d.isSame(holiday2, 'day');

// 交易日历里最后一个日期就是业务日期锚点，独立于切日时间
function expectedAnchor(): Dayjs {
  let cursor = today;
  while (!(isWeekday(cursor) && !isHoliday(cursor) && !cursor.isAfter(today, 'day'))) {
    cursor = cursor.subtract(1, 'day');
  }
  return cursor;
}

const anchor = expectedAnchor();
// 调休上班的周六：窗口内最近的一个周六，只存在于工作日历字典里，交易日历没有它
const makeupDay = latestOnOrBefore(anchor, 6);

const isTradeDay = (d: Dayjs) => isWeekday(d) && !isHoliday(d) && !d.isAfter(today, 'day');
// 工作日历的成员以字典为准：周末调休上班日算工作日，节假日（即使是周中）不算
const isWorkDay = (d: Dayjs) => d.isSame(makeupDay, 'day') || (isWeekday(d) && !isHoliday(d));

function seed(): void {
  const holidayDates = [dateLiteral(holiday1), dateLiteral(holiday2)];
  const holidayAndMiss = [...holidayDates, dateLiteral(missDay)];
  psql(`
    delete from addax_log where tid in (select id from etl_table where source_table like 'cal_e2e_%');
    delete from etl_statistic where tid in (select id from etl_table where source_table like 'cal_e2e_%');
    delete from etl_jour where tid in (select id from etl_table where source_table like 'cal_e2e_%');
    delete from etl_execution where tid in (select id from etl_table where source_table like 'cal_e2e_%');
    delete from etl_column_mapping where tid in (select id from etl_table where source_table like 'cal_e2e_%');
    delete from etl_column where tid in (select id from etl_table where source_table like 'cal_e2e_%');
    delete from etl_job where tid in (select id from etl_table where source_table like 'cal_e2e_%');
    delete from etl_table where source_table like 'cal_e2e_%';
    delete from etl_source where name like 'Cal E2E %';
    delete from sys_item where dict_code in (1021, 1022);

    insert into etl_source (name, url, username, pass, start_at, enabled, max_concurrency, db_type, calendar)
    values
      ('Cal E2E Natural', 'jdbc:postgresql://${dbHost}:5432/${dbName}', 'cal_e2e', 'cal_e2e',
       '23:59:00', true, 2, 'POSTGRESQL', 'NATURAL_DAY'),
      ('Cal E2E Work', 'jdbc:postgresql://${dbHost}:5432/${dbName}', 'cal_e2e', 'cal_e2e',
       '23:59:00', true, 2, 'POSTGRESQL', 'WORK_DAY'),
      ('Cal E2E Trade', 'jdbc:postgresql://${dbHost}:5432/${dbName}', 'cal_e2e', 'cal_e2e',
       '23:59:00', true, 2, 'POSTGRESQL', 'TRADE_DAY');

    insert into etl_table (source_db, source_table, target_db, target_table, sid, status, created_at, updated_at)
    select 'public', v.prefix || '_' || g, 'public', 'cal_e2e_target_' || v.prefix || '_' || g,
           s.id, 'N', ${dateLiteral(today.subtract(90, 'day'))}::timestamp,
           ${dateLiteral(today.subtract(90, 'day'))}::timestamp
    from etl_source s
    join (values
      ('Cal E2E Natural', 'cal_e2e_nat', ${NATURAL_TABLES}),
      ('Cal E2E Work', 'cal_e2e_work', ${WORK_TABLES}),
      ('Cal E2E Trade', 'cal_e2e_trade', ${TRADE_TABLES})
    ) v(name, prefix, n) on v.name = s.name
    cross join lateral generate_series(1, v.n) g;

    insert into sys_item (dict_code, item_key, item_value, remark)
    select 1022, to_char(d, 'YYYYMMDD'), to_char(d, 'YYYYMMDD'), 'cal e2e'
    from generate_series(${dateLiteral(rangeStart)}, ${dateLiteral(statEnd)}, interval '1 day') gs(d)
    where (extract(isodow from d) between 1 and 5
           and d::date not in (${holidayDates.join(', ')}))
       or d::date = ${dateLiteral(makeupDay)}
    union all
    select 1021, to_char(d, 'YYYYMMDD'), to_char(d, 'YYYYMMDD'), 'cal e2e'
    from generate_series(${dateLiteral(rangeStart)}, ${dateLiteral(statEnd)}, interval '1 day') gs(d)
    where extract(isodow from d) between 1 and 5
      and d::date not in (${holidayDates.join(', ')})
      and d::date <= ${dateLiteral(today)};

    insert into etl_statistic (tid, start_at, end_at, take_secs, total_bytes, total_recs, total_errors, biz_date)
    select t.id, d + time '10:00', d + time '10:05', 5, 0, 100, 0, d::date
    from etl_table t
    join etl_source s on s.id = t.sid
    cross join generate_series(${dateLiteral(rangeStart)}, ${dateLiteral(statEnd)}, interval '1 day') gs(d)
    where t.source_table like 'cal_e2e_%'
      and case s.calendar
        when 'NATURAL_DAY' then true
        when 'WORK_DAY' then d::date = ${dateLiteral(makeupDay)}
          or (extract(isodow from d) between 1 and 5
              and d::date not in (${holidayAndMiss.join(', ')}))
        when 'TRADE_DAY' then extract(isodow from d) between 1 and 5
          and d::date not in (${holidayDates.join(', ')})
          and d::date <= ${dateLiteral(today)}
      end;
  `);
}

async function apiToken(request: APIRequestContext): Promise<string> {
  const username = process.env.E2E_USERNAME || 'admin';
  const password = process.env.E2E_PASSWORD || 'admin123';
  const res = await request.post(`${apiBase}/api/v1/auth/login`, { data: { username, password } });
  expect(res.ok(), `login failed: ${res.status()}`).toBeTruthy();
  const body = await res.json();
  expect(body.code).toBe(0);
  return body.data as string;
}

interface CalendarRow {
  biz_date: string;
  expected: number;
  collected: number;
  missing: number;
}

test('missing calendar judges each source by its own collection calendar', async ({ request }) => {
  seed();

  const token = await apiToken(request);
  const headers = { Authorization: `Bearer ${token}` };

  // 字典是直接写库的，让后端重新从字典生成业务日期锚点（交易日历的最后一个日期）
  const reload = await request.post(`${apiBase}/api/v1/settings/reload-sys-config`, { headers });
  expect(reload.ok(), `reload-sys-config failed: ${reload.status()}`).toBeTruthy();

  const res = await request.get(
    `${apiBase}/api/v1/dashboard/missing-collect-calendar?days=${DAYS}`,
    {
      headers,
    }
  );
  expect(res.ok(), `missing calendar failed: ${res.status()}`).toBeTruthy();
  const rows = (await res.json()) as CalendarRow[];

  const expectedRows = new Map<
    string,
    { expected: number; collected: number; missing: number; workDay: boolean; tradeDay: boolean }
  >();
  for (let i = DAYS - 1; i >= 0; i--) {
    const day = anchor.subtract(i, 'day');
    const workDay = isWorkDay(day);
    const tradeDay = isTradeDay(day);
    const expected = NATURAL_TABLES + (workDay ? WORK_TABLES : 0) + (tradeDay ? TRADE_TABLES : 0);
    const collected =
      NATURAL_TABLES +
      (workDay && !day.isSame(missDay, 'day') ? WORK_TABLES : 0) +
      (tradeDay ? TRADE_TABLES : 0);
    expectedRows.set(day.format('YYYY-MM-DD'), {
      expected,
      collected,
      missing: Math.max(expected - collected, 0),
      workDay,
      tradeDay,
    });
  }

  const failures: string[] = [];
  const observed: Array<Record<string, unknown>> = [];
  for (const row of rows) {
    const want = expectedRows.get(row.biz_date);
    const actual = {
      biz_date: row.biz_date,
      expected: Number(row.expected),
      collected: Number(row.collected),
      missing: Number(row.missing),
    };
    if (!want) {
      failures.push(`${row.biz_date}: returned a date outside the expected window`);
      observed.push({ ...actual, error: 'outside-window' });
      continue;
    }
    const ok =
      actual.expected === want.expected &&
      actual.collected === want.collected &&
      actual.missing === want.missing;
    if (!ok) {
      failures.push(
        `${row.biz_date}: expected ${want.expected}/${want.collected}/${want.missing}, ` +
          `got ${actual.expected}/${actual.collected}/${actual.missing}`
      );
    }
    observed.push({ ...actual, want, ok });
  }

  // 结构断言：窗口正好覆盖 N 天，最后一天是交易日历的锚点
  expect(rows, 'window length').toHaveLength(DAYS);
  expect(rows[rows.length - 1]?.biz_date, 'window ends on the trading-day anchor').toBe(
    anchor.format('YYYY-MM-DD')
  );

  // 回归核心：普通周末只应期望自然日表，缺失必须为 0
  const makeupKey = makeupDay.format('YYYY-MM-DD');
  const weekendRows = rows.filter(row => {
    const day = dayjs(row.biz_date);
    return (day.day() === 0 || day.day() === 6) && row.biz_date !== makeupKey;
  });
  expect(weekendRows.length, 'window contains regular weekends').toBeGreaterThan(0);
  for (const row of weekendRows) {
    expect(Number(row.expected), `${row.biz_date} weekend expected`).toBe(NATURAL_TABLES);
    expect(Number(row.missing), `${row.biz_date} weekend missing`).toBe(0);
  }

  // 调休上班的周六在字典里，就必须按工作日算应采，且按字典采到后不报缺失
  const makeupRow = rows.find(row => row.biz_date === makeupKey)!;
  expect(makeupRow, 'makeup workday falls inside the window').toBeTruthy();
  expect(dayjs(makeupKey).day(), 'makeup day is a Saturday').toBe(6);
  expect(Number(makeupRow.expected), 'makeup Saturday expects work-day tables').toBe(
    NATURAL_TABLES + WORK_TABLES
  );
  expect(Number(makeupRow.collected), 'makeup Saturday collected').toBe(
    NATURAL_TABLES + WORK_TABLES
  );
  expect(Number(makeupRow.missing), 'makeup Saturday missing').toBe(0);

  // 真正的漏采仍要报出来，且只报那一天
  const missingDays = rows.filter(row => Number(row.missing) > 0).map(row => row.biz_date);
  expect(missingDays, 'only the deliberately missed work day is reported').toEqual([
    missDay.format('YYYY-MM-DD'),
  ]);
  const missRow = rows.find(row => row.biz_date === missDay.format('YYYY-MM-DD'))!;
  expect(Number(missRow.missing), 'missed work day missing count').toBe(WORK_TABLES);

  const artifact = {
    generatedAt: new Date().toISOString(),
    apiBase,
    database: dbName,
    window: { days: DAYS, anchor: anchor.format('YYYY-MM-DD') },
    seed: {
      calendars: { NATURAL_DAY: NATURAL_TABLES, WORK_DAY: WORK_TABLES, TRADE_DAY: TRADE_TABLES },
      holidays: [holiday1.format('YYYY-MM-DD'), holiday2.format('YYYY-MM-DD')],
      makeupWorkDay: makeupKey,
      missedWorkDay: missDay.format('YYYY-MM-DD'),
    },
    rows: observed,
    weekendRows: weekendRows.map(row => ({
      biz_date: row.biz_date,
      expected: Number(row.expected),
      collected: Number(row.collected),
      missing: Number(row.missing),
    })),
    failures,
    passed: failures.length === 0,
  };
  fs.mkdirSync(artifactDir, { recursive: true });
  fs.writeFileSync(
    path.join(artifactDir, 'missing-calendar-api.json'),
    `${JSON.stringify(artifact, null, 2)}\n`
  );

  expect(failures, 'per-day expected/collected/missing mismatches').toEqual([]);
});

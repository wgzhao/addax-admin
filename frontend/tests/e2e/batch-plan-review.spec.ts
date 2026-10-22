import { execFileSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { expect, test } from '@playwright/test';
import { apiLogin } from './helpers/auth';

const dbName = process.env.E2E_DB_NAME || 'addax_admin_history_e2e';
const dbUser = process.env.E2E_DB_USER || process.env.USER || '';
const artifactDir = fileURLToPath(new URL('./artifacts', import.meta.url));

function psql(sql: string): string {
  return execFileSync('psql', ['-d', dbName, '-v', 'ON_ERROR_STOP=1', '-q', '-At', '-c', sql], {
    encoding: 'utf8',
  }).trim();
}

function sqlLiteral(value: string): string {
  return `'${value.replaceAll("'", "''")}'`;
}

test('batch plan preview isolates managed plans from external blocking errors', async ({
  request,
}) => {
  test.setTimeout(90_000);
  const runId = `${Date.now()}_${Math.random().toString(36).slice(2, 8)}`;
  const managedSource = `plan_managed_source_${runId}`;
  const managedTarget = `plan_managed_target_${runId}`;
  const externalSource = `plan_external_source_${runId}`;
  const externalTarget = `plan_external_missing_${runId}`;
  const sourceCode = `PS${runId.slice(-6)}`;
  const targetCode = `PT${runId.slice(-6)}`;
  const jdbcUrl = `jdbc:postgresql://localhost:5432/${dbName}`;

  psql(`
    drop table if exists public.${managedSource};
    drop table if exists public.${managedTarget};
    drop table if exists public.${externalSource};
    drop table if exists public.${externalTarget};

    create table public.${managedSource} (
      id bigint primary key,
      customer_name text,
      amount numeric(12,2)
    );
    create table public.${externalSource} (
      id bigint primary key,
      customer_name text
    );

    delete from etl_source where code = ${sqlLiteral(sourceCode)};
    delete from etl_target where code = ${sqlLiteral(targetCode)};

    insert into etl_source (
      code, name, url, username, pass, start_at, enabled, max_concurrency, db_type, calendar
    ) values (
      ${sqlLiteral(sourceCode)}, 'Batch Plan E2E', ${sqlLiteral(jdbcUrl)},
      ${sqlLiteral(dbUser)}, '', '23:59:00', true, 1, 'POSTGRESQL', 'NATURAL_DAY'
    );

    insert into etl_target (
      code, name, target_type, connect_config, writer_template_key, enabled, is_default, remark
    ) values (
      ${sqlLiteral(targetCode)}, 'Batch Plan E2E Target', 'POSTGRESQL',
      ${sqlLiteral(JSON.stringify({ url: jdbcUrl, username: dbUser, password: '' }))},
      'wR', true, false, 'batch plan e2e'
    );
  `);

  const sourceId = Number(psql(`select id from etl_source where code = ${sqlLiteral(sourceCode)}`));
  const targetId = Number(psql(`select id from etl_target where code = ${sqlLiteral(targetCode)}`));
  const token = await apiLogin(request);

  const response = await request.post('/api/v1/tables/schema-plans/preview', {
    headers: { Authorization: `Bearer ${token}` },
    data: [
      {
        sourceId,
        sourceDb: 'public',
        sourceTable: managedSource,
        targetId,
        targetDb: 'public',
        targetTable: managedTarget,
        schemaManagementMode: 'MANAGED',
        targetOptions: {
          rdbms: { businessDateColumn: 'logdate', createPrimaryKey: true },
        },
      },
      {
        sourceId,
        sourceDb: 'public',
        sourceTable: externalSource,
        targetId,
        targetDb: 'public',
        targetTable: externalTarget,
        schemaManagementMode: 'EXTERNAL',
        targetOptions: {
          rdbms: { businessDateColumn: '', createPrimaryKey: false },
        },
      },
    ],
  });

  expect(response.ok()).toBeTruthy();
  const plans = await response.json();
  expect(plans).toHaveLength(2);

  const managed = plans.find((plan: { sourceTable: string }) => plan.sourceTable === managedSource);
  expect(managed.status).not.toBe('ERROR');
  expect(managed.action).toBe('CREATE_TABLE');
  expect(managed.plannedColumns.map((column: { name: string }) => column.name)).toEqual([
    'id',
    'customer_name',
    'amount',
    'logdate',
  ]);
  expect(
    managed.plannedColumns.find((column: { name: string }) => column.name === 'logdate')
  ).toEqual(expect.objectContaining({ role: 'BUSINESS_DATE', nullable: false }));
  expect(managed.plannedColumns.find((column: { name: string }) => column.name === 'id')).toEqual(
    expect.objectContaining({ primaryKey: true })
  );
  expect(managed.planDdl).toContain('PRIMARY KEY');

  const external = plans.find(
    (plan: { sourceTable: string }) => plan.sourceTable === externalSource
  );
  expect(external.status).toBe('ERROR');
  expect(external.action).toBe('BLOCKED');
  expect(external.findings).toEqual(
    expect.arrayContaining([
      expect.objectContaining({ code: 'TARGET_TABLE_MISSING', severity: 'ERROR' }),
    ])
  );

  fs.mkdirSync(artifactDir, { recursive: true });
  const artifactPath = path.join(artifactDir, 'batch-plan-preview.json');
  fs.writeFileSync(
    artifactPath,
    JSON.stringify(
      {
        runId,
        managed: {
          status: managed.status,
          action: managed.action,
          columns: managed.plannedColumns,
        },
        external: {
          status: external.status,
          action: external.action,
          findings: external.findings,
        },
      },
      null,
      2
    )
  );
  await test.info().attach('batch-plan-preview', {
    path: artifactPath,
    contentType: 'application/json',
  });
});

test('batch wizard reviews every selected table before the final create action', async ({
  page,
}) => {
  test.setTimeout(90_000);
  await page.setViewportSize({ width: 1440, height: 1000 });
  const runId = `${Date.now()}_${Math.random().toString(36).slice(2, 8)}`;
  const sourceCode = `WZ${runId.slice(-6)}`;
  const targetCode = `WT${runId.slice(-6)}`;
  const firstSource = `wizard_source_a_${runId}`;
  const secondSource = `wizard_source_b_${runId}`;
  const jdbcUrl = `jdbc:postgresql://localhost:5432/${dbName}`;

  psql(`
    drop table if exists public.${firstSource};
    drop table if exists public.${secondSource};
    create table public.${firstSource} (
      id bigint primary key,
      customer_name text,
      amount numeric(12,2)
    );
    create table public.${secondSource} (
      id bigint primary key,
      status text
    );
    delete from etl_source where code = ${sqlLiteral(sourceCode)};
    delete from etl_target where code = ${sqlLiteral(targetCode)};
    insert into etl_source (
      code, name, url, username, pass, start_at, enabled, max_concurrency, db_type, calendar
    ) values (
      ${sqlLiteral(sourceCode)}, 'Batch Review UI', ${sqlLiteral(jdbcUrl)},
      ${sqlLiteral(dbUser)}, '', '23:59:00', true, 1, 'POSTGRESQL', 'NATURAL_DAY'
    );
    insert into etl_target (
      code, name, target_type, connect_config, writer_template_key, enabled, is_default, remark
    ) values (
      ${sqlLiteral(targetCode)}, 'Batch Review UI Target', 'POSTGRESQL',
      ${sqlLiteral(JSON.stringify({ url: jdbcUrl, username: dbUser, password: '' }))},
      'wR', true, false, 'batch review ui'
    );
  `);

  await page.goto('/table/batch-add');

  const expectNoHorizontalOverflow = async (step: string) => {
    const state = await page.locator('.batch-add-shell').evaluate(shell => {
      const shellRect = shell.getBoundingClientRect();
      const offenders = Array.from(shell.querySelectorAll<HTMLElement>('*'))
        .map(element => ({
          className: element.className?.toString().slice(0, 120),
          tag: element.tagName.toLowerCase(),
          text: element.textContent?.trim().slice(0, 80),
          width: Math.round(element.getBoundingClientRect().width),
          right: Math.round(element.getBoundingClientRect().right),
        }))
        .filter(element => element.right > shellRect.right + 1)
        .slice(0, 12);
      return {
        overflow: shell.scrollWidth - shell.clientWidth,
        offenders,
      };
    });
    expect(
      state.overflow,
      `${step} should not overflow the viewport: ${JSON.stringify(state.offenders)}`
    ).toBeLessThanOrEqual(1);
  };

  await expectNoHorizontalOverflow('step 1');
  const sourcePanels = page.locator('.stage-grid--2 > .stage-panel');
  await expect(sourcePanels).toHaveCount(2);
  const firstSourcePanel = await sourcePanels.nth(0).boundingBox();
  const secondSourcePanel = await sourcePanels.nth(1).boundingBox();
  expect(firstSourcePanel).not.toBeNull();
  expect(secondSourcePanel).not.toBeNull();
  expect(Math.abs(firstSourcePanel!.y - secondSourcePanel!.y)).toBeLessThanOrEqual(1);
  expect(Math.abs(firstSourcePanel!.width - secondSourcePanel!.width)).toBeLessThanOrEqual(2);

  const sourceSelect = page.getByRole('combobox', { name: '采集源系统 *' });
  await page.locator('.v-select').filter({ hasText: '采集源系统' }).click();
  await page.keyboard.type(sourceCode);
  await page.keyboard.press('Enter');
  await expect(sourceSelect).toHaveValue(new RegExp(sourceCode));
  const dbSelect = page.getByRole('combobox', { name: '源数据库 *' });
  await page.locator('.v-select').filter({ hasText: '源数据库' }).click();
  await page.keyboard.type(dbName);
  await page.keyboard.press('Enter');
  await expect(dbSelect).toHaveValue(dbName);
  await page.getByRole('button', { name: '下一步' }).click();

  await expectNoHorizontalOverflow('step 2');
  await expect(page.getByRole('columnheader', { name: '源表名' })).toBeVisible();
  await expect(page.getByRole('columnheader', { name: '表注释' })).toBeVisible();
  await expect(page.getByRole('columnheader', { name: '近似行数' })).toBeVisible();
  await expect(page.getByRole('columnheader', { name: '目标库' })).toHaveCount(0);
  await expect(page.getByRole('columnheader', { name: '目标表' })).toHaveCount(0);

  await page.getByRole('textbox', { name: '搜索表名 / 注释' }).fill(runId);
  for (const table of [firstSource, secondSource]) {
    const row = page.getByRole('row', { name: new RegExp(table) });
    await expect(row).toBeVisible();
    await row.getByRole('checkbox').check();
  }
  await page.getByRole('button', { name: '下一步' }).click();

  await expect(page.getByText('Step 3')).toBeVisible();
  await expectNoHorizontalOverflow('step 3 desktop');
  const targetFields = page.locator('.target-field');
  await expect(targetFields).toHaveCount(4);
  const fieldBoxes = await Promise.all(
    Array.from({ length: 4 }, (_, index) => targetFields.nth(index).boundingBox())
  );
  expect(fieldBoxes.every(Boolean)).toBeTruthy();
  expect(Math.abs(fieldBoxes[0]!.y - fieldBoxes[1]!.y)).toBeLessThanOrEqual(1);
  expect(Math.abs(fieldBoxes[2]!.y - fieldBoxes[3]!.y)).toBeLessThanOrEqual(1);
  expect(fieldBoxes[2]!.y).toBeGreaterThan(fieldBoxes[0]!.y + fieldBoxes[0]!.height - 1);
  expect(Math.abs(fieldBoxes[0]!.width - fieldBoxes[1]!.width)).toBeLessThanOrEqual(2);
  expect(Math.abs(fieldBoxes[2]!.width - fieldBoxes[3]!.width)).toBeLessThanOrEqual(2);
  const targetRowGap = Math.round(fieldBoxes[2]!.y - (fieldBoxes[0]!.y + fieldBoxes[0]!.height));
  expect(
    targetRowGap,
    'target configuration rows need a clear vertical gap'
  ).toBeGreaterThanOrEqual(12);
  const fieldHeights = await targetFields.evaluateAll(fields =>
    fields.map(field => {
      const rect = field.getBoundingClientRect();
      const control = field.querySelector<HTMLElement>('.v-field');
      return {
        fieldHeight: Math.round(rect.height),
        controlHeight: control ? Math.round(control.getBoundingClientRect().height) : 0,
      };
    })
  );
  expect(
    fieldHeights[2]!.fieldHeight,
    `target database field must not stretch to the template hint row: ${JSON.stringify(
      fieldHeights
    )}`
  ).toBeLessThanOrEqual(fieldHeights[0]!.fieldHeight + 1);
  expect(fieldHeights[2]!.fieldHeight).toBe(fieldHeights[2]!.controlHeight);
  await page.screenshot({
    path: path.join(artifactDir, 'batch-wizard-target-layout.png'),
    fullPage: true,
  });

  await page.locator('.v-select').filter({ hasText: '目标端' }).click();
  await page.keyboard.type('Batch Review UI Target');
  await page
    .locator('.v-overlay-container .v-list-item:visible')
    .filter({ hasText: /^Batch Review UI Target \(POSTGRESQL\)$/ })
    .first()
    .click();
  await page.getByLabel(/目标库名/).fill('public');

  await page.setViewportSize({ width: 720, height: 900 });
  await expectNoHorizontalOverflow('step 3 compact');
  const compactFieldBoxes = await Promise.all(
    Array.from({ length: 4 }, (_, index) => targetFields.nth(index).boundingBox())
  );
  expect(new Set(compactFieldBoxes.map(box => box!.x)).size).toBe(1);
  await page.setViewportSize({ width: 1440, height: 1000 });

  await expect(page.getByRole('button', { name: '提交', exact: true })).toHaveCount(0);
  await page.getByRole('button', { name: '生成预览并校验' }).click();

  await expect(page.getByText('Step 4')).toBeVisible();
  await expectNoHorizontalOverflow('step 4');
  await expect(page.getByText('预览与校验').last()).toBeVisible();
  await expect(page.locator('.review-row').filter({ hasText: firstSource })).toBeVisible();
  await expect(page.locator('.review-row').filter({ hasText: secondSource })).toBeVisible();
  await expect(page.locator('.v-chip').filter({ hasText: /共 2 张 · 2 张纳入创建/ })).toBeVisible();
  await page.getByRole('button', { name: '确认创建 2 张表' }).click();

  await expect(page.getByText('Step 5')).toHaveCount(0);
  await expect(page.getByText('已创建 2 张表')).toBeVisible();
  await expectNoHorizontalOverflow('step 5');
  expect(psql(`select to_regclass('public.${firstSource}') is not null`)).toBe('t');
  expect(psql(`select to_regclass('public.${secondSource}') is not null`)).toBe('t');
  expect(
    psql(
      `select count(*) from etl_table where source_table in (${sqlLiteral(
        firstSource
      )}, ${sqlLiteral(secondSource)})`
    )
  ).toBe('2');

  fs.mkdirSync(artifactDir, { recursive: true });
  const artifactPath = path.join(artifactDir, 'batch-plan-review-ui.json');
  fs.writeFileSync(
    artifactPath,
    JSON.stringify(
      {
        runId,
        tables: [firstSource, secondSource],
        targetDb: 'public',
        result: 'STEP_5_CREATED_2',
      },
      null,
      2
    )
  );
  await test.info().attach('batch-plan-review-ui', {
    path: artifactPath,
    contentType: 'application/json',
  });
  await test.info().attach('batch-wizard-target-layout', {
    path: path.join(artifactDir, 'batch-wizard-target-layout.png'),
    contentType: 'image/png',
  });
});

test('managed RDBMS snapshot column is created and accepted by the target contract', async ({
  request,
}) => {
  test.setTimeout(90_000);
  const runId = `${Date.now()}_${Math.random().toString(36).slice(2, 8)}`;
  const sourceTable = `snapshot_contract_source_${runId}`;
  const targetTable = `snapshot_contract_target_${runId}`;
  const sourceCode = `SS${runId.slice(-6)}`;
  const targetCode = `ST${runId.slice(-6)}`;
  const jdbcUrl = `jdbc:postgresql://localhost:5432/${dbName}`;

  psql(`
    drop table if exists public.${sourceTable};
    drop table if exists public.${targetTable};
    create table public.${sourceTable} (
      id bigint primary key,
      customer_name text
    );
    delete from etl_source where code = ${sqlLiteral(sourceCode)};
    delete from etl_target where code = ${sqlLiteral(targetCode)};
    insert into etl_source (
      code, name, url, username, pass, start_at, enabled, max_concurrency, db_type, calendar
    ) values (
      ${sqlLiteral(sourceCode)}, 'Snapshot Contract E2E', ${sqlLiteral(jdbcUrl)},
      ${sqlLiteral(dbUser)}, '', '23:59:00', true, 1, 'POSTGRESQL', 'NATURAL_DAY'
    );
    insert into etl_target (
      code, name, target_type, connect_config, writer_template_key, enabled, is_default, remark
    ) values (
      ${sqlLiteral(targetCode)}, 'Snapshot Contract E2E Target', 'POSTGRESQL',
      ${sqlLiteral(JSON.stringify({ url: jdbcUrl, username: dbUser, password: '' }))},
      'wR', true, false, 'snapshot contract e2e'
    );
  `);

  const sourceId = Number(psql(`select id from etl_source where code = ${sqlLiteral(sourceCode)}`));
  const targetId = Number(psql(`select id from etl_target where code = ${sqlLiteral(targetCode)}`));
  const token = await apiLogin(request);
  const headers = { Authorization: `Bearer ${token}` };
  const targetOptions = {
    rdbms: { businessDateColumn: 'logdate', createPrimaryKey: true },
  };

  const previewResponse = await request.post('/api/v1/tables/schema-plans/preview', {
    headers,
    data: [
      {
        sourceId,
        sourceDb: dbName,
        sourceTable,
        targetId,
        targetDb: 'public',
        targetTable,
        schemaManagementMode: 'MANAGED',
        targetOptions,
      },
    ],
  });
  expect(previewResponse.ok()).toBeTruthy();
  const [plan] = await previewResponse.json();

  const commitResponse = await request.post('/api/v1/tables/schema-plans/commit', {
    headers,
    data: [
      {
        table: {
          sourceDb: dbName,
          sourceTable,
          targetDb: 'public',
          targetTable,
          targetId,
          targetOptions,
          filter: '1=1',
          status: 'U',
          kind: 'A',
          retryCnt: 3,
          maxRuntime: 120,
          sid: sourceId,
          duration: 0,
          tblComment: 'snapshot contract e2e',
          splitPk: '',
          autoPk: false,
          writeMode: 'insert',
          schemaManagementMode: 'MANAGED',
        },
        mappings: plan.mappings.map(
          (mapping: { sourceColumnName: string; targetColumnName: string }, index: number) => ({
            sourceColumnName: mapping.sourceColumnName,
            targetColumnName: mapping.targetColumnName,
            ordinalNo: index + 1,
          })
        ),
      },
    ],
  });
  expect(commitResponse.ok()).toBeTruthy();
  const [result] = await commitResponse.json();
  expect(result.status).toBe('CREATED');

  expect(
    psql(`
      select string_agg(column_name || ':' || data_type, ',' order by ordinal_position)
        from information_schema.columns
       where table_schema = 'public' and table_name = ${sqlLiteral(targetTable)}
    `)
  ).toContain('logdate:character varying');

  const contractResponse = await request.post(
    `/api/v1/tables/${result.tableId}/target-contract/check`,
    { headers }
  );
  expect(contractResponse.ok()).toBeTruthy();
  const contract = await contractResponse.json();
  expect(contract.status).not.toBe('ERROR');
  expect(
    contract.findings.some(
      (finding: { code: string }) => finding.code === 'TARGET_REQUIRED_COLUMN_UNMAPPED'
    )
  ).toBeFalsy();
});

test('external RDBMS snapshot column stays platform-provided without target DDL', async ({
  request,
}) => {
  test.setTimeout(90_000);
  const runId = `${Date.now()}_${Math.random().toString(36).slice(2, 8)}`;
  const sourceTable = `external_snapshot_source_${runId}`;
  const targetTable = `external_snapshot_target_${runId}`;
  const sourceCode = `ES${runId.slice(-6)}`;
  const targetCode = `ET${runId.slice(-6)}`;
  const jdbcUrl = `jdbc:postgresql://localhost:5432/${dbName}`;

  psql(`
    drop table if exists public.${sourceTable};
    drop table if exists public.${targetTable};
    create table public.${sourceTable} (
      id bigint primary key,
      customer_name text
    );
    create table public.${targetTable} (
      id bigint primary key,
      customer_name text,
      logdate varchar(8) not null
    );
    delete from etl_source where code = ${sqlLiteral(sourceCode)};
    delete from etl_target where code = ${sqlLiteral(targetCode)};
    insert into etl_source (
      code, name, url, username, pass, start_at, enabled, max_concurrency, db_type, calendar
    ) values (
      ${sqlLiteral(sourceCode)}, 'External Snapshot E2E', ${sqlLiteral(jdbcUrl)},
      ${sqlLiteral(dbUser)}, '', '23:59:00', true, 1, 'POSTGRESQL', 'NATURAL_DAY'
    );
    insert into etl_target (
      code, name, target_type, connect_config, writer_template_key, enabled, is_default, remark
    ) values (
      ${sqlLiteral(targetCode)}, 'External Snapshot E2E Target', 'POSTGRESQL',
      ${sqlLiteral(JSON.stringify({ url: jdbcUrl, username: dbUser, password: '' }))},
      'wR', true, false, 'external snapshot e2e'
    );
  `);

  const sourceId = Number(psql(`select id from etl_source where code = ${sqlLiteral(sourceCode)}`));
  const targetId = Number(psql(`select id from etl_target where code = ${sqlLiteral(targetCode)}`));
  const token = await apiLogin(request);
  const headers = { Authorization: `Bearer ${token}` };
  const targetOptions = {
    rdbms: { businessDateColumn: 'logdate', createPrimaryKey: false },
  };

  const previewResponse = await request.post('/api/v1/tables/schema-plans/preview', {
    headers,
    data: [
      {
        sourceId,
        sourceDb: dbName,
        sourceTable,
        targetId,
        targetDb: 'public',
        targetTable,
        schemaManagementMode: 'EXTERNAL',
        targetOptions,
      },
    ],
  });
  expect(previewResponse.ok()).toBeTruthy();
  const [plan] = await previewResponse.json();
  expect(plan.status).not.toBe('ERROR');
  expect(
    plan.mappings.some(
      (mapping: { targetColumnName?: string }) => mapping.targetColumnName === 'logdate'
    )
  ).toBeFalsy();

  const commitResponse = await request.post('/api/v1/tables/schema-plans/commit', {
    headers,
    data: [
      {
        table: {
          sourceDb: dbName,
          sourceTable,
          targetDb: 'public',
          targetTable,
          targetId,
          targetOptions,
          filter: '1=1',
          status: 'N',
          kind: 'A',
          retryCnt: 3,
          maxRuntime: 120,
          sid: sourceId,
          duration: 0,
          tblComment: 'external snapshot e2e',
          splitPk: '',
          autoPk: false,
          writeMode: 'insert',
          schemaManagementMode: 'EXTERNAL',
        },
        mappings: plan.mappings.map(
          (mapping: { sourceColumnName: string; targetColumnName: string }, index: number) => ({
            sourceColumnName: mapping.sourceColumnName,
            targetColumnName: mapping.targetColumnName,
            ordinalNo: index + 1,
          })
        ),
        targetSchemaFingerprint: plan.targetSchemaFingerprint,
      },
    ],
  });
  expect(commitResponse.ok()).toBeTruthy();
  const [result] = await commitResponse.json();
  expect(result.status).toBe('CREATED');

  expect(
    psql(`
      select string_agg(column_name || ':' || data_type, ',' order by ordinal_position)
        from information_schema.columns
       where table_schema = 'public' and table_name = ${sqlLiteral(targetTable)}
    `)
  ).toBe('id:bigint,customer_name:text,logdate:character varying');

  const missingDateTarget = `${targetTable}_missing_date`;
  psql(`
    drop table if exists public.${missingDateTarget};
    create table public.${missingDateTarget} (
      id bigint primary key,
      customer_name text
    );
  `);
  const blockedResponse = await request.post('/api/v1/tables/schema-plans/preview', {
    headers,
    data: [
      {
        sourceId,
        sourceDb: dbName,
        sourceTable,
        targetId,
        targetDb: 'public',
        targetTable: missingDateTarget,
        schemaManagementMode: 'EXTERNAL',
        targetOptions,
      },
    ],
  });
  expect(blockedResponse.ok()).toBeTruthy();
  const [blocked] = await blockedResponse.json();
  expect(blocked.status).toBe('ERROR');
  expect(blocked.findings).toEqual(
    expect.arrayContaining([
      expect.objectContaining({ code: 'TARGET_BUSINESS_DATE_COLUMN_MISSING' }),
    ])
  );
});

test('legacy managed batch initializes target tables after the definition commit', async ({
  request,
}) => {
  test.setTimeout(90_000);
  const runId = `${Date.now()}_${Math.random().toString(36).slice(2, 8)}`;
  const sourceTables = [`legacy_batch_a_${runId}`, `legacy_batch_b_${runId}`];
  const sourceCode = `LB${runId.slice(-6)}`;
  const targetCode = `LT${runId.slice(-6)}`;
  const jdbcUrl = `jdbc:postgresql://localhost:5432/${dbName}`;

  psql(`
    ${sourceTables
      .map(
        table => `
          drop table if exists public.${table};
          create table public.${table} (
            id bigint primary key,
            source_value text
          );
        `
      )
      .join('\n')}
    delete from etl_source where code = ${sqlLiteral(sourceCode)};
    delete from etl_target where code = ${sqlLiteral(targetCode)};
    insert into etl_source (
      code, name, url, username, pass, start_at, enabled, max_concurrency, db_type, calendar
    ) values (
      ${sqlLiteral(sourceCode)}, 'Legacy Batch E2E', ${sqlLiteral(jdbcUrl)},
      ${sqlLiteral(dbUser)}, '', '23:59:00', true, 1, 'POSTGRESQL', 'NATURAL_DAY'
    );
    insert into etl_target (
      code, name, target_type, connect_config, writer_template_key, enabled, is_default, remark
    ) values (
      ${sqlLiteral(targetCode)}, 'Legacy Batch E2E Target', 'POSTGRESQL',
      ${sqlLiteral(JSON.stringify({ url: jdbcUrl, username: dbUser, password: '' }))},
      'wR', true, false, 'legacy batch e2e'
    );
  `);

  const sourceId = Number(psql(`select id from etl_source where code = ${sqlLiteral(sourceCode)}`));
  const targetId = Number(psql(`select id from etl_target where code = ${sqlLiteral(targetCode)}`));
  const token = await apiLogin(request);
  const response = await request.post('/api/v1/tables/batch', {
    headers: { Authorization: `Bearer ${token}` },
    data: sourceTables.map(sourceTable => ({
      sourceDb: dbName,
      sourceTable,
      targetDb: 'public',
      targetTable: sourceTable,
      targetId,
      targetOptions: { rdbms: { businessDateColumn: '', createPrimaryKey: true } },
      filter: '1=1',
      status: 'U',
      kind: 'A',
      retryCnt: 3,
      maxRuntime: 120,
      sid: sourceId,
      duration: 0,
      tblComment: 'legacy batch e2e',
      splitPk: '',
      autoPk: false,
      writeMode: 'insert',
      schemaManagementMode: 'MANAGED',
    })),
  });
  expect(response.status()).toBe(201);

  await expect
    .poll(
      () =>
        sourceTables
          .map(table => psql(`select to_regclass('public.${table}') is not null`))
          .join(','),
      { timeout: 20_000 }
    )
    .toBe('t,t');
  await expect
    .poll(
      () =>
        psql(
          `select count(*) from etl_column where tid in (select id from etl_table where source_table in (${sourceTables
            .map(sqlLiteral)
            .join(', ')}))`
        ),
      { timeout: 20_000 }
    )
    .toBe('4');
});

import { test, expect, type APIRequestContext } from '@playwright/test';
import { execFileSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { apiLogin } from './helpers/auth';

const dbName = process.env.E2E_DB_NAME || 'addax_admin_history_e2e';
const dbUser = process.env.E2E_DB_USER || process.env.USER || '';
const fixtureHome = fileURLToPath(new URL('./fixtures/fake-addax', import.meta.url));
const artifactDir = fileURLToPath(new URL('./artifacts', import.meta.url));

function psql(sql: string): string {
  return execFileSync('psql', ['-d', dbName, '-v', 'ON_ERROR_STOP=1', '-q', '-At', '-c', sql], {
    encoding: 'utf8',
  }).trim();
}

function sqlLiteral(value: string): string {
  return `'${value.replaceAll("'", "''")}'`;
}

const authHeaders = (token: string) => ({ Authorization: `Bearer ${token}` });

async function detail(request: APIRequestContext, token: string, tid: number) {
  const response = await request.get(`/api/v1/tables/${tid}`, {
    headers: authHeaders(token),
  });
  expect(response.ok()).toBeTruthy();
  return response.json();
}

async function job(request: APIRequestContext, token: string, tid: number) {
  const response = await request.get(`/api/v1/tables/${tid}/addax-job`, {
    headers: authHeaders(token),
  });
  expect(response.ok()).toBeTruthy();
  return response.json();
}

async function latestExecution(request: APIRequestContext, token: string, tid: number) {
  const response = await request.get(`/api/v1/tables/${tid}/executions`, {
    headers: authHeaders(token),
  });
  expect(response.ok()).toBeTruthy();
  return (await response.json()).content[0];
}

test('external target contract, renamed mapping, drift blocking, and managed regression', async ({
  request,
}) => {
  test.setTimeout(120_000);
  const runId = `${Date.now()}_${Math.random().toString(36).slice(2, 8)}`;
  const sourceName = `external_mapping_source_${runId}`;
  const targetName = `external_mapping_target_${runId}`;
  const managedSource = `managed_mapping_source_${runId}`;
  const managedTarget = `managed_mapping_target_${runId}`;
  const sourceCode = `XE${runId.slice(-6)}`;
  const targetCode = `XT${runId.slice(-6)}`;
  const jdbcUrl = `jdbc:postgresql://localhost:5432/${dbName}`;
  const fixtureHomeSql = fixtureHome.replaceAll("'", "''");

  psql(`
    drop table if exists public.${sourceName};
    drop table if exists public.${targetName};
    drop table if exists public.${managedSource};
    drop table if exists public.${managedTarget};

    create table public.${sourceName} (
      id bigint primary key,
      customer_name text,
      amount numeric(12,2),
      updated_at timestamp
    );
    insert into public.${sourceName} values
      (1, 'Alice', 12.50, '2026-10-22 10:00:00'),
      (2, 'Bob', 99.95, '2026-10-22 11:00:00');

    create table public.${targetName} (
      id bigint primary key,
      customer_name_std text,
      amount numeric(12,2),
      updated_at timestamp,
      loaded_at timestamp default now()
    );

    create table public.${managedSource} (
      id bigint primary key,
      source_value text
    );

    delete from addax_log where tid in (
      select id from etl_table where source_table in (${sqlLiteral(sourceName)}, ${sqlLiteral(
    managedSource
  )})
    );
    delete from etl_statistic where tid in (
      select id from etl_table where source_table in (${sqlLiteral(sourceName)}, ${sqlLiteral(
    managedSource
  )})
    );
    delete from etl_jour where tid in (
      select id from etl_table where source_table in (${sqlLiteral(sourceName)}, ${sqlLiteral(
    managedSource
  )})
    );
    delete from etl_execution where tid in (
      select id from etl_table where source_table in (${sqlLiteral(sourceName)}, ${sqlLiteral(
    managedSource
  )})
    );
    delete from etl_column_mapping where tid in (
      select id from etl_table where source_table in (${sqlLiteral(sourceName)}, ${sqlLiteral(
    managedSource
  )})
    );
    delete from etl_column where tid in (
      select id from etl_table where source_table in (${sqlLiteral(sourceName)}, ${sqlLiteral(
    managedSource
  )})
    );
    delete from etl_job where tid in (
      select id from etl_table where source_table in (${sqlLiteral(sourceName)}, ${sqlLiteral(
    managedSource
  )})
    );
    delete from etl_table where source_table in (${sqlLiteral(sourceName)}, ${sqlLiteral(
    managedSource
  )});
    delete from etl_source where code = ${sqlLiteral(sourceCode)};
    delete from etl_target where code = ${sqlLiteral(targetCode)};

    insert into etl_source (
      code, name, url, username, pass, start_at, enabled, max_concurrency, db_type, calendar
    ) values (
      ${sqlLiteral(sourceCode)}, 'External Mapping E2E', ${sqlLiteral(jdbcUrl)},
      ${sqlLiteral(dbUser)}, '', '23:59:00', true, 1, 'POSTGRESQL', 'NATURAL_DAY'
    );

    insert into etl_target (
      code, name, target_type, connect_config, writer_template_key, enabled, is_default, remark
    ) values (
      ${sqlLiteral(targetCode)}, 'External Mapping E2E Target', 'POSTGRESQL',
      ${sqlLiteral(JSON.stringify({ url: jdbcUrl, username: dbUser, password: '' }))},
      'wR', true, false, 'external mapping e2e'
    );

    update sys_item set item_value = '${fixtureHomeSql}'
      where dict_code = 1000 and item_key = 'ADDAX';
  `);

  const sourceId = Number(psql(`select id from etl_source where code = ${sqlLiteral(sourceCode)}`));
  const targetId = Number(psql(`select id from etl_target where code = ${sqlLiteral(targetCode)}`));
  const token = await apiLogin(request);

  const previewResponse = await request.post('/api/v1/tables/schema-preview', {
    headers: authHeaders(token),
    data: {
      sourceId,
      sourceDb: 'public',
      sourceTable: sourceName,
      targetId,
      targetDb: 'public',
      targetTable: targetName,
      schemaManagementMode: 'EXTERNAL',
      targetOptions: {
        rdbms: { businessDateColumn: '', createPrimaryKey: false },
      },
    },
  });
  expect(previewResponse.ok()).toBeTruthy();
  const preview = await previewResponse.json();
  expect(preview.targetTable.exists).toBe(true);
  expect(preview.sourceColumns.map((column: { name: string }) => column.name)).toEqual([
    'id',
    'customer_name',
    'amount',
    'updated_at',
  ]);
  expect(preview.unmatchedSourceColumns).toEqual(['customer_name']);
  expect(preview.unmatchedTargetColumns).toContain('customer_name_std');
  expect(
    preview.findings.some((finding: { code: string }) => finding.code === 'TARGET_EXTRA_COLUMN')
  ).toBeTruthy();

  const mappings = preview.suggestions.map(
    (suggestion: { sourceColumnName: string; targetColumnName?: string }) => ({
      sourceColumnName: suggestion.sourceColumnName,
      targetColumnName:
        suggestion.sourceColumnName === 'customer_name'
          ? 'customer_name_std'
          : suggestion.targetColumnName,
    })
  );

  const createResponse = await request.post('/api/v1/tables/external', {
    headers: authHeaders(token),
    data: {
      table: {
        sourceDb: 'public',
        sourceTable: sourceName,
        targetDb: 'public',
        targetTable: targetName,
        targetId,
        targetOptions: {
          rdbms: { businessDateColumn: '', createPrimaryKey: false },
        },
        filter: '1=1',
        status: 'N',
        kind: 'A',
        retryCnt: 3,
        maxRuntime: 120,
        sid: sourceId,
        duration: 0,
        tblComment: 'external mapping e2e',
        splitPk: '',
        autoPk: false,
        writeMode: 'insert',
        schemaManagementMode: 'EXTERNAL',
      },
      mappings,
      targetSchemaFingerprint: preview.targetSchemaFingerprint,
    },
  });
  expect(createResponse.ok()).toBeTruthy();
  const created = await createResponse.json();
  const tid = created.id as number;

  const mappingResponse = await request.get(`/api/v1/tables/${tid}/column-mappings`, {
    headers: authHeaders(token),
  });
  expect(mappingResponse.ok()).toBeTruthy();
  const savedMappings = await mappingResponse.json();
  expect(savedMappings).toEqual(
    expect.arrayContaining([
      expect.objectContaining({
        sourceColumnName: 'customer_name',
        targetColumnName: 'customer_name_std',
      }),
    ])
  );

  const generatedJob = await job(request, token, tid);
  expect(generatedJob.job.content.reader.parameter.column).toEqual(
    expect.arrayContaining([expect.stringContaining('customer_name as customer_name_std')])
  );
  expect(generatedJob.job.content.writer.parameter.column).toEqual(
    expect.arrayContaining([expect.stringContaining('customer_name_std')])
  );

  const contractBeforeResponse = await request.post(`/api/v1/tables/${tid}/target-contract/check`, {
    headers: authHeaders(token),
  });
  expect(contractBeforeResponse.ok()).toBeTruthy();
  const contractBefore = await contractBeforeResponse.json();
  expect(contractBefore.status).toBe('WARNING');
  expect(
    contractBefore.findings.some(
      (finding: { code: string; column?: string }) =>
        finding.code === 'TARGET_EXTRA_COLUMN' && finding.column === 'loaded_at'
    )
  ).toBeTruthy();

  const targetColumnsBefore = psql(`
    select string_agg(column_name || ':' || data_type, ',' order by ordinal_position)
      from information_schema.columns
     where table_schema = 'public' and table_name = ${sqlLiteral(targetName)}
  `);
  expect(targetColumnsBefore).toContain('customer_name_std:text');

  psql(`
    alter table public.${targetName}
      rename column customer_name_std to customer_name_changed;
  `);

  const driftCheckResponse = await request.post(`/api/v1/tables/${tid}/target-contract/check`, {
    headers: authHeaders(token),
  });
  expect(driftCheckResponse.ok()).toBeTruthy();
  const driftCheck = await driftCheckResponse.json();
  expect(driftCheck.status).toBe('ERROR');
  expect(
    driftCheck.findings.some(
      (finding: { code: string; column?: string }) =>
        finding.code === 'TARGET_COLUMN_MISSING' && finding.column === 'customer_name_std'
    )
  ).toBeTruthy();

  const blockedRun = await request.post(`/api/v1/tasks/${tid}/executions?isSync=true`, {
    headers: authHeaders(token),
    data: {},
  });
  expect(blockedRun.ok()).toBeTruthy();
  const blockedResult = await blockedRun.json();
  expect(blockedResult.success).toBe(false);
  expect(blockedResult.message).toContain('Target contract blocked');

  const failedExecution = await latestExecution(request, token, tid);
  expect(failedExecution.status).toBe('failed');
  expect(failedExecution.configSnapshot.table.schemaManagementMode).toBe('EXTERNAL');
  expect(failedExecution.configSnapshot.columns).toEqual(
    expect.arrayContaining([
      expect.objectContaining({
        sourceName: 'customer_name',
        targetName: 'customer_name_std',
      }),
    ])
  );
  expect(failedExecution.configSnapshot.schemaValidation.status).toBe('ERROR');

  const tableStatus = psql(`select status from etl_table where id = ${tid}`);
  expect(tableStatus).toBe('U');

  psql(`
    alter table public.${targetName}
      rename column customer_name_changed to customer_name_std;
  `);
  const recoveredResponse = await request.post(`/api/v1/tables/${tid}/target-contract/check`, {
    headers: authHeaders(token),
  });
  expect(recoveredResponse.ok()).toBeTruthy();
  expect((await recoveredResponse.json()).status).toBe('WARNING');

  const managedCreateResponse = await request.post('/api/v1/tables', {
    headers: authHeaders(token),
    data: {
      sourceDb: 'public',
      sourceTable: managedSource,
      targetDb: 'public',
      targetTable: managedTarget,
      targetId,
      targetOptions: {
        rdbms: { businessDateColumn: '', createPrimaryKey: true },
      },
      filter: '1=1',
      status: 'N',
      kind: 'A',
      retryCnt: 3,
      maxRuntime: 120,
      sid: sourceId,
      duration: 0,
      tblComment: 'managed regression',
      splitPk: '',
      autoPk: false,
      writeMode: 'insert',
      schemaManagementMode: 'MANAGED',
    },
  });
  expect(managedCreateResponse.ok()).toBeTruthy();
  const managedTable = await managedCreateResponse.json();
  const managedRefresh = await request.post(`/api/v1/tables/${managedTable.id}/actions/refresh`, {
    headers: authHeaders(token),
  });
  expect(managedRefresh.ok()).toBeTruthy();
  expect(psql(`select to_regclass('public.${managedTarget}') is not null`)).toBe('t');

  const targetColumnsAfter = psql(`
    select string_agg(column_name || ':' || data_type, ',' order by ordinal_position)
      from information_schema.columns
     where table_schema = 'public' and table_name = ${sqlLiteral(targetName)}
  `);
  expect(targetColumnsAfter).toBe(targetColumnsBefore);

  fs.mkdirSync(artifactDir, { recursive: true });
  const artifact = {
    runId,
    external: {
      tid,
      source: `public.${sourceName}`,
      target: `public.${targetName}`,
      targetColumnsBefore,
      targetColumnsAfter,
      mappings: savedMappings.map(
        (mapping: { sourceColumnName: string; targetColumnName: string }) =>
          `${mapping.sourceColumnName}->${mapping.targetColumnName}`
      ),
      readerColumns: generatedJob.job.content.reader.parameter.column,
      writerColumns: generatedJob.job.content.writer.parameter.column,
      contractBefore: {
        status: contractBefore.status,
        fingerprint: contractBefore.fingerprint,
        findings: contractBefore.findings,
      },
      driftContract: {
        status: driftCheck.status,
        findings: driftCheck.findings,
      },
      blockedExecution: {
        id: failedExecution.id,
        status: failedExecution.status,
        message: failedExecution.errorMessage,
        schemaValidationStatus: failedExecution.configSnapshot.schemaValidation.status,
        mappings: failedExecution.configSnapshot.columns,
      },
      statusAfterBlock: tableStatus,
      recoveredStatus: 'WARNING',
    },
    managedRegression: {
      tid: managedTable.id,
      targetCreated: true,
    },
    verifiedAt: new Date().toISOString(),
  };
  const artifactPath = path.join(artifactDir, 'external-schema-mapping.json');
  fs.writeFileSync(artifactPath, JSON.stringify(artifact, null, 2));
  await test.info().attach('external-schema-mapping', {
    path: artifactPath,
    contentType: 'application/json',
  });
});

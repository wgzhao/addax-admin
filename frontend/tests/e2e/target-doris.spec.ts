import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { expect, test } from '@playwright/test';

const artifactDir = fileURLToPath(new URL('./artifacts', import.meta.url));

test('Doris target dialog exposes Stream Load settings and keeps type/template paired', async ({
  page,
}) => {
  test.setTimeout(60_000);
  await page.setViewportSize({ width: 1440, height: 1000 });
  await page.goto('/target?action=create');

  const dialog = page.getByRole('dialog');
  await expect(dialog).toBeVisible();
  await page.locator('#target-code').fill('E2E_DORIS_TARGET');
  await page.locator('#target-name').fill('E2E Doris Target');

  const typeField = dialog.locator('.field').filter({ hasText: '目标端类型' });
  await typeField.locator('.v-field').click();
  await page.keyboard.type('DORIS');
  await page.keyboard.press('Enter');
  await expect(typeField).toContainText('DORIS');

  // Doris replaces the HDFS settings section and uses the FE MySQL endpoint placeholder.
  await expect(page.locator('#target-doris-load-url')).toBeVisible();
  await expect(page.locator('#target-default-fs')).toHaveCount(0);
  await expect(page.locator('#target-url')).toHaveAttribute(
    'placeholder',
    'jdbc:mysql://fe:9030/db'
  );

  await page.locator('#target-url').fill('jdbc:mysql://fe:9030/ods');
  await expect(dialog.locator('.dialect-hint')).toContainText('DORIS');
  await expect(dialog.locator('.dialect-hint')).toContainText('doriswriter');

  // The schema prefix is a common mistake; the dialog rejects it before any API call.
  await page.locator('#target-doris-load-url').fill('http://fe1:8030');
  await dialog.getByRole('button', { name: '保存' }).click();
  await expect(dialog.getByText('端点格式不正确')).toBeVisible();

  await page.locator('#target-doris-load-url').fill('fe1:8030\nfe2:8030');
  await page.locator('#target-doris-batch-size').fill('4096');
  await page.locator('#target-doris-flush-interval').fill('1500');
  await page.locator('#target-doris-flush-queue').fill('2');
  await page.locator('#target-doris-load-props').fill('{ "format": "csv" }');

  await dialog.getByRole('button', { name: '查看连接 JSON' }).click();
  const connectJson = page.locator('#target-connect-json');
  await expect(connectJson).toBeVisible();
  const connect = JSON.parse(await connectJson.inputValue());
  expect(connect).toMatchObject({
    url: 'jdbc:mysql://fe:9030/ods',
    loadUrl: ['fe1:8030', 'fe2:8030'],
    batchSize: 4096,
    flushInterval: 1500,
    flushQueueLength: 2,
    loadProps: { format: 'csv' },
  });

  // Leaving Doris must not leave wD behind: the backend pairs DORIS with wD and rejects a mismatch.
  await typeField.locator('.v-field').click();
  await page.keyboard.type('RDBMS');
  await page.keyboard.press('Enter');
  await expect(typeField).toContainText('RDBMS');
  await expect(page.locator('#target-doris-load-url')).toHaveCount(0);
  const templateField = dialog.locator('.field').filter({ hasText: 'Writer 模板键' });
  await expect(templateField).toContainText('wR');

  // wD must not be selectable for an RDBMS target: choosing the key alone used to show the Doris
  // fields, then the save silently rewrote the key back and discarded the Stream Load config.
  await templateField.locator('.v-field').click();
  const templateOptions = page.locator('.v-overlay-container .v-list-item');
  await expect(templateOptions.filter({ hasText: 'wD' })).toHaveCount(0);
  await page.keyboard.press('Escape');

  await typeField.locator('.v-field').click();
  await page.keyboard.type('DORIS');
  await page.keyboard.press('Enter');
  await expect(page.locator('#target-doris-load-url')).toBeVisible();
  await expect(templateField).toContainText('wD');

  await templateField.locator('.v-field').click();
  await expect(page.locator('.v-overlay-container .v-list-item').filter({ hasText: 'wD' })).toHaveCount(1);
  await page.keyboard.press('Escape');

  fs.mkdirSync(artifactDir, { recursive: true });
  const artifactPath = path.join(artifactDir, 'target-doris.json');
  fs.writeFileSync(
    artifactPath,
    JSON.stringify(
      {
        connect,
        checks: [
          'DORIS type shows the Stream Load section and hides HDFS settings',
          'the dialect hint is DORIS · doriswriter, not MYSQL',
          'loadUrl with an http:// prefix is rejected client-side',
          'connect JSON carries loadUrl, batch/flush knobs and loadProps',
          'switching type keeps DORIS/wD and RDBMS/wR paired',
        ],
      },
      null,
      2
    )
  );
  await test.info().attach('target-doris', {
    path: artifactPath,
    contentType: 'application/json',
  });
});

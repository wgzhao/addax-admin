import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { expect, test } from '@playwright/test';

const artifactDir = fileURLToPath(new URL('./artifacts', import.meta.url));

test('target dialog keeps its fields readable and validates required input', async ({ page }) => {
  test.setTimeout(60_000);
  await page.setViewportSize({ width: 1440, height: 1000 });
  await page.goto('/target?action=create');

  const dialog = page.getByRole('dialog');
  await expect(dialog).toBeVisible();
  await expect(dialog.getByText('新增目标端')).toBeVisible();

  const card = dialog.locator('.v-card').first();
  // Wait out the dialog scale transition before measuring field widths.
  await expect
    .poll(async () => Math.round((await card.boundingBox())!.width))
    .toBeGreaterThanOrEqual(840);
  const cardBox = await card.boundingBox();
  expect(cardBox!.width).toBeLessThanOrEqual(900);

  const basicFieldWidths = await dialog
    .locator('.form-section')
    .first()
    .locator('.field')
    .evaluateAll(fields => fields.map(field => Math.round(field.getBoundingClientRect().width)));
  expect(Math.min(...basicFieldWidths)).toBeGreaterThanOrEqual(350);

  const urlFieldWidth = await dialog
    .locator('.field')
    .filter({ hasText: 'JDBC URL' })
    .evaluate(field => Math.round(field.getBoundingClientRect().width));
  expect(urlFieldWidth).toBeGreaterThanOrEqual(750);

  const bodyOverflow = await dialog
    .locator('.ds-dialog-body')
    .evaluate(body => body.scrollWidth - body.clientWidth);
  expect(bodyOverflow).toBeLessThanOrEqual(1);

  await page.screenshot({
    path: path.join(artifactDir, 'target-dialog-layout.png'),
    fullPage: true,
  });

  await dialog.getByRole('button', { name: '保存' }).click();
  await expect(dialog.getByText('必填项')).toHaveCount(2);

  await page.locator('#target-code').fill('E2E_DIALOG_TARGET');
  await page.locator('#target-name').fill('E2E Dialog Target');
  await page.locator('#target-url').fill('jdbc:postgresql://localhost:5432/demo');
  await page.locator('#target-user').fill('demo');

  const password = page.getByLabel('连接密码');
  await password.fill('secret');
  await expect(password).toHaveAttribute('type', 'password');
  await dialog.getByRole('button', { name: '显示密码' }).click();
  await expect(password).toHaveAttribute('type', 'text');
  await expect(dialog.getByRole('button', { name: '隐藏密码' })).toBeVisible();

  await dialog.getByRole('button', { name: '查看连接 JSON' }).click();
  const connectJson = page.locator('#target-connect-json');
  await expect(connectJson).toBeVisible();
  expect(JSON.parse(await connectJson.inputValue())).toMatchObject({
    url: 'jdbc:postgresql://localhost:5432/demo',
    username: 'demo',
    password: 'secret',
  });
  await expect(dialog.getByRole('button', { name: '隐藏连接 JSON' })).toBeVisible();

  const typeField = dialog.locator('.field').filter({ hasText: '目标端类型' });
  await typeField.locator('.v-field').click();
  await page.keyboard.type('POSTGRESQL');
  await page.keyboard.press('Enter');
  await expect(typeField).toContainText('POSTGRESQL');
  await expect(page.locator('#target-driver')).toHaveCount(0);
  await expect(page.locator('#target-driver-path')).toHaveCount(0);

  await typeField.locator('.v-field').click();
  await page.keyboard.type('HDFS');
  await page.keyboard.press('Enter');
  await expect(typeField).toContainText('HDFS');
  await expect(page.locator('#target-driver')).toBeVisible();
  await expect(page.locator('#target-driver-path')).toBeVisible();

  await expect(dialog.getByText('必填项')).toHaveCount(0);

  fs.mkdirSync(artifactDir, { recursive: true });
  const artifactPath = path.join(artifactDir, 'target-dialog.json');
  fs.writeFileSync(
    artifactPath,
    JSON.stringify(
      {
        dialog: { width: Math.round(cardBox!.width) },
        basicFieldWidths,
        urlFieldWidth,
        checks: [
          'required fields show inline errors',
          'password visibility toggles',
          'connect JSON reflects the form',
          'driver fields follow the target type',
        ],
      },
      null,
      2
    )
  );
  await test.info().attach('target-dialog', {
    path: artifactPath,
    contentType: 'application/json',
  });
});

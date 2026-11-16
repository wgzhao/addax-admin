import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { expect, test } from '@playwright/test';

const artifactDir = fileURLToPath(new URL('./artifacts', import.meta.url));

/**
 * HDFS 目标端的“测试连接”只能证明 HiveServer2 可达，证明不了能不能写 HDFS。这个用例验证：
 * 1. 打开弹窗就能看到完整校验清单（全部待校验），且不用滚动即可看到，不是等跑完才冒出来的结果块；
 * 2. 点「校验目标端」后逐项打勾：前四项已完成时，HDFS 写入冒烟仍在运行中；
 * 3. 校验会真的跑一次写入探针，并逐项报告 Addax 运行时、插件、模板、HiveServer2、HDFS 写入；
 * 4. 改错 defaultFS 后，探针失败并给出可操作的根因，而不是一个笼统的“连接失败”；
 * 5. 编辑任一设置会把清单重置为待校验，避免陈旧结论；
 * 6. RDBMS 目标端保留原来的「测试连接」（它本来就只测 JDBC）。
 *
 * 环境依赖：本机可访问测试集群 Hive (nn01:10000) 与 HDFS (hdfs://yytz)，且 /tmp 可写。
 */
test('HDFS target health check runs a real write probe and reports each check', async ({
  page,
}) => {
  test.setTimeout(180_000);
  await page.setViewportSize({ width: 1440, height: 1100 });
  await page.goto('/target?action=create');

  const dialog = page.getByRole('dialog');
  await expect(dialog).toBeVisible();
  const code = `E2E_HC_${Date.now()}`;

  await page.locator('#target-code').fill(code);
  await page.locator('#target-name').fill('E2E HDFS 健康检查');
  await page.locator('#target-url').fill('jdbc:hive2://nn01.bigdata.gp51.com:10000/default');
  await page.locator('#target-user').fill('hive');
  await page.locator('#target-default-fs').fill('hdfs://yytz');
  await page.locator('#target-hdfs-prefix').fill('/tmp');
  await page.locator('#target-hdfs-site').fill('/etc/hadoop/conf/hdfs-site.xml');

  // The dialog footer used to hold one ambiguous “测试连接”; for HDFS it now lives next to the
  // settings it actually exercises.
  await expect(
    dialog.locator('.ds-dialog-actions').getByRole('button', { name: '测试连接' })
  ).toHaveCount(0);
  await expect(
    dialog.locator('.form-section').nth(1).getByRole('button', { name: '测试连接' })
  ).toHaveCount(0);
  const healthButton = dialog.getByRole('button', { name: '校验目标端' });
  await expect(healthButton).toBeVisible();

  // The checklist exists before the first run, in execution order, and is visible without scrolling.
  const checks = dialog.locator('.target-check__item');
  await expect(checks).toHaveCount(5);
  await expect(dialog.locator('.target-check')).toBeInViewport();
  await expect(dialog.getByText('待校验')).toBeVisible();
  expect(await dialog.locator('.target-check__label').allTextContents()).toEqual([
    'Addax 程序目录',
    'hdfswriter 插件',
    'HDFS 写入模板',
    'HiveServer2 连接',
    'HDFS 写入冒烟',
  ]);
  await expect(dialog.locator('.target-check__item.is-pending')).toHaveCount(5);

  await healthButton.click();
  // Progressive ticking: by the time the multi-second probe runs, the four cheap checks are already
  // ticked — the operator sees progress instead of one spinner for the whole batch.
  const probeRow = checks.filter({ hasText: 'HDFS 写入冒烟' });
  await expect(probeRow).toHaveClass(/is-running/, { timeout: 90_000 });
  await expect(dialog.locator('.target-check__item.is-ok')).toHaveCount(4);
  fs.mkdirSync(artifactDir, { recursive: true });
  await dialog.locator('.target-check').screenshot({
    path: path.join(artifactDir, 'target-health-check-running.png'),
  });

  await expect(dialog.getByText('目标端校验通过')).toBeVisible({ timeout: 120_000 });
  await expect(probeRow).toHaveClass(/is-ok/);
  await expect(healthButton).toBeEnabled();
  await expect(dialog.locator('.target-check__item.is-ok')).toHaveCount(5);
  await expect(dialog.locator('.target-check__item.is-fail')).toHaveCount(0);
  // The probe writes under the target's own prefix, so the result names the exact directory.
  await expect(dialog.locator('.target-check')).toContainText(`/tmp/_addax_admin_check/${code}`);
  await expect(dialog.locator('.target-check')).toBeInViewport();
  await dialog.locator('.target-check').screenshot({
    path: path.join(artifactDir, 'target-health-check.png'),
  });
  await page.screenshot({
    path: path.join(artifactDir, 'target-health-check-layout.png'),
  });

  // Any edit puts the checklist back to pending while keeping it visible: a stale “pass” next to
  // changed settings is worse than none.
  await page.locator('#target-default-fs').fill('hdfs://nonexistent-cluster');
  await expect(dialog.locator('.target-check')).toBeVisible();
  await expect(dialog.getByText('待校验')).toBeVisible();
  await expect(dialog.locator('.target-check__item.is-ok')).toHaveCount(0);
  await expect(dialog.locator('.target-check__item.is-pending')).toHaveCount(5);

  await healthButton.click();
  await expect(dialog.getByText('目标端校验未通过')).toBeVisible({ timeout: 120_000 });
  const failed = dialog.locator('.target-check__item.is-fail');
  await expect(failed).toHaveCount(1);
  await expect(failed).toContainText('UnknownHostException');
  await expect(failed).toContainText('检查 defaultFS');
  // Cheap checks before the probe are still reported independently.
  await expect(dialog.locator('.target-check__item.is-ok')).toHaveCount(4);
  await dialog.locator('.target-check').screenshot({
    path: path.join(artifactDir, 'target-health-check-fail.png'),
  });

  // RDBMS keeps the plain JDBC test: nothing beyond the connection is configurable there.
  const typeField = dialog.locator('.field').filter({ hasText: '目标端类型' });
  await typeField.locator('.v-field').click();
  await page.keyboard.type('RDBMS');
  await page.keyboard.press('Enter');
  await expect(dialog.getByRole('button', { name: '测试连接' })).toBeVisible();
  await expect(dialog.getByRole('button', { name: '校验目标端' })).toHaveCount(0);

  fs.writeFileSync(
    path.join(artifactDir, 'target-health-check.json'),
    JSON.stringify(
      {
        target: code,
        hiveUrl: 'jdbc:hive2://nn01.bigdata.gp51.com:10000/default',
        defaultFS: 'hdfs://yytz',
        probeDir: `/tmp/_addax_admin_check/${code}`,
        progressive: '打开弹窗即列出 5 项待校验；写入探针运行时前 4 项已打勾',
        positiveChecks: [
          'Addax 程序目录 ok',
          'hdfswriter 插件 ok',
          'HDFS 写入模板 ok',
          'HiveServer2 连接 ok',
          'HDFS 写入冒烟 ok (真实写入 /tmp/_addax_admin_check/<code>)',
        ],
        negativeCheck:
          'defaultFS=hdfs://nonexistent-cluster → 前 4 项 ok、HDFS 写入冒烟 fail (UnknownHostException)',
        invalidation: '编辑 defaultFS 后清单保留并重置为待校验',
        rdbms: '仅显示「测试连接」',
      },
      null,
      2
    )
  );
});

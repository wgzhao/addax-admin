import { test, expect, type Page } from '@playwright/test';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

// Guards the CJK typography layer: Vuetify typography classes must inherit the
// Chinese-first font stack, Material's Latin tracking must be zeroed, synthetic
// weights disabled, and tables must use hover instead of zebra stripes.
//
// Artifacts: tests/e2e/artifacts/typography/{light,dark}-{dashboard,table}.png
//            tests/e2e/artifacts/typography/typography-{light,dark}.json
const artifactDir = fileURLToPath(new URL('./artifacts/typography', import.meta.url));

type Computed = {
  fontFamily: string;
  fontSize: string;
  fontWeight: string;
  lineHeight: string;
  letterSpacing: string;
  fontSynthesis: string;
  textAutospace: string;
};

async function openDashboard(page: Page, theme: 'light' | 'dark') {
  await page.addInitScript(value => localStorage.setItem('theme', value), theme);
  await page.addInitScript(() => localStorage.setItem('addax.onboarding.dismissed', '1'));
  await page.goto('/');
  await expect(page.locator('.stat-tile').first()).toBeVisible({ timeout: 20_000 });
  await page.waitForTimeout(400);
}

async function readComputed(page: Page, selector: string): Promise<Computed> {
  return page.locator(selector).first().evaluate(el => {
    const style = getComputedStyle(el);
    return {
      fontFamily: style.fontFamily,
      fontSize: style.fontSize,
      fontWeight: style.fontWeight,
      lineHeight: style.lineHeight,
      letterSpacing: style.letterSpacing,
      fontSynthesis: style.fontSynthesis,
      // Older engines ignore text-autospace and return an empty string.
      textAutospace: (style as CSSStyleDeclaration & { textAutospace?: string }).textAutospace ?? '',
    };
  });
}

async function rowBackground(page: Page, index: number) {
  return page
    .locator('.ds-table-wrap:visible tbody tr')
    .nth(index)
    .evaluate(el => getComputedStyle(el).backgroundColor);
}

test.describe('CJK typography layer', () => {
  test.describe.configure({ mode: 'serial' });

  for (const theme of ['light', 'dark'] as const) {
    test(`keeps Chinese metrics and clean table surfaces in ${theme}`, async ({ page }) => {
      fs.mkdirSync(artifactDir, { recursive: true });
      await openDashboard(page, theme);

      const app = await readComputed(page, '.v-application');
      expect(app.fontFamily).toContain('PingFang SC');
      expect(app.fontFamily).not.toMatch(/^Roboto/);
      expect(app.fontSynthesis).toBe('none');
      expect(['normal', '']).toContain(app.textAutospace);

      // Chromium normalises `letter-spacing: 0` to "normal"; anything else must
      // be zero as well, never Material's positive tracking.
      const isZeroTracking = (value: string) => value === 'normal' || parseFloat(value) === 0;

      const button = await readComputed(page, '.v-btn');
      expect(isZeroTracking(button.letterSpacing)).toBe(true);

      const typography = await readComputed(
        page,
        '.text-title-medium, .text-title-small, .v-chip',
      );
      expect(isZeroTracking(typography.letterSpacing)).toBe(true);

      await page.screenshot({ path: path.join(artifactDir, `${theme}-dashboard.png`) });

      // Table pages: zebra stripes are gone, so every row starts from the same
      // background; hover is the only row-level highlight.
      await page.goto('/table');
      const rows = page.locator('.ds-table-wrap:visible tbody tr');
      await expect(rows.first()).toBeVisible({ timeout: 20_000 });
      await expect.poll(() => rows.count()).toBeGreaterThan(1);
      await page.mouse.move(0, 0);
      const odd = await rowBackground(page, 0);
      const even = await rowBackground(page, 1);
      expect(even).toBe(odd);

      await page.screenshot({ path: path.join(artifactDir, `${theme}-table.png`) });

      fs.writeFileSync(
        path.join(artifactDir, `typography-${theme}.json`),
        JSON.stringify({ theme, app, button, table: { odd, even } }, null, 2),
      );
    });
  }
});

import { defineConfig, devices } from '@playwright/test';

const host = process.env.PLAYWRIGHT_BASE_URL || 'http://127.0.0.1:3030';

export default defineConfig({
  testDir: './tests/e2e',
  testMatch: /(table-detail-editing|help-dialogs)\.spec\.ts/,
  fullyParallel: false,
  workers: 1,
  timeout: 30_000,
  expect: {
    timeout: 8_000,
  },
  reporter: [['list']],
  use: {
    baseURL: host,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  webServer: {
    command: 'VITE_API_HOST=http://127.0.0.1:50601 yarn dev',
    url: host,
    reuseExistingServer: true,
    timeout: 120_000,
  },
  projects: [
    {
      name: 'chromium',
      use: {
        ...devices['Desktop Chrome'],
      },
    },
  ],
});

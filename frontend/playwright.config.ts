import { defineConfig, devices } from '@playwright/test'

const host = process.env.PLAYWRIGHT_BASE_URL || 'http://127.0.0.1:3030'
const authFile = 'tests/e2e/.auth/user.json'

export default defineConfig({
  testDir: './tests/e2e',
  fullyParallel: true,
  timeout: 30_000,
  expect: {
    timeout: 8_000
  },
  retries: process.env.CI ? 1 : 0,
  reporter: [['html', { open: 'never' }], ['list']],
  use: {
    baseURL: host,
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure'
  },
  webServer: {
    command: 'yarn dev',
    url: host,
    reuseExistingServer: true,
    timeout: 120_000
  },
  projects: [
    {
      name: 'setup',
      testMatch: /.*\.setup\.ts/
    },
    {
      name: 'chromium',
      use: {
        ...devices['Desktop Chrome'],
        storageState: authFile
      },
      dependencies: ['setup'],
      testIgnore: /.*\.setup\.ts/
    }
  ]
})

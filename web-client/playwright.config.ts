import { defineConfig, devices } from '@playwright/test';

/**
 * End-to-end smoke test against a RUNNING app (it does not start one).
 *
 *   Local:  make up && make e2e                      (http://localhost:8088, no login)
 *   Cloud:  E2E_BASE_URL=https://<id>.cloudfront.net \
 *           E2E_EMAIL=<confirmed user> E2E_PASSWORD=<password> make e2e
 *
 * Locally the test writes to the database as the shared `dev-user`, so it adds
 * a couple of diary rows and water entries to "today".
 */
export default defineConfig({
  testDir: './e2e',
  timeout: 60_000,
  retries: 0,
  // Every project uses the same account: run them one at a time so one
  // project's saves can't race another's assertions.
  workers: 1,
  fullyParallel: false,
  reporter: 'list',
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:8088',
    screenshot: 'only-on-failure',
    trace: 'retain-on-failure',
  },
  projects: [
    { name: 'desktop', use: { ...devices['Desktop Chrome'], viewport: { width: 1360, height: 900 } } },
    { name: 'phone', use: { ...devices['Pixel 7'] } },
  ],
});

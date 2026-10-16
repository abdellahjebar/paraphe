import { defineConfig } from '@playwright/test'

/**
 * End-to-end tests: a real browser against the real backend and a real PostgreSQL.
 *
 * Nothing is started here. The backend and the front end must be running (see docs/ops/local-setup.md), the
 * backend with a short session time-out so the expiry can be seen: SESSION_IDLE_TIMEOUT=90s.
 *
 * The browser is the Microsoft Edge that is already on the machine (`channel: 'msedge'`); CI installs Chromium.
 */
export default defineConfig({
  testDir: 'e2e',
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: [['list']],
  timeout: 60_000,
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:5173',
    channel: process.env.E2E_CHANNEL ?? 'msedge',
    locale: 'fr-FR',
    trace: 'retain-on-failure',
  },
})

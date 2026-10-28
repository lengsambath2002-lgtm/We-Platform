import { defineConfig, devices } from '@playwright/test';

/**
 * End-to-end journeys against a running API (port 8280, its own database) and web app (port 3300).
 * Start both first: `./gradlew bootRun` in apps/api and `pnpm dev` here. Each test signs up fresh users.
 */
export default defineConfig({
  testDir: './e2e',
  timeout: 120_000, // the dev server compiles each page on first visit
  fullyParallel: false,
  // Two browsers at a time: a dev server compiles pages on demand and stalls under more.
  workers: 2,
  reporter: [['list']],
  use: { baseURL: process.env.WEB_URL ?? 'http://localhost:3300', trace: 'retain-on-failure' },
  projects: [
    { name: 'desktop', use: { ...devices['Desktop Chrome'] } },
    { name: 'phone', use: { ...devices['Pixel 7'] } },
  ],
});

import { defineConfig, devices } from "@playwright/test";

// E2E 콘솔 서버는 개발 백엔드(8080)와 분리된 E2E 백엔드(기본 18080)를 바라본다.
export default defineConfig({
  testDir: "e2e",
  forbidOnly: true,
  retries: 0,
  workers: 1,
  fullyParallel: false,
  reporter: "list",
  use: {
    baseURL: "http://127.0.0.1:3000",
  },
  projects: [
    {
      name: "chromium",
      use: { ...devices["Desktop Chrome"] },
    },
  ],
  webServer: {
    command: "npm run build && npm run start",
    url: "http://127.0.0.1:3000",
    reuseExistingServer: false,
    timeout: 180000,
    env: {
      BACKEND_BASE_URL: process.env.E2E_BACKEND_BASE_URL ?? "http://127.0.0.1:18080",
    },
  },
});

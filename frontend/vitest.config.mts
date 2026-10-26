import react from "@vitejs/plugin-react";
import tsconfigPaths from "vite-tsconfig-paths";
import { defineConfig } from "vitest/config";

export default defineConfig({
  plugins: [tsconfigPaths(), react()],
  test: {
    allowOnly: false,
    passWithNoTests: false,
    projects: [
      {
        test: {
          name: "unit",
          environment: "node",
          include: ["tests/unit/**/*.test.ts"],
          allowOnly: false,
        },
      },
      {
        test: {
          name: "proxy",
          environment: "node",
          include: ["tests/proxy/**/*.test.ts"],
          allowOnly: false,
        },
      },
      {
        test: {
          name: "component",
          environment: "jsdom",
          include: ["tests/component/**/*.test.tsx"],
          setupFiles: ["tests/setup/component.ts"],
          allowOnly: false,
        },
      },
    ],
  },
});

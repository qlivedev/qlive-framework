import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";
import { fileURLToPath } from "node:url";
import trackUsage from "./plugins/track-usage-vite-plugin";

const rootDir = fileURLToPath(new URL("../..", import.meta.url));
const frontendSrcDir = fileURLToPath(new URL("./src/", import.meta.url));

// TODO: placeholder tracked-function config - replace with real functions to
// analyze once the project has established analysis targets. Demonstrates
// tracking createFrameworkConfig(...) calls (statically-evaluable object-literal
// args) - e.g. the call in src/framework-wiring/App.tsx.
const trackedFunctions = {
  frameworkConfig: { module: "@quinscape/qlive-ts", fn: "createFrameworkConfig" },
};

export default defineConfig({
  plugins: [react(), trackUsage({ trackedFunctions, sourceRoot: frontendSrcDir, debug: false })],
  build: {
    outDir: "dist",
  },
  server: {
    // qlive-ts is pnpm-symlinked in from outside this package's directory;
    // let Vite serve straight from its source so edits show up without a rebuild.
    fs: {
      allow: [rootDir],
    },
    proxy: {
      "/api": {
        target: "http://localhost:8080",
        changeOrigin: true,
      },
    },
  },
  test: {
    environment: "jsdom",
  },
});

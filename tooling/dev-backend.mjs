#!/usr/bin/env node
// Launches the already-built qlive-test jar directly, skipping Maven entirely.
// Kept snappy for the frontend inner loop: rebuilding via Maven on every
// `pnpm dev` forces jOOQ DB codegen and a full pnpm frontend build even though
// Vite (started alongside this) is what actually serves the frontend.
import { existsSync, readdirSync } from "node:fs";
import { spawn } from "node:child_process";
import { join, dirname } from "node:path";
import { fileURLToPath } from "node:url";

const rootDir = join(dirname(fileURLToPath(import.meta.url)), "..");
const targetDir = join(rootDir, "qlive-test", "target");

const jar = existsSync(targetDir)
    ? readdirSync(targetDir)
        .filter((name) => /^qlive-test-.*\.jar$/.test(name) && !name.endsWith(".original"))
        .sort()[0]
    : undefined;

if (!jar) {
    console.error("No backend jar found at qlive-test/target/. Run 'pnpm build' once, then 'pnpm dev' again.");
    process.exit(1);
}

const child = spawn("java", ["-jar", join(targetDir, jar)], { stdio: "inherit" });

for (const signal of ["SIGINT", "SIGTERM"]) {
    process.on(signal, () => child.kill(signal));
}

child.on("exit", (code, signal) => {
    process.exit(code ?? (signal ? 1 : 0));
});

import {defineConfig} from "vitest/config";

// The old package had no tests (its "test" script was a no-op stub), so the
// suite here starts from FilterDSL.test.ts. passWithNoTests is kept so adding
// a package without tests does not fail `pnpm -r test`.
export default defineConfig({
    test: {
        passWithNoTests: true,
    },
});

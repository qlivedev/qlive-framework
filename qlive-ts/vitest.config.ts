import {defineConfig} from "vitest/config";

// No test files ported from qlive-js (the old package had no tests either -
// its "test" script was a no-op stub). passWithNoTests keeps `pnpm -r test`
// green instead of failing on an empty suite.
export default defineConfig({
    test: {
        passWithNoTests: true,
    },
});

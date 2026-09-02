import {defineConfig} from "vitest/config";

// Tests live in test/, mirroring the src/ tree they cover, so that src/ stays
// exactly the set of files that ship. The include pattern is spelled out rather
// than left at the default: a stray *.test.ts under src/ should be ignored (and
// noticed) instead of quietly running from the wrong place.
//
// The old package had no tests (its "test" script was a no-op stub), so the
// suite here starts from FilterDSL.test.ts. passWithNoTests is kept so adding
// a package without tests does not fail `pnpm -r test`.
export default defineConfig({
    test: {
        include: ["test/**/*.test.{ts,tsx}"],
        passWithNoTests: true,
    },
});

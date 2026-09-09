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
        server: {
            deps: {
                // The track-usage plugin loads the application's codegen with a plain dynamic import,
                // and in a dev server that is Node loading it, not Vite. Left to Vitest's module graph
                // it resolves graphql to its ESM build there and to its CJS build inside the codegen's
                // own externalized dependencies -- two realms, and every type check across them fails.
                external: [/qlive-codegen/],
            },
        },
    },
});

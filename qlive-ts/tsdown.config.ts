import {defineConfig} from "tsdown";

/*
 * Two builds, because the package has two audiences. The runtime entry is browser code an application
 * bundles; the "./vite" entry is Node code its vite.config.ts loads, and it pulls in babel, which has no
 * business being resolved against browser conditions. Sharing one config would mean picking a platform
 * that is wrong for one of them.
 *
 * Cleaning is safe to leave on in both: tsdown wipes the shared outDir once for the whole config array,
 * not once per config.
 */
export default defineConfig([
    {
        entry: {index: "src/index.ts", filter: "src/filter.ts"},
        format: "esm",
        platform: "browser",
        dts: true,
        outDir: "dist",
        clean: true,

        // Plain CSS needs no compilation, but it has to land in dist so the
        // published package has a single output surface. It is deliberately not
        // imported from src/index.ts: routing it through the JS would make Vite
        // auto-inject it in dev (where the test app aliases to source) while
        // consumers of the built package would have to import it by hand - a
        // dev/prod divergence. Consumers import it explicitly via the
        // "./styles.css" export instead, which also keeps load order theirs.
        copy: [{from: "src/styles/*.css", to: "dist", flatten: true}],
    },
    {
        entry: {vite: "src/vite/index.ts"},
        format: "esm",
        platform: "node",
        dts: true,
        outDir: "dist",
        clean: true,

        // A node-platform build defaults to .mjs/.d.mts. The package is type: "module", so .js already
        // means ESM here, and the exports map reads better with both entries named the same way.
        outExtensions: () => ({js: ".js", dts: ".d.ts"}),
    },
]);

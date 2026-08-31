import {defineConfig} from "tsdown";

export default defineConfig({
    entry: ["src/index.ts"],
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
});

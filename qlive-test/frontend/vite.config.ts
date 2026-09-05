import {defineConfig} from "vitest/config";
import react from "@vitejs/plugin-react";
import {fileURLToPath} from "node:url";
import trackUsage, {TrackedFunctionSpec} from "./plugins/track-usage-vite-plugin";

const rootDir = fileURLToPath(new URL("../..", import.meta.url));
const frontendSrcDir = fileURLToPath(new URL("./src/", import.meta.url));
const qliveTsDir = fileURLToPath(new URL("../../qlive-ts/", import.meta.url));

// qlive-ts is built by tsdown, and its package.json points at dist/ - which is
// what consumers get and what `vite build` therefore resolves. In dev that
// would mean editing qlive-ts/src does nothing until something rebuilds dist,
// losing the instant inner loop, so serve mode is aliased straight to source.
// Vitest runs in serve mode too, so tests exercise source as well.
//
// Consequence worth knowing: only `vite build` touches dist, so packaging bugs
// (bad exports map, missing emitted file) surface at build time, not in dev.
// `pnpm build` runs that build, so CI still catches them.
const devAliases = {
    "@quinscape/qlive-ts/styles.css": qliveTsDir + "src/styles/qlive.css",
    "@quinscape/qlive-ts": qliveTsDir + "src/index.ts",
};

const trackedFunctions : { [name: string]: TrackedFunctionSpec } = {
    i18n: {
        module: "@quinscape/qlive-ts", fn: "i18n",
        varArgs: true
    },
    useInjection: {
        module: "@quinscape/qlive-ts", fn: "useInjection", allowIdentifier: true
    },
    GraphQLQuery: {
        module: "@quinscape/qlive-ts", fn: "GraphQLQuery"
    },
};

const backendOrigin = "http://localhost:8080";

export default defineConfig(({command}) => ({
    // ViteIndexController serves the built index.html under /app/**, so the dev server serves the application
    // from the same prefix. QLive's router derives its routes from this, which is why both sides agree on
    // where a view lives.
    base: "/app/",
    resolve: {
        alias: command === "serve" ? devAliases : {},
    },
    plugins: [
        trackUsage({
            trackedFunctions,
            sourceRoot: frontendSrcDir,
            debug: false,
            pushUrl: `${backendOrigin}/_dev/track-usage`,
        }),
        react(),
    ],
    build: {
        outDir: "dist",
    },
    server: {
        // qlive-ts is pnpm-symlinked in from outside this package's directory;
        // let Vite serve straight from its source so edits show up without a rebuild.
        fs: {
            allow: [rootDir],
        },
        // Both prefixes the browser asks the backend for: /api/** (bootstrap, update) and
        // /graphql. Keeping them same-origin is what lets the frontend send its session cookie
        // and CSRF header exactly the way it does in production - no CORS, no credentialed
        // cross-origin code path that only ever runs in dev.
        proxy: {
            "/api": {
                target: backendOrigin,
                changeOrigin: true,
            },
            "/graphql": {
                target: backendOrigin,
                changeOrigin: true,
            },
        },
    },
    test: {
        environment: "jsdom",
        // Tests live in test/, mirroring the src/ tree they cover, so src/ holds
        // only application code. Spelling the pattern out means a stray
        // *.test.ts under src/ is ignored rather than quietly running.
        include: ["test/**/*.test.{ts,tsx}"],
    },
}));

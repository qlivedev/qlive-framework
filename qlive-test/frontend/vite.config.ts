import {defineConfig} from "vitest/config";
import react from "@vitejs/plugin-react";
import {fileURLToPath} from "node:url";
import trackUsage from "./plugins/track-usage-vite-plugin";

const rootDir = fileURLToPath(new URL("../..", import.meta.url));
const frontendSrcDir = fileURLToPath(new URL("./src/", import.meta.url));

const trackedFunctions = {
    i18n: {
        module: "@quinscape/qlive-ts", fn: "i18n",
        varArgs: true
    },
    inject: {
        module: "@quinscape/qlive-ts", fn: "inject"
    },
    GraphQLQuery: {
        module: "@quinscape/qlive-ts", fn: "GraphQLQuery"
    },
};

const backendOrigin = "http://localhost:8080";

export default defineConfig({
    plugins: [
        react(),
        trackUsage({
            trackedFunctions,
            sourceRoot: frontendSrcDir,
            debug: false,
            pushUrl: `${backendOrigin}/_dev/track-usage`,
        }),
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
        proxy: {
            "/api": {
                target: backendOrigin,
                changeOrigin: true,
            },
        },
    },
    test: {
        environment: "jsdom",
    },
});

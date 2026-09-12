import {defineConfig} from "vitest/config";
import react from "@vitejs/plugin-react";
import {fileURLToPath} from "node:url";
import {trackUsage} from "@quinscape/qlive-ts/vite";

const rootDir = fileURLToPath(new URL("../..", import.meta.url));
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
//
// The "@quinscape/qlive-ts/vite" import above is the exception: this file is loaded by Node
// before any of these aliases exist, so it always comes from dist. Editing the plugin means
// rebuilding qlive-ts, which is why the root `dev` script does that first.

// Subpath entries first: a string alias matches on prefix, so the bare one would swallow them.
const devAliases = {
    "@quinscape/qlive-ts/styles.css": qliveTsDir + "src/styles/qlive.css",
    "@quinscape/qlive-ts/filter": qliveTsDir + "src/filter.ts",
    "@quinscape/qlive-ts": qliveTsDir + "src/index.ts",
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
        // Which calls get recorded, and the endpoint the snapshots go to in dev, are QLive's own -- the
        // backend looks them up by name. All this application has to say is where its backend is.
        trackUsage({
            backendOrigin,
        }),
        react(),
    ],
    build: {
        outDir: "dist",
        rollupOptions: {
            // The application has a second entry point: the login page spring security serves under
            // /login, with its own HTML and its own reduced startup. Naming it here is what puts
            // login.html into the build, where VitePageRenderer picks it up the way it picks up
            // index.html.
            input: {
                main: fileURLToPath(new URL("./index.html", import.meta.url)),
                login: fileURLToPath(new URL("./login.html", import.meta.url)),
            },
        },
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
            // The push websocket. `ws: true` is what makes the dev server proxy the upgrade instead of
            // answering the GET itself -- without it the handshake gets a 404 from Vite and the client
            // reconnects into it forever.
            //
            // No changeOrigin, and that one is load-bearing rather than incidental: Spring registers an
            // OriginHandshakeInterceptor that accepts same-origin handshakes only, comparing the browser's
            // Origin header against the host the request arrived at. The browser sends the dev server's
            // origin, so rewriting Host to the backend's is exactly what would make the two differ, and
            // every handshake would be answered with a 403.
            "/push": {
                target: backendOrigin,
                ws: true,
            },
            // Only the form POST belongs to the backend. The page itself is served by the dev server
            // like every other page, so that it gets the same module graph and HMR -- bypass hands the
            // GET to the login entry point instead of proxying it. The path has to carry `base`: a bare
            // "/login.html" is outside it and gets answered with a redirect to /app/ instead.
            //
            // No changeOrigin either: the login redirects carry an absolute Location built from the Host
            // header, so rewriting Host to the backend would bounce the browser off the dev server and
            // onto :8080 the moment a login succeeds.
            "/login": {
                target: backendOrigin,
                bypass: (req) => req.method === "GET" ? "/app/login.html" : undefined,
            },
            // Logout has no page of its own, only the POST Spring Security answers with a redirect back
            // to /login. Same reasoning as above rules out changeOrigin: an unchanged Host is what keeps
            // that redirect's Location pointed at the dev server instead of :8080.
            "/logout": {
                target: backendOrigin,
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

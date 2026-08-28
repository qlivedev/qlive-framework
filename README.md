# qlive-framework (Repo A — developer monorepo)

One repo for framework maintainers/contributors. Buildable with a single
command; nothing is published to iterate locally.

```
backend-lib/              Java library, version 1.0.0-SNAPSHOT
frontend-lib/              @qlive/frontend-lib, linked by pnpm workspace (no build step, Vite reads its TS source directly)
fullstack-app/             Spring Boot app, depends on backend-lib as a SNAPSHOT
  frontend/                 Vite + React app, depends on frontend-lib via "workspace:*"
```

`fullstack-app` is both the framework's integration/regression test target
and the source the end-user template gets extracted from later, so it's
split into two layers that must stay decoupled:

- `framework-wiring` (`fullstack-app/frontend/src/framework-wiring`, and
  `fullstack-app/src/main/java/...`) — minimal bootstrap/config/provider
  setup. Builds and runs standalone as a "hello world" of the framework.
- `test-scenarios` (`fullstack-app/frontend/src/test-scenarios`, and
  `fullstack-app/src/test/java/.../scenarios`) — edge cases, fixtures,
  multiple auth strategies, deliberately broken states. Exists purely to
  stress-test the framework and must never be imported by `framework-wiring`.

## One-command entry points

npm/pnpm is the front door; Maven runs underneath and you shouldn't need to
call `mvnw` directly.

```bash
pnpm install   # symlinks frontend-lib into fullstack-app/frontend/node_modules
pnpm build     # ./mvnw install — builds backend-lib, then frontend (via frontend-maven-plugin
               # + pnpm workspace), then fullstack-app, copying the built frontend into
               # fullstack-app/target/classes/static
pnpm test      # ./mvnw test (Java) + pnpm -r test (TS, both frontend-lib and the frontend app)
pnpm dev       # backend (spring-boot:run on :8080) + frontend (vite on :5173, proxying /api
               # to :8080) together, both with hot reload
```

While `pnpm dev` is running, editing `frontend-lib/src` reflects immediately
in the browser — no rebuild step, because Vite transpiles the linked
workspace source directly (`frontend-lib`'s `package.json` points `main` at
its `.ts` source, not a `dist/`).

## Toolchain

- Java 17 (`mvnw`/`mvnw.cmd` pin Maven 3.9.12 itself). `JAVA_HOME` must point
  at a full JDK, not a JRE-only install — a JRE has no `javac`, and the
  compiler plugin will fail with a confusing "release version 17 not
  supported" instead of a missing-compiler error.
- Node 22.23.2 (`.nvmrc`; also pinned in `fullstack-app/pom.xml`'s
  `frontend-maven-plugin` config so `mvn install` uses the same Node
  regardless of what's on `PATH`)
- pnpm 9.15.0 (`packageManager` field in the root `package.json`; enable via
  `corepack enable`)
- `.devcontainer/devcontainer.json` pins all three together, optional

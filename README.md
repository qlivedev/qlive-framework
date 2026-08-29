# qlive-framework (Repo A — developer monorepo)

One repo for framework maintainers/contributors. Buildable with a single
command; nothing is published to iterate locally.

```
qlive/                     Java library, version 1.0.0-SNAPSHOT
qlive-ts/                  @quinscape/qlive-ts, linked by pnpm workspace (no build step, Vite reads its TS source directly)
qlive-test/                Spring Boot app, depends on qlive as a SNAPSHOT
  frontend/                 Vite + React app, depends on qlive-ts via "workspace:*"
```

`qlive-test` is both the framework's integration/regression test target
and the source the end-user template gets extracted from later.

On the frontend, it's split into two layers that must stay decoupled:

- `framework-wiring` (`qlive-test/frontend/src/framework-wiring`) —
  minimal bootstrap/config/provider setup. Builds and runs standalone as
  a "hello world" of the framework.
- `test-scenarios` (`qlive-test/frontend/src/test-scenarios`) — edge
  cases, fixtures, multiple auth strategies, deliberately broken states.
  Exists purely to stress-test the framework and must never be imported
  by `framework-wiring`.

The Java side (`qlive-test/src/main/java/com/dataciders/qlivetest/...`)
mirrors the demo app migrated from the framework's previous incarnation:
GraphQL/DomainQL config, jOOQ-backed auth and domain model, and query
logic exercising `qlive`. It isn't yet split along the same
wiring/scenarios lines as the frontend.

## One-command entry points

npm/pnpm is the front door; Maven runs underneath and you shouldn't need to
call `mvnw` directly.

```bash
pnpm install   # symlinks qlive-ts into qlive-test/frontend/node_modules
pnpm build     # ./mvnw install — builds qlive, then frontend (via frontend-maven-plugin
               # + pnpm workspace), then qlive-test, copying the built frontend into
               # qlive-test/target/classes/static
pnpm test      # ./mvnw test (Java) + pnpm -r test (TS, both qlive-ts and the frontend app)
pnpm dev       # backend (spring-boot:run on :8080) + frontend (vite on :5173, proxying /api
               # to :8080) together, both with hot reload
```

While `pnpm dev` is running, editing `qlive-ts/src` reflects immediately
in the browser — no rebuild step, because Vite transpiles the linked
workspace source directly (`qlive-ts`'s `package.json` points `main` at
its `.ts` source, not a `dist/`).

## Toolchain

- Java 25 (`mvnw`/`mvnw.cmd` pin Maven 3.9.12 itself). `JAVA_HOME` must point
  at a full JDK, not a JRE-only install — a JRE has no `javac`, and the
  compiler plugin will fail with a confusing "release version 25 not
  supported" instead of a missing-compiler error.
- Node 22.23.2 (`.nvmrc`; also pinned in `qlive-test/pom.xml`'s
  `frontend-maven-plugin` config so `mvn install` uses the same Node
  regardless of what's on `PATH`)
- pnpm 9.15.0 (`packageManager` field in the root `package.json`; enable via
  `corepack enable`)
- `.devcontainer/devcontainer.json` pins all three together, optional

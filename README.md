# qlive-framework (Repo A — developer monorepo)

One repo for framework maintainers/contributors. Buildable with a single
command; nothing is published to iterate locally.

```
qlive/                     Java library, version 1.0.0-SNAPSHOT
qlive-ts/                  @quinscape/qlive-ts, linked by pnpm workspace (built with tsdown; dev aliases to its TS source)
qlive-codegen/             @quinscape/qlive-codegen, the generate-ts CLI (schema.graphql -> types.d.ts)
qlive-test/                Spring Boot app, depends on qlive as a SNAPSHOT
  frontend/                 Vite + React app, depends on qlive-ts via "workspace:*"
qlive-doc/                 framework-user documentation (plain .md for now)
```

`qlive-test` is both the framework's integration/regression test target
and the source the end-user template gets extracted from later.

## Documentation

- **[`qlive-doc/`](qlive-doc/README.md)** — documentation for the framework
  *user*, i.e. someone building an application on QLive. Markdown pages built
  with Astro Starlight and published to
  <https://quinscape.github.io/qlive-framework/>. Deliberately outside both
  the Maven reactor and the pnpm workspace, so its toolchain installs only
  when someone builds the docs — `pnpm docs:dev`, `pnpm docs:build`.
- `docs/` — internal development documentation for maintainers.
  `docs/design/` is for ideas not yet realized.

The second half of the framework-user documentation — what gets generated
into a new application alongside the template — waits for the templating
command.

The frontend (`qlive-test/frontend/src/...`) mirrors the demo app
migrated from the framework's previous incarnation: a Home page using
`FilterDSL`/`useInjection`, a typed `GraphQLQuery` (`Q_Foo`), and the
`types.d.ts`. It isn't split along wiring/scenarios lines.

The Java side (`qlive-test/src/main/java/com/dataciders/qlivetest/...`)
mirrors the demo app migrated from the framework's previous incarnation:
GraphQL/DomainQL config, jOOQ-backed auth and domain model, and query
logic exercising `qlive`. Neither side is split along wiring/scenarios
lines.

The codegen CLI is a separate package on purpose. It needs `graphql` and
`@graphql-tools/*` - about 5 MB that the `qlive-ts` runtime never imports -
so applications that do not run codegen should not have to carry them.
Add it as a devDependency where you need it, as `qlive-test/frontend`
does, and run it via `pnpm generate`.

## One-command entry points

npm/pnpm is the front door; Maven runs underneath and you shouldn't need to
call `mvnw` directly.

```bash
pnpm install   # symlinks qlive-ts into qlive-test/frontend/node_modules
pnpm build     # ./mvnw install — builds qlive, then qlive-ts (tsdown), then the frontend
               # (via frontend-maven-plugin + pnpm workspace), then qlive-test, copying
               # the built frontend into qlive-test/target/classes/static
pnpm test      # ./mvnw test (Java) + pnpm -r test (TS: qlive-ts, qlive-codegen, the frontend app)
pnpm dev       # backend (spring-boot:run on :8080) + frontend (vite on :5173, proxying /api
               # to :8080) together, both with hot reload
pnpm dev-ts    # expects java backend to be started, frontend (vite on :5173, proxying /api
               # to :8080) together, both with hot reload 
```

While `pnpm dev` is running, editing `qlive-ts/src` reflects immediately
in the browser — no rebuild step. `qlive-ts`'s `package.json` points at
`dist/`, which is what consumers get and what `vite build` resolves, but
`qlive-test/frontend/vite.config.ts` aliases the package to its `.ts`
source in serve mode, so Vite transpiles the linked workspace source
directly. Vitest runs in serve mode too, so tests exercise source as well.

The trade: only `vite build` touches `dist/`, so packaging mistakes (a bad
`exports` entry, a file that never got emitted) surface at build time
rather than in the inner loop. `pnpm build` runs that build, so they are
still caught before anything ships.

QLive's stylesheet is shipped as a separate artifact, imported explicitly
by the application (`import "@quinscape/qlive-ts/styles.css"`) rather than
pulled in by the JS, so the app controls where it lands in the cascade.
See `docs/styling.md`.

## Toolchain

- Java 25 (`mvnw`/`mvnw.cmd` pin Maven 3.9.12 itself). `JAVA_HOME` must point
  at a full JDK, not a JRE-only install — a JRE has no `javac`, and the
  compiler plugin will fail with a confusing "release version 25 not
  supported" instead of a missing-compiler error.
- Node 22.23.2 (`.nvmrc`; also pinned in `qlive-test/pom.xml`'s
  `frontend-maven-plugin` config so `mvn install` uses the same Node
  regardless of what's on `PATH`)
- pnpm 11.24.0 (`packageManager` field in the root `package.json`; enable via
  `corepack enable`)
- `.devcontainer/devcontainer.json` pins all three together, optional

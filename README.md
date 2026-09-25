<p>
    <picture>
      <source media="(prefers-color-scheme: dark)" srcset="./docs/qlive-logo-dark.svg">
      <source media="(prefers-color-scheme: light)" srcset="./docs/qlive-logo-light.svg">
      <img alt="qlive framework" src="./docs/qlive-logo-dark.svg">
    </picture>
</p>

Monorepo for the QLive fullstack framework.

[User Documentation](https://qlivedev.github.io/qlive-framework/)

---

## About this repo

The monorepo collects the parts that make up the core fullstack framework on both
the Java/server side and the TypeScript/client side.

```
qlive/              QLive Java library
qlive-ts/           QLive TypeScript library
qlive-codegen/      CLI tools to generate Typescript types from GraphQL
qlive-test/         QLive Test app (based on Spring Boot)
  frontend/         QLive Test app frontend: Vite + React app
qlive-doc/          Source for the Github pages documentation 
qlive-api/          QLive API for applications 
qlive-graphql/      QLive jOOQ/GraphQL engine 
```

`qlive-test` is both the framework's integration/regression test target
and the source the end-user template gets extracted from later.

## One-command entry points

npm/pnpm is the front door; Maven runs underneath and you shouldn't need to
call `mvnw` directly.

```bash
pnpm install   # symlinks qlive-ts into qlive-test/frontend/node_modules
pnpm build     # ./mvnw install — builds qlive, then qlive-ts (tsdown), then the frontend
               # (via frontend-maven-plugin + pnpm workspace), then qlive-test, copying
               # the built frontend into qlive-test/target/classes/static
pnpm test      # ./mvnw test (Java) + pnpm -r test (TS: qlive-ts, qlive-codegen, the frontend app)
pnpm dev       # the built backend jar on :8080 + frontend (vite on :5173, proxying /api,
               # /graphql and /push to :8080)
pnpm dev-ts    # the frontend half alone, for when the backend already runs somewhere else
```
### Github pages documentation

```bash
pnpm docs:dev         # symlinks qlive-ts into qlive-test/frontend/node_modules
pnpm docs:build       # ./mvnw install — builds qlive, then qlive-ts (tsdown), then the frontend
pnpm docs:api         # generate API part of the documentation from jsdoc
pnpm docs:api:check   # check API documentation status
pnpm docs:preview     # run docs preview 
```

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

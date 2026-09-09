---
title: Example Application layout
description: Folder structure, and the constants both halves have to agree on.
sidebar:
  order: 2
---

An application is one Maven module with a Vite frontend inside it.
`qlive-test` is the reference; the template will be extracted from it.

The purpose of this is to get you up to speed quickly to develop with QLive. If you are an experienced Spring
developer, you might have your own way of doing things and so you can just use `qlive-test` to see which components
you need to copy into your Spring configuration.

```
my-app/
  pom.xml                        Spring Boot app, depends on qlive
  src/main/java/...              logic beans, config, hand-written types
    domain/                      code generator output -- do not hand-edit
    model/                       hand-written model types
    runtime/                     config, controllers, logic
  src/main/resources/
  frontend/
    package.json
    vite.config.ts
    index.html                   the application entry point
    login.html                   a second entry point, if you want one
    schema.graphql               input for the type codegen
    src/
      main.tsx                   entry module: calls startup()
      types.d.ts                 generated from schema.graphql
      app/                       views -- and only views
        Home.tsx
        sub/View.tsx
      component/                 everything that is not a view
    test/                        mirrors src/
```

The `domain` / `model` / `runtime` split on the Java side is a convention
worth keeping: the code generator owns `domain` outright and deletes
anything in it that it did not write, so a hand-written type cannot live
there even if you wanted it to.

## The constants both halves agree on

Four values have to line up. Three of them are compile-time constants
rather than configuration, because the request mappings that serve them are
annotations.

| What | Frontend | Backend |
|---|---|---|
| Where the app is mounted | `base: "/app/"` in `vite.config.ts` | `QLivePaths.APP_BASE` |
| Where views live | the `import.meta.glob()` pattern, `./app/**/*.tsx` | `QLivePaths.VIEW_ROOT`, `./app/` |
| Where tracked modules are rooted | the track-usage plugin's `sourceRoot`, `src/` by default | `qlive.dev.ts-source` (dev only) |
| Type declarations | `src/types.d.ts` | `schema.graphql` it was generated from |

`/app/sub/view` is served by the module `./app/sub/View` because of the
first two rows and nothing else. Changing your Vite `base` means changing
`APP_BASE` with it.

`sourceRoot` defaults to `src/` below Vite's `root`, and you only pass it
if your code lives somewhere else. Pass it as an absolute path: the plugin
derives the babel `root` and the relative source root the underlying
babel-plugin-track-usage wants from it. That plugin computes a module id
relative to the babel `root` and then strips its own `sourceRoot` from the
result, so handing *it* an absolute path silently strips nothing and every
module id comes out wrong.

## Frontend dependencies

```jsonc
{
  "dependencies": {
    "@quinscape/qlive-ts": "...",
    "react": "^18.3.1",
    "react-dom": "^18.3.1"
  },
  "devDependencies": {
    "@quinscape/qlive-codegen": "...",   // the generate-ts CLI
    // ... vite, typescript, vitest
  }
}
```

`@quinscape/qlive-codegen` is a separate package on purpose: it pulls in
`graphql` and `@graphql-tools/*`, about 5 MB the `qlive-ts` runtime never
imports. Applications that do not run codegen should not carry it.

Do not add `temporal-polyfill` yourself. `qlive-ts` re-exports `Temporal`
from its own copy, and instants from a second copy do not typecheck against
instants from the first.

## Vite config

The two pieces QLive needs:

```ts
import {trackUsage} from "@quinscape/qlive-ts/vite";

const backendOrigin = "http://localhost:8080";

export default defineConfig(({command}) => ({
    base: "/app/",
    plugins: [
        trackUsage({backendOrigin}),
        react(),
    ],
    // ...
}));
```

`@quinscape/qlive-ts/vite` is the package's build-time entry point. It is
Node code your Vite config loads, kept apart from the runtime entry so that
babel never enters your application bundle's dependency graph.

`trackUsage()` records the calls the backend's static analysis reads. Which
calls those are is QLive's own business -- the server looks each one up by
a symbolic name it has compiled in -- so the plugin brings them along and an
application configures none of it. `backendOrigin` is all it needs: the dev
endpoint the analysis is POSTed to belongs to the framework as well.

An application that wants its *own* calls analysed adds them under
`trackedFunctions`, which is merged with QLive's:

```ts
trackUsage({
    backendOrigin,
    trackedFunctions: {
        track: {module: "./service/analytics", fn: "track"},
    },
});
```

The key is the symbolic name the analysis records the call under, and by
convention it is the function's own name. `allowIdentifier` on a spec is
what lets the analysis record *which identifier* was passed rather than a
static value; QLive uses it for `useInjection`. The full default set is
exported as `QLIVE_TRACKED_FUNCTIONS` if you want to look at it.

One of QLive's own keys is refused rather than overridden, and the dev
server or build stops with the name in the message. The server reads those
calls back out of the analysis under exactly those names, so a replacement
cannot be made to work from the application side -- everything between the
call and that read belongs to the framework. Nothing stops you tracking the
same function under a key of your own: the key only says what the analysis
files the call under.

Proxy `/api/**` and `/graphql` to the backend from the dev server rather
than pointing the frontend at another origin: keeping them same-origin is
what lets the session cookie and the CSRF header work in dev exactly as
they do in production.

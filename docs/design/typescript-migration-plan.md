# TypeScript / frontend migration plan

## Context

This mirrors the Java migration already done for `qlive` and `qlive-test`
(see git history: "Migrate qlive and qlive-test Java sources from old
repos"). The old sources live in two sibling, unrelated-git-history repos:

- `/home/sven/ideaprojects/qlive-framework.old/qlive-js` — the framework's
  TypeScript client library, `@quinscape/qlive-js` (config/bootstrap,
  GraphQL query execution, the FilterDSL condition builder, a codegen tool
  that turns a GraphQL schema into `.d.ts` types)
- `/home/sven/ideaprojects/qlive-framework.old/qlive-test/src/main/ts` and
  root-level `schema.graphql`/`.graphqlconfig`/`test.graphql` — the demo
  app's frontend, built with rspack + yarn

The new skeleton (`/home/sven/ideaprojects/qlive-framework`) currently has
generated placeholder content on the TypeScript side too, same as the Java
side had before its migration:

- `qlive-ts/src/index.ts` — a `createFrameworkConfig`/`fetchGreeting`
  "hello world" (analogous to the old `qlive/backend/Greeting.java`)
- `qlive-test/frontend/src/framework-wiring/{App.tsx,main.tsx}` and
  `src/test-scenarios/{authStrategies.ts,greeting.stress.test.ts}` — a
  wiring/scenarios split with hello-world content and fixture-based tests

As with the Java migration, the user has decided to **favor the old
projects' content and structure over the generated scaffolding**: the
frontend's wiring/test-scenarios split gets abandoned too, in favor of a
flat structure mirroring the old `qlive-test/src/main/ts` layout. (This is
a deliberate, explicit repeat of the Java-side decision — the split had
more supporting build config behind it than the Java side did, but the
user chose consistency over preserving it.)

Goal: bring the real TypeScript sources over from both old repos into the
new skeleton, on top of the already-established Vite + pnpm + React 18
tooling (keep this — it's a strict upgrade over the old rspack + yarn
setup and there's no reason to revert it, same reasoning as keeping jar
packaging over WAR on the Java side).

## Decisions already made

1. **Structure**: abandon the `framework-wiring`/`test-scenarios` split for
   the frontend. Flatten `qlive-test/frontend/src` to mirror the old
   `src/main/ts` layout. `README.md` needs another correction pass (see
   below) to stop describing this split as real.
2. **Build tooling**: keep Vite + pnpm + React 18, `babel-plugin-track-usage`
   wired through `vite.config.ts` — do not revert to rspack + yarn. Old
   `qlive-js`'s own build tooling (`tsdown`, `rollup`, `esbuild`) is also
   dropped — the new `qlive-ts` package is source-referenced (no build
   step, `main`/`types` point straight at `src/index.ts`), same as it is
   today, so none of that bundler tooling is needed.
3. **Package naming**: no renaming problem here (unlike Java's
   `de.quinscape`/`com.dataciders` split) — `@quinscape/qlive-js` simply
   becomes `@quinscape/qlive-ts`, already done in the new skeleton. Only
   the *consumers'* import specifiers need updating when old `qlive-test`
   frontend files are ported over.

## qlive-ts package (framework core)

### Copy
Delete the placeholder `qlive-ts/src/index.ts` and `index.test.ts`
(hello-world scaffolding). Copy from
`/home/sven/ideaprojects/qlive-framework.old/qlive-js/src/**` to
`/home/sven/ideaprojects/qlive-framework/qlive-ts/src/**`:

- `index.ts` — barrel export (`QueryDocument`, `GraphQLQuery`, `inject`,
  `decompileFilter`, `startup`, `FilterDSL` namespace)
- `config.ts` — `QLiveConfig`/`init()`/default export `config()`, plus the
  `GenericTypeInfo`/`RelationInfo` domain-metadata types
- `inject.ts` — `inject<T>(query, params)` (currently returns a
  hardcoded stub result — pre-existing, not something this migration
  should "fix")
- `startup.ts` — DOM-data-driven config bootstrap (`init()` from a
  `#root-data` script tag's JSON) **plus** a webpack-specific dynamic
  import mechanism — needs adaptation, see below, not a verbatim copy
- `GraphQLQuery.ts` — the `GraphQLQuery<T>` class (register/access/execute)
- `GraphQL.ts` — generic GraphQL scalar wrapper types
- `FilterDSL.ts` — the condition/operation builder DSL (`field`, `value`,
  `values`, `and`/`or`/`not`, `condition`, `component`, `toJSON`,
  `isComputedValue`, `now`/`today`, etc.) — this is the TS-side mirror of
  the Java `qlive` module's `model/condition/*` + `runtime/scalar/FilterDSL`
- `QueryDocument.ts` — `QueryDocument<T>` class with `update()`
- `components/InjectionProvider.tsx` — a React context stub
  (`InjectionAPI.resolve()` is unimplemented — pre-existing, port as-is)
- `util/decompileFilter.ts` — pretty-prints a `FilterDSL` condition graph
  back to source-like text (mirrors the Java `FilterDSLDecompiler`)
- `util/graphql.ts` — the `fetch()`-based GraphQL transport function

Copy `tooling/generateTS.js` and `tooling/type-utils.js` to
`qlive-ts/tooling/`. This is the schema-to-TypeScript-types codegen tool
(GraphQL introspection → `.d.ts`), the TS-side equivalent of the Java
module's DomainQL typedocs exec-plugin. Keep the `bin` entry
(`"generate-ts": "tooling/generateTS.js"`) in `qlive-ts/package.json` so
pnpm exposes it to consumers via the workspace.

### package.json changes (`qlive-ts/package.json`)
Add:
- `dependencies`: `temporal-polyfill` (real runtime dependency —
  `FilterDSL.ts` checks `value instanceof Temporal.Instant`)
- `devDependencies` needed only for the codegen tool, not the library
  itself: `graphql`, `@graphql-tools/graphql-file-loader`,
  `@graphql-tools/load`
- `bin`: `{ "generate-ts": "tooling/generateTS.js" }`

Do **not** port: `tsdown`, `rollup`, `rollup-plugin-dts`,
`rollup-plugin-esbuild`, `esbuild` (build tooling for a bundled dist that
the new package doesn't produce), the `module`/`typings` fields pointing
at `dist/*` (keep the new skeleton's `main`/`types` → `src/index.ts`
convention instead).

**React version**: old `qlive-js` declares `peerDependencies: { react:
"^19.2.4" }`; the rest of the new repo (qlive-test/frontend) is pinned to
React 18.3.x. None of the ported code uses React 19-only APIs (the only
`.tsx` file, `InjectionProvider.tsx`, just calls `React.createContext` —
no JSX). Keep the new skeleton's existing React 18 peer dependency, don't
bump the whole repo to React 19 as a side effect of this migration.
  
## Issues to ignore
- `util/graphql.ts` referencing the undeclared identifiers `contextPath`,
  `csrfToken`, and `variables` is pre-existing, incomplete code in the old
  repo, unrelated to this migration. Port the file as-is. It will fail to
  typecheck if anything actually calls the transport function — that's
  expected and out of scope here; it gets fixed whenever someone next
  works on the GraphQL transport directly, not as part of this migration.

### Resolved: startup.ts's webpack-based auto-discovery
`startup.ts`'s `ImportMetaWebpackContext` type and its use of
`webpackCtx(name)` is a webpack/rspack-specific dynamic-import mechanism
(`import.meta.webpackContext`) with no Vite equivalent by that name — it
categorically cannot work unmodified under Vite.

Vite *does* have an equivalent capability, just a different-shaped API:
[`import.meta.glob()`](https://vite.dev/guide/features.html#glob-import).
It's statically analyzed at build time the same way `require.context`/
`import.meta.webpackContext` is, and returns a map of matched file paths
to import functions:
```ts
const modules = import.meta.glob("./app/**/*.{ts,tsx}");
// => { "./app/Home.tsx": () => import("./app/Home.tsx"), ... }
const mod = await modules[`./app/${name}.tsx`]();
```
You look a key up in the returned map instead of calling a context
function directly, but the capability (dynamically load one of N modules
matched by a glob, resolved at build time) is the same.

Resolution: **don't reimplement this now.** Comment out the
webpack-context-dependent parts of `startup.ts` in place — the
`ImportMetaWebpackContext` type and the `webpackCtx(...)` call/parameter
inside `startup()` — with a comment block recording what the mechanism
was for and pointing at `import.meta.glob()` as the intended replacement
when this gets picked back up. Comment out the corresponding `export {
startup } from "./startup"` line in `index.ts` too, so `qlive-ts` doesn't
advertise a non-functional export. Keep the DOM-data bootstrap logic
(`init(data)` reading `#root-data`) active and uncommented — it has
nothing to do with webpack and still works standalone. This matches the
old demo app's own behavior: `app.jsx`'s call to `startup()` is already
commented out there too, so nothing currently exercises this path.

## qlive-test/frontend (demo app)

### Delete scaffolding
Delete `qlive-test/frontend/src/framework-wiring/` and
`qlive-test/frontend/src/test-scenarios/` entirely (decision #1 above).

### Copy and flatten
Copy from
`/home/sven/ideaprojects/qlive-framework.old/qlive-test/src/main/ts/**`
into `qlive-test/frontend/src/**`, updating `@quinscape/qlive-js` imports
to `@quinscape/qlive-ts` (the only textual change needed — no package-name
rename problem like Java's `de.quinscape`/`com.dataciders`):

- `app.jsx` → becomes the new `src/main.tsx` — but simplify: since the old
  file's `startup()` call is commented out and it just does a static
  `root.render(...)`, port it as a plain static-render entry point (React
  18's `createRoot`, matching the current skeleton's `main.tsx` pattern)
  rather than reintroducing the commented-out dynamic-loading path.
- `app/Home.tsx` → `src/app/Home.tsx` — the real demo page using
  `FilterDSL`/`inject`/`Q_Foo`
- `app/Q_Foo.ts` → `src/app/Q_Foo.ts` — a typed `GraphQLQuery` querying
  `Foo`/`AppUser`/`FooType` (these type names still match the migrated
  Java domain model 1:1 — `Foo`, `AppUser`, `FooType` — so no adaptation
  needed there)
- `app/sub/View.tsx` → `src/app/sub/View.tsx` — trivial placeholder
  component, port as-is
- `types.d.ts` → **do not hand-copy as final content** — it's a generated
  file (header says "Generated types. Do *not* edit."). Copy it once as a
  starting point so the app compiles immediately, but wire up the
  `generate` script (below) as the source of truth going forward.
- Skip `components/App.tsx` and `components/App.css` — confirmed unused
  rsbuild boilerplate leftover (matches the same "delete dead scaffolding"
  call made for the Java side's `ServletInitializer`/`AppTest.java`).
- `index.html` — adapt the existing `qlive-test/frontend/index.html`: keep
  its `<title>`, change the entry script tag from
  `/src/framework-wiring/main.tsx` to `/src/main.tsx`.

### Test
Old `src/test/ts/test.test.ts` has no real assertions — it just
`console.log`s a `FilterDSL.or(...)` expression. Unlike the Java
migration's `TestCase.java` (which was pre-existing *broken*, not just
assertion-less — it threw on the cyclic-structure serialization), this one
doesn't fail, it just doesn't assert anything. Keep it as-is: port
verbatim, no `.skip`, no added assertions, no `@Disabled`-equivalent.

### Root-level GraphQL fixtures
Copy `/home/sven/ideaprojects/qlive-framework.old/qlive-test/schema.graphql`,
`.graphqlconfig`, `test.graphql`, and `schema.json` into
`qlive-test/frontend/` (co-located with where the TS build/tooling now
lives, rather than at the old repo's project root — there is no
equivalent "project root" for the frontend in the new layout since it's
nested under the Java module). Add a `generate` script to
`qlive-test/frontend/package.json` analogous to the old `"update":
"generateTS schema.graphql src/main/ts/types.d.ts"`:
```json
"generate": "generateTS schema.graphql src/types.d.ts"
```
(`generateTS` resolves via pnpm's workspace-linked bin from `qlive-ts`.)

### vite.config.ts changes
Update the `trackedFunctions` config (currently tracks the placeholder
`createFrameworkConfig`) to track the real functions the old
`babel.config.js` tracked, minus the nonexistent `query` (see qlive-ts
section above):
```ts
const trackedFunctions = {
  inject: { module: "@quinscape/qlive-ts", fn: "inject" },
  GraphQLQuery: { module: "@quinscape/qlive-ts", fn: "GraphQLQuery" },
};
```
Leave `sourceRoot`/`debug` and the rest of the plugin wiring untouched —
already correct.

### package.json changes (`qlive-test/frontend/package.json`)
The `@quinscape/qlive-ts": "workspace:*"` dependency is already correct,
no change needed. No new dependencies required for the ported demo code
itself (it only uses `qlive-ts`'s exports + React, both already present).

## Documentation follow-up

`README.md` currently describes (from the Java migration's doc pass) the
frontend-side `framework-wiring`/`test-scenarios` split as real and
decoupled. Correct this the same way the Java-side README section was
corrected: replace the two-layer description with a note that
`qlive-test/frontend` mirrors the demo app migrated from the framework's
previous incarnation (Home page, typed GraphQL query, FilterDSL usage),
without a wiring/scenarios split — consistent with the Java side's Java
package layout.

## Verification

```bash
cd /home/sven/ideaprojects/qlive-framework
pnpm install
pnpm -r test         # qlive-ts + qlive-test/frontend vitest suites
pnpm build           # full build: mvn install -> qlive, frontend (vite build), qlive-test
```

Expected/acceptable gaps, already resolved above rather than left open:
- `util/graphql.ts` is known-broken (undeclared identifiers) and ignored
  per the "Issues to ignore" section — expect a typecheck failure only if
  something actually calls it; nothing currently does.
- `startup.ts`'s webpack-context code is commented out in place (see
  "Resolved: startup.ts's webpack-based auto-discovery" above) rather than
  rewritten — verify only that the file still compiles with that part
  commented out and `init()`'s DOM-data bootstrap still exported.

The old `test.test.ts` is ported verbatim (see "Test" above) — it passes
trivially since it has no assertions, so `pnpm -r test` being green there
doesn't mean much for that one file, but that's expected, not a gap.

## Explicitly out of scope

- Regenerating `types.d.ts` from a live GraphQL schema (requires the
  migrated Java app actually running with a live DB) — copy the old
  generated snapshot as a starting point, wire up the `generate` script,
  but don't attempt to run it live as part of this migration.
- `qlive-js`'s `dist/` build output — irrelevant, the new package is
  source-referenced.
- Reconciling the old rspack-produced `track-usage.json` format with the
  new Vite-produced one — already flagged as a known follow-up in the
  Java migration plan (`docs/design/module-distribution.md` is unrelated
  to this; the actual flag lives in the Java migration's plan history),
  still applies here on the producer side too.

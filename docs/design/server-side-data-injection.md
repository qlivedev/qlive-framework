# Server-side query execution + typed embedded data channel

Status: shelved — researched and designed, not started. Written up so a future
session can pick up one slice of this without re-deriving the investigation
below. Do not treat the phase breakdown as committed scope; re-validate
assumptions against the code before resuming, since this reflects the state of
the repo as of the TS migration (commit 0986d26).

## The problem this is trying to solve

The framework tracks calls to `inject()` and `new GraphQLQuery()` (from
`@quinscape/qlive-ts`) across the frontend via a Vite-driven wrapper
(`qlive-test/frontend/plugins/track-usage-vite-plugin.ts`) around the
third-party `babel-plugin-track-usage`. The stated purpose is to let the
*server* know, per route, what data a page needs, so that data can be
server-prepared rather than fetched client-side after mount — "just there" in
dev via a live DomainQL/GraphQL endpoint, and via a typed embedded script
block in the generated document for production.

Today only half of this exists: the tracked-call data is consumed for
TypeScript type generation (`GraphQLQueryTypingService`,
`qlive/src/main/java/com/dataciders/qlive/runtime/domain/GraphQLQueryTypingService.java`),
which parses each tracked `GraphQLQuery` construction, resolves it against the
live DomainQL `GraphQLSchema`, and rewrites the `.ts` file's generic type
parameter. This is compile-time codegen, not runtime data preparation. The
runtime data path (`inject()`, `InjectionProvider`/`InjectionAPI.resolve`,
`startup()`'s `#root-data` bootstrap) is all stubbed or disconnected — see
"What's still missing" below.

## Verified facts (confirmed by reading/running the actual code, not assumed)

- **`inject()` tracking is currently a silent no-op.** `vite.config.ts`'s
  `trackedFunctions.inject` entry lacks `allowIdentifier: true`.
  `babel-plugin-track-usage` only captures literal/object-literal arguments by
  default; `inject(Q_Foo, {})`'s first argument is an identifier reference, not
  a literal. Confirmed by running the plugin against the real `Home.tsx`:
  produces `"calls": {"inject": []}` — empty. This is a standalone, tiny,
  independently-fixable bug.
- **The rspack-vs-Vite `track-usage.json` shape mismatch** flagged as an open
  follow-up in `docs/design/typescript-migration-plan.md:278-283` is already
  resolved — `qlive-test/frontend/dist/track-usage.json` and the Java-side
  `TrackUsageData`/`ModuleFunctionReferences`
  (`qlive/src/main/java/com/dataciders/qlive/model/ts/`) already agree on
  shape. The migration doc is stale on this point.
- **`ModuleFunctionReferences.requires`** already captures the local-module
  import graph per file, so "what does this route's entry module transitively
  pull in" is answerable from existing `track-usage.json` data with no new
  producer-side artifact.
- **`WEB-INF/template.html` does not exist anywhere in this repo.** It existed
  pre-migration at
  `qlive-framework.old/qlive-test/src/main/webapp/WEB-INF/template.html` and
  was never carried over by either migration commit.
  `JsViewResolver` in `qlive-test/src/main/java/com/dataciders/qlivetest/runtime/config/WebConfiguration.java:56`
  references it by string path. Its actual placeholder set (`$CONTEXT_PATH`,
  `$LANG`, `$ASSETS`, `$VIEW_DATA`, plus whatever `JsViewProvider`s add) is a
  strict subset of the old template's placeholders — `$CONTENT` is never set
  by current code and silently no-ops, so the old template can likely be
  ported close to verbatim.
- **No Vite manifest is produced.** `vite.config.ts` has no `build.manifest`
  option set. `RsPackAssetProvider`
  (`qlive/src/main/java/com/dataciders/qlive/runtime/util/RsPackAssetProvider.java`)
  expects a flat rspack-shaped `manifest.json` that Vite doesn't produce and
  wouldn't structurally match even if manifest generation were turned on
  (Vite's manifest is a nested, per-entry-point shape with `file`/`css`/`imports`
  keys). `RsPackAssetProvider`/`RsPackManifest` are used in exactly one place
  (`WebConfiguration.java`) — a clean swap for a Vite-shaped equivalent.
- **No Spring profile is activated by any current entry point.** Root
  `package.json`'s `dev` script runs `spring-boot:run` with no
  `-Dspring-boot.run.profiles`. All `@Profile("dev")`/`@Profile("prod")` beans
  in
  `qlive-test/src/main/java/com/dataciders/qlivetest/runtime/config/DevConfiguration.java`
  are currently inert. The documented `pnpm dev` workflow (browsing
  `localhost:5173`) never exercises the Spring/JsView pipeline at all — nobody
  currently browses port 8080, which is why none of the above breakage has had
  a visible symptom.
- **`QLiveConfiguration.resourceLoader()`**
  (`qlive/src/main/java/com/dataciders/qlive/runtime/config/QLiveConfiguration.java:36-44`)
  builds a `ServletResourceLoader` used for every resource the JsView pipeline
  reads (template, manifest, `track-usage.json`). Its behavior depends on
  `ServletContext.getRealPath(...)` resolving to a real filesystem path — a
  WAR-exploded-webapp concept. Both `qlive` and `qlive-test` package as jars.
  Whether this actually returns null under the real Spring Boot/embedded-Tomcat
  setup needs an empirical check (cheap: one log line, hit `/app/**`) before
  designing a fix — flagged, not yet confirmed.
- **`inject()`'s signature is synchronous** (`(query, params) => T`, not
  `=> Promise<T>`), which structurally forbids it from doing a live fetch
  itself when no server-prepared value exists. `GraphQLQuery.execute()` can,
  since it already returns a `Promise`. This matters for any future runtime
  design: `inject()` needs something like a Suspense-style synchronous
  resolver (the unimplemented `InjectionProvider`/`InjectionAPI.resolve` in
  `qlive-ts/src/components/InjectionProvider.tsx` looks like it was intended
  for exactly this).
- **`GraphQLUtil.executeGraphQLQuery(GraphQL, String query, Map<String,Object>
  variables, Object context)`**
  (`qlive/src/main/java/com/dataciders/qlive/runtime/util/GraphQLUtil.java:47-61`)
  already exists and is exactly the shape needed to execute a tracked query
  string server-side — reusable as-is by any future execution service.
- **`GraphQLQueryTypingService.analyzeModule`/`RE_VAR_NAME`** already assumes
  one `new GraphQLQuery(...)` construction per module (its regex only finds
  the first). This convention is already load-bearing for type-codegen today;
  any future query-resolution work would make it load-bearing for a second
  purpose, so it should go from implicit to explicitly documented/diagnosed.
- **Two static-document paths coexist unreconciled in production.** Vite's own
  `dist/index.html` (a self-contained, data-less SPA shell) gets copied to the
  classpath and is served by Spring Boot's default static-resource handling
  for any path `JsViewController` doesn't claim (i.e. everything except
  `/app/**`). Nothing currently reconciles this with the JsView-rendered
  document — browsing `/` vs `/app/` would show two different documents, only
  one of which ever gets server-injected data.

## How Tomcat and Vite actually cooperate (and the dev-mode gap this surfaced)

**Production**: no cooperation at request time. Vite runs once, at build time,
as a Maven side effect (`frontend-maven-plugin` → `pnpm run build`); its
output (`dist/**`) is copied onto the Spring Boot jar's classpath under
`/static/`. At runtime only Tomcat exists — it serves static files directly
and (once the fixes above land) renders `/app/**` dynamically via `JsView`,
reading the classpath-baked manifest/build artifacts. There is no live Vite
process in production.

**Dev, as currently documented**: two independent processes. Vite's dev
server (`:5173`) is what you browse — it serves `index.html` and
on-demand-transformed modules, and proxies API calls like `/graphql` through
to Tomcat (`:8080`). Tomcat's JsView/`WEB-INF/template.html`/`#root-data`
pipeline sits completely unused in this flow, since nobody browses `:8080`
directly.

**The gap**: `DevConfiguration.java`'s hot-reload beans assume Tomcat can
*watch a file* at `/static/track-usage.json` for changes (`spring-jsview`'s
file-watching `ResourceHandle`). But `track-usage-vite-plugin.ts`'s
`serve`-mode (`configureServer`) never writes `track-usage.json` to disk — it
only serves the in-memory `devData` over an HTTP middleware on Vite's own
port. Nothing updates the classpath-copied file Tomcat would be watching, so
the type-codegen hot-reload chain likely doesn't work live today, and there is
no live path for a route's tracked calls to reach Tomcat during dev at all.
A viable fix sketch: replace the file-watch assumption with Vite's plugin
`POST`ing updates to a small `dev`-profile-only Tomcat endpoint on each
change, and make Tomcat render `/app/**` in dev too (with script tags pointing
at `http://localhost:5173/@vite/client` + the entry module — Vite's own
documented backend-integration pattern for dev, not a custom workaround),
rather than treating `:5173` as the only thing anyone browses.

## What's still missing / unimplemented on the runtime data-injection side

- `inject<T>(query, params)` (`qlive-ts/src/inject.ts`) is a hardcoded stub
  ignoring both arguments.
- `InjectionProvider`'s `InjectionAPI.resolve(name)`
  (`qlive-ts/src/components/InjectionProvider.tsx`) is an empty stub.
- `startup()` (`qlive-ts/src/startup.ts`), which reads `#root-data` and calls
  `init(data as QLiveConfig)`, is commented out of `qlive-ts/src/index.ts` and
  never called from `qlive-test/frontend/src/main.tsx`.
- `QLiveViewDataProvider`
  (`qlive/src/main/java/com/dataciders/qlive/runtime/config/QLiveViewDataProvider.java`)
  only embeds DomainQL schema metadata today — never query results.
- `qlive-ts/src/util/graphql.ts` (used by `GraphQLQuery.execute()`'s live-fetch
  path) references undeclared identifiers (`contextPath`, `csrfToken`,
  `variables`) — pre-existing broken code, deferred by the TS migration plan.
- There is no router in the frontend yet
  (`qlive-test/frontend/src/main.tsx` renders a static placeholder), so there
  is currently no route → entry-module mapping to hang per-request query
  execution on.

## Sketch of a full design (not committed scope — re-validate before resuming)

If picked back up, the shape we converged on was:

1. **Foundation**: restore `WEB-INF/template.html`, reconcile the asset
   manifest (adapt to Vite's real manifest format rather than reshaping Vite's
   output to match the rspack-era flat shape), fix the resource-loader's
   `ServletContext.getRealPath` dependency, wire the `dev` Spring profile from
   the actual `pnpm dev` entry point, and resolve the dev-mode Tomcat/Vite
   cooperation gap (push-based bridge instead of file-watch; a `dev`-profile
   asset provider pointing at Vite's dev server; a decision on what happens to
   Vite's own generated `index.html`/the root path).
2. **Call-site identity**: a small, framework-owned Babel transform (distinct
   from the third-party `babel-plugin-track-usage`) tagging each tracked
   `inject()`/`GraphQLQuery()` call with a stable ID derived from module path +
   source offset, so a specific runtime call can be matched to its
   server-computed value. This transform can also enforce, at build time, that
   `inject()`'s params are statically literal (a framework rule — dynamic data
   needs go through `GraphQLQuery.execute()` instead, which remains
   live-fetch-only for those cases).
3. **Routing**: stand up a minimal file-based routing convention
   (`src/app/**/*.tsx` → URL path, root page named `index.tsx`) since none
   exists today, needed to know which tracked calls apply to a given request.
4. **Server-side execution**: a new service, sibling to
   `GraphQLQueryTypingService`, that walks a resolved route's transitive
   tracked calls and executes them via the existing
   `GraphQLUtil.executeGraphQLQuery` overload, producing a
   `Map<invocationId, result>`.
5. **Embedding + consumption**: extend `QLiveViewDataProvider`'s view data with
   the query results, wire `startup()`/`#root-data` back up, and implement
   `InjectionProvider`/`InjectionAPI.resolve` as a Suspense-style synchronous
   resolver so `inject()` can stay synchronous while still falling back to a
   live fetch in dev or for any call with no server-prepared value.

Each of the five areas above is plausible as its own standalone session —
they don't need to land together, and 1 in particular (rendering `/app/**`
correctly at all) blocks nothing else being demoable once done.

## Open questions for whoever resumes this

- Does `ServletContext.getRealPath("/")` actually return null under this
  project's jar-packaged Spring Boot setup? Cheap to check, and several
  downstream design choices branch on the answer.
- Root page naming: rename `Home.tsx` → `index.tsx` for the routing
  convention (matches ecosystem expectations, but touches the file every
  future app copies from), or special-case the existing name?
- Should server-side prefetch require `inject()`'s params to always be
  build-time-static (enforced as a compile error), with `GraphQLQuery.execute()`
  as the only sanctioned path for dynamic data? (Leaning yes, but not fully
  settled.)
- Root path handling: redirect `/` to `/app/` and drop Vite's own generated
  `index.html` from the served static tree, or extend `JsViewController` to
  also match `/`?
- Is the `track-usage.json` dev-mode hot-reload path (feeding
  `GraphQLQueryTypingService`) worth fixing in the same pass as the
  asset-serving integration, or genuinely separable, since it's tied to a
  currently-unexercised type-codegen feature rather than page rendering
  itself?
- No production deployment entry point (Dockerfile/CI/systemd) exists yet in
  this repo to activate the `prod` Spring profile — whatever eventually
  deploys this needs to pass `--spring.profiles.active=prod`.

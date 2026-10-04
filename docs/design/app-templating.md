# Application templating (design sketch)

Status: sketched, not started. Written 2026-09-26.

How a new, empty QLive application comes into existence. This is not
`module-templating.md`, which covers a module materializing its types
and views into an application that already exists. The two share an
idea -- the application's tree is where generated and templated things
have to live -- but they differ in who owns the result and in what
happens on update, so they are designed separately.

## Problem

There is no way to start a QLive application other than copying
qlive-test and deleting things. qlive-test is meant to be the structural
template for applications, but it is three things at once:

- **Wiring every application needs.** The six `runtime/config` classes,
  `LoginController`, the application class, `vite.config.ts` (base,
  proxy rules, login entry), `main.tsx`, `login.tsx`/`login.html`, the
  frontend, jOOQ and DocsExtractor plugins in the pom, and the four
  tables the `qlive` module reaches by name: `app_user`, `app_login`,
  `app_field_layout`, `app_version`.
- **Test scenarios.** Foo/Bar/Baz/Qux, their `configureRelation` calls,
  `ExampleMetadataProvider`, `pageSize(5)`, `TestComponent`, the quick
  login users, the regression tests.
- **Monorepo coupling.** The parent pom's `relativePath`, `workspace:*`
  dependencies, the Vite aliases into `../../qlive-ts/src` and the
  `fs.allow` of the repository root, and the `pnpm-build-qlive-ts`
  execution that runs from the repository root. None of it can exist in
  an application outside this repository.

The pom's description names `frontend/src/framework-wiring` and
`frontend/src/test-scenarios`. Neither exists; the split it describes
was never made.

The tutorial in qlive-doc is waiting on this: it is to be written
against a freshly generated empty application.

## Shrink the wiring first

Everything a template writes belongs to the application from then on and
never sees another framework update. Most of the wiring above is
framework code that happens to live in the application, so the first
step is moving it out -- independent of templating, and qlive-test gets
thinner either way.

**Explicitly, not by autoconfiguration.** No `AutoConfiguration.imports`,
no starter, no `@ConditionalOnMissingBean`. Autoconfiguration hides what
got wired, and customizing it means finding the condition to defeat. The
framework's wiring ships as plain `@Configuration` classes in `qlive`,
and the application lists them in its `@Import`, the way
`QLiveConfiguration` is imported today:

```java
@Import({
    QLiveConfiguration.class,
    QLiveDevConfiguration.class,   // today's DevConfiguration
    QLiveWebConfiguration.class,   // VitePageRenderer, ViteIndexController
    QLiveLoginConfiguration.class, // LoginController
    JOQQConfiguration.class,       // application-owned
    SecurityConfiguration.class    // application-owned
})
```

That list is the table of contents of the application's wiring. Every
entry is one ctrl-click from its source, and customizing one means
dropping it and writing a replacement. Replacing must not mean copying
neighbors, so classes are cut along the lines an application would want
to replace -- the login page is the obvious one, which is why it is its
own class above.

What stays application-owned, and why:

- **`JOQQConfiguration`.** It names the SQL dialect, which is the
  application's decision.
- **`SecurityConfiguration`.** Security has to be readable in the
  application. It keeps its own `SecurityFilterChain` bean and calls
  into the framework for the QLive-specific parts: `QLivePaths.DEV_URIS`
  permitted in dev and denied otherwise, the CSRF exemptions, the login
  page, remember-me backed by `DefaultPersistentTokenRepository` and
  `AppUserDetailsService`. Which URIs need which role stays visible.

**The same on the Vite side.** Not a preset returning a config nobody
reads, but pieces the application composes in its own `vite.config.ts`
-- say a `qliveProxy(backendOrigin)` returning the proxy block. The
reasoning currently written next to that block (why `/push` needs `ws`,
why `/push`, `/login` and `/logout` must not `changeOrigin`) moves to
the framework source it explains, instead of being copied into every
application.

The cost: an imported class can change behavior on a framework update
with nothing in the application's diff. That is the point of moving it,
but it makes every change to these classes a release note item.

## The template is a buildable application

`qlive-template/`, a module in the reactor and a package in the pnpm
workspace, holds the wiring left after the step above and nothing else:
the application class, the application-owned configuration, `main.tsx`,
the login page, one or two minimal views, the pom, `vite.config.ts`,
properties.

Its identity is a **sentinel made of valid identifiers**: package
`io.github.qlivedev.template`, artifact and database name
`qlivetemplate`, title `QLive Template`. The template compiles,
typechecks and runs as it stands; the generator renames the sentinel and
moves the package directory. There is no template language, which keeps
`${...}` free for module templates, where `module-templating.md`
reserves it.

**The alternative, deriving the template from qlive-test by stripping
marked regions, rots silently.** A test-only line leaking into the
template or a wiring change missing from it fails nothing. A template
that is itself built in CI fails loudly.

**The baseline schema is SQL, not a `.backup`.** The generator has to
load it into whatever database the application names, and the qlive-test
reality -- a binary backup as the only persistence, see the dev database
note -- is not something to hand a new user as their first artifact. It
holds the four tables `qlive` reaches by name. `qlive/qlive.backup` is
not that baseline; it is the source of qlive's own test domain, `test_*`
tables the framework's tests build their schema from.

**Generated artifacts are committed.** jOOQ classes, `schema.graphql`
and `types.d.ts` for the baseline schema ship in the template, so a
fresh application compiles before any database exists. jOOQ codegen
needs a live database, and the first build must not.

## Keeping qlive-test and the template aligned

qlive-test stays hand-maintained as template plus scenarios; it is not
regenerated. A check (Maven test or `tooling/` script) asserts that the
files both have -- the application class, the security configuration,
`main.tsx`, the `vite.config.ts` skeleton -- are identical after
substituting identities, except inside regions qlive-test marks as
scenario-only. After the shrink step that set is small, so the check
is cheap and its failures are specific.

## The generator is the `qlive` CLI

`module-distribution.md` leaves open whether `qlive mod add` is its own
package or a bin of an existing one. Application creation settles it:
one npm package with a `qlive` bin, `qlive new <name>` beside the future
`qlive mod add`, plus a `create-qlive-app` alias so `pnpm create
qlive-app` works. The root `package.json` already promises that Maven
runs under the hood and never needs to be called; this keeps the front
door on the pnpm side.

`qlive new` does:

1. Copy the template, rename the sentinel identity, move the package
   directory.
2. Write the pom with real framework versions.
3. Generate a random `application.remember-me.key` rather than leaving
   the `TODO` qlive-test's prod properties carry.
4. Optionally create the database role and database and load the
   baseline SQL.
5. Run or print what remains: `pnpm install`, `pnpm dev`.

## Publishing is the real prerequisite

A generated application depends on `@qlivedev/qlive-ts` from npm and on
`io.github.qlivedev:qlive`, `qlive-api` and `qlive-graphql` from Maven.
Nothing is published yet.

Until it is, `qlive new --local <repo>` resolves the Java side from
`~/.m2` after `mvnw install` and the npm side from `pnpm pack` tarballs.
That is also what CI runs: generate an application, build it, boot it,
GET `/api/bootstrap`. That round trip is the template's actual test,
and it catches regressions in the shrink step as well.

The template imports `@qlivedev/qlive-ts/vite`, which is on no API page
on purpose. A generated application depending on it is the strongest
argument yet for deciding that entry point's documentation.

## Order

1. Shrink the wiring: move the configuration classes into `qlive`,
   build the security helper and the Vite pieces, update qlive-test to
   use them. Useful on its own.
2. Create `qlive-template` with the sentinel identity and baseline SQL.
3. The alignment check between qlive-test and the template.
4. `qlive new` with `--local`, and the CI round trip.
5. Publishing, then `qlive new` against published versions.
6. The tutorial in qlive-doc, written against a generated application.

## Open items

- **Publish before or after `--local`.** Local-first means the CI round
  trip exists before anything is public.
- **Seeded admin.** Whether `qlive new` seeds a known dev-only admin
  user. The dev database is disposable, so it is defensible for dev; the
  prod profile must never get one.
- **Parent POM or standalone.** A published `qlive-parent` BOM makes the
  plugin configuration (frontend, jOOQ, DocsExtractor) updatable, a
  standalone pom makes it explicit. Explicit wiring argues for
  standalone; the rule that framework code does not belong in the
  application argues for the BOM, since that plugin configuration is
  framework code.
- **Where the baseline schema lives.** In the template only, or in
  `qlive` as the framework's own DDL that the template references.
  The latter is closer to `module-templating.md`'s owned types, with the
  core as the first module.

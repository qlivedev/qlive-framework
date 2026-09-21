# Full-stack module distribution (design sketch)

Status: sketched, not started. Written 2026-08-29, revised 2026-09-10.

## Problem

QLive modules can have both a JavaScript part and a Java part. We want a
way to distribute, install, and version these as one logical unit, without
building a bespoke package manager from scratch.

## Two kinds of module

A module brings *behavior*, or it brings a *domain and the UI over it*.
The two need almost disjoint support, and conflating them is what makes
this look harder than it is.

- **Behavior modules** are generic over the application's domain: the
  working set and merge, websocket push, an audit trail, an import
  service. They bring no tables and no views. What they need is API
  surface -- exported seams in `qlive-ts`, event publication in `qlive`,
  and a way to get their beans into the context.
- **Domain/UI modules** bring their own tables and their own screens.
  An auth module owning `app_user`, `app_login` and a login view is the
  canonical example, and it is the hardest possible case: schema,
  generated jOOQ classes, generated TypeScript types and routes are all
  produced per application, ahead of its build, and none of them can be
  linked out of a jar or a node package. Supporting that species means
  materialising the module's content into the application before its
  build runs, which is a separate mechanism -- see
  `module-templating.md`.

**What decides whether something can be a module is whether the framework
exposes the seam it needs, not the distribution mechanism.** Websocket
push is the proof: no tables, no views, no install-time machinery at all,
a perfect npm/Maven citizen -- and it cannot be written today because
`QueryDocument.notify()` is private and `MergeService` publishes nothing.

The rest of this document is about the first species, which is cheap.

## Approach

Reuse the two existing ecosystems rather than inventing a new registry:

- **JavaScript half** is the primary artifact, published to the npm
  registry (or a private one) as normal.
- **Java half** is published to Maven Central or a private
  Nexus/Artifactory, as normal.
- The npm package's `package.json` carries a small marker block that
  points at the Maven coordinates. This is the only new metadata format
  in the whole design.

```json
{
  "name": "@qlive/auth-module",
  "version": "1.4.0",
  "qliveModule": {
    "groupId": "io.qlive.modules",
    "artifactId": "auth-module"
  }
}
```

`version` is not required in `qliveModule`. It defaults to the same version as the npm package and that would the strong
recommendation. We should, however, allow specifying version numbers.
`groupId`/`artifactId` stay explicit because they can't be derived from the NPM name.

No custom `provides`/`requires` fields: npm's own `dependencies` /
`peerDependencies` already express compatibility ranges, so the manifest
doesn't need to reinvent that.

**`@quinscape/qlive-ts` is a `peerDependency` of every module, never a
`dependency`.** `config()` is a module-level singleton and the
converters register into module-level state, so a second resolved copy
is not a heavier install, it is a module talking to a framework the
application is not using. The same reasoning `index.ts` already spells
out for `temporal-polyfill` applies to qlive-ts itself, one level up.
`qlive mod add` checks this.

## Versioning

Ideally, we have one version stream per module — i.e., the npm version and the Maven version
are equal. However, since the user is interacting with the NPM artifact as primary artifact, we don't
strictly need that to be true. The user adds "feature@1.0.1" and that artifact knows what server side it is meant
to pair with.

The framework-compatibility direction is asymmetric and worth knowing.
npm `peerDependencies` gives the JS half a real constraint that fails
loudly. Maven has no equivalent: a module built against `qlive` 2.1 that
lands in an application pinning 2.0 resolves by nearest-wins and fails
at runtime, if at all. `validate-modules` is where that gets caught --
see Tooling.

## Java-side self-announcement (ServiceLoader)

The npm→Maven marker (`qliveModule` in `package.json`) only tells us the JS
side knows about its Java pair. We also want the reverse: a Java module on
the classpath announcing that it *is* a qlive server module, and which npm
package it pairs with — so a jar with no matching frontend package gets
caught too, not just the other way round.

Rather than invent a second, separate mechanism for that, reuse
`java.util.ServiceLoader`. This is also the auto-discovery mechanism the
server needs anyway to find and register installed modules at startup, so
one SPI covers both jobs instead of two:

- **Runtime**: `@EnableQLive`'s registrar enumerates
  `ServiceLoader.load(QliveServerModule.class)` to find the installed
  modules and register the configuration classes they name.
- **Build time**: `qlive-maven-plugin`'s `validate-modules` goal reads the
  same providers to confirm each announced module's npm package is present
  in `frontend/node_modules` (see Tooling below).

**The provider is metadata, not a bean source.** It answers "which
modules are installed" and names what each contributes; it never produces
beans itself and never sees an application context. What acts on it is
Registration, below.

The interface lives in a small shared module (e.g. `qlive-server-api`)
that every server module already depends on:

```java
package io.qlive.spi;

public interface QliveServerModule {
    /** npm package this module pairs with, e.g. "@qlive/feature-x". */
    String npmPackage();

    /** Pinned npm version */
    String npmVersion();

    /** Spring configuration classes this module contributes, by name. */
    List<String> configurationClasses();
}
```

A module jar registers its provider the normal way, via
`META-INF/services/io.qlive.spi.QliveServerModule`.

The provider implementation should stay metadata-only — no Spring
annotations, no `@Autowired` fields, no static side effects. It gets
constructed in two very different places: inside the running server, and
inside the Maven plugin at build time (see below). Anything that assumes
an application context exists will break the latter. `configurationClasses()`
returns names rather than `Class` objects for exactly that reason: the
build-time side must be able to read it without loading Spring.

## Registration

`@EnableQLive` on the application class is how QLive and every installed
module reach the application context.

```java
@SpringBootApplication
@EnableQLive
public class MyApplication { ... }
```

The annotation imports `QLiveConfiguration` plus a registrar that walks
the ServiceLoader inventory and registers each provider's
`configurationClasses()`. One line in the application, and it covers
every module that application will ever install -- its author never has
to learn a module's configuration class name.

It also replaces the `@Import(QLiveConfiguration.class)` every
application currently copies out of qlive-test. Incidentally, the other
five entries in that block are already redundant: they name
`@Configuration` classes that sit inside the scanned package and would be
found anyway.

**Why not Spring Boot auto-configuration.** It would work -- a module
ships `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
and Boot registers what it names. But auto-configuration exists for
*starters*: libraries that must configure themselves, unasked, adapting
to whatever happens to be on the classpath. A QLive module is not that.
Somebody ran `qlive mod add`, so the module is present on purpose and
there is no adaptation problem to solve. What it would cost is a second
metadata file per module, overlapping confusingly with the ServiceLoader
provider, whose failure mode is silence: a missing or misspelled imports
file leaves a jar on the classpath doing nothing at all, and covering
that needs a startup cross-check whose only job is to catch the
mechanism's own failure mode.

Reading the SPI instead makes announcing and registering the same act. A
module that announces itself is registered; one that does not is caught
by `validate-modules`. Nothing is left for a cross-check to find, and the
error messages are QLive's own.

What that gives up is worth knowing: module authors learn a QLive idiom
rather than a Boot one, and there is no `@AutoConfigureAfter` ordering
graph. `@Order` and `@DependsOn` cover what modules are likely to need,
and the registrar can be a `DeferredImportSelector` if
`@ConditionalOnMissingBean` ever has to work the way it does for
starters.

**Component scan does not reach a module, and is not made to.**
`@SpringBootApplication` scans the package of the class declaring it and
below -- `io.github.qlivedev.qlivetest.runtime` in qlive-test -- so a
module's beans in `io.qlive.*` are never scanned, with or without this
annotation. A module declares its beans as explicit `@Bean` methods in
the configuration class it announces, in preference to putting
`@ComponentScan` on its own package: its contribution to the context is
then one readable file rather than the result of a scan.

That bites `@GraphQLLogic` in particular, which is meta-annotated
`@Component` and therefore only becomes a bean by being scanned. A
module's logic class has to be declared, not annotated and forgotten.

Once the beans are defined, the lookups an application's
`DomainQLConfiguration` already performs pick them up without knowing a
module exists: `getBeansWithAnnotation(GraphQLLogic.class)` finds a
module's logic, `getBeansOfType(MetadataProvider.class)` finds its
metadata provider, and `InjectionArgumentProcessor` arrives as an ordered
`List<>`. Those channels are open. Registration was the only thing
missing.

## Security

A module secures its own endpoints itself, by contributing a
`SecurityFilterChain` bean with its own `securityMatcher`. This composes
without the application touching its `SecurityConfiguration`, provided
the module orders its chain ahead of the application's catch-all. It is
one of the few areas of the application's configuration that a module can
reach directly.

## What a module cannot reach

Three things, and only the first is cheap to fix.

**The DomainQL builder.** `QLiveDomain.newDomain()` hands a builder back
to the application, which finishes it. Everything said there is closed to
a module: `withAdditionalScalar`, `configureRelation`,
`configureNameField`, `objectTypes` and `withTypeDocsFrom` -- note that
QLive's own `qlive-typedocs.json` is loaded by a line the application
copied into its config, and a module shipping typedocs would need the
same line added by hand.

This matters beyond the individual calls: the more that copied
`DomainQLConfiguration` does, the less modular the framework is, because
every decision living in it is one a module can only reach by asking the
application's author to edit it. An ordered `QLiveDomainCustomizer` bean
(a `Consumer<DomainQLBuilder>`) moves those out of the template and is
the seam that decides how much a module can say about the schema.

**DDL.** jOOQ generates per application, from the application's live
database, into the application's package. Schema state is a `.backup`
dump; there is no migration tool. A module cannot create its own tables.
Consequently a module also cannot reference generated jOOQ classes for
them -- it reaches its tables by name through `DSL.table(DSL.name(...))`,
the way `AppUserDetailsService` and `DefaultPersistentTokenRepository`
already do, and reaches application tables through DomainQL's type
registry, the way the query side already does. That pattern is the
module contract for data access, and it exists because QLive needed it
first.

**The view line.** `trackUsage` excludes `/node_modules/` outright and
`VIEW_ROOT` is a compile-time constant; `import.meta.glob` takes only
literals and resolves only relative to the calling file. So the
application's own entry point is the only place views can be declared.

| A module can ship | A module cannot ship |
|---|---|
| components | views / routes |
| hooks, stores, converters | `useInjection` anywhere |
| GraphQL logic, mutations | `GraphQLQuery` with generated result types |
| metadata and its typings | i18n tags |
| CSS in its own layer | its own tracked-call specs |

The rule underneath that table is worth stating on its own, because it is
what the templating design is built on: **what must be application-local
is exactly what static analysis has to see.** A module's tracked-call
specs are the one entry that has a cheap workaround -- the module exports
a `trackedFunctions` object and the application spreads it into its
`trackUsage({...})` call, one explicit line.

## Seams the framework owes a module

The concrete list, in the order they are worth taking. All of them are
small, and most land inside work already planned.

1. **`@EnableQLive` and the module registrar**, per Registration above.
2. **`MergeService` step E publishes an `ApplicationEvent`.** The version
   holders become one listener; a push module is the second. The merge
   design already names step E as the place a broker would be handed the
   version records -- publishing instead of calling makes that additive
   rather than a patch.
3. **A "stored state changed" entry point on both stores.** The merge
   design already commits to this for `WorkingSet`, with a push message
   named as the second caller. `QueryDocument` needs the same and does
   not have it: `rows`, `config` and `rowCount` are public and mutable
   while `notify()` is private, so an external writer can change a
   document and the change stays invisible. Making `notify()` public
   would be the wrong fix -- it lets a caller render an inconsistent
   document. One narrow entry per store, same vocabulary in both.
4. **`QLiveDomainCustomizer`**, per the previous section.

## Tooling

Two complementary pieces:

1. **`qlive mod add <package>`** (CLI, write side) — runs `pnpm add`,
   reads the `qliveModule` block from the installed package's
   `package.json`, and after confirmation, writes/updates the matching `<dependency>` in
   `pom.xml` (`groupId`/`artifactId` from the manifest, `version` from
   `package.json`). Maven's own POM stays the explicit, static source of
   truth for the Java side. No such CLI package exists yet.

2. **`qlive-maven-plugin`** (Maven plugin, read/verify side) — a real
   Maven plugin module (packaging `maven-plugin`), sibling of `qlive`
   and `qlive-test` in the reactor. One Mojo, goal `validate-modules`,
   bound to the `process-resources` phase (same slot
   `maven-resources-plugin`'s `copy-frontend-build` execution already
   uses in `qlive-test/pom.xml`, since both need `node_modules` /
   `frontend/dist` to already exist — frontend-maven-plugin's
   `pnpm-install`/`pnpm-build` executions run in `generate-resources`).
   The Mojo gets `MavenProject` injected and reads the already-resolved
   dependency set via `project.getArtifacts()` (no shelling out to `mvn
   dependency:list`). It checks both directions and collects *all*
   mismatches into a single `MojoFailureException`, not just the first:

   - **npm → Maven**: walks `frontend/node_modules/**/package.json`
     (including scoped `@scope/*` packages), and for every one carrying a
     `qliveModule` block, confirms the corresponding
     `groupId:artifactId:version` is present among the resolved Maven
     dependencies.
   - **Maven → npm**: builds a `URLClassLoader` over the resolved
     dependency jars, parented on the plugin's own classloader (so
     `QliveServerModule` resolves to the same class on both sides —
     don't put `qlive-server-api` itself in the child URLs, it's already
     visible through the parent, and duplicating it there would break
     `ServiceLoader`'s `instanceof` matching), then
     `ServiceLoader.load(QliveServerModule.class, thatLoader)`. For every
     provider found, confirms `npmPackage()` at version `npmVersion()`
     (or the jar's own version if unset) is present among the same
     `frontend/node_modules/**/package.json` names already walked above.
   - **Framework compatibility**: confirms the resolved `qlive` version
     satisfies what each module was built against. This is the Maven-side
     equivalent of the npm `peerDependency` the JS half gets for free.

## Open items (not decided)

- Whether `qlive mod add` should be its own workspace package or a bin of
  an existing one. Nothing exists yet either way.
- Whether frontend module auto-wiring (a Vite plugin scanning
  `node_modules/*/package.json` for frontend pieces) is worth the
  complexity — parked until there are enough real modules to justify it.
- Whether the view line is worth lifting at all, given that
  `module-templating.md` answers the same need by putting views in the
  application's source tree. Lifting it would mean a module shipping its
  own track-usage fragment for the plugin to merge, plus a route
  namespace and a collision rule. Not attempted.
- **Decided**: `QLiveServerModule` stays metadata-only. It carries
  pairing info and names a module's configuration classes, but never
  produces beans and never sees an application context. `@EnableQLive`
  and its registrar are what act on it; Spring Boot auto-configuration is
  deliberately not used, see Registration.

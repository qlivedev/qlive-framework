# Full-stack module distribution (design sketch)

Status: shelved — sharpened but not started. Written 2026-08-29.

## Problem

QLive modules can have both a JavaScript part and a Java part. We want a
way to distribute, install, and version these as one logical unit, without
building a bespoke package manager from scratch.

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

## Versioning

Ideally, we have one version stream per module — i.e., the npm version and the Maven version
are equal. However, since the user is interacting with the NPM artifact as primary artifact, we don't
strictly need that to be true. The user adds "feature@1.0.1" and that artifact knows what server side it is meant
to pair with.

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

- **Runtime**: the server enumerates `ServiceLoader.load(QliveServerModule.class)`
  to discover and register installed modules — no separate registry, no
  manual wiring per module.
- **Build time**: `qlive-maven-plugin`'s `validate-modules` goal reads the
  same providers to confirm each announced module's npm package is present
  in `frontend/node_modules` (see Tooling below).

The interface lives in a small shared module (e.g. `qlive-server-api`)
that every server module already depends on:

```java
package io.qlive.spi;

public interface QliveServerModule {
    /** npm package this module pairs with, e.g. "@qlive/feature-x". */
    String npmPackage();

    /** Pinned npm version */
    String npmVersion();
}
```

A module jar registers its provider the normal way, via
`META-INF/services/io.qlive.spi.QliveServerModule`.

The provider implementation should stay metadata-only — no Spring
annotations, no `@Autowired` fields, no static side effects. It gets
constructed in two very different places: inside the running server, and
inside the Maven plugin at build time (see below). Anything that assumes
an application context exists will break the latter.

## Tooling

Two complementary pieces:

1. **`qlive mod add <package>`** (CLI, write side) — runs `pnpm add`,
   reads the `qliveModule` block from the installed package's
   `package.json`, and after confirmation, writes/updates the matching `<dependency>` in
   `pom.xml` (`groupId`/`artifactId` from the manifest, `version` from
   `package.json`). Maven's own POM stays the explicit, static source of
   truth for the Java side.

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

An `exec-maven-plugin`/`exec:java` script was considered and rejected:
`qlive-test` is explicitly the template `qlive-test-template` is later
derived from, i.e. everything done there should be isomorphic to what a
real framework consumer does in their own app. A real Maven plugin,
declared once and bound to a phase, is something a consumer can add to
their own `pom.xml` verbatim; a project-local `exec:java` wiring is not.

## Open items (not decided)

- Whether `qlive-maven-plugin` should also validate `qliveApiVersion`-style
  framework-compatibility constraints at build time, once real modules
  exist to motivate it (deferred — npm `peerDependencies` covers the JS
  side already; no Java-side equivalent designed yet).
- Whether frontend module auto-wiring (a Vite plugin scanning
  `node_modules/*/package.json` for frontend pieces) is worth the
  complexity — parked until there are enough real modules to justify it.
  The backend half of this (`QliveServerModule` via `ServiceLoader`) is
  now designed above.
- **Decided**: `QliveServerModule` stays metadata-only (pairing info for
  discovery/validation), not the mechanism for actual bean/route
  auto-registration. Whatever drives Spring-Boot-autoconfigure-style
  registration is a separate SPI, still undesigned, layered on top rather
  than folded into this one.

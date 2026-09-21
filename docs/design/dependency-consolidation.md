# Consolidating dependencies and namespace

Status: planned, not started. Written 2026-09-19.

## Problem

A framework user meets more names than the framework has reason to show
them. To build against QLive today you resolve three artifacts beyond the
obvious ones, and read two unrelated namespaces:

| Dependency | What QLive uses of it |
| --- | --- |
| `de.quinscape.domainql:domainql` | 86 of 215 Java files, 35 types |
| `de.quinscape:spring-jsview` | one class, `JSONUtil` |
| `babel-plugin-track-usage` | one file, `qlive-ts/src/vite/trackUsage.ts` |

The Java packages and the Maven groupId are `com.dataciders`; the npm scope
is `@quinscape`. Both are inherited from where the framework started. With
rights in QLive transferring to its author, neither is the framework's own
name -- and they are not even the same name as each other.

Two of the three are also resolved as SNAPSHOT versions that are published
to no repository, so the build is reproducible only where they have been
installed by hand. That is a standing build-hygiene problem independent of
everything else here.

None of this is load-bearing. QLive develops these in lockstep or does not
develop them at all, so the artifact boundaries cost users resolution
steps, extra documentation and a second namespace, and buy nothing back.

## domainql is absorbed, not depended on

domainql is 128 files and roughly 14.9k lines, against QLive's 123 files
and 14.6k. It is not a general-purpose library that QLive happens to use;
it is the back half of an earlier system, with a seam that does not
describe anything QLive needs.

- `model/` (13 files, 2,046 lines) is a specific application's domain
  model, not a generic one: `StateMachine`, `DTLayout`, `FKLayout`,
  `FieldMode`. QLive imports none of it.
- `schema/` (17 files, 1,269 lines) plus `DomainGenerator` is a runtime DDL
  subsystem -- `DDLOperations`, `InformationSchemaOperations`,
  `SchemaUpdateMode`, `NamingStrategy` -- that generates a database from a
  JSON model. QLive generates jOOQ classes from an existing database, the
  opposite direction, and imports exactly one interface from the whole
  package: `DomainQLAware`, at four sites. `DomainGenerator` is referenced
  by nothing inside domainql itself.

That is roughly 3,500 lines, about a quarter of the library, carried for
one interface.

**Where each piece lands: what a framework user touches gets dissolved into
QLive's own packages; what only QLive's internals touch stays behind the
seam as an internal package.**

Dissolve, because the split is accidental and user-visible:

- Scalars. QLive has `runtime/scalar` and domainql has `scalar/`, and which
  scalar lives where is an accident of history.
- The annotations. Nobody should import `@GraphQLQuery` from one namespace
  and QLive's annotations from another.
- `generic/` -- `GenericScalar`, `DomainObject` and their coercings are the
  wire format, referenced throughout QLive.

Keep behind an internal package, because it is a real subsystem with its
own shape:

- `DomainQL`, `DomainQLBuilder`, `TypeRegistry`, `TypeContext`,
  `LogicBeanAnalyzer`, `RelationBuilder` -- roughly 4,900 lines of schema
  assembly, genuinely separable from the working set, the merge and push.
  Dissolving it into QLive's runtime packages would turn a large coherent
  engine into shapeless fragments.

### Three modules

The reactor gains two modules beside `qlive`:

| module | holds |
| --- | --- |
| `qlive-api` | what application code touches: the annotations, the domain object and generic scalar carriers |
| `qlive-graphql` | the engine: schema assembly, fetchers, logic bean invocation, the scalar definitions |
| `qlive` | the framework proper, depending on both |

A single module would have been simpler, but the dissolve-upward plan and a
two-module split are incompatible: `DomainQL`, `DomainQLBuilder`,
`LogicBeanAnalyzer` and `TypeRegistry` all reference `domainql.annotation`,
and `LogicBeanAnalyzer` exists to scan for `@GraphQLLogic`. Annotations
above the engine with the engine below is a cycle. Three modules break it
properly: annotations at the bottom, engine in the middle, framework on
top.

The DomainQL name retires with the fork. It survives only where it is
owed -- the `NOTICE`, the Apache-2.0 attribution, and a mention deep in
the documentation -- not in a package name. `qlive-graphql` rather than
`qlive-schema` because the code is not only schema assembly: `fetcher/`
and `logic/` are roughly 1,240 lines of `DataFetcher` and
`DataFetchingEnvironment` handling that run during query execution.

**The scalar definitions belong in `qlive-graphql`, not `qlive-api`.**
They look like they must go up, because `generic/` imports
`BigIntegerScalar` -- but `generic/` is not one thing. Its data carriers
are clean leaves, while its `Coercing` and `GraphQLScalarType`
implementations are bound to `DomainQL` itself:

| file | internal dependencies | module |
| --- | --- | --- |
| `GenericScalar` | `annotation.GraphQLScalar` | api |
| `DomainObject`, `GenericDomainObject` | `fetcher.FetcherContext` | api |
| `DelayedCoercing`, `DomainObjectFactory` | none | api |
| `DomainObjectCreationException` | `DomainQLException` | api |
| `GenericScalarType`, `DomainObjectScalar` | `DomainQL`, `scalar.*` | graphql |
| `GenericScalarCoercing`, `DomainObjectCoercing` | `DomainQL` | graphql |

The two files that imported `BigIntegerScalar` are exactly the two that go
down, so after the split everything referencing `scalar/` is already in
`qlive-graphql` and no cycle appears.

`annotation/` has no internal imports at all, which makes it the natural
core of `qlive-api`. `FetcherContext` has to come up out of `fetcher/`
because `DomainObject` needs it -- the one place a module boundary cuts
through a package rather than between packages.

Open for later: registering a custom scalar is a planned extension point,
and a user doing so would have to reach the machinery in `qlive-graphql`.
The built-in definitions being internal is right, but the registration
seam may need an api-level type before `scalarEqual(type, a, b)` is
designed.

One dependency improvement falls out that the current boundary prevents:
`docs/` splits. `TypeDoc`, `FieldDoc`, `ParamDoc` and the comparators are
runtime -- `DomainQL` and `DomainQLBuilder` load typedocs to attach
descriptions to the schema -- but only `DocsExtractor` touches javaparser,
in one file. Move the extractor to the build tooling and `javaparser-core`
leaves the runtime classpath, where it sits today only because domainql
ships the extractor in the same jar.

**Do this as a move, not a rewrite.** Vendor the sources, repackage, prune
the dead subsystem, keep behavior identical, get the tests green. Changing
namespace and behavior in one pass means debugging both at once.
Improvements come after.

### License obligations

Rights in QLive are held outright. domainql and babel-plugin-track-usage
are used purely on the basis of their Apache-2.0 grant, so vendoring them
is redistribution of modified Apache-2.0 source and section 4 applies.

The facts make this small. Both upstreams are Apache-2.0, as is QLive, so
there is no compatibility question in either direction. Neither ships a
`NOTICE` file, so no inherited attribution notices have to be reproduced
under 4(d). Neither carries copyright headers or `@author` tags in any
source file, so 4(c) has nothing to retain. The repository's existing
`LICENSE` satisfies 4(a).

That leaves **4(b): modified files must carry prominent notices stating
that we changed them.** Repackaging touches every vendored file, so every
vendored file is modified. Rather than 128 per-file headers -- noise, and
at odds with how comments are written here -- put the statement where the
vendored tree begins: a repo-root `NOTICE` plus a `package-info.java` on
the internal package, each naming the upstream project, the exact commit
vendored from, the Apache-2.0 grant, and what was changed (repackaged,
`model/` and `schema/` pruned, `JSONUtil` redirected). Provenance of
third-party code is exactly the kind of thing that belongs in the source,
unlike our own history.

Vendor from the local `fix/timestamp-scalar-utc` tip rather than
`github/master`: it is what `0.3.1-SNAPSHOT` was built from, so the
vendored code is what QLive's tests already run against. Record both that
commit and the `github/master` it descends from.

Apache-2.0 grants no trademark rights (section 6). The rename removes the
question for package names; describing provenance factually is nominative
use and is fine.

## Step 1 -- QLive's own `JSONUtil`, as a forwarding facade

QLive uses a thin svenson facade and nothing else: `DEFAULT_GENERATOR` (25
sites), `DEFAULT_PARSER` (6), `OBJECT_SUPPORT` (3), `getClassInfo` (3),
`DEFAULT_UTIL` (3), `formatJSON`, `findAnnotation`. Not one reference to
`JsView`, `JsViewResolver` or `WebpackAssets` -- QLive uses none of what
spring-jsview is actually for.

The facade **aliases the spring-jsview statics rather than building new
ones**:

```java
public final static JavaObjectSupport OBJECT_SUPPORT =
    de.quinscape.spring.jsview.util.JSONUtil.OBJECT_SUPPORT;
```

A second `ObjectSupport` must not exist while domainql still uses the
first. `TypeAnalyzer.getClassInfo` does `holders.putIfAbsent(cls, holder)`
on a static map keyed by class alone, and `ClassInfoHolder` analyzes
lazily, so the losing holder never runs: a second support is not double
work, it is a support that is *silently ignored* for every class the other
one reached first. Nothing customizes `ObjectSupport` today --
spring-jsview's is a plain `JavaObjectSupport`, domainql has no
`setObjectSupport` call at all, and QLive's two (`PushMessageParser`,
`ConditionParser`) pass the shared instance in -- so two supports would be
harmless right now. They would also restore exactly the condition that
`DEFAULT_GENERATOR = new JSON(OBJECT_SUPPORT, '"')` exists to remove, and
stay harmless only until the first one gets configured. Per-type scalar
handling is heading that way.

Aliasing splits the work where the risk divides: all 24 import sites move
now and the change is obviously behavior-preserving, while the dependency
narrows to a single line.

## Step 2 -- absorb domainql

1. Vendor the sources at the `fix/timestamp-scalar-utc` tip and repackage
   to our namespace. One commit, no behavior change.
2. Prune `model/`, `schema/` and `DomainGenerator`, keeping `DomainQLAware`.
3. Redirect domainql's own 23 `JSONUtil` sites and its five `JsView*`
   references at the Step 1 facade.
4. **Then**, as a separate commit, flip the facade from aliases to real
   implementations -- `new JavaObjectSupport()` and
   `new JSON(OBJECT_SUPPORT, '"')`, carrying the reasoning in the comment
   with them. It has to follow step 3 rather than accompany it, so no
   commit in between has two supports. One line per static, because Step 1
   already moved every call site.
5. Move `DocsExtractor` to the build tooling; drop `javaparser-core` from
   the runtime classpath.
6. Regenerate `qlive-typedocs.json` and repoint the jOOQ generator strategy
   in `qlive/pom.xml`.

spring-jsview then disappears entirely -- a 46 KB jar reduced to one class
we own -- and the unpublished SNAPSHOT dependencies go with it.

## Step 3 -- one namespace

`com.dataciders` (215 files) and `@quinscape` (54 files) both go. Decided:

| | value |
| --- | --- |
| GitHub org | `qlivedev` |
| npm org | `qlivedev` |
| Maven groupId | `io.github.qlivedev` |
| Java packages | `io.github.qlivedev.*` |
| npm packages | `@qlivedev/qlive-ts`, `@qlivedev/qlive-codegen` |

One name, `qlivedev`, across GitHub, npm and the Maven namespace, and the
groupId and the Java package root are the same string. The unhyphenated
form is what makes that possible: a hyphen is legal in a groupId but
illegal in a Java package, so `qlive-dev` would have forced the two apart.
The Maven namespace is verified by creating a public repository under the
organization.

Ruled out along the way: `io.github.qlive.*`, which is the namespace
Sonatype auto-grants to the existing `Qlive` GitHub account holder, and
`dev.qlive.*` / `io.qlive.*`, which assert domains owned by others
(`qlive.dev` registered 2026-06-09, `qlive.io` free but not bought).

These four are independent of one another, and treating them that way is
the point. The long name appears only in a consumer's `pom.xml`
coordinate; everywhere a person reads or types the framework it is
`qlive`. Sonatype states that hyphens are fine in a groupId and that the
Java package need not match it, and OpenFeign already ships this exact
split -- groupId `io.github.openfeign`, packages `feign.*`.

**The short package root is the load-bearing choice, not the groupId.**
Renaming 215 files is the expensive, irreversible move: every consumer's
imports break if it is redone. A groupId is cheap by comparison -- publish
under a new coordinate later and old artifacts simply stay where they are
while consumers change one line. With packages at `qlive.*`, a future move
to a domain-based groupId costs no source changes at all. Welding the
hosting choice into every file as `io.github.qlive-dev.*` would throw that
away for nothing.

So the domain question does not have to be answered now, and `io.qlive`
as floated in `docs/design/module-distribution.md` stays open. Note that
`qlive.dev`, `qlive.org` and the `qlive` GitHub name are all already taken;
`qlive.io` and `qlive.com` looked undelegated when checked, but that was
inferred from missing DNS rather than from registry data.

The rename is free right now and stops being free at the first release.
Every package is `private: true` and nothing has been published, so it
costs a mechanical sweep and nothing else.

Three cautions:

- Everything published to npm is scoped under `@qlivedev`, including the
  private workspace packages that are never published. A mix of scoped and
  unscoped packages in one workspace is worse than the redundancy in
  `@qlivedev/qlive-ts`. Package names are otherwise unchanged, so the
  rename is purely a change of scope.

  One consequence to carry into `docs/design/module-distribution.md`: only
  organization members can publish into a scope, so a third-party module
  cannot be `@qlivedev/auth-module`. Modules from outside need their own
  names, and the marker block in their `package.json` is what identifies
  them as QLive modules -- which is what that design already relies on.
- `io.github.qlivedev` is verified manually, not automatically: the
  automatic grant at signup covers only the GitHub username. Create a
  temporary public repository under the org whose *name* is the
  verification key the Portal issues, then delete it once verified.
- Several files carrying the package names are generated --
  `qlive-typedocs.json`, `domain-typedocs.json`, the jOOQ output under
  `com.dataciders.qlive.testdomain`, the generated TypeScript. Regenerate
  those rather than rewriting them in place.

Keep the rename in its own commits, separate from the absorption and from
the `JSONUtil` work.

### The repository move

The remote is still `fforw/qlive-framework`, and
`qlive-doc/astro.config.mjs` points `site` at `quinscape.github.io` with
repository links at `github.com/quinscape/qlive-framework` -- neither of
which has ever matched. Moving the repository to `qlive-dev` settles all of
it at once, and unblocks the docs site: Pages needs a public repository,
which is now a decision rather than a constraint.

The Astro `base` derives from the repository name, so the move touches the
site config alongside the remote.

## Step 4 -- vendor babel-plugin-track-usage## Step 4 -- vendor babel-plugin-track-usage

The least urgent. It is 810 lines in two files, Apache-2.0, with exactly
one consumer here (`qlive-ts/src/vite/trackUsage.ts`, already behind a
`.d.ts` shim).

Given the size and the single wrapped consumer, vendoring it into
`qlive-ts` beats tracking it: it removes the last external dependency in
this group, and the shim means the change stops at one file's imports.
Until then, pin it to exactly `0.3.4` rather than `^0.3.4`, so the build is
reproducible.

## Non-goals

- Not touching svenson. It is Apache-2.0 and not part of this.
- Not rewriting behavior during the move. See above.

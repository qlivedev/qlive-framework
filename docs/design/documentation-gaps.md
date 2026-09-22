# Documentation gaps (design sketch)

Status: sketched, not started. Written 2026-09-16 out of a survey of the
hand-written half of `qlive-doc`.

What the user site is missing, page by page, and where each missing page
goes. Two of the gaps the survey found are not in here: push, which is
being written separately, and the editing story, which was closed the
same day by `how-to/enable-merging-for-a-type.md` and
`how-to/edit-rows-with-a-working-set.md`.

## Problem

The API quadrant is generated from the `qlive-ts` declarations and is
complete by construction. The other three are hand-written, and what is
missing in them is not evenly spread: the explanation quadrant covers
the concepts the framework was built around, and the how-to quadrant
covers the tasks those concepts made necessary. What neither covers is
the ordinary surface an application sits on -- a database it can run
against, a user it can log in, a table screen, a build that ships.

The shape of the hole is worth naming, because it decides the order
below. A reader can follow the documentation from "what is data
injection" to "how do I write a row back", and cannot get an application
to the point where any of it runs.

## 1. The tables and columns QLive requires

**What exists.** `MergeTables` names `app_version` and
`app_field_layout` as constants, deliberately not configurable, reached
through plain jOOQ names because the generated classes live in the
application. `AppUserDetailsService` and `DefaultPersistentTokenRepository` read a
user table and a remember-me table whose names are constructor arguments
-- `app_user` and `app_login` in `qlive-test` -- so those two are the
application's to name and the merge's two are not. `app_version.owner_id`
is a foreign key onto the user table, so the merge needs it as well. A
versioned type needs `version varchar(36)`.

**What a reader cannot find.** Any of it. There is no DDL in the
repository at all -- no migration, no schema dump -- and the only
statement of the layout is `docs/design/working-set-merge.md`, which is
internal and is a design document rather than a contract. A user who
turns on merging meets a runtime failure against a table nothing ever
told them to create.

**The page.** `reference/framework-tables.md`. The DDL for all four
tables, the `version` column convention, which feature needs which, and
which names are fixed (the merge's two) against which are arguments (the
user and remember-me tables). Reference rather than how-to: it is looked up while
writing a migration, not read through.

`how-to/enable-merging-for-a-type.md` currently carries the merge half
of that DDL inline, with a TODO saying so. It cuts back to a pointer
once this page exists.

**Open question.** Whether the DDL ships as a file as well -- a
`qlive/src/main/resources/schema/*.sql` an application can run or
reference from its own migration tool. A page can go stale against the
code; a file that the framework's own test schema is built from cannot.
That is a framework change rather than a documentation one, and it is
the better answer if the page would otherwise be a copy nobody
re-checks.

## 2. Authentication

**What exists.** `AppAuthentication` (with `current()`, the id every
merge records as owner), `AppUserDetails`, `AppUserDetailsService` over
`app_user`, `DefaultPersistentTokenRepository` for remember-me, and on
the client `config().auth`, `QuickLogin` and `Logout`.

**What a reader cannot find.** How to log a user in.
`how-to/secure-an-application.md` is about three Spring Security rules
that are easy to get wrong -- GraphQL-shaped errors, the login POST,
closing the dev endpoints -- and names none of the beans above. The
components have API entries and nothing that says when to reach for
them.

**The page.** `how-to/authenticate-users.md`, before
`secure-an-application` in the order: the `app_user` table, the
`UserDetailsService` bean, the login page (which is already half
written in `add-an-entry-point`), remember-me, and on the client how a
view reads who is logged in and what roles they hold.

The overlap with `add-an-entry-point` needs deciding rather than
duplicating. That page's login example is there to show a *second entry
point*, and the login page is merely the one every application has; the
auth page should link to it rather than repeat it.

## 3. The table screen

**What exists.** `useQueryDocument`, `update()` on a document,
`DomainTables`, `decompileFilter`, `sortFields`, the page size cap that
comes back on the config, `QueryConfigMetadataProvider`.

**What a reader cannot find.** The screen every internal application is
made of: a table, a page control, sortable columns, a filter form whose
state round-trips through a condition. Every piece is documented and
the assembly is not. `explanation/query-documents.md` explains the delta
model, and the closest thing to a worked table is a `<pre>` in the
overview.

**The page.** `how-to/build-a-table-screen.md`, in the client cluster
next to the working-set page. Inject a document, page it with
`update({offset})`, sort with `sortFields`, drive a filter form through
FilterDSL and read the term back with `findComponentNode()` or
`decompileFilter()`, and respect the applied config that comes back
rather than the one that was sent.

**Open question.** What `DomainTables` is for, in the documentation's
terms. It is exported and has an API entry, and the filter plumbing on
it is inert on purpose -- the replacement search is to be a client-side
FilterDSL expression. A page that presents it as the way to render a
table would be wrong by the time that lands. Either the page builds the
table by hand and mentions the component as a shortcut for the simple
case, or `DomainTables` gets its own short page once the search is
decided.

## 4. Getting started

**What exists.** A requirements list on the home page, a reference page
describing the `qlive-test` layout, and `how-to/wire-up-a-spring-
application.md`, which assumes the project already exists.

**What a reader cannot find.** What to install, in what order, and what
the resulting project looks like before any QLive concept enters. The
gap is felt immediately -- it is the first one a new reader hits -- and
it is the one most constrained by decisions that have not been made.

**The constraints.** A tutorial waits on application templating, which
is `module-templating.md` and not built. The `qlive-ts/vite` entry point
is on no API page on purpose, so a page here cannot spell out the
plugin's options. Whatever gets written now has to survive both.

**The page.** `reference/project-anatomy.md`: the modules a QLive
application is made of, what each build step produces and what consumes
it (jOOQ, `schema.graphql`, `types.d.ts`, query result types,
`track-usage.json`), and what `pnpm dev` actually runs. Descriptive
rather than instructive, so that it stays true when a template starts
generating the layout it describes, and so that it says nothing about
plugin options.

`reference/qlive-test-layout.md` is the neighbour to check against
first; some of this may belong in it rather than in a page of its own.

## 5. Production build and deployment

**What exists.** `ProdStaticAnalysisProvider` reads `track-usage.json`
off the classpath, where the Maven build puts Vite's output;
`ViteIndexController` serves the built `index.html` and hands the
emitted assets to static resource handling; the production build fails
at startup when the analysis is missing or its injections are wrong.

**What a reader cannot find.** How to ship. The pieces are named across
`overview`, `wire-up-a-spring-application` and `add-an-entry-point`,
each in service of a different point, and nothing puts them in the
order a deployment happens in.

**The page.** `how-to/build-for-production.md`: the frontend build into
the classpath, the profile, what is checked at startup and what that
failure looks like, the context path and the Vite base, and the dev
endpoints that have to be closed (which `secure-an-application` already
covers and should be linked to rather than repeated).

## 6. Styling

**What exists.** `docs/styling.md`, which is internal, and is linked
from `how-to/add-an-entry-point.md` by raw GitHub URL.

**What a reader cannot find.** It, in the place they are reading. The
content is user-facing -- `@layer qlive`, the import order that decides
the cascade, how an application overrides what the components bring --
and the link leaves the site to reach it.

**The move.** Into `qlive-doc` as `how-to/style-an-application.md` or
`reference/styling.md`, with `docs/styling.md` deleted rather than left
as a second copy. Which quadrant depends on what the file turns out to
be once it is read as documentation rather than as a note: a list of
layers is reference, "put your import after ours and here is why" is a
how-to.

## 7. Error handling

**What exists.** `ErrorBoundary` and `ErrorView`, a failing injection
being fatal for the page on purpose, `graphql()` rejecting on any
GraphQL error, `execute()` doing the same, and the 401/403 distinction
`GraphQLSecurityErrorHandler` preserves.

**What a reader cannot find.** What the user sees when something goes
wrong, and what the application is supposed to do about it. Three pages
each mention one piece in passing.

**The page.** Probably `explanation/when-things-fail.md` rather than a
how-to: the decisions are the interesting part -- why a missing
injection fails the page instead of rendering without it, why the
frontend has to be able to tell an expired session from a missing role,
what a boundary can and cannot catch when the failure happened before
the page was sent.

## 8. `i18n()` is a stub with an API entry

`i18n(tag, ...args)` returns `"[" + tag + "]"`. It is exported, it
appears on the startup API page, and its entry says "Undocumented",
which reads as an oversight rather than as a placeholder. Somebody will
build on it.

Two ways out, and the choice is not the documentation's: mark it
`@internal` so the generator drops it until there is a message
mechanism, or give it a doc comment that says plainly that it is a
placeholder returning its tag. The second is better if applications are
expected to call it now so that the call sites exist when it becomes
real.

## 9. Testing

There are no test utilities in `qlive-ts` for application authors -- the
package's own tests are vitest over its internals -- and no page says
anything about testing an application.

This is a feature gap that documentation cannot close: a view reads an
injection that normally arrives in the page, so testing one means
standing up a bootstrap. Automaton's `createMockedQuery` and
`evaluateMemoryQuery` are the prior art worth reading before designing
anything.

What can be written today is a short honest page saying what is
possible: the shape of the bootstrap a test has to provide, that the
FilterDSL and the working set are plain objects a test can drive
directly, and that there is no view-level harness yet. It is worth
writing even so -- silence reads as "nobody thought about it", and
`qlive-test` doubles as the structural template applications copy.

## 10. Types that are not tables

**What exists.** `DomainQLBuilder.objectType(Class)` registers a
hand-written class as a domain type backed by something selectable that
the code generator did not produce: a database view, a function returning
rows, any shape a `SELECT` has. The class carries that shape in JPA
annotations -- `@Table` for the table-like name and schema, `@Column` per
property, `@NotNull` for the non-null ones -- and `SumPerMonth` in the
`qlive-graphql` tests is the worked example.

What such a type does not have is foreign keys, so
`configureRelation(TableField, ...)` has nothing to resolve and cannot
describe its relations. `withRelation(RelationBuilder)` can:
`withPojoFields(sourcePojo, sourceFields, targetPojo, targetFields)` names
both sides by class and property instead, and is mutually exclusive with
`withForeignKeyFields()`, which looks an actual jOOQ foreign key up.
Everything the relation otherwise takes from a foreign key -- the source
and target field behavior, both object names, the id, the meta tags -- is
settable on the builder.

**What a reader cannot find.** Any of it. `objectType()` is named on no
page, `RelationBuilder` is named on no page, and `withPojoFields()` is
the only way a view-backed type gets a relation at all. The explanation
quadrant does not have the distinction either: `unified-domain.md` says
the schema comes from "the generated POJO types from the database, the
handwritten POJOs, and the GraphQL methods in the logic beans", which
covers both kinds of hand-written POJO in one phrase and separates
neither from the other.

**The page.** `how-to/expose-a-database-view.md`, how-to order 14. The
annotated class, where the `objectType()` call goes in the builder chain,
and a relation declared with `withPojoFields()` for a type with no
foreign key to offer. A task with a shape rather than something looked
up, so how-to rather than reference.

**Open question.** Whether the two kinds of hand-written POJO want
naming apart in `explanation/unified-domain.md` as well. Replacing a
generated type and standing a type up over a view are different jobs
that both arrive as "a hand-written POJO", and a reader with only that
page has one slot for two things.

## 11. `replace-a-generated-type.md` names the wrong registration route

Not a gap; an error on a page that exists, recorded here because nothing
else tracks those yet.

The page says to register the replacement "with `objectType()` after the
schema's own types". `objectType()` requires a
`jakarta.persistence.Table` annotation on the class it is handed, and
that annotation is not `@Inherited`, so a class extending a generated
POJO -- which is what the same page correctly requires -- does not carry
it. The call throws unless the replacement redeclares `@Table` itself,
which the page does not mention.

What the framework's own test does instead is register the replacement
as a logic bean return type: `OutputTypeOverrideLogic` returns
`beans.SourceSeven`, and `updateTableLookups()` repoints the table lookup
at it by simple name. That route needs no annotation and keeps the real
jOOQ table rather than a `DSL.table()` rebuilt from the annotation.

Fixing the page means deciding which route is canonical first. Both
appear to work if the annotation is redeclared, and they do not produce
the same `TableLookup`.

## Build order

1. **Framework tables** (1). Blocks running the software at all, and
   the merge how-to already has a TODO pointing at it.
2. **Authentication** (2). Blocks the same reader one step later, and
   the merge records an `app_user` id whether or not they got there.
3. **The table screen** (3). The most-asked question of the ones that
   have an answer today.
4. **Production build** (5) and **styling** (6). Both are collection
   and placement rather than new material.
5. **Project anatomy** (4). Cheap to write and cheap to be wrong, so it
   waits until the templating decision is closer.
6. **Error handling** (7), **i18n** (8), **testing** (9). Smaller, and
   each is partly a code decision.

Outside that order: **types that are not tables** (10) is not blocked by
anything and does not block anything, so it goes in whenever the relation
builder is fresh in mind. The `replace-a-generated-type.md` correction
(11) wants doing sooner than any of them -- a reader following that page
today hits an exception -- but it needs a decision rather than writing
time.

## Sidebar numbering

Pages place themselves with `sidebar.order` inside their quadrant, so
adding one at the end is a one-file change and inserting one in the
middle renumbers the pages after it. The how-to quadrant is at 13 after
the merge pages went in at 9 and 10. Every page above wants a middle
position, so each lands with a handful of frontmatter edits beside it --
cheap, and worth checking in the same commit so a stray duplicate order
never reaches the built sidebar.

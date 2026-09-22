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

**What exists.** `DomainQLBuilder.objectType(Class)` stands a domain type
up over something selectable that the code generator never saw: a
database view, a function returning rows, anything with the shape of a
`SELECT` result. There is no generated POJO and no jOOQ table behind it.
The hand-written class carries the shape in JPA annotations -- `@Table`
for the table-like name and schema, `@Column` per property, `@NotNull`
for the non-null ones -- and `objectType()` builds the table reference
from them with `DSL.table()`. `SumPerMonth` in the `qlive-graphql` tests
is the worked example.

What such a type does not have is foreign keys, so
`configureRelation(TableField, ...)` has nothing to resolve. The extended
relation builder is the answer:
`withPojoFields(sourcePojo, sourceFields, targetPojo, targetFields)` names
both sides by class and property, and is mutually exclusive with
`withForeignKeyFields()`, which looks an actual foreign key up.
Everything a relation otherwise takes from a foreign key -- the source
and target field behavior, both object names, the id, the meta tags -- is
settable on the builder. A view with relations is the case this exists
for.

**What a reader cannot find.** Any of it. `objectType()` is named on no
page, `RelationBuilder` is named on no page, and `withPojoFields()` is
the only way a view-backed type gets a relation at all.

**The pages.** Two, because the concept needs somewhere to live before
the task makes sense.

`explanation/database-views.md`, explanation order 4, after
`the-java-half.md`: what backs a domain type, why some types have no
generated POJO behind them, and why a relation that no foreign key
describes has to be declared instead of discovered. Inserting at 4
renumbers the five pages after it, which the sidebar section below
already prices; appending at 10 is cheaper and reads worse, a
domain-modeling concept sitting after push.

`how-to/expose-a-database-view.md`, how-to order 14: the annotated
class, where the `objectType()` call goes in the builder chain, and a
relation declared with `withPojoFields()` for a type with no foreign key
to offer.

**Both pages say "database view", never a bare "view".** A view in this
documentation is the component a URL renders --
`explanation/views-and-routing.md` opens by saying so, and there is an
API page of the same name. Spelled out, the term is unambiguous and is
what a reader holding a view will search for; a broader title naming
the abstraction instead would be accurate and findable by nobody. The
other shapes the mechanism takes, a function returning rows or any other
selectable, belong in the body of the explanation page.

**`unified-domain.md` stays as it is.** Decided 2026-09-22. It explains
the high-level concept, and view support is the wrong altitude for it.
The separation between the two kinds of hand-written POJO gets made on
the new explanation page and in #11 instead.

## 11. `replace-a-generated-type.md` sent the reader to the view API

Fixed 2026-09-22. Not a gap; an error on a page that existed, recorded
here because nothing else tracks those. Kept after the fix because it is
what the page's warning against `objectType()` argues from.

**What the feature is.** The table is real and the generator produced a
POJO for it. A hand-written subclass says the things the columns cannot:
a `@GraphQLComputed` field, or a `@GraphQLField(type = ...)` naming the
scalar for a Java type that is ambiguous about it -- the `long` that is
a currency amount, which nothing in the column can tell the schema. The
table, its relations and its foreign keys are untouched.

The class reaches the type registry as a logic bean return or parameter
type, and `updateTableLookups()` then repoints the table lookup at it by
simple name, keeping the generated jOOQ table.
`OutputTypeOverrideLogic` returning `beans.SourceSeven` and
`beans.TargetSeven` is the worked example, and its relation is still a
real foreign key through `withForeignKeyFields(SOURCE_SEVEN.TARGET)`.

**What the page says instead.** To register the subclass "with
`objectType()` after the schema's own types" -- which is #10's entry
point, for types that have no table. It does not work here for a
mechanical reason: `objectType()` requires `jakarta.persistence.Table`
on the class it is handed, that annotation is not `@Inherited`, and the
subclass the same page correctly requires does not carry it. The call
throws.

Redeclaring `@Table` on the subclass would get past the exception and is
the wrong fix: `objectType()` would then rebuild the table as a
`DSL.table()` from the annotation, discarding the generated table the
type is supposed to keep.

**What was done.** The page now names the registration route the feature
actually has -- the class in a logic bean's signature, usually the
document query's `@GraphQLTypeParam` -- and says why `objectType()` is
not it. A section on pinning a scalar joins the computed-field one, with
the `long`/`Long` default mapping that makes the pin necessary in either
direction.

The worked example carried the same error in its Javadoc, and a
redeclared `@Table` that nothing reads, so the `Qux` in `qlive-test`'s
`model/types` was corrected with it. A template application should not
hold the loaded gun the page warns about.

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

Outside that order: **database views** (10) is blocked by nothing and
blocks nothing. It is best written next while (11) is fresh, so the two
features get told apart on the pages as well as here -- (11) now says
what a hand-written POJO over a real table does, and (10) owes the
reader the other case. Its explanation page should land before its
how-to, which
is the one ordering constraint inside it.

## Sidebar numbering

Pages place themselves with `sidebar.order` inside their quadrant, so
adding one at the end is a one-file change and inserting one in the
middle renumbers the pages after it. The how-to quadrant is at 13 after
the merge pages went in at 9 and 10. Every page above wants a middle
position, so each lands with a handful of frontmatter edits beside it --
cheap, and worth checking in the same commit so a stray duplicate order
never reaches the built sidebar.

# Fixture mode: views running on given data

Status: fixture mode and the recorder are implemented in qlive-ts, and
qlive-doc runs views on fixtures (2026-10-02). Several fixtures on one
page, and the view's route, are [fixture-scope.md](fixture-scope.md).

## Problem

Showing QLive to someone takes a running Java server with a Postgres
database behind it. Bundling one into qlive-test or hosting it would
work, but it isn't worth it: what a hosted qlive-test shows is mostly a
datagrid, and QLive isn't the datagrid. What's worth showing is the
relationship between a view's code and what it renders, and a static
page can show that next to a live component.

Application authors hit the same wall. A view can't be rendered in
Storybook, a component test or any other simulated environment without
a server to answer its injections.

## Direction

A view runs **on a fixture** when QLive was initialized from a
bootstrap it was handed instead of one a server served. Its injections
come from the fixture. Query documents in it answer `update()` in the
browser, filtering, sorting and paging all the rows the fixture holds.
The view can't tell the difference and runs unchanged.

Where the fixture comes from doesn't matter to QLive: recorded from a
running application (see [Recorder](#recorder)), written by hand for a
test, or generated. The name is borrowed from testing, where a fixture
is the prepared data a test runs on. It says nothing about a server
and promises no faked behavior, and fixture mode fakes none.

This is library code in qlive-ts, not part of a demo, so that
applications can do the same in their own simulated environments.

Fixture mode reads data and nothing else:

- **No writes.** `mergeWorkingSet` and every other `graphql()` call
  reject with an error saying the view runs on a fixture. Faking merges,
  versions and conflicts would cost a lot and add little to what a
  page can show.
- **No push.** `subscribeToTopic()` registers nothing, so the socket,
  which opens on the first subscription, never opens. Document watches
  stay silent. Faking concurrent edits has the same cost-benefit problem
  as writes, and adds even less to what the UI can show.

An orchestrated page that wants to show a change does it outside the
library, by changing the rows it handed over and calling `update()`.

## The fixture

A fixture is a `QLiveBoostrap`, the type `init()` already takes:
`config`, `csrfToken`, `authentication` and the injections under
`data`, each in wire format. It differs from what a page gets live in
one way: **a query document injection holds every row**, not one page.
`rows` is the whole result and `rowCount` its length. Its `config`
stays the one the view injected with, so the view opens on its first
page as it does live.

Injections that aren't query documents are taken as they come.

`QLiveFixture` is that bootstrap plus the `route` of the view it was
recorded on. Injection ids start with it, see
[The view's route](#the-views-route).

## API

**`initFixture(fixture)`**, next to `init()` in `config.ts`.
Initializes from the fixture the way `init()` does and runs QLive on
it until the next `init()`. `isFixture()` says whether it does, for a
view that wants to hide what only a server makes work. Astro islands, Storybook decorators and tests
call this, because none of them go through `startup()`: each renders
one component into a root it got from somewhere else, while `startup()`
picks a view from the URL and renders into `#root`.

**`startup({fixture: bootstrap, ...})`** runs a whole application on
a fixture. It calls `initFixture` instead of reading `#root-data` or
fetching `/api/bootstrap`, and is otherwise the same.

The mode follows from how QLive was initialized. No flag gets passed
around, and nothing outside the places below asks for it.

## What changes inside qlive-ts

1. **`inject()`** converts as it does now: `convertResultFromServer` on
   first use, through the query's conversion map. For a query document
   it then calls `holdRows()` (in `localDocument.ts`), which sets a local
   source evaluating all the fixture's rows and replaces the document's
   rows with the page the injected config asks for. Everything after
   that is `localDocument()`'s existing path.
2. **`subscribeToTopic()`** returns an unsubscribe that does nothing,
   and registers and connects nothing.
3. **`graphql()`** rejects with the "running on a fixture" error
   before it gets to `fetch()`. A view that queries outside its injections shows
   up at once, not as a failed network request.

`evaluate.ts` already lists where the browser's evaluation differs from
the database's: collation, `likeRegex`, the clock. Those apply to
fixture mode as they do to `localDocument()`, and it adds no new ones.

## The view's route

An island or a story renders the view component directly, so
`location.pathname` is the page it sits on, not the view's route.
Settled in [fixture-scope.md](fixture-scope.md): injection ids carry
the route, a `ViewRoute` tells the view below it which route it is,
and `useInjection()` and `useRoute()` read it before the location. A
view that links to other views still gets dead links on a page that
isn't the application.

## Several fixtures on one page

`initFixture()` replaces whatever was loaded, which suits Storybook and
tests that start each case from scratch. A page with several views on
fixtures uses `addFixture()`, or a `FixtureScope` per view, which adds
each fixture next to the others; route-keyed ids keep them apart. One
fixture per route on a page, see
[fixture-scope.md](fixture-scope.md#limits).

## Recorder

Dev tooling: a "Record fixture" button that `startup()` pins to a
corner of every view with injections in dev mode, and that saves the
view's data as a fixture. The
fixture then has the shapes and wire formats the server really
produces, which a hand-written one can't promise.

The server turns track-usage's identifiers into query text and
variables in `InjectionService`, and keeps them there: the bootstrap
holds results only. The browser, though, sees every `useInjection()`
call with its `GraphQLQuery` and params while the view renders. So:

1. **`inject()` notes** each call's injection id, query and params in
   `fixture/notes.ts`, and `startup()` keeps a copy of the bootstrap as
   received, before `init()` changes its config. Dev only.
2. **On record**, the recorder takes that copy. For each query document
   injection it takes the full config the server sent back, sets
   `offset: 0, pageSize: 0` on the variable typed `QueryConfig`, converts
   the other params with `convertVariablesToServer`, runs the query raw
   through `graphql()`, stores the rows, and puts the injected config
   back. A query document no view read fails the recording.
3. **It hands the result over** as a JSON download named after the
   path, with the path in it and the CSRF token blanked.

Asked for `pageSize: 0`, the server returns everything unless the type
has a `maxPageSize` and the result is larger. The recorder compares
`rowCount` with the number of rows it got and fails, naming the type.
A cut-off result would page and filter wrong without any sign of it,
and demo data large enough to hit the limit is a mistake anyway.

The recorder lives in `fixture/recorder.ts`, which `startup()` loads
with a dynamic import in dev mode only, so it is a chunk of its own
that a production build never fetches.

A fixture carries the recording user's `authentication`. Record as a
demo login, not a personal one.

Later, maybe: the recorder could note `graphql()` calls the view makes
beyond its injections and save them with their responses, and fixture
mode would answer exactly those calls (same query, same variables)
instead of throwing.

## Showing a view in qlive-doc

`<QLiveDemo view="grid/Sorting"/>` on a page under
`qlive-doc/src/content/docs/demo/` runs the view in a `client:only`
React island, on the fixture at `src/demo/fixtures/grid/Sorting.json`,
with the view's source in a second tab. qlive-doc's README says how to
add one.

- **Modules:** qlive-doc compiles qlive-ts and the view from source,
  with the aliases qlive-test's dev server uses. Their dependencies
  resolve from the repository's install, so the docs workflow installs
  the repository too, and React is deduplicated so islands and views
  share one copy. Views and fixtures are lazy globs, so a page loads
  only its own.
- **Babel plugin:** nothing of it is needed at runtime. track-usage
  only reports calls to the server, and `i18n()` is a plain function.
- **CSS:** qlive-ts's stylesheet. The demo maps QLive's color tokens to
  Starlight's, so it follows the site's theme switch rather than
  `prefers-color-scheme`. qlive-test's own stylesheet styles `body` and
  stays out.

Each island renders its view in a `FixtureScope`, so a page can show
several demos, one per view. A fixture is mostly
the config's schema. The recorder sets the schema's descriptions to
null, which only DomainTables shows; that took the Sorting fixture from
72 to 57 KB compact, 44 of them still schema. Pruning the schema to the
types a view reaches would be the next step if size starts to matter.

## Not chosen

- **An embedded database in the jar, or a hosted instance.** Both
  need a real server. qlive-test is Postgres-bound (jOOQ dialect,
  JSONB, a `pg_dump` seed), and what a deployment shows is the UI, not
  QLive.
- **An in-memory GraphQL executor** behind a swappable transport.
  Without writes and push it has nothing to do that local documents
  over the fixture's rows don't already do.
- **Recording on the server,** with a dev endpoint that builds the
  bootstrap with `pageSize: 0`. The server knows the injections and
  their arguments too, but the browser has them already, and dynamic
  queries a view makes are visible only there.
- **Other names.** "Static" fits a demo page and nothing else.
  "Serverless" and "offline" are taken. "Mock" promises faked
  behavior. "Local" collides with localhost, and "detached" and
  "isolated" name the missing server rather than the data.

## First step

Done: the Sorting view of qlive-test runs on a recorded fixture under
Demos in qlive-doc, with its source next to it.

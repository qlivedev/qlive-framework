# Static mode: views without a server

Status: proposed. Written 2026-10-01. Nothing of it exists yet.

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

A view runs in **static mode** when QLive was initialized from a
recorded bootstrap instead of a server's. Its injections come from the
recording. Query documents in it answer `update()` in the browser,
filtering, sorting and paging all the rows the recording holds. The view
can't tell the difference and runs unchanged.

This is library code in qlive-ts, not part of the demo, so that
applications can do the same in their own simulated environments.

Static mode reads data and nothing else:

- **No writes.** `mergeWorkingSet` and every other `graphql()` call
  throw an error saying there's no server in static mode. Faking merges,
  versions and conflicts would cost a lot and add little to what a
  static page can show.
- **No push.** `subscribeToTopic()` registers nothing, so the socket,
  which opens on the first subscription, never opens. Document watches
  stay silent. Faking concurrent edits has the same cost-benefit problem
  as writes, and adds even less to what the UI can show.

An orchestrated page that wants to show a change does it outside the
library, by changing the rows it handed over and calling `update()`.

## The dump

A dump is a `QLiveBoostrap`, the type `init()` already takes:
`config`, `csrfToken`, `authentication` and the injections under
`data`, each in wire format. Two things differ from what a page gets
live:

- **A query document injection holds every row**, not one page:
  `rows` is the whole result and `rowCount` its length.
- **Its `config` stays the one the view injected with.** The recording
  has to fetch with `pageSize: 0` to get all rows, but that config must
  not end up as the document's. Otherwise the view would open on one
  page holding everything, instead of on its first page as it does live.

Asked for `pageSize: 0`, the server returns everything unless the type
has a `maxPageSize` and the result is larger. The recording catches
that by comparing `rowCount` with the number of rows it got and fails,
naming the type. A cut-off result would page and filter wrong without
any sign of it, and demo data large enough to hit the limit is a
mistake anyway.

Injections that aren't query documents are recorded as they come.

A dump carries the recording user's `authentication`. Record as a
demo login, not a personal one.

## API

**`initStatic(dump)`**, next to `init()` in `config.ts`. Takes a dump,
initializes from it the way `init()` does, and marks the config as
static. Astro islands, Storybook decorators and tests call this,
because none of them go through `startup()`: each renders one component
into a root it got from somewhere else, while `startup()` picks a view
from the URL and renders into `#root`.

**`startup({static: dump, ...})`** runs a whole application static. It
calls `initStatic` instead of reading `#root-data` or fetching
`/api/bootstrap`, and is otherwise the same.

The mode follows from how QLive was initialized. No flag gets passed
around, and nothing outside the three places below asks for it.

## What changes inside qlive-ts

1. **`inject()`** converts as it does now: `convertResultFromServer` on
   first use, through the query's conversion map. For a query document
   it then calls `setLocalSource` with an `evaluateQuery` over all the
   recorded rows, instead of `query.register(result)`, and replaces the
   document's rows with the first page under the recorded config, the
   same way `localDocumentOf()` builds its first result. Everything
   after that is `localDocument()`'s existing path.
2. **`subscribeToTopic()`** returns an unsubscribe that does nothing,
   and registers and connects nothing.
3. **`graphql()`** throws the "no server in static mode" error before
   it gets to `fetch()`. A view that queries outside its injections
   shows up at once, not as a failed network request.

`evaluate.ts` already lists where the browser's evaluation differs from
the database's: collation, `likeRegex`, the clock. Those apply to static
mode as they do to `localDocument()`, and it adds no new ones.

## One dump per page

QLive's config and injections are module state, and `initData()`
replaces all injections at once. Within one module instance, only one
dump is active at a time:

- **Storybook and tests** call `initStatic` per story or test, the same
  way `startup()` already calls `initPubSub()` to clear what a previous
  startup left.
- **Astro islands** on one page share their modules, so a second
  island's `initStatic` with a different dump would wipe the first
  one's injections. A page has one dump that covers every island on it,
  initialized once before any island renders. Two islands that need
  different data under the same query name use `__id`, as two
  injections in one live view do.

A React provider per island would remove that limit. It would also
change how `useInjection` finds its data in every mode, so it waits for
a case that needs it.

## Recording

To be decided. A dump should come from a real qlive-test run, not be
written by hand, so that it has the shapes and wire formats the server
really produces. Two candidates:

- **A dev-only server endpoint**, beside `TrackUsageDevController`,
  that builds a view's bootstrap the way `/api/bootstrap` does but runs
  query document injections with `pageSize: 0` and puts the view's
  config back afterwards. The server already knows each view's
  injections and their arguments, and wire format is its native output.
- **A client-side recorder** in a running view that re-executes each
  injected document's query raw through `graphql()`. It needs no new
  server code, but the client doesn't hold an injection's arguments
  other than `config`, so it would have to learn them first.

The endpoint looks like less work for a more faithful result.

## Not chosen

- **An embedded database in the jar, or a hosted instance.** Both
  need a real server. qlive-test is Postgres-bound (jOOQ dialect,
  JSONB, a `pg_dump` seed), and what a deployment shows is the UI, not
  QLive.
- **An in-memory GraphQL executor** behind a swappable transport.
  Without writes and push it has nothing to do that local documents
  over recorded rows don't already do.

## First step

One Astro island in qlive-doc rendering one qlive-test view, the Foo
grid, from a recorded dump, filterable and pageable, with the view's
code next to it.

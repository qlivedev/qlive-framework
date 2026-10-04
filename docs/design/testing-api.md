# Testing API: a bootstrap from a mock, a server for the rest

Status: proposed 2026-10-03, nothing built. Builds on
[fixture-mode.md](done/fixture-mode.md) and [fixture-scope.md](done/fixture-scope.md).
What application authors have so far is in the user docs: *Test a view
on a fixture* and the Storybook sketch.

## Problem

Fixture mode gives an application component tests for the read path: a
view renders on recorded data, and pages, sorts and filters in the
browser. Three things are still in the way.

- **Everything but reading is untestable.** `graphql()` rejects on a
  fixture and nothing is pushed. A save through `useMerge`/`WorkingSet`,
  a conflict, an error view, or a "rows changed elsewhere" banner can't
  be tested. QLive's own tests do it with `init()` and `initPubSub()`,
  both deliberately unexported, plus `respondWith()`, which stubs
  `fetch` with one canned answer for every request. None of that is
  available to an application, and the single answer is too crude for a
  view that sends several different requests.
- **A recording is all or nothing.** It carries the config, the login of
  whoever recorded it, and the data, all in one file. A test of
  role-dependent UI that wants `admin` in one case and a plain user in
  the next needs two recordings of the same data. A test that wants an
  empty list or one row has to edit wire-format JSON by hand. Every
  file repeats the config, about 50 KB of schema and meta.
- **Import order is a trap.** A view may run code on import that needs
  the config (`i18n()` at module level in `grid/Sorting.tsx`). A test
  therefore has to call `initFixture()` first and import the view
  dynamically, because a static import is hoisted above the call. The
  how-to documents this, but it's a workaround.

## Direction

**A fixture has two parts, and they come from different places.**

- **The bootstrap part:** `config`, `authentication`, `csrfToken`. The
  same for every view of an application. Built by a mock from the
  application's config.
- **The data part:** `route`, `description` and the injections under
  `data`. Specific to a view. Recorded, then varied by helpers.

qlive-doc already stores them separately: one shared
`src/demo/config.json`, data-only fixtures, merged at load time by
`QLiveIsland.tsx`. This makes that split the supported shape.

**A test server answers what fixture mode rejects.** Reads stay in the
browser as they are in fixture mode. What a test server is installed
for, `graphql()` sends to it instead of rejecting, and push frames come
from it. A test needs no second kind of initialization: fixture mode
plus a server is the live-ish mode.

Everything here goes in a **`@qlivedev/qlive-ts/testing`** subpath,
exported the way `/filter` is. Test code stays out of application
bundles, and the subpath can reach internals (`maskOf()`, the transport
seam below) without putting them on the main barrel.

The subpath doesn't depend on Vitest. Nothing in it uses `vi`, so it
works under Jest or a plain browser harness too.

## The bootstrap part

### Where the config comes from

The mock can make up a user and a token. It can't make up the config.

`schema.graphql` gives the schema, but the meta exists only on the
server, assembled by `MetadataProvider` beans: relations, unique keys,
`maxPageSize`, name fields, generic types, and application entries such
as qlive-test's `quickSearchTypes`. A config built without them
degrades without error: no name display, no merge identity, columns
that can't tell a relation from a plain object. A hand-maintained meta
drifts. So the config comes from the server, and the question is only
how.

**Decided: split off recordings.** A recording already holds the full
config. A qlive-codegen command takes one apart:

    add-fixture recording.json [--replace-config]

It writes `test/fixtures/config.json` the first time, and the fixture
without its config under `test/fixtures/<route>.json`. A recording whose
config differs from the stored one is refused, listing the fixtures to
record again, unless `--replace-config` is passed. That's
`qlive-doc/tooling/addFixture.mjs` as it is, generalized: a target
directory instead of qlive-doc's paths, and the view path taken from the
route as it already does. qlive-doc then uses the codegen command too.

This needs no new server endpoint, and the workflow stays "click Record
fixture, run one command". The recorder keeps downloading full
recordings, so a single recording remains self-contained for anyone
who just wants one file.

**Not now: a dev endpoint.** `/_dev/config` next to `/_dev/graphql`,
with a `generate-config` command, would refresh the config without
re-recording when the schema changes. That's one more thing on the
server and a second source the data can drift from. Add it if
re-recording after schema changes turns out to be the annoying part.

### `mockBootstrap()` and `mockAuthentication()`

    mockBootstrap({config, authentication?, data?, route?}): QLiveFixture
    mockAuthentication({login?, roles?, id?}): Authentication

`mockBootstrap()` assembles a fixture from the stored config, a mocked
authentication unless one is given, and a CSRF token. `data` defaults to
none and `route` to the empty route, so its result is what a setup file
initializes with. Its description says it's mocked.

`mockAuthentication()` defaults to a plain user, `ROLE_USER` with a
fixed id, the way `testAuthentication()` does in qlive-ts's tests. The
id is fixed because a condition built against the current user compares
it literally.

The internal `testAuthentication()` and `testCsrfToken()` become these;
qlive-ts's tests import them from the subpath like an application would.

### Setup file instead of dynamic imports

With the bootstrap part independent of any view, QLive can be
initialized before any test file runs, in a Vitest `setupFiles` entry:

    // test/setup.ts
    import {initFixture} from "@qlivedev/qlive-ts";
    import {mockBootstrap} from "@qlivedev/qlive-ts/testing";
    import config from "./fixtures/config.json";

    await initFixture(mockBootstrap({config}));

Setup files run before the test file's imports, so views are imported
statically again, and each test only adds its data with `FixtureScope`.
The Storybook preview does the same. The how-to's dynamic-import section
then becomes a note for setups without a setup file.

### Data-only fixtures

`addFixture()` today takes a full config, or the reduced one without a
schema (`noSchema()`), and checks it against the one in place. A
fixture with **no config at all** becomes valid where QLive is already
initialized: it brings data and nothing to check. `initFixture()` still
requires one, since there is nothing yet to run on.

`QLiveFixture` changes accordingly: `config`, `authentication` and
`csrfToken` become optional, and a fixture without them is the data
part. The type stays one type rather than two, because a full recording
and a data part go through the same calls.

### Changing the user

The authentication lives in the config, which a test initializes once.
A test of role-dependent UI needs another user without starting over.

**Open:** a `withAuthentication(authentication, () => ...)` that swaps
it for the duration of a callback, or a plain `setAuthentication()` the
test resets in `afterEach`. The callback can't leak into the next test,
but a render that outlives it (an effect, an update resolving later)
sees the user change under it. Decide when building, with a real
role-dependent view to test.

## Fixture variants

A recording gives one data set. Tests and stories want variants: an
empty list, one row, a long name that breaks the layout. Editing the
JSON by hand means knowing the wire format: the injection id is the
route plus the query name, the document sits under the query's alias,
and `rowCount` has to match.

    withRows(fixture, Q_FooList, rows): QLiveFixture

returns a copy with the document that `Q_FooList` injects holding
`rows`. The id and the alias come from the `GraphQLQuery`, `rowCount`
follows the rows, and the injected config stays, so the view opens the
same way. Rows are given in wire format, as they are recorded;
`fixtureRows(fixture, Q_FooList)` reads them back for a test to filter
or edit.

A variant shares its route with the recording, so it can't share a page
with it (`addFixture()` refuses two data sets for one id). That's fine
in a test file, which renders one at a time, and the reason the
Storybook sketch keeps one fixture per view.

## The test server

    const server = testServer();

installs a server for the page's fixture mode and returns it. From then
on `graphql()` hands its requests to the server instead of rejecting,
and the push connection is the server's. `server.close()` uninstalls
it; a test file that installs one in `beforeEach` closes it in
`afterEach`.

### The seam

The server hooks in at `graphql()`, not at `fetch`. `graphql()` holds
the `GraphQLQuery` it was given, so the server can match a request by
the query object instead of parsing the text, and nothing global is
stubbed. Push gets the same: `pubsub` opens its socket through a
transport the server can replace, instead of `new WebSocket(...)`
directly.

The fetch path itself stays covered by qlive-ts's own tests, which
keep stubbing `fetch` as they do now.

### Answering requests

    server.answer(Q_Bars, variables => result)
    server.answer(Q_Bars, result)
    server.answerMerge(({changes, deletions, mergeConfig}) => mergeResult)

`answer()` takes a `GraphQLQuery`, so a renamed query fails to compile
instead of silently not matching. The answer is the value of the
query's one top-level selection, as `execute()` yields it, in wire
format; the server wraps it in the response shape. A function gets the
variables as sent.

`answerMerge()` is the one write an application doesn't hold a query
for: the `mergeWorkingSet` mutation is the framework's own, sent by
`WorkingSet`. The answer gets the mutation's variables and returns a
`MergeResult`. Builders make the common answers
short:

    merged()
    conflict({type: "Bar", id, fields: [...]})

These are `mergeResponse()` and `conflictOn()` from qlive-ts's tests,
typed against the exported `MergeResult` and `MergeConflict`.

**A request nobody answers fails the request** with an error that names
the query and lists the queries the server does answer. Answering with
something made up would let a test pass on data the view never
asked for.

**Reads stay in the browser.** A query document's `update()` is
answered from the fixture's rows, as in fixture mode, without a server
answer. A test only answers what it is about: the save, the extra
query, the failure.

### What was sent

    server.requests: {query: GraphQLQuery, variables}[]
    server.requestsOf(Q_Bars)

for asserting on what a save sent, the way `sentVariables()` serves
qlive-ts's tests now.

### Push

    server.publishChange("Foo", id, ["name"])

delivers an `EntityVersion` change to every subscription that watches
it. The helper builds the frame: the field mask from the field names
with `maskOf()`, which stays unexported because its bit positions have
to match `FieldLayout` on the Java side, and the rest of the payload
with fixed values. This is what `publish()` does in
`useDocumentWatch.test.tsx` and `gridWatch.test.tsx`, without the test
knowing the frame format or the subscription ids.

`server.subscriptions` lists what the page watches, for a test that
checks a view subscribes and unsubscribes.

## What stays internal

- **The schema builders** (`scalar`, `object`, `field`, `NAMED`, …) and
  `testConfig`, `gridConfig`, `mergeConfig`. They build synthetic
  schemas so the framework can test itself in isolation. An application
  tests against its own config, and a hand-built one would only drift.
  They'd matter for a component library built on QLive, and there is
  none.
- **The `render()` helpers.** Ten copies across qlive-ts's tests, all
  generic `act()` plumbing that Testing Library provides.
- **`atViewRoute()`.** `FixtureScope` and `ViewRoute` are the public way
  to give a view its route.
- **`FakeWebSocket`.** Replaced by the push transport seam for
  applications; qlive-ts's own pubsub tests can keep it.

## Order of work

1. `QLiveFixture` with an optional bootstrap part; `addFixture()` taking
   a data-only fixture.
2. The `/testing` subpath with `mockBootstrap()` and
   `mockAuthentication()`; qlive-ts's tests switch to them.
3. `add-fixture` in qlive-codegen, qlive-doc switched to it, and
   qlive-test's `test/fixtures/` split into config and data. The
   Sorting test moves to a setup file and static imports, and the
   how-to follows.
4. `withRows()` and `fixtureRows()`.
5. The test server with the `graphql()` seam, `answer()`,
   `answerMerge()` and `requests`, with a qlive-test test of a save and
   a conflict on `bar/`.
6. The push transport and `publishChange()`, with a qlive-test test of
   the watch banner.

Each step is usable alone. 1–3 make fixtures cheaper, 4 makes them
flexible, and 5–6 open the write and push paths.

## Open questions

- **Changing the user**, see above.
- **Whether `testServer()` belongs to fixture mode only.** A page
  started live by `startup()` has a real server, and a test server
  there would mean two. Proposed: `testServer()` throws unless
  `isFixture()`.
- **Error answers.** A server error (`errors` in the response) and a
  transport failure test different paths in a view. Probably
  `server.fail(Q_Bars, error)` and `server.unreachable(Q_Bars)`; settle
  with step 5's tests.

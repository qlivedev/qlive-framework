# Fixture scope: several views on fixtures at once

Status: designed, not built. Written 2026-10-02, out of the one-demo
limit in qlive-doc. Extends [fixture-mode.md](fixture-mode.md) and
settles its open question, [The view's route](fixture-mode.md#the-views-route).

## Problem

`initFixture()` loads a fixture into module state. That limits a page to
one fixture, and qlive-doc to one demo per page:

- **Injections.** `initData()` replaces the whole injection map and
  clears what `inject()` already converted. A second `initFixture()`
  takes the first view's data away; on its next render the first view
  throws "No injection …" or picks up the other fixture's document.
- **Ids.** Merging the fixtures instead doesn't help, because an
  injection id is the query name and views share query names: eight
  grid views in qlive-test inject `Q_FooList`, each recorded with its
  own rows and config.

The fixture mode note suggested `__id` for clashing names. That works
for a view written for the page. It doesn't work for a demo, which runs
qlive-test's views unchanged.

A related problem is already open in that note: a view rendered in an
island sees the docs page's `location`, not its own route, so its links
and `routeOf()` come out wrong.

On the server one page renders one view, so ids that are unique within
a view are enough. The first place that breaks is a page that runs
several views on one module instance: a docs page with several demos, a
Storybook canvas with several stories, a component test that renders
two views.

## Direction

**An injection id carries the route of the view that injects it.**
`useInjection(Q_FooList)` in `app/grid/Sorting.tsx` reads
`grid/sorting/Q_FooList`, not `Q_FooList`.

Both sides know the route without being told:

- **The server** builds a page's injections from the view module that
  serves it, and only views may inject (`injectionsOutsideViews`). The
  route is that module's name below `QLivePaths.VIEW_ROOT`, lowercased,
  which is what `moduleForPath` reverses.
- **The client** computes the route from the location with `routeOf()`,
  the same function the router uses to pick the view.

No code is rewritten and nobody spells out a view name. A live page
needs nothing new, because its route is the location.

A page with several views has one location but several routes, so each
view's subtree can say which route it is. **`<ViewRoute route=…>`**
provides it, and both `inject()` and the router read it before falling
back to the location. That's the context the fixture mode note's
route question asked for: a view in an island links and routes as it
does in the application.

With route-keyed ids, fixtures stop clashing, so the page store can
**take fixtures in addition** instead of replacing them. A page loads
each fixture into one store, and each view finds its own injections by
its route.

Client-side navigation needs the same. A future `<Link/>` keeps the
config, fetches the new route's injections from `/api/update` and
renders the new view. With route-keyed ids those injections go in next
to the old route's instead of over them, and a view still rendering
during the transition keeps reading its own. Navigating back to a route
brings fresh data for ids already in the store, so navigation replaces
a route's injections as a whole, where `addFixture` refuses a clash
(see [API](#api)). Navigation isn't part of this design; it only
shouldn't have to undo it.

## The id

    route + "/" + (__id ?? queryName)

`grid/sorting/Q_FooList`; an `__id` replaces the query name as before,
so `useInjection(Q_FooList, {__id: "pinned"})` there reads
`grid/sorting/pinned`. The application root has the empty route, so a view
there would read `/Q_Foo`; no view lives there, since `/app/` renders
the landing page.

The route is normalized the way both sides already normalize it:
relative to the application base, no slashes at either end, and
lowercased invariantly (`toLowerCase()`, `Locale.ROOT`).

An entry point outside the Vite base, like `login`, has no injections
today. If one ever gets them, its route is its module name, which is
what `moduleForPath` maps it to.

## API

**`<ViewRoute route="grid/sorting">`**, exported from qlive-ts. Takes
the route of the view below it. `startup()` doesn't need it; a page
rendering a view anywhere but at its own URL does.

**`<FixtureScope fixture={...}>`** is what islands, stories and tests
use. It adds its fixture to the page store (below) and renders its
children inside a `ViewRoute` with the fixture's route. Rendering one
view on its fixture stays one wrapper:

    <FixtureScope fixture={sortingFixture}>
        <Sorting/>
    </FixtureScope>
    <FixtureScope fixture={filtersFixture}>
        <Filters/>
    </FixtureScope>

**`addFixture(fixture)`** is what `FixtureScope` calls, exported for
code that sets up a page without React. It adds the fixture's
injections to the page store and keeps the ones already there. The
config it takes as `initFixture()` does if QLive isn't initialized
yet, and otherwise checks it:

- **The other config came from a server:** throws. `graphql()` and
  `subscribeToTopic()` ask `isFixture()`, which is page-wide, so a page
  can't be live in one place and on a fixture in another.
- **Different type names in the schema:** throws, naming the first
  that differs. Fixtures from two applications would convert with the
  wrong schema without any sign of it.
- **One config is reduced:** a `noSchema()` page gets a config without
  the schema. The full one is kept, whichever came first.
- **Same id twice with different data:** throws, naming the id. Two
  fixtures for one route, recorded at different times, would otherwise
  give one view's data to the other.

The `authentication` and the blanked CSRF token come from the fixture
that initialized the config. Fixtures recorded as the same demo login
carry the same ones.

**`initFixture()`** stays: it replaces whatever was loaded, for
`startup({fixture})` and for a test that starts each case from scratch.

**`QLiveFixture.path`** becomes `route`, and required. The recorder
writes `routeOf(location.pathname)`. The pathname it writes now, e.g.
`/app/grid/sorting/`, can only be resolved with the recording
application's base, which a docs site doesn't have: qlive-doc's
`viteBaseUrl()` is `/qlive-framework/`.

## What changes

**qlive** (Java):

1. `InjectionService` prefixes every id with the route of the module
   it plans for. The duplicate check stays per module, and is now also
   per route.
2. `/api/update` returns ids in the same form. It's for client-side
   navigation, not built yet: a future `<Link/>` keeps the config,
   fetches the new route's injections there and renders its view.

**qlive-ts:**

1. `inject()` builds the id from the route (`ViewRoute`, else the
   location) and passes it to the store. It's framework-internal, so
   its signature is free to change.
2. `useInjection()` reads `ViewRoute` with `useContext` and passes the
   route to `inject()`. A view can't tell anything changed.
3. `initData()` keeps replacing everything for `init()`; an `addData()`
   next to it adds, and refuses an id that's already there with
   different data.
4. The router's `routeOf()` callers that ask about the current view
   take the route from `ViewRoute` when there is one.
5. The recorder writes `route`. The ids it writes are the ones the
   bootstrap carries, already route-keyed.
6. Tests that hand `init()` a bootstrap key its data by route, as the
   server will.

`data()` and `injectionSource()` are public reads that aren't hooks.
They take the full id, route included, and their doc comments say so.
No view in qlive-test calls either.

**qlive-doc:** `QLiveIsland` wraps its view in a `FixtureScope` and
drops its `active` guard and the "one demo per page" error. The
one-per-page notes in `QLiveDemo.astro`, qlive-doc's README and
fixture-mode.md go with it. The Sorting fixture is re-recorded.

## Limits

- **One fixture per route on a page.** Two islands showing the same
  view on different data clash, and `addFixture` says so. Two islands
  showing the same view on the same fixture share its documents: sort
  one, and the other sorts with it. Neither is a case a docs page has
  yet.
- **The schema in every fixture.** It's 44 of the Sorting fixture's 57
  KB, and three demos on a page download it three times. Accepted for
  now; pruning each fixture's schema to the types its view reaches is
  the next step fixture-mode.md already names. Recording the config
  once per application and the injections per view would load it once,
  but it changes the fixture format and is more than this calls for.

## Not chosen

- **A store per scope.** `FixtureScope` holds its own injection store
  and `useInjection()` reads from it, with ids left as query names.
  Isolates fully: the same view twice keeps separate documents. But it
  needs a store context that only fixtures use, keeps the route
  question open, and leaves ids that mean something only inside their
  scope. Worth revisiting if the same-route limit starts to matter.
- **One fixture per page with `__id`** (fixture-mode.md's earlier
  answer). Demo views run unchanged and have no `__id`.
- **The view's module name in the id.** The server knows it, but the
  client only does if track-usage rewrites every `useInjection()` call,
  and then every pipeline compiling a view has to run the plugin:
  Vitest, Storybook, qlive-doc's Astro build. The route is the same
  identifier and both sides already have it.
- **A module instance per island** (iframes). Isolates everything, at
  the cost of a document per demo, theme switching across frames and
  sizing them to content.

## Build order

1. Route-keyed ids, on both sides at once: `InjectionService`,
   `inject()`, the tests. One commit, since neither side works with
   the other's old ids. qlive-test runs as before.
2. `ViewRoute`, read by `inject()` and the router.
3. `addData()`, `addFixture()` and `FixtureScope`, with tests: two
   scopes on different routes over one query keep their own data, a
   clashing id throws, a schema mismatch throws, a live page throws,
   the full config wins over a reduced one.
4. The recorder writes `route`; re-record the Sorting fixture.
5. qlive-doc: `QLiveIsland` on `FixtureScope`, and a page with two
   demos to show it.
6. Doc updates: fixture-mode.md (its route question and the
   one-per-page section), the `initFixture()` and `useInjection()`
   doc comments, qlive-doc's README, regenerated API pages.

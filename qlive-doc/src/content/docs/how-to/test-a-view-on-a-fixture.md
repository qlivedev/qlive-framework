---
title: Test a view on a fixture
description: Recording a view's data and rendering the view on it in Vitest, without a server.
sidebar:
  order: 14
---

A view can run on a **fixture**: data it is handed instead of data a
server sends. It runs unchanged. Its injections come out of the fixture,
and its query documents filter, sort and page the rows the fixture holds,
in the browser. That makes a component test possible with no backend and
no database behind it.

Fixture mode only reads. `graphql()` rejects, so nothing is written, and
nothing is pushed. A test of a save or of a concurrent edit still needs
the server.

## Record the fixture

Start the application in dev mode and open the view. Every view with
injections gets a **Record fixture** button in the bottom right corner.
Clicking it saves the view's data as a JSON download named after the
route, `fixture-grid-Sorting.json` for `/app/grid/Sorting`.

Save it under `test/fixtures/`, mirroring the view's path:

```
frontend/
  src/app/grid/Sorting.tsx
  test/
    app/grid/Sorting.test.tsx
    fixtures/grid/Sorting.json
```

A few things to know about what gets recorded:

- **Every row, not one page.** The recorder queries each query document
  again for all its rows, so the test can page, sort and filter. The view
  still opens on the page it opened on live.
- **Up to `maxPageSize`.** Where a type limits how many rows one query
  returns, the fixture holds the first rows in the view's sort order and
  treats them as all there are. Its `description` says so: "Q_FooList
  with 500 of 1200 Foo rows".
- **Your login.** The fixture carries the authentication of whoever
  recorded it, so record as a test or demo user, not a personal one. The
  CSRF token is blanked.
- **The schema.** The fixture's `config` is the application's config at
  recording time. Record the fixture again when the schema changes, or the
  view and its data stop matching.

A fixture can also be written by hand or generated. It's a
`QLiveFixture`, the bootstrap a server sends plus a `route` and a
`description`. Recording is the easier way, because a recorded fixture
has exactly the shapes and wire formats the server produces.

## Write the test

The test renders the view inside a `FixtureScope`, which runs the view on
the fixture at the fixture's route. This is
`qlive-test/frontend/test/app/grid/Sorting.test.tsx`, shortened:

```tsx
import {act, type ComponentType} from "react";
import {createRoot, type Root} from "react-dom/client";
import {beforeAll, expect, it} from "vitest";
import {FixtureScope, initFixture, type QLiveFixture} from "@qlivedev/qlive-ts";
import sortingFixture from "../../fixtures/grid/Sorting.json";

(globalThis as {IS_REACT_ACT_ENVIRONMENT?: boolean}).IS_REACT_ACT_ENVIRONMENT = true;

const fixture = sortingFixture as unknown as QLiveFixture;

let Sorting: ComponentType;
let container: HTMLElement;

beforeAll(async () => {
    await initFixture(fixture);
    Sorting = (await import("../../../src/app/grid/Sorting")).default;

    container = document.createElement("div");
    document.body.appendChild(container);
    const root: Root = createRoot(container);
    await act(async () => root.render(
        <FixtureScope fixture={ fixture }>
            <Sorting/>
        </FixtureScope>
    ));
});

it("sorts all the recorded rows, not just the page on screen", async () => {
    await act(async () => container.querySelector<HTMLElement>(".qlive-grid-sort-header button")!.click());
    // ...compare the rows on screen with the fixture's, sorted
});
```

It needs a DOM, so set `environment: "jsdom"` in the `test` section of
`vite.config.ts`.

### Initialize QLive before you import the view

The view is imported dynamically, after `initFixture()`, and not with an
`import` at the top. Static imports are hoisted above everything else in
the file, so the view's module would run before QLive is initialized. Any
code it runs on import that needs the config then fails. `Sorting.tsx`
calls `i18n()` at module level for its sort options, for example.
`startup()` keeps the same order in the application: config first, then
the views.

`FixtureScope` adds the fixture again while rendering, which changes
nothing. It's still needed, because it provides the route the view reads
its injections by.

### Wait for updates with `act()`

Clicking a sort header or a pager button calls `update()` on the query
document. On a fixture, that resolves in the browser, but it's still
asynchronous. Wrapping the click in `await act(async () => ...)` waits
for the update and the render after it. `IS_REACT_ACT_ENVIRONMENT` tells
React that it runs in a test, which `act()` needs. A testing library such
as Testing Library does both for you, if you use one.

## Several views in one test file

The config and the injections are module state. Vitest runs each test
file in a fresh module context, so fixtures in different files don't meet.
Within one file, render each view in its own `FixtureScope`. Fixtures for
different routes share the page. Two fixtures for the same route can't,
and the second throws.

`initFixture()`, `addFixture()`, `FixtureScope` and the `QLiveFixture`
type are in
[Startup and configuration in the API reference](/qlive-framework/api/startup-and-config/).
For showing views in Storybook, see
[Show a view in Storybook](/qlive-framework/how-to/show-a-view-in-storybook/).

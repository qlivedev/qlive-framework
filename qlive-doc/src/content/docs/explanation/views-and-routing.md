---
title: Views and routing
description: How a URL becomes a view module.
sidebar:
  order: 5
---

A **view** is the component a URL renders. Views live under `src/app`, they
are the only modules that may call `useInjection()`, and they are the only
modules the server ever resolves a request to.

Anything else is a component. The distinction is not stylistic: see
[Injections](/qlive-framework/explanation/injections/).

## From URL to module

Views are registered from the glob you hand to `startup()`:

```tsx
await startup({
    views: import.meta.glob("./app/**/*.tsx"),
});
```

The registration drops the directory prefix all the paths share and uses
what is left as the view name. The route is that name, lower-cased:

| Module | View name | Route | URL (base `/app/`) |
|---|---|---|---|
| `./app/Home.tsx` | `Home` | `home` | `/app/home` |
| `./app/sub/View.tsx` | `sub/View` | `sub/view` | `/app/sub/view` |

The prefix is derived from the paths rather than configured, because your
glob pattern has already decided where views live.

Two views whose names differ only in case cannot both be registered -- the
route table could not tell them apart, and neither can the server, which
reports it rather than picking one.

## Rendering the view for a path

`startup()` does this for the URL the page was loaded with -- given
`views`, it resolves the path and renders what it finds, so an application
that only ever renders the view its URL names writes no routing code at
all.

The step itself is public, for an application that swaps views without a
page load -- `startup()` resolves to the React root to render into:

```tsx
const View = await loadViewForPath(location.pathname);
```

`loadViewForPath()` converts the path to a route, looks up the view name
and loads the module, returning its default export. It throws if no view
matches, listing the URLs that do exist. Its chunk is fetched at that
moment -- nothing loaded it up to that point.

The application root, `/app/` itself, addresses no view. What it renders
is the `root` option of
[`startup()`](/qlive-framework/api/startup-and-config/#startupoptionsroot)
-- a component, or the name of the view the root should behave as. `qlive-test` renders a
landing component of its own there.

## Navigating

QLive does not ship a router. What it gives you is the resolution step --
route to module -- and a way to refresh the injected data for a new path
without a full page load: `GET /api/update?path=...` answers with the
injections for that path alone, so an application that swaps views
client-side can ask for the data the new path needs. Those are the result
of actually running that path's queries, so the data is current as of the
call rather than as of the page load.

`appBase()`, `routeOf()`, `urlOf()` and the view loaders are
[Views and routing in the API reference](/qlive-framework/api/views-and-routing/).

Build links with `urlOf()` rather than by hand. It is the only thing that
knows both the servlet context path -- which Vite knows nothing about --
and the Vite base.

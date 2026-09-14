---
title: Router helpers
description: appBase(), routeOf(), urlOf() and the view loaders.
sidebar:
  order: 3
---

The resolution step -- route to module -- and the URL arithmetic around it.
How a URL becomes a view, and what QLive leaves to you because it ships no
router, is
[Views and routing](/qlive-framework/explanation/views-and-routing/).

```ts
import {appBase, routeOf, urlOf, loadViewForPath} from "@quinscape/qlive-ts";
import {loadView, viewNames, routeNames} from "@quinscape/qlive-ts";
```

| Function | |
|---|---|
| `appBase()` | the prefix the application is served under, with trailing slash. Context path + Vite base |
| `routeOf(pathName)` | `location.pathname` -> route, lower case, no leading or trailing slash. `""` for the root |
| `urlOf(route)` | route -> URL. `"sub/view"` -> `/app/sub/view` |
| `loadViewForPath(pathName)` | the view component for a browser path |
| `loadView(name)` | the view component by view name, e.g. `"sub/View"` |
| `routeNames()` / `viewNames()` | every registered route / view name, sorted |

Build links with `urlOf()` rather than by hand. It is the only thing that
knows both the servlet context path (which Vite knows nothing about) and
the Vite base.

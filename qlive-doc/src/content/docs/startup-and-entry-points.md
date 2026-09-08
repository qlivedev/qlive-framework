---
title: Startup and entry points
description: startup(), further entry points, and noSchema().
sidebar:
  order: 3
---

An **entry point** is an HTML file plus the module it loads. Every
application has at least one -- `index.html` loading `src/main.tsx` -- and
may declare more.

## The main entry point

```tsx {7-10}
import {findRoot, loadViewForPath, startup} from "@quinscape/qlive-ts";
import "@quinscape/qlive-ts/styles.css";
import "./style.css";

document.addEventListener("DOMContentLoaded", async () => {

    await startup({
        path: location.pathname,
        views: import.meta.glob("./app/**/*.tsx"),
    });

    const root = createRoot(findRoot());
    const View = await loadViewForPath(location.pathname);

    root.render(<React.StrictMode><View/></React.StrictMode>);
});
```

`startup(options)` returns a promise and does three things: registers the
view modules, obtains the bootstrap data, and initialises the config, the
converters and the injections from it. Nothing that reads the config may
run before it resolves.

### StartupOptions

| Field | Type | |
|---|---|---|
| `path` | `string` | required. `location.pathname` -- the browser's own, including the context path and percent-encoded the way the browser encodes it |
| `views` | `ViewModules` | optional. The map `import.meta.glob(pattern)` returns |

`path` has to be `location.pathname` verbatim. The server compares it
against the request URI of the production route, and the two have to agree
down to the character.

`import.meta.glob()` is resolved by Vite at build time, relative to the
file it appears in, and only accepts literal patterns -- which is why the
call lives in your application and not inside QLive. Do not pass `eager`:
without it the map holds loader functions, so each view becomes its own
chunk, fetched the first time it is actually needed.

`views` is optional. An entry point rendering one fixed page resolves no
routes and has nothing to register.

## Import the stylesheet before your own

```tsx
import "@quinscape/qlive-ts/styles.css";
import "./style.css";
```

QLive ships its CSS as a separate artifact rather than pulling it in from
the JS, so your application decides where it lands in the cascade.
Everything in it sits in `@layer qlive`, which anything unlayered overrides
regardless of specificity. See
[`docs/styling.md`](https://github.com/quinscape/qlive-framework/blob/main/docs/styling.md).

## Further entry points

Declare the HTML file in your Vite config's
`build.rollupOptions.input`, and serve it from a controller through the
same `VitePageRenderer` the application itself uses. It then boots exactly
like the application does: bootstrap embedded in production, fetched in
`vite dev`.

The login page in `qlive-test` is the worked example. It is deliberately an
application controller rather than part of QLive -- the login page is the
one page every application wants to look like its own.

```tsx
// src/login.tsx
import {noSchema, startup} from "@quinscape/qlive-ts";

noSchema();

document.addEventListener("DOMContentLoaded", async () => {
    await startup({path: location.pathname});
    // render the form
});
```

A plain HTML form is the right thing there: the POST is what authenticates
the session, and letting the browser submit it means the response -- a
redirect to the requested view, or back with `?error` -- is handled by the
browser too. The CSRF token that POST needs arrives with the bootstrap,
which is why the page is served through the renderer at all.

## `noSchema()`

```ts
import {noSchema} from "@quinscape/qlive-ts";

noSchema();
```

Call it at the top level of an entry module. It does nothing at runtime and
exists to be seen by the build's analysis. A path whose module declares it
gets the **reduced bootstrap**: context path, CSRF token and injections,
with an empty schema and empty meta data in place of the domain. On a large
domain that is the difference between shipping the whole introspection
result and shipping none of it.

Only for entry points that issue no queries and render no application view
-- a login page, an error page, a public landing page. Anything that
resolves a route or reads an injection with a query needs the schema and
will fail on its first type lookup without it.

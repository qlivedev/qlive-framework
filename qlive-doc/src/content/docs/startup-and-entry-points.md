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

```tsx {8-11}
import "@quinscape/qlive-ts/styles.css";
import "./style.css";

import {startup} from "@quinscape/qlive-ts";
import Landing from "./component/Landing";

document.addEventListener("DOMContentLoaded", async () => {

    await startup({
        views: import.meta.glob("./app/**/*.tsx"),
        root: Landing,
    });
});
```

That is the whole entry module. `startup(options)` returns a promise and
owns the page's start-up in full:

1. registers the view modules,
2. obtains the bootstrap data -- spliced into the document in production,
   fetched from `/api/bootstrap` in `vite dev`,
3. initialises the config, the converters and the injections from it,
4. awaits your `init` hook, if you gave one,
5. picks the component the current URL asks for and renders it into the
   `#root` element.

It resolves to the React `Root` it created -- to swap what is rendered
without a page load, to unmount in a test, or to render into yourself.
Given none of `views`, `root` and `render`, step 5 renders nothing at all:
an entry point that asked for initialization and nothing else gets the
initialized config and an empty root to do as it likes with.

Nothing that reads the config may run before the promise resolves --
which is why application code belongs in the view, not next to the
`startup()` call. By the time a view renders, its injections are already
there.

`startup()` reads `location.pathname` itself. There is no `path` option:
the value has to be the browser's own path, verbatim, because the server
compares it against the request URI of the production route and the two
have to agree down to the character.

### StartupOptions

| Field | Type | |
|---|---|---|
| `views` | `ViewModules` | optional. The map `import.meta.glob(pattern)` returns |
| `root` | `string \| FunctionComponent` | optional. What the application root, `/app/`, renders |
| `render` | `FunctionComponent<{config}>` | optional. The one component of an entry point that has no views |
| `init` | `(config) => Promise<void>` | optional. Runs after the config is initialised, before anything renders. Where `config.errorView` is set |
| `strictMode` | `boolean` | optional, default `true`. Wrap what is rendered in `React.StrictMode` |

### `views`

`import.meta.glob()` is resolved by Vite at build time, relative to the
file it appears in, and only accepts literal patterns -- which is why the
call lives in your application and not inside QLive. Do not pass `eager`:
without it the map holds loader functions, so each view becomes its own
chunk, fetched the first time it is actually needed.

Given `views`, `startup()` resolves the current URL through the route
table and renders the view it names. See
[Views and routing](/qlive-framework/views-and-routing/).

`views` is optional. An entry point rendering one fixed page resolves no
routes and has nothing to register -- see `render` below.

### `root`

The application root, `/app/` itself, addresses no view. `root` says what
happens there:

```tsx
root: Landing              // render this component at /app/
root: "Home"               // /app/ behaves as /app/home/
```

A component is rendered at the root URL and nowhere else. A **string** is
a view name: `startup()` replaces the history entry with that view's URL
before it resolves anything, so the root URL loads that view and the
address bar shows where the user actually is.

Without `root`, the root URL is resolved like any other, finds no view,
and renders the [error view](#the-error-view) naming the URLs that do
exist. QLive has no
landing page of its own to put there: what belongs at the root of your
application is yours to say, and `root` is where you say it.

This governs `/app/` alone. The site root, `/`, is redirected to
`/app/home` by the server, so a visitor arriving at the application lands
on a view without `root` being involved -- `/app/` itself is where
`vite dev` opens, and a URL someone types.

### `render`

An entry point with no views -- a login page, an error page -- names its
one component instead:

```tsx
await startup({
    render: ({config}) => <LoginForm csrfToken={config.csrfToken!}/>,
});
```

`render` is only consulted when `views` was not given. The initialised
config is passed as a prop for convenience; the exported `config()`
returns the same object anywhere below.

### `init`

Runs once the config, converters and injections are in place and before
anything is rendered. This is where a hook that has to be in effect for
the first render goes -- registering a converter that overrides a
built-in, say:

```tsx
await startup({
    views: import.meta.glob("./app/**/*.tsx"),
    init: async config => {
        registerConverter("Timestamp", myTimestampConverter);
    },
});
```

## The error view

`config.errorView` is the component QLive renders in place of a view it
could not produce -- a path no view answers, a view module that failed to
load. An initialised config always carries one, so it is replaced by
assignment rather than configured:

```tsx
await startup({
    views: import.meta.glob("./app/**/*.tsx"),
    init: async config => {
        config.errorView = MyErrorPage;
    },
});
```

It receives `ErrorViewProps` -- `{error: unknown}`, because that is what a
`catch` binding and a React error boundary both hand on. The built-in one
states the error and stops there; a page that belongs to your application
is yours to design.

`startup()` renders the view inside an `ErrorBoundary`, so a view that
throws while rendering takes down its own page and nothing more. The
boundary is exported for an application that renders into the root itself:

```tsx
import {ErrorBoundary, loadViewForPath, routeOf} from "@quinscape/qlive-ts";

const View = await loadViewForPath(path);

root.render(
    <ErrorBoundary key={routeOf(path)}>
        <View/>
    </ErrorBoundary>
);
```

A boundary keeps showing the error it caught -- that is what makes it a
boundary rather than a retry. The `key` is what clears it: change it with
what is rendered below, and React discards the failed instance along with
its error.

This is the one member of the config an application writes rather than
reads, which is why the `init` hook is where it goes: the hook runs after
the config exists and before anything is rendered, so the first thing
that can fail already finds your view.

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

    await startup({
        render: ({config}) => {

            const {csrfToken} = config;

            // Posting to the path without the query string keeps ?error out of
            // the next round-trip.
            return (
                <form className="login" method="post" action={location.pathname}>
                    ...
                    <input type="hidden" name={csrfToken!.param} value={csrfToken!.value}/>
                    <button type="submit">Log in</button>
                </form>
            );
        },
    });
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

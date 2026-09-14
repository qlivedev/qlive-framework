---
title: Startup
description: startup(), StartupOptions and noSchema().
sidebar:
  order: 2
---

An **entry point** is an HTML file plus the module it loads. Every
application has at least one -- `index.html` loading `src/main.tsx` -- and
may [declare more](/qlive-framework/how-to/add-an-entry-point/).

## The main entry point

```tsx {9-13} title="main.tsx"
import "@quinscape/qlive-ts/styles.css";
import "./style.css";

import {startup} from "@quinscape/qlive-ts";
import Landing from "./component/Landing";

document.addEventListener("DOMContentLoaded", async () => {

    await startup({
        views: import.meta.glob("./app/**/*.tsx"),
        root: Landing,
        init: async () => …
    });
});
```

`startup(options)` returns a promise and does three things: registers the
view modules, obtains the bootstrap data, and initializes the config, the
converters and the injections from it. 

### StartupOptions

| Field | Type | |
|---|---|---|
| `views` | `ViewModules` | optional. The map `import.meta.glob(pattern)` returns |
| `init` | `( config: QLiveConfig ) => Promise<void>` | optional init function to call after the config is initialized but before anything renders.|
| `render` | `FunctionComponent<{ config: QLiveConfig}>` | optional render function to use when no views are defined. |
| `root` | `string \| FunctionComponent<any>` | optional property to define what happens when the user invokes /app/. A string is redirected to the view with that name, a function component is rendered | 
| `strictMode` | `boolean` | Whether to wrap the views in React.StrictMode. Default is `true` | 

`ViewModules`, `QLiveConfig` and the rest of what those types are made of are
in
[Startup and configuration in the API reference](/qlive-framework/api/startup-and-config/).


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

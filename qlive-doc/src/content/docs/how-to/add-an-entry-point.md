---
title: Add an entry point
description: A second HTML page that boots the way the application does.
sidebar:
  order: 4
---

An **entry point** is an HTML file plus the module it loads. An application
starts with one, `index.html` loading `src/main.tsx`. A login page, an
error page or a public landing page is a second one: its own HTML file, its
own module, served outside the Vite base and booted the same way. The login
page in `qlive-test` is the worked example.

## What the main one looks like

```tsx {9-13} title="main.tsx"
import "@qlivedev/qlive-ts/styles.css";
import "./style.css";

import {startup} from "@qlivedev/qlive-ts";
import Landing from "./component/Landing";

document.addEventListener("DOMContentLoaded", async () => {

    await startup({
        views: import.meta.glob("./app/**/*.tsx"),
        root: Landing,
        init: async () => …
    });
});
```

`startup(options)` returns a promise and does three things: it registers
the view modules, obtains the bootstrap data, and initializes the config,
the converters and the injections from it. Every entry point below is a
variation on this one.

**Import QLive's stylesheet before your own.** QLive ships its CSS as a
separate artifact rather than pulling it in from the JS, so your
application decides where it lands in the cascade. Everything in it sits in
`@layer qlive`, which anything unlayered overrides regardless of
specificity. See
[`docs/styling.md`](https://github.com/quinscape/qlive-framework/blob/main/docs/styling.md).

## Declare the HTML file

Name it in `build.rollupOptions.input` in `vite.config.ts`, or `vite build`
never emits it:

```ts title="vite.config.ts"
build: {
    rollupOptions: {
        input: {
            main:  fileURLToPath(new URL("./index.html", import.meta.url)),
            login: fileURLToPath(new URL("./login.html", import.meta.url)),
        },
    },
},
```

## Serve it through the page renderer

Map it from a controller of your own, rendering through the same
`VitePageRenderer` the application is served with:

```java
@GetMapping(SecurityConfiguration.LOGIN_URI)
public ResponseEntity<String> login(HttpServletRequest request, CsrfToken csrfToken)
{
    return vitePageRenderer.render("login.html", request.getRequestURI(), csrfToken);
}
```

It then boots exactly like the application does: bootstrap embedded in
production, fetched from `/api/bootstrap` in `vite dev`. Pass the request
URI rather than the mapping constant -- that is what stays equal to the
`location.pathname` the frontend sends when it fetches its own bootstrap,
whatever context path the application is deployed under.

The controller is deliberately yours rather than QLive's. The login page is
the one page every application wants to look like its own, so it stays
where it can simply be rewritten.

## Write the entry module

A second entry module that is integrated into the QLive world can be useful e.g.
to create a separate admin area that is logically separated from the normal application
and is served from a `/admin/**` requiring ROLE_ADMIN or so.

```tsx title="src/another.tsx"
import {startup} from "@qlivedev/qlive-ts";

document.addEventListener("DOMContentLoaded", async () => {
    await startup({
        views: import.meta.glob("./app/**/*.tsx"),
        root: () => <ViteDevHome quickLoginUsers={ quickLoginUsers }/>
    });
});
```
We define the views as vite meta glob import that reads all *.tsx below the `app/` directory. Every .tsx is a view, other
files like query definitions can live there if they have a .ts extension.

Since all our views are in `./app/**`, the system does not know what to render for `/app/`. We can either define the name
of the view to use for root with an client-side redirect, or we can define a component that is rendered there. The example
application uses it to have a dev starting page with `<QuickLogin/>` buttons.

Every option `startup()` takes is
[Startup and configuration in the API reference](/qlive-framework/api/startup-and-config/).


### noSchema entry-point

An entry-point that does not query anything and that does not need to know about the GraphQL schema can use a 
`noSchema` declaration to get a simplified boostrap injection. You still might need the boostrap to know e.g. which CSRF 
Token to send to submit your forms or know which user is logged in with which roles.

See [noSchema() in the API reference](/qlive-framework/api/startup-and-config/#noschema) for details on the reduced bootstrap.

```tsx title="src/simplified.tsx"
import {noSchema, startup} from "@qlivedev/qlive-ts";

noSchema();

document.addEventListener("DOMContentLoaded", async () => {
    await startup({
        render: ({config}) => (
            <MyComponent config={ config }/>
        )
    });
});
```

No `views` option: the page resolves no routes. If we want it to render we can define a function component with `render`.
Otherwise, startup will resolve after initialization.

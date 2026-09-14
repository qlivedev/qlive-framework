---
title: Add an entry point
description: A second HTML page that boots the way the application does.
sidebar:
  order: 3
---

An application starts with one entry point, `index.html` loading
`src/main.tsx`. A login page, an error page or a public landing page is a
second one: its own HTML file, its own module, served outside the Vite base
and booted the same way. The login page in `qlive-test` is the worked
example.

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

```tsx title="src/login.tsx"
import {noSchema, startup} from "@quinscape/qlive-ts";

noSchema();

document.addEventListener("DOMContentLoaded", async () => {
    await startup({path: location.pathname});
    // render the form
});
```

No `views` option: the page resolves no routes. `noSchema()` keeps the
domain out of the bootstrap this page gets -- see
[Startup](/qlive-framework/reference/startup/) for what that leaves and
when it is not allowed.

## Let the browser submit the form

A plain HTML form is the right thing on a login page: the POST is what
authenticates the session, and letting the browser submit it means the
response -- a redirect to the requested view, or back with `?error` -- is
handled by the browser too. The CSRF token that POST needs arrives with the
bootstrap, which is why the page is served through the renderer at all.

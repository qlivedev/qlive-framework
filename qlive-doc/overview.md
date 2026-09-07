# Overview

QLive is a full-stack framework with a Java half and a TypeScript half. The
Java half is `qlive`, a Spring Boot library built on
[DomainQL](https://github.com/quinscape/domainql) and jOOQ. The TypeScript
half is `@quinscape/qlive-ts`, a React library. They are two halves of one
thing, not a server and a client that happen to talk.

## The idea

An application's views declare the data they need, in the view, once:

```tsx
export default function Home() {
    const foos = useInjection(Q_Foo, {config: {pageSize: 5}});
    // ...
}
```

The frontend build analyses that call statically and records it. The server
reads that analysis, so by the time a request for the page arrives it
already knows which queries that page runs. It runs them and ships the
results **inside the HTML document**. The page arrives with its data in it;
nothing fetches anything after the first render.

That is the central trade of the framework. What it buys is a page that is
complete when it paints. What it costs is a constraint: a `useInjection()`
call has to be readable at build time. See
[Injections](injections.md) for what that rules out.

## How a page is served

**In production**, `ViteIndexController` serves the built `index.html` for
any route below the Vite base. `VitePageRenderer` splices the bootstrap
data -- config, CSRF token, and the injections for that path -- into the
empty `#root-data` placeholder script tag the template carries.
`startup()` reads it out of the document.

**In `vite dev`**, nobody touches that placeholder: the dev server serves
its own `index.html`. The placeholder stays empty, and `startup()` falls
back to fetching the same data from `/api/bootstrap?path=...`. The page
boots identically either way; only where the bootstrap came from differs.

The static analysis reaches the server by two different routes for the same
reason:

| | production | `vite dev` |
|---|---|---|
| Analysis | `track-usage.json` written by `vite build`, read off the classpath | POSTed to `/_dev/track-usage` by the Vite plugin as you edit |
| Provider bean | `ProdStaticAnalysisProvider` | `DevStaticAnalysisProvider` |
| Missing analysis | fails at startup -- a build error | answers "not ready"; the frontend retries |

Consumers never see the difference: both are `StaticAnalysisProvider`.

## What the server knows about the frontend

Three things, and they are worth naming because they are the seams:

- **Which module serves a path.** Derived from the analysis and from the
  path conventions in [Application layout](application-layout.md).
- **Which queries that module injects, and with what parameters.**
  Recorded by the build's track-usage analysis from the `useInjection()`
  call itself.
- **Whether that module declared `noSchema()`.** Decides whether the page
  gets the full GraphQL schema or a reduced bootstrap.

Nothing else. In particular the server never sees your components, only the
entry point or view a request resolves to.

## The dev loop

`pnpm dev` runs the Spring Boot backend and the Vite dev server together.
On top of the usual HMR, one extra thing happens: as you edit a module
holding a `GraphQLQuery`, the plugin pushes the analysis to the backend,
which parses the query against the live schema and **writes the generated
result type back into your source file**. See
[Queries and types](queries-and-types.md).

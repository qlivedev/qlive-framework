---
title: QLive
description: Documentation for building applications on the QLive framework.
template: splash
sidebar:
  hidden: true
hero:
  tagline: One domain, from the database to the browser
  actions:
    - text: Read the overview
      link: /qlive-framework/explanation/overview/
      icon: right-arrow
    - text: View on GitHub
      link: https://github.com/qlivedev/qlive-framework
      icon: external
      variant: minimal
---

Your domain is one thing. It starts in the database and reaches the browser
without being translated on the way.

## Defined once

A type is defined once, and everything downstream knows it -- the database,
the Java code, the TypeScript in the browser. Nothing is hand-mapped between
the two sides. The common language is a GraphQL schema, and you do not write
it: it comes from your tables and your server code.

Filters are the same story. One condition, written once, runs as SQL against
the database or as a predicate over Java objects -- in the semantics of
wherever it lands, rather than a lowest common denominator.

## The data is already there

A view's query is a static declaration in that shared language, so the
server can tell what a page needs before the page exists:

```tsx
export default function Home() {
    const foos = useInjection(Q_Foo);
    // ...
}
```

It runs the query and puts the result in the page. Nothing fetches it, and
there is no loading state to render. Components get simpler for it, and
their tests more so: there is no fetch to mock and nothing to wait for.

If React Server Components are your reference point: the same idea, for
teams that want a Java server.

## Requirements

 * Java 25
 * pnpm
 * vite
 * React 18
 * Spring Boot
 * jOOQ

## Where to look

Start with the [Overview](/qlive-framework/explanation/overview/), which
explains what the framework does and how a page reaches the browser.

The rest is grouped by what you came for:

- **Explanation** -- what QLive does and why it is built that way. Meant to
  be read through, in the order it is in.
- **How-to guides** -- one task at a time: wiring up a Spring application,
  refreshing the schema after a domain change, adding an entry point.
- **Reference** -- the application layout, and the artifacts the codegen
  writes. Meant to be looked up, not read.
- **API** -- every export of `@qlivedev/qlive-ts`, generated from the
  declarations themselves.

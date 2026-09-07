# QLive documentation

Documentation for the **framework user** -- someone building an application
on QLive, not someone working on QLive itself.

Internal development documentation stays in `docs/`: design sketches for
ideas not yet realized, and notes aimed at maintainers. Nothing in this
folder is for maintainers; if a page here explains how QLive works
internally, it is because an application author has to know it.

Plain Markdown for now, linked from the repo README. The intent is to grow
a build of some kind over it later -- most likely GitHub Pages -- so pages
are written to stand on their own and link to each other by relative path.

There is a second half of the framework-user documentation that does not
live here: what gets generated into a new application alongside the
template extracted from `qlive-test`. Some of it will be inherited from
these pages (the pnpm/Maven setup, the dev loop), some of it will be
specific to the generated application. That half waits for the templating
command.

## Pages

Read in this order the first time:

1. [Overview](overview.md) -- what QLive is and how a page reaches the
   browser
2. [Application layout](application-layout.md) -- the folder structure and
   the handful of constants both halves have to agree on
3. [Startup and entry points](startup-and-entry-points.md) -- `startup()`,
   further entry points, `noSchema()`
4. [Views and routing](views-and-routing.md) -- how a URL becomes a view
   module
5. [Queries and types](queries-and-types.md) -- `GraphQLQuery`, generated
   result types, `types.d.ts`, converters
6. [Injections](injections.md) -- `useInjection()` and the rules around it
7. [Query documents](query-documents.md) -- paging, sorting, `update()`
8. [Filter DSL](filter-dsl.md) -- building conditions and sort fields
9. [Server setup](server-setup.md) -- the Spring beans an application
   wires up

Styling is documented in [`docs/styling.md`](../docs/styling.md) for the
moment. It reads as framework-user documentation and is a candidate to move
here, but it has not been moved.

## Status

Written against the repo as of 2026-09-07. QLive is pre-release: nothing is
published to a registry yet, and `qlive-test` is both the integration test
target and the source the application template will be extracted from. Where
a page describes something that is not settled, it says so rather than
inventing a story.

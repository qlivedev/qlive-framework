---
title: Regenerate from the schema
description: Refreshing schema.graphql after a domain change, and the types that come off it.
sidebar:
  order: 5
---

Every TypeScript type the frontend has for the domain is generated from
`schema.graphql` -- the domain types in `types.d.ts`, and the result type
next to each query. A stale `schema.graphql` is therefore not an error
anyone sees: it is wrong types in checked-in source.

So after anything that changes the schema -- a column, a logic bean method,
a new type in a `@GraphQLTypeParam` -- refresh it and regenerate.

## Refresh `schema.graphql`

Two ways, from a running backend:

- **your IDE's GraphQL plugin**, pointed at the backend, which is what
  `qlive-test`'s own file was written by;
- **the codegen CLI**, for when you have no such plugin:

  ```bash
  generate-schema http://localhost:8080 schema.graphql
  ```

  An introspection query and `printSchema`, nothing besides. It reads
  `/_dev/graphql` -- unauthenticated and CSRF-exempt, and
  [refused outside the dev profile](/qlive-framework/how-to/secure-an-application/),
  so the backend has to be running one.

**Pick one and stay with it.** Both produce the same schema, but not the
same file: printers disagree about indentation and about how a description
is quoted. Neither shape is the right one, and nothing here checks which
you used -- but two of them alternating rewrite the whole file back and
forth, and a real change is then a needle in a few hundred lines of
reformatting. Changing your mind is a one-time reformat; do it on its own.

Neither belongs in `pnpm generate`. That has to keep working on a fresh
checkout with no backend running, which is exactly when you want the types
regenerated from the schema you already have.

## Regenerate the TypeScript

With the backend no longer needed:

```bash
pnpm generate
```

In `qlive-test` that is `generate-ts` and `generate-query-types` together
-- the domain types and every query's result type, from the schema you just
refreshed. A schema change and the query types it invalidates are one step
on purpose.

A dev server running through it all generates the query types by itself, on
each save. It never writes `types.d.ts`, so `pnpm generate` is still the
step after a schema change.

## Read the diff

Everything generated here is written into your source and checked in, so
the diff is the review. A query that no longer fits the schema shows up as
a result type that lost a field, or -- if it cannot be typed at all -- is
reported by name and skipped, which `generate-query-types` exits non-zero
for.

See [Generated artifacts](/qlive-framework/reference/generated-artifacts/)
for what they contain, and for the rules a query has to follow to get one.

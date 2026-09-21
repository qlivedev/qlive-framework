---
title: Generated artifacts
description: The rules a query has to follow, and what the codegen writes from it.
sidebar:
  order: 2
---

Every TypeScript type the frontend has for the domain is generated from
`schema.graphql`: the domain types in `types.d.ts`, and a result type next
to each query. This page is what those artifacts contain and what the
generator requires of your source. Refreshing them is
[Regenerate from the schema](/qlive-framework/how-to/regenerate-from-the-schema/).

GraphQL queries complicate the picture, because they select from types that
claim to have all kinds of fields the selected data does not have. So the
correct TypeScript type for a query is derived rather than written, and the
rules below are what makes that derivation possible.

## Rules a query has to follow

```ts {11-24} title="src/app/Q_Foo.ts"
import {GraphQLQuery, QueryDocumentMethods} from "@qlivedev/qlive-ts";
import {AppUser, Foo, FooDocument} from "../types";

// generated
export type Q_FooResult = Pick<FooDocument, "type" | "config"> & {
    rows: Array<Pick<Foo, "id" | "name" | "description"> & {
        owner: Pick<AppUser, "id" | "login">
    }>
} & QueryDocumentMethods<Q_FooResult>

export const Q_Foo = new GraphQLQuery<Q_FooResult>(
    // language=GraphQL
    `query Q_Foo($config: QueryConfig!) {
        queryFooDocument(config: $config) {
            type
            config
            rows {
                id
                name
                description
                owner { id login }
            }
        }
    }`
)
```

| | |
|---|---|
| **Module scope, exported const** | `export const <Name> = new GraphQLQuery(...)`. The name identifies the query and doubles as the GraphQL operation name, so it has to be a GraphQL name: `[_A-Za-z][_0-9A-Za-z]*`. Upper case first is the convention -- query names are type-like. |
| **The source is a literal** | A query assembled at runtime is invisible to the analysis, cannot be injected, and gets no generated type. |
| **One query method per query** | Every query defines exactly one top-level selection. |
| **No fragments** | Neither spreads nor inline. Both the generated result type and the client's conversion map would silently miss the fields a fragment contributes, so a query using one is refused where it is declared. |
| **Naming** | Either the module is named after the query -- `app/Q_Foo.ts` exporting `Q_Foo`, which is what the generated result types assume -- or the operation is named like the identifier, which covers queries collected in a shared module. |

`T` in `GraphQLQuery<T>` is what **one execution** yields: the value of the
query's single top-level selection, unwrapped. Aliasing the one method
changes nothing.

Only one method per query looks like a loss at first. The point of the
restriction is effective data fetching, and with the injection mechanism it
does not matter how many queries a view uses -- the server runs them in one
go anyway. Why the literal and the module scope are not negotiable is
[Injections](/qlive-framework/explanation/injections/).

Queries are not required to live in any particular directory. `src/app/` is
where `qlive-test` puts them, next to the views that inject them, but the
analysis resolves the identifier through the view's imports rather than by
location.

## Generated result types

`Q_FooResult` above is **generated**, not written by hand. The generator
parses the query against `schema.graphql` and patches the result type into
the module at the source offsets the track-usage plugin recorded, along
with the imports it needs: the domain types it picks fields out of, and
`QueryDocumentMethods` when the query selects a document.

It runs from two places, and it is the same code in both:

- **the track-usage plugin**, while a dev server runs. Once at startup, so
  a type someone else left behind is caught before you trip over it, then
  per save. The loop is: edit the query, save, and the type next to it
  updates. No backend involved.
- **the codegen CLI**, when you ask:

  ```bash
  generate-query-types schema.graphql src
  ```

  `qlive-test` wires it into `pnpm generate` behind `generate-ts`, which is
  where it belongs: a schema change and the query types it invalidates are
  one step.

Either way the type is written into your source, so keep it checked in.
`vite build` does not generate it -- the dev server and `pnpm generate` do,
and a diff after either is how you notice a query that no longer matches
the schema.

The plugin generates whenever `schema.graphql` is next to your
`vite.config.ts` and `@qlivedev/qlive-codegen` is installed. Point it
elsewhere, or turn it off, with the plugin's `queryTypes` option. The
`indexes` option of babel-plugin-track-usage has to stay on (it is by
default) -- without the source offsets there is nowhere to patch.

So a new query is written like this, and nothing else:

```ts title="src/app/Q_Bar.ts"
import {GraphQLQuery} from "@qlivedev/qlive-ts";

export const Q_Bar = new GraphQLQuery(
    // language=GraphQL
    `query Q_Bar($config: QueryConfig!) {
        queryBarDocument(config: $config) { rows { id name } }
    }`
)
```

The type argument, the result type and the imports are all added when the
dev server sees the file -- creating it is enough, saving it again is not
needed. Domain types come from `src/types.d.ts`, imported relative to
where the query sits -- `queryTypes.typesModule` and the CLI's third
argument say so if yours live elsewhere.

Names are only ever added to an import, never taken out: nothing here can
tell an import a selection stopped needing from one your own code still
uses.

A query the generator cannot type -- one that does not fit the schema -- is
reported by name and skipped. The other queries still get theirs, so a
query halfway through an edit does not hold up the module you are looking
at. `generate-query-types` exits non-zero when any query was skipped.

## `types.d.ts`

The TypeScript view of your domain, generated from the schema by the
codegen CLI:

```bash
generate-ts schema.graphql src/types.d.ts
```

`qlive-test` wires it up as `pnpm generate`, together with the query result
types above. The output declares one type per GraphQL type, the `*Document`
types derived from `QueryDocument<T>`, and a `DomainObject` union of the
schema's object types.

Nothing typechecks a generated `.d.ts` in a normal build -- applications
set `skipLibCheck`, and should -- so regenerate it when the schema changes
rather than editing it.

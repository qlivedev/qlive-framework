---
title: GraphQL and Typescript
description: GraphQLQuery, generated result types, types.d.ts and converters.
sidebar:
  order: 5
---
Since we're using both Typescript and GraphQL as basis for our project, we tried to unite them as much as we could. In 
general, we generate Typescript types from the GraphQL schema. GraphQL queries however introduce another complication 
because they are all about selecting from types that claim to have all kinds of fields the selected data actually does
not have.

So we introduced a way to automatically derive the correct Typescript types for a GraphQL Query. The standard we use
here is derived both from the needs of Typescript and the injection mechanism.

## Query rules

The standard is that every query is defined in its own file and that the exported name matches the internal query name.

Also, every query is allowed to only defined one query method. This might seem like a loss at first, but the main reason 
for that is effectively fetching data and with the injection mechanism it does not matter how many queries we use since 
it all is done in one go on the server anyway. 

### Query Example

```ts {11-24} title="src/app/Q_Foo.ts"
import {GraphQLQuery, QueryDocumentMethods} from "@quinscape/qlive-ts";
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

Two rules the analysis depends on:

- **`export const <Name> = new GraphQLQuery(...)` at module scope.** The
  name is how the query is identified, and it doubles as the GraphQL
  operation name, so it has to be a GraphQL name: `[_A-Za-z][_0-9A-Za-z]*`.
  Upper case first is the convention -- query names are type-like.
- **The query source is a literal.** A query assembled at runtime is
  invisible to the analysis, cannot be injected, and gets no generated type.

`T` in `GraphQLQuery<T>` is what **one execution** yields: the value of the
query's single top-level selection, unwrapped. Aliasing the one method changes
nothing.

Fragments are not supported, spreads or inline. Both the generated result
type and the client's conversion map would silently miss the fields a
fragment contributes, so a query using one is refused where it is declared.

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
`vite.config.ts` and `@quinscape/qlive-codegen` is installed. Point it
elsewhere, or turn it off, with the plugin's `queryTypes` option. The
`indexes` option of babel-plugin-track-usage has to stay on (it is by
default) -- without the source offsets there is nowhere to patch.

So a new query is written like this, and nothing else:

```ts title="src/app/Q_Bar.ts"
import {GraphQLQuery} from "@quinscape/qlive-ts";

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

## Running a query directly

```ts
const result = await Q_Foo.execute({config: {offset: 0, pageSize: 10}});
```

`execute()` posts the query, converts the variables on the way out and the
result on the way in, and unwraps the single top-level selection -- so it
resolves to `T`.

For the cases that do not fit -- several top-level selections, or you want
the wire format -- there is the raw call:

```ts
import graphql, {firstValue} from "@quinscape/qlive-ts";
const data = await graphql(query, params);   // the whole "data" object
```

It rejects on a transport error or on any GraphQL error in the response.

## Where `schema.graphql` comes from

Everything above generates from `schema.graphql`, so a stale one is not an
error anyone sees -- it is wrong types in checked-in source. Two ways to
refresh it from a running backend:

- **your IDE's GraphQL plugin**, pointed at the backend, which is what
  `qlive-test`'s own file was written by;
- **the codegen CLI**, for when you have no such plugin:

  ```bash
  generate-schema http://localhost:8080 schema.graphql
  ```

  An introspection query and `printSchema`, nothing besides. It reads
  `/_dev/graphql` -- unauthenticated and CSRF-exempt, and
  [refused outside the dev profile](/qlive-framework/server-setup/), so the
  backend has to be running one.

**Pick one and stay with it.** Both produce the same schema, but not the
same file: printers disagree about indentation and about how a description
is quoted. Neither shape is the right one, and nothing here checks which
you used -- but two of them alternating rewrite the whole file back and
forth, and a real change is then a needle in a few hundred lines of
reformatting. Changing your mind is a one-time reformat; do it on its own.

Neither belongs in `pnpm generate`. That has to keep working on a fresh
checkout with no backend running, which is exactly when you want the types
regenerated from the schema you already have.

## `types.d.ts`

The TypeScript view of your domain is generated from the schema by the
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

## Converters

Values do not arrive as they travel. A converter is registered per GraphQL
named type and runs in both directions:

| Type | On the wire | In the application |
|---|---|---|
| `Timestamp` | ISO-8601 string | `Temporal.Instant` |
| `Date` | ISO-8601 string | `Temporal` value |
| `FooDocument` and friends | plain JSON object | `QueryDocument` instance |

```ts
import {registerConverter, Temporal} from "@quinscape/qlive-ts";

registerConverter("MyScalar", {
    fromServer: (value, type) => ...,
    toServer:   (value, type) => ...,   // optional for output-only types
});
```

Registering a second converter for a type replaces the first, so you can
override the ones QLive brings. Do that from `startup()`'s
[`init` hook](/qlive-framework/startup-and-entry-points/): the built-ins
are registered while `startup()` initialises the config, and the hook is
the point after that and before the first view renders.

A converter is never called with `null` or `undefined`. Import `Temporal`
from `@quinscape/qlive-ts`, never from `temporal-polyfill` directly -- a
second copy of the polyfill produces instants that do not typecheck against
the first.

## Hand-written types on the Java side

When a table's columns do not say everything about a type, replace the
generated POJO with a hand-written class that extends it, and register it
with `objectType()` after the schema's own types. DomainQL resolves a
domain type by simple name, so yours takes the generated one's place --
including for the query document service, which materializes whatever the
table lookup names.

Extending the generated POJO is what keeps it able to hold a row: the
columns, their JPA annotations and the fetcher context all come along.

A field no column backs is fetched from the object rather than selected:

```java
@GraphQLComputed
public String getSummary()
{
    return getName() + " / " + getStringValue();
}
```

A property has to be writable to become a field at all, so such a field
needs a setter even when nothing reads what it stores.

A query selecting it should select the fields it is computed from as well
-- nothing fetches a column on its account. A filter is the other case: a
computed property cannot go into a `WHERE` clause, and a filter path naming
one is an error.

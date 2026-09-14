---
title: Query documents
description: Paging, sorting, update(), and the server-side query.
sidebar:
  order: 5
---

A **query document** is the framework's paged result: rows, the config they
were fetched with, and the total row count. Server-side it is
`QueryDocument<T>`; in the schema it appears per type as `FooDocument`,
`BarDocument` and so on; in the browser it is a `QueryDocument` instance.

```graphql
query Q_Foo($config: QueryConfig!) {
    queryFooDocument(config: $config) {
        type
        config
        rows { id name }
    }
}
```

## What a view sees

`useInjection()` on a query selecting a document gives you a **snapshot**:

Every method of a document and every field of a snapshot is
[Query documents in the API reference](/qlive-framework/api/query-documents/).

```ts
interface QueryDocumentSnapshot<T> {
    type: string
    config: QueryConfig
    rows: T[]
    rowCount: number
    update(newConfig: QueryConfigDelta): Promise<QueryDocumentSnapshot<T>>
}
```

`rowCount` is everything the condition matches, not the number of rows you
just received -- it is the number you page by.

The document itself is a store that is mutated in place; the snapshot is
the opposite. A fresh object every time the document changes, the same
object as long as it does not. That is what makes an update visible to
`React.memo`, to effect dependencies and to React's own change detection,
and it is why you render the snapshot rather than the document.

## Updating

```tsx
const foos = useInjection(Q_Foo);

<button onClick={() => foos.update({offset: 5})}>Next page</button>
```

`update()` re-executes the query the document came from, with its config
changed as given, and updates the document in place. Every component
subscribed to it re-renders with a new snapshot. It resolves to the
snapshot the update produced.

It takes a **delta**, spread over the document's current config -- so
`{offset: 5}` changes the offset and leaves the filter, the page size and
the sort alone.

Only a document that came out of an execution can be updated. One you built
by hand carries no query and `update()` says so.

## QueryConfig

```ts
interface QueryConfig {
    condition: FilterExpression | null
    offset: number
    pageSize: number
    sortFields: FieldExpression[]
}
```

`QueryConfigDelta` is the same with every field optional.

The config that comes back on the document is **the config that was
applied**, not the one you sent. Where you named no sort fields, the server
sorts by the primary key and says so in the config it returns -- and since
your next `update()` is spread over exactly that config, a document that
came back sorted stays sorted.

## Round-tripping

A config travels as a GraphQL variable in both directions, which is why the
condition scalar's JSON form has to be readable back in. It is: the node
types survive the trip, values keep their scalar types, and timestamps come
back as the UTC they went out as. Taking the config off a document and
sending it straight back in is the supported path, and the one `update()`
itself takes.

## The server side

An application exposes document queries from a logic bean. One generic
method covers every type:

```java
@GraphQLLogic
public class QueryLogic
{
    private final QueryDocumentService queryDocumentService;

    @GraphQLQuery
    public <T> @NotNull QueryDocument<T> queryDocument(
        @GraphQLTypeParam(
            namePattern = "query*Document",
            typeNamePattern = "*Document",
            types = {Foo.class, Bar.class, Baz.class, AppUser.class}
        )
        Class<T> type,
        DataFetchingEnvironment env,
        @NotNull QueryConfig config
    )
    {
        return queryDocumentService.buildQuery(type, env, config)
            .selectByFilter(true)
            .execute();
    }
}
```

`@GraphQLTypeParam` is what turns one Java method into `queryFooDocument`,
`queryBarDocument` and the rest. Adding a type to `types` adds a query.

### What the query does

The plan comes from the **GraphQL selection**: what the query selects is
what gets queried. One statement fetches the root type and every to-one
relation below it -- those are left joins on aliases named after the
relation field -- plus one more statement to count what the paging cut off.

A to-many relation is never joined, because that would multiply rows and
take both the page and the count with it. It gets a query of its own, keyed
by the parents already fetched, and is stitched back on. Many-to-many is
not a special case: the link table is a to-many relation and the far side a
to-one relation of that.

A filter path reaching through a to-many relation becomes a correlated
`EXISTS` rather than a join, for the same reason.

### `selectByFilter`

The one option worth understanding.

- **`false` (the default) is the strict mode.** A filter or sort path may
  only name a field the query actually selects. That makes the query
  document itself the security boundary: the selection is static source
  text your build already analyzed, and a config posted by a browser varies
  the `WHERE`, the `ORDER BY` and the page, and can reach nothing the
  document does not name.
- **`true` lets a path extend the plan**: relations it crosses get joined,
  and the field it ends at gets selected. More convenient, and a wider
  surface -- a client can then read through relations the query did not
  select.

Either way a path naming nothing at all is an error, never a condition
quietly dropped, and operator names are checked against a positive list
before anything is done with them. Values are always bound, never rendered
into the SQL.

### Maximum page size

A page size of 0 asks for every row there is, and a config comes from the
browser. A type can say how far that goes:

```java
@Bean
public MetadataProvider queryConfigMetadata()
{
    return QueryConfigMetadataProvider.newProvider()
        .forType(Foo.class)
            .maxPageSize(100)
            .build();
}
```

Every query over rows of that type is held to it, whoever asked and however
the config got there -- an injection, an `update()`, a logic bean building
one by hand. A larger page, and an unlimited one, become that page size.

The config that comes back on the document says the page size that was
applied, so a client that hit the maximum sees it: `rowCount` is still
everything the condition matches, the next `update()` spreads over the
applied config, and a cut page does not look like the last page of a short
table. The maximum itself travels to the client as type meta data under
`maxPageSize`, for the page size controls that would rather not offer what
the server will not give.

A type that declares none is unlimited, which is what every type is until
an application says otherwise.

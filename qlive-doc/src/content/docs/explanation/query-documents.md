---
title: Query documents
description: The paged result, the store behind it, and the query plan it comes from.
sidebar:
  order: 6
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

One Java method covers every type that has one -- see
[Expose document queries](/qlive-framework/how-to/expose-document-queries/).

## The store and its snapshots

`useInjection()` on a query selecting a document does not hand you the
document. It hands you a **snapshot** of it, and subscribes your component.

The document itself is a store that is mutated in place; the snapshot is
the opposite. A fresh object every time the document changes, the same
object as long as it does not. That is what makes an update visible to
`React.memo`, to effect dependencies and to React's own change detection,
and it is why you render the snapshot rather than the document.

`rowCount` on it is everything the condition matches, not the number of
rows you just received -- it is the number you page by.

## Updating is a delta

```tsx
const foos = useInjection(Q_Foo);

<button onClick={() => foos.update({offset: 5})}>Next page</button>
```

`update()` re-executes the query the document came from with its config
changed as given, updates the document in place, and every component
subscribed to it re-renders with a new snapshot.

What it takes is a **delta**, spread over the document's current config --
so `{offset: 5}` changes the offset and leaves the filter, the page size
and the sort alone. Only a document that came out of an execution can be
updated; one built by hand carries no query and says so.

The config that comes back on the document is **the config that was
applied**, not the one you sent. Where you named no sort fields, the server
sorts by the primary key and says so in the config it returns -- and since
your next `update()` is spread over exactly that config, a document that
came back sorted stays sorted. A page size the server capped comes back
capped for the same reason, so a cut page does not look like the last page
of a short table.

## Round-tripping

A config travels as a GraphQL variable in both directions, which is why the
condition scalar's JSON form has to be readable back in. It is: the node
types survive the trip, values keep their scalar types, and timestamps come
back as the UTC they went out as. Taking the config off a document and
sending it straight back in is the supported path, and the one `update()`
itself takes.

## What the query does

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

## Why the document is the security boundary

A `QueryConfig` arrives from a browser, and it varies the `WHERE`, the
`ORDER BY` and the page of a query it did not write.

What holds that is the selection. It is static source text your build
already analyzed, so a config can reach nothing the document does not name
-- that is the strict mode a document query runs in by default, and
[`selectByFilter`](/qlive-framework/how-to/expose-document-queries/#selectbyfilter)
is where an application decides to widen it.

Either way a path naming nothing at all is an error, never a condition
quietly dropped, and operator names are checked against a positive list
before anything is done with them. Values are always bound, never rendered
into the SQL.

Every field of a snapshot and every method of a document is
[Query documents in the API reference](/qlive-framework/api/query-documents/).

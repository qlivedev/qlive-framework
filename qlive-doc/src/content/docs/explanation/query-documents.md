---
title: Query documents
description: The paged result, the store behind it, and the query plan it comes from.
sidebar:
  order: 5
---
**Query documents** are the central aspect of the data querying in QLive. A query document contains the results of a query
as well as the **QueryConfig** the query was created with. 

The QueryConfig encapsulates
 
 * row offset
 * pagination size 
 * [FilterDSL condition](/qlive-framework/explanation/filter-dsl/) (optional)
 * Sort expressions

This allows client components to update the document to get the next page or sort it a different way. 

### QueryDocument&lt;T&gt;

One the Java / server-side, the query documents exist as generic type `QueryDocument<T>`. QLive generates a concrete 
query document type for each used row type. The types are commonly named `FooDocument`, `BarDocument` and so on.

The query document types enter the schema usually by being referenced by a QueryDocumentService endpoint. 

See [Expose document queries](/qlive-framework/how-to/expose-document-queries/).

## Query documents on the client side

On the client side, we can receive a query document in two ways. We receive a snapshot of it via `useInjection(Q_XXX)` 
or as a runtime query result by invoking `Q_XXX.execute()`. 

Both ways ensure that the result from the server is converted properly into a `QueryDocument` instance in TypeScript.

The original query is attached to the document as its source, and it is that query that gets executed again to 
update the query document. 

## QueryDocuments in React
                         
The QueryDocument instance itself is mutable but offers snapshot/subscribe for the QLive hooks and React. 
`useInjection` and `useQueryDocument` return an immutable query document snapshot and register the component to the 
query document so that updates re-render automatically. The stable identity of the snapshot allows for the usual React
patterns like React.memo to be applied. 

### Updating

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

The config that comes back on the document is **the config the query ran
with**, not the one you sent. For an injection that is the config the
server assembled -- the type's defaults, the call's delta, whatever an
interceptor changed -- so every document carries a complete config, and
sending it back as it is runs the same query again. A page size the server
capped comes back capped, so a cut page does not look like the last page of
a short table.

Under that config, the server makes the order the SQL runs in total.
Sorting by a column with duplicates would leave the order among them to the
database, and paging over that can show a row on two pages or on none. So
where the sort fields don't already cover a unique key of NOT NULL
columns, the server appends the rest of one they partly cover, or else the
primary key; with no sort fields at all, the primary key is the sort. None
of that shows in the config that comes back. It follows from the config's
sort the same way every time, so the config alone gets the same order
again, and a sort nobody chose never looks like one somebody did.

## QueryDocumentService
                       
The QueryDocumentService provides the ability to actually execute queries to receive a query document.

The service turns a GraphQL query selection and a `QueryConfig` instance into an optimized query plan.

Starting at the root type, all to-one relations are simply joined. 
A to-many relation is never joined, but gets a query of its own which is stitched back on in-memory.

A filter path reaching through a to-many relation becomes a correlated `EXISTS` rather than a join.

## Why the document can be a security boundary

The expressions embedded within `QueryConfig` allow an extension of the query into joined objects the query never
mentions. You need to know whether it is relevant to security concerns in your application. You might just 
consider the schema itself a security boundary and keep certain aspects of the database out of it. You might want
to define it all in the schema and intercept query configs for security purposes.

In any case, using `.selectByFilter(true)` opts into allowing the filter to extend the initial selection and not doing
do creates an error at runtime if that is the case.

Every field of a snapshot and every method of a document is [Query documents in the API reference](/qlive-framework/api/query-documents/).

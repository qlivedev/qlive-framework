---
title: Injections
description: useInjection() and the rules the static analysis imposes.
sidebar:
  order: 3
---

An injection is a query the server runs **before the page is sent**, whose
result arrives inside the HTML document. It is what makes a QLive page
complete when it paints.

```tsx
import {useInjection} from "@quinscape/qlive-ts";
import {Q_Foo} from "./Q_Foo";

export default function Home() {

    const foos = useInjection(Q_Foo);

    return <pre>{JSON.stringify(foos, null, 4)}</pre>;
}
```

Nothing is declared twice. `useInjection(Q_Foo, ...)` in the view is the
only place the query is named; the build's analysis carries it to the
server, which resolves the identifier the same way the bundler does -- in
the calling module and in the modules it imports directly.

## Signature

```ts
function useInjection<T>(query: GraphQLQuery<T>, params?: InjectParams): T
```

It returns `T`, the value of the query's single top-level selection. Where
that value is a query document, what you get is a **snapshot** of it and
your component is subscribed: an `update()` re-renders it. See
[Query documents](/qlive-framework/reference/query-documents/).

Rules of hooks apply -- call it at the top level of a view,
unconditionally.

## The rules

These are not style advice. Each one is a thing the mechanism cannot do.

### Only views may inject

A component that calls `useInjection()` is refused. In production the build
fails at startup; in dev it is reported and the server keeps serving so you
can move the call.

The reason is that a request identifies exactly one module: the view being
served. A component would be invisible to the server, so its data would not
be in the page, and the failure would surface in the browser as a missing
injection rather than where the mistake is. It would also mean a component
quietly costing a query in every view that imports it, whether or not it
renders.

A component that needs data gets it from the view that injected it.

### Only the view's own calls run

Not the calls in the modules it imports. What a view imports is not what it
renders.

### Parameters have to be readable at build time

```tsx
// works -- the analysis can see this
const foos = useInjection(Q_Foo, {config: {pageSize: 5}});

// does not -- there is nothing to read at build time
const foos = useInjection(Q_Foo, {config: {pageSize: userPreference}});
```

The parameters are the query's GraphQL variables, and the server reads them
out of the analysis to run the query before the page exists. Anything it
cannot see at build time is not there when the query runs.

For values only known at runtime, inject a starting page and move on with
`update()` or `execute()`.

### The query has to be a declared constant

`new GraphQLQuery(...)` at module scope, with a literal source. Two ways of
naming it are accepted, because both are in use:

- the query lives in a module named after it -- `app/Q_Foo.ts` exporting
  `Q_Foo` -- which is what the generated result types assume, or
- the operation itself is named like the identifier:
  `export const Q_Foo = new GraphQLQuery("query Q_Foo ...")`, which covers
  queries collected in a shared module.

## Injection ids and `__id`

The result goes out under an **injection id**, which is the GraphQL
operation name by default. A view injecting the same query twice needs to
disambiguate at least one of them:

```tsx
const first  = useInjection(Q_Foo, {__id: "first",  config: {pageSize: 5}});
const second = useInjection(Q_Foo, {__id: "second", config: {offset: 10}});
```

`__id` is not a variable of the query -- it is stripped before execution,
and a query does not declare it. Two calls reading the same id is an error,
reported at build time: one of them would get data it did not ask for, and
only you can say which.

## Query config parameters

Naming no config at all is the normal case:

```tsx
useInjection(Q_Foo);
```

The injection still runs with a complete `QueryConfig`. It is assembled on
the server, most general first:

1. the defaults a fresh `QueryConfig` has,
2. the delta the row type of the queried document declares, if it declares
   one -- see [Per-type query config](#per-type-query-config) below,
3. whatever the call itself names.

So the page size, sort order and standing condition of a type are said once,
on the server, next to the type -- and every view querying those rows gets
them without repeating itself.

Where a view does want something else, it gives a **delta**: the fields it
cares about, and no others.

```tsx
useInjection(Q_BazList, {config: {pageSize: 50}});
```

The rest still comes from the two layers underneath, the same way
`QueryConfigDelta` spreads over a document's current config on the client.

That is what strong defaults are for -- to be broken, and broken only where
something local wants something different. The example above is a picker
that needs its options all at once, which is a fact about that one form and
not about the type, so it is said at the call site. A page size every view
of those rows should have is the opposite kind of fact and belongs on the
type, where saying it once covers all of them.

### Per-type query config

What a type declares is written by a `QueryConfigMetadataProvider` bean on
the Java side:

```java
@Bean
public MetadataProvider queryConfigMetadata()
{
    return QueryConfigMetadataProvider.newProvider()
        .forAllTypes()
            .pageSize(20)
        .andForType(Foo.class)
            .sortFields("name")
            .maxPageSize(100)
        .build();
}
```

`forAllTypes()` is the house rule for every row type the domain has a query
document for; `forType(Class)` and `forTypes(Class...)` are the departures
from it. Chain statements with `andForType()`, `andForTypes()` and
`andForAllTypes()`, and close the chain with `build()`.

A type may only be named once -- say it with `forAllTypes()` and depart from
it per type rather than declaring the same type twice.

## Reading the raw injection

```ts
import {data} from "@quinscape/qlive-ts";

const {value, type, meta} = data("Q_Foo");
```

`useInjection()` is the normal way in. `data()` is for the cases that want
the GraphQL type or the meta alongside the value, or the id of an injection
no view claimed. It gives you the value in whatever state it is in -- raw
as received until the first read converts it -- and subscribes to nothing.

Both, and the query and parameter types they are spelled with, are
[Injection in the API reference](/qlive-framework/api/injection/).

## When something goes wrong

A view reads its injection unconditionally, so a page served without one
fails in the browser where the reason is no longer visible. QLive therefore
treats a failing injection as fatal for the page rather than leaving it out,
and reports the mistakes it can see -- a call outside a view, two calls on
one id, an identifier resolving to no query -- off the analysis instead: at
startup for a production build, and at every push in dev.

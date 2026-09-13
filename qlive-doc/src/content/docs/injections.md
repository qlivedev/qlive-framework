---
title: Injections
description: useInjection() and the rules the static analysis imposes.
sidebar:
  order: 106
---

An injection is a query the server runs **before the page is sent**, whose
result arrives inside the HTML document. It is what makes a QLive page
complete when it paints.

```tsx
import {useInjection} from "@quinscape/qlive-ts";
import {Q_Foo} from "./Q_Foo";

export default function Home() {

    const foos = useInjection(Q_Foo, {config: {pageSize: 5}});

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
[Query documents](/qlive-framework/query-documents/).

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

A `QueryConfig` variable may be given as a **delta** -- the fields you care
about, and no others:

```tsx
useInjection(Q_Foo, {config: {pageSize: 5}});
```

The rest comes from the default config, the same way `QueryConfigDelta`
behaves on the client. A variable the call does not name at all is left
alone, so a query that insists on its config reports a missing one rather
than being handed a default nobody asked for.

## Reading the raw injection

```ts
import {data} from "@quinscape/qlive-ts";

const {value, type, meta} = data("Q_Foo");
```

`useInjection()` is the normal way in. `data()` is for the cases that want
the GraphQL type or the meta alongside the value, or the id of an injection
no view claimed. It gives you the value in whatever state it is in -- raw
as received until the first read converts it -- and subscribes to nothing.

## When something goes wrong

A view reads its injection unconditionally, so a page served without one
fails in the browser where the reason is no longer visible. QLive therefore
treats a failing injection as fatal for the page rather than leaving it out,
and reports the mistakes it can see -- a call outside a view, two calls on
one id, an identifier resolving to no query -- off the analysis instead: at
startup for a production build, and at every push in dev.
